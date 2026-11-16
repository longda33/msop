package com.msop.player.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.core.model.Track
import com.msop.player.core.model.toDurationLabel
import com.msop.player.ui.components.CircleGlassButton
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.GlassPill
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.PrimaryPill
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 专辑详情。头部给足封面和信息，下面才是曲目——
 * 避免一进详情页就是一条条没有上下文的歌名。
 *
 * P7-09：整个页面（封面头部 + 曲目列表）进同一条 LazyColumn，
 * 滚动时内容经过顶部毛玻璃标题栏后方；标题栏固定显示专辑名与艺术家。
 */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    val totalMs = tracks.sumOf { it.durationMs }
    val artist = tracks.firstNotNullOfOrNull { it.artist } ?: "未知艺术家"

    HazeTitleScaffold(
        title = albumName,
        subtitle = "专辑 · $artist",
        onBack = onBack,
        contentTopInset = HazeTitleHeight + 4.dp,
    ) { sourceModifier, topInset ->
        LazyColumn(
            modifier = sourceModifier,
            contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dim.screen, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(142.dp)
                            .shadow(26.dp, Shapes.coverGrid, spotColor = Color.Black.copy(alpha = 0.5f)),
                    ) {
                        CoverThumb(
                            coverUri = tracks.firstNotNullOfOrNull { it.coverUri },
                            size = 142.dp,
                            shape = Shapes.coverGrid,
                            seed = albumName,
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Palette.TextPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassPill(
                                label = "${tracks.size} 首",
                                color = Palette.TextSecondary,
                                background = Color.White.copy(alpha = 0.055f),
                            )
                            tracks.firstNotNullOfOrNull { it.year }?.let {
                                GlassPill(
                                    label = "$it",
                                    color = Palette.TextSecondary,
                                    background = Color.White.copy(alpha = 0.055f),
                                )
                            }
                            if (totalMs > 0) {
                                GlassPill(
                                    label = "${(totalMs / 60_000).coerceAtLeast(1)} 分钟",
                                    color = Palette.TextSecondary,
                                    background = Color.White.copy(alpha = 0.055f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dim.screen, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryPill(
                        label = "播放全部",
                        onClick = { onPlayAll(tracks, 0) },
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                    )
                    CircleGlassButton(
                        icon = PlayerIcons.Shuffle,
                        contentDescription = "随机播放",
                        onClick = { if (tracks.isNotEmpty()) onPlayAll(tracks.shuffled(), 0) },
                        size = 42.dp,
                        iconSize = 19.dp,
                        tint = Palette.BlueLight,
                        container = Palette.Blue.copy(alpha = 0.14f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dim.screen, end = Dim.screen, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "曲目",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${tracks.size} 首",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                    )
                }
            }
            itemsIndexed(items = tracks, key = { _, t -> t.id }) { index, track ->
                val current = track.id == currentTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(60.dp)
                        .clip(Shapes.cardSmall)
                        .background(
                            if (current) Palette.Blue.copy(alpha = 0.14f) else Color.Transparent,
                        )
                        .clickable { onPlayAll(tracks, index) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(30.dp)) {
                        if (current) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Palette.Blue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) PlayerIcons.Queue else PlayerIcons.Disc,
                                    contentDescription = null,
                                    tint = Palette.BlueLight,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else {
                            Text(
                                text = (track.trackNo ?: (index + 1)).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (current) Palette.BlueLight else Palette.TextPrimary,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist ?: artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        text = track.durationMs.toDurationLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                    )
                }
            }
        }
    }
}
