package com.msop.player.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 远端音频的**小范围 Range 读取**（时长 / 内嵌封面 / 内嵌歌词共用）。
 *
 * 2026-11-18 修复：三个 Probe 曾各自实现 `range()`，其中两个在判断响应体大小之前
 * 就调用 `body.bytes()`。服务器忽略 `Range`（回 200 + 整首歌）时会把几十 MB 读进堆，
 * 4 路并发足以 OOM；`RemoteDurationProbe` 早在 11-17 已改成流式限长读取，另两个漏改。
 * 这里把正确实现收敛为一份，避免第三次分叉。
 *
 * 约束：
 * - 最多读取 `length * 8` 字节，超出部分不分配、直接关流；
 * - 尾部请求（`start == null`）只接受 206：服务器回 200 时拿到的是文件开头，
 *   按尾部解析 MP4 `moov` 会得到错误结果；
 * - 所有异常都当「探测失败」返回 null，不能影响同步 / 播放主链路。
 */
internal object RemoteRangeReader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun range(
        url: String,
        headers: Map<String, String> = emptyMap(),
        start: Long?,
        length: Long,
    ): ByteArray? = range(http, url, headers, start, length)

    /** 供 JVM 单测注入本地 HttpServer；生产代码走上面的默认客户端。 */
    internal fun range(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        start: Long?,
        length: Long,
    ): ByteArray? {
        val cap = length * 8L
        val rangeValue = if (start == null) "bytes=-$length" else "bytes=$start-${start + length - 1}"
        val builder = Request.Builder().url(url).header("Range", rangeValue).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val declared = resp.body.contentLength()
                if (start == null && (resp.code != 206 || (declared >= 0 && declared > cap))) {
                    return null
                }
                val limit = if (declared >= 0) minOf(declared, cap) else cap
                val out = ByteArrayOutputStream(limit.coerceAtMost(length).toInt())
                resp.body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (total < limit) {
                        val want = minOf(8 * 1024L, limit - total).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
