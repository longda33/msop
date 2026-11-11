package com.primuse.lite.feature.local.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 读取本机音频所需权限。
 *
 * - Android 13（API 33）及以上：`READ_MEDIA_AUDIO`
 * - 12 及以下：`READ_EXTERNAL_STORAGE`
 *
 * SAF 自定义目录**不需要**这些权限（走 URI 授权），所以只扫媒体库时才需要申请。
 */
object AudioPermission {

    val required: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, required) == PackageManager.PERMISSION_GRANTED
}
