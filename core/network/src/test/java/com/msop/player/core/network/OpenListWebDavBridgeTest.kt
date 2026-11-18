package com.msop.player.core.network

import com.msop.player.core.datastore.WebDavConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** WebDAV 音源指向 OpenList /dav 时的播放桥（2026-11-17）。 */
class OpenListWebDavBridgeTest {

    @Test
    fun restBaseFor_stripsOnlyDavSegment() {
        assertEquals(
            "http://172.207.160.128:5244",
            OpenListWebDavBridge.restBaseFor("http://172.207.160.128:5244/dav"),
        )
        assertEquals(
            "https://example.com/openlist",
            OpenListWebDavBridge.restBaseFor("https://example.com/openlist/dav"),
        )
        assertNull(OpenListWebDavBridge.restBaseFor("https://dav.2dland.cn"))
        assertNull(OpenListWebDavBridge.restBaseFor("https://example.com/webdav"))
    }

    @Test
    fun rootPathIsPrependedToRelativePath() {
        assertEquals(
            "/cd18/Music/Nivis/a.flac",
            WebDavUrl.combine("/cd18/Music", "/Nivis/a.flac"),
        )
    }

    @Test
    fun rawUrlUsesNativeRestAndLeavesSessionForEmptyHeaders() {
        val loginCalls = AtomicInteger()
        val fsGetCalls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${server.address.port}/dav"
        server.createContext("/api/auth/login") { exchange ->
            loginCalls.incrementAndGet()
            val body = exchange.readBody()
            assertTrue(body.contains("admin"))
            assertTrue(body.contains("secret"))
            exchange.respond(200, """{"code":200,"message":"success","data":{"token":"jwt-1"}}""")
        }
        server.createContext("/api/fs/get") { exchange ->
            fsGetCalls.incrementAndGet()
            assertEquals("jwt-1", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals(
                "/cd18/Music/Nivis/a.flac",
                exchange.readBody().let { body ->
                    // 不做完整 JSON 解析，只验证路径确实带上了用户填的音乐目录
                    body.substringAfter("\"path\":\"").substringBefore("\"")
                },
            )
            exchange.respond(
                200,
                """{"code":200,"data":{"raw_url":"http://127.0.0.1:${server.address.port}/raw/a.flac"}}""",
            )
        }
        server.start()
        try {
            val bridge = OpenListWebDavBridge()
            val config = WebDavConfig(
                baseUrl = base,
                username = "admin",
                password = "secret",
                rootPath = "/cd18/Music",
                allowPlainHttp = true,
            )
            assertFalse(bridge.hasSession(config))
            assertEquals(
                "http://127.0.0.1:${server.address.port}/raw/a.flac",
                bridge.rawUrlOrNull(config, "/Nivis/a.flac"),
            )
            assertTrue(bridge.hasSession(config)) // headers() 据此返回空 map，不给 raw_url 带 Basic 头
            assertEquals(1, loginCalls.get())
            assertEquals(1, fsGetCalls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun plainWebDavDoesNotHitNativeApi() {
        val bridge = OpenListWebDavBridge()
        val config = WebDavConfig(
            baseUrl = "https://dav.example.com",
            username = "u",
            password = "p",
        )
        assertNull(bridge.rawUrlOrNull(config, "/a.flac"))
        assertFalse(bridge.hasSession(config))
    }

    @Test
    fun nonJsonLoginResponseFallsBackWithoutThrowing() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/auth/login") { exchange ->
            val bytes = "<html>not openlist</html>".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val bridge = OpenListWebDavBridge()
            val config = WebDavConfig(
                baseUrl = "http://127.0.0.1:${server.address.port}/dav",
                username = "u",
                password = "p",
                allowPlainHttp = true,
            )
            assertNull(bridge.rawUrlOrNull(config, "/a.flac"))
            assertFalse(bridge.hasSession(config))
        } finally {
            server.stop(0)
        }
    }

    @Test(expected = WebDavException.PlainHttpNotConfirmed::class)
    fun plainHttpWithoutConfirmationNeverLogsIn() {
        // 未二次确认时连登录请求都不能发：否则账号密码会明文 POST 出去。
        OpenListWebDavBridge().rawUrlOrNull(
            WebDavConfig(
                baseUrl = "http://127.0.0.1:1/dav",
                username = "u",
                password = "p",
            ),
            "/a.flac",
        )
    }

    private fun HttpExchange.readBody(): String = requestBody.bufferedReader().use { it.readText() }

    private fun HttpExchange.respond(code: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
