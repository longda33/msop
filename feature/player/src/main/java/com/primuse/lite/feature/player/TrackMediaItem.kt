package com.primuse.lite.feature.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.primuse.lite.core.model.Track

/**
 * 把统一曲目模型转成 ExoPlayer 的播放项。
 *
 * `mediaId` 用 `Track.id`：控制器、通知、以后的状态恢复都靠它把播放项对回曲库记录，
 * 不要用 `uri`（远程地址会带一次性签名，每次都不一样）。
 *
 * 元数据是给**通知栏和锁屏**看的，不是给播放页看的——播放页直接用 Track。
 */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUri?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()
