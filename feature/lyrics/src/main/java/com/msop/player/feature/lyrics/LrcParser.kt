package com.msop.player.feature.lyrics

/**
 * LRC 解析。
 *
 * 支持「逐行 + 逐字（卡拉OK）」两种时间轴：
 * - 普通 LRC：`[01:23.45]歌词`，整行一个时间点，[LyricLine.words] 为空。
 * - Enhanced LRC：`[01:23.45]<01:23.45>被 <01:24.10>命 <01:25.00>中`，行内每个词带 `<mm:ss.xx>`，
 *   解析后 [LyricLine.words] 非空，播放页据此做 BetterLyrics 式逐字高亮。
 *
 * 解析失败不抛异常，返回空歌词，播放页退化成「暂无歌词」。
 *
 * 支持的写法：
 * - `[01:23.45]歌词`（百分秒）
 * - `[01:23.456]歌词`（毫秒）
 * - `[01:23]歌词`（整秒）
 * - `[00:10.00][00:20.00]歌词`（一行多个时间点，常见于副歌复用）
 * - `<00:12.34>词`（逐字标签，可多个）
 * - `[00:44.23]哭[00:44.55]着[00:44.72]说`（**方括号逐字**，酷狗/QQ 那种每个字
 *   自带一个 `[mm:ss.xx]` 的「逐字 LRC」）
 * - `[offset:-500]`（整首偏移，单位毫秒）
 * - `[ti:]` / `[ar:]` / `[al:]` / `[by:]` 等元信息标签直接忽略
 *
 * 2026-11-16 BUGFIX（用户报「歌词整屏同一句重复 8 行以上」）：
 * 酷狗/QQ 导出的「逐字 LRC」把每个字都写成 `[mm:ss.xx]字`，一行里有 20 多个方括号。
 * 旧实现把**每个方括号都当成一行**、文本取整句（`TIME_TAG` 全删后剩下的全文），
 * 于是「哭着说欠我的未来 下辈子再爱」在 1:44–1:52 之间被复制了 20 次，
 * 屏幕上就是整屏重复。现在按「方括号之间有没有文字」区分两种含义：
 * - 标签之间**没有文字**（`[00:10][00:20]歌词`）→ 同一句的多个时间点，仍拆成多行（副歌复用）；
 * - 标签之间**有文字**（`[00:44.23]哭[00:44.55]着…`）→ 逐字时间轴，合成**一行**，
 *   每个方括号后那段文字是一个 [LyricWord]，行时间取第一个标签。
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val BOM = '\uFEFF'

    fun parse(raw: String): Lyrics {
        if (raw.isBlank()) return Lyrics(emptyList())

        val text = raw.removePrefix(BOM.toString())
        val offsetMs = OFFSET_TAG.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val lines = ArrayList<LyricLine>(64)
        for (rawLine in text.lineSequence()) {
            val bracketTags = TIME_TAG.findAll(rawLine).toList()
            if (bracketTags.isEmpty()) continue

            // 每个方括号之后、下一个方括号之前的那截文字（逐字 LRC 里就是一个字）。
            val segments = ArrayList<String>(bracketTags.size)
            bracketTags.forEachIndexed { index, tag ->
                val start = tag.range.last + 1
                val end = (bracketTags.getOrNull(index + 1)?.range?.first ?: rawLine.length)
                    .coerceAtMost(rawLine.length)
                segments += if (start in 0..end && start <= rawLine.lastIndex) {
                    rawLine.substring(start, end)
                } else {
                    ""
                }
            }

            // 方括号之间普遍有文字（≥2 截）→ 逐字时间轴，整行合成一行，不拆行。
            if (segments.count { it.isNotBlank() } > 1) {
                val words = ArrayList<LyricWord>(bracketTags.size)
                bracketTags.forEachIndexed { index, tag ->
                    val segment = segments[index]
                    if (segment.isNotEmpty()) {
                        val t = parseTimeTag(tag) ?: return@forEachIndexed
                        words += LyricWord((t + offsetMs).coerceAtLeast(0L), segment)
                    }
                }
                if (words.isEmpty()) continue

                lines += LyricLine(
                    timeMs = words.first().timeMs,
                    text = words.joinToString("") { it.text }.trim(),
                    words = words.toList(),
                    // 方括号之间已经是原文字符，不能再插空格，否则中文会逐字裂开
                    wordJoiner = "",
                )
                continue
            }

            val content = TIME_TAG.replace(rawLine, "").trim()
            // 空文本行（间奏占位）不进列表：高亮落在空行上会像卡住了
            if (content.isEmpty()) continue

            // 逐字标签：<mm:ss.xx>词。没有就整行退化。
            val wordMatches = WORD_TAG.findAll(content).toList()
            val words = if (wordMatches.isNotEmpty()) {
                val parsed = ArrayList<LyricWord>(wordMatches.size)
                wordMatches.forEach { m ->
                    val t = parseTimeTag(m) ?: return@forEach
                    val next = WORD_TAG.find(content, m.range.last + 1)
                    val end = next?.range?.first ?: content.length
                    val wtext = content.substring(m.range.last + 1, end).trim()
                    if (wtext.isNotEmpty()) {
                        parsed += LyricWord((t + offsetMs).coerceAtLeast(0L), wtext)
                    }
                }
                // 首词之前若有前置文字（极少见），当作第一个词处理，避免丢字
                val lead = content.substring(0, wordMatches.first().range.first).trim()
                if (lead.isNotEmpty()) {
                    listOf(LyricWord(parsed.firstOrNull()?.timeMs ?: 0L, lead)) + parsed
                } else {
                    parsed
                }
            } else {
                emptyList()
            }

            val lineText = if (words.isNotEmpty()) words.joinToString(" ") { it.text } else content

            for (tag in bracketTags) {
                val timeMs = parseTimeTag(tag) ?: continue
                lines += LyricLine(
                    (timeMs + offsetMs).coerceAtLeast(0L),
                    lineText,
                    if (words.isNotEmpty()) words.toList() else emptyList(),
                )
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
