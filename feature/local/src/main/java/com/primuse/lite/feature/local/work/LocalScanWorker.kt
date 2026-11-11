package com.primuse.lite.feature.local.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.primuse.lite.core.common.log.AppLog
import com.primuse.lite.feature.local.scan.LocalLibrarySyncer
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

            notifier.finished(stats)
            AppLog.i("LocalScanWorker 完成 $stats")
            Result.success(
                workDataOf(
                    KEY_SCANNED to stats.scanned,
                    KEY_WRITTEN to stats.written,
                    KEY_ELAPSED_MS to stats.elapsedMs,
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

    companion object {
        const val KEY_FULL: String = "full"
        const val KEY_SCANNED: String = "scanned"
        const val KEY_WRITTEN: String = "written"
        const val KEY_ELAPSED_MS: String = "elapsedMs"

        private const val MAX_ATTEMPTS = 3
    }
}
