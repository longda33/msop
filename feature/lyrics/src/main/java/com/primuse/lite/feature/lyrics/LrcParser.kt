package com.primuse.lite.feature.lyrics

/**
 * LRC 解析。
 *
 * 只做「时间轴 + 文本」，不认逐字标签（`<00:12.34>`）——那是 P5-01 之外的增强格式，
 * 先把逐行做扎实。解析失败不抛异常，返回空歌词，播放页退化成「暂无歌词」。
 *
 * 支持的写法：
 * - `[01:23.45]歌词`（百分秒）
 * - `[01:23.456]歌词`（毫秒）
 * - `[01:23]歌词`（整秒）
 * - `[00:10.00][00:20.00]歌词`（一行多个时间点，常见于副歌复用）
 * - `[offset:-500]`（整首偏移，单位毫秒）
 * - `[ti:]` / `[ar:]` / `[al:]` / `[by:]` 等元信息标签直接忽略
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val BOM = '\uFEFF'

    fun parse(raw: String): Lyrics {
        if (raw.isBlank()) return Lyrics(emptyList())

        val text = raw.removePrefix(BOM.toString())
        val offsetMs = OFFSET_TAG.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val lines = ArrayList<LyricLine>(64)
        for (rawLine in text.lineSequence()) {
            val tags = TIME_TAG.findAll(rawLine).toList()
            if (tags.isEmpty()) continue

            val content = TIME_TAG.replace(rawLine, "").trim()
            // 空文本行（间奏占位）不进列表：高亮落在空行上会像卡住了
            if (content.isEmpty()) continue

            for (tag in tags) {
                val timeMs = parseTimeTag(tag) ?: continue
                lines += LyricLine((timeMs + offsetMs).coerceAtLeast(0L), content)
            }
        }

        // 稳定排序：同一时间点的多行保持文件里的先后顺序
        return Lyrics(lines.sortedBy { it.timeMs })
    }

    /**
     * 解码字节。中文 LRC 很多是 GBK 编码，按 UTF-8 硬读会得到一堆 `�`。
     * 先按 UTF-8 读，出现替换字符再退回 GBK。
     */
    fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if ('\uFFFD' !in utf8) return utf8
        return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
    }

    private fun parseTimeTag(tag: MatchResult): Long? {
        val minutes = tag.groupValues[1].toLongOrNull() ?: return null
        val seconds = tag.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60) return null

        val fractionRaw = tag.groupValues[3]
        val fractionMs = when (fractionRaw.length) {
            0 -> 0L
            1 -> fractionRaw.toLong() * 100L
            2 -> fractionRaw.toLong() * 10L
            else -> fractionRaw.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}
