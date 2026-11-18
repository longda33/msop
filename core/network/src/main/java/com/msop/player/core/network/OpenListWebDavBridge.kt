package com.msop.player.core.network

import com.msop.player.core.datastore.WebDavConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 音源指向 OpenList 的 `/dav` 端点时的**播放桥**。
 *
 * 2026-11-17 实测（用户内网 OpenList，`http://172.207.160.128:5244/dav` + `/cd18/Music`）：
 * - `PROPFIND` 带 Basic Auth → `207`，列目录正常，所以同步能拿到 182 首；
 * - 同一个文件的 `GET`（带 Basic Auth，带不带 Range 都一样）→ `403` XML，
 *   不带认证 → `401`；因此 WebDAV 曲目在播放器里必然 `Source error`；
 * - 同一台机器的原生 REST：`POST /api/auth/login` → JWT，
 *   `POST /api/fs/get {"path":"/cd18/Music/..."}` → `raw_url`，
 *   该直链 `GET` 带 `Range: bytes=0-1023` → `206 fLaC...`。
 *
 * 所以：列目录继续走 WebDAV，取播放地址改走 OpenList 原生 REST。
 * 对普通 WebDAV 服务（如 `dav.2dland.cn`）本桥直接返回 null，行为不变。
 *
 * 三条硬约束沿用 OpenListClient：JWT 请求头**没有** Bearer 前缀；raw_url 绝不入库；
 * 拿 raw_url 时**不能**带 Authorization 头（由 [WebDavClient.headers] 在本桥拿到
 * session 后返回空 map 保证）。
 */
