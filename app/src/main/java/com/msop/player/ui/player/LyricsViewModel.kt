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
}
