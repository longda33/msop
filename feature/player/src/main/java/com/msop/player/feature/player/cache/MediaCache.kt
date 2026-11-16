package com.msop.player.feature.player.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.msop.player.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程音频的边播边缓存（P3-04）。全应用只有一个 [SimpleCache] 实例。
 *
 * **为什么必须是单例**：Media3 用目录锁（`SimpleCache.isCacheFolderLocked`）防止
 * 同一目录被两个实例同时打开——那样会把缓存索引写坏。Hilt 的 `@Singleton`
 * 保证进程内唯一，且生命周期覆盖 PlaybackService 的反复创建销毁。
 *
 * **为什么不 release**：`SimpleCache.release()` 之后实例就不能再用，但服务的销毁
 * 只是「暂时没在播」，下次点歌还要复用同一个对象。索引数据库的生命周期跟进程
 * 走即可，进程被杀时系统自己会回收。
 *
 * 懒初始化：构造函数只记目录，真正打开 SQLite + 扫描缓存文件推迟到第一次
 * 远程播放或设置页查询，避免拖慢冷启动（P5-04 的约束）。
 */
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    internal val cache: SimpleCache by lazy {
        val dir = File(appContext.cacheDir, DIR_NAME)
        SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(appContext),
        ).also {
            AppLog.i("远程播放缓存已初始化 dir=${dir.absolutePath} 上限=${MAX_BYTES / MB}MB")
        }
    }

    /** 当前占用字节数。走 IO 线程调用（索引读取是同步的）。 */
    fun sizeBytes(): Long = cache.cacheSpace

    /** 缓存了多少个资源（每首远程曲目一个 key）。 */
    fun resourceCount(): Int = cache.keys.size

    /**
     * 清空远程音频缓存。
     *
     * 用 [SimpleCache.removeResource] 而不是直接 `File.deleteRecursively()`：
     * 缓存索引在 SQLite 里，光删文件会留下「索引说有、文件没了」的幽灵条目，
     * 之后命中判断会读到不存在的文件。先快照 key 再逐个 remove，避免遍历时
     * 集合被并发修改。
     *
     * 注意：正在播放的远程曲目被清掉后，当前已缓冲的数据还能放完，
     * 但后续读取会重新走网络；设置页的确认文案里已经提示了这一点。
     */
    fun clear(): Int {
        val target = cache
        val keys = target.keys.toList()
        var removed = 0
        for (key in keys) {
            runCatching { target.removeResource(key) }
                .onFailure { AppLog.w("清理远程缓存条目失败 key=$key 原因=${it.message}") }
                .onSuccess { removed++ }
        }
        AppLog.i("远程播放缓存已清空 删除=$removed")
        return removed
    }

    companion object {
        const val DIR_NAME = "media_cache"

        /** 与 `docs/02` §6/§8.4 一致：2 GB，LRU 淘汰，设置页可一键清理。 */
        const val MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

        private const val MB = 1024L * 1024L
    }
}
