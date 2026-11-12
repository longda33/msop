package com.primuse.lite.feature.remote

import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.network.FsItem
import com.primuse.lite.core.network.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTrackMapperTest {

    @Test
    fun `只认常见音频格式且大小写不敏感`() {
        listOf("a.mp3", "a.FLAC", "a.m4a", "a.wav", "a.ogg", "a.opus", "a.aac")
            .forEach { assertTrue(it, RemoteIdFactory.isAudio(it)) }
        listOf("a.txt", "cover.jpg", "a.mp4", "a.pdf", "noext")
            .forEach { assertFalse(it, RemoteIdFactory.isAudio(it)) }
    }

    @Test
    fun `mime 类型按扩展名推断`() {
        assertEquals("audio/flac", RemoteIdFactory.mimeType("song.flac"))
        assertEquals("audio/mpeg", RemoteIdFactory.mimeType("song.MP3"))
        assertNull(RemoteIdFactory.mimeType("song.txt"))
    }

    @Test
    fun `标题去掉扩展名但保留没有扩展名的文件`() {
        assertEquals("Butter-Fly - 和田光司", RemoteIdFactory.displayTitle("Butter-Fly - 和田光司.flac"))
        assertEquals("README", RemoteIdFactory.displayTitle("README"))
    }

    @Test
    fun `路径归一化与拼接`() {
        assertEquals("/", RemoteIdFactory.normalizePath("///"))
        assertEquals("/a/b", RemoteIdFactory.normalizePath("a//b/"))
        assertEquals("/a.flac", RemoteIdFactory.join("/", "a.flac"))
        assertEquals("/music/a.flac", RemoteIdFactory.join("/music", "a.flac"))
        assertEquals("/music/a.flac", RemoteIdFactory.join("/music/", "a.flac"))
    }

    @Test
    fun `父目录与专辑名`() {
        assertEquals("/music", RemoteIdFactory.parentPath("/music/a.flac"))
        assertEquals("/", RemoteIdFactory.parentPath("/a.flac"))
        assertEquals("music", RemoteIdFactory.parentName("/music/a.flac"))
        assertEquals("OpenList", RemoteIdFactory.parentName("/a.flac"))
    }

    @Test
    fun `ID 稳定且与路径绑定`() {
        assertEquals(RemoteIdFactory.id("/a.flac"), RemoteIdFactory.id("a.flac"))
        assertNotEquals(RemoteIdFactory.id("/a.flac"), RemoteIdFactory.id("/b.flac"))
        assertTrue(RemoteIdFactory.id("/a.flac").startsWith("openlist:"))
        assertNotEquals(RemoteIdFactory.id("/a.flac"), RemoteIdFactory.albumId("/a.flac"))
    }

    @Test
    fun `映射成统一 Track 且不含会过期的直链`() {
        val item = FsItem(
            name = "Butter-Fly - 和田光司.flac",
            size = 34_929_358L,
            modified = "2026-11-02T08:36:47Z",
        )
        val track = RemoteTrackMapper.toTrack(item, "/music/Butter-Fly - 和田光司.flac", 1_700_000_000L)

        assertEquals(SourceType.OPENLIST, track.source)
        assertEquals("Butter-Fly - 和田光司", track.title)
        assertEquals("music", track.album)
        assertEquals("openlist:/music/Butter-Fly - 和田光司.flac", track.uri)
        assertEquals("audio/flac", track.mimeType)
        assertEquals(34_929_358L, track.sizeBytes)
        assertEquals(0L, track.durationMs)
        assertNull(track.coverUri)
        assertNull(track.localPath)
        assertEquals(1_700_000_000L, track.addedAt)
        assertFalse("uri 不能是带签名的 http 直链", track.uri.startsWith("http"))
        assertEquals("/music/Butter-Fly - 和田光司.flac", ServerUrl.pathFromInternalUri(track.uri))
    }

    @Test
    fun `ISO 时间解析成秒 无法解析时退化为 0`() {
        val parsed = RemoteTrackMapper.parseEpochSeconds("2026-11-02T08:36:47Z")
        assertEquals(java.time.Instant.parse("2026-11-02T08:36:47Z").epochSecond, parsed)
        assertTrue(parsed > 1_700_000_000L)
        assertEquals(0L, RemoteTrackMapper.parseEpochSeconds(null))
        assertEquals(0L, RemoteTrackMapper.parseEpochSeconds("not a date"))
    }

    @Test
    fun `服务端无封面时维持 null 不造假数据`() {
        val track = RemoteTrackMapper.toTrack(FsItem("a.mp3", thumb = ""), "/a.mp3", 1L)
        assertNull(track.coverUri)
    }
}
