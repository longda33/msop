package com.primuse.lite.core.model

/**
 * 播放列表。
 *
 * [trackCount] 与 [coverUri] 是列表页直接要展示的聚合值，由数据库一次查出，
 * 避免 UI 为每个歌单再查一次曲目。
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val coverUri: String? = null,
    val createdAt: Long = 0L,
)
