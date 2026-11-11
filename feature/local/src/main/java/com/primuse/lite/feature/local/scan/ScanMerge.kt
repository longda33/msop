package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.model.Track

/**
 * 把本次扫描结果与库里已有的同 id 记录合并。
 *
 * 为什么需要：扫描器只能看到**文件系统里有**的东西，看不到我们后来补上的东西——
 * 封面缓存路径、SAF 通道读不出的标签。而入库是整行 upsert，不做这层合并的话，
 * 每次重扫都会把这些字段抹成 null：
 * - 封面每次重扫被清空，缓存文件全部变成孤儿被回收，等于白提
 * - SAF-only 文件每次重扫丢掉标签，又因为时长为 0 被重新选中，每扫一次重读一遍
 *
 * 合并规则：**扫描值优先，空缺才用库里的**。代价是「用户在文件里删掉了某个标签」
 * 这种变化同步不过来（扫描给 null，这里会保留旧值）。两害相权，保住补出来的数据更重要。
 */
internal fun Track.mergedWithExisting(existing: Track?): Track {
    if (existing == null) return this
    return copy(
        artist = artist ?: existing.artist,
        album = album ?: existing.album,
        albumId = albumId ?: existing.albumId,
        durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
        trackNo = trackNo ?: existing.trackNo,
        discNo = discNo ?: existing.discNo,
        year = year ?: existing.year,
        coverUri = coverUri ?: existing.coverUri,
    )
}
