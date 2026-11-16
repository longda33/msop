package com.msop.player.desktop

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 桌面悬浮歌词的本地偏好（P7-08）。
 *
 * 用 SharedPreferences 而不是 DataStore：悬浮歌词服务在 `onCreate` 里**同步**读样式，
 * 用 DataStore 会逼出 runBlocking。这里的字段都很小，也不参与备份。
 */
@Singleton
class DesktopLyricsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 12–32sp。 */
    var fontSp: Int
        get() = prefs.getInt(KEY_FONT_SP, DEFAULT_FONT_SP).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        set(value) = prefs.edit().putInt(KEY_FONT_SP, value.coerceIn(MIN_FONT_SP, MAX_FONT_SP)).apply()

    /** [TEXT_COLORS] 的下标。 */
    var colorIndex: Int
        get() = prefs.getInt(KEY_COLOR, 0).coerceIn(0, TEXT_COLORS.lastIndex)
        set(value) = prefs.edit().putInt(KEY_COLOR, value.coerceIn(0, TEXT_COLORS.lastIndex)).apply()

    /** 背景黑色透明度，0–80（百分比）。 */
    var backgroundPercent: Int
        get() = prefs.getInt(KEY_BACKGROUND, DEFAULT_BACKGROUND).coerceIn(0, MAX_BACKGROUND)
        set(value) = prefs.edit()
            .putInt(KEY_BACKGROUND, value.coerceIn(0, MAX_BACKGROUND))
            .apply()

    var singleLine: Boolean
        get() = prefs.getBoolean(KEY_SINGLE_LINE, false)
        set(value) = prefs.edit().putBoolean(KEY_SINGLE_LINE, value).apply()

    /** 锁定后窗口加 `FLAG_NOT_TOUCHABLE`，不会挡住桌面/其他 App 的点击。 */
    var locked: Boolean
        get() = prefs.getBoolean(KEY_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCKED, value).apply()

    /** 悬浮窗位置（屏幕绝对像素）。[UNSET_POSITION] 表示还没拖过，由服务按屏幕尺寸给默认值。 */
    var x: Int
        get() = prefs.getInt(KEY_X, UNSET_POSITION)
        set(value) = prefs.edit().putInt(KEY_X, value).apply()

    var y: Int
        get() = prefs.getInt(KEY_Y, UNSET_POSITION)
        set(value) = prefs.edit().putInt(KEY_Y, value).apply()

    fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val MIN_FONT_SP = 12
        const val MAX_FONT_SP = 32
        const val DEFAULT_FONT_SP = 18
        const val MAX_BACKGROUND = 80
        const val DEFAULT_BACKGROUND = 30
        const val UNSET_POSITION = Int.MIN_VALUE

        /** 白色 + 6 个预设色。值直接给 TextView 用（ARGB Int）。 */
        val TEXT_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFD166.toInt(),
            0xFF7FE7FF.toInt(),
            0xFFFF9EC4.toInt(),
            0xFF8CFFB4.toInt(),
            0xFFC7B3FF.toInt(),
            0xFFFFB37A.toInt(),
        )

        private const val PREFS_NAME = "msop_desktop_lyrics"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FONT_SP = "font_sp"
        private const val KEY_COLOR = "color_index"
        private const val KEY_BACKGROUND = "background_percent"
        private const val KEY_SINGLE_LINE = "single_line"
        private const val KEY_LOCKED = "locked"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
    }
}
