package com.msop.player.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Playlist
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.feature.player.PlayMode
import com.msop.player.ui.components.CircleGlassButton
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.EnteringPage
import com.msop.player.ui.components.GlassPill
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.iosStackSpec
import com.msop.player.ui.components.PrimaryPill
import com.msop.player.ui.components.ReorderHandle
import com.msop.player.ui.components.ReorderState
import com.msop.player.ui.components.TrackMenuActions
import com.msop.player.ui.components.TrackRow
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.library.LibraryViewModel
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 「歌单」类目（曲库第 4 个 Tab，排在「文件夹」之前）。
 *
 * 2026-11-15 第九轮（用户指令）：音乐库右上角菜单里的「播放列表」改名「歌单」，
 * 并挪到「文件夹」类目前面 —— 于是它变成曲库自己的一个 Tab：列表内嵌在曲库外壳里，
 * 不再自带标题栏与返回箭头；点条目由曲库根层把详情页当二级页右滑打开。
 */
@Composable
fun PlaylistTab(
    playlistVm: PlaylistViewModel,
    topInset: Dp,
    onOpenDetail: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val favorites by playlistVm.favorites.collectAsStateWithLifecycle()
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    // 拖动排序：本地维护一份顺序（拖动时即时换位），抬手后落库
    var order by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val rowHeightPx = with(LocalDensity.current) { 84.dp.toPx() }
    val reorder = remember(rowHeightPx) { ReorderState(rowHeightPx) }
    LaunchedEffect(playlists) {
        if (!reorder.active) order = playlists
    }
    val shown = if (order.map { it.id }.toSet() == playlists.map { it.id }.toSet()) order else playlists

    // 收藏是虚拟歌单：置顶、不可重命名/删除，点开看收藏的歌
    val favoritesPlaylist = Playlist(
        id = PlaylistViewModel.FAVORITES_ID,
        name = PlaylistViewModel.FAVORITES_NAME,
        trackCount = favorites.size,
        coverUri = favorites.firstOrNull()?.coverUri,
    )

    Column(modifier.fillMaxSize()) {
        // 标题栏是曲库外壳的毛玻璃层，列表首项要让开它，否则被压住
        Spacer(Modifier.height(topInset))
        // 2026-11-17：收藏条目要求「置顶」。当前 Compose foundation 1.12 没有
        // stickyHeader DSL，直接把标题 + 收藏条放在 LazyColumn 外面，滚动列表时保持固定。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "歌单",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${playlists.size} 个歌单 · 收藏置顶 · 按住右侧手柄可拖动排序",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                CircleGlassButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "新建歌单",
                    onClick = { createOpen = true },
                    size = 34.dp,
                    iconSize = 18.dp,
                    tint = Palette.BlueLight,
                    container = Palette.Blue.copy(alpha = 0.14f),
                )
            }
            FavoritesRow(
                count = favorites.size,
                coverUri = favorites.firstOrNull()?.coverUri,
                onClick = { onOpenDetail(favoritesPlaylist) },
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Dim.screen,
                end = Dim.screen,
                top = 4.dp,
                bottom = LocalHazeBottomInset.current + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items = shown, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenDetail(playlist) },
                    onRename = { renameTarget = playlist },
                    onDelete = { deleteTarget = playlist },
                    dragHandle = {
                        ReorderHandle(
                            state = reorder,
                            index = index,
                            count = { shown.size },
                            onDrop = { from, to ->
                                val list = shown.toMutableList()
                                val item = list.removeAt(from)
                                list.add(to, item)
                                order = list
                                playlistVm.reorderPlaylists(list.map { it.id })
                            },
                        )
                    },
                    modifier = Modifier.graphicsLayer {
                        translationY = reorder.translationFor(index)
                    },
                )
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyHint(
                        title = "还没有歌单",
                        description = "把喜欢的歌整理成歌单，重启后也不会丢。",
                        action = {
                            PrimaryPill(
                                label = "新建歌单",
                                onClick = { createOpen = true },
                                icon = Icons.Filled.Add,
                            )
                        },
                    )
                }
            }
        }
    }

    if (createOpen) {
        NameDialog(
            title = "新建歌单",
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
            title = "重命名歌单",
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
            containerColor = Palette.Surface2,
            shape = Shapes.card,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("删除歌单？") },
            text = { Text("「${target.name}」会从歌单列表中移除，歌曲文件本身不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistVm.delete(target.id)
                        deleteTarget = null
                    },
                ) { Text("删除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = Palette.TextSecondary) }
            },
        )
    }
}

