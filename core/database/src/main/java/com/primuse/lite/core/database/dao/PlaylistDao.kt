package com.primuse.lite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.primuse.lite.core.database.entity.PlaylistEntity
import com.primuse.lite.core.database.entity.PlaylistTrackEntity
import com.primuse.lite.core.database.entity.TrackEntity
import com.primuse.lite.core.model.Playlist
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
         ORDER BY p.createdAt DESC
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

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

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
}
