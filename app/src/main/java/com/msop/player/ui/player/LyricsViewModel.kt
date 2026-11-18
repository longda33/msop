package com.msop.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.model.Track
import com.msop.player.feature.lyrics.Lyrics
import com.msop.player.feature.lyrics.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    /** 当前这次加载；切歌 / 重载前取消，避免上一次的 Range 请求跑完把结果写回来。 */
    private var loadJob: Job? = null

    /**
     * 每次加载自增的序号。只比对 [currentTrackId] 不够——[reload] 会把 id 清空再设回来，
     * 此时旧协程恢复后看到的 id 又是「当前曲」，照样会覆盖新结果。
     */
    private var loadToken = 0

    fun load(track: Track?) {
        if (track == null) {
            loadJob?.cancel()
            loadJob = null
            currentTrackId = null
            _lyrics.value = null
            _loading.value = false
            return
        }
        if (track.id == currentTrackId) return

        currentTrackId = track.id
        _lyrics.value = null
        _loading.value = true
        startLoad(track)
    }

    /**
     * 强制重读当前曲目的歌词（2026-11-15：歌词页三点菜单的「重新加载歌词」）。
     *
     * [load] 对同一首是幂等早退的（`track.id == currentTrackId` 直接 return），
     * 所以重载必须自己起一次新加载，不能指望 [load]。
     */
    fun reload(track: Track?) {
        if (track == null) {
            load(null)
            return
        }
        currentTrackId = track.id
        _lyrics.value = null
        _loading.value = true
        startLoad(track)
    }

    private fun startLoad(track: Track) {
        loadJob?.cancel()
        val token = ++loadToken
        loadJob = viewModelScope.launch {
            val result = repository.load(track)
            // 切歌很快时，上一次请求的结果不能盖掉新歌的
            if (token == loadToken && currentTrackId == track.id) {
                _lyrics.value = result
                _loading.value = false
            }
        }
    }
}
