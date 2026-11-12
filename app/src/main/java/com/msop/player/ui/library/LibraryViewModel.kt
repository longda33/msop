package com.msop.player.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.Track
import com.msop.player.feature.player.PlaybackController
import com.msop.player.feature.player.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("msop_ui_prefs", Context.MODE_PRIVATE)

    /** 最近搜索，新的在前。 */
    private val _recentSearches = MutableStateFlow(readList(KEY_RECENT_SEARCHES))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /** 最近播放的 trackId，新的在前。 */
    private val _recentPlayedIds = MutableStateFlow(readList(KEY_RECENT_PLAYED))
    val recentPlayedIds: StateFlow<List<String>> = _recentPlayedIds.asStateFlow()

    /** 收藏的 trackId。 */
    private val _favorites = MutableStateFlow(readSet(KEY_FAVORITES))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

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
        recordPlayed(list[safeIndex].id)
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

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = (listOf(q) + _recentSearches.value.filter { it != q }).take(12)
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("\n")).apply()
    }

    fun clearSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    fun toggleFavorite(id: String) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    private fun recordPlayed(id: String) {
        val next = (listOf(id) + _recentPlayedIds.value.filter { it != id }).take(30)
        _recentPlayedIds.value = next
        prefs.edit().putString(KEY_RECENT_PLAYED, next.joinToString("\n")).apply()
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun readSet(key: String): Set<String> =
        prefs.getStringSet(key, null) ?: emptySet()

    private companion object {
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_RECENT_PLAYED = "recent_played"
        const val KEY_FAVORITES = "favorites"
    }
}
