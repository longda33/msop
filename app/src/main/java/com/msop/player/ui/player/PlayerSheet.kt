package com.msop.player.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.msop.player.core.model.Track
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.toDurationLabel
import com.msop.player.feature.lyrics.Lyrics
import com.msop.player.feature.player.PlayMode
import com.msop.player.feature.player.PlaybackState
import com.msop.player.ui.components.CircleGlassButton
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.GlassPill
import com.msop.player.ui.components.PrimaryPill
import com.msop.player.ui.components.TrackInfoDialog
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.GeneratedArtwork
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.LocalMsopPalette
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全屏播放页。用 overlay 而不是路由：返回键只收起，不进返回栈，
 * 也就不会出现「从播放页返回却回到列表顶部」这种断裂。
 *
 * 视觉：封面是唯一主角，背景用同一张封面的重度模糊 + 多层压暗，
 * 没有封面时用程序化极光封面兜底，保证任何曲目都有颜色。
 */
@Composable
fun PlayerSheet(
    state: PlaybackState,
    positionMs: Long,
    track: Track?,
    onCollapse: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectMode: (PlayMode) -> Unit,
    onOpenQueue: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    /** 播放页三点菜单的「下载歌曲」；云端曲目才显示，本机曲目传 null。 */
    onDownload: (() -> Unit)? = null,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    // 播放模式扇形菜单：开关 + 按钮上报的锚点（root 坐标 px）
    var modeMenuOpen by remember { mutableStateOf(false) }
    var modeAnchor by remember { mutableStateOf(Offset.Zero) }

    BackHandler(enabled = modeMenuOpen) { modeMenuOpen = false }
    BackHandler(enabled = !modeMenuOpen, onBack = onCollapse)

    var showLyrics by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 2026-11-16 用户指令：「歌词横屏不息屏」——横屏全屏歌词相当于看视频，
    // 只在横屏时挂 FLAG_KEEP_SCREEN_ON，退出横屏立刻恢复系统息屏策略。
    if (isLandscape) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.context as? android.app.Activity)?.window
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val duration = if (state.durationMs > 0) state.durationMs else (track?.durationMs ?: 0L)
    var dragValue by remember { mutableFloatStateOf(-1f) }
    val liveFraction = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fraction = if (dragValue >= 0f) dragValue else liveFraction

    // 2026-11-15 BUGFIX（用户报「拖动进度条后切下一首，进度条还停在上一首拖到的位置」）：
    // `dragValue` 是「拖动预览值」，只有 -1 时才回落到真实播放位置。切歌时若它还留着
    // 上一首的拖动值，进度条就整个卡住不动。这里按 track.id 强制归位；
    // 同时 SeekBar 抬手分支改成「无条件提交」，让 onCommit 里的归位一定执行。
    LaunchedEffect(track?.id) { dragValue = -1f }

    // 定时关闭：sleepEndsAt 为 0 表示未开启
    var sleepEndsAt by remember { mutableLongStateOf(0L) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playingNow by rememberUpdatedState(state.isPlaying)
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt > 0L) {
            while (true) {
                nowMs = System.currentTimeMillis()
                if (nowMs >= sleepEndsAt) {
                    sleepEndsAt = 0L
                    if (playingNow) onToggle()
                    break
                }
                delay(1_000)
            }
        }
    }
    val sleepRemainingMin = if (sleepEndsAt > 0L) {
        (((sleepEndsAt - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
    } else {
        0
    }
    val sleepLabel = if (sleepRemainingMin > 0) "${sleepRemainingMin}分" else "定时"
    val sleepActive = sleepRemainingMin > 0

    if (sleepDialogOpen) {
        SleepDialog(
            sleepRemainingMin = sleepRemainingMin,
            onDismiss = { sleepDialogOpen = false },
            onPick = { minutes ->
                sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
                sleepDialogOpen = false
            },
            onClear = {
                sleepEndsAt = 0L
                sleepDialogOpen = false
            },
        )
    }

    // 下滑收起：内容跟手移动，松手后决定「回去」还是「关掉」。
    // 2026-11-15（用户指令）：删掉「上滑打开队列」——上滑不再有任何动作，
    // 队列只从底部 dock 的「队列」按钮进入。
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // 手势只挂在「封面区 / 顶部栏 / 歌词头部」这些非滚动区域。
    // 挂在根布局会和歌词 LazyColumn 的滚动抢事件——实测会把页面误收起。
    val dragModifier = Modifier.pointerInput(screenHeightPx) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                // 只跟手下滑；上滑钳在 0（不位移、不触发任何行为）。
                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragEnd = {
                scope.launch {
                    if (offsetY > 120.dp.toPx()) {
                        animate(offsetY, screenHeightPx, animationSpec = tween(180)) { value, _ ->
                            offsetY = value
                        }
                        onCollapse()
                    } else {
                        animate(
                            offsetY,
                            0f,
                            animationSpec = spring(stiffness = 900f),
                        ) { value, _ -> offsetY = value }
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    animate(offsetY, 0f, animationSpec = spring(stiffness = 900f)) { value, _ ->
                        offsetY = value
                    }
                }
            },
        )
    }

    // 2026-11-15（用户指令，第二次修正）：**整页一起下滑，后面正常露出曲库**。
    // 「顶部异常色块」的真正成因（本轮实测确认）：`PlayerBackdrop` 里那张模糊封面
    // 被 `graphicsLayer scale 1.18` 放大，绘制会溢出本节点上下各 ~9% 屏高（1600px 屏约
    // 144px），而遮罩只铺面板自身范围——于是面板顶边之上露出 100px 左右**未经遮罩压暗**
    // 的亮色模糊带，与面板顶形成硬边。修法：根 Box 加 `clipToBounds()`，把溢出裁掉。
    //
    // 2026-11-16（用户指令「显示效果要的是液态玻璃效果」）：遮罩压得比上轮轻一档，
    // 让模糊封面的颜色透出来（上轮浅色主题 0.40→0.80 的白罩把整页洗成了灰白，
    // 看起来就是一块「背景框」而不是玻璃）。现在 0.20→0.62，底部才收到主题底色，
    // 字仍然读得清。
    val darkTheme = LocalMsopPalette.current.isDark
    val scrimTop = if (darkTheme) Color(0x40000000) else Color(0x33FFFFFF)
    val scrimMid = if (darkTheme) Color(0xB3000000) else Color(0x9E_F4F2FB)
    val scrimBottom = if (darkTheme) Color(0xE607080D) else Color(0xF2F4F2FB)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .clipToBounds(),
    ) {
        PlayerBackdrop(track)
        // 2026-11-14 深浅色：遮罩色随主题（深色压暗 / 浅色提亮均保字可读）。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scrimTop,
                            0.45f to scrimMid,
                            1f to scrimBottom,
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // 2026-11-15 第五轮（用户指令「歌词界面恢复成原始 msop」）：歌词页顶部信息
            // 与播放页共用同一套顶栏——原版就是「顶栏 + 歌词头部 + 控制条」三层常驻，
            // 不再为歌词页单独裁掉顶栏与缓冲提示。
            if (!isLandscape) {
                PlayerTopBar(
                    onCollapse = onCollapse,
                    state = state,
                    hasTrack = track != null,
                    dragModifier = dragModifier,
                    track = track,
                    onDownload = onDownload,
                )

                PlaybackNotice(
                    isBuffering = state.isBuffering,
                    networkLost = state.networkLost,
                )
            }

            if (isLandscape) {
                // 2026-11-16 用户指令：横屏完全按 music-web-cf-main 横屏全屏歌词排
                // （附件截图 `Screenshot_20261115_190337` / `_192650`）：
                // 左列封面 + 歌名 + 歌手 + 液态玻璃迷你控制卡，右列工具条 + 左对齐歌词。
                LandscapeLyricsPane(
                    track = track,
                    positionMs = positionMs,
                    durationMs = duration,
                    isPlaying = state.isPlaying,
                    vm = lyricsViewModel,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                // 2026-11-15（用户指令，第二轮）：封面 ↔ 歌词改成 **HorizontalPager 跟手翻页**。
                // 旧实现用 detectHorizontalDragGestures 累计位移，松手后直接切 if 分支 →
                // 松手瞬间硬切，没有任何过渡（用户报「封面滑动到歌词没有动画」）。
                //
                // 形态参考：YouTube Music / OuterTune 播放页的左右翻页
                // （OuterTune `ui/player/Player.kt`，GPL-3.0；2026-11-15 D26 起 GPL 不再设限，
                //  但它用 AnimatedContent 做「点按展开」而非跟手翻页，不符合本页需求，
                //  所以这里用 Compose 官方 foundation pager 实现）。
                val pagerState = rememberPagerState(initialPage = if (showLyrics) 1 else 0) { 2 }

                // 翻页落定 → 同步给底部 dock 的「封面/歌词」按钮态。
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { showLyrics = it == 1 }
                }
                // 点按钮 → 翻页（用户拖动过程中不打断）。
                LaunchedEffect(showLyrics) {
                    val target = if (showLyrics) 1 else 0
                    if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
                        pagerState.animateScrollToPage(target)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    // 轻微视差：翻页时页面缩到 0.96，跟手时就有「两层纸在错动」的观感。
                    val parallax = Modifier.graphicsLayer {
                        val p = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val s = 1f - (abs(p) * 0.04f).coerceIn(0f, 0.05f)
                        scaleX = s
                        scaleY = s
                    }
                    if (page == 0) {
                        // 竖直收起手势只挂在封面页（歌词页由 LazyColumn 自己消费竖直滚动，
                        // 外层再挂会抢事件、把页面误收起——2026-11-15 实测过）。
                        Box(Modifier.fillMaxSize().then(parallax).then(dragModifier)) {
                            CoverStage(track = track, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        // 2026-11-15 第五轮：歌词页恢复 msop 原版形态——页面内只有
                        // 「封面+歌名+喜欢」头部与居中歌词；进度条与控制键统一由下方
                        // PlayerTransport 承担（原版就是这样一套常驻控制条）。
                        LyricsPage(
                            track = track,
                            positionMs = positionMs,
                            vm = lyricsViewModel,
                            dragModifier = dragModifier,
                            modifier = Modifier.fillMaxSize().then(parallax),
                        )
                    }
                }

                // 2026-11-15 第五轮（恢复原版）：进度条 + 控制键 + 底部 dock 在
                // 「封面 / 歌词」两页都常驻；dock 里的「封面 / 歌词」按钮负责翻页。
                PlayerTransport(
                    state = state,
                    duration = duration,
                    fraction = fraction,
                    onFractionChange = { dragValue = it },
                    onCommit = { f ->
                        onSeek((f.coerceIn(0f, 1f) * duration).toLong())
                        dragValue = -1f
                    },
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onOpenModeMenu = { modeAnchor = it; modeMenuOpen = true },
                    onOpenQueue = onOpenQueue,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    sleepLabel = sleepLabel,
                    sleepActive = sleepActive,
                    onSleepTimer = { sleepDialogOpen = true },
                    showLyricsToggle = true,
                    lyricsSelected = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    compact = false,
                )
            }
        }

        // 2026-11-15（用户指令）：播放模式扇形菜单——挂在页面内的全屏覆盖层里，
        // 锚点用模式按钮自己上报的 root 坐标。点空白处 / 返回键关闭。
        if (modeMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) {
                        detectTapGestures { modeMenuOpen = false }
                    },
            ) {
                PlayModeFan(
                    options = playModeOptions.filter { it.mode != state.playMode },
                    anchor = modeAnchor,
                    onPick = { picked ->
                        onSelectMode(picked)
                        modeMenuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerBackdrop(track: Track?) {
    val seed = track?.artSeed() ?: "msop"
    Box(Modifier.fillMaxSize().background(Palette.Void)) {
        if (track?.coverUri != null) {
            AsyncImage(
                model = track.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .blur(72.dp),
            )
        } else {
            GeneratedArtwork(
                seed = seed,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .blur(66.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Palette.Blue.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 1200f,
                    ),
                ),
        )
    }
}

@Composable
private fun PlayerTopBar(
    onCollapse: () -> Unit,
    state: PlaybackState,
    hasTrack: Boolean,
    dragModifier: Modifier,
    track: Track?,
    onDownload: (() -> Unit)?,
) {
    // 2026-11-16 用户指令：「顶部标题随播放状态变化，不再是固定的『正在播放』」。
    var topMenuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    val statusText = when {
        !hasTrack -> "未在播放"
        state.networkLost -> "网络已断开"
        state.isBuffering -> "缓冲中"
        state.isPlaying -> "正在播放"
        else -> "已暂停"
    }
    val dotColor = when {
        !hasTrack -> Palette.TextTertiary
        state.networkLost -> Palette.Gold
        state.isBuffering -> Palette.BlueLight
        state.isPlaying -> Palette.Teal
        else -> Palette.TextTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(dragModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleGlassButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "收起",
            onClick = onCollapse,
            size = 42.dp,
            iconSize = 24.dp,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                letterSpacing = 1.sp,
            )
        }
        // 2026-11-16 用户指令：歌词页头部那颗三点删掉，改成播放页顶栏右上角这一颗——
        // 封面页与歌词页共用同一个菜单（收藏 / 重新加载歌词）。
        // 2026-11-16 晚（本轮 12 条第 6 条）：删掉「收藏」，改为「歌曲信息」「下载歌曲」。
        Box(Modifier.size(42.dp)) {
            CircleGlassButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                onClick = { topMenuOpen = true },
                size = 42.dp,
                iconSize = 20.dp,
            )
            DropdownMenu(
                expanded = topMenuOpen,
                onDismissRequest = { topMenuOpen = false },
                containerColor = Palette.Surface2,
                shape = Shapes.cardSmall,
                offset = DpOffset(x = (-6).dp, y = 0.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("歌曲信息") },
                    leadingIcon = {
                        Icon(Icons.Filled.Info, null, tint = Palette.TextSecondary)
                    },
                    enabled = track != null,
                    onClick = {
                        topMenuOpen = false
                        infoOpen = true
                    },
                )
                // 只有云端曲目给 onDownload（本机文件已经在本地，不需要下载）
                if (track != null && track.source != SourceType.LOCAL) {
                    onDownload?.let { download ->
                        DropdownMenuItem(
                            text = { Text("下载歌曲") },
                            leadingIcon = {
                                Icon(PlayerIcons.Download, null, tint = Palette.TextSecondary)
                            },
                            onClick = {
                                topMenuOpen = false
                                download()
                            },
                        )
                    }
                }
            }
        }
    }

    if (infoOpen) {
        val current = track
        if (current != null) {
            TrackInfoDialog(track = current, onDismiss = { infoOpen = false })
        } else {
            infoOpen = false
        }
    }
}

/**
 * 缓冲 / 断网提示（P7-03）。
 *
 * 缓冲提示延迟 1.2 秒出现：切歌和 seek 都会短暂进入 BUFFERING，立刻弹提示会闪。
 * 超过 15 秒改文案为「网络较慢」，断网提示优先于缓冲提示。
 */
@Composable
private fun PlaybackNotice(
    isBuffering: Boolean,
    networkLost: Boolean,
    modifier: Modifier = Modifier,
) {
    var showBuffering by remember { mutableStateOf(false) }
    var slowNetwork by remember { mutableStateOf(false) }

    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(1_200)
            showBuffering = true
            delay(13_800)
            slowNetwork = true
        } else {
            showBuffering = false
            slowNetwork = false
        }
    }

    AnimatedVisibility(
        visible = networkLost || showBuffering,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .glassSurface(Shapes.pill, Palette.Surface, elevation = 8.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!networkLost) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = Palette.BlueLight,
                        strokeWidth = 1.8.dp,
                    )
                }
                Text(
                    text = when {
                        networkLost -> "网络已断开，播放已暂停"
                        slowNetwork -> "网络较慢，仍在缓冲…"
                        else -> "缓冲中…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (networkLost) Palette.Gold else Palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CoverStage(
    track: Track?,

    modifier: Modifier = Modifier,
) {    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val stageWidth = maxWidth
        val stageHeight = maxHeight
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val coverSize = minOf(
                stageWidth - 64.dp,
                stageHeight - 118.dp,
                380.dp,
            ).coerceAtLeast(120.dp)
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .shadow(
                        elevation = 34.dp,
                        shape = Shapes.coverStage,
                        spotColor = Palette.Blue.copy(alpha = 0.55f),
                        ambientColor = Color.Black,
                    )

            ) {
                CoverThumb(
                    coverUri = track?.coverUri,
                    size = null,
                    shape = Shapes.coverStage,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 64.dp,
                    seed = track?.artSeed() ?: "msop",
                )
            }

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = track?.title ?: "未在播放",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = listOfNotNull(track?.artist, track?.album)
                        .joinToString(" · ")
                        .ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPill(
                        label = if (track != null && track.source.name != "LOCAL") "云端" else "本地",
                        color = Palette.TextSecondary,
                        background = Color.White.copy(alpha = 0.06f),
                    )
                    track?.mimeType?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(6.dp))
                        GlassPill(
                            label = it.uppercase(),
                            color = Palette.TextTertiary,
                            background = Color.White.copy(alpha = 0.04f),
                        )
                    }
                    // 2026-11-14：删掉「点封面看歌词」提示词（用户指定）。
                }
            }
        }
    }
}

