package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顺序语义回归测试：后写入的来源会覆盖先写入的同 ID 记录，
 * 所以元数据更丰富的来源必须排在最后。
 */
class CompositeTrackSourceTest {

    private class FakeSource(
        private val pages: List<List<Track>>,
        private val label: String = "",
    ) : TrackPageSource {
        var lastSince: Long = -1L

        override suspend fun scan(
            since: Long,
            pageSize: Int,
            onPage: suspend (List<Track>) -> Unit,
        ): Int {
            lastSince = since
            var total = 0
            for (page in pages) {
                total += page.size
                onPage(page)
            }
            return total
        }
    }

    private fun track(id: String, album: String? = null, duration: Long = 0L) = Track(
        id = id,
        source = SourceType.LOCAL,
        uri = "content://x/$id",
        title = id,
        album = album,
        durationMs = duration,
    )

    @Test
    fun `返回各来源的曲目数之和`() = runTest {
        val composite = CompositeTrackSource(
            listOf(
                FakeSource(listOf(listOf(track("a"), track("b")))),
                FakeSource(listOf(listOf(track("c")))),
            ),
        )

        val total = composite.scan(0L, 500) { }

        assertEquals(3, total)
    }

    @Test
    fun `后一个来源的丰富元数据会覆盖前一个`() = runTest {
        // SAF 只有路径信息，MediaStore 有 album 和时长
        val saf = FakeSource(listOf(listOf(track("a", album = null, duration = 0L))))
        val mediaStore = FakeSource(listOf(listOf(track("a", album = "叶惠美", duration = 269_000L))))

        val emitted = mutableListOf<Track>()
        CompositeTrackSource(listOf(saf, mediaStore)).scan(0L, 500) { emitted += it }

        assertEquals(2, emitted.size)
        assertEquals("叶惠美", emitted.last().album)
        assertEquals(269_000L, emitted.last().durationMs)
    }

    @Test
    fun `顺序反过来会导致元数据退化`() = runTest {
        // 这个用例是为了固定住「不能把 MediaStore 放前面」这个约束
        val mediaStore = FakeSource(listOf(listOf(track("a", album = "叶惠美", duration = 269_000L))))
        val saf = FakeSource(listOf(listOf(track("a", album = null, duration = 0L))))

        val emitted = mutableListOf<Track>()
        CompositeTrackSource(listOf(mediaStore, saf)).scan(0L, 500) { emitted += it }

        assertEquals(null, emitted.last().album)
        assertEquals(0L, emitted.last().durationMs)
    }

    @Test
    fun `since 会透传给每一个来源`() = runTest {
        val first = FakeSource(emptyList())
        val second = FakeSource(emptyList())

        CompositeTrackSource(listOf(first, second)).scan(1_700_000_000L, 500) { }

        assertEquals(1_700_000_000L, first.lastSince)
        assertEquals(1_700_000_000L, second.lastSince)
    }

    @Test
    fun `空来源列表返回零`() = runTest {
        assertEquals(0, CompositeTrackSource(emptyList()).scan(0L, 500) { })
    }
}
