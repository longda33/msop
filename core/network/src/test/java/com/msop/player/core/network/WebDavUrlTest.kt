package com.msop.player.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WebDAV 内部路径与真实 URL 的换算（P7-07）。纯 JVM 单测。 */
class WebDavUrlTest {

    @Test
    fun `路径归一化折叠斜杠并保留根`() {
        assertEquals("/", WebDavUrl.normalizePath("///"))
        assertEquals("/", WebDavUrl.normalizePath(""))
        assertEquals("/a/b", WebDavUrl.normalizePath("a//b/"))
        assertEquals("/a/b", WebDavUrl.normalizePath("\\a\\b\\"))
        assertEquals("/a/b", WebDavUrl.normalizePath("/a/b/"))
    }

    @Test
    fun `根路径与子路径拼接`() {
        assertEquals("/music/a.flac", WebDavUrl.combine("/music", "/a.flac"))
        assertEquals("/a.flac", WebDavUrl.combine("/", "/a.flac"))
        assertEquals("/music", WebDavUrl.combine("/music", "/"))
        assertEquals("/", WebDavUrl.combine("/", "/"))
        assertEquals("/m/a.flac", WebDavUrl.combine("/m/", "a.flac"))
    }

    @Test
    fun `内部 URI 往返且不包含真实 URL`() {
        assertEquals("webdav:/music/中文 歌.flac", WebDavUrl.internalUri("/music/中文 歌.flac"))
        assertEquals("/music/中文 歌.flac", WebDavUrl.pathFromInternalUri("webdav:/music/中文 歌.flac"))
        assertNull(WebDavUrl.pathFromInternalUri("openlist:/a.flac"))
        assertNull(WebDavUrl.pathFromInternalUri("content://media/1"))
    }

    @Test
    fun `播放地址由 base 根路径与文件路径组成`() {
        val url = WebDavUrl.buildUrl(
            baseUrl = "https://dav.example.com/dav",
            rootPath = "/music",
            path = "/Album/中文 歌 #1.flac",
        )
        assertEquals("https", url.scheme)
        assertEquals("dav.example.com", url.host)
        assertEquals("dav/music/Album/中文 歌 #1.flac", url.pathSegments.joinToString("/").trimStart('/'))
        // 实际传输的编码形式必须把空格与 # 都转义，不能把 # 当成 fragment
        assertEquals(null, url.fragment)
        assertEquals(
            listOf("dav", "music", "Album", "中文 歌 #1.flac"),
            url.pathSegments.filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `根路径为斜杠时不重复拼接`() {
        val url = WebDavUrl.buildUrl("https://dav.example.com", "/", "/a.mp3")
        assertEquals("https://dav.example.com/a.mp3", url.toString())
    }

    @Test
    fun `href 还原内部路径 支持绝对与相对形式`() {
        val base = "https://dav.example.com/dav".toHttpUrl()

        assertEquals(
            "/Album/a.flac",
            WebDavUrl.pathFromHref("https://dav.example.com/dav/music/Album/a.flac", base, "/music"),
        )
        assertEquals(
            "/Album/a.flac",
            WebDavUrl.pathFromHref("/dav/music/Album/a.flac", base, "/music"),
        )
        assertEquals(
            "/Album/a.flac",
            WebDavUrl.pathFromHref("music/Album/a.flac", base, "/music"),
        )
        // 百分号编码的中文
        assertEquals(
            "/中文/a.flac",
            WebDavUrl.pathFromHref(
                "https://dav.example.com/dav/music/%E4%B8%AD%E6%96%87/a.flac",
                base,
                "/music",
            ),
        )
        // 根目录本身（带尾斜杠）
        assertEquals("/", WebDavUrl.pathFromHref("/dav/music/", base, "/music"))
    }

    @Test
    fun `href 不在根路径下时返回 null`() {
        val base = "https://dav.example.com/dav".toHttpUrl()
        assertNull(WebDavUrl.pathFromHref("/dav/other/a.flac", base, "/music"))
        assertNull(WebDavUrl.pathFromHref("/etc/passwd", base, "/music"))
        assertNull(WebDavUrl.pathFromHref("", base, "/music"))
    }
}
