package com.msop.player.feature.player

import kotlinx.serialization.Serializable

/**
 * 播放现场快照（P7-02）。
 *
 * 只存 id，不存 `MediaItem` 或 `uri`：
 * - 远程 `uri` 是稳定形式 `openlist:/路径`，但重建队列仍然应该回数据库取完整 [com.msop.player.core.model.Track]，
 *   避免快照里出现两份字段、升级后对不上；
 * - 本地 id 已经包含路径哈希，重扫后 id 不变。
 *
 * [playMode] 存字符串而不是枚举序号：将来枚举增删不会把旧快照读错。
 */
@Serializable
data class PlaybackSnapshot(
    val trackIds: List<String> = emptyList(),
    val currentTrackId: String? = null,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val playMode: String = PlayMode.SEQUENTIAL.name,
    val wasPlaying: Boolean = false,
    /** 写入时的系统时间（epoch ms），用来判断快照是否过期。 */
    val savedAt: Long = 0L,
) {
    /**
     * 把快照对回现有曲库。
     *
     * @param available 数据库里仍然存在的曲目 id
     * @return 过滤后的队列与合法索引；快照过期、为空或一首都不剩时返回 null
     */
    fun resolve(
        available: Set<String>,
        nowMs: Long,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ): RestoredQueue? {
        if (trackIds.isEmpty()) return null
        if (savedAt <= 0L || nowMs - savedAt > maxAgeMs) return null

        val kept = trackIds.filter { it in available }
        if (kept.isEmpty()) return null

        val index = currentTrackId
            ?.takeIf { it in kept }
            ?.let { kept.indexOf(it) }
            ?: currentIndex.coerceIn(0, kept.lastIndex)

        return RestoredQueue(
            trackIds = kept,
            index = index,
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    companion object {
        /** 默认保留 7 天：再久用户已经想不起上次在听什么，恢复反而突兀。 */
        const val DEFAULT_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

/** 快照与当前曲库求交后的结果。 */
data class RestoredQueue(
    val trackIds: List<String>,
    val index: Int,
    val positionMs: Long,
)
