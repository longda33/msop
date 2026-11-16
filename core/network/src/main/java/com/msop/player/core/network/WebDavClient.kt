package com.msop.player.core.network

import com.msop.player.core.datastore.WebDavConfig
import com.msop.player.core.datastore.WebDavConfigStore
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 列目录得到的一项。路径是相对用户配置根目录的内部路径，不是 URL。 */
data class WebDavItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val sizeBytes: Long?,
    val lastModifiedSeconds: Long,
    val mimeType: String?,
)

/**
 * 最小 WebDAV 客户端（P7-07）：只做 PROPFIND 列目录 + 播放地址拼接，不做上传/删除/
 * 重命名等通用网盘能力。
 *
 * 为什么没有用 dav4jvm（硬约束第 5 条的偏差，见 `AGENTS.md` D4）：
 * 当前 JitPack 上的 2.2.1 直接依赖 `okhttp3.internal.http.StatusLine`，该内部类在
 * OkHttp 5 中已不存在；5.0.0 改成 Ktor 客户端，需要再引入一套 Ktor 引擎与认证插件。
 * 为一个「列目录」功能同时维护两套 HTTP 栈不划算，因此按硬约束允许的最小实现，
 * 只用 OkHttp + DOM 解析 Multi-Status。
 *
 * 播放不走这个类：`resolve()` 只拼 URL，真正的 Range/断点续传由 Media3 的
 * `DefaultHttpDataSource` 完成，Basic Auth 通过 [headers] 放进 DataSpec。
 */
@Singleton
class WebDavClient @Inject constructor(
    private val configStore: WebDavConfigStore,
) : StreamUrlResolver {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun hasConfig(): Boolean = configStore.load()?.configured == true

    fun savedConfig(): WebDavConfig? = configStore.load()?.takeIf { it.configured }

    /** 连通性测试：对根目录做一次 Depth:1 PROPFIND，返回子项数量。 */
    fun testConnectionBlocking(): Int {
        val config = requireConfig()
        return listBlocking("/", config).size
    }

    /** 对指定配置做连通性测试，不要求它是启用项（列表里逐条测用）。 */
    fun testConnectionBlocking(config: WebDavConfig): Int = listBlocking("/", config).size

    /**
     * 列出某个目录的直接子项（不含目录自身）。
     *
     * @param path 相对根目录的内部路径，如 `/Album`；根目录传 `/`
     */
    fun listBlocking(path: String, config: WebDavConfig = requireConfig()): List<WebDavItem> {
        ensureAllowed(config)
        val url = collectionUrl(config, path)
        val xml = execute(config, url, depth = 1)
        val requestedPath = WebDavUrl.normalizePath(path)
        val baseUrl = config.baseUrl.toHttpUrlOrNullSafe()

        return WebDavMultiStatusParser.parse(xml).mapNotNull { entry ->
            val itemPath = WebDavUrl.pathFromHref(entry.href, baseUrl, config.rootPath)
                ?: return@mapNotNull null
            if (itemPath == requestedPath) return@mapNotNull null

            val name = entry.displayName
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.isNotEmpty() }
                ?: itemPath.substringAfterLast('/')
            if (name.isBlank()) return@mapNotNull null

            WebDavItem(
                path = itemPath,
                name = name,
                isDir = entry.isCollection || entry.href.trimEnd().endsWith("/"),
                sizeBytes = entry.contentLength,
                lastModifiedSeconds = entry.lastModifiedSeconds,
                mimeType = entry.mimeType,
            )
        }
    }

    /** 播放地址就是「服务器地址 + 根路径 + 文件路径」，不需要像 OpenList 那样换直链。 */
    override fun resolve(internalUri: String): String {
        val parsed = WebDavUrl.parseInternalUri(internalUri) ?: return internalUri
        val config = resolveConfig(parsed.profileId)
        ensureAllowed(config)
        return WebDavUrl.buildUrl(config.baseUrl, config.rootPath, parsed.path).toString()
    }

    /** Basic Auth 只发给 WebDAV 曲目；本地与 OpenList 不附加任何请求头。 */
    override fun headers(internalUri: String): Map<String, String> {
        if (!internalUri.startsWith(WebDavUrl.SCHEME)) return emptyMap()
        val profileId = WebDavUrl.parseInternalUri(internalUri)?.profileId
        val config = runCatching { resolveConfig(profileId) }.getOrNull() ?: return emptyMap()
        return mapOf("Authorization" to Credentials.basic(config.username, config.password))
    }

    /** 按 profile 找配置；缺 profile（老数据）时回落第一台启用项。 */
    fun resolveConfig(profileId: String?): WebDavConfig {
        val config = profileId
            ?.let { id -> configStore.profiles().firstOrNull { it.id == id } }
            ?: configStore.activeConfig()
        return config?.takeIf { it.configured } ?: throw WebDavException.NotConfigured()
    }

    // ---------------------------------------------------------------- 内部

    private fun requireConfig(): WebDavConfig =
        configStore.load()?.takeIf { it.configured }
            ?: throw WebDavException.NotConfigured()

    /**
     * 明文 HTTP 的行为约束与 OpenList 一致：manifest 已放开明文，但只有用户在
     * 保存时对这条配置点过「仍然继续」才真的发请求。
     */
    private fun ensureAllowed(config: WebDavConfig) {
        if (config.baseUrl.startsWith("http://", ignoreCase = true) && !config.allowPlainHttp) {
            throw WebDavException.PlainHttpNotConfirmed()
        }
    }

    private fun collectionUrl(config: WebDavConfig, path: String): HttpUrl {
        val url = WebDavUrl.buildUrl(config.baseUrl, config.rootPath, path)
        return if (url.encodedPath.endsWith('/')) {
            url
        } else {
            // 目录 URL 带尾斜杠，避免部分服务器对 PROPFIND 先回 301。
            url.newBuilder().addPathSegment("").build()
        }
    }

    private fun execute(config: WebDavConfig, url: HttpUrl, depth: Int): String {
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_TYPE))
            .header("Depth", depth.toString())
            .header("Authorization", Credentials.basic(config.username, config.password))
            .header("Accept", "application/xml, text/xml")
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw WebDavException.Network(e)
        }
        response.use { resp ->
            when {
                resp.code == 401 -> throw WebDavException.Auth()
                resp.code == 403 -> throw WebDavException.Http(resp.code)
                resp.code == 404 -> throw WebDavException.NotFound()
                resp.code == 405 -> throw WebDavException.Unsupported()
                resp.code == 207 || resp.code == 200 -> Unit
                else -> throw WebDavException.Http(resp.code)
            }
            return try {
                resp.body.string()
            } catch (e: IOException) {
                throw WebDavException.Network(e)
            }
        }
    }

    private fun String.toHttpUrlOrNullSafe(): HttpUrl =
        toHttpUrlOrNull() ?: throw WebDavException.BadResponse()

    private companion object {
        val XML_TYPE = "application/xml; charset=utf-8".toMediaType()

        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:getcontenttype/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}

