package com.msop.player.feature.local.metadata

import com.msop.player.core.model.Track

/**
 * 元数据补齐所需的存储能力。与 [com.msop.player.feature.local.scan.LocalTrackStore] 同样
 * 刻意收窄，让补齐逻辑能在纯 JVM 单测里跑。
 */
interface MetadataStore {

    /**
     * 需要补标签的曲目。
     *
     * 判据是 `durationMs == 0`，不是「artist 为空」：
     * - 时长是任何能解析的音频文件都有的字段，读不到说明**我们还没读过这个文件**
     * - 有些文件本来就没有 artist 标签，用 artist 判断会每次扫描都重读一遍，永远补不完
     * - 读取失败（文件损坏）时时长仍是 0，下次扫描会重试，正是想要的行为
     */
    suspend fun tracksNeedingMetadata(limit: Int): List<Track>

    /**
     * 需要补音质的曲目（P7-06）。默认空实现，纯 JVM 测试的 FakeStore 不需要关心。
     */
    suspend fun tracksNeedingQuality(limit: Int): List<Track> = emptyList()

    /** 还没有封面的曲目，按标题排序保证多次调用顺序稳定。 */
    suspend fun tracksMissingCover(limit: Int): List<Track>

    suspend fun upsertAll(tracks: List<Track>)

    suspend fun countWithCover(): Int

    /** 全部非空的 `coverUri`，供封面缓存清理孤立文件。 */
    suspend fun referencedCovers(): Set<String>

    /**
     * 已有 `coverUri` 的曲目（2026-11-16 晚用户报「封面怎么是白色」后新增）。
     *
     * 用于**封面自愈**：数据库里的 `coverUri` 可能指向一个已经被删掉的文件（换包、
     * 清缓存、系统清应用数据都会），而 [tracksMissingCover] 只挑 `coverUri IS NULL`
     * 的条目，这些「有 URI 但文件没了」的曲目永远不会被重新提取，只能一直白着。
     * 默认空实现，纯 JVM 测试的 FakeStore 不用关心。
     */
    suspend fun tracksWithCover(limit: Int): List<Track> = emptyList()

    /** 把这些曲目的 `coverUri` 清空，好让封面预热重新提取一次。 */
    suspend fun clearCover(ids: List<String>) = Unit
}
