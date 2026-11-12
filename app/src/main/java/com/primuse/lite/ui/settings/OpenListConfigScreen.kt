package com.primuse.lite.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primuse.lite.core.network.ServerUrl
import com.primuse.lite.ui.components.IconAction
import com.primuse.lite.ui.theme.Dim
import com.primuse.lite.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OpenList 服务器配置页（P3-07）。全屏浮层，挂在设置页上方。
 *
 * 只填三样东西：地址、账号、密码。地址在保存时由 [ServerUrl.normalize] 规范化
 * （补 https、去尾斜杠、剥误粘页面名），落库后所有请求走 HttpUrl 拼路径。
 */
@Composable
fun OpenListConfigScreen(
    vm: OpenListViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isPlainHttp = remember(state.url) {
        runCatching { ServerUrl.normalize(state.url).isPlainHttp }.getOrDefault(false)
    }
    // true = 确认后执行「保存并同步」，false = 确认后执行「测试连接」
    var pendingHttpAction by remember { mutableStateOf<Boolean?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = Dim.screen, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Text(
                    text = "OpenList 服务器",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.TextPrimary,
                )
            }

            Text(
                text = "连接公网 OpenList，把远程音频同步进曲库，与本机歌曲混排播放。" +
                    "本机部署若未启用 Subsonic，会自动走 OpenList 原生接口。",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dim.screen, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Surface2)
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = if (state.configured) "已配置" else "尚未配置",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.configured) Palette.Green else Palette.TextSecondary,
                    )
                    Text(
                        text = "曲库中 ${state.syncedCount} 首远程歌曲 · ${formatSyncTime(state.lastSyncAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = if (state.appKey.isNotBlank()) {
                            "Subsonic 元数据已启用：真实封面、艺术家、专辑"
                        } else {
                            "未填应用密钥：无封面，专辑取目录名"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.appKey.isNotBlank()) Palette.BlueLight else Palette.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            ConfigField(
                label = "服务器地址",
                value = state.url,
                hint = "music.example.com 或 https://example.com/openlist",
                onValueChange = vm::onUrlChange,
            )
            ConfigField(
                label = "账号",
                value = state.username,
                hint = "OpenList 用户名",
                onValueChange = vm::onUsernameChange,
            )
            ConfigField(
                label = "密码",
                value = state.password,
                hint = "OpenList 密码",
                onValueChange = vm::onPasswordChange,
                isPassword = true,
            )
            ConfigField(
                label = "Subsonic 应用密钥（可选）",
                value = state.appKey,
                hint = "olmp_ 开头；填了才有真实封面与艺术家/专辑",
                onValueChange = vm::onAppKeyChange,
                isPassword = true,
            )

            if (isPlainHttp) {
                Text(
                    text = "⚠ 当前是 http:// 明文地址，App 默认禁止明文连接，建议改用 https://",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Red,
                    modifier = Modifier.padding(horizontal = Dim.screen, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dim.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (isPlainHttp) pendingHttpAction = false
                        else vm.testConnection()
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.Surface3,
                        contentColor = Palette.TextPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("测试连接") }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (isPlainHttp) pendingHttpAction = true
                        else vm.saveAndSync()
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue),
                    modifier = Modifier.weight(1f),
                ) { Text("保存并同步") }
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.padding(horizontal = Dim.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Palette.BlueLight,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在连接服务器并同步曲库…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                    )
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isError) Palette.Red else Palette.Green,
                    modifier = Modifier.padding(horizontal = Dim.screen, vertical = 8.dp),
                )
            }

            if (state.configured) {
                TextButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.padding(horizontal = Dim.screen),
                ) {
                    Text("清除配置与远程曲目", color = Palette.TextTertiary)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (pendingHttpAction != null) {
        AlertDialog(
            onDismissRequest = { pendingHttpAction = null },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("确认使用明文 HTTP？") },
            text = {
                Text(
                    "公网明文传输会暴露账号密码，且 Android 默认禁止明文连接，很可能连不上。" +
                        "请优先改成 https://。自用局域网调试才继续。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sync = pendingHttpAction == true
                        pendingHttpAction = null
                        if (sync) vm.saveAndSync(allowPlainHttp = true)
                        else vm.testConnection(allowPlainHttp = true)
                    },
                ) { Text("仍然继续", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttpAction = null }) {
                    Text("改用 HTTPS", color = Palette.BlueLight)
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("清除服务器配置？") },
            text = { Text("会同时从曲库移除已同步的远程歌曲，本机歌曲不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        vm.clearConfig()
                    },
                ) { Text("清除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消", color = Palette.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        textStyle = TextStyle(color = Palette.TextPrimary),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Uri,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 6.dp),
    )
}

private fun formatSyncTime(atMillis: Long): String = if (atMillis <= 0L) {
    "尚未同步"
} else {
    "上次 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(atMillis))
}
