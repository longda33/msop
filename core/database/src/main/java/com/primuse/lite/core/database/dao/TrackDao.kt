package com.primuse.lite.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.primuse.lite.core.database.entity.TrackEntity
import com.primuse.lite.core.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title COLLATE NOCASE ASC")
    fun observeBySource(source: SourceType): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findById(id: String): TrackEntity?

    /** 去重用：一次取回全部 id，扫描时在内存里比对，避免逐条查库。 */
    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<String>

    /** 增量扫描的起点：某来源下最大的修改时间。 */
    @Query("SELECT MAX(lastModified) FROM tracks WHERE source = :source")
    suspend fun latestModified(source: SourceType): Long?

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun deleteBySource(source: SourceType)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}
