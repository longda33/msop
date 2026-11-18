package com.msop.player.feature.remote

import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.dao.deleteByIdsChunked
import com.msop.player.core.database.mapper.toEntity
import com.msop.player.core.datastore.WebDavConfigStore
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.RemoteDurationProbe
import com.msop.player.core.network.WebDavClient
import com.msop.player.core.network.WebDavUrl
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 WebDAV 音乐库同步进统一的 `tracks` 表（P7-07）。
 *
 * 2026-11-16 晚起支持**多台同时启用**：遍历 `enabledConfigs()`，曲目 id / uri 带
 * profile 前缀，跨服务器同名歌曲由 [RemoteDeduper] 收敛成一条。
 *
 * BFS 遍历，不递归；WebDAV 一次 PROPFIND 就能列完一个目录，没有分页。
 * 只清理 [SourceType.WEBDAV] 的幽灵条目，绝不碰本机与 OpenList。
 *
 * 三个安全阀：
 * - 一个文件都没扫到时不清理（多半是地址/权限配错）；
 * - 有目录列失败时也不清理（该目录下的曲目不能因为一次 403 就被当成已删除）；
 * - 目录数触顶（循环引用 / 超大库）时不清理：剩余目录压根没扫，里面的曲目会落到 stale。
 */
@Singleton
class WebDavLibrarySyncer @Inject constructor(
    private val client: WebDavClient,
    private val dao: TrackDao,
    private val configStore: WebDavConfigStore,
) {

    suspend fun sync(onProgress: suspend (Int) -> Unit = {}): RemoteSyncStats =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val nowSeconds = started / 1000L

            val configs = configStore.enabledConfigs().ifEmpty {
                configStore.activeConfig()?.let { listOf(it) }.orEmpty()
            }.filter { it.configured }
            if (configs.isEmpty()) throw com.msop.player.core.network.WebDavException.NotConfigured()

            val raw = LinkedHashMap<String, Track>()
            val scanned = AtomicInteger()
            val directories = AtomicInteger()
            val failedDirectories = AtomicInteger()

            configs.forEach { stored ->
                // 2026-11-17 深夜（续）用户需求「路径支持填写多条（一行一条）」：
                // - 单目录：行为完全不变（从 `/` 往下走，库里路径相对该目录）。
                // - 多目录：改用「相对服务器根」的绝对路径（根设成 `/`，每个目录各作一个起点），
                //   这样不同目录下的同名文件不会撞成同一条 uri；播放换算走 effectiveRootPath。
                val roots = stored.rootPaths
                val multiRoot = roots.size > 1
                val config = if (multiRoot) stored.copy(rootPath = "/") else stored
                val perProfile = LinkedHashMap<String, Track>()
                val pendingDirs = ArrayDeque<String>().apply {
                    if (multiRoot) roots.forEach { add(it) } else add("/")
                }
                val localDirs = AtomicInteger()
                // 「请求并发数」和 OpenList 侧同语义：0 用默认 4，按层并行列目录
                val gate = Semaphore(config.effectiveConcurrency)
                val mapLock = Any()

                while (pendingDirs.isNotEmpty() && directories.get() < MAX_DIRECTORIES) {
                    val batch = pendingDirs.toList()
                    pendingDirs.clear()
                    coroutineScope {
                        batch.map { dir ->
                            async {
                                gate.withPermit {
                                    directories.incrementAndGet()
                                    localDirs.incrementAndGet()
                                    val items = try {
                                        client.listBlocking(dir, config)
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (e: Exception) {
                                        failedDirectories.incrementAndGet()
                                        AppLog.e("WebDAV 列目录失败 ${config.id}:$dir", e)
                                        emptyList()
                                    }

                                    items.forEach { item ->
                                        synchronized(mapLock) {
                                            scanned.incrementAndGet()
                                            // item.path 来自 PROPFIND 的 href，是唯一权威的路径来源；
                                            // displayName 只用于展示，可能缺扩展名或被服务器本地化。
                                            val childPath = WebDavUrl.normalizePath(item.path)
                                            val fileName = childPath.substringAfterLast('/')
                                            when {
                                                item.isDir -> pendingDirs.add(childPath)
                                                RemoteIdFactory.isAudio(fileName) -> {
                                                    val track = WebDavTrackMapper.toTrack(
                                                        item = item,
                                                        profileId = config.id,
                                                        nowSeconds = nowSeconds,
                                                    )
                                                    perProfile[track.id] = track
                                                }
                                            }
                                        }
                                    }
                                    onProgress(raw.size + perProfile.size)
                                }
                            }
                        }.awaitAll()
                    }
                    if (localDirs.get() > MAX_DIRECTORIES) break
                }
                raw.putAll(perProfile)
            }

            val (tracks, deduped) = RemoteDeduper.dedupe(raw.values)
            // 时长是客户端探测出来的，重新同步要保住，不能被 0 覆盖；
            // 封面同理（2026-11-18 用户报「新加服务器后封面全没了」）：映射出来的
            // `coverUri` 恒为 null，整行 upsert 会抹掉 `RemoteCoverExtractor` 已探测的路径。
            val previous = dao.bySource(SourceType.WEBDAV).associateBy { it.id }
            val merged = tracks.map { track ->
                val kept = previous[track.id]
                track.copy(
                    durationMs = if (track.durationMs <= 0L) kept?.durationMs ?: 0L else track.durationMs,
                    coverUri = track.coverUri ?: kept?.coverUri,
                )
            }
            val found = merged.associateBy { it.id }
            val stale = dao.idsBySource(SourceType.WEBDAV).toSet() - found.keys
            // 2026-11-18 修复：MAX_DIRECTORIES 触顶后剩余目录根本没扫，不能把它们
            // 里面的曲目当成「云端已删除」。与 RemoteLibrarySyncer 的 pagesExhausted 同语义。
            val directoriesExhausted = directories.get() >= MAX_DIRECTORIES
            val canClean = found.isNotEmpty() && failedDirectories.get() == 0 && !directoriesExhausted
            if (found.isNotEmpty()) {
                if (canClean && stale.isNotEmpty()) dao.deleteByIdsChunked(stale)
                dao.upsertAll(found.values.map { it.toEntity() })
                if (failedDirectories.get() == 0 && !directoriesExhausted) {
                    // 每台各记各的数量，不被合并总数覆盖
                    configs.forEach { config ->
                        val own = found.values.count { track ->
                            WebDavUrl.parseInternalUri(track.uri)?.profileId == config.id
                        }
                        configStore.markSynced(config.id, own)
                    }
                }
            }

            RemoteSyncStats(
                scanned = scanned.get(),
                tracks = found.size,
                deleted = if (canClean) stale.size else 0,
                directories = directories.get(),
                elapsedMs = System.currentTimeMillis() - started,
                skippedDirs = failedDirectories.get(),
                deduped = deduped,
                servers = configs.size,
            )
        }

    /** 给还没有时长的 WebDAV 曲目补时长（后台跑，见 [RemoteDurationProbe]）。 */
    suspend fun fillMissingDurations(limit: Int = 300): Int = withContext(Dispatchers.IO) {
        val configs = configStore.enabledConfigs()
        if (configs.isEmpty()) return@withContext 0
        val pending = dao.needingMetadata(SourceType.WEBDAV, limit)
        val filled = AtomicInteger()
        val gate = Semaphore(configs.maxOf { it.effectiveConcurrency })
        coroutineScope {
            pending.map { entity ->
                async {
                    gate.withPermit {
                        val url = runCatching { client.resolve(entity.uri) }.getOrNull()
                            ?: return@withPermit
                        val headers = runCatching { client.headers(entity.uri) }
                            .getOrDefault(emptyMap())
                        val duration = RemoteDurationProbe.durationMs(url, headers, entity.sizeBytes)
                        if (duration != null && duration > 0L) {
                            dao.upsertAll(listOf(entity.copy(durationMs = duration)))
                            filled.incrementAndGet()
                        }
                    }
                }
            }.awaitAll()
        }
        filled.get()
    }

    /** 清除全部 WebDAV 配置时只删 WEBDAV 来源，本机与 OpenList 曲目不受影响。 */
    suspend fun clearRemote() = withContext(Dispatchers.IO) {
        dao.deleteBySource(SourceType.WEBDAV)
        configStore.markSynced(0)
    }

    /** 只清掉某台 WebDAV 服务器的曲目（取消启用 / 删除配置时用）。 */
    suspend fun clearRemote(profileId: String?) = withContext(Dispatchers.IO) {
        val prefix = profileId?.let { "webdav:$it:" }
        val ids = dao.idsBySource(SourceType.WEBDAV).filter { id ->
            prefix == null || id.startsWith(prefix)
        }
        if (ids.isNotEmpty()) dao.deleteByIdsChunked(ids)
    }

    private companion object {
        /** 安全上限：2 万个目录，防止服务器返回循环引用。 */
        const val MAX_DIRECTORIES = 20_000
    }
}
