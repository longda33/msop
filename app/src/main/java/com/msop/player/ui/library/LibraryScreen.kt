package com.msop.player.ui.library

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.ui.components.CoverSquare
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.ScreenTitle
import com.msop.player.ui.components.TrackRow
import com.msop.player.ui.playlist.PlaylistScreen
import com.msop.player.ui.playlist.PlaylistViewModel
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

private val TABS = listOf("歌曲", "最近", "专辑", "艺术家", "文件夹")

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onRescan: () -> Unit,
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val playlistVm: PlaylistViewModel = hiltViewModel()
    var tab by remember { mutableIntStateOf(0) }
    var sourceFilter by remember { mutableStateOf(SourceFilter.ALL) }
    var detailAlbum by remember { mutableStateOf<String?>(null) }
    var playlistsOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val filtered = remember(tracks, sourceFilter) {
        when (sourceFilter) {
            SourceFilter.ALL -> tracks
            SourceFilter.LOCAL -> tracks.filter { it.source == SourceType.LOCAL }
            SourceFilter.CLOUD -> tracks.filter { it.source == SourceType.OPENLIST }
        }
    }

    if (playlistsOpen) {
        PlaylistScreen(
            playlistVm = playlistVm,
            libraryVm = vm,
            currentTrackId = playback.currentTrackId,
            isPlaying = playback.isPlaying,
            onPlay = { list, index -> vm.play(list, index) },
            onBack = { playlistsOpen = false },
        )
        return
    }

    detailAlbum?.let { album ->
        AlbumDetailScreen(
            albumName = album,
            tracks = filtered.filter { (it.album ?: "未知专辑") == album },
            currentTrackId = playback.currentTrackId,
            isPlaying = playback.isPlaying,
            onBack = { detailAlbum = null },
            onPlayAll = { list, index -> vm.play(list, index) },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = "音乐库") {
            Box {
                IconAction(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("播放列表") },
                        onClick = {
                            menuOpen = false
                            playlistsOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重新扫描曲库") },
                        onClick = {
                            menuOpen = false
                            onRescan()
                        },
                    )
                }
            }
        }

        TabRow(selected = tab, onSelect = { tab = it })

        val localCount = remember(tracks) { tracks.count { it.source == SourceType.LOCAL } }
        val cloudCount = remember(tracks) { tracks.count { it.source == SourceType.OPENLIST } }
        SourceChips(
            selected = sourceFilter,
            total = tracks.size,
            local = localCount,
            cloud = cloudCount,
            onSelect = { sourceFilter = it },
        )

        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = if (tracks.isEmpty()) -1 else tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "libraryTab",
            ) { t ->
                when {
                    t == -1 -> EmptyHint(
                        title = "曲库还是空的",
                        description = "首次扫描可能需要几秒。也可以到设置里添加自定义目录。",
                    )
                    t == 0 -> TrackList(
                        tracks = filtered,
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        onPlay = { list, index -> vm.play(list, index) },
                    )
                    t == 1 -> TrackList(
                        tracks = remember(filtered) { filtered.sortedByDescending { it.addedAt }.take(80) },
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        onPlay = { list, index -> vm.play(list, index) },
                    )
                    t == 2 -> AlbumGrid(
                        tracks = filtered,
                        onOpen = { detailAlbum = it },
                    )
                    t == 3 -> ArtistList(
                        tracks = filtered,
                        onOpen = { artist ->
                            val list = filtered.filter { (it.artist ?: "未知艺术家") == artist }
                            if (list.isNotEmpty()) vm.play(list, 0)
                        },
                    )
                    else -> FolderList(tracks = filtered, onPlay = { list, index -> vm.play(list, index) })
                }
            }
        }
    }
}

private enum class SourceFilter { ALL, LOCAL, CLOUD }

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dim.screen),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            TABS.forEachIndexed { index, label ->
                val active = index == selected
                Column(
                    modifier = Modifier.clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Palette.TextPrimary else Palette.TextTertiary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 9.dp, bottom = 11.dp),
                    )
                    Box(
                        Modifier
                            .height(2.5.dp)
                            .width(if (active) 22.dp else 0.dp)
                            .clip(CircleShape)
                            .background(if (active) Palette.BlueLight else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
    }
}

@Composable
private fun SourceChips(
    selected: SourceFilter,
    total: Int,
    local: Int,
    cloud: Int,
    onSelect: (SourceFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dim.screen, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("全部 $total", selected == SourceFilter.ALL) { onSelect(SourceFilter.ALL) }
        Chip("本机 $local", selected == SourceFilter.LOCAL) { onSelect(SourceFilter.LOCAL) }
        if (cloud > 0) {
            Chip("云端 $cloud", selected == SourceFilter.CLOUD) { onSelect(SourceFilter.CLOUD) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Shapes.pill)
            .background(if (active) Palette.Blue else Color.Transparent)
            .then(if (active) Modifier else Modifier.border(1.dp, Palette.Line, Shapes.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else Palette.TextSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onPlay(tracks, tracks.indexOf(track)) },
            )
        }
    }
}

@Composable
private fun AlbumGrid(tracks: List<Track>, onOpen: (String) -> Unit) {
    val albums = remember(tracks) {
        tracks.groupBy { it.album ?: "未知专辑" }
            .map { (name, list) ->
                AlbumUi(
                    name = name,
                    artist = list.firstNotNullOfOrNull { it.artist },
                    coverUri = list.firstNotNullOfOrNull { it.coverUri },
                    count = list.size,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dim.screen, end = Dim.screen, top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(count = albums.size, key = { albums[it].name }) { index ->
            val album = albums[index]
            Column(Modifier.clickable { onOpen(album.name) }) {
                CoverSquare(
                    coverUri = album.coverUri,
                    shape = Shapes.coverGrid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "${album.artist ?: "未知艺术家"} · ${album.count} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private data class AlbumUi(val name: String, val artist: String?, val coverUri: String?, val count: Int)

@Composable
private fun ArtistList(tracks: List<Track>, onOpen: (String) -> Unit) {
    val artists = remember(tracks) {
        tracks.groupBy { it.artist ?: "未知艺术家" }
            .map { (name, list) -> Triple(name, list.size, list.mapNotNull { it.album }.distinct().size) }
            .sortedBy { it.first.lowercase() }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items = artists, key = { it.first }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable { onOpen(artist.first) }
                    .padding(horizontal = Dim.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Palette.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = artist.first.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextSecondary,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = artist.first,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${artist.third} 张专辑 · ${artist.second} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderList(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    val folders = remember(tracks) {
        tracks.filter { it.localPath != null }
            .groupBy { track ->
                val path = track.localPath.orEmpty()
                path.substringBeforeLast('/', missingDelimiterValue = "/")
            }
            .toList()
            .sortedBy { it.first }
    }

    if (folders.isEmpty()) {
        EmptyHint(title = "没有文件夹信息", description = "本机扫描完成后，这里会按目录归类。")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items = folders, key = { it.first }) { (folder, list) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { onPlay(list, 0) }
                    .padding(horizontal = Dim.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = com.msop.player.ui.icons.PlayerIcons.Folder,
                    contentDescription = null,
                    tint = Palette.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = folder.substringAfterLast('/').ifBlank { folder },
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "${list.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}
