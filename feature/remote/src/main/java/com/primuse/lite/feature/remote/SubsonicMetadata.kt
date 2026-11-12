package com.primuse.lite.feature.remote

import com.primuse.lite.core.model.Track
import com.primuse.lite.core.network.SubsonicSong

/**
 * 用 Subsonic 的歌曲元数据补全原生 REST 同步出来的 [Track]。纯函数，可单测。
 *
 * 为什么按**文件名**匹配而不是路径：实测 Subsonic 的 `path` 是服务器绝对路径
 * （`/od-20200439/musicweb/xxx.flac`），而原生 REST 的路径是相对用户根目录的
 * （`/xxx.flac`），两者不通用；但末段文件名完全一致，且同一个音乐库里不应有
 * 同名文件（真有就取第一个，不做猜测）。
 *
 * 只**覆盖**能确定的字段，绝不把已有值清成 null——这样即使 Subsonic 挂了，
 * 原生 REST 的同步结果依然完整可用。
 */
object SubsonicMetadata {

    data class SongMeta(
        val fileName: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val albumId: String?,
        val mimeType: String?,
        val sizeBytes: Long,
        val coverId: String?,
    )

    fun fileNameOf(path: String?): String? =
        path?.substringAfterLast('/')?.trim()?.takeIf { it.isNotBlank() }

    /** Track 的 `uri` 是 `openlist:/路径`，末段就是文件名。 */
    fun fileNameOfTrack(track: Track): String? = fileNameOf(track.uri)

    fun toMeta(song: SubsonicSong): SongMeta? {
        val fileName = fileNameOf(song.path) ?: return null
        return SongMeta(
            fileName = fileName,
            title = song.title,
            artist = song.artist?.takeIf { it.isNotBlank() },
            album = song.album?.takeIf { it.isNotBlank() },
            albumId = song.albumId?.takeIf { it.isNotBlank() },
            // contentType 实测形如 audio/flac；缺失时按扩展名兜底
            mimeType = song.contentType?.takeIf { it.isNotBlank() }
                ?: RemoteIdFactory.mimeType(fileName),
            sizeBytes = song.size,
            coverId = song.coverArt?.takeIf { it.isNotBlank() },
        )
    }

    /** 文件名 → 元数据。同名只保留第一个，避免随机覆盖。 */
    fun index(songs: List<SubsonicSong>): Map<String, SongMeta> = buildMap {
        songs.forEach { song ->
            toMeta(song)?.let { meta -> putIfAbsent(meta.fileName, meta) }
        }
    }

    /**
     * @param coverUrl 封面地址构造函数（需要服务端配置），失败时保留原 coverUri
     */
    fun enrich(track: Track, meta: SongMeta?, coverUrl: (String) -> String): Track {
        if (meta == null) return track
        return track.copy(
            title = meta.title.ifBlank { track.title },
            artist = meta.artist ?: track.artist,
            album = meta.album ?: track.album,
            albumId = meta.albumId ?: track.albumId,
            mimeType = meta.mimeType ?: track.mimeType,
            sizeBytes = if (meta.sizeBytes > 0L) meta.sizeBytes else track.sizeBytes,
            coverUri = meta.coverId
                ?.let { id -> runCatching { coverUrl(id) }.getOrNull() }
                ?: track.coverUri,
        )
    }
}
