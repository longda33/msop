package com.msop.player.core.database.dao

/**
 * 批量删改的分片执行（2026-11-17 新增）。
 *
 * `DELETE ... WHERE id IN (?, ?, ...)` 单条语句能绑定的参数有上限：老系统 999，
 * Android 12+ 的 SQLite 通常是 32766。曲库安全上限设的是 2 万条，一次把整个来源的
 * 幽灵条目丢给 [TrackDao.deleteByIds] 会抛 `SQLiteException: too many SQL variables`，
 * 而这条路径在「换服务器 / 目录结构大改」时最容易被触发。
 *
 * 分片大小取 400：远低于任何版本的上限，也避免单条语句过大拖慢事务。
 */
const val SQL_SAFE_CHUNK = 400

suspend fun TrackDao.deleteByIdsChunked(ids: Collection<String>) {
    if (ids.isEmpty()) return
    ids.toList().chunked(SQL_SAFE_CHUNK).forEach { deleteByIds(it) }
}

/** 同上：封面自愈时可能一次清掉几千条 `coverUri`。 */
suspend fun TrackDao.clearCoverChunked(ids: Collection<String>) {
    if (ids.isEmpty()) return
    ids.toList().chunked(SQL_SAFE_CHUNK).forEach { clearCover(it) }
}
