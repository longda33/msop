package com.msop.player.ui.library

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Playlist
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.ui.components.CoverSquare
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.EmptyHint
import com.msop.player.ui.components.EnteringPage
import com.msop.player.ui.components.ExitConfirmDialog
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.LocalHazeTopInset
import com.msop.player.ui.components.SectionHeader
import com.msop.player.ui.components.TrackMenuActions
import com.msop.player.ui.components.TrackRow
import com.msop.player.ui.components.iosStackSpec
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.playlist.PlaylistDetailPage
import com.msop.player.ui.playlist.PlaylistTab
import com.msop.player.ui.playlist.PlaylistViewModel
import com.msop.player.desktop.DesktopLyricsService
import com.msop.player.feature.player.PlaybackService
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

// 2026-11-15 用户指令：删掉「最近」类目。
// 2026-11-15 第二轮（用户指令）：歌手与专辑位置互换 → 歌曲 / 歌手 / 专辑 / 文件夹。
// 2026-11-15 第九轮（用户指令）：右上角菜单里的「播放列表」改名「歌单」并挪到
// 「文件夹」类目前面 → 歌曲 / 歌手 / 专辑 / 歌单 / 文件夹。
private val TABS = listOf("歌曲", "歌手", "专辑", "歌单", "文件夹")

/** P7-11 曲库二级页：歌单详情 / 专辑详情 / 文件夹详情 / 歌手详情 / 已移除歌曲，overlay 形态右滑入。 */
private sealed interface LibrarySecondary {
    data class PlaylistDetail(val id: Long) : LibrarySecondary
    data class Album(val name: String) : LibrarySecondary
    data class Folder(val path: String) : LibrarySecondary
    data class Artist(val name: String) : LibrarySecondary

