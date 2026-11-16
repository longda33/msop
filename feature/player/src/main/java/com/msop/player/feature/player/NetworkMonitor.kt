package com.msop.player.feature.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.msop.player.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络在线状态（P7-03）。
 *
 * 只关心「有没有可用网络」，不区分 Wi-Fi/蜂窝——远程音源的断网保护不需要更细的信息；
 * 「仅 Wi-Fi 缓存」的计费判断在 `MediaCachePolicy` 里单独做。
 *
 * 用 `registerDefaultNetworkCallback`（API 24+）而不是轮询：断网要尽可能在 1 秒内
 * 触发暂停，轮询做不到。回调可能重复触发，这里统一收敛成 [online] 的布尔值。
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(hasInternet())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            connectivity?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _online.value = true
                    }

                    override fun onLost(network: Network) {
                        // 切换网络时会先 lost 后 available，这里重新问一次当前活动网络，
                        // 避免 Wi-Fi → 蜂窝的切换把播放误暂停。
                        _online.value = hasInternet()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        _online.value = capabilities
                            .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                },
            )
        }.onFailure { AppLog.w("网络状态监听注册失败，按在线处理", it) }
    }

    fun isOnline(): Boolean = _online.value || hasInternet()

    private fun hasInternet(): Boolean {
        val manager = connectivity ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
