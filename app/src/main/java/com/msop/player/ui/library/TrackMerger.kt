package com.msop.player.ui.library

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track

/**
 * 曲库展示层去重（2026-11-16 晚用户需求「本地与云端同名合并，本地优先」，
 * 2026-11-16 晚追加「有重复，见截图」后加强）。
 *
 * 两件事：
 * 1. **本地优先**：云端条目与本机条目是同一首歌时，只留本机那条（本机播放不耗流量、
 *    不依赖服务器）。只在展示层去掉，数据库里两边记录都还在——取消启用服务器、删掉本机
 *    文件之后，另一份会自动回到列表。
 * 2. **同一首歌只出现一次**：同一来源里同歌名 + 同歌手 + 同大小 + 同时长的条目（典型来源
 *    是同一个音频文件被两个目录都扫到了，比如下载到 `msop-music` 后与原来的 `Music`
 *    目录重复）只留第一条。
 *
 * 判定键 = 规范化后的「歌名 + 歌手」，并额外容忍云端同步来的「歌名 - 歌手」写在一列、
 * 歌手为空的老格式：OpenList/WebDAV 的远端文件名常是 `歌名 - 歌手.flac`，同步时整串进了
 * 标题，若不拆开就永远匹配不上本机那条（截图里 `Love Paradise` 出现三次就是这个原因）。
 */
object TrackMerger {

    fun merge(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks

        val localKeys = tracks.asSequence()
            .filter { it.source == SourceType.LOCAL }
            .flatMap { keys(it).asSequence() }
            .toHashSet()

        val kept = ArrayList<Track>(tracks.size)
        // 同一首歌（同键 + 同时长 + 同体积）只保留第一条。体积/时长必须一起比：
        // 同名的现场版、翻唱、不同码率版本不该被合并掉。
        val seen = HashMap<String, Track>(tracks.size)

        for (track in tracks) {
            val candidates = keys(track)
            if (candidates.isNotEmpty()) {
                // 云端同名 → 本地优先，直接丢掉云端这条
                if (track.source != SourceType.LOCAL && candidates.any { it in localKeys }) continue

                val canonical = candidates.min()
                val identity = "$canonical|${track.durationMs}|${track.sizeBytes ?: 0L}"
                if (seen.put(identity, track) != null) continue
            }
            kept += track
        }
        return kept
    }

    /**
     * 「同一首歌」的全部条目（含本机/云端的同源副本）。
     *
     * 用于「移除曲库」——列表上只有一行，但数据库里可能躺着同一个文件的第二份（同目录不同
     * 路径扫进来）或云端同一首。只墓碑一行的话，另一份会立刻把这一行顶回来，用户会觉得
     * 「移除了又回来了」。判定放宽到「键有交集 + 时长差 ≤1 秒或体积相同」：云端时长是
     * 文件头探测出来的，同文件常差 1ms，不能要求严格相等。
     */
    fun twinsOf(target: Track, all: List<Track>): List<Track> {
        val targetKeys = keys(target)
        if (targetKeys.isEmpty()) return listOf(target)
        return all.filter { other ->
            val otherKeys = keys(other)
            otherKeys.isNotEmpty() &&
                otherKeys.any { it in targetKeys } &&
                (kotlin.math.abs(other.durationMs - target.durationMs) <= 1_000L ||
                    (other.sizeBytes != null && other.sizeBytes == target.sizeBytes))
        }
    }

    /** 规范化比较键集合：`歌名|歌手`（含「歌名 - 歌手」拆开后的那一份）。 */
    private fun keys(track: Track): Set<String> {
        val out = LinkedHashSet<String>(2)
        val normalizedTitle = normalize(track.title)
        if (normalizedTitle.isNotEmpty()) {
            out += normalizedTitle + "|" + normalize(track.artist.orEmpty())
        }
        splitTitleArtist(track.title)?.let { (title, artist) ->
            val t = normalize(title)
            if (t.isNotEmpty()) out += t + "|" + normalize(artist)
        }
        return out
    }

    /**
     * 把「歌名 - 歌手」拆成两段。
     *
     * 只在最后一个 `" - "` 处拆一次，且要求右侧像人名（非空、不超过 40 字、不含括号内的
     * 版本说明）——远端文件名基本都是这个格式；标题里本身带连字符但没有歌手段的情况
     * （右段为空）会被 [split] 拒掉，交给只用标题的降级键。
     */
    private fun splitTitleArtist(raw: String): Pair<String, String>? {
        val index = raw.lastIndexOf(SEPARATOR)
        if (index <= 0) return null
        val title = raw.substring(0, index).trim()
        val artist = raw.substring(index + SEPARATOR.length).trim()
        if (title.isEmpty() || artist.isEmpty() || artist.length > 40) return null
        return title to artist
    }

    private fun normalize(raw: String): String =
        raw.lowercase().filter { it.isLetterOrDigit() }

    private const val SEPARATOR = " - "
}