@Composable
private fun PlayerTransport(
    state: PlaybackState,
    duration: Long,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenModeMenu: (Offset) -> Unit,
    onOpenQueue: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    sleepLabel: String,
    sleepActive: Boolean,
    onSleepTimer: () -> Unit,
    showLyricsToggle: Boolean,
    lyricsSelected: Boolean,
    onToggleLyrics: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontal = if (compact) 10.dp else 24.dp
    val playSize = if (compact) 52.dp else 68.dp
    val skipSize = if (compact) 42.dp else 50.dp
    val modeSize = if (compact) 36.dp else 42.dp

    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = horizontal)) {
            SeekBar(
                fraction = fraction,
                enabled = duration > 0,
                onFractionChange = onFractionChange,
                onCommit = onCommit,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    // 2026-11-15：`toDurationLabel()` 对 0 返回 "--:--"（列表里表示「时长未知」），
                    // 但播放页左侧是「当前进度」，0 就该显示 0:00——切歌归零后显示 --:-- 像出了故障。
                    text = (fraction * duration).toLong()
                        .let { if (it > 0L) it.toDurationLabel() else "0:00" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Text(
                    text = duration.toDurationLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontal, end = horizontal, top = if (compact) 6.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 2026-11-15（用户指令）：左下角改为「播放模式」按钮（默认顺序），
            // 点开扇形菜单直接选 顺序 / 随机 / 循环 / 单曲循环。
            PlayModeButton(
                mode = state.playMode,
                size = modeSize,
                iconSize = if (compact) 17.dp else 20.dp,
                onOpen = onOpenModeMenu,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 18.dp),
            ) {
                TransportIcon(
                    icon = PlayerIcons.SkipPrevious,
                    contentDescription = "上一首",
                    onClick = onPrevious,
                    size = skipSize,
                    iconSize = if (compact) 23.dp else 26.dp,
                )
                Box(
                    modifier = Modifier
                        .size(playSize)
                        .shadow(
                            elevation = 22.dp,
                            shape = CircleShape,
                            spotColor = Palette.Blue.copy(alpha = 0.5f),
                            ambientColor = Color.Black,
                        )
                        .clip(CircleShape)
                        // 2026-11-14：播放键底色随主题反转（深色白底、浅色墨底），
                        // 两套主题下都与背景形成强对比。
                        .background(Palette.TextPrimary)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Palette.Ink,
                        modifier = Modifier.size(if (compact) 26.dp else 32.dp),
                    )
                }
                TransportIcon(
                    icon = PlayerIcons.SkipNext,
                    contentDescription = "下一首",
                    onClick = onNext,
                    size = skipSize,
                    iconSize = if (compact) 23.dp else 26.dp,
                )
            }
            // 2026-11-15（用户指令）：喜欢从顶栏挪到这里（原循环按钮位置），
            // 只有图标、没有圆圈底。
            TransportIcon(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                onClick = onToggleFavorite,
                tint = if (isFavorite) Palette.Red else Palette.TextTertiary,
                size = modeSize,
                iconSize = if (compact) 18.dp else 21.dp,
            )
        }

        FooterDock(
            onOpenQueue = onOpenQueue,
            sleepLabel = sleepLabel,
            sleepActive = sleepActive,
            onSleepTimer = onSleepTimer,
            showLyricsToggle = showLyricsToggle,
            lyricsSelected = lyricsSelected,
            onToggleLyrics = onToggleLyrics,
            compact = compact,
            modifier = Modifier.padding(
                start = horizontal,
                end = horizontal,
                top = if (compact) 6.dp else 18.dp,
                bottom = if (compact) 4.dp else 14.dp,
            ),
        )
    }
}

