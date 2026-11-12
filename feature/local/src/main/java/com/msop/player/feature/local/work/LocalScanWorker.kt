package com.msop.player.feature.local.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.msop.player.core.common.log.AppLog
import com.msop.player.feature.local.metadata.LocalMetadataEnricher
import com.msop.player.feature.local.scan.LocalLibrarySyncer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val full = inputData.getBoolean(KEY_FULL, false)
        val notifier = ScanNotifier(applicationContext).also { it.ensureChannel() }

        AppLog.i("LocalScanWorker 开始 full=$full attempt=$runAttemptCount")

        return try {
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

            // 只有全量扫描会删曲目，也只有它可能留下没人引用的封面
            if (full) sweepOrphanCovers()

            notifier.finished(stats, enriched)
            AppLog.i("LocalScanWorker 完成 $stats 补齐=$enriched")
            Result.success(
                workDataOf(
                    KEY_SCANNED to stats.scanned,
                    KEY_WRITTEN to stats.written,
                    KEY_ELAPSED_MS to stats.elapsedMs,
                    KEY_ENRICHED to enriched,
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

    private suspend fun enrichMetadata(): Int = try {
        enricher.enrichMissing().enriched
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppLog.e("元数据补齐失败（不影响扫描结果）", throwable)
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

        private const val MAX_ATTEMPTS = 3
    }
}