@Singleton
class OpenListWebDavBridge @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    /** fingerprint → JWT；改密码/换地址后 fingerprint 变化，旧 token 自动失效。 */
    private val sessions = ConcurrentHashMap<String, String>()

    /** fingerprint → 下次允许重试登录的时间戳；避免普通 WebDAV 每首歌都白试一次登录。 */
    private val noBridgeUntil = ConcurrentHashMap<String, Long>()

    /** 只有登录成功过（缓存里有 JWT）才成立，供 headers() 决定是否去掉 Basic 头。 */
    fun hasSession(config: WebDavConfig): Boolean {
        val base = restBaseFor(config.baseUrl) ?: return false
        return sessions.containsKey(fingerprint(config, base))
    }

    /**
     * 取可直接交给 Media3 的直链；确定不是 OpenList 时返回 null，由调用方回退普通 WebDAV URL。
     * [relativePath] 是 WebDAV 内部路径（相对用户填的 rootPath），这里补回完整 OpenList 路径。
     */
    fun rawUrlOrNull(config: WebDavConfig, relativePath: String): String? {
        // 2026-11-18：本类会用 WebDAV 配置里的账号密码去 POST /api/auth/login。
        // WebDavClient.resolve()/headers() 虽然已先做明文闸门，但这里是 @Singleton 的
        // 公开方法，必须自己守住硬约束 §3-7，不能靠调用方记得先检查。
        if (config.baseUrl.startsWith("http://", ignoreCase = true) && !config.allowPlainHttp) {
            throw WebDavException.PlainHttpNotConfirmed()
        }
        val base = restBaseFor(config.baseUrl) ?: return null
        val key = fingerprint(config, base)
        val now = System.currentTimeMillis()
        pruneNoBridge(now)
        noBridgeUntil[key]?.let { until -> if (until > now) return null }

        var token = sessions[key]
        if (token == null) {
            token = loginBlocking(config, base)
            if (token == null) {
                noBridgeUntil[key] = now + NEGATIVE_TTL_MS
                return null
            }
            sessions[key] = token
            pruneOldSessions(config, base, key)
            noBridgeUntil.remove(key)
        }

        val path = WebDavUrl.combine(config.effectiveRootPath, relativePath)
        var raw = try {
            fsGetRawUrl(base, token, path)
        } catch (e: Exception) {
            null // 可能是 JWT 过期，或非 OpenList 服务返回了非 JSON；下面重登一次再判
        }
        if (raw == null) {
            // 401/403 多半是 JWT 过期：清掉重登再试一次
            sessions.remove(key)
            val fresh = loginBlocking(config, base)
            if (fresh != null) {
                sessions[key] = fresh
                pruneOldSessions(config, base, key)
                raw = try {
                    fsGetRawUrl(base, fresh, path)
                } catch (e: Exception) {
                    null
                }
            }
        }
        if (raw == null) {
            // 这次确实是 OpenList 但没拿到直链：不要让 headers() 把 Basic 头带到普通 WebDAV URL 上
            sessions.remove(key)
            noBridgeUntil[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS
            return null
        }
        return raw
    }

    // ---------------------------------------------------------------- 内部

    private fun loginBlocking(config: WebDavConfig, restBase: String): String? {
        val request = Request.Builder()
            .url(ServerUrl.api(restBase, "api/auth/login"))
            .post(json.encodeToString(LoginBody(config.username, config.password)).toRequestBody(JSON_TYPE))
            .build()
        val envelope = try {
            parse<LoginEnvelope>(request)
        } catch (e: Exception) {
            // 非 OpenList 服务可能 200 回 HTML，JSON 解析也会失败；都当作「不桥接」
            return null
        }
        return envelope?.data?.token?.takeIf { it.isNotBlank() && envelope.code == 200 }
    }

    private fun fsGetRawUrl(restBase: String, token: String, path: String): String {
        val request = Request.Builder()
            .url(ServerUrl.api(restBase, "api/fs/get"))
            .header("Authorization", token) // 实测没有 Bearer 前缀
            .post(json.encodeToString(FsGetBody(path)).toRequestBody(JSON_TYPE))
            .build()
        val envelope = parse<FsGetEnvelope>(request)
            ?: throw IOException("fs/get 返回空响应")
        if (envelope.code != 200) throw IOException("fs/get 业务码 ${envelope.code}: ${envelope.message}")
        return envelope.data?.rawUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: throw IOException("fs/get 没有返回 raw_url")
    }

    private inline fun <reified T> parse(request: Request): T? {
        val response = http.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body.string()
            json.decodeFromString<T>(text)
        }
    }

    /** 已过期的「暂不桥接」记录及时清掉，避免反复改配置时无界增长。 */
    private fun pruneNoBridge(now: Long) {
        noBridgeUntil.entries.removeIf { entry -> entry.value <= now }
    }

    /**
     * 同一服务器 + 用户名只保留当前密码对应的 JWT。
     * 改密码后旧 fingerprint 的 token 已经失效，留着只是内存里的敏感数据堆积。
     */
    private fun pruneOldSessions(config: WebDavConfig, restBase: String, keep: String) {
        val prefix = "$restBase|${config.username}|"
        sessions.keys.removeIf { key -> key.startsWith(prefix) && key != keep }
    }

    private fun fingerprint(config: WebDavConfig, restBase: String): String =
        "$restBase|${config.username}|${config.password.hashCode()}"

    @Serializable
    private data class LoginBody(val username: String, val password: String)

    @Serializable
    private data class LoginEnvelope(
        val code: Int = 0,
        val message: String = "",
        val data: LoginData? = null,
    )

    @Serializable
    private data class LoginData(val token: String = "")

    @Serializable
    private data class FsGetBody(val path: String)

    @Serializable
    private data class FsGetEnvelope(
        val code: Int = 0,
        val message: String = "",
        val data: FsGetData? = null,
    )

    @Serializable
    private data class FsGetData(@SerialName("raw_url") val rawUrl: String = "")

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val NEGATIVE_TTL_MS = 5L * 60L * 1000L

        /**
         * `http://host:5244/dav` → `http://host:5244`；反代子路径 `https://h/openlist/dav`
         * → `https://h/openlist`。不是 `/dav` 结尾就返回 null（普通 WebDAV 服务）。
         * 一律用 HttpUrl 拆路径段，不做字符串拼接。
         */
        fun restBaseFor(webDavBaseUrl: String): String? {
            val url = webDavBaseUrl.toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (segments.lastOrNull()?.equals("dav", ignoreCase = true) != true) return null
            val prefix = segments.dropLast(1)
            val builder = url.newBuilder().encodedPath("/")
            if (prefix.isNotEmpty()) builder.addPathSegments(prefix.joinToString("/"))
            return builder.build().toString().trimEnd('/')
        }
    }
}
