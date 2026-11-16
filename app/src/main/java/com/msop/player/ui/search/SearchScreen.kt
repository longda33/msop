package com.msop.player.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Track
import com.msop.player.ui.components.CoverSquare
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.SectionHeader
import com.msop.player.ui.components.TrackRow
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.library.LibraryViewModel
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 搜索。空查询时是「发现」页：曲库概览 + 最近搜索 + 最近播放 + 最近添加。
 * 输入后在本地曲库前端过滤——5000 首以内一次 filter 是毫秒级。
 */
@Composable
fun SearchScreen(vm: LibraryViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val recentPlayedIds by vm.recentPlayedIds.collectAsStateWithLifecycle()
    // P5-04：查询词与过滤都交给 ViewModel（防抖 + Default 线程），
    // 这里只负责把输入写回去、把结果读出来。原来每敲一键就在主线程全表扫。
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    // P5-04：5 万首库里 firstOrNull 是 O(n·m)，先建一次 id 索引变 O(n+m)。
    val recentPlayed = remember(recentPlayedIds, tracks) {
        val byId = tracks.associateBy { it.id }
        recentPlayedIds.mapNotNull { byId[it] }
    }
    // P5-04：整表排序换部分选择，5 万首只扫一遍找 6 个最大值。
    val recentAdded = remember(tracks) {
        val top = arrayOfNulls<Track>(6)
        for (t in tracks) {
            var j = 5
            while (j >= 0 && (top[j] == null || top[j]!!.addedAt <= t.addedAt)) {
                if (j + 1 < 6) top[j + 1] = top[j]
                j--
            }
            if (j + 1 < 6) top[j + 1] = t
        }
        top.filterNotNull()
    }

    // P7-09：标题栏 + 搜索框固定在顶部毛玻璃层，结果列表铺满全屏，
    // 用 contentPadding 让首项从固定头下方开始，滚动时会经过标题栏后方。
    // 2026-11-14：副标题改「共 N 首」（用户指定，替换「在 N 首歌里找」）。
    HazeTitleScaffold(
        title = "搜索",
        subtitle = if (tracks.isEmpty()) "曲库还是空的" else "共 ${tracks.size} 首",
        contentTopInset = HazeTitleHeight + 68.dp,
        pinnedHeader = {
            SearchField(
                query = query,
                onQueryChange = { vm.setSearchQuery(it) },
                onSubmit = { vm.addSearch(query) },
            )
        },
    ) { sourceModifier, topInset ->
        AnimatedContent(
            targetState = query.isBlank(),
            modifier = sourceModifier,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "searchState",
        ) { blank ->
            if (!blank) {
                when {
                    results.isEmpty() -> EmptyHint(
                        title = "没有找到",
                        description = "换个关键词试试，或者确认曲库已经扫描完成。",
                    )

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 16.dp),
                        ) {
                            item {
                                SectionHeader(
                                    title = "搜索结果",
                                    subtitle = "${results.size} 首 · 点击播放",
                                )
                            }
                            items(items = results, key = { it.id }, contentType = { "track_row" }) { track ->
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
            } else {
                Discovery(
                    tracks = tracks,
                    recentSearches = recentSearches,
                    onPickSearch = { vm.setSearchQuery(it); vm.addSearch(it) },
                    onClearSearches = vm::clearSearches,
                    recentPlayed = recentPlayed,
                    recentAdded = recentAdded,
                    currentTrackId = playback.currentTrackId,
                    isPlaying = playback.isPlaying,
                    onPlay = { list, index -> vm.play(list, index) },
                    topInset = topInset,
                )
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
            .padding(horizontal = Dim.screen, vertical = 8.dp)
            .height(52.dp)
            .clip(Shapes.cardSmall)
            .background(Palette.Surface2.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), Shapes.cardSmall)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Palette.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
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
                textStyle = TextStyle(
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(Palette.BlueLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = Palette.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Discovery(
    tracks: List<Track>,
    recentSearches: List<String>,
    onPickSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    recentPlayed: List<Track>,
    recentAdded: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    topInset: Dp = 0.dp,
) {
    if (tracks.isEmpty()) {
        EmptyHint(
            title = "搜点什么",
            description = "输入歌名、艺术家或专辑名。\n本机与云端的结果会合并显示。",
        )
        return
    }

    // 2026-11-14：删掉统计三卡与「不知道听什么」随机卡（用户指定，截图标注区域），
    // 发现页直接从最近搜索/最近播放/最近添加开始。
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 18.dp),
    ) {
        if (recentSearches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "最近搜索",
                    action = {
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.BlueLight,
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .clickable(onClick = onClearSearches)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dim.screen),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentSearches.forEach { s -> SearchChip(s) { onPickSearch(s) } }
                }
            }
        }

        if (recentPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "最近播放",
                    subtitle = "${recentPlayed.size} 首",
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dim.screen),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = recentPlayed, key = { "rp_${it.id}" }) { t ->
                        RecentCard(t) { onPlay(recentPlayed, recentPlayed.indexOf(t)) }
                    }
                }
            }
        }

        if (recentAdded.isNotEmpty()) {
            item {
                SectionHeader(title = "最近添加", subtitle = "新入库的歌")
            }
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
private fun StatCard(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(72.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RecentCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .glassSurface(Shapes.coverGrid, Palette.Surface2),
        ) {
            CoverSquare(
                coverUri = track.coverUri,
                shape = Shapes.coverGrid,
                seed = track.artSeed(),
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
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
            .background(Palette.Surface2.copy(alpha = 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), Shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            maxLines = 1,
        )
    }
}



