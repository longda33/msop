package com.primuse.lite.feature.lyrics

import java.io.InputStream

/**
 * 内嵌歌词提取。
 *
 * 只做两种最常见的容器：
 * - MP3 的 ID3v2.3 / v2.4 `USLT` 帧（`LYRICS` 之外还有 `UNSYNCEDLYRICS`、`UNSYNCED LYRICS`）
 * - FLAC 的 Vorbis Comment（`LYRICS=`）
 *
 * 不引第三方标签库：这两种格式的头部都足够简单，而且只需要读到元数据块就停，
 * 不必把几十 MB 的音频读进内存。解析失败一律返回 null，不影响播放。
 */
object EmbeddedLyrics {

    /** 元数据区上限。正常歌词远小于这个值，超过说明文件损坏，直接放弃。 */
    private const val MAX_METADATA_BYTES = 4 * 1024 * 1024

    fun extract(input: InputStream): String? {
        val magic = ByteArray(4)
        if (!input.readFully(magic)) return null

        return when {
            magic.isAscii("ID3") -> {
                val rest = ByteArray(6)
                if (!input.readFully(rest)) return null
                extractId3(input, magic + rest)
            }
            magic.isAscii("fLaC") -> extractFlac(input)
            else -> null
        }
    }

    // ---------------------------------------------------------------- ID3

    private fun extractId3(input: InputStream, header: ByteArray): String? {
        val major = header[3].toInt() and 0xFF
        // v2.2 的帧头是 3 字节 ID + 3 字节长度，格式差异大且现在几乎见不到，不猜。
        if (major != 3 && major != 4) return null

        val flags = header[5].toInt() and 0xFF
        var remaining = synchsafe(header, 6).coerceAtMost(MAX_METADATA_BYTES)

        if (flags and 0x40 != 0) {
            // 扩展头：v2.3 的 size 不含自身 4 字节，v2.4 的 synchsafe size 含自身
            val ext = ByteArray(4)
            if (!input.readFully(ext)) return null
            remaining -= 4
            if (major == 4) {
                val extSize = synchsafe(ext, 0)
                val skip = (extSize - 4).coerceAtLeast(0)
                if (!input.skipFully(skip.toLong())) return null
                remaining -= skip
            } else {
                val skip = beInt(ext, 0)
                if (!input.skipFully(skip.toLong())) return null
                remaining -= skip
            }
        }

        while (remaining >= 10) {
            val frame = ByteArray(10)
            if (!input.readFully(frame)) return null
            remaining -= 10

            // 全零是 padding，后面不会再有帧
            if (frame.all { it == 0.toByte() }) return null

            val id = String(frame, 0, 4, Charsets.ISO_8859_1)
            val size = if (major == 4) synchsafe(frame, 4) else beInt(frame, 4)
            if (size <= 0 || size > remaining) return null

            if (id == "USLT") {
                val body = ByteArray(size)
                if (!input.readFully(body)) return null
                return decodeUslt(body)
            }

            if (!input.skipFully(size.toLong())) return null
            remaining -= size
        }
        return null
    }

    private fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body[0].toInt() and 0xFF
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }

        // 跳过 encoding(1) + language(3)，再跳过 null 结尾的内容描述
        var index = 4
        if (encoding == 1 || encoding == 2) {
            while (index + 1 < body.size) {
                if (body[index] == 0.toByte() && body[index + 1] == 0.toByte()) {
                    index += 2
                    break
                }
                // 有些标签把空描述写成单字节 0，后面直接跟歌词的 BOM
                if (body[index] == 0.toByte() && isBom(body, index + 1)) {
                    index += 1
                    break
                }
                index += 2
            }
        } else {
            while (index < body.size) {
                if (body[index] == 0.toByte()) {
                    index += 1
                    break
                }
                index += 1
            }
        }
        if (index >= body.size) return null

        return runCatching {
            String(body, index, body.size - index, charset).trimEnd('\u0000')
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun isBom(bytes: ByteArray, index: Int): Boolean {
        if (index + 1 >= bytes.size) return false
        return (bytes[index] == 0xFF.toByte() && bytes[index + 1] == 0xFE.toByte()) ||
            (bytes[index] == 0xFE.toByte() && bytes[index + 1] == 0xFF.toByte())
    }

    // --------------------------------------------------------------- FLAC

    private fun extractFlac(input: InputStream): String? {
        while (true) {
            val blockHeader = ByteArray(4)
            if (!input.readFully(blockHeader)) return null

            val first = blockHeader[0].toInt() and 0xFF
            val isLast = first and 0x80 != 0
            val type = first and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                (blockHeader[3].toInt() and 0xFF)

            if (length > MAX_METADATA_BYTES) return null

            if (type == 4) {
                val body = ByteArray(length)
                if (!input.readFully(body)) return null
                return parseVorbisComment(body)
            }

            if (!input.skipFully(length.toLong())) return null
            if (isLast) return null
        }
    }

    private fun parseVorbisComment(body: ByteArray): String? {
        var offset = 0

        fun readLe32(): Int? {
            if (offset + 4 > body.size) return null
            val value = (body[offset].toInt() and 0xFF) or
                ((body[offset + 1].toInt() and 0xFF) shl 8) or
                ((body[offset + 2].toInt() and 0xFF) shl 16) or
                ((body[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return value
        }

        val vendorLength = readLe32() ?: return null
        if (vendorLength < 0 || offset + vendorLength > body.size) return null
        offset += vendorLength

        val count = readLe32() ?: return null
        if (count < 0) return null

        repeat(count) {
            val length = readLe32() ?: return null
            if (length < 0 || offset + length > body.size) return null

            val entry = String(body, offset, length, Charsets.UTF_8)
            offset += length

            val equals = entry.indexOf('=')
            if (equals > 0 && isLyricsKey(entry.substring(0, equals))) {
                val value = entry.substring(equals + 1)
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun isLyricsKey(key: String): Boolean =
        key.equals("LYRICS", ignoreCase = true) ||
            key.equals("UNSYNCEDLYRICS", ignoreCase = true) ||
            key.equals("UNSYNCED LYRICS", ignoreCase = true)

    // ------------------------------------------------------------- 小工具

    private fun synchsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun ByteArray.isAscii(text: String): Boolean {
        if (size < text.length) return false
        for (index in text.indices) {
            if (this[index].toInt() != text[index].code) return false
        }
        return true
    }

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val count = read(buffer, read, buffer.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun InputStream.skipFully(count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (read() < 0) return false
            remaining--
        }
        return true
    }
}
