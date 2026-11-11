package com.primuse.lite

import android.app.Application
import com.primuse.lite.core.common.crash.CrashReporter
import com.primuse.lite.core.common.log.AppLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PrimuseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AppLog.i("Application onCreate 开始")
        CrashReporter.install()
        AppLog.i("崩溃捕获已安装，启动完成")
    }
}
