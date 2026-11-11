package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibrarySyncerTest {

    private class FakeSource(private val pages: List<List<Track>>) : TrackPageSource {
        var requestedSince: Long = -1L
        var requestedPageSize: Int = -1

        override suspend fun scan(
            since: Long,
            pageSize: Int,
            onPage: suspend (List<Track>) -> Unit,
        ): Int {
            requestedSince = since
            requestedPageSize = pageSize
            var total = 0
            for (page in pages) {
                total += page.size
                onPage(page)
            }
            return total
        }
    }

    private class FakeStore(var latest: Long? = null) : LocalTrackStore {
        val stored = LinkedHashMap<String, Track>()

        override suspend fun allIds(): Set<String> = stored.keys.toSet()

        override suspend fun latestModified(): Long? = latest

        override suspend fun upsertAll(tracks: List<Track>) {
            tracks.forEach { stored[it.id] = it }
        }

        override suspend fun count(): Int = stored.size
    }

    private fun track(id: String, title: String = id) = Track(
        id = id,
        source = SourceType.LOCAL,
        uri = "content://media/external/audio/media/$id",
        title = title,
    )

    @Test
    fun `全量扫描把两页曲目都写入`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"), track("b")), listOf(track("c"))))
        val store = FakeStore()

        val stats = LocalLibrarySyncer(source, store) { 1000L }.sync()

        assertEquals(3, stats.scanned)
        assertEquals(3, stats.written)
        assertEquals(0, stats.deduped)
        assertEquals(3, store.count())
    }

    @Test
    fun `默认使用库内最新修改时间作为增量起点`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"))))
        val store = FakeStore(latest = 1_700_000_000L)

        LocalLibrarySyncer(source, store).sync()

        assertEquals(1_700_000_000L, source.requestedSince)
    }

    @Test
    fun `全量模式忽略增量起点`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"))))
        val store = FakeStore(latest = 1_700_000_000L)

        LocalLibrarySyncer(source, store).sync(full = true)

        assertEquals(0L, source.requestedSince)
    }

    @Test
    fun `批内重复 id 会被去重`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"), track("a"), track("b"))))
        val store = FakeStore()

        val stats = LocalLibrarySyncer(source, store).sync()

        assertEquals(3, stats.scanned)
        assertEquals(2, stats.written)
        assertEquals(1, stats.deduped)
    }

    @Test
    fun `addedAt 为 0 时补上时间戳`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"))))
        val store = FakeStore()

        LocalLibrarySyncer(source, store) { 999L }.sync()

        assertEquals(999L, store.stored["a"]?.addedAt)
    }

    @Test
    fun `已有 addedAt 不被覆盖`() = runTest {
        val source = FakeSource(listOf(listOf(track("a").copy(addedAt = 5L))))
        val store = FakeStore()

        LocalLibrarySyncer(source, store) { 999L }.sync()

        assertEquals(5L, store.stored["a"]?.addedAt)
    }

    @Test
    fun `分页大小透传给数据源`() = runTest {
        val source = FakeSource(emptyList())

        LocalLibrarySyncer(source, FakeStore()).sync(pageSize = 250)

        assertEquals(250, source.requestedPageSize)
    }

    @Test
    fun `空数据源不写入任何东西`() = runTest {
        val stats = LocalLibrarySyncer(FakeSource(emptyList()), FakeStore()).sync()

        assertTrue(stats.isEmpty)
        assertEquals(0, stats.written)
    }
}
