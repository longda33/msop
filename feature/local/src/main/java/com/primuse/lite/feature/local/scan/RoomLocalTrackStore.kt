package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.database.dao.TrackDao
import com.primuse.lite.core.database.mapper.toEntity
import com.primuse.lite.core.database.mapper.toTrack
import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track

/** [LocalTrackStore] 的 Room 实现，把同步逻辑与 Room 隔开。 */
class RoomLocalTrackStore(
    private val dao: TrackDao,
) : LocalTrackStore {

    /**
     * 只返回 LOCAL 来源的 id。
     *
     * 用 `allIds()` 会把远程曲目也带进来，一旦全量扫描后做差集清理，
     * 远程曲目会被当成「文件已删除」全部抹掉。
     */
    override suspend fun allIds(): Set<String> =
        dao.idsBySource(SourceType.LOCAL).toHashSet()

    override suspend fun latestModified(): Long? = dao.latestModified(SourceType.LOCAL)

    /**
     * 入库前先与库里已有记录合并。
     *
     * 扫描器看不到封面缓存路径，也读不出 SAF 文件的标签；整行 upsert 会把这些字段
     * 抹成 null。合并逻辑见 [mergedWithExisting]。
     *
     * 每页最多 500 条，`IN (:ids)` 的参数个数不会触到 SQLite 的绑定变量上限。
     */
    override suspend fun upsertAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val existing = dao.findByIds(tracks.map { it.id }).associateBy { it.id }
        dao.upsertAll(
            tracks.map { track ->
                track.mergedWithExisting(existing[track.id]?.toTrack()).toEntity()
            },
        )
    }

    override suspend fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    override suspend fun count(): Int = dao.count()
}
