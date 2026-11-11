package com.primuse.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.primuse.lite.core.common.log.AppLog
import com.primuse.lite.feature.local.permission.AudioPermission
import com.primuse.lite.feature.local.work.LocalScanScheduler
import com.primuse.lite.ui.PrimuseApp
import com.primuse.lite.ui.theme.PrimuseLiteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scanScheduler: LocalScanScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        triggerLibraryScan()

        setContent {
            PrimuseLiteTheme {
                PrimuseApp()
            }
        }
    }

    /**
     * 启动时触发一次增量扫描。
     *
     * 幂等且增量，重复触发成本很低（第二次扫描基本只查一条 SQL 就结束）。
     * 没有音频权限时直接跳过——权限申请属于 P1-04，目前只在日志里说明原因。
     */
    private fun triggerLibraryScan() {
        if (AudioPermission.isGranted(this)) {
            scanScheduler.enqueue()
        } else {
            AppLog.i("未授予音频权限（${AudioPermission.required}），跳过自动扫描")
        }
    }
}
