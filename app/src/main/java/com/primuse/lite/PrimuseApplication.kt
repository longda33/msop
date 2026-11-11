package com.primuse.lite

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.primuse.lite.core.common.crash.CrashReporter
import com.primuse.lite.core.common.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrimuseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        AppLog.i("Application onCreate 开始")
        CrashReporter.install()
        AppLog.i("崩溃捕获已安装，启动完成")
    }
}
