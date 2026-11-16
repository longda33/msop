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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Track
import com.msop.player.core.model.toDurationLabel
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.playlist.PlaylistViewModel
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 「已移除歌曲」页（2026-11-16 晚用户需求：曲库右上角三点菜单增加已移除歌曲，可恢复）。
 *
 * 数据来自 `hidden_tracks` 墓碑表：被「移除曲库」的歌只是从曲库列表里隐藏，
 * 曲目行仍在 `tracks`（歌名/歌手/封面都在），所以这里能直接列出、点一下就能恢复，
 * 不需要重新扫描。恢复 = 删墓碑行。
 */
@Composable
fun RemovedTracksScreen(
    playlistVm: PlaylistViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val removed by playlistVm.removedTracks.collectAsStateWithLifecycle()

    HazeTitleScaffold(
        title = "已移除歌曲",
        subtitle = if (removed.isEmpty()) "没有移除过歌曲" else "${removed.size} 首 · 点「恢复」放回曲库",
        onBack = onBack,
        contentTopInset = HazeTitleHeight + 4.dp,
    ) { sourceModifier, topInset ->
        if (removed.isEmpty()) {
            EmptyHint(
                title = "没有已移除的歌曲",
                description = "在曲目三点菜单里点「移除曲库」，歌曲会先到这里；想放回曲库点一下就回来。",
            )
        } else {
            LazyColumn(
                modifier = sourceModifier,
                contentPadding = PaddingValues(
                    top = topInset + 4.dp,
                    bottom = LocalHazeBottomInset.current + 18.dp,
                ),
            ) {
                items(items = removed, key = { it.id }) { track ->
                    RemovedTrackRow(
                        track = track,
                        onRestore = { playlistVm.restoreToLibrary(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemovedTrackRow(
    track: Track,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            coverUri = track.coverUri,
            size = Dim.coverSmall,
            shape = Shapes.coverSmall,
            seed = track.artSeed(),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.artist ?: "未知艺术家")
                    if (track.durationMs > 0) append(" · ${track.durationMs.toDurationLabel()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(Shapes.pill)
                .background(Palette.Blue.copy(alpha = 0.14f))
                .clickable(onClick = onRestore)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = "恢复",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.BlueLight,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
