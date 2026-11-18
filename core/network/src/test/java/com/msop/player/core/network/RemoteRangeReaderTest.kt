package com.msop.player.core.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Range 读取的安全边界（2026-11-18）。
 *
 * 重点是「服务器忽略 Range」时不能把整首歌读进堆：头部请求要截断到上限，
 * 尾部请求要直接放弃（回 200 时拿到的是文件开头，不是尾部）。
 */
class RemoteRangeReaderTest {

    private val client = OkHttpClient()

    @Test
    fun headRequest_isCappedWhenServerIgnoresRange() {
        val server = serve { exchange ->
            // 模拟不支持 Range 的服务器：无论请求什么都回 200 + 完整文件
            assertEquals("bytes=0-63", exchange.requestHeaders.getFirst("Range"))
            exchange.respond(200, ByteArray(4096) { (it % 251).toByte() })
        }
        try {
            val bytes = RemoteRangeReader.range(client, server.url(), emptyMap(), 0L, 64L)
            assertTrue(bytes != null)
            // length * 8 = 512；多出来的 3584 字节绝不能被读进内存
            assertEquals(512, bytes!!.size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun tailRequest_returning200IsRejected() {
        val server = serve { exchange ->
            assertEquals("bytes=-64", exchange.requestHeaders.getFirst("Range"))
            exchange.respond(200, "file-start-not-tail".toByteArray())
        }
        try {
            assertNull(RemoteRangeReader.range(client, server.url(), emptyMap(), null, 64L))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun tailRequest_returning206IsAccepted() {
        val payload = "tail-bytes".toByteArray()
        val server = serve { it.respond(206, payload) }
        try {
            val bytes = RemoteRangeReader.range(client, server.url(), emptyMap(), null, 64L)
            assertTrue(bytes != null)
            assertTrue(payload.contentEquals(bytes!!))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun customHeadersAreForwarded() {
        val server = serve { exchange ->
            assertEquals("Basic dTpw", exchange.requestHeaders.getFirst("Authorization"))
            exchange.respond(206, "ok".toByteArray())
        }
        try {
            val url = server.url()
            val bytes = RemoteRangeReader.range(
                client,
                url,
                mapOf("Authorization" to "Basic dTpw"),
                0L,
                16L,
            )
            assertEquals("ok", bytes?.toString(Charsets.UTF_8))
        } finally {
            server.stop(0)
        }
    }

    private fun serve(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/", handler)
            start()
        }

    private fun HttpServer.url(): String = "http://127.0.0.1:${address.port}/range"

    private fun HttpExchange.respond(code: Int, body: ByteArray) {
        sendResponseHeaders(code, body.size.toLong())
        responseBody.use { it.write(body) }
    }
}
