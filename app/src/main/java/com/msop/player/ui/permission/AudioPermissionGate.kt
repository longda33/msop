package com.msop.player.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.msop.player.feature.local.permission.AudioPermission
import com.msop.player.feature.local.permission.PermissionUiState
import com.msop.player.ui.components.AmbientBackground
import com.msop.player.ui.components.PrimaryPill
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.GeneratedArtwork
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 权限门禁：未授权时挡住整个 App，授权后才渲染 [content]。
 *
 * 视觉与主界面统一：极光背景 + 程序化封面圆盘 + 玻璃说明卡。
 * 权限逻辑保持不变，只重做呈现。
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
    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GeneratedArtwork(seed = "msop-permission", modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x44000000)),
                )
                Icon(
                    imageVector = PlayerIcons.Disc,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "msop",
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "本机优先的音乐播放器",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(Shapes.card, Palette.Surface.copy(alpha = 0.78f), elevation = 16.dp)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (state == PermissionUiState.DENIED_PERMANENTLY) {
                        "权限被永久拒绝"
                    } else {
                        "先开放音乐权限"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (state) {
                        PermissionUiState.DENIED_PERMANENTLY ->
                            "请到系统设置里手动开启「音乐和音频」权限后返回，App 会自动继续。"
                        PermissionUiState.DENIED_CAN_ASK ->
                            "没有这个权限就扫不到任何歌曲。再次点击下方按钮重新授权。"
                        else ->
                            "msop 只读取本机音频文件来建立曲库，文件不会上传，也不会被修改。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            PrimaryPill(
                label = if (state == PermissionUiState.DENIED_PERMANENTLY) "打开系统设置" else "授予权限",
                onClick = if (state == PermissionUiState.DENIED_PERMANENTLY) onOpenSettings else onRequest,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "只读访问 · 本地处理 · 不上传",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
            )
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
