package com.msop.player.feature.local.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限界面的四种状态。
 *
 * 区分 [DENIED_CAN_ASK] 与 [DENIED_PERMANENTLY] 是关键：
 * 前者还能弹系统对话框，后者系统不会再弹，只能引导用户去设置页。
 */
enum class PermissionUiState {
    UNKNOWN,
    GRANTED,
    DENIED_CAN_ASK,
    DENIED_PERMANENTLY,
}

/**
 * 读取本机音频所需权限。
 *
 * - Android 13（API 33）及以上：`READ_MEDIA_AUDIO`
 * - 12 及以下：`READ_EXTERNAL_STORAGE`
 *
 * SAF 自定义目录**不需要**这些权限（走 URI 授权），所以只扫媒体库时才需要申请。
 */
object AudioPermission {

    private const val PREFS_NAME = "msop_permission"
    private const val KEY_ASKED = "audio_permission_asked"

    val required: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * 一次请求的权限集合。
     *
     * 顺带申请 `POST_NOTIFICATIONS`：13+ 没有它扫描进度通知不会显示，
     * 与其分两次打扰用户，不如一次问完。
     */
    fun requestable(): Array<String> = buildList {
        add(required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, required) == PackageManager.PERMISSION_GRANTED

    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, required)

    fun hasAskedBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED, true)
            .apply()
    }

    fun resolveState(context: Context, activity: Activity?): PermissionUiState =
        resolvePermissionState(
            granted = isGranted(context),
            shouldShowRationale = activity?.let { shouldShowRationale(it) } ?: false,
            askedBefore = hasAskedBefore(context),
        )
}

/**
 * 纯函数版的状态判定，便于单测。
 *
 * Android 的规则：拒绝一次后 `shouldShowRequestPermissionRationale` 为 true（还能再问）；
 * 勾选「不再询问」或拒绝两次后变为 false，此时系统不会再弹窗。
 */
internal fun resolvePermissionState(
    granted: Boolean,
    shouldShowRationale: Boolean,
    askedBefore: Boolean,
): PermissionUiState = when {
    granted -> PermissionUiState.GRANTED
    shouldShowRationale -> PermissionUiState.DENIED_CAN_ASK
    askedBefore -> PermissionUiState.DENIED_PERMANENTLY
    else -> PermissionUiState.UNKNOWN
}
