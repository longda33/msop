package com.primuse.lite.core.network

import com.primuse.lite.core.datastore.ServerConfig
import com.primuse.lite.core.datastore.ServerConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subsonic 元数据客户端（`@rest` 前缀）。**只用来补元数据与封面，不用来流播。**
 *
 * 2026-11-11 实测这台 OpenList v4.2.9 的三条结论决定了本类的边界：
 * 1. 鉴权只认 **`apiKey` 参数**（媒体应用密钥）。`u`+`t`(md5) 和 `u`+`p` 都返回
 *    `code=40 Wrong username or password`，所以这里没有用户名/密码，也没有 salt。
 * 2. `getCoverArt` 返回真实 JPEG，这是原生 REST 给不了的东西，也是引入 Subsonic
 *    的最大收益。
 * 3. `stream` 会 302 跳到 **`http://`** 明文的 `/d/...?sign=` 直链，会被 App 的
 *    `network_security_config`（禁明文）拦掉；因此流播继续用原生 REST 的
 *    `/api/fs/get` 拿 HTTPS raw_url，见 `OpenListClient`。
 */
@Singleton
class SubsonicClient @Inject constructor(
    private val configStore: ServerConfigStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = configStore.load()?.let {
        it.configured && it.appKey.isNotBlank()
    } == true

    /** 连通性测试：ping + 取一页歌曲，返回库里的歌曲总数。 */
    fun testConnectionBlocking(): Int {
        val config = requireConfig()
        execute(apiUrl(config, "ping.view"))
        return execute(apiUrl(config, "search3.view", *searchParams(pageSize = 1)))
            .searchResult3?.total ?: 0
    }

    suspend fun testConnection(): Int = withContext(Dispatchers.IO) { testConnectionBlocking() }

    /**
     * 一次拉全库歌曲。
     *
     * 用 `search3` 空查询而不是 `getAlbumList2` + 逐个 `getAlbum`：实测空查询
     * 会返回全部 23 首，1 次请求顶 22 次，媒体库更大时差距更明显。
     */
    fun allSongsBlocking(pageSize: Int = PAGE_SIZE): List<SubsonicSong> {
        val config = requireConfig()
        val songs = mutableListOf<SubsonicSong>()
        var offset = 0
        var guard = 0
        while (guard++ < MAX_PAGES) {
            val body = execute(
                apiUrl(config, "search3.view", *searchParams(pageSize, offset)),
            )
            val page = body.searchResult3?.song.orEmpty()
            songs += page
            val total = body.searchResult3?.total ?: 0
            if (page.isEmpty() || page.size < pageSize || songs.size >= total) break
            offset += page.size
        }
        return songs
    }

    suspend fun allSongs(pageSize: Int = PAGE_SIZE): List<SubsonicSong> =
        withContext(Dispatchers.IO) { allSongsBlocking(pageSize) }

    /**
     * 封面地址。带 `apiKey` 的完整 HTTPS URL，可直接交给 Coil。
     * 用 [HttpUrl] 拼参数，不做字符串拼接。
     */
    fun coverArtUrl(coverId: String, size: Int = 300): String {
        val config = requireConfig()
        return apiUrl(
            config,
            "getCoverArt.view",
            "id" to coverId,
            "size" to size.toString(),
        ).toString()
    }

    private fun searchParams(pageSize: Int, offset: Int = 0): Array<Pair<String, String>> = arrayOf(
        "query" to "",
        "artistCount" to "0",
        "albumCount" to "0",
        "songCount" to pageSize.toString(),
        "songOffset" to offset.toString(),
    )

    private fun apiUrl(
        config: ServerConfig,
        endpoint: String,
        vararg params: Pair<String, String>,
    ): HttpUrl {
        val builder = ServerUrl.api(config.baseUrl, "$REST_PREFIX/$endpoint").newBuilder()
            .addQueryParameter("apiKey", config.appKey)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private fun execute(url: HttpUrl): SubsonicResponse {
        val request = Request.Builder().url(url).get().build()
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
            val body = try {
                json.decodeFromString<SubsonicEnvelope>(text).response
            } catch (e: Exception) {
                throw OpenListException.BadResponse()
            }
            if (body.status != STATUS_OK) {
                val code = body.error?.code ?: -1
                val message = body.error?.message ?: "Subsonic 返回未知错误"
                // 40 账号/密钥错，50 无权限：都当作鉴权失败，UI 会提示检查应用密钥
                if (code == 40 || code == 50) throw OpenListException.Auth()
                throw OpenListException.Api(code, message)
            }
            return body
        }
    }

    private fun requireConfig(): ServerConfig =
        configStore.load()?.takeIf { it.configured && it.appKey.isNotBlank() }
            ?: throw OpenListException.NotConfigured()

    private companion object {
        const val REST_PREFIX = "@rest"
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "primuse"
        const val STATUS_OK = "ok"
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 40
    }
}
