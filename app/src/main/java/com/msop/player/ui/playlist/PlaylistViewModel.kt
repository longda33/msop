package com.msop.player.ui.playlist

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.database.dao.FavoriteDao
import com.msop.player.core.database.dao.HiddenTrackDao
import com.msop.player.core.database.dao.PlaylistDao
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.entity.HiddenTrackEntity
import com.msop.player.core.database.entity.PlaylistEntity
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.Playlist
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.DownloadCenter
import com.msop.player.core.network.RemoteFileDownloader
import com.msop.player.core.model.TrackMerger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val favoriteDao: FavoriteDao,
    private val trackDao: TrackDao,
    private val hiddenDao: HiddenTrackDao,
    private val downloader: RemoteFileDownloader,
    private val downloadCenter: DownloadCenter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏曲目（歌单 Tab 顶部置顶的虚拟歌单）。 */
    val favorites: StateFlow<List<Track>> = favoriteDao.observeTracks()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏集合，供三点菜单显示「已收藏」。 */
    val favoriteIds: StateFlow<Set<String>> = favoriteDao.observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** trackId → 已加入的歌单 id 集合，三点菜单据此显示「已加歌单」。 */
    val memberships: StateFlow<Map<String, Set<Long>>> = dao.observeMemberships()
        .map { rows -> rows.groupBy({ it.trackId }, { it.playlistId }).mapValues { it.value.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 已移除歌曲列表（墓碑 + 曲目元数据），供曲库右上角「已移除歌曲」页展示。 */
    val removedTracks: StateFlow<List<Track>> = hiddenDao.observeHidden()
        .map { rows -> rows.map { it.track.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 恢复：删墓碑行，曲目重新出现在曲库（不用重扫）。
     *
     * 2026-11-16 晚第十三轮：移除时会把「同一首歌」的全部副本一起墓碑（见
     * [TrackMerger.twinsOf]），所以恢复也要一起复原，否则看着恢复了一行、其实另一份还
     * 藏在墓碑里——下次移除又「回来」。
     */
    fun restoreToLibrary(track: Track) = viewModelScope.launch {
        val hiddenNow = removedTracks.value
        val ids = TrackMerger.twinsOf(track, hiddenNow).map { it.id }.toSet() + track.id
        ids.forEach { hiddenDao.restore(it) }
    }

    fun tracks(playlistId: Long): Flow<List<Track>> =
        dao.observeTracks(playlistId).map { entities -> entities.map { it.toTrack() } }

    fun create(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            // 新建的歌单排在最上面：sortOrder 取当前最小值 - 1
            val order = (dao.minSortOrder() ?: 0) - 1
            dao.insertPlaylist(
                PlaylistEntity(
                    name = clean.take(40),
                    createdAt = System.currentTimeMillis(),
                    sortOrder = order,
                ),
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

    /** 歌单列表拖动排序落库。 */
    fun reorderPlaylists(orderedIds: List<Long>) = viewModelScope.launch {
        dao.reorderPlaylists(orderedIds)
    }

    /** 歌单内曲目拖动排序落库。 */
    fun reorderTracks(playlistId: Long, orderedTrackIds: List<String>) = viewModelScope.launch {
        dao.reorderTracks(playlistId, orderedTrackIds)
    }

    /** 移动到其它歌单。 */
    fun moveTrack(fromPlaylistId: Long, toPlaylistId: Long, trackId: String) =
        viewModelScope.launch {
            dao.moveTrack(
                fromPlaylistId = fromPlaylistId,
                toPlaylistId = toPlaylistId,
                trackId = trackId,
                addedAt = System.currentTimeMillis(),
            )
        }

    fun toggleFavorite(trackId: String) = viewModelScope.launch {
        favoriteDao.toggle(trackId, System.currentTimeMillis())
    }

    fun removeFavorite(trackId: String) = viewModelScope.launch { favoriteDao.remove(trackId) }

    /**
     * 从曲库移除（2026-11-16 晚用户需求改版）。
     *
     * 旧实现是 `trackDao.deleteById(track.id)`——重扫一次歌又回来了（用户报的 bug）。
     * 现在改成「墓碑」：曲目行留在 `tracks`，往 `hidden_tracks` 写一行把它挡在曲库之外，
     * 扫描的 `@Upsert` 碰不到这张表，所以重扫不会复活；「已移除歌曲」页删掉墓碑即恢复。
     * 同时把歌单/收藏里的关联清掉，否则恢复前它还会在歌单里出现。
     */
    fun removeFromLibrary(track: Track) = viewModelScope.launch {
        // 同一首歌的多份副本一起墓碑：列表上只有一行，数据库里可能有同文件第二份或云端
        // 同一首，只墓碑一条的话另一条会把这一行顶回来（用户会以为「移除又复活」）。
        val all = trackDao.all().map { it.toTrack() }
        val ids = TrackMerger.twinsOf(track, all).map { it.id }.toSet() + track.id
        ids.forEach { id ->
            dao.removeTrackFromAllPlaylists(id)
            favoriteDao.remove(id)
            hiddenDao.hide(HiddenTrackEntity(id = id, removedAt = System.currentTimeMillis()))
        }
    }    // ------------------------------------------------------------ 多选批量操作（2026-11-16 晚）
    // 用户要求「歌单类目下歌曲无法批量操作」→ 多选后可以批量移除 / 移动到其它歌单 /
    // 加入其它歌单 / 下载云端曲目。四个动作都只碰数据库或下载器，不改变单首行为。

    fun removeTracks(playlistId: Long, trackIds: List<String>) = viewModelScope.launch {
        if (trackIds.isEmpty()) return@launch
        dao.removeTracks(playlistId, trackIds)
    }

    fun moveTracks(fromPlaylistId: Long, toPlaylistId: Long, trackIds: List<String>) =
        viewModelScope.launch {
            if (trackIds.isEmpty()) return@launch
            dao.moveTracks(fromPlaylistId, toPlaylistId, trackIds, System.currentTimeMillis())
        }

    /** 批量加入其它歌单（原歌单条目保留）。 */
    fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) = viewModelScope.launch {
        if (trackIds.isEmpty()) return@launch
        dao.addTracks(playlistId, trackIds, System.currentTimeMillis())
    }

    /** 批量下载选中的云端曲目：逐首串行下载，完成后一条 toast 汇总。 */
    fun downloadTracks(tracks: List<Track>) = viewModelScope.launch {
        val cloud = tracks.filter { it.source != SourceType.LOCAL }
        if (cloud.isEmpty()) {
            Toast.makeText(context, "本机曲目不需要下载", Toast.LENGTH_SHORT).show()
            return@launch
        }
        var ok = 0
        var failed = 0
        cloud.forEach { track ->
            runCatching { downloader.download(track) }
                .onSuccess { ok++ }
                .onFailure { failed++ }
        }
        val message = buildString {
            append("下载完成 $ok 首")
            if (failed > 0) append(" · 失败 $failed 首")
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** 单首下载（三点菜单用）。2026-11-16 晚：交给 DownloadCenter 统一显示进度。 */
    fun download(track: Track) = downloadCenter.start(track)

    companion object {
        /** 收藏是虚拟歌单，用负数 id 表示，避免和自建歌单（自增正数）冲突。 */
        const val FAVORITES_ID: Long = -1L
        const val FAVORITES_NAME: String = "收藏歌曲"
    }
}
