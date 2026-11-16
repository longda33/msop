package com.msop.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.datastore.ServerStatus
import com.msop.player.core.datastore.ServerStatusInfo
import com.msop.player.core.datastore.WebDavConfig
import com.msop.player.core.datastore.WebDavConfigStore
import com.msop.player.core.network.ServerUrl
import com.msop.player.core.network.WebDavClient
import com.msop.player.core.network.WebDavException
import com.msop.player.core.network.WebDavUrl
import com.msop.player.core.network.userMessage
import com.msop.player.feature.remote.WebDavLibrarySyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * WebDAV 第三音源管理（P7-07，2026-11-16 起支持多条配置）。
 *
 * 与 OpenList 的 ViewModel 刻意保持同构：列表 + 编辑器两层，**多条可以同时启用**，
 * 启用 / 取消启用都会重跑一次同步，让曲库与启用集合保持一致。
 * 两者配置、曲目清理与失败提示互不影响。
 */
@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val configStore: WebDavConfigStore,
    private val client: WebDavClient,
    private val syncer: WebDavLibrarySyncer,
) : ViewModel() {

    data class ProfileRow(
        val config: WebDavConfig,
        val status: ServerStatusInfo,
        val enabled: Boolean,
        val syncedCount: Int,
    )

    data class UiState(
        val name: String = "",
        val url: String = "",
        val username: String = "",
        val password: String = "",
        /** 音乐库在服务器上的根目录，空 = `/`。 */
        val rootPath: String = "/",
        /** 请求并发数（字符串，空串 = 默认 4）。 */
        val concurrency: String = "",
        val editingId: String? = null,
        val editorOpen: Boolean = false,
        val busy: Boolean = false,
        val busyLabel: String = "",
        val message: String? = null,
        val isError: Boolean = false,
        val profiles: List<ProfileRow> = emptyList(),
        val enabledIds: List<String> = emptyList(),
        val configured: Boolean = false,
        val syncedCount: Int = 0,
        val lastSyncAt: Long = 0L,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val all = configStore.profiles()
        val enabled = configStore.enabledIds()
        _state.value = _state.value.copy(
            profiles = all.map { config ->
                ProfileRow(
                    config = config,
                    status = configStore.status(config.id),
                    enabled = config.id in enabled,
                    syncedCount = configStore.syncedCount(config.id),
                )
            },
            enabledIds = enabled,
            configured = all.isNotEmpty(),
            syncedCount = all.filter { it.id in enabled }.sumOf { configStore.syncedCount(it.id) },
            lastSyncAt = configStore.lastSyncedAt(),
        )
    }

    // ---------------------------------------------------------------- 编辑器

    fun openAdd() {
        _state.value = _state.value.copy(
            name = "",
            url = "",
            username = "",
            password = "",
            rootPath = "/",
            concurrency = "",
            editingId = null,
            editorOpen = true,
            message = null,
            isError = false,
        )
    }

    fun openEdit(id: String) {
        val config = configStore.profiles().firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            name = config.name,
            url = config.baseUrl,
            username = config.username,
            password = config.password,
            rootPath = config.rootPath,
            concurrency = config.concurrency.takeIf { it > 0 }?.toString().orEmpty(),
            editingId = id,
            editorOpen = true,
            message = null,
            isError = false,
        )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editorOpen = false, message = null, isError = false)
    }

    fun onNameChange(value: String) = edit { copy(name = value) }
    fun onUrlChange(value: String) = edit { copy(url = value) }
    fun onUsernameChange(value: String) = edit { copy(username = value) }
    fun onPasswordChange(value: String) = edit { copy(password = value) }
    fun onRootPathChange(value: String) = edit { copy(rootPath = value) }
    fun onConcurrencyChange(value: String) = edit {
        copy(concurrency = value.filter { it.isDigit() }.take(2))
    }

    private inline fun edit(block: UiState.() -> UiState) {
        _state.value = _state.value.block().copy(message = null, isError = false)
    }

    fun save(allowPlainHttp: Boolean = false) = persistAnd(allowPlainHttp)

    fun testCurrent(allowPlainHttp: Boolean = false) = persistAnd(allowPlainHttp, thenTest = true)

    fun saveAndSync(allowPlainHttp: Boolean = false) = persistAnd(allowPlainHttp, thenSync = true)

    private fun persistAnd(
        allowPlainHttp: Boolean,
        thenTest: Boolean = false,
        thenSync: Boolean = false,
    ) {
        val current = _state.value
        if (current.busy) return
        viewModelScope.launch {
            _state.value = current.copy(busy = true, busyLabel = "正在保存…", message = null, isError = false)
            try {
                val normalized = ServerUrl.normalize(current.url)
                if (normalized.isPlainHttp && !allowPlainHttp) {
                    _state.value = _state.value.copy(
                        busy = false,
                        busyLabel = "",
                        isError = true,
                        message = "http:// 是明文传输，请确认后再继续，或改用 https://",
                    )
                    return@launch
                }
                val rootPath = WebDavUrl.normalizePath(current.rootPath.ifBlank { "/" })
                val saved = configStore.upsert(
                    WebDavConfig(
                        id = current.editingId.orEmpty(),
                        name = current.name.trim(),
                        baseUrl = normalized.baseUrl,
                        username = current.username.trim(),
                        password = current.password,
                        rootPath = rootPath,
                        concurrency = current.concurrency.toIntOrNull() ?: 0,
                        allowPlainHttp = normalized.isPlainHttp && allowPlainHttp,
                    ),
                )
                _state.value = _state.value.copy(
                    editingId = saved.id,
                    url = normalized.baseUrl,
                    rootPath = rootPath,
                    name = saved.name,
                )
                refresh()

                when {
                    thenTest -> {
                        _state.value = _state.value.copy(busyLabel = "正在连接服务器…")
                        runTest(saved)
                    }
                    thenSync -> {
                        _state.value = _state.value.copy(busyLabel = "正在同步曲库…")
                        val result = activateAndSync(saved.id)
                        _state.value = _state.value.copy(
                            busy = false,
                            busyLabel = "",
                            editorOpen = false,
                            message = result,
                            isError = false,
                        )
                        refresh()
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            busyLabel = "",
                            editorOpen = false,
                            message = "已保存「${saved.name}」",
                            isError = false,
                        )
                    }
                }
            } catch (e: WebDavException) {
                fail(e.userMessage())
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "地址格式不正确")
            } catch (e: Exception) {
                fail("操作失败：${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- 列表动作

    fun test(id: String) {
        val config = configStore.profiles().firstOrNull { it.id == id } ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true,
                busyLabel = "正在测试「${config.displayName}」…",
                message = null,
                isError = false,
            )
            runTest(config)
        }
    }

    /** 启用 / 取消启用；两者都会重跑同步。 */
    fun toggle(id: String) {
        if (_state.value.busy) return
        val enabled = configStore.isEnabled(id)
        val name = configStore.profiles().firstOrNull { it.id == id }?.displayName.orEmpty()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true,
                busyLabel = if (enabled) "正在取消启用并同步…" else "正在启用并同步…",
                message = null,
                isError = false,
            )
            try {
                if (enabled) {
                    configStore.setEnabled(id, false)
                    syncer.clearRemote(profileId = id)
                } else {
                    configStore.setEnabled(id, true)
                }
                refresh()
                val result = if (configStore.enabledConfigs().isEmpty()) {
                    "已取消启用「$name」，WebDAV 曲库已清空"
                } else {
                    "「$name」${if (enabled) "已取消启用" else "已启用"}。" + runSync()
                }
                _state.value = _state.value.copy(
                    busy = false,
                    busyLabel = "",
                    message = result,
                    isError = false,
                )
                refresh()
            } catch (e: WebDavException) {
                fail(e.userMessage())
            } catch (e: Exception) {
                fail("操作失败：${e.message}")
            }
        }
    }

    fun activate(id: String) {
        if (configStore.isEnabled(id)) {
            _state.value = _state.value.copy(message = "「${configStore.activeConfig()?.displayName}」已经是启用状态")
            return
        }
        toggle(id)
    }

    fun syncActiveNow() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "正在同步曲库…", message = null)
            try {
                _state.value = _state.value.copy(
                    busy = false,
                    busyLabel = "",
                    message = runSync(),
                    isError = false,
                )
                refresh()
            } catch (e: WebDavException) {
                fail(e.userMessage())
            } catch (e: Exception) {
                fail("同步失败：${e.message}")
            }
        }
    }

    fun delete(id: String) {
        val wasEnabled = configStore.isEnabled(id)
        val name = configStore.profiles().firstOrNull { it.id == id }?.displayName.orEmpty()
        viewModelScope.launch {
            if (wasEnabled) syncer.clearRemote(profileId = id)
            configStore.delete(id)
            refresh()
            _state.value = _state.value.copy(
                message = if (wasEnabled) "已删除「$name」并清理这一台的曲目" else "已删除「$name」",
                isError = false,
            )
            if (wasEnabled && configStore.enabledConfigs().isNotEmpty()) {
                runCatching { runSync() }
                refresh()
            }
        }
    }

    fun clearConfig() {
        configStore.clear()
        viewModelScope.launch {
            syncer.clearRemote()
            refresh()
            _state.value = UiState(message = "已清除全部 WebDAV 配置与曲目", isError = false)
        }
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun runTest(config: WebDavConfig) {
        try {
            val items = withContext(Dispatchers.IO) { client.testConnectionBlocking(config) }
            configStore.saveStatus(config.id, ServerStatus.OK, "根路径下 $items 项")
            _state.value = _state.value.copy(
                busy = false,
                busyLabel = "",
                message = "连接成功，根路径下 $items 项。",
                isError = false,
            )
        } catch (e: WebDavException) {
            configStore.saveStatus(config.id, ServerStatus.FAIL, e.userMessage())
            fail(e.userMessage())
        } catch (e: Exception) {
            configStore.saveStatus(config.id, ServerStatus.FAIL, e.message ?: "未知错误")
            fail("测试失败：${e.message}")
        } finally {
            refresh()
        }
    }

    private suspend fun activateAndSync(id: String): String {
        configStore.setEnabled(id, true)
        refresh()
        return runSync()
    }

    private suspend fun runSync(): String {
        val stats = syncer.sync()
        // 同 OpenList：同步跑通就顺手把状态行刷成「已同步 N 首」
        configStore.enabledIds().forEach { id ->
            configStore.saveStatus(id, ServerStatus.OK, "已同步 ${configStore.syncedCount(id)} 首")
        }
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncer.fillMissingDurations() }
                .onSuccess { if (it > 0) refresh() }
        }
        return "同步完成：${stats.summary}"
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(
            busy = false,
            busyLabel = "",
            isError = true,
            message = message,
        )
    }
}
