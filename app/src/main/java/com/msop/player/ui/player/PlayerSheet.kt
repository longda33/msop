package com.msop.player.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.msop.player.core.model.Track
import com.msop.player.core.model.toDurationLabel
import com.msop.player.feature.player.PlayMode
import com.msop.player.feature.player.PlaybackState
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.msop.player.feature.lyrics.Lyrics
import kotlin.math.abs

/**
 * 全屏播放页。用 overlay 而不是路由：返回键只收起，不进返回栈，
 * 也就不会出现「从播放页返回却回到列表顶部」这种断裂。
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
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onCollapse)

    var showLyrics by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val duration = if (state.durationMs > 0) state.durationMs else (track?.durationMs ?: 0L)
    var dragValue by remember { mutableFloatStateOf(-1f) }
    val liveFraction = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fraction = if (dragValue >= 0f) dragValue else liveFraction

    // 下滑收起：只跟手移动，松手后再决定「回去」还是「关掉」。
    // 上滑不移动面板（上面已经是屏幕边缘），累计位移超过阈值就打开队列。
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // 手势只挂在「封面区 / 顶部栏 / 歌词头部」这些非滚动区域。
    // 挂在根布局会和歌词 LazyColumn 的滚动抢事件——实测会把页面误收起。
    val dragModifier = Modifier.pointerInput(screenHeightPx) {
        var upward = 0f
        detectVerticalDragGestures(
            onDragStart = { upward = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (dragAmount > 0f) {
                    upward = 0f
                    offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                } else {
                    upward -= dragAmount
                }
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
                if (upward > 56.dp.toPx() && offsetY < 10f) {
                    onOpenQueue()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .graphicsLayer { translationY = offsetY },
    ) {
        // 模糊封面背景。blur 在 API 31+ 生效，低版本退化成「压暗的原图」，
        // 下面这层黑幕保证任何情况下文字都有足够对比度。
        if (track?.coverUri != null) {
            AsyncImage(
                model = track.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0xB30A0A0C),
                                0.55f to Color(0xE60A0A0C),
                                1f to Palette.Ink,
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color(0x3DD6A640), Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.22f),
                                radius = size.width * 0.95f,
                            ),
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color(0x2E2E56A0), Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.96f),
                                radius = size.width * 0.8f,
                            ),
                        )
                    },
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .then(dragModifier)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(Icons.Filled.KeyboardArrowDown, "收起", onCollapse)
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(44.dp))
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LandscapePlayerPane(
                        state = state,
                        track = track,
                        duration = duration,
                        fraction = fraction,
                        onFractionChange = { dragValue = it },
                        onFinishSeek = {
                            if (dragValue >= 0f) onSeek((dragValue * duration).toLong())
                            dragValue = -1f
                        },
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onCycleMode = onCycleMode,
                        onOpenQueue = onOpenQueue,
                        modifier = Modifier.weight(0.94f).fillMaxHeight(),
                    )
                    LyricsPane(
                        track = track,
                        positionMs = positionMs,
                        onSeek = onSeek,
                        vm = lyricsViewModel,
                        dragModifier = dragModifier,
                        showHeader = false,
                        modifier = Modifier.weight(1.06f).fillMaxHeight(),
                    )
                }
            } else {
                if (showLyrics) {
                    LyricsPane(
                        track = track,
                        positionMs = positionMs,
                        onSeek = onSeek,
                        vm = lyricsViewModel,
                        dragModifier = dragModifier,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(1f).then(dragModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 42.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(Shapes.coverStage)
                                    .background(Palette.Surface3)
                                    .clickable { showLyrics = true },
                            ) {
                                CoverThumb(
                                    coverUri = track?.coverUri,
                                    size = null,
                                    shape = Shapes.coverStage,
                                    modifier = Modifier.fillMaxSize(),
                                    iconSize = 56.dp,
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .clip(Shapes.pill)
                                        .background(Color(0x80000000))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = if (track?.source?.name == "OPENLIST") "云端" else "本地文件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Palette.TextSecondary,
                                    )
                                }
                            }

                            Column(Modifier.padding(start = 30.dp, end = 30.dp, top = 24.dp)) {
                                Text(
                                    text = track?.title ?: "未在播放",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(track?.artist, track?.album)
                                        .joinToString(" · ")
                                        .ifBlank { "—" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    }
                }

                PlayerTransport(
                    state = state,
                    duration = duration,
                    fraction = fraction,
                    onFractionChange = { dragValue = it },
                    onFinishSeek = {
                        if (dragValue >= 0f) onSeek((dragValue * duration).toLong())
                        dragValue = -1f
                    },
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCycleMode = onCycleMode,
                    onOpenQueue = onOpenQueue,
                    showViewToggle = true,
                    lyricsSelected = showLyrics,
                    onShowLyrics = { showLyrics = true },
                    onShowCover = { showLyrics = false },
                )
            }
        }
    }
}

@Composable
private fun LandscapePlayerPane(
    state: PlaybackState,
    track: Track?,
    duration: Long,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFinishSeek: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val coverSize = minOf(maxWidth - 20.dp, maxHeight)
                    .coerceAtMost(250.dp)
                    .coerceAtLeast(48.dp)
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .clip(Shapes.coverStage)
                        .background(Palette.Surface3),
                ) {
                    CoverThumb(
                        coverUri = track?.coverUri,
                        size = null,
                        shape = Shapes.coverStage,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 48.dp,
                    )
                }
            }
            Text(
                text = track?.title ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(track?.artist, track?.album)
                    .joinToString(" · ")
                    .ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        PlayerTransport(
            state = state,
            duration = duration,
            fraction = fraction,
            onFractionChange = onFractionChange,
            onFinishSeek = onFinishSeek,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onCycleMode = onCycleMode,
            onOpenQueue = onOpenQueue,
            showViewToggle = false,
            lyricsSelected = true,
            onShowLyrics = {},
            onShowCover = {},
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlayerTransport(
    state: PlaybackState,
    duration: Long,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFinishSeek: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    showViewToggle: Boolean,
    lyricsSelected: Boolean,
    onShowLyrics: () -> Unit,
    onShowCover: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progressPadding = if (compact) 10.dp else 30.dp
    val controlPadding = if (compact) 4.dp else 30.dp
    val buttonSize = if (compact) 36.dp else 44.dp
    val skipSize = if (compact) 38.dp else 48.dp
    val playSize = if (compact) 50.dp else 66.dp

    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = progressPadding)) {
            Slider(
                value = fraction,
                onValueChange = onFractionChange,
                onValueChangeFinished = onFinishSeek,
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Palette.BlueLight,
                    inactiveTrackColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth().height(if (compact) 22.dp else 26.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = (fraction * duration).toLong().toDurationLabel(),
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
            modifier = Modifier.fillMaxWidth().padding(
                start = controlPadding,
                end = controlPadding,
                top = if (compact) 6.dp else 18.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIcon(
                icon = PlayerIcons.Shuffle,
                contentDescription = "随机",
                onClick = onCycleMode,
                tint = if (state.playMode == PlayMode.SHUFFLE) Palette.BlueLight else Palette.TextTertiary,
                size = buttonSize,
                iconSize = if (compact) 18.dp else 22.dp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 20.dp),
            ) {
                CircleIcon(PlayerIcons.SkipPrevious, "上一首", onPrevious, size = skipSize, iconSize = 22.dp)
                Box(
                    modifier = Modifier
                        .size(playSize)
                        .clip(CircleShape)
                        .background(Palette.Blue)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 24.dp else 30.dp),
                    )
                }
                CircleIcon(PlayerIcons.SkipNext, "下一首", onNext, size = skipSize, iconSize = 22.dp)
            }
            CircleIcon(
                icon = PlayerIcons.Repeat,
                contentDescription = "循环",
                onClick = onCycleMode,
                tint = if (state.playMode == PlayMode.REPEAT_ONE) Palette.BlueLight else Palette.TextTertiary,
                size = buttonSize,
                iconSize = if (compact) 18.dp else 22.dp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (compact) 0.dp else 18.dp,
                end = if (compact) 0.dp else 18.dp,
                top = if (compact) 2.dp else 20.dp,
                bottom = if (compact) 4.dp else 24.dp,
            ),
            horizontalArrangement = if (showViewToggle) Arrangement.SpaceAround else Arrangement.Center,
        ) {
            if (showViewToggle) {
                FootEntry(
                    icon = PlayerIcons.Lyrics,
                    label = "歌词",
                    active = lyricsSelected,
                    onClick = onShowLyrics,
                )
                FootEntry(
                    icon = PlayerIcons.Disc,
                    label = "封面",
                    active = !lyricsSelected,
                    onClick = onShowCover,
                )
            }
            FootEntry(
                icon = PlayerIcons.Queue,
                label = "队列",
                active = false,
                onClick = onOpenQueue,
            )
        }
    }
}

@Composable
private fun CircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Palette.TextSecondary,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier = Modifier
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

@Composable
private fun FootEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(Shapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Palette.BlueLight else Palette.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Palette.BlueLight else Palette.TextTertiary,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * 歌词区。
 *
 * 三种状态：加载中、无歌词（给出可操作的提示）、有歌词（跟随滚动 + 高亮 + 点击跳转）。
 *
 * 「手动滑动后 5 秒回中」是 P5-02 的验收点：用户往上翻看后面的歌词时，
 * 不能被自动滚动抢回去；停手 5 秒后恢复跟随，并滚回当前行。
 */
