package com.msop.player.feature.local.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.msop.player.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描任务的唯一入口。UI 与设置页只调这里，不直接碰 WorkManager。
 *
 * 冲突策略分两种：
 * - 自动触发的增量扫描用 `KEEP`：重复触发时不叠加，也不打断正在跑的那个
 * - 用户手动触发的全量扫描用 `REPLACE`：用户明确要求重扫，若此时正好有增量在跑，
 *   用 `KEEP` 会把这次请求**静默丢弃**，用户点了按钮却什么都没发生
 *
 * 打断正在跑的扫描是安全的：同步按确定性 ID upsert，被杀掉重跑没有副作用。
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
            .enqueueUniqueWork(WORK_NAME, policyFor(full), request)

        AppLog.i("已排入扫描任务 full=$full")
    }

    private fun policyFor(full: Boolean): ExistingWorkPolicy =
        if (full) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

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
