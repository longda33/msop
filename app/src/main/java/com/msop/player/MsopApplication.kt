package com.msop.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.msop.player.core.common.crash.CrashReporter
import com.msop.player.core.common.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 显式实现 [SingletonImageLoader.Factory] 而不是靠 Coil 的 ServiceLoader 自动发现。
 *
 * 原因很具体：Coil 3 的网络 fetcher 是通过 `META-INF/services` 注册的，
 * release 包的 R8 + `shrinkResources` 会把这个注册文件和实现类一起删掉，
 * 于是 debug 里远程封面正常、release 里静默变成占位图（本地 `file://` 封面不受影响，
 * 极容易误判成服务端问题）。这里直接引用 [OkHttpNetworkFetcherFactory]，
 * R8 能看到静态引用就不会删；`proguard-rules.pro` 里另有 keep 规则兜底。
 */
@HiltAndroidApp
class MsopApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()

        AppLog.i("Application onCreate 开始")
        CrashReporter.install()
        AppLog.i("崩溃捕获已安装，启动完成")
    }
}
