package com.primuse.lite.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primuse.lite.core.database.dao.PlaylistDao
import com.primuse.lite.core.database.entity.PlaylistEntity
import com.primuse.lite.core.database.mapper.toTrack
import com.primuse.lite.core.model.Playlist
import com.primuse.lite.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val dao: PlaylistDao,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun tracks(playlistId: Long): Flow<List<Track>> =
        dao.observeTracks(playlistId).map { entities -> entities.map { it.toTrack() } }

    fun create(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.insertPlaylist(
                PlaylistEntity(name = clean.take(40), createdAt = System.currentTimeMillis()),
            )
        }
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { dao.rename(id, clean.take(40)) }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun addTracks(playlistId: Long, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            dao.addTracks(
                playlistId = playlistId,
                trackIds = tracks.map { it.id },
                addedAt = System.currentTimeMillis(),
            )
        }
    }

    fun removeTrack(playlistId: Long, trackId: String) = viewModelScope.launch {
        dao.removeTrack(playlistId, trackId)
    }
}
