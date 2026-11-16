package com.msop.player.core.database.entity

import androidx.room.Embedded

/**
 * 「已移除歌曲」列表行：墓碑时间 + 曲目原数据（曲目行仍在 `tracks`，只是被
 * [HiddenTrackEntity] 挡住不出现在曲库）。
 */
data class HiddenTrackRow(
    @Embedded val track: TrackEntity,
    val removedAt: Long,
)
