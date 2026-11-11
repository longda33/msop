package com.primuse.lite.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.primuse.lite.core.model.Track
import com.primuse.lite.ui.components.MiniPlayer
import com.primuse.lite.ui.icons.PlayerIcons
import com.primuse.lite.ui.library.LibraryScreen
import com.primuse.lite.ui.library.LibraryViewModel
import com.primuse.lite.ui.player.PlayerSheet
import com.primuse.lite.ui.player.QueueSheet
import com.primuse.lite.ui.search.SearchScreen
import com.primuse.lite.ui.settings.SettingsScreen
import com.primuse.lite.ui.theme.Palette
import kotlinx.coroutines.delay

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    TabItem("library", "音乐库", Icons.AutoMirrored.Filled.List),
    TabItem("search", "搜索", Icons.Filled.Search),
    TabItem("settings", "设置", Icons.Filled.Settings),
)

/**
 * 应用外壳：三 Tab + 常驻迷你播放条 + 全屏播放页浮层。
 *
 * 播放页是 overlay 而不是导航目的地——这样返回键只收起它，
 * 不会把用户从播放页弹回列表顶部（Android 上这个细节很容易做错）。
 */
@Composable
fun PrimuseApp(
    viewModel: LibraryViewModel,
    directoryCount: Int = 0,
    onRescan: () -> Unit = {},
    onDirectoryPicked: (Uri) -> Unit = {},
    onWarmCovers: () -> Unit = {},
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()

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

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Scaffold(
            containerColor = Palette.Ink,
            bottomBar = {
                Column {
                    if (currentTrack != null) {
                        MiniPlayer(
                            title = currentTrack.title,
                            subtitle = listOfNotNull(currentTrack.artist, currentTrack.album)
                                .joinToString(" · ")
                                .ifBlank { "—" },
                            coverUri = currentTrack.coverUri,
                            isPlaying = playback.isPlaying,
                            progress = if (playback.durationMs > 0) {
                                (positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onExpand = { playerOpen = true },
                            onToggle = { viewModel.toggle() },
                        )
                    }
                    BottomNav(
                        isActive = { route ->
                            currentDestination?.hierarchy?.any { it.route == route } == true
                        },
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "library",
                modifier = Modifier.padding(innerPadding),
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
                        directoryCount = directoryCount,
                        onAddDirectory = { treePicker.launch(null) },
                        onRescan = onRescan,
                        onWarmCovers = onWarmCovers,
                    )
                }
            }
        }

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
                onCycleMode = { viewModel.cycleMode() },
                onOpenQueue = { queueOpen = true },
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
            )
        }
    }
}

@Composable
private fun BottomNav(
    isActive: (String) -> Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { tab ->
            val active = isActive(tab.route)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) Palette.BlueWash else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Palette.BlueLight else Palette.TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Palette.BlueLight else Palette.TextTertiary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
