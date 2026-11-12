package com.msop.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.datastore.ServerConfig
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.network.OpenListClient
import com.msop.player.core.network.OpenListException
import com.msop.player.core.network.ServerUrl
import com.msop.player.core.network.SubsonicClient
import com.msop.player.core.network.userMessage
import com.msop.player.feature.remote.RemoteLibrarySyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpenListViewModel @Inject constructor(
    private val configStore: ServerConfigStore,
    private val client: OpenListClient,
    private val subsonic: SubsonicClient,
    private val syncer: RemoteLibrarySyncer,
) : ViewModel() {

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: String = "",
        /** OpenList 媒体应用密钥，可选；填了才有真实封面与艺术家/专辑。 */
        val appKey: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
        val configured: Boolean = false,
        val syncedCount: Int = 0,
        val lastSyncAt: Long = 0L,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val saved = configStore.load()
        _state.value = UiState(
            url = saved?.baseUrl.orEmpty(),
            username = saved?.username.orEmpty(),
            password = saved?.password.orEmpty(),
            appKey = saved?.appKey.orEmpty(),
            configured = saved?.configured == true,
            syncedCount = configStore.lastSyncedTrackCount(),
            lastSyncAt = configStore.lastSyncedAt(),
        )
    }

    fun onUrlChange(value: String) = edit { copy(url = value) }
    fun onUsernameChange(value: String) = edit { copy(username = value) }
    fun onPasswordChange(value: String) = edit { copy(password = value) }
    fun onAppKeyChange(value: String) = edit { copy(appKey = value) }

    private inline fun edit(block: UiState.() -> UiState) {
        _state.value = _state.value.block().copy(message = null, isError = false)
    }

    /** 只验证连通性，不同步曲库。会先把当前输入落库，改配置后旧 JWT 由客户端作废。 */
    fun testConnection(allowPlainHttp: Boolean = false) = launchConfigAction(allowPlainHttp) {
        val total = client.testConnection()
        val subsonicHint = if (_state.value.appKey.isNotBlank()) {
            try {
                "Subsonic 密钥有效，媒体库 ${subsonic.testConnection()} 首。"
            } catch (e: OpenListException) {
                "Subsonic 密钥不可用：${e.userMessage()}"
            }
        } else {
            "未填 Subsonic 应用密钥，同步后只有文件名、没有封面与艺术家。"
        }
        "连接成功，根目录共 $total 项。$subsonicHint"
    }

    /** 保存配置 → 验证登录 → 递归同步远程曲库。 */
    fun saveAndSync(allowPlainHttp: Boolean = false) = launchConfigAction(allowPlainHttp) {
        client.testConnection()
        val stats = syncer.sync()
        _state.value = _state.value.copy(
            configured = true,
            syncedCount = stats.tracks,
            lastSyncAt = configStore.lastSyncedAt(),
        )
        "同步完成：${stats.summary}"
    }

    fun clearConfig() {
        configStore.clear()
        client.clearSession()
        viewModelScope.launch {
            // 清掉远程曲目，避免本地曲库里留下一堆打不开的条目
            syncer.clearRemote()
        }
        _state.value = UiState(message = "已清除服务器配置与远程曲目", isError = false)
    }

    private fun launchConfigAction(
        allowPlainHttp: Boolean,
        action: suspend () -> String,
    ) {
        val current = _state.value
        if (current.busy) return
        viewModelScope.launch {
            _state.value = current.copy(busy = true, message = null, isError = false)
            try {
                val normalized = ServerUrl.normalize(current.url)
                if (normalized.isPlainHttp && !allowPlainHttp) {
                    _state.value = _state.value.copy(
                        busy = false,
                        isError = true,
                        message = "http:// 是明文传输，请确认后再继续，或改用 https://",
                    )
                    return@launch
                }
                configStore.save(
                    ServerConfig(
                        baseUrl = normalized.baseUrl,
                        username = current.username.trim(),
                        password = current.password,
                        allowPlainHttp = normalized.isPlainHttp && allowPlainHttp,
                        appKey = current.appKey.trim(),
                    ),
                )
                _state.value = _state.value.copy(
                    url = normalized.baseUrl,
                    configured = true,
                )
                client.clearSession()
                val result = action()
                _state.value = _state.value.copy(busy = false, message = result, isError = false)
            } catch (e: OpenListException) {
                _state.value = _state.value.copy(
                    busy = false,
                    isError = true,
                    message = e.userMessage(),
                )
            } catch (e: IllegalArgumentException) {
                _state.value = _state.value.copy(
                    busy = false,
                    isError = true,
                    message = e.message ?: "地址格式不正确",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    isError = true,
                    message = "操作失败：${e.message}",
                )
            }
        }
    }
}
