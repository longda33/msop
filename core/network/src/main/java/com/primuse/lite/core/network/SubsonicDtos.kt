package com.primuse.lite.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenList v4.2.9 的 Subsonic 响应结构，字段以 2026-11-11 实测为准。
 *
 * 与原生 REST 最大的不同：**错误也是 HTTP 200**，必须看
 * `subsonic-response.status` 和 `error.code`（40=账号错、50=无权限、70=未找到）。
 * 根节点的 key 带连字符，必须用 [SerialName]。
 */
@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String = "failed",
    val version: String = "",
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: SubsonicError? = null,
    @SerialName("searchResult3") val searchResult3: SubsonicSearchResult? = null,
)

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class SubsonicSearchResult(
    val offset: Int = 0,
    val total: Int = 0,
    val song: List<SubsonicSong>? = null,
)

@Serializable
data class SubsonicSong(
    val id: String,
    val parent: String? = null,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    @SerialName("coverArt") val coverArt: String? = null,
    val size: Long = 0L,
    /** 形如 `audio/flac`，可直接用作 MediaItem 的 mimeType。 */
    @SerialName("contentType") val contentType: String? = null,
    val suffix: String? = null,
    /** 服务器内绝对路径，实测与 `/api/fs/get` 的路径**不通用**，只用来取文件名。 */
    val path: String? = null,
    @SerialName("albumId") val albumId: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    /** 标准字段，但这台服务器不返回（恒为 null）。 */
    val duration: Long? = null,
)
