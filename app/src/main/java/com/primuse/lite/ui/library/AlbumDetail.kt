package com.primuse.lite.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.primuse.lite.core.model.Track
import com.primuse.lite.core.model.toDurationLabel
import com.primuse.lite.ui.components.CoverThumb
import com.primuse.lite.ui.components.IconAction
import com.primuse.lite.ui.icons.PlayerIcons
import com.primuse.lite.ui.theme.Dim
import com.primuse.lite.ui.theme.Palette
import com.primuse.lite.ui.theme.Shapes

/**
 * 专辑详情。结构照 MusicFree 的歌单页：
 * 头部（封面 + 名称 + 元信息）→ 播放全部 → 曲目列表。
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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
            Text(
                text = "专辑",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Dim.touch))
        }

        Row(
            modifier = Modifier.padding(horizontal = Dim.screen, vertical = 8.dp),
        ) {
            CoverThumb(
                coverUri = tracks.firstNotNullOfOrNull { it.coverUri },
                size = 118.dp,
                shape = Shapes.coverGrid,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(top = 4.dp)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(artist)
                        tracks.firstNotNullOfOrNull { it.year }?.let { append(" · $it") }
                        append(" · ${tracks.size} 首")
                        if (totalMs > 0) append(" · ${(totalMs / 60000).toInt()} 分钟")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(Shapes.pill)
                    .background(Palette.Blue)
                    .clickable { onPlayAll(tracks, 0) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            RoundAction(PlayerIcons.Shuffle, "随机播放") {
                if (tracks.isNotEmpty()) onPlayAll(tracks.shuffled(), 0)
            }
            RoundAction(Icons.Filled.FavoriteBorder, "收藏") { }
        }

        Text(
            text = "曲目",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(start = Dim.screen, top = 6.dp, bottom = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(items = tracks, key = { _, t -> t.id }) { index, track ->
                val current = track.id == currentTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onPlayAll(tracks, index) }
                        .padding(horizontal = Dim.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(24.dp)) {
                        if (current && isPlaying) {
                            Icon(
                                imageVector = PlayerIcons.Queue,
                                contentDescription = null,
                                tint = Palette.BlueLight,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                text = (track.trackNo ?: (index + 1)).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (current) Palette.BlueLight else Palette.TextPrimary,
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

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Palette.TextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}
