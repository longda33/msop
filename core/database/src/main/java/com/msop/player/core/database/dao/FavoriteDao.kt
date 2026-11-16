package com.msop.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.msop.player.core.database.entity.FavoriteEntity
import com.msop.player.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * 「收藏歌曲」虚拟歌单的数据访问（2026-11-16 用户需求：歌单列表里收藏条目置顶）。
 *
 * 只做收藏关系，不碰 `playlists` 表；曲目被移除曲库时外键 CASCADE 自动带走。
 */
@Dao
abstract class FavoriteDao {

    @Query(
        """
        SELECT t.*
          FROM tracks t
          INNER JOIN favorites f ON f.trackId = t.id
         ORDER BY f.addedAt DESC
        """,
    )
    abstract fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM favorites")
    abstract fun observeIds(): Flow<List<String>>

    @Query("SELECT trackId FROM favorites")
    abstract suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    abstract suspend fun remove(trackId: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE trackId = :trackId")
    abstract suspend fun contains(trackId: String): Int

    @Transaction
    open suspend fun toggle(trackId: String, now: Long): Boolean {
        return if (contains(trackId) > 0) {
            remove(trackId)
            false
        } else {
            insert(FavoriteEntity(trackId = trackId, addedAt = now))
            true
        }
    }
}
