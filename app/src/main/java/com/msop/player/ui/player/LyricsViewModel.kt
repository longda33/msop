package com.msop.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.model.Track
import com.msop.player.feature.lyrics.Lyrics
import com.msop.player.feature.lyrics.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 播放页歌词。按当前曲目加载，切歌时丢弃上一次的结果。 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
) : ViewModel() {

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var currentTrackId: String? = null

    fun load(track: Track?) {
        if (track == null) {
            currentTrackId = null
            _lyrics.value = null
            _loading.value = false
            return
        }
        if (track.id == currentTrackId) return

        currentTrackId = track.id
        _lyrics.value = null
        _loading.value = true

        viewModelScope.launch {
            val result = repository.load(track)
            // 切歌很快时，上一次请求的结果不能盖掉新歌的
            if (currentTrackId == track.id) {
                _lyrics.value = result
                _loading.value = false
            }
        }
    }

    /**
     * 强制重读当前曲目的歌词（2026-11-15：歌词页三点菜单的「重新加载歌词」）。
     *
     * [load] 对同一首是幂等早退的（`track.id == currentTrackId` 直接 return），
     * 所以重载必须先把 id 清掉，否则点了菜单没有任何反应。
     */
    fun reload(track: Track?) {
        currentTrackId = null
        load(track)
    }
}
