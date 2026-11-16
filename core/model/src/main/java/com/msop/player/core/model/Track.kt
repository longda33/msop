package com.msop.player.core.model

/** 音源类型。本地与远程共用同一套模型，靠这个字段区分。 */
enum class SourceType {
    LOCAL,
    OPENLIST,
    /** P7-07：独立第三音源，单账号，直接走 WebDAV。 */
    WEBDAV,
}

/**
 * 统一曲目模型。
 *
 * ID 规则：
 * - 本地：`local:` + sha1(绝对路径)
 * - OpenList：`openlist:` + sha1(服务器相对路径)
 * - WebDAV：`webdav:` + sha1(根目录相对路径)
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
    /** 平均码率（kbps）。本地由 MediaExtractor 或文件大小/时长估算；远程来自 Subsonic。 */
    val bitrateKbps: Int? = null,
    /** 采样率（Hz），如 44100 / 96000。 */
    val sampleRateHz: Int? = null,
    /** 位深，如 16 / 24；有损格式没有这个字段。 */
    val bitDepth: Int? = null,
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

/**
 * 音质摘要，如 `24bit / 96kHz / 2304kbps`。字段全缺时返回 null，文件信息弹窗据此隐藏整行。
 *
 * `0` 表示「读过但拿不到」（有损格式没有位深、部分容器不报码率），按缺省处理。
 */
fun Track.audioQualityLabel(): String? {
    val parts = mutableListOf<String>()
    bitDepth?.takeIf { it > 0 }?.let { parts += "${it}bit" }
    sampleRateHz?.takeIf { it > 0 }?.let { hz ->
        parts += if (hz % 1000 == 0) {
            "${hz / 1000}kHz"
        } else {
            String.format(java.util.Locale.US, "%.1fkHz", hz / 1000.0)
        }
    }
    bitrateKbps?.takeIf { it > 0 }?.let { parts += "${it}kbps" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}
