package com.msop.player.feature.remote

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.SubsonicSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubsonicMetadataTest {

    private fun song(
        path: String? = "/od-1/music/Butter-Fly - 和田光司.flac",
        title: String = "Butter-Fly",
        artist: String? = "和田光司",
        album: String? = "Butter-Fly",
        coverArt: String? = "al-e32892411a34",
        size: Long = 34_929_358L,
        contentType: String? = "audio/flac",
    ) = SubsonicSong(
        id = "so-1",
        title = title,
        album = album,
        artist = artist,
        coverArt = coverArt,
        size = size,
        contentType = contentType,
        path = path,
        albumId = "al-e32892411a34",
    )

    private fun track(
        uri: String = "openlist:/Butter-Fly - 和田光司.flac",
        title: String = "Butter-Fly - 和田光司",
        artist: String? = null,
        album: String? = "OpenList",
        coverUri: String? = null,
    ) = Track(
        id = "openlist:abc",
        source = SourceType.OPENLIST,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        coverUri = coverUri,
    )

    @Test
    fun `从绝对路径取文件名`() {
        assertEquals("a.flac", SubsonicMetadata.fileNameOf("/x/y/a.flac"))
        assertEquals("a.flac", SubsonicMetadata.fileNameOf("a.flac"))
        assertNull(SubsonicMetadata.fileNameOf(null))
        assertNull(SubsonicMetadata.fileNameOf("   "))
    }

    @Test
    fun `从 Track 的内部 URI 取文件名`() {
        assertEquals("a.flac", SubsonicMetadata.fileNameOfTrack(track(uri = "openlist:/x/a.flac")))
    }

    @Test
    fun `映射成元数据并兜底 mime`() {
        val meta = SubsonicMetadata.toMeta(song())
        assertNotNull(meta)
        assertEquals("Butter-Fly - 和田光司.flac", meta!!.fileName)
        assertEquals("Butter-Fly", meta.title)
        assertEquals("和田光司", meta.artist)
        assertEquals("al-e32892411a34", meta.albumId)
        assertEquals("audio/flac", meta.mimeType)
        assertEquals(34_929_358L, meta.sizeBytes)
    }

    @Test
    fun `contentType 缺失时按扩展名推断`() {
        val meta = SubsonicMetadata.toMeta(song(contentType = null))
        assertEquals("audio/flac", meta?.mimeType)
    }

    @Test
    fun `没有 path 的歌曲被跳过`() {
        assertNull(SubsonicMetadata.toMeta(song(path = null)))
        assertNull(SubsonicMetadata.toMeta(song(path = "")))
    }

    @Test
    fun `按文件名建索引且同名只保留第一个`() {
        val a = song(path = "/m/a.flac", title = "A1")
        val b = song(path = "/m/a.flac", title = "A2")
        val c = song(path = "/m/b.flac", title = "B")
        val index = SubsonicMetadata.index(listOf(a, b, c))
        assertEquals(2, index.size)
        assertEquals("A1", index["a.flac"]?.title)
        assertEquals("B", index["b.flac"]?.title)
    }

    @Test
    fun `补全覆盖艺术家专辑封面与标题`() {
        val meta = SubsonicMetadata.toMeta(song())!!
        val out = SubsonicMetadata.enrich(track(), meta) { "https://example.com/cover?id=$it" }
        assertEquals("Butter-Fly", out.title)
        assertEquals("和田光司", out.artist)
        assertEquals("Butter-Fly", out.album)
        assertEquals("al-e32892411a34", out.albumId)
        assertEquals("audio/flac", out.mimeType)
        assertEquals("https://example.com/cover?id=al-e32892411a34", out.coverUri)
    }

    @Test
    fun `没有匹配到元数据时原样返回`() {
        val original = track()
        assertEquals(original, SubsonicMetadata.enrich(original, null) { "x" })
    }

    @Test
    fun `空标题不覆盖已有标题`() {
        val meta = SubsonicMetadata.toMeta(song(title = ""))!!
        val out = SubsonicMetadata.enrich(track(title = "原标题"), meta) { "x" }
        assertEquals("原标题", out.title)
    }

    @Test
    fun `封面构造失败时保留原封面`() {
        val meta = SubsonicMetadata.toMeta(song())!!
        val out = SubsonicMetadata.enrich(track(coverUri = "file://old.jpg"), meta) {
            throw IllegalStateException("boom")
        }
        assertEquals("file://old.jpg", out.coverUri)
    }

    @Test
    fun `size 为 0 时不覆盖已有大小`() {
        val meta = SubsonicMetadata.toMeta(song(size = 0))!!
        val out = SubsonicMetadata.enrich(
            track().copy(sizeBytes = 123L),
            meta,
        ) { "x" }
        assertEquals(123L, out.sizeBytes)
    }
}
