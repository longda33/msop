package com.msop.player.feature.local.scan

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.msop.player.core.common.log.AppLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 请求系统 MediaProvider 重扫标准媒体目录（P 修复：2026-11-14）。
 *
 * 为什么需要它：本地扫描的主通道是 MediaStore，而 MediaStore 只包含**已被系统索引**的
 * 文件。adb push、部分下载工具或系统索引器漏扫落盘的文件不会出现在 MediaStore 里，
 * 表现就是「曲库扫描没用」——App 侧扫描日志显示 `扫描=7 写入=0`，一切正常但查不到
 * 新文件。让用户手动跑
 * `adb shell content call --uri content://media --method scan_volume --arg external_primary`
 * 不现实，所以全量扫描前先通过公开 API [MediaScannerConnection.scanFile] 请系统
 * 重扫一遍标准目录，再走 MediaStore 查询。
 *
 * 约束与兜底：
 * - 只扫公共音乐目录（Music / Download），不枚举任意路径——那需要
 *   MANAGE_EXTERNAL_STORAGE，与 `docs/08` §3.2 的独立重写原则冲突，不采用；
 * - 传入的是目录路径，ModernMediaScanner 按需求扫描（scan on demand）；
 * - 绑定失败 / 服务无响应时回调不会来，等待带 [TIMEOUT_SEC] 超时，超时后照常继续
 *   本地同步——最坏情况与修复前行为一致（下次增量扫描按 date_modified 兜底）；
 * - 回调按 path 逐个触发，CountDownLatch 全部到齐或超时才返回。
 */
object SystemMediaIndexer {

    /** 音乐最常见落点；不要扩大到整个根目录，Download 里非音频很多，扫起来慢。 */
    private val SCAN_DIR_TYPES = listOf(
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_DOWNLOADS,
    )

    private const val TIMEOUT_SEC = 15L

    /**
     * @return true = 所有目录都等到系统扫描回调；false = 超时或没有可扫目录。
     *         无论返回什么，调用方都应继续本地同步。
     */
    fun request(context: Context): Boolean {
        val paths = SCAN_DIR_TYPES
            .mapNotNull { type ->
                runCatching {
                    Environment.getExternalStoragePublicDirectory(type)?.absolutePath
                }.getOrNull()
            }
            .distinct()
        if (paths.isEmpty()) {
            AppLog.w("系统媒体目录重扫：找不到标准目录路径，跳过")
            return false
        }

        val latch = CountDownLatch(paths.size)
        // MediaScannerConnection 的回调依赖其内部 Handler，绑到主线程 Looper 上最稳。
        Handler(Looper.getMainLooper()).post {
            MediaScannerConnection.scanFile(
                context,
                paths.toTypedArray(),
                null,
            ) { path, uri ->
                AppLog.i("系统媒体目录重扫回调 path=$path uri=$uri")
                latch.countDown()
            }
        }

        return try {
            val done = latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!done) AppLog.w("系统媒体目录重扫超时（${TIMEOUT_SEC}s），继续本地同步")
            done
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            AppLog.w("系统媒体目录重扫等待被中断，继续本地同步")
            false
        }
    }
}
