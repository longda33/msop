package com.msop.player.desktop

import com.msop.player.feature.lyrics.Lyrics

/**
 * 悬浮歌词两行文本的纯计算（P7-08）。抽出来是为了能单测，不依赖 Android/View。
 *
 * 规则：
 * - 没有歌词：第一行歌名，第二行艺术家；
 * - 有同步歌词但还没到第一行：第一行歌名，第二行第一句（给用户一个预告）；
 * - 同步歌词：当前行 + 下一行；最后一行时第二行为空；
 * - 静态歌词：前两句。
 */
object DesktopLyricsText {

    data class Content(val current: String, val next: String)

    fun at(
        title: String,
        artist: String?,
        lyrics: Lyrics?,
        positionMs: Long,
    ): Content {
        if (lyrics == null || lyrics.isEmpty) {
            return Content(title, artist.orEmpty())
        }

        if (!lyrics.synced) {
            return Content(
                current = lyrics.lines.first().text,
                next = lyrics.lines.getOrNull(1)?.text.orEmpty(),
            )
        }

        val index = lyrics.indexAt(positionMs)
        if (index < 0) {
            return Content(
                current = title,
                next = lyrics.lines.first().text,
            )
        }
        return Content(
            current = lyrics.lines[index].text,
            next = lyrics.lines.getOrNull(index + 1)?.text.orEmpty(),
        )
    }
}