/**
 * 播放模式按钮（2026-11-15 用户指令）。
 *
 * 只负责画当前模式的图标 + 点击时把自身中心（root 坐标）报上去，
 * 扇形菜单本体挂在 `PlayerSheet` 的全屏覆盖层里——用 Popup 时窗口会被系统
 * 钳制到屏幕内，实测位置完全对不上（项会跑到播放键和底部 dock 上）。
 */
@Composable
private fun PlayModeButton(
    mode: PlayMode,
    size: Dp,
    iconSize: Dp,
    onOpen: (Offset) -> Unit,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    TransportIcon(
        icon = modeIcon(mode),
        contentDescription = "播放模式：${modeDescription(mode)}",
        onClick = { onOpen(center) },
        size = size,
        iconSize = iconSize,
        tint = if (mode == PlayMode.SEQUENTIAL) Palette.TextTertiary else Palette.BlueLight,
        modifier = Modifier.onGloballyPositioned { center = it.boundsInRoot().center },
    )
}

@Composable
private fun FooterDock(
    onOpenQueue: () -> Unit,
    sleepLabel: String,
    sleepActive: Boolean,
    onSleepTimer: () -> Unit,
    showLyricsToggle: Boolean,
    lyricsSelected: Boolean,
    onToggleLyrics: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 54.dp)
            .glassSurface(Shapes.cardSmall, Palette.Surface.copy(alpha = 0.48f), borderAlpha = 0.75f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLyricsToggle) {
            FooterAction(
                icon = if (lyricsSelected) PlayerIcons.Disc else PlayerIcons.Lyrics,
                label = if (lyricsSelected) "封面" else "歌词",
                active = false,
                onClick = onToggleLyrics,
                modifier = Modifier.weight(1f),
            )
            FooterDivider()
        }
        // 2026-11-14：队列与喜欢互换——队列从顶栏移到底部 dock。
        FooterAction(
            icon = PlayerIcons.Queue,
            label = "队列",
            active = false,
            onClick = onOpenQueue,
            modifier = Modifier.weight(1f),
        )
        FooterDivider()
        FooterAction(
            icon = PlayerIcons.Timer,
            label = sleepLabel,
            active = sleepActive,
            onClick = onSleepTimer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FooterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Palette.BlueLight,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(Shapes.cardSmall)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) activeColor else Palette.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else Palette.TextTertiary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun FooterDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Color.White.copy(alpha = 0.07f)),
    )
}

