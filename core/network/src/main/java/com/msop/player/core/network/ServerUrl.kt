package com.msop.player.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 服务器地址规范化（P3-06 / P3-07）。纯函数，全部规则都有 JVM 单测。
 *
 * 用户最容易粘错的三种输入，这里一次处理掉：
 * 1. 只填域名 `music.xxx.com` → 补 `https://`
 * 2. 反代子路径 `https://xxx.com/openlist/` → 去掉尾斜杠，保留前缀
 * 3. 从浏览器复制的 `https://xxx.com/index.html?a=1#/music` → 剥掉页面名、query、fragment
 *
 * **所有真实请求 URL 都必须走 [api]**，禁止字符串拼路径——公网带子路径时
 * 字符串拼接必错，这是本项目最容易踩的坑（见 `AGENTS.md` §11）。
 */
object ServerUrl {

    data class Normalized(
        val baseUrl: String,
        /** 用户填了 http://；调用方必须弹警告并要二次确认才能保存。 */
        val isPlainHttp: Boolean,
    )

    /** 从浏览器地址栏误粘进来的页面名，保存时剥掉。 */
    private val MISTAKEN_PAGES = listOf("index.html", "index.htm", "index.php", "default.html")

    fun normalize(raw: String): Normalized {
        var value = raw.trim()
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        require(value.isNotEmpty()) { "服务器地址不能为空" }

        if (!value.contains("://")) value = "https://$value"
        value = value.trimEnd('/')
        for (page in MISTAKEN_PAGES) {
            if (value.endsWith("/$page", ignoreCase = true)) {
                value = value.dropLast(page.length + 1)
                break
            }
        }

        val parsed = value.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("地址格式不正确：$raw")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "只支持 http:// 或 https:// 地址"
        }

        // 交给 HttpUrl 做一次规范化（小写 host、去默认端口等），再去掉根路径的尾斜杠
        val canonical = parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
        return Normalized(canonical, parsed.scheme == "http")
    }

    /**
     * 在（可能带反代子路径的）base URL 后追加 API 路径。
     *
     * @param path 形如 `"api/fs/list"`，会被按 `/` 拆成多段，自动做百分号编码
     */
    fun api(baseUrl: String, path: String): HttpUrl =
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.trim('/'))
            .build()

    /** 把远程曲目用的内部 URI（`openlist:/xxx.flac`）还原成 OpenList 路径。 */
    fun pathFromInternalUri(uri: String): String? = parseInternalUri(uri)?.path

    /** 内部 URI 的两段：profileId 决定用哪台服务器，path 决定文件。 */
    data class Internal(val profileId: String?, val path: String)

    /**
     * 解析 `openlist:{profileId}:/路径`；也兼容 2026-11-16 之前的 `openlist:/路径`
     * （没有 profileId 时由调用方回落到第一台启用项）。
     */
    fun parseInternalUri(uri: String): Internal? {
        if (!uri.startsWith(SCHEME)) return null
        val rest = uri.removePrefix(SCHEME)
        if (rest.startsWith('/')) return Internal(null, rest)
        val sep = rest.indexOf(':')
        if (sep <= 0) return Internal(null, if (rest.isBlank()) "/" else "/$rest")
        val profileId = rest.substring(0, sep).takeIf { it.isNotBlank() }
        val rawPath = rest.substring(sep + 1)
        val path = if (rawPath.startsWith('/')) rawPath else "/$rawPath"
        return Internal(profileId, path)
    }

    fun internalUri(path: String): String = SCHEME + (if (path.startsWith('/')) path else "/$path")

    /** 带 profile 的内部 URI，多台同时启用时用它区分服务器。 */
    fun internalUri(profileId: String, path: String): String =
        SCHEME + profileId + ":" + (if (path.startsWith('/')) path else "/$path")

    /** 远程曲目的稳定 URI scheme。绝不把带 tempauth 的 raw_url 存进数据库。 */
    const val SCHEME = "openlist:"
}
