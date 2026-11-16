package com.msop.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.msop.player.core.database.entity.PlaylistEntity
import com.msop.player.core.database.entity.PlaylistTrackEntity
import com.msop.player.core.database.entity.TrackEntity
import com.msop.player.core.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    /**
     * 列表页一次取回歌单、曲目数和封面。
     * 封面取第一首有封面的曲目——歌单本身不单独存图。
     */
    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.createdAt AS createdAt,
               COUNT(pt.trackId) AS trackCount,
               (SELECT t.coverUri
                  FROM playlist_tracks x
                  INNER JOIN tracks t ON t.id = x.trackId
                 WHERE x.playlistId = p.id AND t.coverUri IS NOT NULL
                 ORDER BY x.position
                 LIMIT 1) AS coverUri
          FROM playlists p
          LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
         GROUP BY p.id
         ORDER BY p.sortOrder ASC, p.createdAt DESC
        """,
    )
    abstract fun observeAll(): Flow<List<Playlist>>

    @Query(
        """
        SELECT t.*
          FROM tracks t
          INNER JOIN playlist_tracks pt ON pt.trackId = t.id
         WHERE pt.playlistId = :playlistId
         ORDER BY pt.position ASC
        """,
    )
    abstract fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT MIN(sortOrder) FROM playlists")
    abstract suspend fun minSortOrder(): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    /** P7-04 备份用：导出全部歌单（按创建时间升序，恢复后顺序稳定）。 */
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    abstract suspend fun allPlaylists(): List<PlaylistEntity>

    /** P7-04 备份用：按播放顺序取歌单内的 trackId。 */
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    abstract suspend fun trackIds(playlistId: Long): List<String>

    /** P7-04 恢复用：按名称找已有歌单，实现「同名合并」。 */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracks(items: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun removeTrack(playlistId: Long, trackId: String)

    /** 追加到队尾。已在歌单里的曲目被 IGNORE 跳过，不会重复。 */
    @Transaction
    open suspend fun addTracks(playlistId: Long, trackIds: List<String>, addedAt: Long) {
        if (trackIds.isEmpty()) return
        var position = maxPosition(playlistId) + 1
        insertTracks(
            trackIds.map { trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = position++,
                    addedAt = addedAt,
                )
            },
        )
    }

    // ------------------------------------------------------------ 拖动排序 / 移动到其它歌单

    @Query("UPDATE playlists SET sortOrder = :order WHERE id = :id")
    abstract suspend fun setSortOrder(id: Long, order: Int)

    /** 歌单列表拖动后的落库：按传入顺序重排 sortOrder。 */
    @Transaction
    open suspend fun reorderPlaylists(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun setTrackPosition(playlistId: Long, trackId: String, position: Int)

    /** 歌单内曲目拖动后的落库：按传入顺序重排 position。 */
    @Transaction
    open suspend fun reorderTracks(playlistId: Long, orderedTrackIds: List<String>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            setTrackPosition(playlistId, trackId, index)
        }
    }

    /** 某首歌已经在哪些歌单里（三点菜单里显示「已加歌单」用）。 */
    @Query("SELECT * FROM playlist_tracks")
    abstract fun observeMemberships(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    abstract fun observePlaylistIdsOf(trackId: String): Flow<List<Long>>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    abstract suspend fun playlistIdsOf(trackId: String): List<Long>

    /** 移动到其它歌单：先从原歌单摘掉，再追加到目标歌单队尾。 */
    @Transaction
    open suspend fun moveTrack(fromPlaylistId: Long, toPlaylistId: Long, trackId: String, addedAt: Long) {
        removeTrack(fromPlaylistId, trackId)
        addTracks(toPlaylistId, listOf(trackId), addedAt)
    }

    // ------------------------------------------------------------ 多选批量（2026-11-16 晚）

    /** 批量从歌单移除。 */
    @Transaction
    open suspend fun removeTracks(playlistId: Long, trackIds: List<String>) {
        trackIds.forEach { removeTrack(playlistId, it) }
    }

    /** 批量移动到其它歌单：逐首走 [moveTrack]，目标歌单按传入顺序追加在队尾。 */
    @Transaction
    open suspend fun moveTracks(
        fromPlaylistId: Long,
        toPlaylistId: Long,
        trackIds: List<String>,
        addedAt: Long,
    ) {
        trackIds.forEach { moveTrack(fromPlaylistId, toPlaylistId, it, addedAt) }
    }

    /**
     * 把一首歌从**所有**歌单里摘掉（2026-11-16 晚「移除曲库」用）。
     *
     * 移除曲库只是把曲目藏起来（`hidden_tracks` 墓碑），曲目行还在库里——若歌单
     * 关联留着，再扫一遍或恢复时它会从歌单里「复活」。所以移除曲库时一并清掉。
     */
    @Query("DELETE FROM playlist_tracks WHERE trackId = :trackId")
    abstract suspend fun removeTrackFromAllPlaylists(trackId: String)
}

