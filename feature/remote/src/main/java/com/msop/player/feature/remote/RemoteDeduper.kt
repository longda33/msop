package com.msop.player.feature.remote

import com.msop.player.core.model.Track
import java.util.Locale
import kotlin.math.abs

/**
 * 远程曲库去重（2026-11-16 用户反馈「有重复」）。
 *
 * 实测两种重复来源：
 * 1. 同一首歌在不同目录各存一份（`/单车/单车 李克勤.flac` 与 `/李克勤/单车 李克勤.flac`），
 *    文件名完全一致；
 * 2. 同一首歌同时有 `.flac` 与 `.m4a` 两个版本（大小不同，但歌名一致）。
 *
 * 策略按「规范化歌名」判重（小写、只留字母数字），**保留体积更大的一份**（通常是
 * lossless），体积相同保留先扫到的。多服务器同时启用时跨服务器也走同一条规则，
 * 所以两台服务器上的同一首歌只出现一次。
 *
 * 只做展示层收敛：被丢掉的路径不会进曲库，但不会去删服务器上的文件。
 * 风险是同名不同歌（例如都叫「Intro」）会被合并——自用曲库可以接受，真要保留
 * 可以在配置里把两台服务器拆开分别启用。
 */
object RemoteDeduper {

    fun key(track: Track): String =
        track.title.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /**
     * @return 去重后的曲目列表（保持首次出现的顺序）与被丢弃的条目数
     */
    fun dedupe(tracks: Collection<Track>): Pair<List<Track>, Int> {
        val kept = LinkedHashMap<String, Track>()
        var dropped = 0
        tracks.forEach { track ->
            val key = key(track)
            if (key.isEmpty()) {
                kept[track.id] = track
                return@forEach
            }
            val existing = kept[key]
            if (existing == null) {
                kept[key] = track
            } else {
                dropped++
                if (better(track, existing)) kept[key] = track
            }
        }
        return kept.values.toList() to dropped
    }

    /** 体积大的优先（无损通常更大）；体积差在 1% 内视为同一份，保留先扫到的。 */
    private fun better(candidate: Track, current: Track): Boolean {
        val a = candidate.sizeBytes ?: 0L
        val b = current.sizeBytes ?: 0L
        if (a <= 0L || b <= 0L) return false
        if (abs(a - b) * 100L <= b) return false
        return a > b
    }
}