/**
 * 歌单详情页（曲库二级页 overlay）：重命名 / 删除弹窗在这里闭环；
 * 返回键与右上角返回箭头都走 [onBack]，删除后自动退回曲库。
 */
@Composable
fun PlaylistDetailPage(
    playlist: Playlist,
    playlistVm: PlaylistViewModel,
    allTracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    playMode: PlayMode,
    positionMs: Long = 0L,
    onPlay: (List<Track>, Int) -> Unit,
    onToggle: () -> Unit,
    onSetPlayMode: (PlayMode) -> Unit,
    onBack: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    BackHandler(onBack = onBack)

    PlaylistDetail(
        playlist = playlist,
        playlistVm = playlistVm,
        allTracks = allTracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        positionMs = positionMs,
        playMode = playMode,
        onPlay = onPlay,
        onToggle = onToggle,
        onSetPlayMode = onSetPlayMode,
        onBack = onBack,
        onRename = { renameTarget = it },
        onDelete = { deleteTarget = it },
    )

    renameTarget?.let { target ->
        NameDialog(
            title = "重命名歌单",
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
            containerColor = Palette.Surface2,
            shape = Shapes.card,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("删除歌单？") },
            text = { Text("「${target.name}」会从歌单列表中移除，歌曲文件本身不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistVm.delete(target.id)
                        deleteTarget = null
                        onBack()
                    },
                ) { Text("删除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = Palette.TextSecondary) }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            coverUri = playlist.coverUri,
            size = 54.dp,
            shape = Shapes.coverSmall,
            seed = playlist.name,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPill(
                    label = "${playlist.trackCount} 首",
                    color = Palette.TextSecondary,
                    background = Color.White.copy(alpha = 0.055f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "歌单",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }
        Box {
            IconAction(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Palette.Surface2,
                shape = Shapes.cardSmall,
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Palette.TextSecondary) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = Palette.Red) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Palette.Red) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Palette.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        dragHandle?.let { handle ->
            Spacer(Modifier.width(2.dp))
            handle()
        }
    }
}

/** 歌单列表顶部置顶的「收藏歌曲」条目（虚拟歌单，不可改名/删除）。 */
@Composable
private fun FavoritesRow(
    count: Int,
    coverUri: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(Shapes.coverSmall)
                .background(Palette.Red.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUri != null) {
                CoverThumb(coverUri = coverUri, size = 54.dp, shape = Shapes.coverSmall)
            } else {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Palette.Red,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = PlaylistViewModel.FAVORITES_NAME,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPill(
                    label = "$count 首",
                    color = Palette.TextSecondary,
                    background = Color.White.copy(alpha = 0.055f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "收藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Palette.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    playlistVm: PlaylistViewModel,
    allTracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    playMode: PlayMode,
    onPlay: (List<Track>, Int) -> Unit,
    onToggle: () -> Unit,
    onSetPlayMode: (PlayMode) -> Unit,
    onBack: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    val isFavorites = playlist.id == PlaylistViewModel.FAVORITES_ID
    val tracks by remember(playlist.id) {
        if (isFavorites) playlistVm.favorites else playlistVm.tracks(playlist.id)
    }.collectAsStateWithLifecycle(emptyList())
    // 播放/随机按钮要有即时可见的反馈：正在放本歌单的歌时显示暂停，随机模式高亮。
    val playingThisList = isPlaying && currentTrackId != null && tracks.any { it.id == currentTrackId }
    val shuffleOn = playMode == PlayMode.SHUFFLE
    val allPlaylists by playlistVm.playlists.collectAsStateWithLifecycle()
    val favoriteIds by playlistVm.favoriteIds.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val totalMs = tracks.sumOf { it.durationMs }

    // ---------------------------------------------------------------- 多选批量（2026-11-16 晚）
    // 用户报「歌单类目下歌曲无法批量操作」，这里加一套轻量多选：点「选择」进模式，
    // 底部玻璃条提供 移除 / 移动到其它歌单 / 加入其它歌单 / 下载云端歌。
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchMoveOpen by remember { mutableStateOf(false) }
    var batchAddOpen by remember { mutableStateOf(false) }
    val selectedTracks = remember(tracks, selected) { tracks.filter { it.id in selected } }
    LaunchedEffect(selecting) { if (!selecting) selected = emptySet() }

    // 拖动排序（歌单内歌曲）：本地顺序即时换位，抬手落库
    var order by remember { mutableStateOf<List<Track>>(emptyList()) }
    val rowHeightPx = with(LocalDensity.current) { (Dim.rowHeight + 2.dp).toPx() }
    val reorder = remember(rowHeightPx) { ReorderState(rowHeightPx) }
    LaunchedEffect(tracks) {
        if (!reorder.active) order = tracks
    }
    val shownTracks =
        if (order.map { it.id }.toSet() == tracks.map { it.id }.toSet()) order else tracks

    // P7-11：选歌页 iOS 式转场——详情页压缩渐暗，选歌页右滑入。
    val bgScale by animateFloatAsState(
        targetValue = if (pickerOpen) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "pdtBgScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (pickerOpen) 0.55f else 1f,
        animationSpec = tween(180),
        label = "pdtBgDim",
    )

    // P7-09：歌单详情接入毛玻璃标题栏；封面信息卡随列表滚动，经过标题栏后方被模糊。
    HazeTitleScaffold(
        modifier = Modifier.graphicsLayer {
            scaleX = bgScale
            scaleY = bgScale
            alpha = bgAlpha
        },
        title = playlist.name,
        // 2026-11-16 晚用户需求：副标题只留「N 首」，不再拼「· N 分钟」。
        subtitle = "${tracks.size} 首",
        onBack = onBack,
        contentTopInset = HazeTitleHeight + 4.dp,
        actions = {
            if (selecting) {
                Text(
                    text = if (selected.size == tracks.size) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier
                        .clip(Shapes.pill)
                        .clickable {
                            selected = if (selected.size == tracks.size) {
                                emptySet()
                            } else {
                                tracks.map { it.id }.toSet()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.BlueLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(Shapes.pill)
                        .clickable { selecting = false }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else {
                if (!isFavorites) {
                    CircleGlassButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "添加歌曲",
                        onClick = { pickerOpen = true },
                        size = 40.dp,
                        iconSize = 21.dp,
                        tint = Palette.BlueLight,
                        container = Palette.Blue.copy(alpha = 0.14f),
                    )
                }
                Box {
                    IconAction(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Palette.Surface2,
                        shape = Shapes.cardSmall,
                    ) {
                        // 歌单内的歌曲没有「加入歌单」——批量里另有「加入其它歌单」，
                        // 单首也仍可从曲库那边加，避免同一个动作两个入口。
                        DropdownMenuItem(
                            text = { Text("选择歌曲") },
                            leadingIcon = {
                                Icon(Icons.Filled.Check, null, tint = Palette.TextSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                selecting = true
                            },
                        )
                        if (!isFavorites) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Palette.TextSecondary) },
                                onClick = {
                                    menuOpen = false
                                    onRename(playlist)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = Palette.Red) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Palette.Red) },
                                onClick = {
                                    menuOpen = false
                                    onDelete(playlist)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { sourceModifier, topInset ->
        Box(Modifier.fillMaxSize()) {
        if (tracks.isEmpty()) {
            EmptyHint(
                title = if (isFavorites) "还没有收藏" else "歌单还是空的",
                description = if (isFavorites) {
                    "在播放页点一下爱心，就会出现在这里。"
                } else {
                    "从曲库里挑几首歌放进来。"
                },
                action = if (isFavorites) {
                    null
                } else {
                    {
                        PrimaryPill(
                            label = "添加歌曲",
                            onClick = { pickerOpen = true },
                            icon = Icons.Filled.Add,
                        )
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = sourceModifier,
                contentPadding = PaddingValues(top = topInset + 4.dp, bottom = LocalHazeBottomInset.current + 16.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dim.screen, vertical = 10.dp)
                            .glassSurface(Shapes.card, Palette.Surface.copy(alpha = 0.72f), elevation = 10.dp)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverThumb(
                            coverUri = playlist.coverUri,
                            size = 96.dp,
                            shape = Shapes.coverGrid,
                            seed = playlist.name,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Palette.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                // 2026-11-16 晚：信息卡同样去掉「· N 分钟」，只显示曲目数。
                                text = "${tracks.size} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        // 2026-11-17：浅色主题下原来的白底 + Palette.Ink（浅色也是近白）
                                        // 等于看不见按钮，固定蓝底白图标，两种主题都清晰。
                                        .background(Palette.Blue)
                                        .clickable {
                                            if (tracks.isEmpty()) return@clickable
                                            if (playingThisList) onToggle() else onPlay(tracks, 0)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (playingThisList) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playingThisList) "暂停" else "播放全部",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                CircleGlassButton(
                                    icon = PlayerIcons.Shuffle,
                                    contentDescription = if (shuffleOn) "关闭随机播放" else "随机播放",
                                    onClick = {
                                        if (tracks.isEmpty()) return@CircleGlassButton
                                        val enableShuffle = !shuffleOn
                                        onSetPlayMode(if (enableShuffle) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL)
                                        // 立即用新顺序起播，按钮高亮 + 队列同时变化，点击有反馈
                                        onPlay(if (enableShuffle) tracks.shuffled() else tracks, 0)
                                    },
                                    size = 34.dp,
                                    iconSize = 17.dp,
                                    tint = if (shuffleOn) Color.White else Palette.BlueLight,
                                    container = if (shuffleOn) Palette.Blue else Palette.Blue.copy(alpha = 0.16f),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(items = shownTracks, key = { _, track -> track.id }) { index, track ->
                    if (selecting) {
                        SelectableTrackRow(
                            track = track,
                            checked = track.id in selected,
                            onToggle = {
                                selected = if (track.id in selected) {
                                    selected - track.id
                                } else {
                                    selected + track.id
                                }
                            },
                        )
                        return@itemsIndexed
                    }
                    TrackRow(
                        modifier = Modifier.graphicsLayer {
                            translationY = reorder.translationFor(index)
                        },
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlay(shownTracks, index) },
                        // 2026-11-18 用户指令：正在播那一行右侧显示剩余时长并逐秒递减
                        remainingMs = if (track.id == currentTrackId && track.durationMs > 0L) {
                            (track.durationMs - positionMs).coerceAtLeast(0L)
                        } else {
                            null
                        },
                        onRemove = if (isFavorites) {
                            // 2026-11-16 晚用户指令：收藏歌曲的三点菜单里删掉「从歌单移除」，
                            // 行级 onRemove 也一起掐掉（TrackRow 会回落到它），
                            // 取消收藏仍由菜单里的「取消收藏」承担。
                            null
                        } else {
                            { playlistVm.removeTrack(playlist.id, track.id) }
                        },
                        menu = TrackMenuActions(
                            playlists = allPlaylists.map { it.id to it.name },
                            playlistIdsContaining = if (isFavorites) {
                                emptySet()
                            } else {
                                setOf(playlist.id)
                            },
                            inFavorites = track.id in favoriteIds,
                            // 2026-11-16 晚用户指令：歌单三点菜单里删掉「加入歌单」；
                            // 「收藏 / 取消收藏」只在收藏类目里保留（别处由播放页爱心负责）。
                            showFavoriteItem = isFavorites,
                            // 2026-11-16 第一四轮用户指令「收藏歌曲 三点菜单 增加 加歌单」：
                            // 收藏虚拟歌单里的曲目也要能直接加到某个歌单。
                            onAddToPlaylist = if (isFavorites) {
                                { target ->
                                    playlistVm.addTracksToPlaylist(target, listOf(track.id))
                                }
                            } else {
                                null
                            },
                            onRemoveFromPlaylist = if (isFavorites) {
                                null
                            } else {
                                { playlistVm.removeTrack(playlist.id, track.id) }
                            },
                            onToggleFavorite = { playlistVm.toggleFavorite(track.id) },
                            onRemoveFromLibrary = { playlistVm.removeFromLibrary(track) },
                            onDownload = { playlistVm.download(track) },
                        ),
                        dragHandle = {
                            ReorderHandle(
                                state = reorder,
                                index = index,
                                count = { shownTracks.size },
                                onDrop = { from, to ->
                                    val list = shownTracks.toMutableList()
                                    val item = list.removeAt(from)
                                    list.add(to, item)
                                    order = list
                                    if (!isFavorites) {
                                        playlistVm.reorderTracks(playlist.id, list.map { it.id })
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }

        // 多选底部操作条：玻璃卡浮在列表之上（不占列表高度，滚动不受影响）
        if (selecting) {
            BatchActionBar(
                count = selected.size,
                cloudCount = selectedTracks.count { it.source != SourceType.LOCAL },
                isFavorites = isFavorites,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = Dim.screen,
                        end = Dim.screen,
                        bottom = LocalHazeBottomInset.current + 12.dp,
                    ),
                onRemove = {
                    if (isFavorites) {
                        selected.forEach { playlistVm.removeFavorite(it) }
                    } else {
                        playlistVm.removeTracks(playlist.id, selected.toList())
                    }
                    selecting = false
                },
                onMove = { batchMoveOpen = true },
                onAdd = { batchAddOpen = true },
                onDownload = {
                    playlistVm.downloadTracks(selectedTracks)
                    selecting = false
                },
            )
        }
        }
    } // HazeTitleScaffold content

    // 批量「移动到其它歌单」/「加入其它歌单」：一次选目标，整批落库
    if (batchMoveOpen) {
        BatchPlaylistPicker(
            title = "移动到其它歌单",
            playlists = allPlaylists.filter { it.id != playlist.id },
            onDismiss = { batchMoveOpen = false },
            onPick = { target ->
                playlistVm.moveTracks(playlist.id, target, selected.toList())
                batchMoveOpen = false
                selecting = false
            },
        )
    }
    if (batchAddOpen) {
        BatchPlaylistPicker(
            title = "加入其它歌单",
            playlists = allPlaylists.filter { it.id != playlist.id },
            onDismiss = { batchAddOpen = false },
            onPick = { target ->
                playlistVm.addTracksToPlaylist(target, selected.toList())
                batchAddOpen = false
                selecting = false
            },
        )
    }

    // P7-11：选歌页 overlay——右滑入 + 缩放 + 圆角收拢；返回反向滑出。
    AnimatedContent(
        targetState = pickerOpen,
        transitionSpec = { iosStackSpec(targetState) },
        label = "playlistPicker",
    ) { open ->
        if (open) {
            EnteringPage {
                AddTracksScreen(
                    allTracks = allTracks,
                    existingIds = tracks.map { it.id }.toSet(),
                    onDismiss = { pickerOpen = false },
                    onConfirm = { selected ->
                        playlistVm.addTracks(playlist.id, selected)
                        pickerOpen = false
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxSize())
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

    HazeTitleScaffold(
        title = "添加歌曲",
        subtitle = "${results.size} 首可选",
        onBack = onDismiss,
        // 标题栏 72dp + 搜索框 48dp + 上下边距 12dp
        contentTopInset = HazeTitleHeight + 62.dp,
        actions = {
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier
                    .clip(Shapes.pill)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        },
        pinnedHeader = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dim.screen, vertical = 6.dp)
                    .height(48.dp)
                    .clip(Shapes.cardSmall)
                    .background(Palette.Surface2.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), Shapes.cardSmall)
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
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = Palette.TextTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" },
                    )
                }
            }
        },
    ) { sourceModifier, topInset ->
        Column(sourceModifier) {
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 8.dp),
                ) {
                items(items = results, key = { it.id }) { track ->
                    val checked = track.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 1.dp)
                            .height(64.dp)
                            .clip(Shapes.cardSmall)
                            .background(if (checked) Palette.Blue.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable {
                                selected = if (checked) selected - track.id else selected + track.id
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (checked) selected - track.id else selected + track.id
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Palette.Blue,
                                checkmarkColor = Color.White,
                                uncheckedColor = Palette.TextTertiary,
                            ),
                        )
                        CoverThumb(
                            coverUri = track.coverUri,
                            size = 42.dp,
                            seed = track.artSeed(),
                        )
                        Spacer(Modifier.width(10.dp))
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dim.screen, vertical = 10.dp)
                        .padding(bottom = LocalHazeBottomInset.current),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selected.isEmpty()) "选择要添加的歌曲" else "已选 ${selected.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected.isEmpty()) Palette.TextTertiary else Palette.BlueLight,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryPill(
                        label = if (selected.isEmpty()) "添加" else "添加 ${selected.size} 首",
                        onClick = { onConfirm(candidates.filter { it.id in selected }) },
                        enabled = selected.isNotEmpty(),
                        icon = Icons.Filled.Check,
                    )
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
        containerColor = Palette.Surface2,
        shape = Shapes.card,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextSecondary,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                label = { Text("名称") },
                shape = Shapes.cardSmall,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel, color = if (text.isNotBlank()) Palette.BlueLight else Palette.TextTertiary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Palette.TextSecondary) }
        },
    )
}


/** 多选模式下的曲目行（勾选框 + 封面 + 歌名歌手），点整行即勾选。 */
@Composable
private fun SelectableTrackRow(
    track: Track,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dim.rowHeight)
            .clip(Shapes.cardSmall)
            .background(
                if (checked) Palette.Blue.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Palette.BlueLight,
                uncheckedColor = Palette.TextTertiary,
                checkmarkColor = Palette.Ink,
            ),
        )
        CoverThumb(coverUri = track.coverUri, size = 42.dp, shape = Shapes.coverSmall, seed = track.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: "未知艺术家",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.source != SourceType.LOCAL) {
            Icon(PlayerIcons.Cloud, null, tint = Palette.TextTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

/** 多选底部玻璃操作条：移除 / 移动 / 加歌单 / 下载云端歌。 */
@Composable
private fun BatchActionBar(
    count: Int,
    cloudCount: Int,
    isFavorites: Boolean,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onMove: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(Shapes.card, Color.White.copy(alpha = 0.10f), elevation = 14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "已选 $count 首",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        BatchAction(
            label = if (isFavorites) "取消收藏" else "移除",
            icon = Icons.Filled.Delete,
            enabled = count > 0,
            onClick = onRemove,
        )
        if (!isFavorites) {
            BatchAction(label = "移动", icon = PlayerIcons.MoveTo, enabled = count > 0, onClick = onMove)
            BatchAction(label = "加歌单", icon = PlayerIcons.PlaylistAddCircle, enabled = count > 0, onClick = onAdd)
        }
        BatchAction(
            label = "下载",
            icon = PlayerIcons.Download,
            enabled = cloudCount > 0,
            onClick = onDownload,
        )
    }
}

@Composable
private fun BatchAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Palette.TextPrimary else Palette.TextTertiary.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .clip(Shapes.cardSmall)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 批量操作的目标歌单选择弹窗（单首的 PlaylistPicker 在 TrackRow 里，不共用）。 */
@Composable
private fun BatchPlaylistPicker(
    title: String,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = { Text(title, color = Palette.TextPrimary) },
        text = {
            if (playlists.isEmpty()) {
                Text("没有其它歌单，先去歌单页新建一个。", color = Palette.TextSecondary)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shapes.cardSmall)
                                .clickable { onPick(playlist.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverThumb(
                                coverUri = playlist.coverUri,
                                size = 34.dp,
                                shape = Shapes.coverSmall,
                                seed = playlist.name,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Palette.TextSecondary) }
        },
    )
}
