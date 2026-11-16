package com.msop.player.feature.lyrics

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * TTML 歌词解析（P7-05）。
 *
 * 只做「把 TTML 变成 [Lyrics]」这件事，不关心文件从哪里来。支持：
 * - `<tt>` 根、TTML 命名空间，以及不带命名空间的简写文件；
 * - `<p begin end dur>` 段落与 `<span begin>` 逐字/逐词；
 * - 时间表达式：`hh:mm:ss.mmm`、`hh:mm:ss:ff`（帧）、`1.5s`、`500ms`、
 *   `2m`、`1h`、`10f`、`1500t`（tick）；
 * - `ttp:frameRate` / `ttp:subFrameRate` / `ttp:tickRate`；
 * - 没有 `end` 时用下一段的 `begin` 作为结束时间；
 * - `<br/>` 保留为行内换行；`xml:lang` 等属性忽略。
 *
 * 时间语义按实际歌词文件处理：**clock-time 视为绝对时间**，offset-time（`1.5s` 这种）
 * 视为相对父元素 `begin`。Apple Music 一类导出的 TTML 两种混用，这个规则能同时对上。
 *
 * 解析失败不抛异常，返回空歌词；文件能解析但没有时间轴时返回 `synced=false` 的纯文本，
 * 播放页按静态歌词展示，不会把 XML 标签直接糊到屏幕上。
 */
object TtmlParser {

    private const val BOM = '\uFEFF'

    /** `hh:mm:ss:ff` 或 `hh:mm:ss:ff.ss`（帧 + 子帧）。 */
    private val CLOCK_WITH_FRAMES =
        Regex("""^(\d+):(\d{2}):(\d{2}):(\d+)(?:\.(\d+))?$""")

    /** `hh:mm:ss.mmm` / `hh:mm:ss,mmm`。 */
    private val CLOCK_HMS = Regex("""^(\d+):(\d{2}):(\d{2})(?:[.,](\d+))?$""")

    /** `mm:ss.mmm`，少数文件省略小时。 */
    private val CLOCK_MS = Regex("""^(\d+):(\d{2})(?:[.,](\d+))?$""")

    /** `1.5s` / `500ms` / `2m` / `1h` / `10f` / `1500t`。 */
    private val OFFSET_TIME =
        Regex("""^([+-]?\d+(?:\.\d+)?)\s*(ms|h|m|s|f|t)$""", RegexOption.IGNORE_CASE)

    private val SPACE_RUN = Regex("""\s+""")

    /**
     * 粗略判断一段文本是不是 TTML，给 `.xml` 旁挂文件和内嵌歌词用。
     * 只看前 4 KB：真正的解析交给 [parse]，这里不能抛异常。
     */
    fun looksLikeTtml(raw: String): Boolean {
        val head = raw.removePrefix(BOM.toString()).trimStart()
        if (!head.startsWith("<")) return false
        val probe = head.take(4096)
        if (!Regex("""<tt(\s|>)""").containsMatchIn(probe)) return false
        return probe.contains("ttml", ignoreCase = true) ||
            probe.contains("begin=", ignoreCase = true)
    }

