package com.msop.player.core.network

/**
 * 云端曲目**内嵌歌词**探测（2026-11-17）。
 *
 * 此前 `LyricsRepository.readEmbedded()` 对 `source != LOCAL` 直接 return null，
 * 远程歌词等于是零实现。这里对播放直链发小范围 `Range` GET，解析：
 * - MP3 ID3v2 的 `USLT`（v2.2 是 `ULT`）非同步歌词帧；
 * - FLAC Vorbis Comment 的 `LYRICS` / `UNSYNCEDLYRICS` 字段；
 * - MP4/M4A 的 `©lyr` data 原子。
 *
 * 只读文件头（必要时按 ID3/FLAC 声明长度补一次，MP4 补尾部），不下载整首歌；
 * 解析失败返回 null，调用方继续显示「暂无歌词」，不影响播放。
 */
object RemoteLyricsProbe {

    private const val HEAD_BYTES = 512L * 1024L
    private const val MAX_TAG_BYTES = 4L * 1024L * 1024L
    private const val MAX_LYRICS_BYTES = 512 * 1024

    fun lyrics(url: String, headers: Map<String, String> = emptyMap()): String? {
        val head = RemoteRangeReader.range(url, headers, 0L, HEAD_BYTES) ?: return null

        id3Lyrics(head)?.let { return it }
        flacLyrics(head)?.let { return it }
        mp4Lyrics(head)?.let { return it }

        val id3Size = id3TagSize(head)
        if (id3Size != null && id3Size in (head.size + 1L)..MAX_TAG_BYTES) {
            RemoteRangeReader.range(url, headers, 0L, id3Size)?.let { return id3Lyrics(it) }
        }
        val flacEnd = flacVorbisEnd(head)
        if (flacEnd != null && flacEnd in (head.size + 1L)..MAX_TAG_BYTES) {
            RemoteRangeReader.range(url, headers, 0L, flacEnd)?.let { return flacLyrics(it) }
        }
        if (head.size >= HEAD_BYTES) {
            val tail = RemoteRangeReader.range(url, headers, null, HEAD_BYTES) ?: return null
            return mp4Lyrics(tail)
        }
        return null
    }

    // ---------------------------------------------------------------- ID3v2

    fun id3TagSize(b: ByteArray): Long? {
        if (b.size < 10 || !tag(b, 0, "ID3")) return null
        val size = synchsafe(b, 6)
        val footer = if ((b[5].toInt() and 0x10) != 0) 10L else 0L
        return 10L + size + footer
    }

    /** 公开给单测：从 ID3v2 字节里取第一首 USLT/ULT 歌词。 */
    fun id3Lyrics(b: ByteArray): String? {
        val total = id3TagSize(b) ?: return null
        val version = b[3].toInt() and 0xff
        val end = minOf(b.size, total.toInt())
        var i = 10
        while (i + (if (version == 2) 6 else 10) <= end) {
            val idLen = if (version == 2) 3 else 4
            val id = String(b, i, idLen, Charsets.US_ASCII)
            if (id.all { it == '\u0000' }) return null
            val frameSize = if (version == 2) {
                (b[i + 3].toLong() shl 16) or (b[i + 4].toLong() shl 8) or b[i + 5].toLong()
            } else if (version == 4) {
                synchsafe(b, i + 4)
            } else {
                readUInt32(b, i + 4)
            }
            val headerLen = if (version == 2) 6 else 10
            val dataStart = i + headerLen
            val dataEnd = minOf(end, dataStart + frameSize.toInt())
            if (frameSize <= 0L || dataStart >= dataEnd) return null
            if ((id == "USLT" || (version == 2 && id == "ULT")) && dataEnd <= b.size) {
                val bytes = b.copyOfRange(dataStart, dataEnd)
                lyricsFromUslt(bytes)?.let { return it }
            }
            i = dataEnd
        }
        return null
    }

    private fun lyricsFromUslt(data: ByteArray): String? {
        if (data.size < 7) return null
        val enc = data[0].toInt() and 0xff
        if (enc > 3) return null
        var pos = 4 // 3 字节语言码
        pos = skipTerminated(data, pos, enc) ?: return null // 描述
        if (pos >= data.size) return null
        return decode(data.copyOfRange(pos, data.size), enc)
    }

