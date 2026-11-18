package com.msop.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.datastore.ServerConfig
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.datastore.ServerStatus
import com.msop.player.core.datastore.ServerStatusInfo
import com.msop.player.core.network.OpenListClient
import com.msop.player.core.network.OpenListException
import com.msop.player.core.network.ServerUrl
import com.msop.player.core.network.SubsonicClient
import com.msop.player.core.network.userMessage
import com.msop.player.feature.remote.RemoteCoverExtractor
import com.msop.player.feature.remote.RemoteLibrarySyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OpenList 服务器管理（2026-11-16 起支持多条配置，且**多条可同时启用**）。
 *
 * 列表每行一台，行内菜单可以「启用 / 取消启用」（[toggle]）：启用后会立刻重跑
 * 一次同步，把这一台的曲目并进曲库；取消启用会先删掉这一台的曲目再重扫，避免
 * 曲库里留着打不开的条目。编辑器的「保存并同步」等价于「启用 + 全量同步」。
 */
@HiltViewModel
class OpenListViewModel @Inject constructor(
    private val configStore: ServerConfigStore,
    private val client: OpenListClient,
    private val subsonic: SubsonicClient,
    private val syncer: RemoteLibrarySyncer,
    private val remoteCovers: RemoteCoverExtractor,
) : ViewModel() {

    /** 列表里的一行。 */
    data class ProfileRow(
        val config: ServerConfig,
        val status: ServerStatusInfo,
        val enabled: Boolean,
        val syncedCount: Int,
    )

    data class UiState(
        // 编辑器表单
        val name: String = "",
        val url: String = "",
        val username: String = "",
        val password: String = "",
        /** OpenList 媒体应用密钥，可选；填了才有真实封面与艺术家/专辑。 */
        val appKey: String = "",
        /** 要同步的音乐目录，留空同步整个网盘根目录。 */
        val rootPath: String = "",
        /** 请求并发数（表单里是字符串，空串 = 用默认 4）。 */
        val concurrency: String = "",
        /** 正在编辑的配置 id；null 表示「新增」。 */
        val editingId: String? = null,
        val editorOpen: Boolean = false,
        val busy: Boolean = false,
        val busyLabel: String = "",
        val message: String? = null,
        val isError: Boolean = false,
        // 列表
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

    /** 重新读一遍配置列表（测试/同步/删除后都要刷新）。 */
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
            appKey = "",
            rootPath = "",
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
            appKey = config.appKey,
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
    fun onAppKeyChange(value: String) = edit { copy(appKey = value) }
    fun onRootPathChange(value: String) = edit { copy(rootPath = value) }
    fun onConcurrencyChange(value: String) = edit {
        copy(concurrency = value.filter { it.isDigit() }.take(2))
    }

    private inline fun edit(block: UiState.() -> UiState) {
        _state.value = _state.value.block().copy(message = null, isError = false)
    }

    /** 保存但不测试、不同步。 */
    fun save() = save(allowPlainHttp = false)

    fun save(allowPlainHttp: Boolean) = persistAnd(allowPlainHttp)

    /** 保存 → 只测连通性。 */
    fun testCurrent(allowPlainHttp: Boolean = false) = persistAnd(allowPlainHttp, thenTest = true)

    /** 保存 → 启用 → 全量同步曲库。 */
    fun saveAndSync(allowPlainHttp: Boolean = false) =
        persistAnd(allowPlainHttp, thenSync = true)

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
                val saved = configStore.upsert(
                    ServerConfig(
                        id = current.editingId.orEmpty(),
                        name = current.name.trim(),
                        baseUrl = normalized.baseUrl,
                        username = current.username.trim(),
                        password = current.password,
                        allowPlainHttp = normalized.isPlainHttp && allowPlainHttp,
                        appKey = current.appKey.trim(),
                        // 2026-11-17 深夜（续）：「路径支持填写多条（一行一条）」——
                        // 保存时逐行 trim 后按行存回，空行丢掉；不要用 trim() 把多行压成一行。
                        rootPath = current.rootPath.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString("\n"),
                        concurrency = current.concurrency.toIntOrNull() ?: 0,
                    ),
                )
                client.clearSession()
                _state.value = _state.value.copy(
                    editingId = saved.id,
                    url = normalized.baseUrl,
                    name = saved.name,
                    // 2026-11-17 深夜（五）：把归一化后的路径回填到输入框，
                    // 否则保存后框里还是原文（多行时看不出一行一条的整理结果）。
                    rootPath = saved.rootPath,
                )
                refresh()

                when {
                    thenTest -> {
                        _state.value = _state.value.copy(busyLabel = "正在连接服务器…")
                        runTest(saved)
                    }
                    thenSync -> {
                        _state.value = _state.value.copy(busyLabel = "正在同步曲库…")
                        val result = enableAndSync(saved.id)
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
            } catch (e: OpenListException) {
                fail(e.userMessage())
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "地址格式不正确")
            } catch (e: Exception) {
                fail("操作失败：${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- 列表动作

    /** 测试某条已保存的配置（不影响启用状态）。 */
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

    /** 启用 / 取消启用。两者都会触发一次全量同步，让曲库与启用集合保持一致。 */
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
                    // 取消启用：先删掉这一台的曲目，再按剩下的启用项重扫，
                    // 这样另一台并存的那首歌还能回来（去重时被合并掉了）
                    syncer.clearRemote(profileId = id)
                } else {
                    configStore.setEnabled(id, true)
                    client.clearSession()
                }
                refresh()
                val result = if (configStore.enabledConfigs().isEmpty()) {
                    "已取消启用「$name」，OpenList 曲库已清空"
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
            } catch (e: OpenListException) {
                fail(e.userMessage())
            } catch (e: Exception) {
                fail("操作失败：${e.message}")
            }
        }
    }

    /** 兼容旧调用点：启用某条配置并同步。 */
    fun activate(id: String) {
        val enabled = configStore.isEnabled(id)
        if (enabled) {
            _state.value = _state.value.copy(message = "「${configStore.activeConfig()?.displayName}」已经是启用状态")
            return
        }
        toggle(id)
    }

    /** 只对启用项重跑同步（多台一起扫）。 */
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
            } catch (e: OpenListException) {
                fail(e.userMessage())
            } catch (e: Exception) {
                fail("同步失败：${e.message}")
            }
        }
    }

    /**
     * 手动删除一条配置。
     *
     * 删的是启用项时，会一并清掉这一台的曲目并重扫剩下的启用项；删的未启用项
     * 则不动曲库。
     */
    fun delete(id: String) {
        val wasEnabled = configStore.isEnabled(id)
        val name = configStore.profiles().firstOrNull { it.id == id }?.displayName.orEmpty()
        viewModelScope.launch {
            if (wasEnabled) {
                syncer.clearRemote(profileId = id)
                client.clearSession()
            }
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

    /** 清空全部 OpenList 配置与曲目。 */
    fun clearConfig() {
        configStore.clear()
        client.clearSession()
        viewModelScope.launch {
            syncer.clearRemote()
            refresh()
            _state.value = UiState(message = "已清除全部 OpenList 配置与远程曲目", isError = false)
        }
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun runTest(config: ServerConfig) {
        try {
            // 阻塞式 HTTP 调用必须挪到 IO 线程，否则主线程会抛 NetworkOnMainThreadException
            val total = withContext(Dispatchers.IO) { client.testConnectionBlocking(config) }
            val hint = if (config.appKey.isNotBlank()) {
                try {
                    "Subsonic 密钥有效，媒体库 ${subsonic.testConnection()} 首。"
                } catch (e: OpenListException) {
                    "Subsonic 密钥不可用：${e.userMessage()}"
                }
            } else {
                "未填 Subsonic 应用密钥，同步后只有文件名、没有封面与艺术家。"
            }
            configStore.saveStatus(config.id, ServerStatus.OK, "根目录 $total 项")
            _state.value = _state.value.copy(
                busy = false,
                busyLabel = "",
                message = "连接成功，根目录共 $total 项。$hint",
                isError = false,
            )
        } catch (e: OpenListException) {
            configStore.saveStatus(config.id, ServerStatus.FAIL, e.userMessage())
            fail(e.userMessage())
        } catch (e: Exception) {
            configStore.saveStatus(config.id, ServerStatus.FAIL, e.message ?: "未知错误")
            fail("测试失败：${e.message}")
        } finally {
            refresh()
        }
    }

    private suspend fun enableAndSync(id: String): String {
        configStore.setEnabled(id, true)
        client.clearSession()
        refresh()
        return runSync()
    }

    private suspend fun runSync(): String {
        val stats = syncer.sync()
        // 同步跑通说明连接也是通的，顺手把状态行刷成「已同步 N 首」，
        // 否则保存会清状态，列表里会一直显示「未测试」却已经有曲目了。
        configStore.enabledIds().forEach { id ->
            val own = configStore.syncedCount(id)
            configStore.saveStatus(id, ServerStatus.OK, "已同步 $own 首")
        }
        refresh()
        // 云端时长只能读文件头自己算（服务端不给），放后台慢慢补，不挡同步结果
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncer.fillMissingDurations() }
                .onSuccess { if (it > 0) refresh() }
            // 2026-11-18（用户报「新添加服务器后封面不显示，要重启才回来」）：
            // 新入库的曲目 `coverUri` 还是 null，以前要等下次冷启动的探测流程才补；
            // 同步完顺手探一轮，新加的服务器当场就有封面。
            runCatching { remoteCovers.extract() }
                .onSuccess { if (it.covers > 0) refresh() }
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
