package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.common.log.AppLog
import com.primuse.lite.core.model.Track

/**
 * 把「扫描」和「入库」串起来。
 *
 * 职责刻意收窄：只做分页、批内去重、补时间戳、调存储。真正的数据读取在 [TrackPageSource]，
 * 持久化在 [LocalTrackStore]——两者都能在单测里替换成假实现。
 */
class LocalLibrarySyncer(
    private val source: TrackPageSource,
    private val store: LocalTrackStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * @param full 传 true 强制全量重扫（忽略增量起点）
     */
    suspend fun sync(pageSize: Int = 500, full: Boolean = false): ScanStats {
        val startedAt = System.currentTimeMillis()
        val since = if (full) 0L else (store.latestModified() ?: 0L)

        AppLog.i("本地库同步开始 since=$since full=$full")

        var scanned = 0
        var written = 0
        var deduped = 0

        source.scan(since = since, pageSize = pageSize) { page ->
            scanned += page.size

            // 批内去重：同一路径在极端情况下可能被 MediaStore 返回两次
            val unique = LinkedHashMap<String, Track>(page.size)
            for (track in page) {
                if (unique.put(track.id, track) != null) deduped++
            }

            if (unique.isNotEmpty()) {
                val stampedAt = now()
                val ready = unique.values.map { track ->
                    if (track.addedAt == 0L) track.copy(addedAt = stampedAt) else track
                }
                store.upsertAll(ready)
                written += ready.size
            }
        }

        val stats = ScanStats(
            scanned = scanned,
            written = written,
            deduped = deduped,
            elapsedMs = System.currentTimeMillis() - startedAt,
        )
        AppLog.i(
            "本地库同步结束 扫描=${stats.scanned} 写入=${stats.written} " +
                "去重=${stats.deduped} 耗时=${stats.elapsedMs}ms 库内总数=${store.count()}",
        )
        return stats
    }
}
