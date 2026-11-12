package com.primuse.lite.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primuse.lite.ui.components.GroupTitle
import com.primuse.lite.ui.components.ScreenTitle
import com.primuse.lite.ui.icons.PlayerIcons
import com.primuse.lite.ui.theme.Dim
import com.primuse.lite.ui.theme.Palette
import java.util.Locale

/**
 * 设置。
 *
 * 只放**已经能用**的入口：目录管理、OpenList 服务器、重扫、封面缓存清理、
 * 均衡器、后台保活。所有行都能点出真实功能，不放点了没反应的假开关。
 */
@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(
    directories: List<String>,
    onAddDirectory: () -> Unit,
    onRemoveDirectory: (String) -> Unit,
    onRescan: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    openListVm: OpenListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val cache by vm.cache.collectAsStateWithLifecycle()
    val equalizer by vm.equalizerState.collectAsStateWithLifecycle()
    val openList by openListVm.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var equalizerOpen by remember { mutableStateOf(false) }
    var openListOpen by remember { mutableStateOf(false) }
    var directoryManagerOpen by remember { mutableStateOf(false) }
    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }

    // 从系统设置页返回时重新判定；仅靠 LaunchedEffect(Unit) 状态不会刷新
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { batteryExempt = BatteryOptimization.isExempt(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = BatteryOptimization.isExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.refreshCache() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenTitle(title = "设置")

            GroupTitle("音乐来源")
            SettingRow(
                icon = PlayerIcons.Folder,
                title = "本机目录",
                subtitle = "MediaStore 标准目录 + ${directories.size} 个自定义目录",
                value = "管理",
                onClick = { directoryManagerOpen = true },
            )
            SettingRow(
                icon = PlayerIcons.Cloud,
                title = "OpenList 服务器",
                subtitle = if (openList.configured) {
                    "${openList.syncedCount} 首远程歌曲 · 点此管理"
                } else {
                    "填地址、账号，测试连通后同步远程曲库"
                },
                value = if (openList.configured) "已连接" else "未配置",
                valueColor = if (openList.configured) Palette.BlueLight else Palette.TextTertiary,
                onClick = { openListOpen = true },
            )
            SettingRow(
                icon = Icons.Filled.Refresh,
                title = "重新扫描曲库",
                subtitle = "全量重扫会重新读取所有文件的标签与封面",
                onClick = onRescan,
            )

            GroupTitle("播放")
            SettingRow(
                icon = PlayerIcons.Disc,
                title = "均衡器",
                subtitle = "${equalizer.bands.size} 段 · 预设与自定义曲线",
                value = if (equalizer.enabled) "已启用" else "未启用",
                valueColor = if (equalizer.enabled) Palette.BlueLight else Palette.TextTertiary,
                onClick = { equalizerOpen = true },
            )
            SettingRow(
                icon = PlayerIcons.Lyrics,
                title = "歌词",
                subtitle = "同名 .lrc 优先，内嵌歌词兜底",
                value = "已启用",
                valueColor = Palette.BlueLight,
            )

            GroupTitle("后台播放")
            SettingRow(
                icon = PlayerIcons.Queue,
                title = "电池优化白名单",
                subtitle = if (batteryExempt) {
                    "已豁免，息屏后台播放不会被系统冻结"
                } else {
                    "未豁免，国产 ROM 可能息屏后杀掉播放"
                },
                value = if (batteryExempt) "已加入" else "去设置",
                valueColor = if (batteryExempt) Palette.Green else Palette.BlueLight,
                onClick = {
                    val request = BatteryOptimization.requestIgnoreIntent(context)
                    // 部分 ROM 不支持直接弹白名单对话框，降级到电池优化列表
                    val target = request ?: BatteryOptimization.appDetailsIntent(context)
                    runCatching { settingsLauncher.launch(target) }
                        .onFailure {
                            runCatching {
                                settingsLauncher.launch(BatteryOptimization.batterySettingsIntent())
                            }
                        }
                },
            )
            SettingRow(
                icon = Icons.Filled.Settings,
                title = "系统应用设置",
                subtitle = "自启动、后台运行等厂商开关在这里手动开启",
                onClick = {
                    runCatching {
                        settingsLauncher.launch(BatteryOptimization.appDetailsIntent(context))
                    }
                },
            )

            GroupTitle("存储")
            SettingRow(
                icon = PlayerIcons.Disc,
                title = "封面缓存",
                subtitle = "按内容哈希落盘；清理后播放时自动重建",
                value = if (cache.loading) {
                    "统计中…"
                } else {
                    "${cache.count} 张 · ${formatBytes(cache.bytes)}"
                },
                onClick = { confirmClear = true },
            )

            GroupTitle("关于")
            SettingRow(
                icon = PlayerIcons.Queue,
                title = "版本",
                subtitle = "自用，不上架",
                value = versionName,
            )
            SettingRow(
                icon = PlayerIcons.Lyrics,
                title = "只读声明",
                subtitle = "不修改、不上传任何音频文件",
            )

            Spacer(Modifier.height(28.dp))
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Palette.Surface,
                titleContentColor = Palette.TextPrimary,
                textContentColor = Palette.TextSecondary,
                title = { Text("清理封面缓存？") },
                text = {
                    Text(
                        "将删除 ${cache.count} 张缓存封面（${formatBytes(cache.bytes)}）。" +
                            "不会删除音频文件，下次播放时会重新生成。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClear = false
                            vm.clearCache()
                        },
                    ) {
                        Text("清理", color = Palette.BlueLight)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text("取消", color = Palette.TextSecondary)
                    }
                },
            )
        }

        if (directoryManagerOpen) {
            DirectoryManagerDialog(
                directories = directories,
                onDismiss = { directoryManagerOpen = false },
                onAdd = {
                    directoryManagerOpen = false
                    onAddDirectory()
                },
                onRemove = onRemoveDirectory,
            )
        }

        if (equalizerOpen) {
            EqualizerScreen(
                state = equalizer,
                onEnabledChange = vm::setEqualizerEnabled,
                onPreset = vm::applyEqualizerPreset,
                onBandChange = vm::setEqualizerBand,
                onReset = vm::resetEqualizer,
                onBack = { equalizerOpen = false },
            )
        }

        if (openListOpen) {
            OpenListConfigScreen(
                vm = openListVm,
                onBack = { openListOpen = false },
            )
        }
    }
}

/** 列出 / 移除已经通过 SAF 授权的自定义目录。移除后由 MainActivity 触发全量重扫。 */
@Composable
private fun DirectoryManagerDialog(
    directories: List<String>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextSecondary,
        title = { Text("本机目录") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "MediaStore 标准目录始终包含在内。下面是额外添加的目录，移除后会重新全量扫描。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(10.dp))
                if (directories.isEmpty()) {
                    Text(
                        "还没有自定义目录",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    directories.forEach { treeUri ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = PlayerIcons.Folder,
                                contentDescription = null,
                                tint = Palette.BlueLight,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = formatTreeUri(treeUri),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemove(treeUri) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "移除目录 ${formatTreeUri(treeUri)}",
                                    tint = Palette.Red,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Palette.BlueLight,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("添加目录", color = Palette.BlueLight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Palette.TextSecondary)
            }
        },
    )
}

/** tree URI 形如 content://.../tree/primary%3AMusic，取出 documentId 里可读的路径部分。 */
private fun formatTreeUri(treeUri: String): String = runCatching {
    val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
    val path = documentId.substringAfter(':', documentId).trim('/')
    path.ifBlank { "外部存储根目录" }
}.getOrDefault(treeUri)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Palette.TextTertiary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dim.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Palette.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Palette.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
