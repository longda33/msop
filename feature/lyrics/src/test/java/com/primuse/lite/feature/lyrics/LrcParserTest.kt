package com.primuse.lite.feature.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `百分秒时间标签`() {
        val lyrics = LrcParser.parse("[00:01.50]第一行\n[01:02.25]第二行")

        assertEquals(2, lyrics.lines.size)
        assertEquals(1_500L, lyrics.lines[0].timeMs)
        assertEquals("第一行", lyrics.lines[0].text)
        assertEquals(62_250L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `毫秒与整秒写法都认`() {
        val lyrics = LrcParser.parse("[00:01.234]毫秒\n[00:02]整秒")

        assertEquals(1_234L, lyrics.lines[0].timeMs)
        assertEquals(2_000L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `一行多个时间点会展开成多行`() {
        val lyrics = LrcParser.parse("[00:10.00][00:30.00]副歌")

        assertEquals(2, lyrics.lines.size)
        assertEquals(10_000L, lyrics.lines[0].timeMs)
        assertEquals(30_000L, lyrics.lines[1].timeMs)
        assertEquals("副歌", lyrics.lines[0].text)
        assertEquals("副歌", lyrics.lines[1].text)
    }

    @Test
    fun `offset 标签整体平移`() {
        val ahead = LrcParser.parse("[offset:500]\n[00:10.00]晚半秒")
        val behind = LrcParser.parse("[offset:-500]\n[00:10.00]早半秒")

        assertEquals(10_500L, ahead.lines[0].timeMs)
        assertEquals(9_500L, behind.lines[0].timeMs)
    }

    @Test
    fun `offset 平移不会出现负数时间`() {
        val lyrics = LrcParser.parse("[offset:-5000]\n[00:01.00]开头")

        assertEquals(0L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `元信息标签与无时间标签的行被忽略`() {
        val lyrics = LrcParser.parse(
            """
            [ti:晴天]
            [ar:周杰伦]
            [al:叶惠美]
            [by:someone]
            这一行没有时间标签
            [00:12.00]真正的歌词
            """.trimIndent(),
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals("真正的歌词", lyrics.lines[0].text)
    }

    @Test
    fun `空文本行被跳过`() {
        // 间奏占位行如果保留，高亮会停在空行上，看起来像卡住
        val lyrics = LrcParser.parse("[00:01.00]\n[00:02.00]   \n[00:03.00]有内容")

        assertEquals(1, lyrics.lines.size)
        assertEquals("有内容", lyrics.lines[0].text)
    }

    @Test
    fun `非法时间标签被跳过`() {
        // 秒数 >= 60 视为脏数据
        val lyrics = LrcParser.parse("[00:75.00]假的\n[00:05.00]真的")

        assertEquals(1, lyrics.lines.size)
        assertEquals("真的", lyrics.lines[0].text)
    }

    @Test
    fun `乱序输入按时间排序`() {
        val lyrics = LrcParser.parse("[00:30.00]第三\n[00:10.00]第一\n[00:20.00]第二")

        assertEquals(listOf("第一", "第二", "第三"), lyrics.lines.map { it.text })
    }

    @Test
    fun `空输入得到空歌词`() {
        assertTrue(LrcParser.parse("").isEmpty)
        assertTrue(LrcParser.parse("   \n\n").isEmpty)
        assertTrue(LrcParser.parse("[ti:只有标签]").isEmpty)
    }

    @Test
    fun `BOM 不影响第一行解析`() {
        val lyrics = LrcParser.parse("\uFEFF[00:01.00]第一行")

        assertEquals(1, lyrics.lines.size)
        assertEquals("第一行", lyrics.lines[0].text)
    }

    @Test
    fun `GBK 编码回退`() {
        // "你好" 的 GBK 字节，按 UTF-8 读会得到替换字符
        val gbk = byteArrayOf(0xC4.toByte(), 0xE3.toByte(), 0xBA.toByte(), 0xC3.toByte())

        assertEquals("你好", LrcParser.decode(gbk))
    }

    @Test
    fun `UTF-8 正常时不走回退`() {
        val bytes = "[00:01.00]中文歌词".toByteArray(Charsets.UTF_8)

        assertEquals("[00:01.00]中文歌词", LrcParser.decode(bytes))
    }

    @Test
    fun `indexAt 返回当前行`() {
        val lyrics = LrcParser.parse("[00:00.00]A\n[00:10.00]B\n[00:20.00]C")

        assertEquals(-1, lyrics.indexAt(-1))
        assertEquals(0, lyrics.indexAt(0))
        assertEquals(0, lyrics.indexAt(9_999))
        assertEquals(1, lyrics.indexAt(10_000))
        assertEquals(1, lyrics.indexAt(19_999))
        assertEquals(2, lyrics.indexAt(999_999))
    }

    @Test
    fun `indexAt 在空歌词上返回负一`() {
        assertFalse(LrcParser.parse("").indexAt(1_000) >= 0)
    }
}