@Composable
private fun LyricsPane(
    track: Track?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    vm: LyricsViewModel,
    dragModifier: Modifier,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(track?.id) { vm.load(track) }
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().padding(horizontal = 26.dp)) {
        if (showHeader) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp).then(dragModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(track?.coverUri, 42.dp, Shapes.coverSmall)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "未在播放",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.artist ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在读取歌词…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
            }

            lyrics == null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无歌词",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                )
                Text(
                    text = "把同名 .lrc 文件放到歌曲所在目录，或使用内嵌歌词的文件。\n目录需要通过「设置 → 添加音乐目录」授权过。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            else -> {
                val loaded = requireNotNull(lyrics)
                Column(Modifier.fillMaxSize()) {
                    if (!loaded.synced && showHeader) {
                        Text(
                            text = "歌词未同步 · 仅展示全文",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextTertiary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        )
                    }
                    LyricLines(
                        lyrics = loaded,
                        positionMs = positionMs,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricLines(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = lyrics.indexAt(positionMs)
    var autoFollow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    // 用户一拖就暂停跟随，停手 5 秒后恢复
    LaunchedEffect(dragged) {
        if (dragged) {
            autoFollow = false
            delay(5_000)
            autoFollow = true
        }
    }

    // BetterLyrics 式：弹簧动画把当前行平滑居中，而不是硬跳
    val scrollSpec = spring<Float>(stiffness = 650f, dampingRatio = 0.9f)
    LaunchedEffect(currentIndex, autoFollow) {
        if (lyrics.synced && autoFollow && currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (item != null) {
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                val itemCenter = item.offset + item.size / 2
                listState.animateScrollBy((itemCenter - viewportCenter).toFloat(), scrollSpec)
            }
        }
    }

    // 上下边缘渐隐：把歌词「融」进背景，远处歌词透视淡出（BetterLyrics 的 spatial fade）
    BoxWithConstraints(
        modifier.drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Palette.Ink,
                        0.14f to Color.Transparent,
                        0.84f to Color.Transparent,
                        1.0f to Palette.Ink,
                    ),
                ),
            )
        },
    ) {
        val edgePadding = if (lyrics.synced) maxHeight / 2 else 12.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (lyrics.synced) Arrangement.Top else Arrangement.Center,
            contentPadding = PaddingValues(vertical = edgePadding),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = lyrics.synced && index == currentIndex
                val distance = if (currentIndex >= 0) abs(index - currentIndex) else Int.MAX_VALUE
                val near = distance == 1

                when {
                    // 当前行 + 逐字标签：卡拉OK式逐字高亮（已唱变亮，未唱保持暗）
                    active && line.words.isNotEmpty() -> {
                        val activeWord = line.words.indexOfLast { it.timeMs <= positionMs }
                        val annotated = buildAnnotatedString {
                            line.words.forEachIndexed { wi, w ->
                                val sung = wi <= activeWord
                                withStyle(
                                    SpanStyle(color = if (sung) Color.White else Color(0x52FFFFFF)),
                                ) { append(w.text) }
                                if (wi != line.words.lastIndex) append(" ")
                            }
                        }
                        Text(
                            text = annotated,
                            fontSize = 20.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.timeMs) }
                                .padding(vertical = 11.dp),
                        )
                    }

                    // 当前行：大号加粗纯白（对应 index.html 的 .l.on）
                    active -> Text(
                        text = line.text,
                        fontSize = 20.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(line.timeMs) }
                            .padding(vertical = 11.dp),
                    )

                    // 非当前行：邻近行稍亮、远处更暗（对应 .l.near / .l）
                    else -> {
                        val color = when {
                            !lyrics.synced -> Color(0xB2FFFFFF)
                            near -> Color(0x85FFFFFF)
                            else -> Color(0x52FFFFFF)
                        }
                        Text(
                            text = line.text,
                            fontSize = 15.sp,
                            lineHeight = 31.sp,
                            color = color,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (lyrics.synced) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                                .padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
