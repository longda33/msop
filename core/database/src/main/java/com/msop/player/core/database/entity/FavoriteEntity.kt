package com.msop.player.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「收藏歌曲」——歌单 Tab 顶部置顶的虚拟条目（2026-11-16 用户需求）。
 *
 * 为什么不塞进 `playlists` 表：收藏要跟着曲目一起被清理（曲目被移除曲库后不该留下孤儿），
 * 而且它不能被重命名/删除，语义和用户自建歌单不同。单独一张表最简单，也不用改歌单的排序逻辑。
 */
@Entity(
    tableName = "favorites",
    indices = [Index("trackId")],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long,
)
