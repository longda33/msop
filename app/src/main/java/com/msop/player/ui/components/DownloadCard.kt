package com.msop.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.core.network.DownloadCenter
import com.msop.player.ui.theme.Palette
import java.util.Locale

/**
 * 下载进度卡片（2026-11-16 晚用户需求「点击下载 反馈没有 进度也没有」）。
 *
 * 贴在迷你条上方：点「下载歌曲」立刻出现，显示「歌名 · 已下载 12.3MB / 21.6MB · 57%」，
 * 完成变「已保存到 …」并停留 4 秒，失败变红字并停留 6 秒（停留时长由 [DownloadCenter]
 * 控制，UI 只负责画）。服务器没给 `Content-Length` 时用不确定进度条 + 已下载字节数。
 */
@Composable
fun DownloadTaskCard(
    task: DownloadCenter.Task,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (task.state) {
        DownloadCenter.State.RUNNING -> Palette.BlueLight
        DownloadCenter.State.DONE -> Palette.Green
        DownloadCenter.State.FAILED -> Palette.Red
    }
    val dismissInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface2.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                when (task.state) {
                    DownloadCenter.State.RUNNING -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                    DownloadCenter.State.DONE -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp),
                    )
                    DownloadCenter.State.FAILED -> Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(task.title)
                        task.artist?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.state == DownloadCenter.State.FAILED) Palette.Red else Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "收起下载提示",
                tint = Palette.TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        if (task.state == DownloadCenter.State.RUNNING) {
            Spacer(Modifier.height(8.dp))
            val progress = task.progress
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Line),
            ) {
                if (progress != null) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(accent),
                    )
                } else {
                    // 服务器没给长度：不确定进度（来回滑动的一段），仍然给出「在动」的反馈
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

private fun statusText(task: DownloadCenter.Task): String = when (task.state) {
    DownloadCenter.State.RUNNING -> buildString {
        append("正在下载 ")
        append(humanBytes(task.bytes))
        task.total?.let { total ->
            append(" / ")
            append(humanBytes(total))
            append(" · ")
            append((task.bytes.toFloat() / total * 100f).coerceIn(0f, 100f).toInt())
            append("%")
        }
    }
    DownloadCenter.State.DONE -> "已保存到 ${task.savedPath ?: "下载目录"}"
    DownloadCenter.State.FAILED -> task.error ?: "下载失败"
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1fMB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.0fKB", bytes / 1024f)
    else -> "${bytes}B"
}
