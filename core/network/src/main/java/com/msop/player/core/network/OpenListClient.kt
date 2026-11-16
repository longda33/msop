package com.msop.player.core.network

import com.msop.player.core.datastore.ServerConfig
import com.msop.player.core.datastore.ServerConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenList v4.2.9 **原生 REST** 客户端（P3-02）。
 *
 * 为什么不是 Subsonic：2026-11-11 实测用户这台服务器 `media_enabled=false`、
 * Subsonic 真实路由（@rest 前缀）返回 404，且所给账号非管理员无法开启。
 * WebDAV 虽可用（`207 PROPFIND /dav/`），但 GET 文件是 302 跳直链、还要解析
 * XML；原生 API 直接给 JSON 与可 Range 的 raw_url，实现成本最低。详见 `docs/03`。
 *
 * 三条实测要点都写进了代码：
 * 1. 登录拿 JWT，后续请求头是 `Authorization: <jwt>`（**没有** Bearer 前缀）。
 * 2. `/api/fs/get` 返回的 `raw_url` 自带有时效的 tempauth，**不能存进数据库**，
 *    所以每次起播前都要重新解析（见 [resolve]）。
 * 3. 拿 raw_url 去 GET 时**不能带 Authorization 头**（实测会 401），
 *    因此流播完全交给 Media3 的 DefaultHttpDataSource，不加任何自定义头。
 */
