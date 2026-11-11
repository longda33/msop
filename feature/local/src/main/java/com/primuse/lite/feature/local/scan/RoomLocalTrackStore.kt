package com.primuse.lite.feature.local.scan

import com.primuse.lite.core.database.dao.TrackDao
import com.primuse.lite.core.database.mapper.toEntity
import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track

/** [LocalTrackStore] 的 Room 实现，把同步逻辑与 Room 隔开。 */
class RoomLocalTrackStore(
    private val dao: TrackDao,
) : LocalTrackStore {

    override suspend fun allIds(): Set<String> = dao.allIds().toHashSet()

    override suspend fun latestModified(): Long? = dao.latestModified(SourceType.LOCAL)

    override suspend fun upsertAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dao.upsertAll(tracks.map { it.toEntity() })
    }

    override suspend fun count(): Int = dao.count()
}
