package com.msop.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.msop.player.core.model.Track
import com.msop.player.core.model.toDurationLabel
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/** 封面缩略图。没有封面时退化成深色块 + 唱片图标，不留灰洞。 */
@Composable
fun CoverThumb(
    coverUri: String?,
    size: Dp?,
    shape: Shape = Shapes.coverSmall,
    modifier: Modifier = Modifier,
    iconSize: Dp? = null,
) {
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
            .clip(shape)
            .background(Palette.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 小封面按比例缩，大封面用固定尺寸——否则 300dp 的播放页封面会顶出一个
            // 100dp 的唱片图标，喧宾夺主。
            Icon(
                imageVector = PlayerIcons.Disc,
                contentDescription = null,
                tint = Palette.TextTertiary,
                modifier = if (iconSize != null) {
                    Modifier.size(iconSize)
                } else {
                    Modifier.fillMaxSize(0.4f)
                },
            )
        }
    }
}

/** 正方形封面（宽度撑满，高度等于宽度），用于专辑网格。 */
@Composable
fun CoverSquare(
    coverUri: String?,
    shape: Shape = Shapes.coverGrid,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Palette.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = PlayerIcons.Disc,
                contentDescription = null,
                tint = Palette.TextTertiary,
                modifier = Modifier.fillMaxSize(0.32f),
            )
        }
    }
}

/**
 * 歌曲行：44dp 封面 + 标题 + 艺术家 · 专辑 + 时长。
 *
 * 整行可点，点击即播并把当前列表设为队列（由调用方决定列表范围）。
 * 当前播放行标题变蓝、左侧出现竖条、时长位置换成三根音柱。
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    onRemove: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(Dim.rowHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = Dim.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCover) {
                CoverThumb(track.coverUri, Dim.coverSmall)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Palette.BlueLight else Palette.TextPrimary,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            if (isCurrent && isPlaying) {
                PlayingBars()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = track.durationMs.toDurationLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = Palette.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("查看文件信息") },
                onClick = {
                    menuOpen = false
                    infoOpen = true
                },
            )
            onRemove?.let { remove ->
                DropdownMenuItem(
                    text = { Text("从歌单移除") },
                    onClick = {
                        menuOpen = false
                        remove()
                    },
                )
            }
        }
    }

    if (infoOpen) {
        TrackInfoDialog(track = track, onDismiss = { infoOpen = false })
    }
}

/** 当前播放的三根音柱。静态图形，不做动画——列表里滚动的动画开销不值。 */
@Composable
private fun PlayingBars() {
    Row(
        modifier = Modifier.height(11.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        listOf(6.dp, 11.dp, 8.dp).forEach { h ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Palette.BlueLight),
            )
        }
    }
}

private fun Track.subtitle(): String {
    val artist = artist?.takeIf { it.isNotBlank() }
    val album = album?.takeIf { it.isNotBlank() }
    return when {
        artist != null && album != null -> "$artist · $album"
        artist != null -> artist
        album != null -> album
        else -> "未知艺术家"
    }
}

/** 文件信息弹窗。用 Dialog 而不是新页面，够用且不打断列表滚动位置。 */
@Composable
private fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(Shapes.card)
                .background(Palette.Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            InfoLine("艺术家", track.artist ?: "未知")
            InfoLine("专辑", track.album ?: "未知")
            InfoLine("时长", track.durationMs.toDurationLabel())
            InfoLine("来源", if (track.source.name == "LOCAL") "本机" else "OpenList")
            track.mimeType?.let { InfoLine("格式", it) }
            track.sizeBytes?.let { InfoLine("大小", "%.1f MB".format(it / 1024.0 / 1024.0)) }
            track.localPath?.let { InfoLine("路径", it) }
            Text(
                text = "关闭",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.BlueLight,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(top = 6.dp, start = 12.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 空状态：曲库为空、搜索无结果都用它。 */
@Composable
fun EmptyHint(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = PlayerIcons.Disc,
            contentDescription = null,
            tint = Palette.Surface3,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(22.dp))
            it()
        }
    }
}

/** 分组小标题，用于设置页与列表分区。 */
@Composable
fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextTertiary,
        modifier = Modifier.padding(start = Dim.screen, end = Dim.screen, top = 16.dp, bottom = 8.dp),
    )
}

/** 顶部大标题行：标题 + 右侧动作图标。 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dim.screen, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = Palette.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** 48dp 触摸目标的圆形图标按钮，全 App 统一的点击热区。 */
@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Palette.TextSecondary,
) {
    Box(
        modifier = Modifier
            .size(Dim.touch)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}
