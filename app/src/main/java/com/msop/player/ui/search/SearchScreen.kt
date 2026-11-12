package com.msop.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Track
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.ScreenTitle
import com.msop.player.ui.components.TrackRow
import com.msop.player.ui.library.LibraryViewModel
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 搜索。空查询时展示「最近搜索 / 最近播放 / 最近添加」（对应 index.html 的搜索页），
 * 输入后在本地曲库前端过滤——5000 首以内一次 filter 是毫秒级。
 */
@Composable
fun SearchScreen(vm: LibraryViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val recentPlayedIds by vm.recentPlayedIds.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val results = remember(tracks, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            tracks.filter { track ->
                track.title.contains(q, ignoreCase = true) ||
                    track.artist?.contains(q, ignoreCase = true) == true ||
                    track.album?.contains(q, ignoreCase = true) == true
            }.take(80)
        }
    }
    val recentPlayed = remember(recentPlayedIds, tracks) {
        recentPlayedIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
    }
    val recentAdded = remember(tracks) { tracks.sortedByDescending { it.addedAt }.take(4) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = "搜索")
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { vm.addSearch(query) },
        )

        when {
            query.isBlank() -> {
                if (recentSearches.isEmpty() && recentPlayed.isEmpty() && recentAdded.isEmpty()) {
                    EmptyHint(
                        title = "搜点什么",
                        description = "输入歌名、艺术家或专辑名。\n本机与云端的结果会合并显示。",
                    )
                } else {
                    Discovery(
                        recentSearches = recentSearches,
                        onPickSearch = { query = it; vm.addSearch(it) },
                        onClearSearches = vm::clearSearches,
                        recentPlayed = recentPlayed,
                        recentAdded = recentAdded,
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        onPlay = { list, index -> vm.play(list, index) },
                    )
                }
            }

            results.isEmpty() -> EmptyHint(
                title = "没有找到",
                description = "换个关键词试试，或者确认曲库已经扫描完成。",
            )

            else -> {
                Text(
                    text = "找到 ${results.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(start = Dim.screen, top = 12.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(items = results, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == playback.currentTrackId,
                            isPlaying = playback.isPlaying,
                            onClick = {
                                vm.addSearch(query)
                                vm.playTrack(track, results)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 6.dp)
            .height(46.dp)
            .clip(Shapes.pill)
            .background(Palette.Surface3)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Palette.TextTertiary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索歌曲、专辑、艺术家",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.BlueLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "清空",
                tint = Palette.TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun Discovery(
    recentSearches: List<String>,
    onPickSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    recentPlayed: List<Track>,
    recentAdded: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (recentSearches.isNotEmpty()) {
            item {
                SectionHeader("最近搜索", action = "清空", onAction = onClearSearches)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dim.screen),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentSearches.forEach { s -> SearchChip(s) { onPickSearch(s) } }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        if (recentPlayed.isNotEmpty()) {
            item {
                SectionHeader("最近播放", action = "共 ${recentPlayed.size} 首")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dim.screen),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(items = recentPlayed, key = { "rp_${it.id}" }) { t ->
                        RecentCard(t) { onPlay(recentPlayed, recentPlayed.indexOf(t)) }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        if (recentAdded.isNotEmpty()) {
            item { SectionHeader("最近添加") }
            items(items = recentAdded, key = { "ra_${it.id}" }) { t ->
                TrackRow(
                    track = t,
                    isCurrent = t.id == currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { onPlay(recentAdded, recentAdded.indexOf(t)) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dim.screen, end = Dim.screen, top = 18.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall,
                color = if (onAction != null) Palette.BlueLight else Palette.TextTertiary,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
            )
        }
    }
}

@Composable
private fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
    ) {
        CoverThumb(coverUri = track.coverUri, size = 104.dp, shape = Shapes.coverGrid)
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            text = track.artist ?: "未知艺术家",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun SearchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Shapes.pill)
            .background(Palette.Surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
        )
    }
}
