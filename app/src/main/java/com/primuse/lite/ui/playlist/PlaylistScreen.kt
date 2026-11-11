package com.primuse.lite.ui.playlist

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primuse.lite.core.model.Playlist
import com.primuse.lite.core.model.Track
import com.primuse.lite.ui.components.CoverThumb
import com.primuse.lite.ui.components.EmptyHint
import com.primuse.lite.ui.components.IconAction
import com.primuse.lite.ui.components.TrackRow
import com.primuse.lite.ui.library.LibraryViewModel
import com.primuse.lite.ui.theme.Dim
import com.primuse.lite.ui.theme.Palette
import com.primuse.lite.ui.theme.Shapes

/**
 * 播放列表。挂载在音乐库的更多菜单下，用全屏 overlay 而不是导航目的地，
 * 这样返回键只退回曲库，不会把 Tab 状态一起重置。
 */
@Composable
fun PlaylistScreen(
    playlistVm: PlaylistViewModel,
    libraryVm: LibraryViewModel,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val allTracks by libraryVm.tracks.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    val selected = playlists.firstOrNull { it.id == selectedId }

    BackHandler {
        if (selected != null) selectedId = null else onBack()
    }

    if (selected == null) {
        PlaylistList(
            playlists = playlists,
            onBack = onBack,
            onCreate = { createOpen = true },
            onOpen = { selectedId = it.id },
            onRename = { renameTarget = it },
            onDelete = { deleteTarget = it },
        )
    } else {
        PlaylistDetail(
            playlist = selected,
            playlistVm = playlistVm,
            allTracks = allTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onBack = { selectedId = null },
            onRename = { renameTarget = it },
            onDelete = { deleteTarget = it },
        )
    }

    if (createOpen) {
        NameDialog(
            title = "新建播放列表",
            initial = "",
            confirmLabel = "创建",
            onDismiss = { createOpen = false },
            onConfirm = {
                playlistVm.create(it)
                createOpen = false
            },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "重命名",
            initial = target.name,
            confirmLabel = "保存",
            onDismiss = { renameTarget = null },
            onConfirm = {
                playlistVm.rename(target.id, it)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除播放列表？") },
            text = { Text("「${target.name}」会从列表中移除，歌曲文件本身不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistVm.delete(target.id)
                        if (selectedId == target.id) selectedId = null
                        deleteTarget = null
                    },
                ) { Text("删除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "播放列表",
            onBack = onBack,
            actions = { IconAction(Icons.Filled.Add, "新建播放列表", onCreate) },
        )

        if (playlists.isEmpty()) {
            EmptyHint(
                title = "还没有播放列表",
                description = "把喜欢的歌整理成歌单，重启后也不会丢。",
                action = {
                    Button(onClick = onCreate) { Text("新建播放列表") }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
            ) {
                items(items = playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpen(playlist) },
                        onRename = { onRename(playlist) },
                        onDelete = { onDelete(playlist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Dim.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(playlist.coverUri, 50.dp, Shapes.coverSmall)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box {
            IconAction(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    playlistVm: PlaylistViewModel,
    allTracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    val tracks by remember(playlist.id) { playlistVm.tracks(playlist.id) }
        .collectAsStateWithLifecycle(emptyList())
    var pickerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    if (pickerOpen) {
        AddTracksScreen(
            allTracks = allTracks,
            existingIds = tracks.map { it.id }.toSet(),
            onDismiss = { pickerOpen = false },
            onConfirm = { selected ->
                playlistVm.addTracks(playlist.id, selected)
                pickerOpen = false
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Header(
            title = playlist.name,
            onBack = onBack,
            actions = {
                IconAction(Icons.Filled.Add, "添加歌曲", onClick = { pickerOpen = true })
                Box {
                    IconAction(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = {
                                menuOpen = false
                                onRename(playlist)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = {
                                menuOpen = false
                                onDelete(playlist)
                            },
                        )
                    }
                }
            },
        )

        if (tracks.isEmpty()) {
            EmptyHint(
                title = "歌单还是空的",
                description = "从曲库里挑几首歌放进来。",
                action = { Button(onClick = { pickerOpen = true }) { Text("添加歌曲") } },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dim.screen, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${tracks.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                PillButton("播放全部") { onPlay(tracks, 0) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlay(tracks, index) },
                        onRemove = { playlistVm.removeTrack(playlist.id, track.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTracksScreen(
    allTracks: List<Track>,
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<Track>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    BackHandler(onBack = onDismiss)

    val candidates = remember(allTracks, existingIds) {
        allTracks.filter { it.id !in existingIds }
    }
    val results = remember(candidates, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            candidates
        } else {
            candidates.filter { track ->
                track.title.contains(q, ignoreCase = true) ||
                    track.artist?.contains(q, ignoreCase = true) == true ||
                    track.album?.contains(q, ignoreCase = true) == true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Dim.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp),
            )
            Text(
                text = "添加歌曲",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (selected.isEmpty()) "完成" else "完成 (${selected.size})",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected.isEmpty()) Palette.TextTertiary else Palette.BlueLight,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = selected.isNotEmpty()) {
                        onConfirm(candidates.filter { it.id in selected })
                    }
                    .padding(vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 6.dp)
                .height(44.dp)
                .clip(Shapes.pill)
                .background(Palette.Surface3)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Palette.TextTertiary,
                modifier = Modifier.size(18.dp),
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
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(Palette.BlueLight),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (results.isEmpty()) {
            EmptyHint(
                title = if (candidates.isEmpty()) "没有可添加的歌曲" else "没有找到",
                description = if (candidates.isEmpty()) {
                    "曲库里的歌都已经在这个歌单里了。"
                } else {
                    "换个关键词试试。"
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                items(items = results, key = { it.id }) { track ->
                    val checked = track.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clickable {
                                selected = if (checked) selected - track.id else selected + track.id
                            }
                            .padding(horizontal = Dim.screen),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (checked) selected - track.id else selected + track.id
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(track.artist, track.album)
                                    .joinToString(" · ")
                                    .ifBlank { "未知艺术家" },
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
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                label = { Text("名称") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = Palette.Surface,
    )
}

@Composable
private fun Header(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

/** 胶囊主按钮，和列表页的视觉语言一致。 */
@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Palette.Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
