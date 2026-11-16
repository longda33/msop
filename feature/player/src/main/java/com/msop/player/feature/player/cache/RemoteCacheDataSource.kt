package com.msop.player.feature.player.cache

import android.net.Uri
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory

/**
 * 远程边播边缓存的 DataSource 链（P3-04）。
 *
 * ```
 * DefaultMediaSourceFactory
 *   └─ RemoteCacheDataSourceFactory（按 scheme 分流）
 *        ├─ 本地 content:// / file:// → DefaultDataSource（不缓存）
 *        └─ 远程 openlist:/路径      → CacheDataSource
 *                                       ├─ 读：FileDataSource（命中即离线播）
 *                                       └─ 未命中：ResolvingDataSource
 *                                            └─ DefaultDataSource(https raw_url)
 * ```
 *
 * **为什么 CacheDataSource 要放在 ResolvingDataSource 外面**（这是本任务最关键的一处）：
 * 1. 它看到的是稳定的 `openlist:/路径`，缓存 key 天然固定；如果放在里面，
 *    看到的就是每次都不同的 tempauth 直链，seek 一次多一份缓存、永不命中。
 * 2. 缓存全命中时根本不会 open upstream，连 `/api/fs/get` 都不用调，
 *    二次播放是真正的「秒开 + 零请求」。
 *
 * **为什么本地不缓存**：曲库里有几百首本机歌，把它们灌进 2 GB 配额会把远程
 * 缓存挤掉，而且读本地文件本来就没有流量和延迟问题。
 *
 * 「仅 Wi-Fi 缓存」开启且当前是蜂窝时，构造 [CacheDataSource] 时把写侧
 * [DataSink] 传 `null`：Media3 会走「只读」路径——命中缓存仍从磁盘读，
 * 未命中则直接走网络且 `releaseHoleSpan`，不会往磁盘写任何字节。
 */
internal class RemoteCacheDataSourceFactory(
    private val plainFactory: DataSource.Factory,
    private val resolver: ResolvingDataSource.Resolver,
    private val mediaCache: MediaCache,
    private val policy: MediaCachePolicy,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        RoutingDataSource(plainFactory, resolver, mediaCache, policy)
}

/**
 * 在 `open()` 时才拿得到 URI，所以分流必须做在 DataSource 上，不能做在 Factory 上。
 * 自己实现 [DataSource] 而不用 `BaseDataSource`：后者把 `addTransferListener`
 * 声明成 final 且不暴露已注册的监听器，无法转发给下游 delegate，会导致
 * ExoPlayer 的带宽统计与 AnalyticsListener 收不到任何传输事件。
 */
private class RoutingDataSource(
    private val plainFactory: DataSource.Factory,
    private val resolver: ResolvingDataSource.Resolver,
    private val mediaCache: MediaCache,
    private val policy: MediaCachePolicy,
) : DataSource {

    private val listeners = ArrayList<TransferListener>(2)
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in listeners) listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource 被重复 open，Media3 应当先 close" }
        val uri = dataSpec.uri.toString()

        val delegate: DataSource
        val targetSpec: DataSpec
        if (CacheDecision.isRemote(uri)) {
            delegate = buildCachedDataSource(policy.allowWrite())
            // key 只认稳定内部 URI，跟 raw_url 的 tempauth 完全解耦
            targetSpec = dataSpec.buildUpon().setKey(CacheDecision.stableKey(uri)).build()
        } else {
            delegate = plainFactory.createDataSource()
            targetSpec = dataSpec
        }

        listeners.forEach(delegate::addTransferListener)
        active = delegate
        return try {
            delegate.open(targetSpec)
        } catch (error: Throwable) {
            // open 失败时必须把 active 清掉，否则下一次重试会撞上「重复 open」断言
            runCatching { delegate.close() }
            active = null
            throw error
        }
    }

    /** 组装一条「缓存读 + 可选写 + 解析直链」的链。 */
    private fun buildCachedDataSource(allowWrite: Boolean): DataSource {
        val cache = mediaCache.cache
        val upstream = ResolvingDataSource.Factory(plainFactory, resolver).createDataSource()
        val cacheRead = FileDataSource.Factory().createDataSource()
        val cacheWrite: DataSink? = if (allowWrite) {
            CacheDataSink.Factory().setCache(cache).createDataSink()
        } else {
            null
        }
        return CacheDataSource(
            cache,
            upstream,
            cacheRead,
            cacheWrite,
            // 缓存索引损坏时降级为直连，宁可不缓存也不能播不了
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null,
            CacheKeyFactory.DEFAULT,
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireActive().read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        active?.close()
        active = null
    }

    private fun requireActive(): DataSource =
        active ?: error("DataSource 尚未 open")
}
