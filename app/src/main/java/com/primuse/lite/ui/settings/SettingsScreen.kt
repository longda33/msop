package com.primuse.lite.ui.settings

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
 * 只放**已经能用**的入口：目录管理、重扫、封面缓存清理。
 * OpenList 与均衡器还没做，这里明确写「未接入」而不是放一个点了没反应的开关——
 * 假开关比缺功能更让人困惑。
 */
@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(
    directoryCount: Int,
    onAddDirectory: () -> Unit,
    onRescan: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val cache by vm.cache.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshCache() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(title = "设置")

        GroupTitle("音乐来源")
        SettingRow(
            icon = PlayerIcons.Folder,
            title = "本机目录",
            subtitle = "MediaStore 标准目录 + $directoryCount 个自定义目录",
            value = "添加",
            onClick = onAddDirectory,
        )
        SettingRow(
            icon = PlayerIcons.Cloud,
            title = "OpenList 服务器",
            subtitle = "填地址、账号，测试连通后同步远程曲库",
            value = "未接入",
            valueColor = Palette.TextTertiary,
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
            subtitle = "10 段，关闭 audio offload 后生效",
            value = "未接入",
        )
        SettingRow(
            icon = PlayerIcons.Lyrics,
            title = "歌词",
            subtitle = "同名 .lrc 优先，内嵌歌词兜底",
            value = "已启用",
            valueColor = Palette.BlueLight,
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
}

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
