package com.msop.player.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程播放缓存的偏好设置（P3-04）。
 *
 * 用 SharedPreferences 而不是 DataStore，理由和 [ServerConfigStore] 一样：
 * 播放层的 `DataSource.Factory` 要在 Media3 的加载线程上**同步**读到开关值，
 * DataStore 的挂起 API 会逼出 `runBlocking`。每次创建 DataSource 时重新读一次，
 * 所以设置页改完开关，下一次切歌 / seek 立即生效，不需要跨进程发通知。
 *
 * 和服务器配置分开存：清掉 OpenList 账号时不该顺手把播放偏好也清掉。
 */
@Singleton
class CachePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 「仅 Wi-Fi 缓存」。默认开：蜂窝下不往磁盘写远程音频，但已缓存内容照常读。
     * 关闭后任何网络都写入缓存。
     */
    val wifiOnlyCaching: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnlyCaching(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "msop_playback_cache"
        const val KEY_WIFI_ONLY = "wifi_only_caching"
    }
}
