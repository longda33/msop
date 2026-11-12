package com.msop.player.feature.local.metadata

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalMetadataEnricherTest {

    private lateinit var dir: File
    private lateinit var cache: CoverCache

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("msop-enrich-test").toFile()
        cache = CoverCache(dir, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class FakeStore(initial: List<Track> = emptyList()) : MetadataStore {
        val tracks = LinkedHashMap<String, Track>().apply { initial.forEach { put(it.id, it) } }

        override suspend fun tracksNeedingMetadata(limit: Int): List<Track> =
            tracks.values.filter { it.durationMs == 0L }.take(limit)

        override suspend fun tracksMissingCover(limit: Int): List<Track> =
            tracks.values.filter { it.coverUri == null }.take(limit)

        override suspend fun upsertAll(tracks: List<Track>) {
            tracks.forEach { this.tracks[it.id] = it }
        }

        override suspend fun countWithCover(): Int = tracks.values.count { it.coverUri != null }

        override suspend fun referencedCovers(): Set<String> =
            tracks.values.mapNotNull { it.coverUri }.toHashSet()
    }

    private class FakeReader(
        private val tags: Map<String, TrackMetadata> = emptyMap(),
        private val covers: Map<String, ByteArray> = emptyMap(),
    ) : MetadataReader {
        var tagReads = 0
        var coverReads = 0

        override suspend fun readTags(uri: String): TrackMetadata? {
            tagReads++
            return tags[uri]
        }

        override suspend fun readCover(uri: String): ByteArray? {
            coverReads++
            return covers[uri]
        }
    }

    private fun track(
        id: String,
        uri: String = "content://media/external/audio/media/$id",
        title: String = id,
        durationMs: Long = 0L,
        coverUri: String? = null,
        path: String = "/storage/emulated/0/Music/$id.flac",
    ) = Track(
        id = id,
        source = SourceType.LOCAL,
        uri = uri,
        title = title,
        durationMs = durationMs,
        coverUri = coverUri,
        localPath = path,
    )

    private fun enricher(store: MetadataStore, reader: MetadataReader): LocalMetadataEnricher =
        LocalMetadataEnricher(store, reader, CoverProvider(store, reader, cache), cache)

    @Test
    fun `只补齐时长为零的曲目`() = runTest {
        val ready = track("ready", durationMs = 269_000L, title = "晴天")
        val pending = track("pending", durationMs = 0L, title = "稻香")
        val store = FakeStore(listOf(ready, pending))
        val reader = FakeReader(
            tags = mapOf(pending.uri to TrackMetadata(artist = "周杰伦", album = "魔杰座", durationMs = 223_000L)),
        )

        val stats = enricher(store, reader).enrichMissing()

        assertEquals(1, stats.candidates)
        assertEquals(1, stats.enriched)
        assertEquals(1, reader.tagReads)
        assertEquals("周杰伦", store.tracks["pending"]?.artist)
        assertEquals(223_000L, store.tracks["pending"]?.durationMs)
        // 已经有时长的那首不该被再读一遍
        assertNull(store.tracks["ready"]?.artist)
    }

    @Test
    fun `读不到标签计入失败且不影响其他曲目`() = runTest {
        val broken = track("broken", uri = "content://broken")
        val ok = track("ok")
        val store = FakeStore(listOf(broken, ok))
        val reader = FakeReader(tags = mapOf(ok.uri to TrackMetadata(artist = "陈奕迅", durationMs = 1_000L)))

        val stats = enricher(store, reader).enrichMissing()

        assertEquals(2, stats.candidates)
        assertEquals(1, stats.enriched)
        assertEquals(1, stats.failed)
        assertEquals("陈奕迅", store.tracks["ok"]?.artist)
        // 失败的那首保持原样，下次扫描还会重试
        assertEquals(0L, store.tracks["broken"]?.durationMs)
    }

    @Test
    fun `没有候选时不读任何文件`() = runTest {
        val store = FakeStore(listOf(track("a", durationMs = 100L)))
        val reader = FakeReader()

        val stats = enricher(store, reader).enrichMissing()

        assertTrue(stats.isEmpty)
        assertEquals(0, reader.tagReads)
    }

    @Test
    fun `文件名兜底的标题会被标签替换`() = runTest {
        val t = track("a", title = "track01", path = "/storage/emulated/0/Music/track01.flac")
        val store = FakeStore(listOf(t))
        val reader = FakeReader(tags = mapOf(t.uri to TrackMetadata(title = "晴天", durationMs = 1L)))

        enricher(store, reader).enrichMissing()

        assertEquals("晴天", store.tracks["a"]?.title)
    }

    @Test
    fun `预热封面会落盘并回写 coverUri`() = runTest {
        val t = track("a")
        val store = FakeStore(listOf(t))
        val reader = FakeReader(covers = mapOf(t.uri to byteArrayOf(1, 2, 3, 4, 5)))

        val stats = enricher(store, reader).warmCovers()

        assertEquals(1, stats.candidates)
        assertEquals(1, stats.covers)
        val coverUri = store.tracks["a"]?.coverUri
        assertNotNull(coverUri)
        assertTrue(coverUri!!.startsWith("file://"))
        assertTrue(File(coverUri.removePrefix("file://")).isFile)
    }

    @Test
    fun `同一张专辑的多首歌共用一份封面文件`() = runTest {
        // 内嵌图字节完全相同，按内容哈希命名才能合并成一份
        val art = byteArrayOf(11, 22, 33, 44)
        val first = track("a")
        val second = track("b")
        val store = FakeStore(listOf(first, second))
        val reader = FakeReader(covers = mapOf(first.uri to art, second.uri to art))

        enricher(store, reader).warmCovers()

        assertEquals(1, cache.count())
        assertEquals(store.tracks["a"]?.coverUri, store.tracks["b"]?.coverUri)
    }

    @Test
    fun `已有封面的曲目不会被再次预热`() = runTest {
        val cached = track("cached", coverUri = "file:///tmp/exists.jpg")
        val missing = track("missing")
        val store = FakeStore(listOf(cached, missing))
        val reader = FakeReader(covers = mapOf(missing.uri to byteArrayOf(1)))

        val stats = enricher(store, reader).warmCovers()

        assertEquals(1, stats.candidates)
        assertEquals(1, reader.coverReads)
    }

    @Test
    fun `没有内嵌封面的曲目不会写入数据库`() = runTest {
        val t = track("a")
        val store = FakeStore(listOf(t))
        val reader = FakeReader()

        val stats = enricher(store, reader).warmCovers()

        assertEquals(1, stats.candidates)
        assertEquals(0, stats.covers)
        assertNull(store.tracks["a"]?.coverUri)
        assertEquals(0, cache.count())
    }

    @Test
    fun `曲目被删后孤立封面会被清理`() = runTest {
        val t = track("a")
        val store = FakeStore(listOf(t))
        val reader = FakeReader(covers = mapOf(t.uri to byteArrayOf(1, 2, 3)))
        val enricher = enricher(store, reader)

        enricher.warmCovers()
        assertEquals(1, cache.count())

        // 模拟全量扫描发现文件已删除，把曲目从库里清掉
        store.tracks.clear()

        assertEquals(1, enricher.sweepOrphanCovers())
        assertEquals(0, cache.count())
    }

    @Test
    fun `仍被引用的封面不会被清理`() = runTest {
        val t = track("a")
        val store = FakeStore(listOf(t))
        val reader = FakeReader(covers = mapOf(t.uri to byteArrayOf(1, 2, 3)))
        val enricher = enricher(store, reader)

        enricher.warmCovers()

        assertEquals(0, enricher.sweepOrphanCovers())
        assertEquals(1, cache.count())
    }
}
