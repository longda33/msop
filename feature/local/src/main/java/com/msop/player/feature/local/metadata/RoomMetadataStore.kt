package com.msop.player.feature.local.metadata

import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.dao.clearCoverChunked
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

    override suspend fun tracksNeedingQuality(limit: Int): List<Track> =
        dao.needingQuality(SourceType.LOCAL, limit).map { it.toTrack() }

    override suspend fun tracksMissingCover(limit: Int): List<Track> =
        dao.missingCover(SourceType.LOCAL, limit).map { it.toTrack() }

    override suspend fun upsertAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dao.upsertAll(tracks.map { it.toEntity() })
    }

    override suspend fun countWithCover(): Int = dao.countWithCover(SourceType.LOCAL)

    /**
     * 反查「被引用的封面文件」时**不能只查 LOCAL**（2026-11-18）：
     * 云端曲目的封面由 `RemoteCoverExtractor` 写进同一个 `files/covers` 目录，
     * 只按本机引用做差集会把云端独有的封面文件全删掉。
     */
    override suspend fun referencedCovers(): Set<String> =
        dao.coverUrisAll().toHashSet()

    override suspend fun clearAllCovers(): Int =
        dao.clearAllCovers(SourceType.LOCAL)

    override suspend fun tracksWithCover(limit: Int): List<Track> =
        dao.withCover(SourceType.LOCAL, limit).map { it.toTrack() }

    override suspend fun clearCover(ids: List<String>) {
        dao.clearCoverChunked(ids)
    }
}
