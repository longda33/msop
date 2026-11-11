package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.model.Track

/** 一次扫描的结果统计，用于日志与设置页展示。 */
data class ScanStats(
    val scanned: Int = 0,
    val written: Int = 0,
    val deduped: Int = 0,
    val elapsedMs: Long = 0L,
) {
    val isEmpty: Boolean get() = scanned == 0
}

/**
 * 分页曲目来源。抽成接口是为了把「扫描」与「Android MediaStore」解耦，
 * 让同步逻辑能在纯 JVM 单测里跑。
 */
fun interface TrackPageSource {
    /**
     * @param since 只返回修改时间大于该值的曲目；传 0 表示全量
     * @return 本次实际读取的曲目总数
     */
    suspend fun scan(since: Long, pageSize: Int, onPage: suspend (List<Track>) -> Unit): Int
}

/**
 * 同步所需的存储能力。只声明用得到的四个方法，避免为了测试去实现整个 TrackDao。
 */
interface LocalTrackStore {
    suspend fun allIds(): Set<String>
    suspend fun latestModified(): Long?
    suspend fun upsertAll(tracks: List<Track>)
    suspend fun count(): Int
}
