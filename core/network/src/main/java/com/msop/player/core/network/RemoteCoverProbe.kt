package com.msop.player.core.network

/**
 * 云端曲目的**内嵌封面**探测（2026-11-17 用户报「一大堆歌曲内置封面不显示」）。
 *
 * 服务端（OpenList / WebDAV）只给文件名，不给音频标签；之前只有 Subsonic 能拿到
 * 11 张封面，其余 73 首 OpenList + 182 首 WebDAV 全是无封面的占位图。这里直接
 * 从音频文件本身读内嵌图：对直链发小范围 `Range` GET，解析 ID3 APIC / FLAC
 * PICTURE / MP4 covr，拿到图片字节后交给调用方落盘。
 *
 * **只读文件头尾，不下载整首歌**。解析失败返回 null，不影响播放。
 * [picture] 是同步的，供调用方在受限并发的 DataSource/IO 线程里调用；
 * 请求并发上限由调用方用 `Semaphore` 控制（当前 4），这里不自行起协程。
 */
object RemoteCoverProbe {

    private const val HEAD_BYTES = 512L * 1024L
    private const val MAX_TAG_BYTES = 4L * 1024L * 1024L
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L

    /**
     * @param url 直链（OpenList raw_url 或 WebDAV 地址）
     * @param headers 额外鉴权头（WebDAV Basic Auth）；OpenList raw_url 必须传空
     */
    fun picture(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        val head = RemoteRangeReader.range(url, headers, 0L, HEAD_BYTES) ?: return null

        id3Picture(head)?.let { if (imageExtension(it) != null) return it }
        flacPicture(head)?.let { if (imageExtension(it) != null) return it }
        mp4Picture(head)?.let { if (imageExtension(it) != null) return it }

        // ID3/FLAC 的封面可能超过头 512KB，按标签声明的长度再取一次
        val id3Size = id3TagSize(head)
        if (id3Size != null && id3Size in (head.size + 1L)..MAX_TAG_BYTES) {
            RemoteRangeReader.range(url, headers, 0L, id3Size)?.let { id3Picture(it) }
                ?.let { if (imageExtension(it) != null) return it }
        }
        val flacEnd = flacPictureEnd(head)
        if (flacEnd != null && flacEnd in (head.size + 1L)..MAX_TAG_BYTES) {
            RemoteRangeReader.range(url, headers, 0L, flacEnd)?.let { flacPicture(it) }
                ?.let { if (imageExtension(it) != null) return it }
        }

        // MP4 的 moov/covr 常放在文件尾部
        if (head.size >= HEAD_BYTES) {
            val tail = RemoteRangeReader.range(url, headers, null, HEAD_BYTES) ?: return null
            return mp4Picture(tail)?.takeIf { imageExtension(it) != null }
        }
        return null
    }

    /**
     * 按**魔数**而不是帧里的 picture type 判断图片格式。
     * 2026-11-17 部分云端封面显示白色：ID3/FLAC 里塞的不是图片（或解析偏移错了），
     * 旧代码照样写盘，Coil 解码失败就露出白色底。这里只认合法图片头，
     * 并按真实格式决定文件扩展名，PNG 不再被错存成 .jpg。
     */
    fun imageExtension(b: ByteArray): String? = when {
        b.size >= 3 && (b[0].toInt() and 0xff) == 0xff &&
            (b[1].toInt() and 0xff) == 0xd8 && (b[2].toInt() and 0xff) == 0xff -> "jpg"
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() &&
            b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "png"
        b.size >= 12 && tag(b, 0, "RIFF") && tag(b, 8, "WEBP") -> "webp"
        else -> null
    }

    // ---------------------------------------------------------------- ID3v2

    /** ID3v2 标签总长度（含 10 字节头），不是 ID3 则 null。 */
    fun id3TagSize(b: ByteArray): Long? {
        if (b.size < 10 || !tag(b, 0, "ID3")) return null
        val size = synchsafe(b, 6)
        val footer = if ((b[5].toInt() and 0x10) != 0) 10L else 0L
        return 10L + size + footer
    }

