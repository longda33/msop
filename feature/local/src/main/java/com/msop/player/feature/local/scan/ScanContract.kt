package com.msop.player.feature.local.scan

import com.msop.player.core.model.Track

/**
 * 一次扫描的结果统计，用于日志与设置页展示。
 *
 * 三个计数的关系是 `scanned = written + deduped`：
 * - [scanned] 数据源**返回的记录数**（同一文件被两条通道各返回一次就计 2）
 * - [written] 去重后的**曲目数**（按 id 唯一）
 * - [deduped] 被判定为重复、没有新增的条数
 *
 * 注意「去重」不等于「丢弃」：重复记录仍然会 upsert，因为后到的来源元数据更全。
 */
data class ScanStats(
    val scanned: Int = 0,
    val written: Int = 0,
    val deleted: Int = 0,
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
 * 同步所需的存储能力。只声明用得到的方法，避免为了测试去实现整个 TrackDao。
 */
interface LocalTrackStore {
    /** 库内**本机来源**的全部 id。清理幽灵条目时用来做差集。 */
    suspend fun allIds(): Set<String>

    suspend fun latestModified(): Long?

    suspend fun upsertAll(tracks: List<Track>)

    suspend fun deleteByIds(ids: List<String>)

    suspend fun count(): Int
}