    /** 2026-11-16 晚用户需求：曲库右上角三点菜单里的「已移除歌曲」页。 */
    data object RemovedTracks : LibrarySecondary
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onRescan: () -> Unit,
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    // 2026-11-18 用户指令「列表行右侧时长点击播放时变化」：正在播那行要显示剩余时长，
    // 这里订阅播放进度（播放中每秒刷新一次，暂停即冻结）。
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()
    // 2026-11-16 第十四轮：文件夹 Tab 里云端只按「设置里填写的音乐目录」聚合，
    // 所以这里要拿到 profileId → rootPath 的快照；进曲库时重读一次（改完配置即生效）。
    val cloudRoots by vm.cloudRoots.collectAsStateWithLifecycle()
    // 2026-11-17：本机也按「设置里添加的音乐目录」聚合（一个目录一条），不再按子目录拆。
    val localRoots by vm.localRoots.collectAsStateWithLifecycle()
    // OpenList / WebDAV 文件夹标题用设置里填写的名称（2026-11-17 深夜用户指令）。
    val cloudNames by vm.cloudNames.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCloudRoots() }
    val playlistVm: PlaylistViewModel = hiltViewModel()
    // 2026-11-16：曲库歌曲列表的三点菜单要能「加入歌单 / 已加歌单 / 收藏 / 移除曲库」，
    // 这些状态都在歌单 ViewModel 里（收藏表 + 歌单成员关系）
    val allPlaylists by playlistVm.playlists.collectAsStateWithLifecycle()
    val favoriteIds by playlistVm.favoriteIds.collectAsStateWithLifecycle()
    val memberships by playlistVm.memberships.collectAsStateWithLifecycle()
    val playlistChoices = remember(allPlaylists) { allPlaylists.map { it.id to it.name } }
    val menuFor: (Track) -> TrackMenuActions = remember(playlistChoices, favoriteIds, memberships) {
        { track ->
            TrackMenuActions(
                playlists = playlistChoices,
                playlistIdsContaining = memberships[track.id].orEmpty(),
                // 2026-11-16 晚用户指令：曲库/歌手列表的三点菜单不再有「收藏」——
                // 收藏入口统一由播放页爱心承担，只有「收藏歌曲」类目里保留取消收藏。
                onAddToPlaylist = { playlistId -> playlistVm.addTracks(playlistId, listOf(track)) },
                onRemoveFromLibrary = { playlistVm.removeFromLibrary(track) },
                onDownload = { vm.download(track) },
            )
        }
    }
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    // 歌手 Tab 的搜索词提到页面级：切 Tab 回来仍保留输入（AnimatedContent 会重建子树，
    // 放在子 composable 里的 remember 会被丢掉）。
    var artistQuery by rememberSaveable { mutableStateOf("") }

    var detailAlbum by remember { mutableStateOf<String?>(null) }
    var detailFolder by remember { mutableStateOf<String?>(null) }
    // 2026-11-16 用户指令：「歌手」不要点一下就直接播，和文件夹一样打开列表
    var detailArtist by rememberSaveable { mutableStateOf<String?>(null) }
    // 2026-11-15 第九轮：歌单改成 Tab 之后，详情由曲库根层当二级页打开
    var detailPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 2026-11-16 晚：右上角菜单新增「已移除歌曲」页
    var removedOpen by remember { mutableStateOf(false) }

    val sourceFilter by vm.sourceFilter.collectAsStateWithLifecycle()
    val filtered = remember(tracks, sourceFilter) {
        when (sourceFilter) {
            SourceFilter.ALL -> tracks
            SourceFilter.LOCAL -> tracks.filter { it.source == SourceType.LOCAL }
            // P5-04 修 bug：原来只筛 OPENLIST，WebDAV 曲目在「只看云端」下会整批消失。
            SourceFilter.CLOUD ->
                tracks.filter { it.source == SourceType.OPENLIST || it.source == SourceType.WEBDAV }
        }
    }
    // 2026-11-17：筛选气泡里要显示每档有多少首——选之前就知道会剩多少，
    // 避免选完才发现是空的（这是「点了没反应」投诉的另一半原因）。
    val sourceCounts = remember(tracks) {
        mapOf(
            SourceFilter.ALL to tracks.size,
            SourceFilter.LOCAL to tracks.count { it.source == SourceType.LOCAL },
            SourceFilter.CLOUD to tracks.count {
                it.source == SourceType.OPENLIST || it.source == SourceType.WEBDAV
            },
        )
    }
    // 2026-11-16 用户指令：音乐库标题下方那行「N 首 · 本机 N · 云端 N」删掉，
    // 标题只留「音乐库」，统计信息不再占一行（筛选后的条数在各列表的 SectionHeader 里）。

    // P7-11：二级页（歌单 / 专辑详情）iOS 式转场——主内容压缩渐暗，二级页右滑入。
    val secondary = when {
        removedOpen -> LibrarySecondary.RemovedTracks
        detailPlaylistId != null -> LibrarySecondary.PlaylistDetail(detailPlaylistId!!)
        detailAlbum != null -> LibrarySecondary.Album(detailAlbum!!)
        detailFolder != null -> LibrarySecondary.Folder(detailFolder!!)
        detailArtist != null -> LibrarySecondary.Artist(detailArtist!!)
        else -> null
    }
    val bgScale by animateFloatAsState(
        targetValue = if (secondary != null) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "libBgScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (secondary != null) 0.55f else 1f,
        animationSpec = tween(180),
        label = "libBgDim",
    )

    // 2026-11-17 深夜（五）用户指令：「返回键要能关闭软件，关闭前二次确认；逐级返回，
    // 音乐库·歌曲类目按返回弹窗询问（取消/确认），其它类目或页面逐级返回」。
    // 实现：二级页（歌单/专辑/文件夹/歌手/已移除）各自有 BackHandler，且在父层之后入栈，
    // 会优先消费返回；这里只兜住「音乐库主界面」——先在其它类目退回「歌曲」，
    // 已经在歌曲类目才弹「关闭软件？」。
    var exitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = secondary == null) {
        if (tab != 0) tab = 0 else exitConfirm = true
    }

    if (exitConfirm) {
        // 2026-11-17 定稿「玻璃确认卡」：图标 + 一句问句 + 两个按钮，无副文案。
        ExitConfirmDialog(
            onConfirm = {
                exitConfirm = false
                // 2026-11-17 深夜（五）用户指令「要的是完全关闭软件」：
                // 1) 给播放服务发 ACTION_SHUTDOWN（停播 + 清队列 + 摘通知 + stopSelf）；
                // 2) 关掉桌面悬浮歌词；
                // 3) 连任务一起移除。实测只 stopService 会被 Media3 拉回来，
                //    通知与媒体会话都还在，所以必须走显式指令。
                val appContext = context.applicationContext
                runCatching { DesktopLyricsService.stop(appContext) }
                runCatching {
                    appContext.startService(
                        Intent(appContext, PlaybackService::class.java)
                            .setAction(PlaybackService.ACTION_SHUTDOWN),
                    )
                }
                (context as? Activity)?.finishAndRemoveTask()
            },
            onDismiss = { exitConfirm = false },
        )
    }

    // P7-09：标题栏 + 分类 Tab 固定在顶部毛玻璃层，列表铺满全屏，
    // 用 contentPadding 让首项从固定头下方开始，滚动时会经过标题栏后方。
    // P7-11：modifier 上的压缩/渐暗在二级页打开时生效（背景压缩）。
    // 2026-11-15 BUGFIX（用户报「点开文件夹后整页雾蒙蒙」）：
    // 二级页 overlay 原先挂在 HazeTitleScaffold 的 content 里，而渐暗/缩放的
    // graphicsLayer 挂在整个 scaffold 上 → 二级页自己也被 alpha 0.55 压成半透明；
    // 同时顶部还残留主页面「音乐库」标题栏与分类 Tab。改为与设置页/歌单页相同的
    // 结构：根 Box = 主内容（可压缩渐暗）+ 二级页 overlay（不透明、全屏）。
    Box(Modifier.fillMaxSize()) {
        HazeTitleScaffold(
            modifier = Modifier.graphicsLayer {
                scaleX = bgScale
                scaleY = bgScale
                alpha = bgAlpha
            },
            title = "音乐库",
            subtitle = null,
            contentTopInset = HazeTitleHeight + 52.dp,
            actions = {
                // 2026-11-14：顶部随机按钮删掉（用户指定）；保留筛选与更多。
                // 2026-11-17（用户指令「本机 / 云端提示词位置移动到漏斗前面」）：
                // 筛选生效时把当前档位贴在漏斗左侧显示，读起来就是「漏斗：本机」，
                // 比原来挂在分类 Tab 下面那一行更容易和漏斗对应起来。
                if (sourceFilter != SourceFilter.ALL) {
                    Text(
                        text = when (sourceFilter) {
                            SourceFilter.LOCAL -> "本机"
                            SourceFilter.CLOUD -> "云端"
                            SourceFilter.ALL -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.BlueLight,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                Box {
                    IconAction(
                        // 2026-11-17：用户要求「沙漏图标换回漏斗」——回到 PlayerIcons.Filter。
                        icon = PlayerIcons.Filter,
                        contentDescription = "筛选来源",
                        onClick = { filterMenuOpen = true },
                        tint = if (sourceFilter == SourceFilter.ALL) Palette.TextSecondary else Palette.BlueLight,
                        container = if (sourceFilter == SourceFilter.ALL) Color.Transparent else Palette.BlueWash,
                    )
                    // 2026-11-17（用户指令「漏斗弹窗回滚到原始，弹窗中内容不要回滚」）：
                    // 容器回到原生 DropdownMenu（锚定漏斗、无全屏蒙层，点外面即收起），
                    // 内容保持现状——三档各带来源图标 + 条数，选中项蓝字加粗，
                    // 选之前就能看到会剩多少首。
                    DropdownMenu(
                        expanded = filterMenuOpen,
                        onDismissRequest = { filterMenuOpen = false },
                        containerColor = Palette.Surface2,
                        shape = Shapes.cardSmall,
                    ) {
                        SourceFilter.entries.forEach { filter ->
                            val label = when (filter) {
                                SourceFilter.ALL -> "全部来源"
                                SourceFilter.LOCAL -> "只看本机"
                                SourceFilter.CLOUD -> "只看云端"
                            }
                            val icon = when (filter) {
                                SourceFilter.ALL -> PlayerIcons.AllSources
                                SourceFilter.LOCAL -> PlayerIcons.Device
                                SourceFilter.CLOUD -> PlayerIcons.Cloud
                            }
                            val selected = sourceFilter == filter
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) Palette.BlueLight else Palette.TextPrimary,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) Palette.BlueLight else Palette.TextSecondary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        text = (sourceCounts[filter] ?: 0).toString(),
                                        fontSize = 9.5.sp,
                                        color = if (selected) Palette.BlueLight else Palette.TextTertiary,
                                    )
                                },
                                onClick = {
                                    vm.setSourceFilter(filter)
                                    filterMenuOpen = false
                                },
                            )
                        }
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
                        // 2026-11-16 晚用户需求：右上角菜单增加「已移除歌曲」（可恢复）。
                        DropdownMenuItem(
                            text = { Text("已移除歌曲") },
                            leadingIcon = {
                                Icon(PlayerIcons.RemoveFromLibrary, null, tint = Palette.TextSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                removedOpen = true
                            },
                        )
                        // 2026-11-15 第九轮（用户指令）：原来的「播放列表」入口改名「歌单」
                        // 并挪到「文件夹」类目前面（成了曲库第 4 个 Tab），这里只剩重扫。
                        // 重扫也要有反馈：点了立刻弹一条 toast，设置页里有实时进度。
                        DropdownMenuItem(
                            text = { Text("重新扫描曲库") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = Palette.TextSecondary) },
                            onClick = {
                                menuOpen = false
                                onRescan()
                                Toast.makeText(
                                    context,
                                    "已开始重新扫描曲库，进度可在设置页查看",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                }
            },
            pinnedHeader = {
                // 2026-11-17（用户指令「本机 / 云端提示词位置移动到漏斗前面」）：
                // 原来钉在 Tab 下面的那条状态条挪到标题栏、漏斗左侧，这里只剩分类 Tab。
                TabRow(selected = tab, onSelect = { tab = it })
            },
        ) { sourceModifier, topInset ->
            // 主内容常驻：Tab 切换（保留滚动位置与内部状态）。
            AnimatedContent(
                targetState = if (tracks.isEmpty()) -1 else tab,
                modifier = sourceModifier,
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
                        filter = sourceFilter,
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        positionMs = positionMs,
                        indexOf = { list, id -> vm.indexOf(list, id) },
                        onPlay = { list, index -> vm.play(list, index) },
                        menuFor = menuFor,
                    )
                    t == 1 -> ArtistList(
                        tracks = filtered,
                        query = artistQuery,
                        onQueryChange = { artistQuery = it },
                        // 2026-11-16 用户指令：点歌手不再直接播，改为打开该歌手的曲目列表
                        onOpen = { detailArtist = it },
                    )
                    t == 2 -> AlbumGrid(
                        tracks = filtered,
                        onOpen = { detailAlbum = it },
                    )
                    // 2026-11-15 第九轮：歌单是独立 Tab（排在文件夹之前），列表内嵌在这里
                    t == 3 -> PlaylistTab(
                        playlistVm = playlistVm,
                        topInset = topInset,
                        onOpenDetail = { detailPlaylistId = it.id },
                    )
                    else -> FolderList(
                        tracks = filtered,
                        cloudRoots = cloudRoots,
                        cloudNames = cloudNames,
                        localRoots = localRoots,
                        // 2026-11-15 BUGFIX（用户报「点文件夹条目怎么会自动播放歌曲」）：
                        // 原来是 onPlay(list, 0)，点一下就播。改成只打开该文件夹的曲目列表，
                        // 由用户在详情页里点某一首才播放。
                        onOpen = { detailFolder = it },
                    )
                }
            }
        }

        // P7-11：二级页 overlay——常驻主内容 + EnteringPage 右滑入 + 缩放收拢。
        // 必须是 scaffold 的兄弟节点：这样渐暗/缩放只作用于主内容，二级页保持全屏不透明。
        AnimatedContent(
            targetState = secondary,
            transitionSpec = { iosStackSpec(targetState != null) },
            label = "librarySecondary",
        ) { s ->
            when (s) {
                is LibrarySecondary.PlaylistDetail -> {
                    // 歌单 Tab 里点条目 → 详情当二级页右滑入（返回回到 Tab 列表）
                    val playlists = playlistVm.playlists.collectAsStateWithLifecycle().value
                    // 收藏是虚拟歌单（id = -1），列表里没有这条记录，这里现场合成
                    val favorites = playlistVm.favorites.collectAsStateWithLifecycle().value
                    val playlist = if (s.id == PlaylistViewModel.FAVORITES_ID) {
                        Playlist(
                            id = PlaylistViewModel.FAVORITES_ID,
                            name = PlaylistViewModel.FAVORITES_NAME,
                            trackCount = favorites.size,
                            coverUri = favorites.firstOrNull()?.coverUri,
                        )
                    } else {
                        playlists.firstOrNull { it.id == s.id }
                    }
                    if (playlist != null) {
                        EnteringPage {
                            PlaylistDetailPage(
                                playlist = playlist,
                                playlistVm = playlistVm,
                                allTracks = tracks,
                                currentTrackId = playback.currentTrackId,
                                isPlaying = playback.isPlaying,
                                positionMs = positionMs,
                                playMode = playback.playMode,
                                onPlay = { list, index -> vm.play(list, index) },
                                onToggle = { vm.toggle() },
                                onSetPlayMode = { vm.setPlayMode(it) },
                                onBack = { detailPlaylistId = null },
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
                is LibrarySecondary.Album -> EnteringPage {
                    AlbumDetailScreen(
                        albumName = s.name,
                        tracks = filtered.filter { (it.album ?: "未知专辑") == s.name },
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        positionMs = positionMs,
                        onBack = { detailAlbum = null },
                        onPlayAll = { list, index -> vm.play(list, index) },
                    )
                }
                is LibrarySecondary.Folder -> EnteringPage {
                    FolderDetailScreen(
                        folderPath = s.path,
                        cloudNames = cloudNames,
                        tracks = filtered.filter { folderOf(it, cloudRoots, localRoots) == s.path },
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        positionMs = positionMs,
                        onBack = { detailFolder = null },
                        onPlay = { list, index -> vm.play(list, index) },
                    )
                }
                is LibrarySecondary.Artist -> EnteringPage {
                    ArtistDetailScreen(
                        artistName = s.name,
                        tracks = filtered.filter { (it.artist ?: "未知艺术家") == s.name },
                        currentTrackId = playback.currentTrackId,
                        isPlaying = playback.isPlaying,
                        positionMs = positionMs,
                        onBack = { detailArtist = null },
                        onPlay = { list, index -> vm.play(list, index) },
                        menuFor = menuFor,
                    )
                }
                is LibrarySecondary.RemovedTracks -> EnteringPage {
                    RemovedTracksScreen(
                        playlistVm = playlistVm,
                        onBack = { removedOpen = false },
                    )
                }
                null -> Box(Modifier.fillMaxSize())
            }
        }
        // 2026-11-17（用户指令「漏斗弹窗回滚到原始」）：原来的全屏蒙层随「锚定气泡」
        // 一起撤掉——原生 DropdownMenu 自带窗口 + 点外面收起，不再压暗整屏。
    } // root Box
}

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

/**
 * 歌曲列表（2026-11-14 定稿：纯列表，无歌手轮播——歌手是独立 Tab，见 [ArtistList]）。
 *
 * 2026-11-15 用户指令：标题「全部歌曲」要跟着右上角筛选（漏斗图标）的选择变化，
 * 所以标题由 [filter] 推导：全部来源仍是「全部歌曲」，只看本机 / 只看云端各换名字。
 */
@Composable
private fun TrackList(
    tracks: List<Track>,
    filter: SourceFilter,
    currentTrackId: String?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    indexOf: (List<Track>, String) -> Int,
    onPlay: (List<Track>, Int) -> Unit,
    menuFor: (Track) -> TrackMenuActions = { TrackMenuActions() },
) {
    val title = when (filter) {
        SourceFilter.ALL -> "全部歌曲"
        SourceFilter.LOCAL -> "本机歌曲"
        SourceFilter.CLOUD -> "云端歌曲"
    }
    val subtitle = when (filter) {
        SourceFilter.ALL -> "本机 + 云端 · 共 ${tracks.size} 首"
        SourceFilter.LOCAL -> "只显示本机文件 · 共 ${tracks.size} 首"
        SourceFilter.CLOUD -> "OpenList / WebDAV · 共 ${tracks.size} 首"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = LocalHazeTopInset.current + 4.dp, bottom = LocalHazeBottomInset.current + 12.dp),
    ) {
        item {
            // 2026-11-14：删掉「N 首 · 点歌即播」提示词（用户指定）。
            SectionHeader(title = title, subtitle = subtitle)
        }
        items(items = tracks, key = { it.id }, contentType = { "track_row" }) { track ->
            // 只有正在放的那一行算出「剩余」，其余传 null——不参与每秒重组。
            val remaining = if (track.id == currentTrackId && track.durationMs > 0L) {
                (track.durationMs - positionMs).coerceAtLeast(0L)
            } else {
                null
            }
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onPlay(tracks, indexOf(tracks, track.id).coerceAtLeast(0)) },
                menu = menuFor(track),
                remainingMs = remaining,
            )
        }
    }
}

