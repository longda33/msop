package com.msop.player.feature.local.metadata

import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toEntity
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track

/** [MetadataStore] 的 Room 实现。只碰 LOCAL 来源，远程曲目的元数据来自服务端。 */
class RoomMetadataStore(
    private val dao: TrackDao,
) : MetadataStore {

    override suspend fun tracksNeedingMetadata(limit: Int): List<Track> =
        dao.needingMetadata(SourceType.LOCAL, limit).map { it.toTrack() }

    override suspend fun tracksMissingCover(limit: Int): List<Track> =
        dao.missingCover(SourceType.LOCAL, limit).map { it.toTrack() }

    override suspend fun upsertAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dao.upsertAll(tracks.map { it.toEntity() })
    }

    override suspend fun countWithCover(): Int = dao.countWithCover(SourceType.LOCAL)

    override suspend fun referencedCovers(): Set<String> =
        dao.coverUris(SourceType.LOCAL).toHashSet()
}
