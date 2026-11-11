package com.primuse.lite.feature.local.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.primuse.lite.feature.local.R
import com.primuse.lite.feature.local.scan.ScanStats

/**
 * 扫描进度通知。
 *
 * 用普通通知而非前台服务：扫描是幂等的（按确定性 ID upsert），被杀掉重跑没有副作用，
 * 不值得为它申请 `FOREGROUND_SERVICE_DATA_SYNC` 并承担 Android 14+ 的类型声明成本。
 */
class ScanNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scan_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.scan_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun scanning(scanned: Int, written: Int) {
        if (!canNotify()) return
        val notification = base()
            .setContentTitle(context.getString(R.string.scan_title))
            .setContentText(context.getString(R.string.scan_progress, scanned, written))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        notify(notification)
    }

    /**
     * @param enriched 本次补齐标签的曲目数。0 时用原来的文案，避免通知里出现「补齐标签 0 首」这种噪音。
     */
    fun finished(stats: ScanStats, enriched: Int = 0) {
        if (!canNotify()) return
        val seconds = stats.elapsedMs / 1000
        val text = if (enriched > 0) {
            context.getString(R.string.scan_done_enriched, stats.scanned, stats.written, enriched, seconds)
        } else {
            context.getString(R.string.scan_done, stats.scanned, stats.written, seconds)
        }
        val notification = base()
            .setContentTitle(context.getString(R.string.scan_title))
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(notification)
    }

    fun failed(message: String) {
        if (!canNotify()) return
        val notification = base()
            .setContentTitle(context.getString(R.string.scan_title))
            .setContentText(context.getString(R.string.scan_failed, message))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(notification)
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scan)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

    private fun notify(notification: android.app.Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID: String = "primuse_scan"
        const val NOTIFICATION_ID: Int = 1001
    }
}
