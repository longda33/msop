package com.msop.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.msop.player.core.model.SourceType

/**
 * 曲目表。本地与远程共用一张表，靠 [source] 区分。
 *
 * 索引说明：
 * - `source`：来源筛选（全部/本机/云端）必走
 * - `album` / `artist`：分组浏览必走
 * - `lastModified`：增量扫描按修改时间比较，必走
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("source"),
        Index("album"),
        Index("artist"),
        Index("lastModified"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: SourceType,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val durationMs: Long,
    val trackNo: Int?,
    val discNo: Int?,
    val year: Int?,
    val coverUri: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val localPath: String?,
    val lastModified: Long,
    val addedAt: Long,
)
