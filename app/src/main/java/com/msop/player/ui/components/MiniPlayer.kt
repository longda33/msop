package com.msop.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 胶囊迷你播放条。悬浮在底部导航之上，是 Msop 与国内播放器共有的形态。
 *
 * 点击展开播放页；播放键独立响应，不触发展开。
 * 底部 2dp 细线就是进度，不额外占高度。
 */
@Composable
fun MiniPlayer(
    title: String,
    subtitle: String,
    coverUri: String?,
    isPlaying: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "miniProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .height(Dim.miniHeight)
            .clip(Shapes.mini)
            .background(Palette.Surface)
            .border(1.dp, Color(0x12FFFFFF), Shapes.mini)
            .clickable(onClick = onExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(coverUri = coverUri, size = 42.dp, shape = RoundedCornerShape(11.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Palette.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        MiniProgress(
            progress = animatedProgress,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 播放条下方的进度线。独立出来，方便在 MiniPlayer 外层用 Box 叠在底部。
 */
@Composable
fun MiniProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color(0x14FFFFFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Palette.BlueLight),
        )
    }
}