/**
 * 歌手类目（2026-11-15 第二轮，用户指令）：改成**列表**显示，并带搜索框。
 *
 * 版式照 primuse iOS `ArtistListView` 的 list 形态（chenqi92/primuse，
 * `Primuse/Views/Library/ArtistListView.swift`，AGPL-3.0-only，D26 允许）：
 * 每行 44dp 圆形头像 + 歌手名 + 「N 张专辑 · N 首」两行；顶部固定一个搜索框，
 * 按歌手名即时过滤（本地 filter，几万首也是毫秒级）。
 * 点行直接播该歌手的全部歌——沿用本页原有手感，不额外做歌手详情页。
 */
@Composable
private fun ArtistList(
    tracks: List<Track>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val artists = remember(tracks) {
        tracks.groupBy { it.artist ?: "未知艺术家" }
            .map { (name, list) ->
                ArtistUi(
                    name = name,
                    coverUri = list.firstNotNullOfOrNull { it.coverUri },
                    albumCount = list.map { it.album ?: "未知专辑" }.distinct().size,
                    songCount = list.size,
                )
            }
            // 未知艺术家沉底，其余按名字排序（列表形态下字母序比「按曲目数」更好找）。
            .sortedWith(
                compareBy<ArtistUi> { it.name == "未知艺术家" }
                    .thenBy { it.name.lowercase() },
            )
    }
    val visible = remember(artists, query) {
        val q = query.trim()
        if (q.isEmpty()) artists else artists.filter { it.name.contains(q, ignoreCase = true) }
    }

    if (artists.isEmpty()) {
        EmptyHint(title = "没有歌手", description = "扫描完成后，这里会按歌手归类。")
        return
    }

    Column(Modifier.fillMaxSize()) {
        ArtistSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "搜索歌手",
            modifier = Modifier.padding(top = LocalHazeTopInset.current + 4.dp),
        )

        if (visible.isEmpty()) {
            EmptyHint(
                title = "没有找到歌手",
                description = "换个关键词试试，搜索只匹配歌手名。",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dim.screen,
                end = Dim.screen,
                top = 6.dp,
                bottom = LocalHazeBottomInset.current + 20.dp,
            ),
        ) {
            items(items = visible, key = { it.name }, contentType = { "artist_row" }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(Shapes.cardSmall)
                        .clickable { onOpen(artist.name) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverThumb(
                        coverUri = artist.coverUri,
                        size = 44.dp,
                        shape = CircleShape,
                        iconSize = 19.dp,
                        seed = artist.name,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${artist.albumCount} 张专辑 · ${artist.songCount} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 歌手页顶部搜索框（与搜索页 SearchField 同一套视觉，行高收到 48dp 便于常驻）。 */
@Composable
private fun ArtistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 8.dp)
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
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = Palette.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
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

    if (albums.isEmpty()) {
        EmptyHint(title = "没有专辑", description = "扫描完成后，同一张专辑的歌会自动归类。")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 146.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dim.screen, end = Dim.screen, top = LocalHazeTopInset.current + 6.dp, bottom = LocalHazeBottomInset.current + 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(count = albums.size, key = { albums[it].name }, contentType = { "album_card" }) { index ->
            val album = albums[index]
            Column(Modifier.clickable { onOpen(album.name) }) {
                CoverSquare(
                    coverUri = album.coverUri,
                    shape = Shapes.coverGrid,
                    seed = album.artSeed(),
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

/** 歌手列表行数据（2026-11-15：列表形态需要专辑数与曲目数两列统计）。 */
private data class ArtistUi(
    val name: String,
    val coverUri: String?,
    val albumCount: Int,
    val songCount: Int,
)

private fun AlbumUi.artSeed(): String = name

/**
 * 曲目所属文件夹（与 [FolderList] / [FolderDetailScreen] 共用同一套切分规则）。
 *
 * 2026-11-16 晚用户需求「文件夹增加云端」：文件夹列表原来只按 `localPath` 归类，
 * 云端曲目 `localPath` 为空所以整批被过滤掉。现在云端曲目也能进文件夹：
 * 键 = `来源标签:目录`（本机 `local:…`、OpenList `openlist:…`、WebDAV `webdav:…`）。
 *
 * 2026-11-17（用户报「音乐库 文件夹 不要有那么多路径，显示设置界面填写的路径就行」）：
 * 本机原来按 `localPath` 的**上级目录**切分，`Music/周杰伦`、`Music/林俊杰`、`Music/专辑名`
 * 每个子目录一条，一屏全是路径。现在改成「归属哪个已授权音乐目录」：
 * [localRoots] 是设置页「添加音乐目录」还原出来的根路径（长的在前），命中就整条目录算一个文件夹。
 * 配不上任何已授权目录的曲目（比如整盘 MediaStore 扫出来的），兜底只取存储卷下的一级目录
 * （`Music` / `Download` / `msop-music`），不再往下一层层拆。
 */
private fun folderOf(
    track: Track,
    cloudRoots: Map<String, String>,
    localRoots: List<String> = emptyList(),
): String {
    val tag = when (track.source) {
        SourceType.LOCAL -> FOLDER_LOCAL
        SourceType.OPENLIST -> FOLDER_OPENLIST
        SourceType.WEBDAV -> FOLDER_WEBDAV
    }
    if (track.source != SourceType.LOCAL) {
        val profileId = remoteProfileId(track.uri)
        val key = "$tag:$profileId"
        // 2026-11-17 BUGFIX（用户报「文件夹 不要有那么多路径」）：配置里 rootPath 留空
        // 表示「整个网盘根目录」，原来用 `isNullOrBlank()` 判定，把「留空」当成「没配置」
        // 又回退成按 uri 子目录拆（OpenList 10614 首被拆成 /music/db/歌手 几十条）。
        // 改成「只要这个 profile 在配置里存在」，留空就归一到 `/`（一条「根目录」）。
        if (cloudRoots.containsKey(key)) {
            // 2026-11-17 深夜（续）：「路径支持填写多条（一行一条）」——配置里可能有多条目录，
            // 曲目按「最长命中的那条目录」归桶（单目录时与之前的 key 完全一致）。
            val roots = cloudRoots[key].orEmpty().lines()
                .map { normalizeCloudRoot(it) }
                .distinct()
                .ifEmpty { listOf("/") }
                val path = remotePathOf(track.uri)
                val matched = roots.sortedByDescending { it.length }.firstOrNull { root ->
                    root == "/" || path == root || path.startsWith("$root/")
                }
                return tag + FOLDER_KEY_SEP + (matched ?: roots.first())
        }
    }
    val dir = if (track.source == SourceType.LOCAL) {
        val path = track.localPath.orEmpty()
        when {
            path.isBlank() -> "/"
            // localRoots 已按长度降序，第一个命中即最近的那个音乐目录
            else -> localRoots.firstOrNull { path.startsWith(it) } ?: topBucketOf(path)
        }
    } else {
        val path = remotePathOf(track.uri)
        if (path.isBlank()) "/" else path.substringBeforeLast('/', missingDelimiterValue = "/")
    }
    return tag + FOLDER_KEY_SEP + dir
}

/**
 * 本机路径兜底：只保留「存储卷 + 一级目录」，例如
 * `/storage/emulated/0/Music/周杰伦/晴天.flac` → `/storage/emulated/0/Music`。
 * 不在 `/storage/` 下的路径（少见）回落到原来的上级目录。
 */
private fun topBucketOf(path: String): String {
    if (!path.startsWith("/storage/")) return path.substringBeforeLast('/', missingDelimiterValue = "/")
    val afterStorage = path.removePrefix("/storage/")
    val volume = afterStorage.substringBefore('/')
    val rest = afterStorage.substringAfter('/', "").trim('/')
    if (rest.isEmpty()) return "/storage/$volume"
    val bucket = rest.substringBefore('/')
    return if (bucket.isEmpty()) "/storage/$volume" else "/storage/$volume/$bucket"
}

/** `openlist:p2:/yd/musicweb/x.flac` → `p2`；取不到时返回空串。 */
private fun remoteProfileId(uri: String): String {
    val first = uri.indexOf(':')
    if (first < 0) return ""
    val second = uri.indexOf(':', first + 1)
    if (second < 0) return ""
    return uri.substring(first + 1, second)
}

/** 配置页的 rootPath 统一成「以 / 开头、不以 / 结尾」；空串与 `/` 都表示根目录。 */
private fun normalizeCloudRoot(root: String): String {
    val trimmed = root.trim()
    if (trimmed.isBlank() || trimmed == "/") return "/"
    val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return withLeading.trimEnd('/').ifBlank { "/" }
}

/** `openlist:p2:/yd/musicweb/x.flac` → `/yd/musicweb/x.flac`；取不到时返回空串。 */
private fun remotePathOf(uri: String): String {
    val first = uri.indexOf(':')
    if (first < 0 || first == uri.lastIndex) return ""
    val second = uri.indexOf(':', first + 1)
    if (second < 0 || second == uri.lastIndex) return ""
    return uri.substring(second + 1)
}

private const val FOLDER_KEY_SEP = ":"
private const val FOLDER_LOCAL = "local"
private const val FOLDER_OPENLIST = "openlist"
private const val FOLDER_WEBDAV = "webdav"

/** 文件夹键的展示拆解：来源标签 + 目录 + 目录显示名。 */
private fun folderLabel(
    key: String,
    cloudNames: Map<String, String> = emptyMap(),
): Triple<String, String, String> {
    val tag = key.substringBefore(FOLDER_KEY_SEP)
    val dir = key.substringAfter(FOLDER_KEY_SEP, "/").ifBlank { "/" }
    val sourceLabel = when (tag) {
        FOLDER_LOCAL -> "本机"
        FOLDER_OPENLIST -> "OpenList"
        FOLDER_WEBDAV -> "WebDAV"
        else -> tag
    }
    // 2026-11-17 深夜用户指令：「本机 内部存储」改成本机的**真实路径**；
    // OpenList / WebDAV 用**设置界面里填写的名称**做标题，而不是最后一段目录名。
    if (tag != FOLDER_LOCAL) {
        val configuredName = cloudNames[key]?.takeIf { it.isNotBlank() }
        val fallbackName = if (dir == "/") "根目录" else dir.trimEnd('/').substringAfterLast('/').ifBlank { dir }
        return Triple(configuredName ?: fallbackName, dir, sourceLabel)
    }
    val displayName = when {
        dir == "/" -> "根目录"
        // 存储卷根（`/storage/emulated/0`、`/storage/XXXX-XXXX`）没有末段可读，直接叫内部存储
        dir == "/storage/emulated/0" || dir.count { it == '/' } == 2 -> "内部存储"
        else -> dir.trimEnd('/').substringAfterLast('/').ifBlank { dir }
    }
    return Triple(displayName, dir, sourceLabel)
}

/**
 * 文件夹详情（2026-11-15 新增）：只列曲目，**点某一首才播放**。
 * 之前整个文件夹条目直接 `onPlay(list, 0)`，用户点目录名就被迫开始播放，已修掉。
 */
@Composable
private fun FolderDetailScreen(
    folderPath: String,
    cloudNames: Map<String, String>,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    BackHandler(onBack = onBack)
    val (name, dir, sourceLabel) = folderLabel(folderPath, cloudNames)
    HazeTitleScaffold(
        title = name,
        subtitle = "$sourceLabel · ${tracks.size} 首 · $dir",
        onBack = onBack,
        contentTopInset = HazeTitleHeight + 4.dp,
    ) { sourceModifier, topInset ->
        LazyColumn(
            modifier = sourceModifier,
            contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 20.dp),
        ) {
            item {
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.BlueLight,
                    modifier = Modifier
                        .padding(horizontal = Dim.screen, vertical = 12.dp)
                        .clip(Shapes.pill)
                        .background(Palette.BlueWash)
                        .clickable { if (tracks.isNotEmpty()) onPlay(tracks, 0) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
            items(items = tracks, key = { it.id }, contentType = { "track_row" }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { onPlay(tracks, tracks.indexOfFirst { it.id == track.id }) },
                    remainingMs = if (track.id == currentTrackId && track.durationMs > 0L) {
                        (track.durationMs - positionMs).coerceAtLeast(0L)
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * 歌手详情（2026-11-16 用户指令「歌手名不要点击直接播放，和文件夹一样打开列表」）：
 * 打开后只列该歌手的曲目，点某一首才播放；顶部保留「播放全部」。
 */
@Composable
private fun ArtistDetailScreen(
    artistName: String,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    menuFor: (Track) -> TrackMenuActions = { TrackMenuActions() },
) {
    BackHandler(onBack = onBack)
    val albumCount = remember(tracks) { tracks.map { it.album ?: "未知专辑" }.distinct().size }
    HazeTitleScaffold(
        title = artistName,
        subtitle = "歌手 · $albumCount 张专辑 · ${tracks.size} 首",
        onBack = onBack,
        contentTopInset = HazeTitleHeight + 4.dp,
    ) { sourceModifier, topInset ->
        LazyColumn(
            modifier = sourceModifier,
            contentPadding = PaddingValues(top = topInset, bottom = LocalHazeBottomInset.current + 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = Dim.screen, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverThumb(
                        coverUri = tracks.firstNotNullOfOrNull { it.coverUri },
                        size = 64.dp,
                        shape = CircleShape,
                        seed = artistName,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "播放全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.BlueLight,
                        modifier = Modifier
                            .clip(Shapes.pill)
                            .background(Palette.BlueWash)
                            .clickable { if (tracks.isNotEmpty()) onPlay(tracks, 0) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            items(items = tracks, key = { it.id }, contentType = { "track_row" }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { onPlay(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                    menu = menuFor(track),
                    remainingMs = if (track.id == currentTrackId && track.durationMs > 0L) {
                        (track.durationMs - positionMs).coerceAtLeast(0L)
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderList(
    tracks: List<Track>,
    cloudRoots: Map<String, String>,
    cloudNames: Map<String, String>,
    localRoots: List<String>,
    onOpen: (String) -> Unit,
) {
    val folders = remember(tracks, cloudRoots, localRoots) {
        // 2026-11-16 晚用户需求「文件夹增加云端」：不再按 localPath 过滤，本机 + 云端一起归类
        // 2026-11-16 第十四轮：云端按配置页 rootPath 聚合（一个音源一条），见 folderOf
        // 2026-11-17：本机按「设置里添加的音乐目录」聚合，一个目录一条
        tracks.groupBy { folderOf(it, cloudRoots, localRoots) }
            .toList()
            .sortedWith(compareBy({ it.first.substringBefore(FOLDER_KEY_SEP) }, { folderLabel(it.first).second }))
    }

    if (folders.isEmpty()) {
        EmptyHint(title = "没有文件夹信息", description = "扫描本机或同步云端音源之后，这里会按目录归类。")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dim.screen, end = Dim.screen, top = LocalHazeTopInset.current + 4.dp, bottom = LocalHazeBottomInset.current + 20.dp),
    ) {
        items(items = folders, key = { it.first }, contentType = { "folder_row" }) { (folder, list) ->
            val (name, dir, sourceLabel) = folderLabel(folder, cloudNames)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { onOpen(folder) }
                    .padding(horizontal = Dim.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = com.msop.player.ui.icons.PlayerIcons.Folder,
                    contentDescription = null,
                    tint = if (folder.startsWith(FOLDER_LOCAL)) Palette.TextTertiary else Palette.BlueLight,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$sourceLabel · $dir",
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



