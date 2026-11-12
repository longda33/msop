package com.primuse.lite.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `只填域名自动补 https`() {
        val out = ServerUrl.normalize("music.example.com")
        assertEquals("https://music.example.com", out.baseUrl)
        assertFalse(out.isPlainHttp)
    }

    @Test
    fun `反代子路径保留且去掉尾斜杠`() {
        val out = ServerUrl.normalize("https://example.com/openlist/")
        assertEquals("https://example.com/openlist", out.baseUrl)
        assertFalse(out.isPlainHttp)
    }

    @Test
    fun `明文 http 被标记出来供二次确认`() {
        val out = ServerUrl.normalize("http://192.168.1.10:5244")
        assertEquals("http://192.168.1.10:5244", out.baseUrl)
        assertTrue(out.isPlainHttp)
    }

    @Test
    fun `剥掉误粘的页面名 query 和 fragment`() {
        val out = ServerUrl.normalize("  https://example.com/openlist/index.html?a=1#/music ")
        assertEquals("https://example.com/openlist", out.baseUrl)
    }

    @Test
    fun `剥掉页面名时不影响根路径`() {
        val out = ServerUrl.normalize("https://example.com/index.html")
        assertEquals("https://example.com", out.baseUrl)
    }

    @Test
    fun `主机名与默认端口规范化`() {
        val out = ServerUrl.normalize("HTTPS://EXAMPLE.COM:443/")
        assertEquals("https://example.com", out.baseUrl)
    }

    @Test
    fun `api 拼接支持反代子路径且自动编码`() {
        assertEquals(
            "https://example.com/openlist/api/fs/list",
            ServerUrl.api("https://example.com/openlist", "api/fs/list").toString(),
        )
        assertEquals(
            "https://example.com/api/auth/login",
            ServerUrl.api("https://example.com", "/api/auth/login").toString(),
        )
    }

    @Test
    fun `内部 URI 与路径互转`() {
        assertEquals("openlist:/a/b.flac", ServerUrl.internalUri("/a/b.flac"))
        assertEquals("/a/b.flac", ServerUrl.pathFromInternalUri("openlist:/a/b.flac"))
        assertEquals("/x.flac", ServerUrl.pathFromInternalUri("openlist:x.flac"))
        assertNull(ServerUrl.pathFromInternalUri("content://media/1"))
        assertNull(ServerUrl.pathFromInternalUri("https://example.com/a.flac"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空地址拒绝`() {
        ServerUrl.normalize("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法地址拒绝`() {
        ServerUrl.normalize("https://")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非 http 协议拒绝`() {
        ServerUrl.normalize("ftp://example.com")
    }
}
