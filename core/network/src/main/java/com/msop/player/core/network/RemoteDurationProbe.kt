package com.msop.player.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 云端曲目时长探测（2026-11-16 用户反馈「歌曲时间也没有」）。
 *
 * 实测这台 OpenList v4.2.9 的 OpenSubsonic 实现**完全不返回 `duration`**
 * （`search3` / `getSong` / `getAlbum` 三条接口的 `song` 里都没有这个字段，
 * `album.duration` 甚至写死 0）。服务端拿不到，只能在客户端读文件头自己算。
 *
 * 做法：对该文件的直链发一次 `Range: bytes=0-65535` 的 GET，按容器格式解析：
 * - **FLAC**：`STREAMINFO` 里的 sampleRate + totalSamples，最准，也是本库主力格式；
 * - **WAV**：`fmt ` 的采样率/声道/位深 + `data` 长度；
 * - **MP3**：优先读 Xing/Info 的帧数；没有就按首帧码率估算；
 * - **M4A/MP4**：在头部找 `mvhd`（moov 在尾部时补一次尾部 Range）。
 *
 * 只取头部字节，不下载整首歌；解析失败返回 null，界面继续显示 `--:--`，
 * 不影响同步与播放。
 */
object RemoteDurationProbe {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val HEAD_BYTES = 65_536L
    private const val TAIL_BYTES = 32_768L

    /**
     * @param url 直链（OpenList 的 raw_url 或 WebDAV 的真实地址）
     * @param headers 额外的鉴权头（WebDAV 需要 Basic Auth）
     */
    fun durationMs(url: String, headers: Map<String, String> = emptyMap(), fileSize: Long? = null): Long? {
        val head = range(url, headers, 0L, HEAD_BYTES)
        head?.let { bytes ->
            fromFlac(bytes)?.let { return it }
            fromWav(bytes)?.let { return it }
            fromMp4(bytes)?.let { return it }
            // 带大封面的文件，ID3v2 标签可能把音频头推出这 64KB 之外；
            // 实测还有「.mp3 后缀其实是带 ID3 的 FLAC」，所以按标签长度再取一段重新判格式
            val id3 = id3End(bytes)
            if (id3 != null && id3 + 8 >= bytes.size.toLong()) {
                range(url, headers, id3, HEAD_BYTES)?.let { p ->
                    fromFlac(p)?.let { return it }
                    fromWav(p)?.let { return it }
                    fromMp4(p)?.let { return it }
                    fromMp3(p, fileSize)?.let { return it }
                }
            }
            val payload = if (id3 != null && id3 > 0L && id3 < bytes.size) {
                bytes.copyOfRange(id3.toInt(), bytes.size)
            } else {
                bytes
            }
            fromFlac(payload)?.let { return it }
            fromWav(payload)?.let { return it }
            fromMp4(payload)?.let { return it }
            fromMp3(payload, fileSize)?.let { return it }
        }
        // moov 在文件尾部是 MP4 的常见布局，头部找不到 mvhd 就再取一次尾部
        val tail = range(url, headers, null, TAIL_BYTES)
        tail?.let { bytes ->
            fromMp4(bytes)?.let { return it }
            fromMp3(bytes, fileSize)?.let { return it }
        }
        return null
    }

    /** ID3v2 标签结束位置（没有标签返回 null）；长度是 synchsafe 整数。 */
    private fun id3End(b: ByteArray): Long? {
        if (b.size < 10 || !tag(b, 0, "ID3")) return null
        val size = ((b[6].toLong() and 0xff) shl 21) or ((b[7].toLong() and 0xff) shl 14) or
            ((b[8].toLong() and 0xff) shl 7) or (b[9].toLong() and 0xff)
        val footer = if ((b[5].toInt() and 0x10) != 0) 10L else 0L
        return 10 + size + footer
    }

    // ---------------------------------------------------------------- HTTP

