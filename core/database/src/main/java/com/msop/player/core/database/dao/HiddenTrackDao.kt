package com.msop.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.msop.player.core.database.entity.HiddenTrackEntity
import com.msop.player.core.database.entity.HiddenTrackRow
import kotlinx.coroutines.flow.Flow

/**
 * 「已移除歌曲」墓碑表。
 *
 * 曲目在 `tracks` 里保留（元数据/封面都还在），曲库展示查询把它们排除掉；
 * 恢复 = 删墓碑行。重新扫描是 `@Upsert`，不会碰这张表，所以不会复活。
 */
@Dao
interface HiddenTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(track: HiddenTrackEntity)

    @Query("DELETE FROM hidden_tracks WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM hidden_tracks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM hidden_tracks")
    suspend fun count(): Int

    /** 已移除列表（最近移除的在前），带上曲目原始信息以便直接展示。 */
    @Query(
        "SELECT t.*, h.removedAt AS removedAt FROM tracks t " +
            "INNER JOIN hidden_tracks h ON h.id = t.id ORDER BY h.removedAt DESC",
    )
    fun observeHidden(): Flow<List<HiddenTrackRow>>
}