    fun parse(raw: String): Lyrics {
        if (raw.isBlank()) return Lyrics(emptyList())

        val document = runCatching { parseDocument(raw) }.getOrNull()
            ?: return Lyrics(emptyList())
        val root = document.documentElement ?: return Lyrics(emptyList())

        val frameRate = attr(root, "frameRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 30.0
        val subFrameRate = attr(root, "subFrameRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val tickRate = attr(root, "tickRate")?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: frameRate * subFrameRate
        val timing = Timing(frameRate, subFrameRate, tickRate)

        val paragraphs = findParagraphs(root)
        if (paragraphs.isEmpty()) return Lyrics(emptyList())

        var cursor = 0L
        val drafts = mutableListOf<LineDraft>()
        paragraphs.forEach { paragraph ->
            val draft = parseParagraph(paragraph, timing, cursor)
            if (draft != null) {
                drafts += draft
                cursor = draft.endMs?.takeIf { it > draft.beginMs } ?: draft.beginMs
            }
        }
        if (drafts.isEmpty()) return Lyrics(emptyList())

        val synced = drafts.all { it.hasBegin }
        var lines = drafts
            .map { draft ->
                LyricLine(
                    timeMs = draft.beginMs,
                    text = draft.text,
                    words = draft.words,
                    endMs = draft.endMs,
                    wordJoiner = "",
                )
            }
            .sortedBy { it.timeMs }

        // 没有 end 的段落用下一段的 begin 补；最后一行保持 null，由 UI 自己决定停留多久。
        lines = lines.mapIndexed { index, line ->
            if (line.endMs != null && line.endMs > line.timeMs) {
                line
            } else {
                val next = lines.getOrNull(index + 1)?.timeMs
                line.copy(endMs = next?.takeIf { it > line.timeMs })
            }
        }
        return Lyrics(lines, synced = synced)
    }

    private fun parseDocument(raw: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        // 不解析 DOCTYPE / 外部实体：歌词文件来自用户目录，没必要给 XML 实体攻击留口子。
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        // XML 声明必须在文档第 0 字节；有些导出工具会在前面加空白，这里顺手容忍。
        val clean = raw.removePrefix(BOM.toString()).trimStart()
        return builder.parse(ByteArrayInputStream(clean.toByteArray(Charsets.UTF_8)))
    }

    private fun parseParagraph(
        paragraph: Element,
        timing: Timing,
        fallbackBeginMs: Long,
    ): LineDraft? {
        val beginAttr = attr(paragraph, "begin")
        val parsedBegin = beginAttr?.let { parseTime(it, timing, parentBeginMs = 0L) }
        val begin = (parsedBegin ?: fallbackBeginMs).coerceAtLeast(0L)
        val end = parseEnd(paragraph, begin, timing)

        val segments = mutableListOf<WordDraft>()
        collectChildren(paragraph, begin, timing, segments, inSpan = false)
        val words = finishWords(segments)
        if (words.isEmpty()) return null

        val text = words.joinToString("") { it.text }.trim()
        if (text.isBlank()) return null
        return LineDraft(
            beginMs = begin,
            endMs = end,
            text = text,
            words = words,
            hasBegin = parsedBegin != null,
        )
    }

    private fun parseEnd(paragraph: Element, beginMs: Long, timing: Timing): Long? {
        attr(paragraph, "end")?.let { raw ->
            return parseTime(raw, timing, parentBeginMs = 0L)?.takeIf { it > beginMs }
        }
        attr(paragraph, "dur")?.let { raw ->
            val duration = parseTime(raw, timing, parentBeginMs = 0L) ?: return null
            return (beginMs + duration).takeIf { it > beginMs }
        }
        return null
    }

    /**
     * 深度遍历段落文本。每遇到一段文本就产出一个 [WordDraft]：
     * - 在 `<span>` 里：用最近的 span begin；
     * - 在 span 外：用段落的 begin，并尽量并到上一个词上，避免中间空白变成独立词。
     */
    private fun collectChildren(
        element: Element,
        inheritedBeginMs: Long,
        timing: Timing,
        out: MutableList<WordDraft>,
        inSpan: Boolean,
    ) {
        val children = element.childNodes
        for (index in 0 until children.length) {
            when (val child = children.item(index)) {
                is Element -> when (localName(child)) {
                    "span" -> {
                        val spanBegin = attr(child, "begin")
                            ?.let { parseTime(it, timing, parentBeginMs = inheritedBeginMs) }
                            ?: inheritedBeginMs
                        collectChildren(child, spanBegin, timing, out, inSpan = true)
                    }

                    "br" -> appendText(out, inheritedBeginMs, "\n", mergeIntoPrevious = true)

                    else -> collectChildren(child, inheritedBeginMs, timing, out, inSpan)
                }

                is Node -> {
                    if (child.nodeType != Node.TEXT_NODE) continue
                    val text = child.nodeValue?.replace(SPACE_RUN, " ")
                    if (text.isNullOrEmpty()) continue
                    appendText(out, inheritedBeginMs, text, mergeIntoPrevious = !inSpan)
                }
            }
        }
    }

    private fun appendText(
        out: MutableList<WordDraft>,
        beginMs: Long,
        raw: String,
        mergeIntoPrevious: Boolean,
    ) {
        if (raw.isEmpty()) return
        val last = out.lastOrNull()
        if (mergeIntoPrevious && last != null) {
            last.text += raw
            return
        }
        if (last != null && last.beginMs == beginMs) {
            last.text += raw
            return
        }
        // 嵌套 span 结束后，外层文本可能拿到更早的时间；保持时间单调递增，
        // 否则播放页的 indexOfLast 逐字高亮会来回跳。
        val safeBegin = last?.let { maxOf(beginMs, it.beginMs) } ?: beginMs
        out += WordDraft(safeBegin, raw)
    }

    private fun finishWords(segments: MutableList<WordDraft>): List<LyricWord> {
        if (segments.isEmpty()) return emptyList()

        segments.first().text = segments.first().text.trimStart()
        segments.last().text = segments.last().text.trimEnd()

        val words = mutableListOf<LyricWord>()
        segments.forEach { segment ->
            var text = segment.text
            if (text.isBlank()) return@forEach

            // 两个 span 各自带前导/尾随空格时，拼起来会出现双空格。
            val previous = words.lastOrNull()
            if (previous != null &&
                previous.text.lastOrNull()?.isWhitespace() == true &&
                text.firstOrNull()?.isWhitespace() == true
            ) {
                text = text.trimStart()
            }
            if (text.isEmpty()) return@forEach
            words += LyricWord(segment.beginMs, text)
        }

        if (words.isNotEmpty()) {
            val last = words.last()
            val trimmed = last.text.trimEnd()
            if (trimmed != last.text) words[words.lastIndex] = last.copy(text = trimmed)
        }
        return words.filter { it.text.isNotBlank() }
    }

    private fun findParagraphs(root: Element): List<Element> {
        val result = mutableListOf<Element>()

        fun walk(element: Element) {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child !is Element) continue
                if (localName(child) == "p") result += child
                walk(child)
            }
        }
        walk(root)
        return result
    }

