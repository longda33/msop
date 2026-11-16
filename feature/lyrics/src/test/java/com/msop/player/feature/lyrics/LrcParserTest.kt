package com.msop.player.feature.lyrics

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

    // ---------------------------------------------------------------- 方括号逐字
    // 2026-11-16 BUGFIX：酷狗/QQ 的「逐字 LRC」每个字自带一个 [mm:ss.xx]，
    // 旧实现把每个标签都当一行、文本取整句 → 同一句被复制 20 次（用户报的重复行）。

    @Test
    fun `方括号逐字整行合成一行而不是复制多行`() {
        val raw = "[01:44.23]哭[01:44.55]着[01:44.72]说[01:45.21]欠[01:45.60]我[01:45.81]的"

        val lyrics = LrcParser.parse(raw)

        assertEquals(1, lyrics.lines.size)
        assertEquals("哭着说欠我的", lyrics.lines[0].text)
        assertEquals(104_230L, lyrics.lines[0].timeMs)
        assertEquals(6, lyrics.lines[0].words.size)
        assertEquals("哭", lyrics.lines[0].words.first().text)
        assertEquals(104_550L, lyrics.lines[0].words[1].timeMs)
        // 中文逐字之间不能插空格，否则一个字一段、整行被撑开
        assertEquals("", lyrics.lines[0].wordJoiner)
    }

    @Test
    fun `多行逐字歌词每句只出现一次`() {
        val raw = """
            [00:23.45]还[00:23.79]记[00:24.08]得
            [00:25.60]陪[00:26.07]一[00:26.15]个
            [00:28.74]青[00:29.04]春[00:29.28]来
        """.trimIndent()

        val lyrics = LrcParser.parse(raw)

        assertEquals(3, lyrics.lines.size)
        assertEquals(listOf("还记得", "陪一个", "青春来"), lyrics.lines.map { it.text })
    }

    @Test
    fun `行首多个方括号仍是同一句的多个时间点`() {
        // 方括号之间没有文字 → 副歌复用，仍要拆成多行（这是正常语义，不是重复 bug）
        val lyrics = LrcParser.parse("[00:10.00][00:20.00]副歌")

        assertEquals(2, lyrics.lines.size)
        assertEquals(listOf("副歌", "副歌"), lyrics.lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L), lyrics.lines.map { it.timeMs })
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `方括号逐字的文字拼接保留原始空格`() {
        val lyrics = LrcParser.parse("[00:09.61]吉[00:09.75]他[00:09.88]：[00:10.01]老[00:10.14]田")

        assertEquals("吉他：老田", lyrics.lines[0].text)
    }

    @Test
    fun `逐字标签解析为 words`() {
        val lyrics = LrcParser.parse("[00:12.00]<00:12.00>被 <00:13.50>命 <00:15.00>中")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals(3, line.words.size)
        assertEquals("被", line.words[0].text)
        assertEquals("命", line.words[1].text)
        assertEquals("中", line.words[2].text)
        assertEquals(12_000L, line.words[0].timeMs)
        assertEquals(15_000L, line.words[2].timeMs)
        // 整行文本仍可用（无时间轴时退化展示）
        assertEquals("被 命 中", line.text)
        assertTrue(lyrics.synced)
    }

    @Test
    fun `无逐字标签时 words 为空且向后兼容`() {
        val lyrics = LrcParser.parse("[00:01.00]普通歌词")

        assertEquals(1, lyrics.lines.size)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }

    @Test
    fun `offset 同样作用于逐字时间`() {
        val lyrics = LrcParser.parse("[offset:500]\n[00:10.00]<00:10.00>晚 <00:11.00>半秒")

        val line = lyrics.lines[0]
        assertEquals(10_500L, line.words[0].timeMs)
        assertEquals(11_500L, line.words[1].timeMs)
    }
}
