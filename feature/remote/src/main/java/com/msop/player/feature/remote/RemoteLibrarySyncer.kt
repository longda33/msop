package com.msop.player.feature.remote

import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toEntity
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.OpenListClient
import com.msop.player.core.network.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteSyncStats(
    val scanned: Int = 0,
    val tracks: Int = 0,
    val deleted: Int = 0,
    val directories: Int = 0,
    val elapsedMs: Long = 0L,
    /** 被 Subsonic 元数据补全（艺术家/专辑/封面）的曲目数；没配应用密钥时为 0。 */
    val enriched: Int = 0,
) {
    val summary: String
        get() = buildString {
            append("扫描 $scanned 项 · 写入 $tracks 首")
            if (enriched > 0) append(" · 补全 $enriched 首")
            append(" · 清理 $deleted 条 · $elapsedMs ms")
        }
}

/**
 * 把 OpenList 上的音频同步进统一的 `tracks` 表（P3-03）。
 *
 * 用 BFS 而不是递归：目录层级可能很深，递归在协程里容易爆栈。
 * 每条 Track 的 `uri` 是稳定的 `openlist:/路径`，播放时才由解析器换成直链。
 *
 * **只清理 OPENLIST 来源的幽灵条目**，绝不能动本地曲目——`idsBySource`
 * 就是为此存在的。另有一条安全阀：一个远程文件都没扫到时跳过清理，
 * 因为那多半是配置错误或网络故障，照常差集等于清空远程曲库。
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
            val nowSeconds = System.currentTimeMillis() / 1000L
            val found = LinkedHashMap<String, Track>()
            val pendingDirs = ArrayDeque<String>()
            pendingDirs.add("/")

            var scanned = 0
            var directories = 0
            var pages = 0

            while (pendingDirs.isNotEmpty() && pages < MAX_PAGES) {
                val dir = pendingDirs.removeFirst()
                directories++
                var page = 1
                while (pages < MAX_PAGES) {
                    val data = client.list(dir, page, PAGE_SIZE)
                    val items = data.content.orEmpty()
                    pages++

                    items.forEach { item ->
                        scanned++
                        val childPath = RemoteIdFactory.join(dir, item.name)
                        when {
                            item.isDir -> pendingDirs.add(childPath)
                            RemoteIdFactory.isAudio(item.name) -> {
                                val track = RemoteTrackMapper.toTrack(item, childPath, nowSeconds)
                                found[track.id] = track
                            }
                        }
                    }
                    onProgress(found.size)

                    // total 是**当前目录**的条目总数，不是全库的
                    if (items.size < PAGE_SIZE || page * PAGE_SIZE >= data.total) break
                    page++
                }
            }

            // Subsonic 元数据补全（可选）：真实艺术家/专辑/封面。失败只降级，不阻断同步
            var enriched = 0
            if (found.isNotEmpty() && subsonic.isConfigured()) {
                runCatching {
                    val index = SubsonicMetadata.index(subsonic.allSongs())
                    found.replaceAll { _, track ->
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

            val stale = dao.idsBySource(SourceType.OPENLIST).toSet() - found.keys
            if (found.isNotEmpty()) {
                if (stale.isNotEmpty()) dao.deleteByIds(stale.toList())
                dao.upsertAll(found.values.map { it.toEntity() })
                configStore.markSynced(found.size)
            }

            RemoteSyncStats(
                scanned = scanned,
                tracks = found.size,
                deleted = if (found.isEmpty()) 0 else stale.size,
                directories = directories,
                elapsedMs = System.currentTimeMillis() - started,
                enriched = enriched,
            )
        }

    /** 清除服务器配置时一并清掉远程曲目，避免留下一堆打不开的条目。 */
    suspend fun clearRemote() = withContext(Dispatchers.IO) {
        dao.deleteBySource(SourceType.OPENLIST)
        configStore.markSynced(0)
    }

    private companion object {
        const val PAGE_SIZE = 100
        /** 安全上限：2 万条，避免服务端异常时无限翻页。 */
        const val MAX_PAGES = 200
    }
}
