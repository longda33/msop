package com.msop.player.feature.remote

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.WebDavItem
import com.msop.player.core.network.WebDavUrl

/**
 * WebDAV 目录项 → 统一 [Track]（P7-07）。纯函数，可单测。
 *
 * 三条约定与 OpenList 一致：
 * 1. `uri` 存稳定的 `webdav:/根目录相对路径`，**不存真实 URL**；
 *    服务器地址或根路径改了，歌单与缓存 key 仍然对得上。
 * 2. `lastModified` / `addedAt` 单位是秒。
 * 3. 专辑名取父目录；根目录下的文件用「WebDAV」兜底，不能显示成 OpenList。
 */
object WebDavTrackMapper {

    fun toTrack(item: WebDavItem, nowSeconds: Long): Track =
        toTrack(item, profileId = null, nowSeconds = nowSeconds)

    fun toTrack(item: WebDavItem, profileId: String?, nowSeconds: Long): Track {
        val path = WebDavUrl.normalizePath(item.path)
        return Track(
            id = if (profileId.isNullOrBlank()) {
                RemoteIdFactory.webDavId(path)
            } else {
                RemoteIdFactory.webDavId(profileId, path)
            },
            source = SourceType.WEBDAV,
            uri = if (profileId.isNullOrBlank()) {
                WebDavUrl.internalUri(path)
            } else {
                WebDavUrl.internalUri(profileId, path)
            },
            title = RemoteIdFactory.displayTitle(item.name),
            artist = null,
            album = RemoteIdFactory.webDavAlbumName(path),
            albumId = null,
            durationMs = 0L,
            trackNo = null,
            discNo = null,
            year = null,
            coverUri = null,
            sizeBytes = item.sizeBytes,
            // 扩展名映射优先：部分服务器对音频返回 application/octet-stream，
            // 交给 Media3 会识别不出格式。
            mimeType = RemoteIdFactory.mimeType(item.name) ?: item.mimeType,
            localPath = null,
            lastModified = item.lastModifiedSeconds,
            addedAt = nowSeconds,
        )
    }
}
