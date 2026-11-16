package com.msop.player.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.ui.components.AmbientBackground
import com.msop.player.ui.components.LiquidGlassNavBar
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.LocalLayerBackdrop
import com.msop.player.ui.components.LocalSharedHazeState
import com.msop.player.ui.components.DownloadTaskCard
import com.msop.player.ui.components.MiniPlayer
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.theme.ThemeMode
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.library.LibraryScreen
import com.msop.player.ui.library.LibraryViewModel
import com.msop.player.ui.player.PlayerSheet
import com.msop.player.ui.player.QueueSheet
import com.msop.player.ui.search.SearchScreen
import com.msop.player.ui.settings.SettingsScreen
import com.msop.player.ui.theme.Dim
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    TabItem("library", "音乐库", PlayerIcons.Disc),
    TabItem("search", "搜索", Icons.Filled.Search),
    TabItem("settings", "设置", Icons.Filled.Settings),
)

/**
 * 应用外壳：极光背景 + 全屏内容（唯一 Haze 源 + LayerBackdrop 源）+
 * 底部液态玻璃浮层（P7-10，Kyant0 Backdrop）+ 全屏播放页浮层。
 *
 * 播放页是 overlay 而不是导航目的地——返回键只收起它，
 * 不会把用户从播放页弹回列表顶部（Android 上这个细节很容易做错）。
 *
 * P7-10 结构：不再用 Scaffold 的 bottomBar 裁剪内容，列表铺满全屏、
 * 从底部胶囊后方经过被液态玻璃实时采样；各屏列表用 [LocalHazeBottomInset]
 * 给最后一项留白。标题栏毛玻璃走 Haze（共享 HazeState），底部胶囊/迷你条走
 * Kyant0 LayerBackdrop（共享 [LocalLayerBackdrop]）——两套机制互不干扰。
 *
 * 2026-11-14：[themeMode] 由 MainActivity 持有，设置页通过 [onThemeChange]
 * 回调改动后立即全局生效（含深/浅/跟随系统三态）。
 */
