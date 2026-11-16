package com.msop.player.core.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题模式偏好（2026-11-14 深浅色，仅两档）。
 *
 * 用 SharedPreferences 而不是 DataStore：与 [CachePreferences] 同一决策——
 * 主题需要在 Activity setContent **同步**读取初值，避免闪一次默认色再切换；
 * 改动通过 MainActivity 的状态 + 回调即时生效，无需跨层通知。
 *
 * 只存原始字符串（"DARK"/"LIGHT"）；枚举定义在 app 层的
 * `ui.theme.ThemeMode`——core 模块不反向依赖 app。默认深色：未设置时返回 null，
 * 由调用方兑默认。
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** null = 未设置，调用方兑 DARK（默认深色）。 */
    val themeModeName: String?
        get() = prefs.getString(KEY_MODE, null)

    fun setThemeModeName(name: String) {
        prefs.edit().putString(KEY_MODE, name).apply()
    }

    private companion object {
        const val PREFS_NAME = "msop_appearance"
        const val KEY_MODE = "theme_mode"
    }
}