    private fun range(
        url: String,
        headers: Map<String, String>,
        start: Long?,
        length: Long,
    ): ByteArray? {
        val rangeValue = if (start == null) "bytes=-$length" else "bytes=$start-${start + length - 1}"
        val builder = Request.Builder().url(url).header("Range", rangeValue).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return try {
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body.bytes()
                // 服务器不支持 Range 时会把整首歌吐回来，这里直接放弃，避免把大文件读进内存
                if (bytes.size > length * 8) null else bytes
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- FLAC

    private fun fromFlac(b: ByteArray): Long? {
        if (b.size < 42 || b[0] != 'f'.code.toByte() || b[1] != 'L'.code.toByte()) return null
        if (b[2] != 'a'.code.toByte() || b[3] != 'C'.code.toByte()) return null
        var i = 4
        while (i + 4 <= b.size) {
            val type = b[i].toInt() and 0x7f
            val len = ((b[i + 1].toInt() and 0xff) shl 16) or
                ((b[i + 2].toInt() and 0xff) shl 8) or
                (b[i + 3].toInt() and 0xff)
            if (type == 0) {
                val p = i + 4
                if (p + 18 > b.size) return null
                val x0 = b[p + 10].toLong() and 0xff
                val x1 = b[p + 11].toLong() and 0xff
                val x2 = b[p + 12].toLong() and 0xff
                val x3 = b[p + 13].toLong() and 0xff
                val x4 = b[p + 14].toLong() and 0xff
                val x5 = b[p + 15].toLong() and 0xff
                val x6 = b[p + 16].toLong() and 0xff
                val x7 = b[p + 17].toLong() and 0xff
                val sampleRate = (x0 shl 12) or (x1 shl 4) or (x2 shr 4)
                val totalSamples = ((x3 and 0x0f) shl 32) or (x4 shl 24) or (x5 shl 16) or (x6 shl 8) or x7
                if (sampleRate <= 0L || totalSamples <= 0L) return null
                return totalSamples * 1000L / sampleRate
            }
            if (len <= 0) return null
            i += 4 + len
        }
        return null
    }

    // ---------------------------------------------------------------- WAV

    private fun fromWav(b: ByteArray): Long? {
        if (b.size < 44) return null
        if (!tag(b, 0, "RIFF") || !tag(b, 8, "WAVE")) return null
        var i = 12
        var channels = 0
        var sampleRate = 0L
        var bits = 0
        var dataBytes = 0L
        while (i + 8 <= b.size) {
            val size = readUInt32(b, i + 4)
            when {
                tag(b, i, "fmt ") && i + 16 <= b.size -> {
                    channels = readUInt16(b, i + 10)
                    sampleRate = readUInt32(b, i + 12)
                    if (i + 24 <= b.size) bits = readUInt16(b, i + 22)
                }
                tag(b, i, "data") -> dataBytes = size
            }
            if (size <= 0L) break
            i += 8 + size.toInt()
            if (dataBytes > 0L) break
        }
        if (channels <= 0 || sampleRate <= 0L || bits <= 0 || dataBytes <= 0L) return null
        val bytesPerSecond = sampleRate * channels * bits / 8
        if (bytesPerSecond <= 0L) return null
        return dataBytes * 1000L / bytesPerSecond
    }

    // ---------------------------------------------------------------- MP3

    private fun fromMp3(b: ByteArray, fileSize: Long?): Long? {
        var i = 0
        while (i + 4 <= b.size) {
            if (b[i] == 0xFF.toByte() && (b[i + 1].toInt() and 0xE0) == 0xE0) break
            i++
        }
        if (i + 4 > b.size) return null
        val header = (b[i].toInt() and 0xff shl 24) or (b[i + 1].toInt() and 0xff shl 16) or
            (b[i + 2].toInt() and 0xff shl 8) or (b[i + 3].toInt() and 0xff)
        val versionBits = (header shr 19) and 0x3      // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
        val layerBits = (header shr 17) and 0x3        // 1 = Layer III
        val bitrateIndex = (header shr 12) and 0xF
        val sampleRateIndex = (header shr 10) and 0x3
        if (layerBits != 1 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null
        val baseRate = intArrayOf(44100, 48000, 32000)[sampleRateIndex]
        val sampleRate = when (versionBits) {
            3 -> baseRate
            2 -> baseRate / 2
            else -> baseRate / 4
        }
        val bitrateTable = if (versionBits == 3) {
            intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        } else {
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
        }
        val bitrateKbps = bitrateTable[bitrateIndex]

        // Xing / Info：帧数在 tag 后 8 字节处（flags 之后再 4 字节 frames）
        val searchFrom = i + 4
        val searchTo = minOf(b.size, i + 200)
        var j = searchFrom
        while (j + 8 <= searchTo) {
            if (tag(b, j, "Xing") || tag(b, j, "Info")) {
                val flags = readUInt32(b, j + 4)
                if ((flags and 0x1L) != 0L && j + 12 <= b.size) {
                    val frames = readUInt32(b, j + 8)
                    val samplesPerFrame = if (versionBits == 3) 1152 else 576
                    if (frames in 1..10_000_000L && sampleRate > 0) {
                        return frames * samplesPerFrame * 1000L / sampleRate
                    }
                }
            }
            j++
        }

        // 没有 Xing（多数是 CBR）：按首帧码率与文件大小估算
        if (bitrateKbps > 0 && fileSize != null && fileSize > 0L) {
            return fileSize * 8L / bitrateKbps
        }
        if (bitrateKbps > 0 && fileSize == null) return null
        return null
    }

    // ---------------------------------------------------------------- MP4 / M4A

    private fun fromMp4(b: ByteArray): Long? {
        val idx = indexOfTag(b, "mvhd")
        if (idx < 0) return null
        val version = b[idx + 4].toInt() and 0xff
        val payload = idx + 4
        return if (version == 1) {
            if (payload + 28 > b.size) return null
            val timescale = readUInt32(b, payload + 20)
            val duration = readUInt64(b, payload + 24)
            if (timescale > 0L && duration > 0L) duration * 1000L / timescale else null
        } else {
            if (payload + 20 > b.size) return null
            val timescale = readUInt32(b, payload + 12)
            val duration = readUInt32(b, payload + 16)
            if (timescale > 0L && duration > 0L) duration * 1000L / timescale else null
        }
    }

    // ---------------------------------------------------------------- 工具

    private fun tag(b: ByteArray, offset: Int, text: String): Boolean {
        if (offset + text.length > b.size) return false
        for (k in text.indices) {
            if (b[offset + k] != text[k].code.toByte()) return false
        }
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

    private fun readUInt16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xff) shl 8) or (b[offset + 1].toInt() and 0xff)

    private fun readUInt32(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 0xff) shl 24) or ((b[offset + 1].toLong() and 0xff) shl 16) or
            ((b[offset + 2].toLong() and 0xff) shl 8) or (b[offset + 3].toLong() and 0xff)

    private fun readUInt64(b: ByteArray, offset: Int): Long =
        (readUInt32(b, offset) shl 32) or readUInt32(b, offset + 4)
}
