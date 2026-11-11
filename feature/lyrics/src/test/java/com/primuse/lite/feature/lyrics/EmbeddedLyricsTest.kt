package com.primuse.lite.feature.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class EmbeddedLyricsTest {

    // ------------------------------------------------------------ ID3 USLT

    @Test
    fun `ID3v2_3 ISO-8859-1 USLT`() {
        val tag = id3v23(
            "USLT" to uslt(encoding = 0, language = "eng", text = "Hello lyrics", charset = Charsets.ISO_8859_1),
        )

        assertEquals("Hello lyrics", extract(tag))
    }

    @Test
    fun `ID3v2_3 UTF-16 USLT`() {
        val tag = id3v23(
            "USLT" to uslt(encoding = 1, language = "chi", text = "第一行\n第二行", charset = Charsets.UTF_16),
        )

        assertEquals("第一行\n第二行", extract(tag))
    }

    @Test
    fun `ID3v2_4 UTF-8 USLT`() {
        val tag = id3v24(
            "USLT" to uslt(encoding = 3, language = "chi", text = "[00:01.00]第一行", charset = Charsets.UTF_8),
        )

        assertEquals("[00:01.00]第一行", extract(tag))
    }

    @Test
    fun `USLT 之前有其他帧也能找到`() {
        val tag = id3v23(
            "TIT2" to byteArrayOf(0, 'T'.code.toByte(), 'i'.code.toByte(), 't'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte()),
            "USLT" to uslt(encoding = 0, language = "eng", text = "lyrics", charset = Charsets.ISO_8859_1),
        )

        assertEquals("lyrics", extract(tag))
    }

    @Test
    fun `UTF-16 空描述只写一个 0 也能读`() {
        // 不少标签器把空描述固定写成单字节 0，后面直接跟歌词的 BOM
        val body = byteArrayOf(1) + "chi".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0) + "单行歌词".toByteArray(Charsets.UTF_16)
        val tag = id3v23("USLT" to body)

        assertEquals("单行歌词", extract(tag))
    }

    @Test
    fun `USLT 内容描述被跳过`() {
        val body = byteArrayOf(0) + "eng".toByteArray() + "desc".toByteArray() + byteArrayOf(0) +
            "actual lyrics".toByteArray(Charsets.ISO_8859_1)
        val tag = id3v23("USLT" to body)

        assertEquals("actual lyrics", extract(tag))
    }

    @Test
    fun `没有 USLT 返回 null`() {
        val tag = id3v23("TIT2" to byteArrayOf(0, 'x'.code.toByte()))

        assertNull(extract(tag))
    }

    @Test
    fun `ID3v2_2 不解析`() {
        val tag = "ID3".toByteArray() + byteArrayOf(2, 0, 0) + synchsafe(0)

        assertNull(extract(tag))
    }

    // --------------------------------------------------------------- FLAC

    @Test
    fun `FLAC Vorbis Comment 读取 LYRICS`() {
        val file = flac(
            4 to vorbis("ARTIST=Someone", "LYRICS=[00:01.00]第一行\n[00:02.00]第二行"),
        )

        assertEquals("[00:01.00]第一行\n[00:02.00]第二行", extract(file))
    }

    @Test
    fun `FLAC 跳过其他元数据块`() {
        val file = flac(
            0 to ByteArray(8),
            4 to vorbis("LYRICS=line"),
        )

        assertEquals("line", extract(file))
    }

    @Test
    fun `FLAC 没有 LYRICS 返回 null`() {
        val file = flac(4 to vorbis("ARTIST=Someone", "TITLE=Song"))

        assertNull(extract(file))
    }

    // ---------------------------------------------------------------- 边界

    @Test
    fun `空输入与损坏数据返回 null`() {
        assertNull(extract(ByteArray(0)))
        assertNull(extract(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(extract("fLaC".toByteArray()))
    }

    @Test
    fun `纯文本歌词按行拆分且不参与高亮`() {
        val lyrics = Lyrics.plain("  第一行  \n\n  第二行\n第三行  ")

        assertEquals(listOf("第一行", "第二行", "第三行"), lyrics.lines.map { it.text })
        assertTrue(!lyrics.synced)
        assertEquals(-1, lyrics.indexAt(999_999))
    }

    // ------------------------------------------------------------- 构造工具

    private fun extract(bytes: ByteArray): String? =
        EmbeddedLyrics.extract(ByteArrayInputStream(bytes))

    private fun uslt(encoding: Int, language: String, text: String, charset: Charset): ByteArray {
        // UTF-16 的内容描述终止符是两个 0 字节，其余编码是一个
        val terminator = if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)
        return byteArrayOf(encoding.toByte()) +
            language.toByteArray(Charsets.ISO_8859_1) +
            terminator +
            text.toByteArray(charset)
    }

    private fun id3v23(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, (id, data) ->
            acc + id.toByteArray(Charsets.ISO_8859_1) + be32(data.size) + byteArrayOf(0, 0) + data
        }
        return "ID3".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(3, 0, 0) +
            synchsafe(body.size) +
            body
    }

    private fun id3v24(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, (id, data) ->
            acc + id.toByteArray(Charsets.ISO_8859_1) + synchsafe(data.size) + byteArrayOf(0, 0) + data
        }
        return "ID3".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(4, 0, 0) +
            synchsafe(body.size) +
            body
    }

    private fun flac(vararg blocks: Pair<Int, ByteArray>): ByteArray {
        var bytes = "fLaC".toByteArray(Charsets.ISO_8859_1)
        blocks.forEachIndexed { index, (type, data) ->
            val last = if (index == blocks.lastIndex) 0x80 else 0x00
            bytes += byteArrayOf((last or type).toByte())
            bytes += byteArrayOf(
                ((data.size shr 16) and 0xFF).toByte(),
                ((data.size shr 8) and 0xFF).toByte(),
                (data.size and 0xFF).toByte(),
            )
            bytes += data
        }
        return bytes
    }

    private fun vorbis(vararg entries: String): ByteArray {
        val vendor = "PrimuseTest".toByteArray(Charsets.UTF_8)
        var body = le32(vendor.size) + vendor + le32(entries.size)
        for (entry in entries) {
            val bytes = entry.toByteArray(Charsets.UTF_8)
            body += le32(bytes.size) + bytes
        }
        return body
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun be32(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
