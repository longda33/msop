package com.primuse.lite.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primuse.lite.feature.local.metadata.CoverCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页里需要读写磁盘的状态：目前只有封面缓存。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val coverCache: CoverCache,
) : ViewModel() {

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
