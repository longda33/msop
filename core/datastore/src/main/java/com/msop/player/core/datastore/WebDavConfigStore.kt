package com.msop.player.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 配置列表与同步状态（P7-07）。
 *
 * 独立 SharedPreferences 文件，与 OpenList 的 `msop_openlist` 互不影响：
 * 删除一条 WebDAV 配置只清自己的键，绝不会动 OpenList 的登录态。
 *
 * 与 [ServerConfigStore] 一样支持**多条配置 + 多条同时启用**（`enabled_ids`），
 * 存储布局也保持一致（`profile_ids` / `enabled_ids` / `active_id` / `p_{id}_{field}`），
 * 旧单配置在首次启动时自动迁移。
 * 用 SharedPreferences 而不是 DataStore：[com.msop.player.core.network.WebDavClient.resolve]
 * 在 Media3 的 DataSource 线程同步调用，挂起 API 会逼出 runBlocking。
 */
@Singleton
class WebDavConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    // ---------------------------------------------------------------- 列表

    fun profiles(): List<WebDavConfig> = ids().mapNotNull { readProfile(it) }

    /** 参与同步与播放的配置（可多条同时启用）。 */
    fun enabledIds(): List<String> {
        val raw = prefs.getString(KEY_ENABLED, null)?.takeIf { it.isNotBlank() }
        if (raw != null) return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return prefs.getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            .orEmpty()
    }

    fun enabledConfigs(): List<WebDavConfig> {
        val known = profiles().associateBy { it.id }
        return enabledIds().mapNotNull { known[it] }
    }

    fun isEnabled(id: String): Boolean = id in enabledIds()

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        if (id !in ids()) return
        val current = enabledIds()
        val next = if (enabled) {
            if (id in current) current else current + id
        } else {
            current.filterNot { it == id }
        }
        prefs.edit().apply {
            putString(KEY_ENABLED, next.joinToString(","))
            if (next.isEmpty()) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, next.first())
        }.apply()
    }

    fun activeId(): String? = enabledIds().firstOrNull()

    fun activeConfig(): WebDavConfig? {
        val id = activeId() ?: return null
        return profiles().firstOrNull { it.id == id }
    }

    /** 兼容旧调用点：等价于 [activeConfig]。 */
    fun load(): WebDavConfig? = activeConfig()

    fun status(id: String): ServerStatusInfo = ServerStatusInfo(
        status = runCatching {
            ServerStatus.valueOf(prefs.getString(key(id, FIELD_STATUS), null) ?: "")
        }.getOrDefault(ServerStatus.UNTESTED),
        message = prefs.getString(key(id, FIELD_STATUS_MSG), null).orEmpty(),
        atMillis = prefs.getLong(key(id, FIELD_STATUS_AT), 0L),
    )

    // ---------------------------------------------------------------- 增删改

    @Synchronized
    fun upsert(config: WebDavConfig): WebDavConfig {
        migrateLegacyIfNeeded()
        val existingIds = ids()
        val id = config.id.takeIf { it.isNotBlank() && it in existingIds }
            ?: nextId(existingIds)
        val isNew = id !in existingIds

        prefs.edit().apply {
            if (isNew) putString(KEY_IDS, (existingIds + id).joinToString(","))
            putString(key(id, FIELD_NAME), config.name.ifBlank { hostOf(config.baseUrl) })
            putString(key(id, FIELD_URL), config.baseUrl)
            putString(key(id, FIELD_USER), config.username)
            putString(key(id, FIELD_PASSWORD), config.password)
            putString(key(id, FIELD_ROOT), config.rootPath)
            putInt(key(id, FIELD_CONCURRENCY), config.concurrency)
            putBoolean(key(id, FIELD_ALLOW_HTTP), config.allowPlainHttp)
            // 改过配置，旧的「连接成功」不再可信
            remove(key(id, FIELD_STATUS))
            remove(key(id, FIELD_STATUS_MSG))
            remove(key(id, FIELD_STATUS_AT))
            if (isNew) {
                val enabled = enabledIds()
                val next = if (id in enabled) enabled else enabled + id
                putString(KEY_ENABLED, next.joinToString(","))
                if (next.isNotEmpty()) putString(KEY_ACTIVE, next.first())
            }
        }.apply()

        return config.copy(id = id, name = config.name.ifBlank { hostOf(config.baseUrl) })
    }

    /** 兼容旧调用点。 */
    fun save(config: WebDavConfig) {
        upsert(config)
    }

    fun activate(id: String) = setEnabled(id, true)

    @Synchronized
    fun delete(id: String) {
        val remaining = ids().filterNot { it == id }
        val enabled = enabledIds().filterNot { it == id }
        prefs.edit().apply {
            putString(KEY_IDS, remaining.joinToString(","))
            putString(KEY_ENABLED, enabled.joinToString(","))
            listOf(
                FIELD_NAME, FIELD_URL, FIELD_USER, FIELD_PASSWORD, FIELD_ROOT, FIELD_CONCURRENCY,
                FIELD_ALLOW_HTTP, FIELD_STATUS, FIELD_STATUS_MSG, FIELD_STATUS_AT,
                FIELD_SYNC_COUNT, FIELD_SYNC_AT,
            ).forEach { remove(key(id, it)) }
            if (enabled.isEmpty()) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, enabled.first())
        }.apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** 备份恢复：保留原 profile id 与启用集合，整表覆盖。同 ServerConfigStore.restoreAll。 */
    @Synchronized
    fun restoreAll(configs: List<WebDavConfig>, enabledIds: List<String>) {
        val ids = configs.map { it.id }.filter { it.isNotBlank() }.distinct()
        require(ids.size == configs.size) { "备份中的 WebDAV 配置缺少稳定 id" }
        val enabled = enabledIds.filter { it in ids }
        prefs.edit().apply {
            clear()
            putBoolean(KEY_MIGRATED, true)
            putString(KEY_IDS, ids.joinToString(","))
            putString(KEY_ENABLED, enabled.joinToString(","))
            if (enabled.isNotEmpty()) putString(KEY_ACTIVE, enabled.first())
            configs.forEach { config ->
                putString(key(config.id, FIELD_NAME), config.name.ifBlank { hostOf(config.baseUrl) })
                putString(key(config.id, FIELD_URL), config.baseUrl)
                putString(key(config.id, FIELD_USER), config.username)
                putString(key(config.id, FIELD_PASSWORD), config.password)
                putString(key(config.id, FIELD_ROOT), config.rootPath)
                putBoolean(key(config.id, FIELD_ALLOW_HTTP), config.allowPlainHttp)
                putInt(key(config.id, FIELD_CONCURRENCY), config.concurrency)
            }
        }.apply()
    }

    // ---------------------------------------------------------------- 状态与计数

    @Synchronized
    fun saveStatus(id: String, status: ServerStatus, message: String) {
        prefs.edit()
            .putString(key(id, FIELD_STATUS), status.name)
            .putString(key(id, FIELD_STATUS_MSG), message)
            .putLong(key(id, FIELD_STATUS_AT), System.currentTimeMillis())
            .apply()
    }

    fun lastSyncedTrackCount(): Int = enabledIds().sumOf { syncedCount(it) }

    fun lastSyncedAt(): Long = enabledIds().maxOfOrNull { prefs.getLong(key(it, FIELD_SYNC_AT), 0L) } ?: 0L

    fun syncedCount(id: String): Int = prefs.getInt(key(id, FIELD_SYNC_COUNT), 0)

    fun markSynced(trackCount: Int, atMillis: Long = System.currentTimeMillis()) {
        val id = activeId() ?: return
        markSynced(id, trackCount, atMillis)
    }

    @Synchronized
    fun markSynced(id: String, trackCount: Int, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(key(id, FIELD_SYNC_COUNT), trackCount)
            .putLong(key(id, FIELD_SYNC_AT), atMillis)
            .apply()
    }

    // ---------------------------------------------------------------- 内部

    private fun ids(): List<String> = prefs.getString(KEY_IDS, null)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    private fun nextId(existing: List<String>): String {
        val used = existing.mapNotNull { it.removePrefix("p").toIntOrNull() }.maxOrNull() ?: 0
        return "p${used + 1}"
    }

    private fun readProfile(id: String): WebDavConfig? {
        val url = prefs.getString(key(id, FIELD_URL), null) ?: return null
        if (url.isBlank()) return null
        return WebDavConfig(
            baseUrl = url,
            username = prefs.getString(key(id, FIELD_USER), null).orEmpty(),
            password = prefs.getString(key(id, FIELD_PASSWORD), null).orEmpty(),
            rootPath = prefs.getString(key(id, FIELD_ROOT), null) ?: "/",
            allowPlainHttp = prefs.getBoolean(key(id, FIELD_ALLOW_HTTP), false),
            concurrency = prefs.getInt(key(id, FIELD_CONCURRENCY), 0),
            id = id,
            name = prefs.getString(key(id, FIELD_NAME), null).orEmpty(),
        )
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacyUrl = prefs.getString(LEGACY_URL, null)
        if (ids().isEmpty() && !legacyUrl.isNullOrBlank()) {
            val id = "p1"
            prefs.edit().apply {
                putString(KEY_IDS, id)
                putString(KEY_ACTIVE, id)
                putString(key(id, FIELD_NAME), hostOf(legacyUrl))
                putString(key(id, FIELD_URL), legacyUrl)
                putString(key(id, FIELD_USER), prefs.getString(LEGACY_USER, null).orEmpty())
                putString(key(id, FIELD_PASSWORD), prefs.getString(LEGACY_PASSWORD, null).orEmpty())
                putString(key(id, FIELD_ROOT), prefs.getString(LEGACY_ROOT, null) ?: "/")
                putBoolean(key(id, FIELD_ALLOW_HTTP), prefs.getBoolean(LEGACY_ALLOW_HTTP, false))
                putInt(key(id, FIELD_SYNC_COUNT), prefs.getInt(LEGACY_SYNC_COUNT, 0))
                putLong(key(id, FIELD_SYNC_AT), prefs.getLong(LEGACY_SYNC_AT, 0L))
            }.apply()
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun key(id: String, field: String): String = "p_${id}_$field"

    private companion object {
        const val PREFS_NAME = "msop_webdav"
        const val KEY_IDS = "profile_ids"
        const val KEY_ACTIVE = "active_id"
        const val KEY_ENABLED = "enabled_ids"
        const val KEY_MIGRATED = "profiles_migrated"

        const val FIELD_NAME = "name"
        const val FIELD_URL = "url"
        const val FIELD_USER = "user"
        const val FIELD_PASSWORD = "password"
        const val FIELD_ROOT = "root"
        const val FIELD_ALLOW_HTTP = "allow_http"
        const val FIELD_CONCURRENCY = "concurrency"
        const val FIELD_STATUS = "status"
        const val FIELD_STATUS_MSG = "status_msg"
        const val FIELD_STATUS_AT = "status_at"
        const val FIELD_SYNC_COUNT = "sync_count"
        const val FIELD_SYNC_AT = "sync_at"

        const val LEGACY_URL = "base_url"
        const val LEGACY_USER = "username"
        const val LEGACY_PASSWORD = "password"
        const val LEGACY_ROOT = "root_path"
        const val LEGACY_ALLOW_HTTP = "allow_plain_http"
        const val LEGACY_SYNC_COUNT = "sync_count"
        const val LEGACY_SYNC_AT = "sync_at"
    }
}
