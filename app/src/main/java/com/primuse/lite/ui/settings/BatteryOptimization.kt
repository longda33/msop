package com.primuse.lite.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.primuse.lite.core.common.log.AppLog

/**
 * 后台播放保活的「可引导」部分（P5-05）。
 *
 * 真正的保活主体是 `MediaSessionService` 的前台服务 + `setWakeMode`，
 * 这两项已在 P2-02 / P2-01 落地并真机验证过。国产 ROM 还会额外查电池优化
 * 白名单：没加进去时，息屏一段时间后系统可能直接冻结进程，前台服务也救不回来。
 *
 * 注意这里**不做**任何绕过系统的黑科技（无声播放、双进程互拉、常驻唤醒锁），
 * 只做标准 API 的状态检测与引导——自用 App 不该为保活牺牲续航。
 */
object BatteryOptimization {

    /** 是否已在电池优化白名单里。低于 Android 6 没有 Doze，直接视为已豁免。 */
    fun isExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .onFailure { AppLog.e("读取电池优化状态失败", it) }
            .getOrDefault(true)
    }

    /** 弹系统「允许后台运行」对话框；已豁免或系统不支持时返回 null。 */
    fun requestIgnoreIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isExempt(context)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    }

    /** 电池优化设置列表，作为部分 ROM 不弹对话框时的兜底。 */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** 应用详情页：国产 ROM 的「自启动 / 允许后台运行」开关都在这里。 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
}