    /** 公开给单测：从一段 ID3v2 数据里取第一张 APIC/PIC 图。 */
    fun id3Picture(b: ByteArray): ByteArray? {
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
            if ((id == "APIC" || (version == 2 && id == "PIC")) && dataEnd <= b.size) {
                val bytes = b.copyOfRange(dataStart, dataEnd)
                pictureFromId3Frame(bytes, v2 = version == 2)?.let { return it }
            }
            i = dataEnd
        }
        return null
    }

    private fun pictureFromId3Frame(data: ByteArray, v2: Boolean): ByteArray? {
        if (data.isEmpty()) return null
        val enc = data[0].toInt() and 0xff
        if (enc > 3) return null
        var pos = 1
        if (v2) {
            pos += 3 // JPG / PNG
        } else {
            pos = skipTerminated(data, pos, enc) ?: return null // MIME
        }
        if (pos >= data.size) return null
        pos += 1 // picture type
        pos = skipTerminated(data, pos, enc) ?: return null // description
        if (pos >= data.size || data.size - pos > MAX_IMAGE_BYTES) return null
        return data.copyOfRange(pos, data.size)
    }

    /** 找到编码对应的字符串结束位置，返回紧随其后的下标。 */
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

    // ---------------------------------------------------------------- FLAC

    /** FLAC PICTURE 块的结束位置（相对文件开头），没有则 null。 */
    fun flacPictureEnd(b: ByteArray): Long? {
        if (b.size < 8 || !tag(b, 0, "fLaC")) return null
        var i = 4
        while (i + 4 <= b.size) {
            val type = b[i].toInt() and 0x7f
            val len = ((b[i + 1].toLong() and 0xff) shl 16) or
                ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
            if (type == 6) return i + 4L + len
            if (len <= 0L) return null
            i += 4 + len.toInt()
        }
        return null
    }

    /** 公开给单测：从 FLAC 头里取第一张 PICTURE。 */
    fun flacPicture(b: ByteArray): ByteArray? {
        if (b.size < 8 || !tag(b, 0, "fLaC")) return null
        var i = 4
        while (i + 4 <= b.size) {
            val type = b[i].toInt() and 0x7f
            val len = ((b[i + 1].toLong() and 0xff) shl 16) or
                ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
            if (len <= 0L || i + 4L + len > b.size) return null
            val block = b.copyOfRange(i + 4, i + 4 + len.toInt())
            if (type == 6) return pictureFromFlacBlock(block)
            i += 4 + len.toInt()
        }
        return null
    }

    private fun pictureFromFlacBlock(b: ByteArray): ByteArray? {
        if (b.size < 32) return null
        var pos = 4 // picture type
        val mimeLen = readUInt32(b, pos).toInt(); pos += 4
        if (mimeLen < 0 || mimeLen > b.size - pos) return null
        pos += mimeLen
        val descLen = readUInt32(b, pos).toInt(); pos += 4
        if (descLen < 0 || descLen > b.size - pos) return null
        pos += descLen + 16 // width/height/depth/colors
        if (pos + 4 > b.size) return null
        val imageLen = readUInt32(b, pos).toInt(); pos += 4
        if (imageLen <= 0 || imageLen > MAX_IMAGE_BYTES || pos + imageLen > b.size) return null
        return b.copyOfRange(pos, pos + imageLen)
    }

    // ---------------------------------------------------------------- MP4

    /** 公开给单测：从 MP4 头/尾里取 covr 原子里的图片。 */
    fun mp4Picture(b: ByteArray): ByteArray? {
        val idx = indexOfTag(b, "covr")
        if (idx < 4) return null
        val parentSize = readUInt32(b, idx - 4).toInt()
        if (parentSize <= 8 || idx - 4 + parentSize > b.size) return null
        // covr 的第一个子节点通常是 data：[size4][type4][version/flags4][locale4][payload]
        val childSize = readUInt32(b, idx + 4).toInt()
        if (childSize < 16 || !tag(b, idx + 8, "data")) return null
        val payloadStart = idx + 20
        val payloadEnd = minOf(idx + 4 + childSize, idx - 4 + parentSize, b.size)
        if (payloadEnd - payloadStart <= 0 || payloadEnd - payloadStart > MAX_IMAGE_BYTES) return null
        return b.copyOfRange(payloadStart, payloadEnd)
    }

    // ---------------------------------------------------------------- 工具

    private fun tag(b: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > b.size) return false
        for (k in text.indices) if (b[offset + k] != text[k].code.toByte()) return false
        return true
    }

    private fun indexOfTag(b: ByteArray, text: String): Int {
        var i = 0
        val limit = b.size - text.length
        while (i <= limit) {
            if (tag(b, i, text)) return i
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
