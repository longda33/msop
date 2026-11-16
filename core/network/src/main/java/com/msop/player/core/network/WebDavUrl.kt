package com.msop.player.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * WebDAV 内部路径与真实 URL 的换算（P7-07）。纯函数，方便单测。
 *
 * 数据库里的 `uri` 永远是稳定的 `webdav:/相对根目录的路径`，**不存真实 URL**：
 * 服务器地址、根路径都可能被用户修改，而缓存 key 与歌单引用必须在修改后不变。
 *
 * 与 OpenList 的区别：OpenList 需要先 `fs/get` 换带签名的直链；WebDAV 的播放地址
 * 就是「服务器地址 + 根路径 + 文件路径」，鉴权走 Basic Auth 请求头，不需要中间请求。
 * 因此 [buildUrl] 是纯计算，可在 DataSource 线程同步调用。
 */
object WebDavUrl {

    const val SCHEME = "webdav:"

    /** 统一的内部路径：以 `/` 开头、折叠重复斜杠、去尾斜杠（根路径保留 `/`）。 */
    fun normalizePath(raw: String): String {
        val collapsed = raw.trim()
            .replace('\\', '/')
            .replace(Regex("/{2,}"), "/")
        if (collapsed.isEmpty()) return "/"
        val withRoot = if (collapsed.startsWith('/')) collapsed else "/$collapsed"
        return if (withRoot.length > 1) withRoot.trimEnd('/') else "/"
    }

    /** 文件路径不是 URL，这里只拼路径段，真正的编码交给 [ServerUrl.api]。 */
    fun join(directory: String, name: String): String {
        val parent = normalizePath(directory)
        return if (parent == "/") "/$name" else "$parent/$name"
    }

    fun parentPath(path: String): String {
        val normalized = normalizePath(path)
        val parent = normalized.substringBeforeLast('/', "")
        return parent.ifBlank { "/" }
    }

    fun internalUri(path: String): String = SCHEME + normalizePath(path)

    /** 带 profile 的内部 URI：`webdav:{profileId}:/路径`，多台同时启用时区分服务器。 */
    fun internalUri(profileId: String, path: String): String = SCHEME + profileId + ":" + normalizePath(path)

    data class Internal(val profileId: String?, val path: String)

    /** 解析 `webdav:{profileId}:/路径`，同时兼容老的 `webdav:/路径`。 */
    fun parseInternalUri(uri: String): Internal? {
        if (!uri.startsWith(SCHEME)) return null
        val rest = uri.removePrefix(SCHEME)
        if (rest.startsWith('/')) return Internal(null, normalizePath(rest))
        val sep = rest.indexOf(':')
        if (sep <= 0) return Internal(null, normalizePath(rest))
        val profileId = rest.substring(0, sep).takeIf { it.isNotBlank() }
        return Internal(profileId, normalizePath(rest.substring(sep + 1)))
    }

    fun pathFromInternalUri(uri: String): String? = parseInternalUri(uri)?.path

    /** 根路径 + 相对路径 → 服务器上的完整路径（仍不是 URL）。 */
    fun combine(rootPath: String, path: String): String {
        val root = normalizePath(rootPath)
        val child = normalizePath(path)
        return when {
            root == "/" -> child
            child == "/" -> root
            else -> root + child
        }
    }

    /**
     * 拼接真实播放地址。路径段统一走 [ServerUrl.api]，它会做百分号编码，
     * 中文歌名、空格、`#` 都不会把 URL 拼坏。
     */
    fun buildUrl(baseUrl: String, rootPath: String, path: String): HttpUrl {
        val combined = combine(rootPath, path).trim('/')
        return if (combined.isEmpty()) baseUrl.toHttpUrl() else ServerUrl.api(baseUrl, combined)
    }

    /**
     * 把 PROPFIND 返回的 `href` 还原成内部路径（相对用户配置的根路径）。
     *
     * `href` 可能是绝对 URL、绝对路径或相对路径；统一先解析成 [HttpUrl]，
     * 再用**解码后的 pathSegments** 比较，避免 `%E4%B8%AD` 这类编码导致匹配失败。
     * 不在根路径下的响应返回 null（服务器可能把父目录也列出来）。
     */
    fun pathFromHref(href: String, baseUrl: HttpUrl, rootPath: String): String? {
        val raw = href.trim()
        if (raw.isEmpty()) return null
        val resolved = when {
            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) -> raw.toHttpUrlOrNull()

            raw.startsWith('/') -> baseUrl.resolve(raw)

            else -> (baseUrl.toString().trimEnd('/') + "/" + raw).toHttpUrlOrNull()
        } ?: return null

        // base URL 可能带反代子路径（https://host/dav），href 的 pathSegments 也包含它，
        // 所以比较时必须把 base 的路径段一起算进来，只比 rootPath 会在子路径部署下全部落空。
        val prefixSegments = baseUrl.pathSegments.filter { it.isNotEmpty() }
        val rootSegments = prefixSegments +
            normalizePath(rootPath).trim('/').split('/').filter { it.isNotEmpty() }
        val segments = resolved.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < rootSegments.size) return null
        if (!segments.take(rootSegments.size).equals(rootSegments)) return null

        val relative = segments.drop(rootSegments.size)
        return if (relative.isEmpty()) "/" else "/" + relative.joinToString("/")
    }

    /** 根目录的专辑名兜底，避免显示成 OpenList。 */
    fun rootAlbumName(): String = "WebDAV"
}
