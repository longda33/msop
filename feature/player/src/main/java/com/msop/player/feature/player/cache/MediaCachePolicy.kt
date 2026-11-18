package com.msop.player.feature.player.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.datastore.CachePreferences
import com.msop.player.core.network.ServerUrl
import com.msop.player.core.network.WebDavUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存 key 与「是否允许写入」的纯判定，抽成 JVM 可测函数，不碰 Android 类。
 *
 * 两件事最容易写错，所以单独拎出来：
 * 1. **key 必须是稳定的内部 URI**。`openlist:/路径` 永远不变，而 `/api/fs/get`
 *    换出来的 raw_url 每次都带不同的 tempauth——拿它当 key 的话，同一首歌
 *    seek 一次就多一份缓存，永远命不中。
 * 2. **「仅 Wi-Fi 缓存」只限制写，不限制读**。蜂窝下已经缓存好的歌照常从磁盘读，
 *    这反而省流量；只是不再往磁盘里写新内容。
 */
object CacheDecision {

    private const val KEY_PREFIX = "msop:media:"

    /** 只有远程曲目走缓存；本地 `content://` / `file://` 一律直连，不占缓存配额。 */
    fun isRemote(internalUri: String): Boolean =
        internalUri.startsWith(ServerUrl.SCHEME) || internalUri.startsWith(WebDavUrl.SCHEME)

    /**
     * 由稳定内部 URI 生成缓存 key。
     *
     * 2026-11-18 修复：入参已经是干净的内部 URI（`openlist:{id}:/路径`、webdav 同），
     * 一次性签名只存在于 resolver 解析出的 raw_url 上，不会进到这里。原实现对入参
     * 做 `substringBefore('?')/#`，反而把文件名里合法含有的 `?`/`#` 截掉，
     * 让 `A?1.flac` 与 `A#1.flac` 落到同一个 key、缓存命中后播放另一首歌。
     */
    fun stableKey(internalUri: String): String {
        require(isRemote(internalUri)) { "缓存 key 只能由远程内部 URI 生成：$internalUri" }
        return KEY_PREFIX + internalUri
    }

    /**
     * @param metered 当前网络是否计费/蜂窝；`null` 表示查不到网络能力。
     *   查不到时放行：边播边缓存只是把「正在下载的字节」顺手落盘，
     *   不会比不缓存多耗一份流量，不该因为 ROM 不给能力信息就把功能关掉。
     */
    fun shouldWriteCache(wifiOnly: Boolean, metered: Boolean?): Boolean =
        !wifiOnly || metered != true
}

/**
 * 决定本次 DataSource 是「读 + 写」还是「只读」缓存。
 *
 * 每次创建 DataSource（切歌、seek、重试）时同步查一次当前网络，不注册
 * `NetworkCallback`：既没有注册/注销的生命周期问题，网络切换后下一次请求也会
 * 立刻按新状态决策。查询发生在 Media3 的加载线程，是轻量 Binder 调用。
 */
@Singleton
class MediaCachePolicy @Inject constructor(
    @ApplicationContext context: Context,
    private val prefs: CachePreferences,
) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun allowWrite(): Boolean {
        val wifiOnly = prefs.wifiOnlyCaching
        val metered = currentNetworkMetered()
        val allowed = CacheDecision.shouldWriteCache(wifiOnly, metered)
        AppLog.d("远程缓存策略 wifiOnly=$wifiOnly metered=$metered 写入=$allowed")
        return allowed
    }

    /** `true` = 蜂窝/计费网络，`false` = Wi-Fi 等不计费网络，`null` = 未知。 */
    private fun currentNetworkMetered(): Boolean? {
        // 2026-11-17 重构：原来是 runCatching { ... return null ... }.getOrElse { null }，
        // lambda 里的 `return null` 是非局部返回，直接跳出函数——runCatching 的
        // 兜底分支其实永远不会拿到「查不到网络」。改成显式 try/catch，语义一眼可读。
        return try {
            val manager = connectivity ?: return null
            val network = manager.activeNetwork ?: return null
            val caps = manager.getNetworkCapabilities(network) ?: return null
            // Android SDK 里没有 NET_CAPABILITY_METERED（只有正向能力 NET_CAPABILITY_NOT_METERED），
            // 所以「计费」= 蜂窝网络，或系统没给它标「不计费」（用户把某个 Wi-Fi 设成按流量计费）。
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Exception) {
            AppLog.w("读取网络计费状态失败：${e.message}")
            null
        }
    }
}
