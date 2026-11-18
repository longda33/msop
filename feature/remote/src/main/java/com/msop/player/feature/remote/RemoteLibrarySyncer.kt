package com.msop.player.feature.remote

import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.dao.deleteByIdsChunked
import com.msop.player.core.database.mapper.toEntity
import com.msop.player.core.datastore.ServerConfig
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.OpenListClient
import com.msop.player.core.network.OpenListException
import com.msop.player.core.network.RemoteDurationProbe
import com.msop.player.core.network.ServerUrl
import com.msop.player.core.network.SubsonicClient
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

data class RemoteSyncStats(
    val scanned: Int = 0,
    val tracks: Int = 0,
    val deleted: Int = 0,
    val directories: Int = 0,
    val elapsedMs: Long = 0L,
    /** 被 Subsonic 元数据补全（艺术家/专辑/封面/时长）的曲目数；没配应用密钥时为 0。 */
    val enriched: Int = 0,
    /** 列目录失败的目录数：跳过这些目录后同步照常完成。 */
    val skippedDirs: Int = 0,
    /** 去重丢掉的重复条目数（同名跨目录 / flac+m4a 双版本）。 */
    val deduped: Int = 0,
    /** 参与本次同步的服务器台数。 */
    val servers: Int = 1,
) {
    val summary: String
        get() = buildString {
            append("扫描 $scanned 项 · 写入 $tracks 首")
            if (servers > 1) append(" · $servers 台服务器")
            if (enriched > 0) append(" · 补全 $enriched 首")
            if (deduped > 0) append(" · 去重 $deduped 首")
            if (skippedDirs > 0) append(" · 跳过 $skippedDirs 个异常目录")
            append(" · 清理 $deleted 条 · $elapsedMs ms")
        }
}

/**
 * 把 OpenList 上的音频同步进统一的 `tracks` 表（P3-03）。
 *
 * 2026-11-16 晚起支持**多台同时启用**：遍历 `enabledConfigs()`，每台的曲目
 * `id` / `uri` 都带 profile 前缀，播放时由 uri 里的 profile 决定换哪一台的直链。
 * 跨服务器同名歌曲最后统一走 [RemoteDeduper] 收敛成一条。
 *
 * 用 BFS 而不是递归：目录层级可能很深，递归在协程里容易爆栈。
 *
 * **只清理 OPENLIST 来源的幽灵条目**，绝不能动本地曲目——`idsBySource`
 * 就是为此存在的。安全阀：一个远程文件都没扫到时跳过清理，因为那多半是
 * 配置错误或网络故障，照常差集等于清空远程曲库。
 */
