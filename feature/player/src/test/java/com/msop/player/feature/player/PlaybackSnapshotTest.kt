package com.msop.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放快照与曲库求交的纯函数测试（P7-02）。
 * 不依赖 Android：`resolve` 只接收 id 集合与时间戳。
 */
class PlaybackSnapshotTest {

    private val now = 1_700_000_000_000L

    private fun snapshot(
        ids: List<String> = listOf("a", "b", "c"),
        currentId: String? = "b",
        index: Int = 1,
        positionMs: Long = 42_000L,
        savedAt: Long = now - 1_000L,
    ) = PlaybackSnapshot(
        trackIds = ids,
        currentTrackId = currentId,
        currentIndex = index,
        positionMs = positionMs,
        savedAt = savedAt,
    )

    @Test
    fun `fresh snapshot keeps order and index`() {
        val result = snapshot().resolve(setOf("a", "b", "c"), now)
        assertEquals(listOf("a", "b", "c"), result?.trackIds)
        assertEquals(1, result?.index)
        assertEquals(42_000L, result?.positionMs)
    }

    @Test
    fun `missing tracks are dropped and index follows current track`() {
        val result = snapshot(ids = listOf("a", "b", "c"), currentId = "c", index = 2)
            .resolve(setOf("a", "c"), now)
        assertEquals(listOf("a", "c"), result?.trackIds)
        assertEquals(1, result?.index)
    }

    @Test
    fun `deleted current track falls back to clamped index`() {
        val result = snapshot(ids = listOf("a", "b", "c"), currentId = "b", index = 1)
            .resolve(setOf("a", "c"), now)
        assertEquals(listOf("a", "c"), result?.trackIds)
        assertEquals(1, result?.index)

        val tail = snapshot(ids = listOf("a", "b", "c"), currentId = "c", index = 2)
            .resolve(setOf("a"), now)
        assertEquals(0, tail?.index)
    }

    @Test
    fun `expired snapshot is rejected`() {
        val stale = snapshot(savedAt = now - PlaybackSnapshot.DEFAULT_MAX_AGE_MS - 1)
        assertNull(stale.resolve(setOf("a", "b", "c"), now))
    }

    @Test
    fun `snapshot without savedAt is rejected`() {
        assertNull(snapshot(savedAt = 0L).resolve(setOf("a", "b", "c"), now))
    }

    @Test
    fun `no surviving track yields null`() {
        assertNull(snapshot().resolve(setOf("x", "y"), now))
    }

    @Test
    fun `empty snapshot yields null`() {
        assertNull(snapshot(ids = emptyList(), currentId = null, index = 0).resolve(setOf("a"), now))
    }

    @Test
    fun `negative position is clamped`() {
        val result = snapshot(positionMs = -5_000L).resolve(setOf("a", "b", "c"), now)
        assertEquals(0L, result?.positionMs)
    }
}
