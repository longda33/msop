package com.msop.player.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「已移除歌曲」墓碑表（2026-11-16 晚用户需求）。
 *
 * 为什么不是直接删 `tracks` 行、也不是在 `tracks` 上加布尔列：
 * 1. 用户要求「移除的歌曲重新扫描不要恢复」——扫描是 `@Upsert`，若只在实体上加
 *    `removed` 标记，重扫时构造的实体默认 `removed = false` 会把标记冲掉；
 *    独立表不参与 upsert，天然不会被扫描覆盖。
 * 2. 用户要求「已移除歌曲界面可恢复」——恢复就是删掉这一行，元数据还在 `tracks`，
 *    界面不需要重扫就能显示歌名/歌手/封面。
 *
 * 曲目行本身保留（不删），所以外键、封面缓存、播放状态都不受影响。
 */
@Entity(tableName = "hidden_tracks")
data class HiddenTrackEntity(
    @PrimaryKey val id: String,
    val removedAt: Long,
)
