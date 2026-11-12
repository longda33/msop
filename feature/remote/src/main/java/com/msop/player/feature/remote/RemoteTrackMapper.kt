package com.msop.player.feature.remote

import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.FsItem
import com.msop.player.core.network.ServerUrl
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * OpenList 文件 → 统一 [Track]。纯函数，全部字段可直接单测。
 *
 * 三条关键约定：
 * 1. `uri` 存 `openlist:/路径`，**不存 raw_url**（带 tempauth、会过期）。
 * 2. `lastModified` / `addedAt` 与本地扫描保持一致，单位都是**秒**。
 * 3. 服务端不产出音频封面（`thumb` 实测为空），`coverUri` 留 null，
 *    列表页用默认封面；不做「假封面」。
 */
object RemoteTrackMapper {

    fun toTrack(item: FsItem, path: String, nowSeconds: Long): Track {
        val normalized = RemoteIdFactory.normalizePath(path)
        return Track(
            id = RemoteIdFactory.id(normalized),
            source = SourceType.OPENLIST,
            uri = ServerUrl.internalUri(normalized),
            title = RemoteIdFactory.displayTitle(item.name),
            artist = null,
            album = RemoteIdFactory.parentName(normalized),
            albumId = RemoteIdFactory.albumId(normalized),
            durationMs = 0L,
            coverUri = null,
            sizeBytes = item.size,
            mimeType = RemoteIdFactory.mimeType(item.name),
            localPath = null,
            lastModified = parseEpochSeconds(item.modified),
            addedAt = nowSeconds,
        )
    }

    /** OpenList 返回形如 `2026-11-02T08:36:47Z`；解析不了就当 0，不影响入库。 */
    fun parseEpochSeconds(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            Instant.parse(raw).epochSecond
        } catch (e: DateTimeParseException) {
            0L
        }
    }
}
