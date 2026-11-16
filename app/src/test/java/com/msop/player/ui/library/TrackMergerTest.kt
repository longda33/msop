package com.msop.player.ui.library

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 曲库展示层去重（2026-11-16 晚第十三轮）。
 *
 * 用户截图里 `Love Paradise` 出现了三次，实际是两种重复叠在一起：
 * 1. 同一个文件被两个本机目录都扫进来（`Music` 与 `msop-music`，时长/体积完全相同）；
 * 2. 云端条目把「歌名 - 歌手」整串当标题、歌手留空，旧键 `标题|歌手` 与本机条对不上。
 * 这两个场景都在下面锁住，且要求「同名不同版本」不被误合并。
 */
class TrackMergerTest {

    private fun track(
        id: String,
        title: String,
        artist: String? = null,
        source: SourceType = SourceType.LOCAL,
        durationMs: Long = 196_267L,
        sizeBytes: Long? = 22_658_255L,
        localPath: String? = "/storage/emulated/0/Music/$id.flac",
    ) = Track(
        id = id,
        source = source,
        uri = if (source == SourceType.LOCAL) "content://test/$id" else "openlist:p2:/yd/musicweb/$id.flac",
        title = title,
        artist = artist,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        localPath = localPath,
    )

    @Test
    fun `同一来源同文件重复只留一条`() {
        val a = track("a1", "Love Paradise", "陈慧琳", localPath = "/storage/emulated/0/Music/Love Paradise.flac")
        val b = track("a2", "Love Paradise", "陈慧琳", localPath = "/storage/emulated/0/msop-music/Love Paradise.flac")
        val merged = TrackMerger.merge(listOf(a, b))
        assertEquals(listOf(a), merged)
    }

    @Test
    fun `云端把歌名歌手写在一列也能和本机条合并`() {
        val local = track("l1", "Love Paradise", "陈慧琳")
        val cloud = track(
            "c1",
            "Love Paradise - 陈慧琳",
            artist = null,
            source = SourceType.OPENLIST,
            localPath = null,
        )
        val merged = TrackMerger.merge(listOf(local, cloud))
        assertEquals(listOf(local), merged)
    }

    @Test
    fun `同名不同时长或不同体积不合并`() {
        val studio = track("s1", "晴天", "周杰伦", durationMs = 269_000L, sizeBytes = 30_000_000L)
        val live = track("s2", "晴天", "周杰伦", durationMs = 301_000L, sizeBytes = 34_000_000L)
        assertEquals(listOf(studio, live), TrackMerger.merge(listOf(studio, live)))
    }

    @Test
    fun `不同歌手同名曲目不合并`() {
        val a = track("x1", "Hello", "Adele")
        val b = track("x2", "Hello", "Lionel Richie")
        assertEquals(listOf(a, b), TrackMerger.merge(listOf(a, b)))
    }

    @Test
    fun `云端独占曲目保留`() {
        val cloud = track("c9", "远方的孩子", null, SourceType.WEBDAV, localPath = null)
        assertEquals(listOf(cloud), TrackMerger.merge(listOf(cloud)))
    }
}
