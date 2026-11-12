package com.msop.player.feature.local.scan

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
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

    private class FakeStore(
        var latest: Long? = null,
        preset: List<Track> = emptyList(),
    ) : LocalTrackStore {
        val stored = LinkedHashMap<String, Track>().apply {
            preset.forEach { put(it.id, it) }
        }

        override suspend fun allIds(): Set<String> = stored.keys.toSet()

        override suspend fun latestModified(): Long? = latest

        override suspend fun upsertAll(tracks: List<Track>) {
            tracks.forEach { stored[it.id] = it }
        }

        override suspend fun deleteByIds(ids: List<String>) {
            ids.forEach { stored.remove(it) }
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
    fun `跨页重复的 id 只算一次写入`() = runTest {
        // SAF 与 MediaStore 会分两页返回同一个文件，去重必须跨页生效
        val source = FakeSource(listOf(listOf(track("a"), track("b")), listOf(track("b"), track("c"))))
        val store = FakeStore()

        val stats = LocalLibrarySyncer(source, store).sync()

        assertEquals(4, stats.scanned)
        assertEquals(3, stats.written)
        assertEquals(1, stats.deduped)
        assertEquals(3, store.count())
    }

    @Test
    fun `重复记录仍会写入，后出现的元数据覆盖先出现的`() = runTest {
        // 顺序约定：SAF（只有路径）在前，MediaStore（有 album）在后。
        // 去重不能把后到的那份丢掉，否则元数据会退化。
        val lean = track("a").copy(artist = null, album = null, durationMs = 0L)
        val rich = track("a").copy(artist = "周杰伦", album = "叶惠美", durationMs = 269_000L)
        val source = FakeSource(listOf(listOf(lean), listOf(rich)))
        val store = FakeStore()

        val stats = LocalLibrarySyncer(source, store).sync()

        assertEquals(2, stats.scanned)
        assertEquals(1, stats.written)
        assertEquals(1, stats.deduped)
        assertEquals("周杰伦", store.stored["a"]?.artist)
        assertEquals(269_000L, store.stored["a"]?.durationMs)
    }

    @Test
    fun `扫描计数满足 扫描数 = 写入数 + 去重数`() = runTest {
        val source = FakeSource(listOf(listOf(track("a"), track("a"), track("b"))))

        val stats = LocalLibrarySyncer(source, FakeStore()).sync()

        assertEquals(stats.scanned, stats.written + stats.deduped)
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

    @Test
    fun `全量扫描会删除库中已不存在的条目`() = runTest {
        // 库里原本有 a、b、gone，本次只扫到 a、b
        val store = FakeStore(preset = listOf(track("a"), track("b"), track("gone")))
        val source = FakeSource(listOf(listOf(track("a"), track("b"))))

        val stats = LocalLibrarySyncer(source, store).sync(full = true)

        assertEquals(1, stats.deleted)
        assertEquals(setOf("a", "b"), store.allIds())
    }

    @Test
    fun `增量扫描不删除任何条目`() = runTest {
        // 增量只看得到变更文件，据此清理会误删整个曲库
        val store = FakeStore(
            latest = 1_700_000_000L,
            preset = listOf(track("a"), track("b"), track("c")),
        )
        val source = FakeSource(listOf(listOf(track("a"))))

        val stats = LocalLibrarySyncer(source, store).sync(full = false)

        assertEquals(0, stats.deleted)
        assertEquals(3, store.count())
    }

    @Test
    fun `全量扫描读到零首时不清理曲库`() = runTest {
        // 安全阀：权限被撤销时全量扫描返回空，照常清理会清空整个库
        val store = FakeStore(preset = listOf(track("a"), track("b")))
        val source = FakeSource(emptyList())

        val stats = LocalLibrarySyncer(source, store).sync(full = true)

        assertEquals(0, stats.deleted)
        assertEquals(2, store.count())
    }

    @Test
    fun `全量扫描结果与库内完全一致时不删除`() = runTest {
        val store = FakeStore(preset = listOf(track("a"), track("b")))
        val source = FakeSource(listOf(listOf(track("a"), track("b"))))

        val stats = LocalLibrarySyncer(source, store).sync(full = true)

        assertEquals(0, stats.deleted)
        assertEquals(2, store.count())
    }
}
