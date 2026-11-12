package com.msop.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.Track
import com.msop.player.feature.player.PlaybackController
import com.msop.player.feature.player.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 曲库与播放的共享状态。
 *
 * 为什么队列放在这里而不是问 [PlaybackController] 要：
 * 控制器里只有 `MediaItem`，而播放页要显示封面、时长、来源，需要完整的 [Track]。
 * 与其把 MediaItem 反解回 Track（信息不全，还得查库），不如在入队时把这份列表记下来。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    trackDao: TrackDao,
    private val playback: PlaybackController,
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = trackDao.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playbackState: StateFlow<PlaybackState> = playback.state

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    /** 播放进度。只在播放页可见时由页面轮询刷新，不在后台空转。 */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /**
     * 播放一个列表，从第 [index] 首开始。
     *
     * 点歌行为按国内播放器惯例：**整个当前列表入队**，而不是只播这一首。
     */
    fun play(list: List<Track>, index: Int) {
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(0, list.lastIndex)
        _queue.value = list
        viewModelScope.launch {
            playback.playQueue(list, safeIndex)
            _positionMs.value = 0L
        }
    }

    /** 在当前曲库列表里找到这首歌并播放。 */
    fun playTrack(track: Track, from: List<Track> = tracks.value) {
        val index = from.indexOfFirst { it.id == track.id }
        play(from, if (index >= 0) index else 0)
    }

    fun toggle() = viewModelScope.launch { playback.toggle() }

    fun next() = viewModelScope.launch { playback.next() }

    fun previous() = viewModelScope.launch { playback.previous() }

    fun seekTo(positionMs: Long) {
        _positionMs.value = positionMs
        viewModelScope.launch { playback.seekTo(positionMs) }
    }

    fun cycleMode() = viewModelScope.launch { playback.cycleMode() }

    fun moveInQueue(from: Int, to: Int) = viewModelScope.launch {
        playback.moveInQueue(from, to)
        _queue.value = _queue.value.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** 播放页每 500ms 调一次。页面不可见时不要调。 */
    fun refreshPosition() = viewModelScope.launch {
        _positionMs.value = playback.currentPositionMs()
    }
}
