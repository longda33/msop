package com.msop.player.ui.library

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5-04：曲库列表点击下标索引。
 *
 * 这些用例锁住的是「5 万首库点歌不掉帧」的实现前提：
 * 同一个列表重复查询不重建索引，列表换引用后必须重建。
 */
class TrackIndexTest {

    private fun track(id: String) = Track(
        id = id,
        source = SourceType.LOCAL,
        uri = "content://test/$id",
        title = "曲目 $id",
    )

    @Test
    fun `按下标取到正确位置`() {
        val list = listOf(track("a"), track("b"), track("c"))
        val index = TrackIndex()
        assertEquals(0, index.indexOf(list, "a"))
        assertEquals(1, index.indexOf(list, "b"))
        assertEquals(2, index.indexOf(list, "c"))
    }

    @Test
    fun `找不到返回负一`() {
        assertEquals(-1, TrackIndex().indexOf(listOf(track("a")), "nope"))
    }

    @Test
    fun `空列表返回负一`() {
        assertEquals(-1, TrackIndex().indexOf(emptyList(), "a"))
    }

    @Test
    fun `同一列表重复查询命中缓存`() {
        val list = listOf(track("a"), track("b"))
        val index = TrackIndex()
        index.indexOf(list, "a")
        assertTrue(index.isCachedFor(list))
        index.indexOf(list, "b")
        assertTrue(index.isCachedFor(list))
    }

    @Test
    fun `列表换引用后重建索引`() {
        val first = listOf(track("a"), track("b"))
        val second = listOf(track("b"), track("a"))
        val index = TrackIndex()

        assertEquals(0, index.indexOf(first, "a"))
        assertTrue(index.isCachedFor(first))

        // 内容相同但引用不同，必须重建——否则会拿到旧下标
        assertEquals(1, index.indexOf(second, "a"))
        assertTrue(index.isCachedFor(second))
        assertFalse(index.isCachedFor(first))
    }

    @Test
    fun `内容相同的新列表按新顺序返回`() {
        val index = TrackIndex()
        val a = track("a")
        val b = track("b")
        assertEquals(1, index.indexOf(listOf(a, b), "b"))
        assertEquals(0, index.indexOf(listOf(b, a), "b"))
    }

    @Test
    fun `五万首规模下结果正确`() {
        val list = List(50_000) { track("t$it") }
        val index = TrackIndex()
        assertEquals(0, index.indexOf(list, "t0"))
        assertEquals(25_000, index.indexOf(list, "t25000"))
        assertEquals(49_999, index.indexOf(list, "t49999"))
        assertEquals(-1, index.indexOf(list, "t50000"))
    }
}
