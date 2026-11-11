package com.primuse.lite.feature.local.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.primuse.lite.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描任务的唯一入口。UI 与设置页只调这里，不直接碰 WorkManager。
 *
 * 用 `enqueueUniqueWork + KEEP`：重复触发时不会叠加多个扫描任务，
 * 也不会打断正在跑的那个。
 */
@Singleton
class LocalScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(full: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<LocalScanWorker>()
            .setInputData(workDataOf(LocalScanWorker.KEY_FULL to full))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)

        AppLog.i("已排入扫描任务 full=$full")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 供 UI 观察进度；无任务时发出 null。 */
    fun observe(): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { infos -> infos.firstOrNull() }

    companion object {
        const val WORK_NAME: String = "local-library-scan"
        const val TAG: String = "scan"
    }
}
