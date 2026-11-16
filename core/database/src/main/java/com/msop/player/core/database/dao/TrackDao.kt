package com.msop.player.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.msop.player.core.database.entity.TrackEntity
import com.msop.player.core.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    // 2026-11-16 晚（用户需求「移除曲库的歌曲重新扫描不要恢复」+「已移除歌曲页」）：
    // 曲库侧的四个展示查询统一排除 `hidden_tracks` 里的墓碑条目。扫描入库仍走
    // `allIds()` / `@Upsert`（不过滤），所以条目还在库里，只是不出现、也能恢复。
    @Query("SELECT * FROM tracks WHERE id NOT IN (SELECT id FROM hidden_tracks) ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    /** 按入库时间倒序的全量流（「最近」Tab 用，排序下推到 SQLite）。 */
    @Query("SELECT * FROM tracks WHERE id NOT IN (SELECT id FROM hidden_tracks) ORDER BY addedAt DESC")
    fun observeAllByAddedDesc(): Flow<List<TrackEntity>>

    /** 按来源筛选 + 入库时间倒序（「最近」Tab 在来源筛选下用）。 */
    @Query(
        "SELECT * FROM tracks WHERE source = :source AND id NOT IN (SELECT id FROM hidden_tracks) " +
            "ORDER BY addedAt DESC",
    )
    fun observeBySourceAddedDesc(source: SourceType): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE source = :source AND id NOT IN (SELECT id FROM hidden_tracks) " +
            "ORDER BY title COLLATE NOCASE ASC",
    )
    fun observeBySource(source: SourceType): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findById(id: String): TrackEntity?

    /** 批量取已有记录，扫描入库前用来保住「补出来的字段」（封面、SAF 文件的标签）。 */
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<TrackEntity>

    /** 去重用：一次取回全部 id，扫描时在内存里比对，避免逐条查库。 */
    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<String>

    /** 全量曲目（2026-11-16 晚第十三轮：移除曲库时用来找同一首歌的其它副本）。 */
    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    /** 只取某一来源的 id。清理幽灵条目时必须按来源过滤，否则会误删其他来源的曲目。 */
    @Query("SELECT id FROM tracks WHERE source = :source")
    suspend fun idsBySource(source: SourceType): List<String>

    /** 某来源的全部曲目，供同步时保留「已经探测出来的时长」等增量信息。 */
    @Query("SELECT * FROM tracks WHERE source = :source")
    suspend fun bySource(source: SourceType): List<TrackEntity>

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

    /**
     * 已有标签但还没读音质的曲目（P7-06）。
     *
     * 用 `sampleRateHz IS NULL` 判断，而不是码率：所有能解析的音频都有采样率，
     * 位深/码率则可能天然缺失（有损格式没有位深）。读过一次后采样率非空，不会反复命中。
     */
    @Query(
        "SELECT * FROM tracks WHERE source = :source AND sampleRateHz IS NULL AND durationMs > 0 " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun needingQuality(source: SourceType, limit: Int): List<TrackEntity>

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

    /** 已经有 `coverUri` 的曲目（封面自愈用：URI 存在但文件被删掉时重新提取）。 */
    @Query(
        "SELECT * FROM tracks WHERE source = :source AND coverUri IS NOT NULL " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun withCover(source: SourceType, limit: Int): List<TrackEntity>

    /** 清空封面引用，让预热重新提取（不删文件，缓存按内容哈希会自然复用）。 */
    @Query("UPDATE tracks SET coverUri = NULL WHERE id IN (:ids)")
    suspend fun clearCover(ids: List<String>)

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