@Singleton
class OpenListClient @Inject constructor(
    private val configStore: ServerConfigStore,
) : StreamUrlResolver {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 内存里的 JWT，**按 profile 分开**：多台同时启用时来回取直链不会互相踢掉登录态。
     * 持久化仍按 profile 存在 SharedPreferences 里。
     */
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun hasConfig(): Boolean = configStore.enabledConfigs().any { it.configured }

    /** 换服务器或改密码后调用，避免旧 JWT 一直被复用。 */
    fun clearSession() {
        tokens.clear()
    }

    // ---------------------------------------------------------------- 登录

    private fun validToken(config: ServerConfig): String {
        tokens[config.id]?.let { return it }
        val cached = configStore.cachedToken(config.id)
        if (!cached.isNullOrBlank()) {
            tokens[config.id] = cached
            return cached
        }
        return loginBlocking(config)
    }

    private fun loginBlocking(config: ServerConfig): String {
        val body = json.encodeToString(LoginRequest(config.username, config.password))
        val request = Request.Builder()
            .url(ServerUrl.api(config.baseUrl, "api/auth/login"))
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        val newToken = execute<LoginData>(request).data?.token?.takeIf { it.isNotBlank() }
            ?: throw OpenListException.Auth()
        tokens[config.id] = newToken
        configStore.saveToken(config.id, newToken)
        return newToken
    }

    // ---------------------------------------------------------------- 列目录 / 直链

    fun listBlocking(path: String, page: Int, perPage: Int = PAGE_SIZE): FsListData =
        listBlockingWith(requireConfig(), path, page, perPage)

    /** 列目录，但显式指定配置——用于「测试未启用的那条」而不切换启用项。 */
    fun listBlockingWith(
        config: ServerConfig,
        path: String,
        page: Int,
        perPage: Int = PAGE_SIZE,
    ): FsListData {
        val body = json.encodeToString(FsListRequest(path = path, page = page, perPage = perPage))
        return authed<FsListData>(config, "api/fs/list", body).data ?: FsListData()
    }

    suspend fun list(path: String, page: Int, perPage: Int = PAGE_SIZE): FsListData =
        withContext(Dispatchers.IO) { listBlocking(path, page, perPage) }

    fun rawUrlBlocking(path: String): String = rawUrlBlocking(requireConfig(), path)

    /** 指定配置取直链：多台同时启用时，曲目 uri 里的 profile 决定用哪一台。 */
    fun rawUrlBlocking(config: ServerConfig, path: String): String {
        if (config.baseUrl.startsWith("http://", ignoreCase = true) && !config.allowPlainHttp) {
            throw OpenListException.PlainHttpNotConfirmed()
        }
        val body = json.encodeToString(FsGetRequest(path))
        val data = authed<FsGetData>(config, "api/fs/get", body).data
            ?: throw OpenListException.Api(-1, "fs/get 未返回数据")
        return data.rawUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: throw OpenListException.EmptyStreamUrl()
    }

    suspend fun rawUrl(path: String): String = withContext(Dispatchers.IO) { rawUrlBlocking(path) }

    /** 连通性测试：强制重新登录 + 列一次根目录，返回条目总数。 */
    fun testConnectionBlocking(): Int = testConnectionBlocking(requireConfig())

    /** 对指定配置做连通性测试（不清别的 profile 的 token、不改启用项）。 */
    fun testConnectionBlocking(config: ServerConfig): Int {
        if (config.baseUrl.startsWith("http://", ignoreCase = true) && !config.allowPlainHttp) {
            throw OpenListException.PlainHttpNotConfirmed()
        }
        tokens.remove(config.id)
        configStore.saveToken(config.id, null)
        loginBlocking(config)
        return listBlockingWith(config, "/", 1, 1).total
    }

    suspend fun testConnection(): Int = withContext(Dispatchers.IO) { testConnectionBlocking() }

    // ---------------------------------------------------------------- 播放接缝

    /**
     * [StreamUrlResolver] 实现。**同步**，在 Media3 的 DataSource 线程调用。
     * 非 `openlist:` URI 原样返回，本地歌曲不受影响。
     */
    override fun resolve(internalUri: String): String {
        val parsed = ServerUrl.parseInternalUri(internalUri) ?: return internalUri
        return rawUrlBlocking(resolveConfig(parsed.profileId), parsed.path)
    }

    /**
     * 按 profile 找配置；没有 profile 前缀（老数据）或找不到时回落第一台启用项。
     */
    fun resolveConfig(profileId: String?): ServerConfig {
        val config = profileId
            ?.let { id -> configStore.profiles().firstOrNull { it.id == id } }
            ?: configStore.activeConfig()
        val usable = config?.takeIf { it.configured }
            ?: throw OpenListException.NotConfigured()
        if (usable.baseUrl.startsWith("http://", ignoreCase = true) && !usable.allowPlainHttp) {
            throw OpenListException.PlainHttpNotConfirmed()
        }
        return usable
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 取当前启用配置。
     *
     * 2026-11-16 起 App 允许明文 HTTP（manifest 的 network security config 放开了 base-config），
     * 但**只在用户对这条配置做过二次确认**（`allowPlainHttp=true`）时才真的发请求——
     * 否则抛 [OpenListException.PlainHttpNotConfirmed]，把「明文」这件事留在用户手里决定。
     */
    private fun requireConfig(): ServerConfig {
        val config = configStore.load()?.takeIf { it.configured }
            ?: throw OpenListException.NotConfigured()
        if (config.baseUrl.startsWith("http://", ignoreCase = true) && !config.allowPlainHttp) {
            throw OpenListException.PlainHttpNotConfirmed()
        }
        return config
    }

    private fun buildRequest(
        config: ServerConfig,
        path: String,
        bodyJson: String,
        authToken: String,
    ): Request = Request.Builder()
        .url(ServerUrl.api(config.baseUrl, path))
        .header("Authorization", authToken)
        .post(bodyJson.toRequestBody(JSON_TYPE))
        .build()

    /** 401/403 时清 token 并重登一次，只重试一次，避免无限循环。 */
    private inline fun <reified T> authed(
        config: ServerConfig,
        path: String,
        bodyJson: String,
    ): ApiEnvelope<T> {
        val first = buildRequest(config, path, bodyJson, validToken(config))
        return try {
            execute<T>(first)
        } catch (e: OpenListException) {
            // HTTP 401/403 或业务码 401 都可能是 JWT 过期，重登一次再试
            val retryable = (e is OpenListException.Http && (e.status == 401 || e.status == 403)) ||
                e is OpenListException.Auth
            if (!retryable) throw e
            tokens.remove(config.id)
            val retry = buildRequest(config, path, bodyJson, loginBlocking(config))
            execute<T>(retry)
        }
    }

    private inline fun <reified T> execute(request: Request): ApiEnvelope<T> {
        val response = try {
            http.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw OpenListException.Network(e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) throw OpenListException.Http(resp.code)
            val text = try {
                resp.body.string()
            } catch (e: java.io.IOException) {
                throw OpenListException.Network(e)
            }
            val envelope = try {
                json.decodeFromString<ApiEnvelope<T>>(text)
            } catch (e: Exception) {
                throw OpenListException.BadResponse()
            }
            // HTTP 200 也可能带业务错误码（例如 401 Guest is disabled）
            if (envelope.code != 200) {
                if (envelope.code == 401 || envelope.code == 403) throw OpenListException.Auth()
                throw OpenListException.Api(envelope.code, envelope.message)
            }
            return envelope
        }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val PAGE_SIZE = 100
    }
}
