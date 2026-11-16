package com.msop.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
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

    /**
     * P5-04：显式配内存缓存，而不是吃 Coil 的默认值。
     *
     * 曲库列表一屏十几张封面，5 万首库来回滚时同一张专辑的封面会被反复请求。
     * 默认缓存按可用内存的一个比例算，在大库 + 大图（远程 JPEG 129KB）场景下
     * 命中率不够，滚动时会看到封面「闪一下才出来」。
     *
     * 这里给固定上限：占可用堆的 20%，且不超过 96MB。上限封顶是为了在低端机
     * （堆本身只有 192MB 左右）上不至于把播放器挤到 OOM——封面缓存永远不该
     * 比正在解码的音频缓冲更值钱。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val maxHeap = Runtime.getRuntime().maxMemory()
        val cacheBytes = minOf((maxHeap * 0.20).toLong(), MAX_MEMORY_CACHE_BYTES)
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(cacheBytes)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        AppLog.i("Application onCreate 开始")
        CrashReporter.install()
        AppLog.i("崩溃捕获已安装，启动完成")
    }

    private companion object {
        /** 封面内存缓存硬上限。见 [newImageLoader] 的说明。 */
        const val MAX_MEMORY_CACHE_BYTES = 96L * 1024 * 1024
    }
}