/**
 * 自绘进度条：4dp 轨道 + 白色圆点，拖动/点击都支持。
 *
 * 命中区固定 44dp（视觉轨道只有 4dp），触摸坐标按轨道的左右 8dp 内缩换算，
 * 否则点两端的视觉位置会与圆点对不上。
 *
 * **按下即提交一次 seek**：旧实现只在抬手时提交，一旦 up 事件被父布局或
 * 系统手势吞掉（真机回归里出现过），用户看到的就是「点了没反应」。
 * 拖动过程中只更新预览，抬手时若位置与按下点不同再补一次最终 seek。
 */
@Composable
private fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    onFractionChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val clamped = fraction.coerceIn(0f, 1f)
    // 2026-11-14 深浅色：draw 块不是组合上下文，色值/画刷先取再进 Canvas。
    val accentBrush = Gradients.accent
    val thumbHalo = Palette.BlueLight.copy(alpha = 0.30f)
    val trackColor = Palette.TextTertiary.copy(
        alpha = if (enabled) 0.35f else 0.18f,
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics(mergeDescendants = true) {
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                    setProgress { target ->
                        onCommit(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun fractionAt(x: Float): Float {
                    val inset = 8.dp.toPx()
                    val usable = (widthPx - inset * 2f).coerceAtLeast(1f)
                    return ((x - inset) / usable).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var value = fractionAt(down.position.x)
                    // 按下先提交一次（防 up 被父布局吞掉），再把预览值设成按下点，
                    // 让圆点立刻跟到手指位置。
                    onCommit(value)
                    onFractionChange(value)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        value = fractionAt(change.position.x)
                        if (!change.pressed) {
                            // 2026-11-15 BUGFIX：抬手**无条件**提交一次。
                            // 旧实现只在「与按下点不同」时才 onCommit，而 onCommit 才负责
                            // 把外层的拖动预览值清成 -1；于是「按下即抬手（点一下进度条）」
                            // 之后预览值永远停在那个位置，进度条不再随播放前进
                            // （用户报的「拖动后切歌，进度条还停在上一首位置」）。
                            onCommit(value)
                            break
                        }
                        onFractionChange(value)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val startX = 8.dp.toPx()
            val endX = size.width - 8.dp.toPx()
            val trackWidth = 4.dp.toPx()
            drawLine(
                color = trackColor,
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round,
            )
            if (clamped > 0f) {
                val x = startX + (endX - startX) * clamped
                drawLine(
                    brush = accentBrush,
                    start = Offset(startX, y),
                    end = Offset(x, y),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round,
                )
            }
            if (enabled) {
                val x = startX + (endX - startX) * clamped
                drawCircle(
                    color = thumbHalo,
                    radius = 11.dp.toPx(),
                    center = Offset(x, y),
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
    }
}

@Composable
internal fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    tint: Color = Palette.TextSecondary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 定时关闭对话框（2026-11-15 用户指令）：预设 5 / 15 / 30 分钟，
 * 自定义时间改成**手动输入**（原来的 ±1 步进器已删）。
 */
@Composable
private fun SleepDialog(
    sleepRemainingMin: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var customText by remember { mutableStateOf("") }
    val customMin = customText.toIntOrNull()?.takeIf { it in 1..600 }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        shape = Shapes.card,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextSecondary,
        title = { Text("定时关闭") },
        text = {
            Column {
                if (sleepRemainingMin > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shapes.cardSmall)
                            .background(Palette.Blue.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "还剩 $sleepRemainingMin 分钟后暂停",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.BlueLight,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Red,
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .clickable(onClick = onClear)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 15, 30).forEach { m ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(Shapes.pill)
                                .background(Palette.Surface3.copy(alpha = 0.7f))
                                .clickable { onPick(m) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$m 分钟",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.TextPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input ->
                            // 只收数字，最多 3 位（1–600 分钟）
                            customText = input.filter { it.isDigit() }.take(3)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = {
                            Text(
                                text = "自定义分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextTertiary,
                            )
                        },
                        suffix = {
                            Text(
                                text = "分",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextTertiary,
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = Shapes.cardSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.BlueLight,
                            unfocusedBorderColor = Palette.TextTertiary.copy(alpha = 0.45f),
                            focusedTextColor = Palette.TextPrimary,
                            unfocusedTextColor = Palette.TextPrimary,
                            cursorColor = Palette.BlueLight,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryPill(
                        label = "开始",
                        onClick = { customMin?.let(onPick) },
                        modifier = Modifier
                            .height(44.dp)
                            .alpha(if (customMin != null) 1f else 0.4f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Palette.TextSecondary)
            }
        },
    )
}