    private fun skipTerminated(b: ByteArray, from: Int, enc: Int): Int? {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < b.size) {
                if (b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i + 2
                i += 2
            }
            return null
        }
        var i = from
        while (i < b.size) {
            if (b[i] == 0.toByte()) return i + 1
            i++
        }
        return null
    }

    private fun decode(bytes: ByteArray, enc: Int): String? = try {
        when (enc) {
            0 -> String(bytes, Charsets.ISO_8859_1)
            1 -> when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() ->
                    String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() ->
                    String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
                else -> String(bytes, Charsets.UTF_16)
            }
            2 -> String(bytes, Charsets.UTF_16BE)
            3 -> String(bytes, Charsets.UTF_8)
            else -> null
        }?.takeIf { it.isNotBlank() && it.length <= MAX_LYRICS_BYTES }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- FLAC

    /** FLAC Vorbis Comment 块的结束位置（相对文件开头），没有则 null。 */
    fun flacVorbisEnd(b: ByteArray): Long? {
        if (b.size < 8 || !tag(b, 0, "fLaC")) return null
        var i = 4
        while (i + 4 <= b.size) {
            val type = b[i].toInt() and 0x7f
            val len = ((b[i + 1].toLong() and 0xff) shl 16) or
                ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
            if (type == 4) return i + 4L + len
            if (len <= 0L) return null
            i += 4 + len.toInt()
        }
        return null
    }

    /** 公开给单测：从 FLAC Vorbis Comment 里取 LYRICS。 */
    fun flacLyrics(b: ByteArray): String? {
        if (b.size < 8 || !tag(b, 0, "fLaC")) return null
        var i = 4
        while (i + 4 <= b.size) {
            val type = b[i].toInt() and 0x7f
            val len = ((b[i + 1].toLong() and 0xff) shl 16) or
                ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
            if (len <= 0L || i + 4L + len > b.size) return null
            val block = b.copyOfRange(i + 4, i + 4 + len.toInt())
            if (type == 4) return lyricsFromVorbis(block)
            i += 4 + len.toInt()
        }
        return null
    }

    private fun lyricsFromVorbis(b: ByteArray): String? {
        if (b.size < 8) return null
        var pos = 0
        val vendorLen = readUInt32(b, pos).toInt(); pos += 4
        if (vendorLen < 0 || pos + vendorLen > b.size) return null
        pos += vendorLen
        if (pos + 4 > b.size) return null
        // 32 位无符号数转 Int 后最高位为 1 会变负数；直接 repeat 会抛
        // IllegalArgumentException，被上层 runCatching 吞掉后表现为「有歌词却不显示」。
        val count = readUInt32(b, pos); pos += 4
        if (count <= 0L) return null
        var lyrics: String? = null
        repeat(minOf(count, 4096L).toInt()) {
            if (pos + 4 > b.size) return null
            val len = readUInt32(b, pos).toInt(); pos += 4
            if (len < 0 || pos + len > b.size) return null
            val comment = String(b, pos, len, Charsets.UTF_8)
            pos += len
            if (lyrics != null) return@repeat
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                    lyrics = comment.substring(eq + 1)
                }
            }
        }
        return lyrics?.takeIf { it.isNotBlank() && it.length <= MAX_LYRICS_BYTES }
    }

    // ---------------------------------------------------------------- MP4

    /** 公开给单测：从 MP4 的 ©lyr 原子里取歌词。 */
    fun mp4Lyrics(b: ByteArray): String? {
        // © 在 UTF-8 里是 0xC2 0xA9，文本原子写作 0xA9 + "lyr"（Mac Roman 单字节）
        val idx = indexOfBytes(b, byteArrayOf(0xa9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()))
        if (idx < 4) return null
        val parentSize = readUInt32(b, idx - 4).toInt()
        if (parentSize <= 8 || idx - 4 + parentSize > b.size) return null
        val childSize = readUInt32(b, idx + 4).toInt()
        if (childSize < 16 || !tag(b, idx + 8, "data")) return null
        val payloadStart = idx + 20
        val payloadEnd = minOf(idx + 4 + childSize, idx - 4 + parentSize, b.size)
        if (payloadEnd - payloadStart <= 0 || payloadEnd - payloadStart > MAX_LYRICS_BYTES) return null
        return String(b, payloadStart, payloadEnd - payloadStart, Charsets.UTF_8)
            .takeIf { it.isNotBlank() }
    }

    // ---------------------------------------------------------------- 工具

    private fun tag(b: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > b.size) return false
        for (k in text.indices) if (b[offset + k] != text[k].code.toByte()) return false
        return true
    }

    private fun indexOfBytes(b: ByteArray, needle: ByteArray): Int {
        var i = 0
        while (i + needle.size <= b.size) {
            if (tag(b, i, String(needle, Charsets.ISO_8859_1))) return i
            i++
        }
        return -1
    }

    private fun synchsafe(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 0x7f) shl 21) or
            ((b[offset + 1].toLong() and 0x7f) shl 14) or
            ((b[offset + 2].toLong() and 0x7f) shl 7) or
            (b[offset + 3].toLong() and 0x7f)

    private fun readUInt32(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 0xff) shl 24) or
            ((b[offset + 1].toLong() and 0xff) shl 16) or
            ((b[offset + 2].toLong() and 0xff) shl 8) or
            (b[offset + 3].toLong() and 0xff)
}
