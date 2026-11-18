package com.msop.player.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenList 服务器配置列表与登录态。
 *
 * 2026-11-16 起支持**多条配置**；2026-11-16 晚起支持**多条同时启用**：
 * `enabled_ids` 是有序集合，同步会把所有启用项都扫进来合并成一份曲库，播放时由
 * 曲目 `uri` 里的 profile 前缀决定用哪一台服务器解析直链。因此曲目 id 变成
 * `openlist:{profileId}:{sha1(path)}`、`uri` 变成 `openlist:{profileId}:/路径`，
 * 两台服务器上的同名文件不会互相覆盖。见 `docs/03` §9。
 *
 * 为什么没用 `EncryptedSharedPreferences`（原决策 D6）：`androidx.security:security-crypto`
 * 停在 1.1.0-alpha，Tink 依赖在 AGP 9 / targetSdk 37 上有兼容风险，为一个自用、
 * 不上架、只存几台服务器密码的 App 引入不稳定依赖不划算。这里文件位于应用私有
 * 目录（非 root 不可读），已满足本项目的实际威胁模型；将来若要加固，只需替换本类。
 *
 * 用 SharedPreferences 而不是 DataStore：播放层的 ResolvingDataSource 需要在
 * **同步**回调里读 token，DataStore 的挂起 API 会逼出 runBlocking。
 *
 * 存储布局：`profile_ids` 存有序 id 列表（逗号分隔），`active_id` 存启用项，
 * 每条配置的字段落在 `p_{id}_{field}` 前缀下；token 与同步计数也按 profile 分开，
 * 这样切换服务器时不会拿到别一台的 JWT。
 */
