package com.msop.player.core.model

/** 音源类型。本地与远程共用同一套模型，靠这个字段区分。 */
enum class SourceType {
    LOCAL,
    OPENLIST,
}

/**
 * 统一曲目模型。
 *
 * ID 规则：
 * - 本地：`local:` + sha1(绝对路径)
 * - 远程：`subsonic:` + songId
 *
 * 详见 AGENTS.md §5 与 docs/02-architecture.md §4。
 */
data class Track(
    val id: String,
    val source: SourceType,
    val uri: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val year: Int? = null,
    val coverUri: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val localPath: String? = null,
    val lastModified: Long = 0L,
    val addedAt: Long = 0L,
)

data class Album(
    val id: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val trackCount: Int,
    val source: SourceType,
)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val source: SourceType,
)

/** 把毫秒格式化成 m:ss，用于列表与进度条。 */
fun Long.toDurationLabel(): String {
    if (this <= 0L) return "--:--"
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
