package com.msop.player.feature.remote

import com.msop.player.core.model.SourceType
import com.msop.player.core.network.WebDavItem
import com.msop.player.core.network.WebDavUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WebDAV 目录项 → 统一 Track（P7-07）。纯 JVM 单测。 */
class WebDavTrackMapperTest {

    private fun item(
        path: String = "/music/Album/Butter-Fly.flac",
        name: String = "Butter-Fly.flac",
        isDir: Boolean = false,
        size: Long? = 34_929_358L,
        lastModified: Long = 1_700_000_000L,
        mimeType: String? = null,
    ) = WebDavItem(
        path = path,
        name = name,
        isDir = isDir,
        sizeBytes = size,
        lastModifiedSeconds = lastModified,
        mimeType = mimeType,
    )

    @Test
    fun `映射成统一 Track 且源为 WEBDAV`() {
        val track = WebDavTrackMapper.toTrack(item(), nowSeconds = 1_700_000_100L)

        assertEquals(SourceType.WEBDAV, track.source)
        assertEquals("Butter-Fly", track.title)
        assertEquals("Album", track.album)
        assertEquals("webdav:/music/Album/Butter-Fly.flac", track.uri)
        assertEquals("audio/flac", track.mimeType)
        assertEquals(34_929_358L, track.sizeBytes)
        assertEquals(1_700_000_000L, track.lastModified)
        assertEquals(1_700_000_100L, track.addedAt)
        assertEquals(0L, track.durationMs)
        assertNull(track.localPath)
        assertNull(track.coverUri)
        assertNull(track.albumId)
        assertFalse("uri 不能是真实 URL", track.uri.startsWith("http"))
    }

    @Test
    fun `ID 稳定且与 OpenList 命名空间隔离`() {
        val a = WebDavTrackMapper.toTrack(item(), 1L)
        val b = WebDavTrackMapper.toTrack(item(path = "music/Album/Butter-Fly.flac"), 2L)
        val c = WebDavTrackMapper.toTrack(item(path = "/music/Album/Other.flac"), 3L)

        assertTrue(a.id.startsWith("webdav:"))
        assertEquals("同一路径不同写法应得到同一个 id", a.id, b.id)
        assertNotEquals(a.id, c.id)
        assertNotEquals(RemoteIdFactory.id("/music/Album/Butter-Fly.flac"), a.id)
    }

    @Test
    fun `根目录下的文件专辑回退为 WebDAV`() {
        val track = WebDavTrackMapper.toTrack(item(path = "/a.flac", name = "a.flac"), 1L)
        assertEquals("WebDAV", track.album)
        assertEquals("webdav:/a.flac", track.uri)
    }

    @Test
    fun `扩展名优先于服务器给的 MIME`() {
        // 部分服务器对音频返回 application/octet-stream，交给 Media3 会识别不出格式
        val track = WebDavTrackMapper.toTrack(item(mimeType = "application/octet-stream"), 1L)
        assertEquals("audio/flac", track.mimeType)

        // 扩展名不认识时才用服务器的 MIME
        val unknown = WebDavTrackMapper.toTrack(
            item(path = "/a.bin", name = "a.bin", mimeType = "audio/mpeg"),
            1L,
        )
        assertEquals("audio/mpeg", unknown.mimeType)
    }

    @Test
    fun `内部 URI 能还原成同步时的相对路径`() {
        val track = WebDavTrackMapper.toTrack(item(), 1L)
        assertEquals("/music/Album/Butter-Fly.flac", WebDavUrl.pathFromInternalUri(track.uri))
    }
}
