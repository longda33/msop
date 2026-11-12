package com.msop.player.feature.local.metadata

import com.msop.player.core.common.log.AppLog
import com.msop.player.core.model.Track

/** 一次补齐的结果，用于日志与通知。 */
data class EnrichStats(
    val candidates: Int = 0,
    val enriched: Int = 0,
    val covers: Int = 0,
    val failed: Int = 0,
    val elapsedMs: Long = 0L,
) {
    val isEmpty: Boolean get() = candidates == 0
}

/**
 * 本地曲目的元数据补齐。
 *
 * 两件事分开做，因为代价差一个数量级：
 * - [enrichMissing] 补标签，每次扫描后自动跑，只处理 `durationMs == 0` 的曲目
 * - [warmCovers] 预热封面，按需触发（UI 首屏、设置页手动），有上限
 *
 * 两者都是幂等的：重复跑只会命中更少的候选，不会写坏数据。
 */
class LocalMetadataEnricher(
    private val store: MetadataStore,
    private val reader: MetadataReader,
    private val covers: CoverProvider,
    private val cache: CoverCache,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * 给还没读过标签的曲目补上标签。
     *
     * @param limit 单次上限。SAF 首次扫进几千首时不让一次扫描跑太久，
     *              剩下的留到下次扫描继续——未补齐的曲目时长仍是 0，仍会被选中。
     */
    suspend fun enrichMissing(limit: Int = DEFAULT_TAG_LIMIT): EnrichStats {
        val startedAt = now()
        val candidates = store.tracksNeedingMetadata(limit)
        if (candidates.isEmpty()) {
            return EnrichStats(elapsedMs = now() - startedAt)
        }

        val updated = ArrayList<Track>(candidates.size)
        var failed = 0

        for (track in candidates) {
            val tags = reader.readTags(track.uri)
            if (tags == null) {
                failed++
                continue
            }
            val merged = track.mergedWith(tags)
            if (merged != track) updated += merged
        }

        store.upsertAll(updated)

        val stats = EnrichStats(
            candidates = candidates.size,
            enriched = updated.size,
            failed = failed,
            elapsedMs = now() - startedAt,
        )
        AppLog.i(
            "元数据补齐 候选=${stats.candidates} 更新=${stats.enriched} " +
                "失败=${stats.failed} 耗时=${stats.elapsedMs}ms",
        )
        return stats
    }

    /**
     * 预热封面。没内嵌图的曲目会被反复命中，所以**不要**放进每次扫描的固定流程，
     * 只在首屏渲染或用户手动触发时调用。
     */
    suspend fun warmCovers(limit: Int = DEFAULT_COVER_LIMIT): EnrichStats {
        val startedAt = now()
        val candidates = store.tracksMissingCover(limit)
        if (candidates.isEmpty()) {
            return EnrichStats(elapsedMs = now() - startedAt)
        }

        var found = 0
        for (track in candidates) {
            if (covers.coverUri(track) != null) found++
        }

        val stats = EnrichStats(
            candidates = candidates.size,
            covers = found,
            failed = candidates.size - found,
            elapsedMs = now() - startedAt,
        )
        AppLog.i(
            "封面预热 候选=${stats.candidates} 成功=${stats.covers} " +
                "无封面=${stats.failed} 耗时=${stats.elapsedMs}ms 库内已有封面=${store.countWithCover()}",
        )
        return stats
    }

    /**
     * 清理不再被任何曲目引用的封面文件。
     *
     * 只在全量扫描之后调用：那是唯一会删除曲目的路径，也就唯一可能产生孤儿封面。
     * 返回删掉的文件数。
     */
    suspend fun sweepOrphanCovers(): Int =
        cache.sweep(store.referencedCovers())

    companion object {
        /** 补标签单次上限。1000 首约 20 秒，超过这个量应该分几次扫描做完。 */
        const val DEFAULT_TAG_LIMIT: Int = 500

        /** 封面预热单次上限。100 张约 2–4 秒，够铺满首屏。 */
        const val DEFAULT_COVER_LIMIT: Int = 100
    }
}
