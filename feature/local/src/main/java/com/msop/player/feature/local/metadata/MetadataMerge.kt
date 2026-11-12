package com.msop.player.feature.local.metadata

import com.msop.player.core.model.Track

/**
 * 把文件里的标签合并进已有曲目。
 *
 * **只补空缺，不覆盖已有值**。原因：MediaStore 那份数据是系统解析过的，可信度不低于
 * 我们再读一遍；而 SAF 那份的字段本来就是空的，正好被填上。若反过来无脑覆盖，
 * MediaStore 里精心整理过的专辑名可能被文件里拼错的标签冲掉。
 *
 * 唯一的例外是标题：SAF/MediaStore 拿不到 title 时会用文件名兜底，这种兜底值
 * 应该被标签里的真标题替换。判断方式见 [isFileNameFallback]。
 */
fun Track.mergedWith(metadata: TrackMetadata): Track {
    if (metadata.isEmpty) return this

    return copy(
        title = mergedTitle(metadata.title),
        artist = artist ?: metadata.artist,
        album = album ?: metadata.album,
        year = year ?: metadata.year,
        trackNo = trackNo ?: metadata.trackNo,
        discNo = discNo ?: metadata.discNo,
        durationMs = if (durationMs > 0L) durationMs else metadata.durationMs,
    )
}

private fun Track.mergedTitle(fromTag: String?): String {
    if (fromTag == null) return title
    return if (isFileNameFallback()) fromTag else title
}

/**
 * 当前标题是不是「文件名兜底」。
 *
 * `MediaStoreTrackSource` 在 `TITLE` 为空时用 `DISPLAY_NAME` 去扩展名顶上，
 * `SafTrackSource` 则一直这么干。这种情况标签里的标题一定更准确。
 */
private fun Track.isFileNameFallback(): Boolean {
    val fileName = localPath
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotEmpty() }
        ?: return false
    return title == fileName
}
