package com.primuse.lite.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.primuse.lite.feature.local.permission.AudioPermission
import com.primuse.lite.feature.local.permission.PermissionUiState

/**
 * 权限门禁：未授权时挡住整个 App，授权后才渲染 [content]。
 *
 * ⚠️ **这是功能性的临时界面，不是最终设计。**
 * UI 定稿（`docs/06` §8 的 Q1–Q4）后应替换为正式的空状态/引导页，逻辑部分可保留。
 */
@Composable
fun AudioPermissionGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var state by remember { mutableStateOf(AudioPermission.resolveState(context, activity)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        AudioPermission.markAsked(context)
        state = AudioPermission.resolveState(context, activity)
    }

    // 用户可能去系统设置里改完再回来，回前台时重新判定
    LifecycleResumeEffect(Unit) {
        state = AudioPermission.resolveState(context, activity)
        onPauseOrDispose { }
    }

    LaunchedEffect(state) {
        if (state == PermissionUiState.GRANTED) onGranted()
    }

    if (state == PermissionUiState.GRANTED) {
        content()
    } else {
        PermissionGateScreen(
            state = state,
            onRequest = { launcher.launch(AudioPermission.requestable()) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun PermissionGateScreen(
    state: PermissionUiState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "需要读取音乐权限",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when (state) {
                    PermissionUiState.DENIED_PERMANENTLY ->
                        "权限已被永久拒绝。请到系统设置里手动开启「音乐和音频」权限后返回。"
                    PermissionUiState.DENIED_CAN_ASK ->
                        "没有这个权限就扫不到任何歌曲。再次点击下方按钮重新授权。"
                    else ->
                        "Primuse Lite 需要读取本机音频文件才能建立曲库。文件只在本机处理，不会上传。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (state == PermissionUiState.DENIED_PERMANENTLY) {
                Button(onClick = onOpenSettings) { Text("打开系统设置") }
            } else {
                Button(onClick = onRequest) { Text("授予权限") }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
