package com.msop.player.feature.local.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.TrackMerger
import com.msop.player.feature.local.metadata.LocalMetadataEnricher
import com.msop.player.feature.local.scan.LocalLibrarySyncer
import com.msop.player.feature.local.scan.SystemMediaIndexer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 后台扫描 Worker。
 *
 * 幂等性说明：同步按确定性 ID upsert，中途被杀掉重跑不会产生重复数据，
 * 所以这里允许 `Result.retry()`，失败重试是安全的。
 */
@HiltWorker
class LocalScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncer: LocalLibrarySyncer,
    private val enricher: LocalMetadataEnricher,
    private val trackDao: TrackDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val full = inputData.getBoolean(KEY_FULL, false)
        val notifier = ScanNotifier(applicationContext).also { it.ensureChannel() }

        AppLog.i("LocalScanWorker 开始 full=$full attempt=$runAttemptCount")

        return try {
            // 全量扫描前先请系统重扫 Music/Download（2026-11-14）：adb push 等落盘的
            // 文件不会自动进 MediaStore，主通道查不到就是「扫了没新歌」。增量扫描不触发，
            // 避免每次启动都惊动系统索引器。
            if (full) {
                val indexed = SystemMediaIndexer.request(applicationContext)
                AppLog.i("系统媒体目录重扫 ${if (indexed) "完成" else "未完成（继续本地同步）"}")
            }

            val stats = syncer.sync(full = full) { scanned, written ->
                setProgress(
                    workDataOf(
                        KEY_SCANNED to scanned,
                        KEY_WRITTEN to written,
                    ),
                )
                notifier.scanning(scanned, written)
            }

            // 补齐标签放在入库之后，且失败不影响扫描结果：
            // 标签只是锦上添花，曲库能列出歌才是主任务。
            val enriched = enrichMetadata()
            // 音质补齐与标签补齐分开：候选集不同，失败互不影响。
            val quality = enrichQuality()

            // 2026-11-14：全量扫描后顺带预热封面（上限 100 张）——新入库的歌
            // 不等下次冷启动就出内置封面；无内嵌图的曲目会反复命中，所以增量
            // 扫描仍然不触发（沿用 warmCovers 原设计的取舍）。
            var covers = 0
            if (full) {
                covers = try {
                    enricher.warmCovers().covers
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    AppLog.e("封面预热失败（不影响扫描结果）", throwable)
                    0
                }
            }

            // 只有全量扫描会删曲目，也只有它可能留下没人引用的封面
            if (full) sweepOrphanCovers()

            // 同一个总数同时给通知栏和设置页，避免在 UI 线程重算全库
            val total = libraryTotal()
            notifier.finished(stats, enriched, total)
            AppLog.i("LocalScanWorker 完成 $stats 补齐=$enriched 音质=$quality 封面=$covers")
            Result.success(
                workDataOf(
                    KEY_SCANNED to stats.scanned,
                    KEY_WRITTEN to stats.written,
                    KEY_ELAPSED_MS to stats.elapsedMs,
                    KEY_ENRICHED to enriched,
                    KEY_LIBRARY_TOTAL to total,
                ),
            )
        } catch (cancellation: CancellationException) {
            AppLog.w("LocalScanWorker 被取消")
            throw cancellation
        } catch (throwable: Throwable) {
            AppLog.e("LocalScanWorker 失败 attempt=$runAttemptCount", throwable)
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifier.failed(throwable.message ?: "未知错误")
                Result.failure()
            }
        }
    }

    /**
     * 扫描结束后曲库总条数（本机 + 云端），与音乐库列表用同一套去重，数字才能对上「共 N 首」。
     */
    private suspend fun libraryTotal(): Int = runCatching {
        val all = trackDao.observeAll().first().map { it.toTrack() }
        TrackMerger.merge(all).size
    }.getOrDefault(0)

    private suspend fun enrichMetadata(): Int = try {        enricher.enrichMissing().enriched
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppLog.e("元数据补齐失败（不影响扫描结果）", throwable)
        0
    }

    private suspend fun enrichQuality(): Int = try {
        enricher.enrichQuality().enriched
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppLog.e("音质补齐失败（不影响扫描结果）", throwable)
        0
    }

    private suspend fun sweepOrphanCovers() {
        try {
            enricher.sweepOrphanCovers()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLog.e("封面缓存清理失败（不影响扫描结果）", throwable)
        }
    }

    companion object {
        const val KEY_FULL: String = "full"
        const val KEY_SCANNED: String = "scanned"
        const val KEY_WRITTEN: String = "written"
        const val KEY_ELAPSED_MS: String = "elapsedMs"
        const val KEY_ENRICHED: String = "enriched"
        const val KEY_LIBRARY_TOTAL: String = "libraryTotal"

        private const val MAX_ATTEMPTS = 3
    }
}
