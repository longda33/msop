package com.primuse.lite.core.database.mapper

import com.primuse.lite.core.database.entity.TrackEntity
import com.primuse.lite.core.model.Track

/**
 * 实体与领域模型互转。
 *
 * 两者字段一一对应但职责不同：
 * - [TrackEntity] 是持久化结构，字段全部可空性明确、无默认值
 * - [Track] 是 UI 与业务消费的模型，带默认值，便于构造
 */
fun TrackEntity.toTrack(): Track = Track(
    id = id,
    source = source,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNo = trackNo,
    discNo = discNo,
    year = year,
    coverUri = coverUri,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    localPath = localPath,
    lastModified = lastModified,
    addedAt = addedAt,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    source = source,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNo = trackNo,
    discNo = discNo,
    year = year,
    coverUri = coverUri,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    localPath = localPath,
    lastModified = lastModified,
    addedAt = addedAt,
)