@Singleton
class ServerConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    // ---------------------------------------------------------------- 列表

    /** 按创建顺序返回全部配置。 */
    fun profiles(): List<ServerConfig> = ids().mapNotNull { readProfile(it) }

    /** 参与同步与播放的配置（可多条同时启用）。 */
    fun enabledIds(): List<String> {
        val raw = prefs.getString(KEY_ENABLED, null)?.takeIf { it.isNotBlank() }
        if (raw != null) return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        // 没有 enabled_ids 的老数据：回落到旧的单选 active_id
        return prefs.getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            .orEmpty()
    }

    fun enabledConfigs(): List<ServerConfig> {
        val known = profiles().associateBy { it.id }
        return enabledIds().mapNotNull { known[it] }
    }

    fun isEnabled(id: String): Boolean = id in enabledIds()

    /**
     * 勾选 / 取消勾选一台服务器。
     *
     * 取消启用不会删配置、也不会删曲库——曲库里这一台的歌会在下次同步时被
     * 差集清理掉（调用方负责立刻触发一次同步），和删除配置的语义区分开。
     */
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
            // active_id 只是兼容旧调用点（取第一台启用项）
            if (next.isEmpty()) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, next.first())
        }.apply()
    }

    /** 兼容旧调用点：等价于「把这一台勾上」，不会再取消别的。 */
    fun activate(id: String) = setEnabled(id, true)

    fun activeId(): String? = enabledIds().firstOrNull()

    /** 第一台启用项；启用列表为空时返回 null。 */
    fun activeConfig(): ServerConfig? {
        val id = activeId() ?: return null
        return profiles().firstOrNull { it.id == id }
    }

    /** 兼容旧调用点：等价于 [activeConfig]。 */
    fun load(): ServerConfig? = activeConfig()

    fun status(id: String): ServerStatusInfo = ServerStatusInfo(
        status = runCatching {
            ServerStatus.valueOf(prefs.getString(key(id, FIELD_STATUS), null) ?: "")
        }.getOrDefault(ServerStatus.UNTESTED),
        message = prefs.getString(key(id, FIELD_STATUS_MSG), null).orEmpty(),
        atMillis = prefs.getLong(key(id, FIELD_STATUS_AT), 0L),
    )

    // ---------------------------------------------------------------- 增删改

    /**
     * 新增或更新一条配置，返回带 id 的最终配置。
     *
     * 更新已有配置时会作废它自己的 JWT 与状态——账号/地址可能已经变了，
     * 继续沿用旧 token 或旧的「连接成功」都是误导。
     */
    @Synchronized
    fun upsert(config: ServerConfig): ServerConfig {
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
            putBoolean(key(id, FIELD_ALLOW_HTTP), config.allowPlainHttp)
            putString(key(id, FIELD_APP_KEY), config.appKey)
            putString(key(id, FIELD_ROOT), config.rootPath)
            putInt(key(id, FIELD_CONCURRENCY), config.concurrency)
            remove(key(id, FIELD_TOKEN))
            remove(key(id, FIELD_STATUS))
            remove(key(id, FIELD_STATUS_MSG))
            remove(key(id, FIELD_STATUS_AT))
            // 新建的配置默认直接启用：用户要的就是「多台一起用」，不想用再取消勾选
            if (isNew) {
                val enabled = enabledIds()
                val next = if (id in enabled) enabled else enabled + id
                putString(KEY_ENABLED, next.joinToString(","))
                if (next.isNotEmpty()) putString(KEY_ACTIVE, next.first())
            }
        }.apply()

        return config.copy(id = id, name = config.name.ifBlank { hostOf(config.baseUrl) })
    }

    /** 兼容旧调用点：写入并返回带 id 的配置。 */
    fun save(config: ServerConfig) {
        upsert(config)
    }

    /**
     * 手动删除一条配置，连带清掉它的状态、token 与同步计数。
     *
     * 只是配置层的删除；调用方（ViewModel）负责在删到启用项时清理该来源的曲目。
     * 删完若列表还有其它配置，启用项回落到第一条，避免出现「有配置但没启用」。
     */
    @Synchronized
    fun delete(id: String) {
        val remaining = ids().filterNot { it == id }
        val enabled = enabledIds().filterNot { it == id }
        prefs.edit().apply {
            putString(KEY_IDS, remaining.joinToString(","))
            putString(KEY_ENABLED, enabled.joinToString(","))
            listOf(
                FIELD_NAME, FIELD_URL, FIELD_USER, FIELD_PASSWORD,
                FIELD_ALLOW_HTTP, FIELD_APP_KEY, FIELD_ROOT, FIELD_CONCURRENCY, FIELD_TOKEN,
                FIELD_STATUS, FIELD_STATUS_MSG, FIELD_STATUS_AT,
                FIELD_SYNC_COUNT, FIELD_SYNC_AT,
            ).forEach { remove(key(id, it)) }
            if (enabled.isEmpty()) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, enabled.first())
        }.apply()
    }

    /** 清空全部配置（「清除配置」按钮）。 */
    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 备份恢复：按备份里的 profile id 原样写回。
     *
     * 2026-11-18：曲目的 id/uri 带 profile 前缀（`openlist:{profileId}:...`），
     * 恢复时若走 `upsert()` 会分配新 id，老曲目就找不到配置了。这里整表覆盖并
     * 保留原 id 与启用集合；一次恢复就是一次配置全量替换，符合用户预期。
     */
    @Synchronized
    fun restoreAll(configs: List<ServerConfig>, enabledIds: List<String>) {
        val ids = configs.map { it.id }.filter { it.isNotBlank() }.distinct()
        require(ids.size == configs.size) { "备份中的服务器配置缺少稳定 id" }
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
                putBoolean(key(config.id, FIELD_ALLOW_HTTP), config.allowPlainHttp)
                putString(key(config.id, FIELD_APP_KEY), config.appKey)
                putString(key(config.id, FIELD_ROOT), config.rootPath)
                putInt(key(config.id, FIELD_CONCURRENCY), config.concurrency)
            }
        }.apply()
    }

    // ---------------------------------------------------------------- 状态

    @Synchronized
    fun saveStatus(id: String, status: ServerStatus, message: String) {
        prefs.edit()
            .putString(key(id, FIELD_STATUS), status.name)
            .putString(key(id, FIELD_STATUS_MSG), message)
            .putLong(key(id, FIELD_STATUS_AT), System.currentTimeMillis())
            .apply()
    }

    // ---------------------------------------------------------------- 登录态

    /** JWT 按 profile 分开缓存，切换服务器时不会串用。 */
    fun cachedToken(id: String): String? = prefs.getString(key(id, FIELD_TOKEN), null)

    fun cachedToken(): String? = activeId()?.let { cachedToken(it) }

    @Synchronized
    fun saveToken(id: String, token: String?) {
        if (token == null) prefs.edit().remove(key(id, FIELD_TOKEN)).apply()
        else prefs.edit().putString(key(id, FIELD_TOKEN), token).apply()
    }

    fun saveToken(token: String?) {
        activeId()?.let { saveToken(it, token) }
    }

    // ---------------------------------------------------------------- 同步计数

    fun lastSyncedTrackCount(): Int = enabledIds().sumOf { syncedCount(it) }

    fun lastSyncedAt(): Long = enabledIds().maxOfOrNull { prefs.getLong(key(it, FIELD_SYNC_AT), 0L) } ?: 0L

    fun syncedCount(id: String): Int = prefs.getInt(key(id, FIELD_SYNC_COUNT), 0)

    /** 记录一次同步结果；按 profile 记，多台同时启用时各自显示各自的数量。 */
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

    private fun readProfile(id: String): ServerConfig? {
        val url = prefs.getString(key(id, FIELD_URL), null) ?: return null
        if (url.isBlank()) return null
        return ServerConfig(
            baseUrl = url,
            username = prefs.getString(key(id, FIELD_USER), null).orEmpty(),
            password = prefs.getString(key(id, FIELD_PASSWORD), null).orEmpty(),
            allowPlainHttp = prefs.getBoolean(key(id, FIELD_ALLOW_HTTP), false),
            appKey = prefs.getString(key(id, FIELD_APP_KEY), null).orEmpty(),
            rootPath = prefs.getString(key(id, FIELD_ROOT), null).orEmpty(),
            concurrency = prefs.getInt(key(id, FIELD_CONCURRENCY), 0),
            id = id,
            name = prefs.getString(key(id, FIELD_NAME), null).orEmpty(),
        )
    }

    /**
     * 把 2026-11-16 之前的单配置（裸 `base_url` / `jwt` 等键）搬成第一条 profile。
     *
     * 只跑一次：成功后写 `migrated=true`。旧键保留不动，出问题还能对照排查。
     */
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
                putBoolean(key(id, FIELD_ALLOW_HTTP), prefs.getBoolean(LEGACY_ALLOW_HTTP, false))
                putString(key(id, FIELD_APP_KEY), prefs.getString(LEGACY_APP_KEY, null).orEmpty())
                prefs.getString(LEGACY_TOKEN, null)?.let { putString(key(id, FIELD_TOKEN), it) }
                putInt(key(id, FIELD_SYNC_COUNT), prefs.getInt(LEGACY_SYNC_COUNT, 0))
                putLong(key(id, FIELD_SYNC_AT), prefs.getLong(LEGACY_SYNC_AT, 0L))
            }.apply()
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun key(id: String, field: String): String = "p_${id}_$field"

    private companion object {
        const val PREFS_NAME = "msop_openlist"
        const val KEY_IDS = "profile_ids"
        const val KEY_ACTIVE = "active_id"
        /** 多台同时启用：逗号分隔有序 id 列表。 */
        const val KEY_ENABLED = "enabled_ids"
        const val KEY_MIGRATED = "profiles_migrated"

        const val FIELD_NAME = "name"
        const val FIELD_URL = "url"
        const val FIELD_USER = "user"
        const val FIELD_PASSWORD = "password"
        const val FIELD_ALLOW_HTTP = "allow_http"
        const val FIELD_APP_KEY = "app_key"
        const val FIELD_ROOT = "root"
        const val FIELD_CONCURRENCY = "concurrency"
        const val FIELD_TOKEN = "jwt"
        const val FIELD_STATUS = "status"
        const val FIELD_STATUS_MSG = "status_msg"
        const val FIELD_STATUS_AT = "status_at"
        const val FIELD_SYNC_COUNT = "sync_count"
        const val FIELD_SYNC_AT = "sync_at"

        /** 2026-11-16 之前的单配置键，仅用于迁移。 */
        const val LEGACY_URL = "base_url"
        const val LEGACY_USER = "username"
        const val LEGACY_PASSWORD = "password"
        const val LEGACY_ALLOW_HTTP = "allow_plain_http"
        const val LEGACY_APP_KEY = "media_app_key"
        const val LEGACY_TOKEN = "jwt"
        const val LEGACY_SYNC_COUNT = "sync_count"
        const val LEGACY_SYNC_AT = "sync_at"
    }
}
