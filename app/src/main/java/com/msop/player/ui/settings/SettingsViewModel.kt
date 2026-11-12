package com.msop.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.feature.local.metadata.CoverCache
import com.msop.player.feature.player.equalizer.EqualizerController
import com.msop.player.feature.player.equalizer.EqualizerTuning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页的状态：封面缓存、均衡器、后台保活。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val coverCache: CoverCache,
    private val equalizer: EqualizerController,
) : ViewModel() {

    /** 与 PlaybackService 共用同一个单例，服务 attach 后段数会自动刷新。 */
    val equalizerState: StateFlow<com.msop.player.feature.player.equalizer.EqualizerState> =
        equalizer.state

    fun setEqualizerEnabled(enabled: Boolean) = equalizer.setEnabled(enabled)
    fun applyEqualizerPreset(preset: EqualizerTuning.Preset) = equalizer.applyPreset(preset)
    fun setEqualizerBand(index: Int, levelMb: Int) = equalizer.setBandLevel(index, levelMb)
    fun resetEqualizer() = equalizer.reset()

    data class CacheState(
        val count: Int = 0,
        val bytes: Long = 0L,
        val loading: Boolean = true,
    )

    private val _cache = MutableStateFlow(CacheState())
    val cache: StateFlow<CacheState> = _cache.asStateFlow()

    fun refreshCache() {
        viewModelScope.launch {
            _cache.value = _cache.value.copy(loading = true)
            loadCache(loading = false)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _cache.value = _cache.value.copy(loading = true)
            coverCache.clear()
            loadCache(loading = false)
        }
    }

    private suspend fun loadCache(loading: Boolean) {
        val count = coverCache.count()
        val bytes = coverCache.sizeBytes()
        _cache.value = CacheState(count = count, bytes = bytes, loading = loading)
    }
}
