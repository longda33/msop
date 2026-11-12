package com.primuse.lite.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务器配置与登录态。
 *
 * 为什么没用 `EncryptedSharedPreferences`（原决策 D6）：`androidx.security:security-crypto`
 * 停在 1.1.0-alpha，Tink 依赖在 AGP 9 / targetSdk 37 上有兼容风险，为一个自用、
 * 不上架、只存一台服务器密码的 App 引入不稳定依赖不划算。这里文件位于应用私有
 * 目录（非 root 不可读），已满足本项目的实际威胁模型；将来若要加固，只需替换本类。
 *
 * 用 SharedPreferences 而不是 DataStore：播放层的 ResolvingDataSource 需要在
 * **同步**回调里读 token，DataStore 的挂起 API 会逼出 runBlocking。
 */
@Singleton
class ServerConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (url.isBlank()) return null
        return ServerConfig(
            baseUrl = url,
            username = prefs.getString(KEY_USER, null).orEmpty(),
            password = prefs.getString(KEY_PASSWORD, null).orEmpty(),
            allowPlainHttp = prefs.getBoolean(KEY_ALLOW_HTTP, false),
            appKey = prefs.getString(KEY_APP_KEY, null).orEmpty(),
        )
    }

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_ALLOW_HTTP, config.allowPlainHttp)
            .putString(KEY_APP_KEY, config.appKey)
            .apply()
        // 换了服务器/账号，旧 token 必须作废
        saveToken(null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** JWT 只在内存 + 本地缓存一份，401 时由客户端重新登录覆盖。 */
    fun cachedToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String?) {
        if (token == null) prefs.edit().remove(KEY_TOKEN).apply()
        else prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun lastSyncedTrackCount(): Int = prefs.getInt(KEY_SYNC_COUNT, 0)

    fun lastSyncedAt(): Long = prefs.getLong(KEY_SYNC_AT, 0L)

    fun markSynced(trackCount: Int, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(KEY_SYNC_COUNT, trackCount)
            .putLong(KEY_SYNC_AT, atMillis)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "primuse_openlist"
        const val KEY_URL = "base_url"
        const val KEY_USER = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_ALLOW_HTTP = "allow_plain_http"
        const val KEY_APP_KEY = "media_app_key"
        const val KEY_TOKEN = "jwt"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_SYNC_AT = "sync_at"
    }
}