@Singleton
class RemoteLibrarySyncer @Inject constructor(
    private val client: OpenListClient,
    private val subsonic: SubsonicClient,
    private val dao: TrackDao,
    private val configStore: ServerConfigStore,
) {

    suspend fun sync(onProgress: suspend (Int) -> Unit = {}): RemoteSyncStats =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val nowSeconds = started / 1000L

            val configs = configStore.enabledConfigs().ifEmpty {
                configStore.activeConfig()?.let { listOf(it) }.orEmpty()
            }.filter { it.configured }
            if (configs.isEmpty()) throw OpenListException.NotConfigured()

            val raw = LinkedHashMap<String, Track>()
            // 并发扫描下计数必须原子，否则「扫描 N 项 / 目录 M 个」会漏数
            val scanned = AtomicInteger()
            val directories = AtomicInteger()
            val pages = AtomicInteger()
            val skipped = AtomicInteger()

            configs.forEach { config ->
                val perProfile = LinkedHashMap<String, Track>()
                val pendingDirs = ArrayDeque<String>()
                // 2026-11-17 深夜（续）用户需求「路径支持填写多条（一行一条）」：
                // 配置里的 rootPath 按行切，每个目录都是一个扫描起点（路径本身已经是绝对路径，
                // 所以流播 / 文件夹聚合都不用改）。
                config.rootPaths.forEach { pendingDirs.add(it) }
                // 2026-11-16 晚用户需求：配置页可填「请求并发数」，不填用默认 4。
                // 按层并行：同一层的目录一起列，信号量把同时在飞的请求压到并发数以内；
                // 单个目录仍逐页顺序翻（OpenList 的分页游标不保证乱序安全）。
                val parallel = config.effectiveConcurrency
                val gate = Semaphore(parallel)
                val mapLock = Any()

                while (pendingDirs.isNotEmpty() && pages.get() < MAX_PAGES) {
                    val batch = pendingDirs.toList()
                    pendingDirs.clear()
                    coroutineScope {
                        batch.map { dir ->
                            async {
                                gate.withPermit {
                                    directories.incrementAndGet()
                                    var page = 1
                                    while (pages.get() < MAX_PAGES) {
                                        // 单个目录列目录失败（缺权限、挂载点异常、服务端 500）不能拖垮整次同步，
                                        // 跳过它继续扫别的目录；但 401/403 是鉴权问题，必须原样抛出。
                                        val data = try {
                                            client.listBlockingWith(config, dir, page, PAGE_SIZE)
                                        } catch (e: OpenListException.Auth) {
                                            throw e
                                        } catch (e: OpenListException.NotConfigured) {
                                            throw e
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            AppLog.w("列目录失败，跳过 ${config.id}:$dir: ${e.message}")
                                            skipped.incrementAndGet()
                                            break
                                        }
                                        val items = data.content.orEmpty()
                                        pages.incrementAndGet()

                                        items.forEach { item ->
                                            synchronized(mapLock) {
                                                scanned.incrementAndGet()
                                                val childPath = RemoteIdFactory.join(dir, item.name)
                                                when {
                                                    item.isDir -> pendingDirs.add(childPath)
                                                    RemoteIdFactory.isAudio(item.name) -> {
                                                        val track = RemoteTrackMapper.toTrack(
                                                            item = item,
                                                            profileId = config.id,
                                                            path = childPath,
                                                            nowSeconds = nowSeconds,
                                                        )
                                                        perProfile[track.id] = track
                                                    }
                                                }
                                            }
                                        }
                                        onProgress(raw.size + perProfile.size)

                                        if (items.size < PAGE_SIZE || page * PAGE_SIZE >= data.total) break
                                        page++
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
                raw.putAll(perProfile)
            }

            // Subsonic 元数据补全（可选）：真实艺术家/专辑/封面/时长。失败只降级，不阻断同步
            var enriched = 0
            if (raw.isNotEmpty() && subsonic.isConfigured()) {
                runCatching {
                    val index = SubsonicMetadata.index(subsonic.allSongs())
                    raw.replaceAll { _, track ->
                        val meta = index[SubsonicMetadata.fileNameOfTrack(track)]
                        val updated = SubsonicMetadata.enrich(track, meta) { id ->
                            subsonic.coverArtUrl(id)
                        }
                        if (updated != track) enriched++
                        updated
                    }
                }.onFailure {
                    AppLog.e("Subsonic 元数据补全失败，保留原生 REST 结果", it)
                }
            }

            val (tracks, deduped) = RemoteDeduper.dedupe(raw.values)
            // 服务端列目录不给时长，时长是客户端读完文件头算出来的；
            // 重新同步不能把它清零，否则每次同步都要重新探测一遍（83 首约几十次请求）
            // 2026-11-18（用户报「新添加服务器后歌曲封面全都不显示」）：`coverUri` 同理必须保住。
            // 远程映射里 `coverUri` 恒为 null（服务端不产出封面，靠 `RemoteCoverExtractor`
            // 探测后回写），整行 upsert 会把上次探测出来的路径抹掉——一次同步就让整库封面
            // 退回占位图，只能等下次冷启动重新探测。这里跟 durationMs 一样做「空值才回填」。
            val previous = dao.bySource(SourceType.OPENLIST).associateBy { it.id }
            val merged = tracks.map { track ->
                val kept = previous[track.id]
                track.copy(
                    durationMs = if (track.durationMs <= 0L) kept?.durationMs ?: 0L else track.durationMs,
                    coverUri = track.coverUri ?: kept?.coverUri,
                )
            }
            val found = merged.associateBy { it.id }
            val stale = dao.idsBySource(SourceType.OPENLIST).toSet() - found.keys
            // 2026-11-17 修复（与 WebDavLibrarySyncer 对齐）：清理幽灵条目必须同时满足
            // ① 这次至少扫到一首（否则多半是配置/网络故障）
            // ② 没有跳过任何目录（缺权限、挂载点异常、服务端 500 时，那个目录里的歌
            //    根本没被列出来，按差集算就是「已删除」——一次 500 就能清掉半个曲库）
            // ③ 没把分页安全阀踩满（MAX_PAGES 用尽时后面的目录压根没扫）
            val pagesExhausted = pages.get() >= MAX_PAGES
            val canClean = found.isNotEmpty() && skipped.get() == 0 && !pagesExhausted
            if (found.isNotEmpty()) {
                if (canClean && stale.isNotEmpty()) dao.deleteByIdsChunked(stale)
                dao.upsertAll(found.values.map { it.toEntity() })
                // 按 profile 分别记「已同步 N 首」，不能用总数覆盖——否则每行都显示合并后的总量
                configs.forEach { config ->
                    val own = found.values.count { track ->
                        ServerUrl.parseInternalUri(track.uri)?.profileId == config.id
                    }
                    configStore.markSynced(config.id, own)
                }
            }

            RemoteSyncStats(
                scanned = scanned.get(),
                tracks = found.size,
                deleted = if (canClean) stale.size else 0,
                directories = directories.get(),
                elapsedMs = System.currentTimeMillis() - started,
                enriched = enriched,
                skippedDirs = skipped.get(),
                deduped = deduped,
                servers = configs.size,
            )
        }

    /**
     * 给还没有时长的云端曲目补时长（后台跑，不挡同步结果）。
     *
     * 这台 OpenList 的 OpenSubsonic 实现不返回 `duration`（实测 `search3` /
     * `getSong` / `getAlbum` 三处都没有），所以只能拿直链读文件头自己算，
     * 见 [RemoteDurationProbe]。跑一批就写库一次，曲库列表会跟着刷新。
     */
    suspend fun fillMissingDurations(limit: Int = 300): Int = withContext(Dispatchers.IO) {
        val configs = configStore.enabledConfigs()
        if (configs.isEmpty()) return@withContext 0
        val pending = dao.needingMetadata(SourceType.OPENLIST, limit)
        val filled = AtomicInteger()
        // 探测也是网络请求，跟着配置页的「请求并发数」走（默认 4）
        val gate = Semaphore(configs.maxOf { it.effectiveConcurrency })
        coroutineScope {
            pending.map { entity ->
                async {
                    gate.withPermit {
                        val parsed = ServerUrl.parseInternalUri(entity.uri) ?: return@withPermit
                        val config = parsed.profileId
                            ?.let { id -> configs.firstOrNull { it.id == id } }
                            ?: configs.first()
                        val url = runCatching {
                            client.rawUrlBlocking(config, parsed.path)
                        }.getOrNull() ?: return@withPermit
                        val duration = RemoteDurationProbe.durationMs(url, fileSize = entity.sizeBytes)
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

    /** 只清掉某台服务器的曲目（取消启用或删除配置时用）。 */    suspend fun clearRemote(profileId: String?, keep: Collection<String> = emptyList()) =
        withContext(Dispatchers.IO) {
            val prefix = profileId?.let { "openlist:$it:" }
            val ids = dao.idsBySource(SourceType.OPENLIST).filter { id ->
                when {
                    prefix != null -> id.startsWith(prefix)
                    else -> true
                } && id !in keep
            }
            if (ids.isNotEmpty()) dao.deleteByIdsChunked(ids)
        }

    /** 清除全部 OpenList 配置时一并清掉远程曲目，避免留下一堆打不开的条目。 */
    suspend fun clearRemote() = withContext(Dispatchers.IO) {
        dao.deleteBySource(SourceType.OPENLIST)
        configStore.markSynced(0)
    }

    // 2026-11-17 深夜（五）：原来的 `normalizeRoot(raw)` 在「路径支持多条」改造后已无调用点
    // （目录归一化统一走 `ServerConfig.rootPaths` → `splitRootPaths`），这里删掉避免死代码。

    private companion object {
        const val PAGE_SIZE = 100
        /** 安全上限：2 万条，避免服务端异常时无限翻页。 */
        const val MAX_PAGES = 200
    }
}
