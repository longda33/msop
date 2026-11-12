package com.msop.player.feature.lyrics

/** 一行歌词。[timeMs] 是这一行开始的时间点。 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 一份解析好的歌词。
 *
 * 行按时间升序排列，[indexAt] 用二分查找当前行——播放页每秒都要问一次，
 * 线性扫描在几千行的歌词上会白烧 CPU。
 *
 * [synced] 区分「带时间轴」与「纯文本」：`USLT` 里的歌词大多没有时间标签，
 * 这类歌词只做静态展示，不高亮、不自动滚动、点击也不跳转。
 */
class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean = true,
) {

    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * 返回 [positionMs] 时刻应该高亮的行号；还没到第一行或歌词没有时间轴时返回 -1。
     */
    fun indexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        if (positionMs < lines.first().timeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        /** 把不带时间标签的纯文本歌词按行拆开。空行直接丢弃，避免大段空白。 */
        fun plain(raw: String): Lyrics {
            val lines = raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(0L, it) }
                .toList()
            return Lyrics(lines, synced = false)
        }
    }
}
