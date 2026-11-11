package com.primuse.lite.feature.local.metadata

import com.primuse.lite.core.model.Track

/**
 * 元数据补齐所需的存储能力。与 [com.primuse.lite.feature.local.scan.LocalTrackStore] 同样
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

    /** 还没有封面的曲目，按标题排序保证多次调用顺序稳定。 */
    suspend fun tracksMissingCover(limit: Int): List<Track>

    suspend fun upsertAll(tracks: List<Track>)

    suspend fun countWithCover(): Int

    /** 全部非空的 `coverUri`，供封面缓存清理孤立文件。 */
    suspend fun referencedCovers(): Set<String>
}
