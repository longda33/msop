package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.common.log.AppLog
import com.primuse.lite.core.model.Track

/**
 * 把「扫描」和「入库」串起来。
 *
 * 职责刻意收窄：只做分页、批内去重、补时间戳、调存储、清理幽灵条目。
 * 真正的数据读取在 [TrackPageSource]，持久化在 [LocalTrackStore]——两者都能在单测里替换成假实现。
 */
class LocalLibrarySyncer(
    private val source: TrackPageSource,
    private val store: LocalTrackStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * @param full 传 true 强制全量重扫（忽略增量起点）。**只有全量扫描才会清理幽灵条目**，
     *             因为增量扫描看不到未变更的文件，据此清理会把整个曲库误删。
     * @param onProgress 每写完一页回调一次，用于更新通知进度。参数为累计「已扫描 / 已写入」
     */
    suspend fun sync(
        pageSize: Int = 500,
        full: Boolean = false,
        onProgress: (suspend (scanned: Int, written: Int) -> Unit)? = null,
    ): ScanStats {
        val startedAt = System.currentTimeMillis()
        val since = if (full) 0L else (store.latestModified() ?: 0L)

        AppLog.i("本地库同步开始 since=$since full=$full")

        var scanned = 0
        var written = 0
        var deduped = 0

        // 全量扫描时记录本次见到的所有 id，用于事后差集清理
        val seenIds: MutableSet<String>? = if (full) HashSet() else null

        source.scan(since = since, pageSize = pageSize) { page ->
            scanned += page.size

            // 批内去重：同一路径在极端情况下可能被返回两次
            val unique = LinkedHashMap<String, Track>(page.size)
            for (track in page) {
                if (unique.put(track.id, track) != null) deduped++
            }

            seenIds?.addAll(unique.keys)

            if (unique.isNotEmpty()) {
                val stampedAt = now()
                val ready = unique.values.map { track ->
                    if (track.addedAt == 0L) track.copy(addedAt = stampedAt) else track
                }
                store.upsertAll(ready)
                written += ready.size
            }

            onProgress?.invoke(scanned, written)
        }

        val deleted = if (seenIds != null) {
            removeMissing(seenIds, scanned)
        } else {
            0
        }

        val stats = ScanStats(
            scanned = scanned,
            written = written,
            deleted = deleted,
            deduped = deduped,
            elapsedMs = System.currentTimeMillis() - startedAt,
        )
        AppLog.i(
            "本地库同步结束 扫描=${stats.scanned} 写入=${stats.written} " +
                "删除=${stats.deleted} 去重=${stats.deduped} " +
                "耗时=${stats.elapsedMs}ms 库内总数=${store.count()}",
        )
        return stats
    }

    /**
     * 删除「库里有但这次没扫到」的条目。
     *
     * 安全阀：全量扫描读到 0 首时直接跳过。这种情况多半是权限被撤销或 MediaStore 异常，
     * 照常清理会把整个曲库清空，属于不可逆破坏。
     */
    private suspend fun removeMissing(seenIds: Set<String>, scanned: Int): Int {
        if (scanned == 0) {
            AppLog.w("全量扫描结果为空，跳过幽灵条目清理（可能是权限丢失或媒体库异常）")
            return 0
        }

        val missing = store.allIds() - seenIds
        if (missing.isEmpty()) return 0

        store.deleteByIds(missing.toList())
        AppLog.i("清理幽灵条目 ${missing.size} 条")
        return missing.size
    }
}