    /** 先按 localName 找，再容忍 `ttp:frameRate` 这种带前缀写法。 */
    private fun attr(element: Element, name: String): String? {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val node = attributes.item(index) ?: continue
            val local = node.localName ?: node.nodeName.substringAfter(':')
            if (local == name) {
                val value = node.nodeValue
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    private fun localName(element: Element): String =
        element.localName ?: element.tagName.substringAfter(':')

    /**
     * 解析一个时间表达式。返回 null 表示无法识别，调用方决定回退策略。
     *
     * @param parentBeginMs offset-time 的基准；clock-time 忽略它
     */
    private fun parseTime(raw: String, timing: Timing, parentBeginMs: Long): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        CLOCK_WITH_FRAMES.matchEntire(text)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return null
            val minutes = match.groupValues[2].toLongOrNull() ?: return null
            val seconds = match.groupValues[3].toLongOrNull() ?: return null
            val frames = match.groupValues[4].toLongOrNull() ?: return null
            val subFrames = match.groupValues[5].toLongOrNull() ?: 0L

            val baseMs = hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L
            val frameMs = frames * 1_000.0 / timing.frameRate
            val subFrameMs = subFrames * 1_000.0 / (timing.frameRate * timing.subFrameRate)
            return (baseMs + frameMs + subFrameMs).roundToLong()
        }

        CLOCK_HMS.matchEntire(text)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return null
            val minutes = match.groupValues[2].toLongOrNull() ?: return null
            val seconds = match.groupValues[3].toLongOrNull() ?: return null
            val fraction = fractionToMs(match.groupValues[4])
            return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fraction
        }

        CLOCK_MS.matchEntire(text)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return null
            val seconds = match.groupValues[2].toLongOrNull() ?: return null
            val fraction = fractionToMs(match.groupValues[3])
            return minutes * 60_000L + seconds * 1_000L + fraction
        }

        OFFSET_TIME.matchEntire(text)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            val unitMs = when (match.groupValues[2].lowercase()) {
                "h" -> 3_600_000.0
                "m" -> 60_000.0
                "s" -> 1_000.0
                "ms" -> 1.0
                "f" -> 1_000.0 / timing.frameRate
                "t" -> 1_000.0 / timing.tickRate
                else -> return null
            }
            return (parentBeginMs + value * unitMs).roundToLong()
        }

        return null
    }

    private fun fractionToMs(raw: String): Long {
        if (raw.isEmpty()) return 0L
        return (("0.$raw").toDoubleOrNull()?.times(1_000.0) ?: 0.0).roundToLong()
    }

    private data class Timing(
        val frameRate: Double,
        val subFrameRate: Double,
        val tickRate: Double,
    )

    private class WordDraft(val beginMs: Long, var text: String)

    private class LineDraft(
        val beginMs: Long,
        val endMs: Long?,
        val text: String,
        val words: List<LyricWord>,
        val hasBegin: Boolean,
    )
}
