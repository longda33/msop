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
 * 三件事分开做，因为代价差一个数量级：
 * - [enrichMissing] 补标签，每次扫描后自动跑，只处理 `durationMs == 0` 的曲目
 * - [enrichQuality] 补码率/采样率/位深，只处理 `sampleRateHz IS NULL` 的曲目
 * - [warmCovers] 预热封面，按需触发（UI 首屏、设置页手动），有上限
 *
 * 三者都是幂等的：重复跑只会命中更少的候选，不会写坏数据。
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
     * 给已有标签的曲目补音质（P7-06）。
     *
     * MediaStore 只给时长/大小，码率可以从两者估算，但采样率和位深必须读文件。
     * 与补标签分开是因为候选集不同：补标签看 `durationMs == 0`，这里看 `sampleRateHz IS NULL`。
     * 读取失败的下次扫描还会被选中，正是想要的重试行为。
     */
    suspend fun enrichQuality(limit: Int = DEFAULT_QUALITY_LIMIT): EnrichStats {
        val startedAt = now()
        val candidates = store.tracksNeedingQuality(limit)
        if (candidates.isEmpty()) {
            return EnrichStats(elapsedMs = now() - startedAt)
        }

        val updated = ArrayList<Track>(candidates.size)
        var failed = 0
        for (track in candidates) {
            val quality = reader.readQuality(track.uri)
            if (quality == null || quality.isEmpty) {
                failed++
                continue
            }
            // 码率已由扫描按文件大小/时长算过就保留；采样率/位深以文件真实值为准补空。
            val merged = track.copy(
                bitrateKbps = track.bitrateKbps ?: quality.bitrateKbps,
                sampleRateHz = track.sampleRateHz ?: quality.sampleRateHz,
                bitDepth = track.bitDepth ?: quality.bitDepth,
            )
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
            "音质补齐 候选=${stats.candidates} 更新=${stats.enriched} " +
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
     * 封面自愈（2026-11-16 晚用户报「封面怎么是白色」）。
     *
     * `coverUri` 指向的缓存文件可能已经不在了（重装、清缓存、系统清理应用数据），
     * 而 [tracksMissingCover] 只挑 `coverUri IS NULL` 的条目，所以这些「有 URI 没文件」
     * 的歌永远白着。这里把它们查出来 → 清空 `coverUri` → 立刻跑一次预热重新提取。
     *
     * 只处理前 [DEFAULT_COVER_LIMIT] 首，避免大库首次启动做几千次文件存在性判断；
     * 剩下的下次启动继续。
     */
    suspend fun repairBrokenCovers(limit: Int = DEFAULT_COVER_LIMIT): Int {
        val candidates = store.tracksWithCover(limit)
        if (candidates.isEmpty()) return 0
        val broken = candidates.filter { cache.existing(it.coverUri) == null }
        if (broken.isEmpty()) return 0
        store.clearCover(broken.map { it.id })
        AppLog.i("封面自愈 失效=${broken.size}/${candidates.size}")
        // 清完立刻补：不然要等下一次首屏预热才看得见
        return warmCovers(limit).covers
    }

    /**
     * 全库重建封面（2026-11-18 用户报「清除封面缓存后封面不显示，要重扫+重启才回来」）。
     *
     * 清缓存只删了文件、没动数据库，`coverUri` 于是全部指向不存在的文件；而
     * [repairBrokenCovers] 只处理前 [DEFAULT_COVER_LIMIT] 首，且只在冷启动时跑一次。
     * 这里先清空全部引用，再分批预热直到补不出新的为止（没有内嵌图的曲目会一直命中，
     * 所以用「这一轮一张都没补到就收手」当终止条件）。
     */
    suspend fun rebuildCovers(batch: Int = DEFAULT_COVER_LIMIT): Int {
        val startedAt = now()
        val cleared = store.clearAllCovers()
        var restored = 0
        var rounds = 0
        while (rounds < MAX_REBUILD_ROUNDS) {
            val stats = warmCovers(batch)
            restored += stats.covers
            rounds++
            if (stats.candidates < batch || stats.covers == 0) break
        }
        AppLog.i("封面重建 清空引用=$cleared 重补=$restored 轮次=$rounds 耗时=${now() - startedAt}ms")
        return restored
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

        /** 音质补齐单次上限。读 MediaExtractor 比读标签快，500 首约几秒。 */
        const val DEFAULT_QUALITY_LIMIT: Int = 500

        /** 封面重建最多跑多少批（防御：全是无内嵌图的曲目时避免死循环）。 */
        private const val MAX_REBUILD_ROUNDS: Int = 30
    }
}
