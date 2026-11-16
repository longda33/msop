package com.msop.player.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.desktop.DesktopLyricsPreferences
import com.msop.player.desktop.DesktopLyricsService
import com.msop.player.ui.components.AmbientBackground
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import kotlin.math.roundToInt

/**
 * 桌面悬浮歌词设置（P7-08）。
 *
 * 开启前必须拿到 `SYSTEM_ALERT_WINDOW`。系统授权页返回时 Activity 不一定给 result，
 * 所以用 `ON_RESUME` 重新判定；用户点了「开启」再回来自动补上开关，不用再点一次。
 */
@Composable
fun DesktopLyricsScreen(
    vm: DesktopLyricsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()
    val lyricsEnabled by rememberUpdatedState(state.enabled)

    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by remember { mutableStateOf(false) }

    fun refreshPermission() {
        hasPermission = Settings.canDrawOverlays(context)
        if (hasPermission && pendingEnable) {
            pendingEnable = false
            vm.setEnabled(true)
            DesktopLyricsService.start(context)
        } else if (!hasPermission && lyricsEnabled) {
            // 权限被系统撤销：先把开关和服务都关掉，避免服务反复失败
            vm.setEnabled(false)
            DesktopLyricsService.stop(context)
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshPermission() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 上次是开启状态且权限还在：打开设置页时恢复悬浮窗（进程被杀后由用户打开 App 触发，
    // 不是系统自动重启，符合 P7-08 的生命周期约定）。
    LaunchedEffect(Unit) {
        if (state.enabled && Settings.canDrawOverlays(context)) {
            DesktopLyricsService.start(context)
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                    text = "桌面悬浮歌词",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.TextPrimary,
                )
            }

            Text(
                text = "在桌面或其他 App 上方显示当前歌词。可拖动、吸附到左右边；" +
                    "锁定后不挡点击。暂停或停止播放 10 分钟后自动隐藏。",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
            )

            if (!hasPermission) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dim.screen, vertical = 10.dp)
                        .glassSurface(Shapes.cardSmall, Palette.Surface2)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "需要「显示在其他应用上层」权限",
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Gold,
                        )
                        Text(
                            text = "系统里叫「悬浮窗」或「显示在其他应用上层」。授权后返回本页，开关会自动打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Button(
                            onClick = { overlayLauncher.launch(overlayPermissionIntent(context)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue),
                            modifier = Modifier.padding(top = 10.dp),
                        ) { Text("去授权") }
                    }
                }
            }

            SettingsCard {
                SwitchLine(
                    title = "开启桌面歌词",
                    subtitle = when {
                        state.enabled && hasPermission -> "悬浮窗已开启 · 通知栏可关闭"
                        state.enabled -> "已开启但缺少悬浮窗权限"
                        else -> "开启后桌面显示当前播放歌词"
                    },
                    checked = state.enabled && hasPermission,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Settings.canDrawOverlays(context)) {
                                vm.setEnabled(true)
                                DesktopLyricsService.start(context)
                            } else {
                                pendingEnable = true
                                overlayLauncher.launch(overlayPermissionIntent(context))
                            }
                        } else {
                            pendingEnable = false
                            vm.setEnabled(false)
                            DesktopLyricsService.stop(context)
                        }
                    },
                )
            }

            if (state.enabled) {
                Text(
                    text = "样式",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(start = Dim.screen, top = 18.dp, bottom = 6.dp),
                )
                SettingsCard {
                    SliderLine(
                        title = "字号",
                        valueLabel = "${state.fontSp}sp",
                        value = state.fontSp.toFloat(),
                        valueRange = DesktopLyricsPreferences.MIN_FONT_SP.toFloat()..
                            DesktopLyricsPreferences.MAX_FONT_SP.toFloat(),
                        steps = DesktopLyricsPreferences.MAX_FONT_SP -
                            DesktopLyricsPreferences.MIN_FONT_SP - 1,
                        onValueChange = { vm.setFontSize(it.roundToInt()) },
                    )
                    SliderLine(
                        title = "背景不透明度",
                        valueLabel = "${state.backgroundPercent}%",
                        value = state.backgroundPercent.toFloat(),
                        valueRange = 0f..DesktopLyricsPreferences.MAX_BACKGROUND.toFloat(),
                        steps = 7,
                        onValueChange = { vm.setBackground(it.roundToInt()) },
                    )
                    Text(
                        text = "文字颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DesktopLyricsPreferences.TEXT_COLORS.forEachIndexed { index, argb ->
                            val selected = state.colorIndex == index
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Palette.BlueLight else Color.White.copy(alpha = 0.20f),
                                        shape = CircleShape,
                                    )
                                    .clickable { vm.setColor(index) },
                            )
                        }
                    }
                    SwitchLine(
                        title = "单行模式",
                        subtitle = "只显示当前行，隐藏下一行",
                        checked = state.singleLine,
                        onCheckedChange = vm::setSingleLine,
                    )
                    SwitchLine(
                        title = "锁定位置",
                        subtitle = "锁定后悬浮窗不拦截桌面点击，也不能拖动",
                        checked = state.locked,
                        onCheckedChange = vm::setLocked,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun overlayPermissionIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 6.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface2)
            .padding(vertical = 6.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderLine(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.BlueLight,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
