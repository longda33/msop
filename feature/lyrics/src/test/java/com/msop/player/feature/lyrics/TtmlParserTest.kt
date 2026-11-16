package com.msop.player.feature.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTML 解析测试（P7-05）。
 * 全部是纯 JVM 字符串，不依赖 Android 资源；DOM 走 JDK 自带实现。
 */
class TtmlParserTest {

    private fun ttml(body: String, attributes: String = ""): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:ttp="http://www.w3.org/ns/ttml#parameter"
            xml:lang="zh" $attributes>
          <body>
            <div>
              $body
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun `命名空间根节点与普通行`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """
                <p begin="00:00:01.500" end="00:00:04.000">第一行</p>
                <p begin="00:00:05.000" end="00:00:08.000">第二行</p>
                """.trimIndent(),
            ),
        )

        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.synced)
        assertEquals(1_500L, lyrics.lines[0].timeMs)
        assertEquals(4_000L, lyrics.lines[0].endMs)
        assertEquals("第一行", lyrics.lines[0].text)
        assertEquals(5_000L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `嵌套 span 产生逐字时间且不插空格`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """
                <p begin="0s" end="4s">
                  <span begin="1s">从</span><span begin="2s">前</span><span begin="3s">以后</span>
                </p>
                """.trimIndent(),
            ),
        )

        val line = lyrics.lines.single()
        assertEquals("从前以后", line.text)
        assertEquals("", line.wordJoiner)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), line.words.map { it.timeMs })
        assertEquals(listOf("从", "前", "以后"), line.words.map { it.text })
    }

    @Test
    fun `span 外的空白会保留成词间空格`() {
        val lyrics = TtmlParser.parse(
            ttml("""<p begin="0s"><span begin="0s">Hello</span> <span begin="1s">world</span></p>"""),
        )

        val line = lyrics.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf("Hello ", "world"), line.words.map { it.text })
    }

    @Test
    fun `帧时间按 frameRate 换算`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """<p begin="00:00:02:10" end="00:00:03:00">帧</p>""",
                attributes = """ttp:frameRate="25"""",
            ),
        )

        assertEquals(2_400L, lyrics.lines.single().timeMs)
        assertEquals(3_000L, lyrics.lines.single().endMs)
    }

    @Test
    fun `tick 时间按 tickRate 换算`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """<p begin="1500t" end="2500t">tick</p>""",
                attributes = """ttp:tickRate="1000"""",
            ),
        )

        assertEquals(1_500L, lyrics.lines.single().timeMs)
        assertEquals(2_500L, lyrics.lines.single().endMs)
    }

    @Test
    fun `没有 end 时用下一行 begin`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """
                <p begin="1s">第一行</p>
                <p begin="3s">第二行</p>
                """.trimIndent(),
            ),
        )

        assertEquals(3_000L, lyrics.lines[0].endMs)
        assertNull(lyrics.lines[1].endMs)
    }

    @Test
    fun `dur 换算成结束时间`() {
        val lyrics = TtmlParser.parse(
            ttml("""<p begin="1s" dur="2.5s">有 dur</p>"""),
        )

        assertEquals(1_000L, lyrics.lines.single().timeMs)
        assertEquals(3_500L, lyrics.lines.single().endMs)
    }

    @Test
    fun `缺少 begin 的段落退化成静态歌词`() {
        val lyrics = TtmlParser.parse(
            ttml(
                """
                <p>没有时间</p>
                <p begin="2s">有时间</p>
                """.trimIndent(),
            ),
        )

        assertFalse(lyrics.synced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(0L, lyrics.lines[0].timeMs)
        assertEquals("没有时间", lyrics.lines[0].text)
        assertEquals(2_000L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `没有任何时间轴时仍能展示文本`() {
        val lyrics = TtmlParser.parse(ttml("""<p>只有文本</p>"""))

        assertFalse(lyrics.synced)
        assertEquals("只有文本", lyrics.lines.single().text)
    }

    @Test
    fun `br 保留行内换行`() {
        val lyrics = TtmlParser.parse(ttml("""<p begin="1s">a<br/>b</p>"""))

        assertEquals("a\nb", lyrics.lines.single().text)
    }

    @Test
    fun `省略小时的 mm ss 写法也认`() {
        val lyrics = TtmlParser.parse(ttml("""<p begin="01:02.500" end="01:03">mmss</p>"""))

        assertEquals(62_500L, lyrics.lines.single().timeMs)
    }

    @Test
    fun `坏 XML 不会崩`() {
        val lyrics = TtmlParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s">没闭合""")

        assertTrue(lyrics.isEmpty)
    }

    @Test
    fun `looksLikeTtml 只认 TTML`() {
        assertTrue(TtmlParser.looksLikeTtml(ttml("""<p begin="1s">x</p>""")))
        assertTrue(TtmlParser.looksLikeTtml("""<tt begin="1s">x</tt>"""))
        assertFalse(TtmlParser.looksLikeTtml("普通 LRC 文本"))
        assertFalse(TtmlParser.looksLikeTtml("""<html><body><tt>不是歌词</tt></body></html>"""))
    }
}