@Composable
fun MsopApp(
    viewModel: LibraryViewModel,
    directories: List<String> = emptyList(),
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeChange: (ThemeMode) -> Unit = {},
    onRescan: () -> Unit = {},
    onDirectoryPicked: (Uri) -> Unit = {},
    onRemoveDirectory: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    // 2026-11-16 晚：下载进度卡片（用户报「点击下载 反馈没有 进度也没有」）
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }

    val currentTrack: Track? = remember(queue, tracks, playback.currentTrackId) {
        queue.firstOrNull { it.id == playback.currentTrackId }
            ?: tracks.firstOrNull { it.id == playback.currentTrackId }
    }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // 必须立刻取持久授权，否则重启后这个 tree URI 就失效了
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onDirectoryPicked(uri)
        }
    }

    // 播放中每秒拉一次位置，迷你条与播放页共用。暂停时自动停，不空转。
    LaunchedEffect(playback.isPlaying) {
        while (playback.isPlaying) {
            viewModel.refreshPosition()
            delay(1_000)
        }
    }

    // P7-02：冷启动把上次的队列/当前曲/进度恢复出来，不自动播放。
    LaunchedEffect(Unit) {
        viewModel.restoreSession()
    }

    // P7-10：共享 Haze 源——全屏内容注册一次，顶部标题栏与底部胶囊都从它采样。
    // 2026-11-14：底部液态玻璃改走 Kyant0 LayerBackdrop（与 ShuYin 同源库）。
    val sharedHazeState = rememberHazeState()
    // 2026-11-14 修正「玻璃后有一块颜色不一样的横带」：此前在 LayerBackdrop 里
    // drawRect(兜底色) 画了一层实心底，结果导航胶囊下方的手势区、两块玻璃之间的
    // 缝隙采到的都是这块实色而不是页面背景，看起来就是一条异色带。ShuYin 原版
    // backdrop 没有这层；页面本身有 AmbientBackground 全屏不透明底，删掉即可。
    val layerBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 底部浮层实际占住的高度（供各屏列表的 contentPadding.bottom 让位）：
    // 迷你条（有当前曲时 64dp + 上下 padding 12dp）+ 导航胶囊（62dp + 上下 padding 16dp）
    // + 手势条 + 悬浮间距 Dim.navBottomGap。浮层上移多少，列表留白就加多少，
    // 否则列表最后一项会被上移后的胶囊压住。
    val miniArea = if (currentTrack != null) Dim.miniHeight + 12.dp else 0.dp
    val bottomInset = navBarInset + Dim.navBottomGap + miniArea + Dim.navHeight + 16.dp

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()

        CompositionLocalProvider(
            LocalSharedHazeState provides sharedHazeState,
            LocalLayerBackdrop provides layerBackdrop,
            LocalHazeBottomInset provides bottomInset,
        ) {
            // 全屏内容（LayerBackdrop 源）：顶部标题栏已按 2026-11-14 用户指令改回
            // 完全透明、以占位方式排在内容上方；底部胶囊/迷你条仍从 LayerBackdrop
            // 采样液态玻璃。hazeSource 是 P7-09 遗留，当前无消费者，保留不影响渲染。
            // 只让状态栏，内容一直铺到屏幕底部（底部由各屏的 bottomInset 接管）。
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .hazeSource(sharedHazeState)
                    .layerBackdrop(layerBackdrop),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(240)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(240)) },
                    popExitTransition = { fadeOut(tween(160)) },
                ) {
                    composable("library") {
                        LibraryScreen(
                            vm = viewModel,
                            onRescan = onRescan,
                        )
                    }
                    composable("search") {
                        SearchScreen(vm = viewModel)
                    }
                    composable("settings") {
                        SettingsScreen(
                            directories = directories,
                            themeMode = themeMode,
                            onThemeChange = onThemeChange,
                            onAddDirectory = { treePicker.launch(null) },
                            onRemoveDirectory = onRemoveDirectory,
                            onRescan = onRescan,
                        )
                    }
                }
            }

            // 2026-11-14 定稿：底部浮层直接移植 ShuYin 原代码——迷你条（MiniPlayerBar
            // 移植版）+ 导航栏（LiquidGlassNavBar 移植版，含拖拽滑块/按压光斑/果冻拉伸）。
            // 玻璃垫色随主题（浅 FAFAFA / 深 121212）、字色纯黑/纯白，浅色不发黑、字看得清。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // 2026-11-14：胶囊不再贴死屏幕底部，留出一段悬浮间距；
                    // 与 LocalHazeBottomInset 里的 Dim.navBottomGap 同步。
                    .padding(top = 2.dp, bottom = Dim.navBottomGap),
            ) {
                // 下载任务卡压在迷你条上方（不占列表留白，浮在内容上）
                downloads.forEach { task ->
                    DownloadTaskCard(
                        task = task,
                        onDismiss = { viewModel.dismissDownload(task.trackId) },
                    )
                }
                // 迷你条的玻璃投影 `Shadow(radius = 20.dp, alpha = .8)` 是画在自身布局框
                // 之外的（向上也扩散），12dp 实测still盖住卡片底的进度条，留 22dp 才干净。
                if (downloads.isNotEmpty() && currentTrack != null) {
                    Spacer(Modifier.height(22.dp))
                }
                if (currentTrack != null) {
                    MiniPlayer(
                        title = currentTrack.title,
                        subtitle = listOfNotNull(currentTrack.artist, currentTrack.album)
                            .joinToString(" · ")
                            .ifBlank { "—" },
                        coverUri = currentTrack.coverUri,
                        seed = currentTrack.artSeed(),
                        isPlaying = playback.isPlaying,
                        positionMs = positionMs,
                        durationMs = playback.durationMs,
                        onExpand = { playerOpen = true },
                        onToggle = { viewModel.toggle() },
                        statusText = if (playback.networkLost) "网络已断开，播放已暂停" else null,
                    )
                    // 2026-11-14（用户指令）：迷你条与导航栏的上下间距——ShuYin 实拍
                    // 参考约 8dp 的明确间隙；此前 2dp 视觉上贴死，恢复到 8dp。
                    Spacer(Modifier.height(8.dp))
                }
                LiquidGlassNavBar(
                    backdrop = layerBackdrop,
                    tabs = TABS.map { it.icon to it.label },
                    selectedTabIndex = {
                        TABS.indexOfFirst { tab ->
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        }.coerceAtLeast(0)
                    },
                    onTabSelected = { index ->
                        navController.navigate(TABS[index].route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }

        // 2026-11-15：删掉此前用来「盖住曲库 Aurora 渐变」的 Void 实底。
        // 播放页已改成「背景固定、只有内容跟手下移」，下滑时不会再露出曲库，
        // 这层实底既没必要，又会在收起动画里闪一下深色。
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            PlayerSheet(
                state = playback,
                positionMs = positionMs,
                track = currentTrack,
                onCollapse = { playerOpen = false },
                onToggle = { viewModel.toggle() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeek = { viewModel.seekTo(it) },
                onSelectMode = { viewModel.setPlayMode(it) },
                onOpenQueue = { queueOpen = true },
                isFavorite = currentTrack?.id?.let { it in favorites } ?: false,
                onToggleFavorite = { currentTrack?.id?.let(viewModel::toggleFavorite) },
                // 2026-11-16 晚用户需求：播放页顶栏三点菜单的「下载歌曲」
                onDownload = currentTrack?.takeIf { it.source != SourceType.LOCAL }?.let { track ->
                    { viewModel.download(track) }
                },
            )
        }

        if (queueOpen) {
            QueueSheet(
                queue = queue,
                currentTrackId = playback.currentTrackId,
                onDismiss = { queueOpen = false },
                onSelect = { index ->
                    viewModel.play(queue, index)
                    queueOpen = false
                },
                onMove = { from, to -> viewModel.moveInQueue(from, to) },
            )
        }
    }
}



