package com.msop.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.core.datastore.ServerStatus
import com.msop.player.core.datastore.ServerStatusInfo
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 三点菜单里的一项。 */
internal data class ServerMenuAction(
    val label: String,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 服务器列表的一行（OpenList / WebDAV 共用）。
 *
 * 视觉上是设置页同款玻璃卡：标题 + 地址 + 状态 + 同步计数，右侧一个三点菜单。
 * 点卡片主体 = 编辑；点三点才弹菜单，两者互不抢点击（`AGENTS.md` 的老坑：
 * 弹窗必须贴着三点，别挂在整行上）。
 */
@Composable
internal fun ServerCard(
    title: String,
    urlText: String,
    status: ServerStatusInfo,
    enabled: Boolean,
    syncedCount: Int,
    lastSyncAt: Long,
    busy: Boolean,
    onOpen: () -> Unit,
    actions: List<ServerMenuAction>,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 5.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface2, elevation = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = !busy, onClick = onOpen),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (enabled) {
                        Spacer(Modifier.width(8.dp))
                        ActiveBadge()
                    }
                }
                Text(
                    text = urlText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(status.status)
                    Text(
                        text = statusLine(status),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(status.status),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = syncLine(syncedCount, lastSyncAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = Palette.TextSecondary,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Palette.Surface,
                ) {
                    actions.filter { it.enabled }.forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = action.label,
                                    color = if (action.danger) Palette.Red else Palette.TextPrimary,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveBadge() {
    Text(
        text = "启用中",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.BlueLight,
        modifier = Modifier
            .background(Palette.Blue.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatusDot(status: ServerStatus) {
    Box(
        Modifier
            .size(7.dp)
            .background(statusColor(status).copy(alpha = 0.85f), RoundedCornerShape(50)),
    )
}

@Composable
internal fun statusColor(status: ServerStatus): Color = when (status) {
    ServerStatus.OK -> Palette.Green
    ServerStatus.FAIL -> Palette.Red
    ServerStatus.UNTESTED -> Palette.TextTertiary
}

/** 列表里那行状态文案：没测过 / 成功 / 失败（带原因）。 */
internal fun statusLine(info: ServerStatusInfo): String = when (info.status) {
    ServerStatus.OK -> "连接正常 · ${info.message}".trim().trimEnd('·').trim()
    ServerStatus.FAIL -> "上次失败：${info.message}"
    ServerStatus.UNTESTED -> "未测试"
}

/** 列表里那行同步计数。 */
internal fun syncLine(count: Int, atMillis: Long): String {
    if (count <= 0 && atMillis <= 0L) return "尚未同步"
    val time = if (atMillis <= 0L) {
        "未记录时间"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(atMillis))
    }
    return "曲库 $count 首 · 上次同步 $time"
}
