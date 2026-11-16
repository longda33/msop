package com.msop.player.desktop

import com.msop.player.feature.lyrics.LyricLine
import com.msop.player.feature.lyrics.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** 悬浮歌词两行文本（P7-08）。纯 JVM 单测。 */
class DesktopLyricsTextTest {

    private fun synced() = Lyrics(
        listOf(
            LyricLine(1_000L, "第一句"),
            LyricLine(5_000L, "第二句"),
            LyricLine(9_000L, "第三句"),
        ),
    )

    @Test
    fun `没有歌词时显示歌名与艺术家`() {
        val content = DesktopLyricsText.at("歌名", "歌手", null, 0L)
        assertEquals("歌名", content.current)
        assertEquals("歌手", content.next)
    }

    @Test
    fun `艺术家为空时第二行为空字符串`() {
        val content = DesktopLyricsText.at("歌名", null, null, 0L)
        assertEquals("歌名", content.current)
        assertEquals("", content.next)
    }

    @Test
    fun `还没到第一句时显示歌名并预告第一句`() {
        val content = DesktopLyricsText.at("歌名", "歌手", synced(), 0L)
        assertEquals("歌名", content.current)
        assertEquals("第一句", content.next)
    }

    @Test
    fun `同步歌词按位置显示当前行与下一行`() {
        val middle = DesktopLyricsText.at("歌名", "歌手", synced(), 5_000L)
        assertEquals("第二句", middle.current)
        assertEquals("第三句", middle.next)

        val last = DesktopLyricsText.at("歌名", "歌手", synced(), 9_500L)
        assertEquals("第三句", last.current)
        assertEquals("", last.next)
    }

    @Test
    fun `静态歌词显示前两句`() {
        val lyrics = Lyrics(
            listOf(LyricLine(0L, "甲"), LyricLine(0L, "乙"), LyricLine(0L, "丙")),
            synced = false,
        )
        val content = DesktopLyricsText.at("歌名", "歌手", lyrics, 99_999L)
        assertEquals("甲", content.current)
        assertEquals("乙", content.next)
    }

    @Test
    fun `空歌词对象按无歌词处理`() {
        val content = DesktopLyricsText.at("歌名", "歌手", Lyrics(emptyList()), 1_000L)
        assertEquals("歌名", content.current)
        assertEquals("歌手", content.next)
    }
}