/** WebDAV 请求失败。继承 IOException，Media3 才会把它当可恢复的播放错误。 */
sealed class WebDavException(message: String, cause: Throwable? = null) :
    IOException(message, cause) {

    class NotConfigured : WebDavException("还没有配置 WebDAV 服务器")

    class Network(cause: Throwable) : WebDavException("无法连接 WebDAV：${cause.message}", cause)

    class Http(val status: Int) : WebDavException("WebDAV 服务器返回 HTTP $status")

    class Auth : WebDavException("WebDAV 账号或密码错误")

    class NotFound : WebDavException("WebDAV 根路径不存在")

    class Unsupported : WebDavException("服务器不支持 WebDAV（PROPFIND 405）")

    class BadResponse : WebDavException("WebDAV 返回的地址无法解析")

    /** 地址是明文 HTTP，但这条配置没做过二次确认。 */
    class PlainHttpNotConfirmed : WebDavException("该地址是明文 HTTP，尚未确认允许明文连接")
}

/** 给 UI 直接显示的中文提示。 */
fun WebDavException.userMessage(): String = when (this) {
    is WebDavException.NotConfigured -> "请先在设置里填写 WebDAV 地址与账号"
    is WebDavException.Network -> "连不上 WebDAV。检查网络、HTTPS 证书与地址：${cause?.message.orEmpty()}"
    is WebDavException.Auth -> "账号或密码不对，请检查后重试"
    is WebDavException.Http -> when (status) {
        404 -> "根路径不存在（404），请检查「根路径」是否填对"
        403 -> "没有访问权限（403）"
        else -> "服务器返回 HTTP $status"
    }
    is WebDavException.NotFound -> "根路径不存在，请检查「根路径」是否填对"
    is WebDavException.Unsupported -> "这个地址不支持 WebDAV（PROPFIND 405），确认填的是 WebDAV 端点"
    is WebDavException.BadResponse -> "服务器返回的地址无法解析，确认填的是 WebDAV 地址"
    is WebDavException.PlainHttpNotConfirmed ->
        "这个地址是 http:// 明文，请重新保存并在确认弹窗里点「仍然继续」"
}
