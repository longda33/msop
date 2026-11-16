package com.msop.player.ui.settings

import androidx.lifecycle.ViewModel
import com.msop.player.desktop.DesktopLyricsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 桌面悬浮歌词的设置状态（P7-08）。写入即落 SharedPreferences，服务监听后实时刷新。 */
@HiltViewModel
class DesktopLyricsViewModel @Inject constructor(
    private val prefs: DesktopLyricsPreferences,
) : ViewModel() {

    data class UiState(
        val enabled: Boolean = false,
        val fontSp: Int = DesktopLyricsPreferences.DEFAULT_FONT_SP,
        val colorIndex: Int = 0,
        val backgroundPercent: Int = DesktopLyricsPreferences.DEFAULT_BACKGROUND,
        val singleLine: Boolean = false,
        val locked: Boolean = false,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.enabled = value
        _state.value = _state.value.copy(enabled = value)
    }

    fun setFontSize(value: Int) {
        prefs.fontSp = value
        _state.value = _state.value.copy(fontSp = prefs.fontSp)
    }

    fun setColor(index: Int) {
        prefs.colorIndex = index
        _state.value = _state.value.copy(colorIndex = prefs.colorIndex)
    }

    fun setBackground(percent: Int) {
        prefs.backgroundPercent = percent
        _state.value = _state.value.copy(backgroundPercent = prefs.backgroundPercent)
    }

    fun setSingleLine(value: Boolean) {
        prefs.singleLine = value
        _state.value = _state.value.copy(singleLine = value)
    }

    fun setLocked(value: Boolean) {
        prefs.locked = value
        _state.value = _state.value.copy(locked = value)
    }

    private fun read() = UiState(
        enabled = prefs.enabled,
        fontSp = prefs.fontSp,
        colorIndex = prefs.colorIndex,
        backgroundPercent = prefs.backgroundPercent,
        singleLine = prefs.singleLine,
        locked = prefs.locked,
    )
}
