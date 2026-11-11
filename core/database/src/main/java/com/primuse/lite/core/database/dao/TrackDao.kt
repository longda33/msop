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

    /** 批量取已有记录，扫描入库前用来保住「补出来的字段」（封面、SAF 文件的标签）。 */
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<TrackEntity>

    /** 去重用：一次取回全部 id，扫描时在内存里比对，避免逐条查库。 */
    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<String>

    /** 只取某一来源的 id。清理幽灵条目时必须按来源过滤，否则会误删其他来源的曲目。 */
    @Query("SELECT id FROM tracks WHERE source = :source")
    suspend fun idsBySource(source: SourceType): List<String>

    /** 增量扫描的起点：某来源下最大的修改时间。 */
    @Query("SELECT MAX(lastModified) FROM tracks WHERE source = :source")
    suspend fun latestModified(source: SourceType): Long?

    /**
     * 还没读过标签的曲目（`durationMs = 0` 是「没读过」的标记，不是「时长为零」）。
     * 见 `MetadataStore.tracksNeedingMetadata` 的说明。
     */
    @Query(
        "SELECT * FROM tracks WHERE source = :source AND durationMs = 0 " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun needingMetadata(source: SourceType, limit: Int): List<TrackEntity>

    /** 还没有封面的曲目，供封面预热按序取一批。 */
    @Query(
        "SELECT * FROM tracks WHERE source = :source AND coverUri IS NULL " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun missingCover(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source AND coverUri IS NOT NULL")
    suspend fun countWithCover(source: SourceType): Int

    /** 封面缓存清理时反查引用集合。 */
    @Query("SELECT coverUri FROM tracks WHERE source = :source AND coverUri IS NOT NULL")
    suspend fun coverUris(source: SourceType): List<String>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun deleteBySource(source: SourceType)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}
