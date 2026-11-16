package com.msop.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.model.Track
import com.msop.player.feature.lyrics.Lyrics
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.LocalMsopPalette
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * 2026-11-15 第六轮（用户指令）：歌词页三点菜单 / 横竖屏切换 / 歌词改 music-web-cf-main 效果。
 *
 * 2026-11-15 第七轮（用户看完真机截图后的六条反馈）：
 *   ① 横屏要的是 `music-web-cf-main` 横屏全屏歌词那个形态（附件截图
 *      `Screenshot_20261115_190337`）——不再是「左封面信息列 + 右歌词 + 浮动小方块」，
 *      而是 web 的 5 列栅格：左列（封面 + 歌名 + 歌手 + 迷你控制卡）居中偏左、
 *      歌词列靠右且左对齐、工具条贴歌词列右上；
 *   ② 歌词「重复行」是 bug（已从数据层定位并修在 `LrcParser`：酷狗/QQ 的方括号
 *      逐字 LRC 每个字一个 `[mm:ss.xx]`，旧实现每个标签拆一行、文本取整句 → 一句复制 20 次）；
 *   ③ 横竖屏按钮挪到「独占一行」的工具条（照 web `m2l-tools`），三点菜单里删掉「切换为横屏」；
 *   ④ 迷你控制条不能淡出消失（用户：「有迷你条，这就是 music-web-cf-main 效果」）；
 *   ⑤ 迷你控制卡原来是实心灰色「背景框」，改成**液态玻璃**；
 *   ⑥ 歌词区几个按钮（逐字开关 + 微调偏移 + 换歌词 + 退出全屏 + 横屏）搬过来，
 *      位置与 web 相同（歌词列右上角一排），并顺带落地**逐字歌词**。
 *
 * 歌词视觉规格继续取自 `music-web-cf-main/index.html`（用户自有项目，D26 可直接采用）：
 *   - 字号 `clamp(24px, 3.2vmin + 15px, 46px)` 再被 `h/4.6` 兜底（15224 行）；
 *   - 行样式 `line-height 1.32 / margin 0 0 .42em / weight 700 / letter-spacing .01em`（3125 行）；
 *   - 非当前行 `scale(.75)`、窄屏 `.82`、当前行 `1`，`transform .3s cubic-bezier(.22,.61,.36,1)`；
 *   - 白色三档 `30% / 72%（已唱）/ 100%（当前）` + 当前句 `0 0 22px` 光晕（3132 / 3167 / 3150）；
 *   - 逐行按「到视口中心距离」`(1 - d)/0.25` 淡出（15167 行 `applyFade`）；
 *   - 竖屏居中 / 横屏左对齐，缩放锚点跟着换（3237 / 3245 行）；
 *   - 横屏栅格 `1fr 3.4fr .6fr 7fr 1.4fr`（3264 行），歌词行 `max-width 13em` 居中于歌词列；
 *   - 工具条 `m2l-tools`（16600 行）：逐字胶囊 + `target` / `swap` / `min` / `land` 四个圆钮。
 *
 * 与 web 的唯一差异是按 D23 深浅两主题取墨色：深色沿用白色三档，浅色换 TextPrimary 三档。
 */

/** 用户手动拖动歌词后，多久恢复自动跟随（原版 P5-02 验收点是 5 秒）。 */
private const val MANUAL_SCROLL_GRACE_MS = 5_000L

/** 歌词左右内缩（原版 26dp）。 */
private val LYRICS_HORIZONTAL_PADDING = 26.dp

/** 歌词偏移微调的一步：0.1 秒（点一次按钮的最小动作）。 */
private const val OFFSET_STEP_MS = 100L

/**
 * 歌词区浮层（工具条 / 迷你控制条）无操作多久后淡出 —— 照 web 的
 * `hideTimer = setTimeout(..., 3000)`（`index.html` 15374–15397 行）。
 */
private const val CHROME_IDLE_MS = 3_000L

/** 淡出/淡入的过渡时长 —— 照 web `transition: opacity .38s ease`（3091 行）。 */
private const val CHROME_FADE_MS = 380

/** 空闲检测的轮询粒度：够快（肉眼无感）又不会每帧都写状态。 */
private const val CHROME_TICK_MS = 120L

// ------------------------------------------------------------------ 浮层自动淡出

/**
 * 歌词区浮层的显隐与「唤醒」回调。
 *
 * 语义照 `music-web-cf-main`：
 * - 每次**在歌词区里的**按下 / 移动 / 滚动都调用 [wake]，重新开始 3 秒计时；
 * - 弹窗（偏移微调）打开期间不计时（web 的 `overlayOpen()` 守卫）；
 * - 到点后浮层整体淡出（`html.m2l-chrome-hidden`），动一下手指再回来。
 */
private class ChromeVisibility(val visible: Boolean, val wake: () -> Unit)

@Composable
private fun rememberChromeVisibility(paused: Boolean): ChromeVisibility {
    var visible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val wake: () -> Unit = { lastTouch = SystemClock.elapsedRealtime() }

    LaunchedEffect(paused) {
        // 弹窗刚关掉时重新开始计时（web 的 closeCal() 会再调一次 showChrome()）
        lastTouch = SystemClock.elapsedRealtime()
        while (true) {
            delay(CHROME_TICK_MS)
            val idle = SystemClock.elapsedRealtime() - lastTouch
            val want = paused || idle < CHROME_IDLE_MS
            if (visible != want) visible = want
        }
    }
    return ChromeVisibility(visible, wake)
}

/**
 * 在歌词区任意位置「动一下」就算唤醒。挂在根布局上、只观察不消费，
 * 所以不影响歌词列表自己的滚动与点行 seek（web 的 `wakeChrome` 同理）。
 */
private fun Modifier.wakeChrome(chrome: ChromeVisibility): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            chrome.wake()
        }
    }
}

/**
 * 浮层外壳：显隐用透明度过渡（不占布局，所以藏起来时歌词不会跳），
 * 藏起来时吞掉**点击**避免误触看不见的按钮，但拖动仍落到下面的歌词列表
 * （web 里藏起来的浮层是 `pointer-events:none`）。
 */
@Composable
private fun ChromeOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(CHROME_FADE_MS),
        label = "chromeAlpha",
    )
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (visible) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { } },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

// ------------------------------------------------------------------ 竖屏

/** 竖屏歌词页：头部信息行 + 歌词；工具条是浮层（3 秒无操作淡出）。 */
@Composable
fun LyricsPage(
    track: Track?,
    positionMs: Long,
    vm: LyricsViewModel,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(track?.id) { vm.load(track) }
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ink = lyricsInk()
    val context = LocalContext.current

    var offsetMs by remember(track?.id) { mutableLongStateOf(0L) }
    var wordMode by remember(track?.id) { mutableStateOf(false) }
    var offsetDialogOpen by remember { mutableStateOf(false) }

    // 2026-11-15 第九轮：浮层自动淡出（3 秒无操作藏、动一下回来）
    val chrome = rememberChromeVisibility(paused = offsetDialogOpen)

    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = LYRICS_HORIZONTAL_PADDING)
            .wakeChrome(chrome),
    ) {
        Column(Modifier.fillMaxSize()) {
            LyricsTrackHeader(
                track = track,
                dragModifier = dragModifier,
            )
            LyricsBody(
                lyrics = lyrics,
                loading = loading,
                positionMs = positionMs,
                showUnsyncedHint = true,
                offsetMs = offsetMs,
                wordMode = wordMode,
                centerAligned = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 2026-11-15 第九轮（用户指令）：工具条不再独占一行，改成和 web `m2l-tools`
        // 一样的浮层——胶囊靠左、圆钮靠右，浮在信息行下面，会盖住歌词顶部（预期行为），
        // 3 秒无操作整体淡出。
        ChromeOverlay(
            visible = chrome.visible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 52.dp),
        ) {
            LyricsToolsRow(
                ink = ink,
                wordToggleVisible = lyrics?.lines?.any { it.words.isNotEmpty() } == true,
                wordMode = wordMode,
                onToggleWordMode = { wordMode = !wordMode },
                onAdjustOffset = { offsetDialogOpen = true },
                onReloadLyrics = { vm.reload(track) },
                isLandscape = false,
                onToggleOrientation = { setLyricsOrientation(context, toLandscape = true) },
                spreadChip = true,
            )
        }

        // 2026-11-16 晚：偏移改成页面内浮层（web `m2l-pop` 形态），贴在歌词区底部，
        // 不加遮罩、不挡整屏——上面歌词仍可见，边调边对齐。
        if (offsetDialogOpen) {
            LyricsOffsetPanel(
                offsetMs = offsetMs,
                onOffsetChange = { offsetMs = it },
                onDismiss = { offsetDialogOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

/**
 * 头部：42dp 封面 + 歌名/歌手。
 *
 * 2026-11-16 用户指令：这一行的三点菜单删掉，改由播放页顶栏右上角那颗三点承担
 * （播放 / 歌词两页共用菜单，见 `PlayerSheet.PlayerTopBar`）。这里只留信息。
 */
@Composable
private fun LyricsTrackHeader(
    track: Track?,
    dragModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            coverUri = track?.coverUri,
            size = 42.dp,
            shape = Shapes.coverSmall,
            seed = track?.artSeed() ?: "msop",
        )
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

/**
 * 歌词工具条（照 `music-web-cf-main` 的 `m2l-tools`，`index.html` 16600 行）：
 * 「普通 / 逐字」胶囊开关 + 一排圆钮（微调偏移 / 换歌词 / 横屏）。
 *
 * 2026-11-15 用户指令：① 删掉「全屏」按钮（那颗 ⤢）；② 竖屏时逐字胶囊靠左、圆钮靠右
 * （[spreadChip] = true）；③ 横屏不再独占一行，整组浮在歌词区右上角（不占布局）；
 * ④ 第九轮：浮层 3 秒无操作自动淡出（见 [rememberChromeVisibility]）。
 */
@Composable
private fun LyricsToolsRow(
    ink: Color,
    wordToggleVisible: Boolean,
    wordMode: Boolean,
    onToggleWordMode: () -> Unit,
    onAdjustOffset: () -> Unit,
    onReloadLyrics: () -> Unit,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
    spreadChip: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val arrangement = if (spreadChip) Arrangement.SpaceBetween else Arrangement.End
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 逐字胶囊：只有在真的有逐字时间轴时出现（web 也是 `display:none` 控制）
        if (wordToggleVisible) {
            WordModeChip(on = wordMode, onToggle = onToggleWordMode, ink = ink)
        } else if (spreadChip) {
            Spacer(Modifier.width(0.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (wordToggleVisible && !spreadChip) Spacer(Modifier.width(10.dp))
            LyricsToolButton(
                icon = PlayerIcons.Target,
                description = "歌词对不上时微调偏移",
                onClick = onAdjustOffset,
                ink = ink,
            )
            Spacer(Modifier.width(8.dp))
            LyricsToolButton(
                icon = PlayerIcons.Swap,
                description = "重新加载歌词",
                onClick = onReloadLyrics,
                ink = ink,
            )
            Spacer(Modifier.width(8.dp))
            LyricsToolButton(
                icon = PlayerIcons.Rotate,
                description = if (isLandscape) "切回竖屏" else "切换为横屏",
                onClick = onToggleOrientation,
                ink = ink,
            )
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
    }
}

/** 「普通 / 逐字」胶囊：文字 + 一枚迷你开关（web `.m2l-word-chip`）。 */
@Composable
private fun WordModeChip(
    on: Boolean,
    onToggle: () -> Unit,
    ink: Color,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(ink.copy(alpha = 0.10f))
            .border(1.dp, ink.copy(alpha = 0.16f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (on) "逐字" else "普通",
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.90f),
        )
        Spacer(Modifier.width(8.dp))
        val knobOffset by animateDpAsState(
            targetValue = if (on) 14.dp else 0.dp,
            animationSpec = tween(180),
            label = "wordChipKnob",
        )
        Box(
            Modifier
                .size(width = 28.dp, height = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (on) Palette.BlueLight.copy(alpha = 0.90f) else ink.copy(alpha = 0.22f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = knobOffset + 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White else ink.copy(alpha = 0.75f)),
            )
        }
    }
}

/** 工具条圆钮（web `.btn-ghost .m2l-icon`：半透明底 + 细描边）。 */
@Composable
private fun LyricsToolButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    ink: Color,
    size: Dp = 34.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.08f))
            .border(1.dp, ink.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = ink.copy(alpha = 0.90f),
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * 三点菜单：图标与 `DropdownMenu` 同居一个 `Box`，菜单由 Material3 以这个 `Box`
 * 为锚点向下弹出——弹窗永远贴着三点，不会飘到屏幕中间或另开一层面板。
 *
 * 2026-11-15 用户指令：菜单里删掉「切换为横屏」（工具条已有独立按钮）。
 */
@Composable
private fun LyricsOverflowMenu(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(36.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = Palette.TextTertiary,
                modifier = Modifier.size(19.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Palette.Surface2,
            shape = Shapes.cardSmall,
            offset = DpOffset(x = 4.dp, y = 0.dp),
        ) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Palette.Red else Palette.TextSecondary,
                    )
                },
                onClick = {
                    open = false
                    onToggleFavorite()
                },
            )
        }
    }
}

/**
 * 歌词偏移面板（2026-11-16 晚重做，照 music-web-cf-main 的 `buildCalPop` 浮层）。
 *
 * 用户的 bug：旧实现是 `AlertDialog`——居中的全屏模态框把歌词整片盖住，
 * 打开后看不到歌词，根本没法边调边对齐。
 *
 * web 的做法（`index.html` 15407–15445 行 + CSS 2756–2830）是 `position: fixed`
 * 的**页面内小浮层**，贴在歌词区底部上方，不遮整屏、不加遮罩，上面歌词照样能看见；
 * 内容：标题 + 当前值 + 关闭 ✕ + 说明 + 作用域切换 + 五个步进钮 + `range` 滑杆。
 * 这里按同一形态实现成 Compose 的浮层卡片（不放 Dialog），滑杆改 `Slider`。
 *
 * 偏移为正 = 歌词提前（高亮往前挪），与 web 的 `fmtOffset` 记法一致。
 */
@Composable
private fun LyricsOffsetPanel(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(offsetMs) {
        val sign = when {
            offsetMs > 0 -> "+"
            offsetMs < 0 -> "−"
            else -> ""
        }
        val seconds = abs(offsetMs) / 1000.0
        sign + String.format("%.1f", seconds) + "s"
    }
    // 面板自身吃掉点击，避免穿透到底下的歌词（web: `calPop.addEventListener('click', stopPropagation)`）
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .glassSurface(Shapes.card, Palette.Surface.copy(alpha = 0.92f), elevation = 22.dp)
            .clickable(enabled = false) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "歌词偏移",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (offsetMs == 0L) Palette.TextSecondary else Palette.BlueLight,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Palette.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = "正数 = 歌词提前，负数 = 歌词延后；下面边调边看歌词即可。",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        // 滑杆范围照 web：-10s ~ +10s，步进 0.1s
        Slider(
            value = (offsetMs / 1000f).coerceIn(-10f, 10f),
            onValueChange = { onOffsetChange((it * 1000L).toLong()) },
            valueRange = -10f..10f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OffsetStepButton("−0.5s") { onOffsetChange((offsetMs - 500L).coerceIn(-10_000L, 10_000L)) }
            OffsetStepButton("−0.1s") { onOffsetChange((offsetMs - OFFSET_STEP_MS).coerceIn(-10_000L, 10_000L)) }
            OffsetStepButton("归零") { onOffsetChange(0L) }
            OffsetStepButton("+0.1s") { onOffsetChange((offsetMs + OFFSET_STEP_MS).coerceIn(-10_000L, 10_000L)) }
            OffsetStepButton("+0.5s") { onOffsetChange((offsetMs + 500L).coerceIn(-10_000L, 10_000L)) }
        }
    }
}

@Composable
private fun OffsetStepButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Palette.Surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Palette.TextPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * 横竖屏切换：直接锁到目标方向（`SENSOR_LANDSCAPE` 允许左右两个横屏方向）。
 *
 * 首次调用时 Activity 会重建，但 `MsopApp` 的 `playerOpen` 是 `rememberSaveable`，
 * 播放页仍停在原地；重建后按 `LocalConfiguration` 进横屏歌词分支。
 */
private fun setLyricsOrientation(context: Context, toLandscape: Boolean) {
    val activity = context as? Activity ?: return
    activity.requestedOrientation = if (toLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

/**
 * 横屏全屏歌词时把系统状态栏收起来（用户 2026-11-15：「横屏顶部状态栏不显示」）。
 *
 * 只藏 `statusBars` 而留 `navigationBars`：横屏是手势条，藏了就没了返回手势。
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 保证边缘下滑还能临时唤出。
 * 组件离场（退出歌词 / 回竖屏）时恢复，避免把状态栏永久藏掉。
 */
@Composable
private fun HideStatusBar(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, view).also { c ->
                c.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        if (active) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

/** 深浅两主题的歌词墨色（深色白、浅色 TextPrimary）。 */
@Composable
private fun lyricsInk(): Color {
    val palette = LocalMsopPalette.current
    return if (palette.isDark) Color.White else palette.TextPrimary
}

/**
 * 歌词字号：web 的 `baseLyricFontSize`（`music-web-cf-main/index.html` 15224 行）——
 * `clamp(24, 3.2vmin + 15, 46)`，再用 `h / 4.6` 兜底（矮窗里不让一行字吃掉半屏）。
 *
 * 歌名 / 歌手字号也由它推出来（web 15300–15310 行），所以单独抽成函数两处共用。
 */
private fun lyricFontSize(widthDp: Float, heightDp: Float): Float {
    val vmin = minOf(widthDp, heightDp)
    var size = (3.2f * (vmin / 100f) + 15f).coerceIn(24f, 46f)
    if (heightDp > 0f) size = minOf(size, heightDp / 4.6f)
    return size.coerceAtLeast(16f)
}

/** 三种状态：加载中 / 无歌词 / 有歌词。 */
@Composable
private fun LyricsBody(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    showUnsyncedHint: Boolean,
    offsetMs: Long = 0L,
    wordMode: Boolean = false,
    centerAligned: Boolean = true,
    lineMaxWidthEm: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val ink = lyricsInk()

    when {
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "正在读取歌词…",
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.60f),
            )
        }

        lyrics == null -> Column(
            modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.70f),
            )
            Text(
                text = "把同名 .lrc 文件放到歌曲所在目录，或使用内嵌歌词的文件。\n目录需要通过「设置 → 添加音乐目录」授权过。",
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        else -> Column(modifier) {
            if (!lyrics.synced && showUnsyncedHint) {
                Text(
                    text = "歌词未同步 · 仅展示全文",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
            LyricLines(
                lyrics = lyrics,
                positionMs = positionMs + offsetMs,
                wordMode = wordMode,
                centerAligned = centerAligned,
                lineMaxWidthEm = lineMaxWidthEm,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 歌词列表（`music-web-cf-main` 全屏歌词规格）。
 *
 * 所有行**同一字号**，只靠 `scale` 与白色三档区分层次——web 原文注释说明
 * 「scale 不参与排版，所以当前行变大了也不会被挤到第二行」。
 *
 * 边缘渐隐不是整块 `DstIn` 蒙版，而是 web 的逐行 `applyFade`：透明度 =
 * `(1 - d) / 0.25`，d 是该行中心到视口中心的距离（以半屏为 1）。这段计算放在
 * `graphicsLayer` 里做——只在绘制阶段读滚动状态，滚动时不会触发每一行的重组。
 *
 * [centerAligned]：竖屏居中（锚点也用中心），横屏左对齐（锚点挪到左边缘）。
 * [wordMode]：「逐字」开关。开着且这行有逐字时间轴时，当前句按逐字点亮；
 * 关掉就是普通整行歌词（web 的 `普通 / 逐字` 胶囊）。
 */
@Composable
private fun LyricLines(
    lyrics: Lyrics,
    positionMs: Long,
    wordMode: Boolean,
    centerAligned: Boolean,
    lineMaxWidthEm: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMsopPalette.current
    val dark = palette.isDark
    val ink = if (dark) Color.White else palette.TextPrimary

    // 白色三档（浅色主题换成墨色等价三档，否则白字在浅底上不可读）
    val baseAlpha = if (dark) 0.30f else 0.55f
    val playedAlpha = if (dark) 0.72f else 0.86f
    val plainAlpha = if (dark) 0.72f else 0.86f
    val unsungAlpha = if (dark) 0.38f else 0.55f
    val glow = if (dark) {
        Shadow(color = Color.White.copy(alpha = 0.30f), blurRadius = 24f)
    } else {
        Shadow(color = palette.TextPrimary.copy(alpha = 0.22f), blurRadius = 18f)
    }

    val listState = rememberLazyListState()
    val currentIndex = if (lyrics.synced) lyrics.indexAt(positionMs) else -1
    var autoFollow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    // 用户一拖就暂停跟随，停手 5 秒后恢复。
    LaunchedEffect(dragged) {
        if (dragged) {
            autoFollow = false
        } else if (!autoFollow) {
            delay(MANUAL_SCROLL_GRACE_MS)
            autoFollow = true
        }
    }

    val scrollSpec = spring<Float>(stiffness = 650f, dampingRatio = 0.9f)
    // web: `transform .3s cubic-bezier(.22,.61,.36,1)`
    val ease = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        // 上下各留半屏：首行/末行都能停在正中，当前句正好居中。
        val halfViewport = maxHeight / 2
        val halfViewportPx = with(density) { halfViewport.toPx() }

        // web `baseLyricFontSize`：clamp(24px, 3.2vmin + 15px, 46px)，再用 h/4.6 兜底。
        val lyricSize = lyricFontSize(maxWidth.value, maxHeight.value)
        // 横屏照 web 的 `max-width: 13em` + `margin-left/right: auto`（`index.html` 3280 行）：
        // 行块最宽 13em、在歌词列里居中，等价于左右各留 (列宽 - 13em)/2。
        // 这是三个诉求的平衡点：再窄会折行，再宽会让 margin:auto 失效、整块歌词贴左。
        val lineInset = if (lineMaxWidthEm > 0f) {
            ((maxWidth - (lyricSize * lineMaxWidthEm).dp) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        // 横屏 / 宽屏（左对齐）用 .75，竖屏窄屏用 .82
        val inactiveScale = if (centerAligned) 0.82f else 0.75f
        val lineGap = (lyricSize * 0.42f).dp

        LaunchedEffect(currentIndex, autoFollow, halfViewportPx) {
            if (!lyrics.synced || !autoFollow || currentIndex < 0) return@LaunchedEffect
            // `LazyListItemInfo.offset` **不含 contentPadding**：实际绘制位置是
            // `contentPadding.top + offset`，居中判定必须把 padding 加回去
            // （AGENTS.md 记过的锚点漂移就是漏了这一项）。
            var item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (item == null) {
                listState.animateScrollToItem(currentIndex)
                item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            }
            if (item != null) {
                val displayedCenter = halfViewportPx + item.offset + item.size / 2f
                val delta = displayedCenter - halfViewportPx
                if (abs(delta) > 0.5f) listState.animateScrollBy(delta, scrollSpec)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (lyrics.synced) Arrangement.Top else Arrangement.Center,
            contentPadding = PaddingValues(vertical = if (lyrics.synced) halfViewport else 12.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = lyrics.synced && index == currentIndex
                val played = lyrics.synced && currentIndex >= 0 && index < currentIndex

                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else if (lyrics.synced) inactiveScale else 1f,
                    animationSpec = tween(durationMillis = 300, easing = ease),
                    label = "lyricScale$index",
                )
                val color by animateColorAsState(
                    targetValue = when {
                        !lyrics.synced -> ink.copy(alpha = plainAlpha)
                        active -> ink
                        played -> ink.copy(alpha = playedAlpha)
                        else -> ink.copy(alpha = baseAlpha)
                    },
                    animationSpec = tween(durationMillis = 300, easing = ease),
                    label = "lyricColor$index",
                )

                // 逐行边缘淡出（web `applyFade`）：在绘制阶段算，滚动不触发重组。
                val fadeModifier = Modifier.graphicsLayer {
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val fade = if (!lyrics.synced) {
                        1f
                    } else if (info == null) {
                        0f
                    } else {
                        val centerY = halfViewportPx + info.offset + info.size / 2f
                        val d = abs(centerY - halfViewportPx) / halfViewportPx.coerceAtLeast(1f)
                        ((1f - d) / 0.25f).coerceIn(0f, 1f)
                    }
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                    // 居中排版缩放的锚点必须在中心；横屏左对齐时锚点挪到左边缘，
                    // 否则收缩会把整行往左推（web 3240 行的原注释就是这个坑）。
                    transformOrigin = if (centerAligned) TransformOrigin.Center else TransformOrigin(0f, 0.5f)
                }
                // 2026-11-16 用户指令：点歌词行不再跳转播放（web 版没有这个交互）。
                val seekModifier = Modifier
                val lineHeight = (lyricSize * 1.32f).sp
                val letterSpacing = (lyricSize * 0.01f).sp
                val align = if (centerAligned) TextAlign.Center else TextAlign.Start
                val lineWidthModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = lineInset)

                if (active && wordMode && line.words.isNotEmpty()) {
                    // 逐字：web 的遮罩效果等价于「已唱纯白 / 未唱 38% 白」，逐字行不加光晕。
                    val activeWord = line.words.indexOfLast { it.timeMs <= positionMs }
                    val annotated = buildAnnotatedString {
                        line.words.forEachIndexed { wi, w ->
                            withStyle(
                                SpanStyle(color = if (wi <= activeWord) ink else ink.copy(alpha = unsungAlpha)),
                            ) { append(w.text) }
                            // wordJoiner：LRC 逐字按音节切分需要补空格，TTML / 方括号逐字自带原文
                            if (wi != line.words.lastIndex) append(line.wordJoiner)
                        }
                    }
                    Text(
                        text = annotated,
                        fontSize = lyricSize.sp,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = letterSpacing,
                        textAlign = align,
                        modifier = lineWidthModifier
                            .padding(bottom = lineGap)
                            .then(fadeModifier)
                            .then(seekModifier),
                    )
                } else {
                    Text(
                        text = line.text,
                        fontSize = lyricSize.sp,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = letterSpacing,
                        textAlign = align,
                        color = color,
                        style = TextStyle(shadow = if (active) glow else null),
                        modifier = lineWidthModifier
                            .padding(bottom = lineGap)
                            .then(fadeModifier)
                            .then(seekModifier),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 横屏

/**
 * 横屏歌词页 —— 完全按 `music-web-cf-main` 横屏全屏歌词排（用户附件截图
 * `Screenshot_20261115_190337` + `_192650`）：
 *
 * ```
 * [    ][ 封面 3.4fr ][ .6fr ][ 歌词 7fr ][ 1.4fr ]
 * ```
 * 左列：大封面（吃满封面列、上限屏高 56%）+ 歌名（大号粗体，最多两行）+ 歌手，
 * 作为一组竖向居中；右列：铺满整列高度的歌词，行宽 `13em` 居中。
 * 工具条与迷你控制条都是**浮层**，盖在内容之上（web 的 `position: fixed`），
 * 3 秒无操作一起淡出，手指一动就回来。
 *
 * 整屏背景就是播放页那张模糊封面，不再压一层厚白色遮罩、也没有任何实心色块。
 */
@Composable
fun LandscapeLyricsPane(
    track: Track?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    vm: LyricsViewModel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(track?.id) { vm.load(track) }
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ink = lyricsInk()
    val context = LocalContext.current

    var offsetMs by remember(track?.id) { mutableLongStateOf(0L) }
    var wordMode by remember(track?.id) { mutableStateOf(false) }
    var offsetDialogOpen by remember { mutableStateOf(false) }

    // 横屏全屏歌词：连系统状态栏一起收掉（用户「横屏顶部状态栏不显示」）。
    HideStatusBar(active = true)

    // 2026-11-15 第九轮：工具条与迷你控制条一起自动淡出（web 全屏时 `.player-controls`
    // 跟 `.m2l-tools` 同一套 3 秒计时），弹窗打开期间不计时。
    val chrome = rememberChromeVisibility(paused = offsetDialogOpen)

    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .wakeChrome(chrome),
    ) {
        // web 栅格 `1fr 3.4fr .6fr 7fr 1.4fr`（13.4 份，`index.html` 3265 行）：
        // 相对宽度是 左留白 7.5% / 封面列 25.4% / 间距 4.5% / 歌词 52.2% / 右留白 10.4%。
        // 2026-11-16 晚用户指令「歌词横屏 整体太靠左」：两处一起补——
        // ① web 的 `.player-panel` 自身有 `padding: … calc(4vw …)`（`index.html` 3186 行附近），
        //    栅格是在这个内缩框里分的，之前少了这层 4%，整列往左贴；
        // ② 间距实测比用户附件（2048×922）窄，按参考图歌词正文 x≈43.9% 屏宽把 gutter
        //    从 4.5% 提到 7.5%（右留白相应收窄到 9%），歌词列本身仍是 `weight(1f)`。
        val sidePadding = maxWidth * 0.04f
        val innerWidth = maxWidth - sidePadding * 2
        val leadingInset = innerWidth * 0.075f
        val coverColumnWidth = innerWidth * 0.254f
        val gutter = innerWidth * 0.075f
        val trailing = innerWidth * 0.090f
        // web `--m2l-art = min(W*3.4/13.4, H*0.56)`（`index.html` 15315 行）：封面吃满封面列，
        // 但不超过屏高 56%（矮窗里把高度让给歌词和标题）。
        val coverSize = minOf(coverColumnWidth, maxHeight * 0.56f).coerceAtLeast(96.dp)
        // 歌名 / 歌手字号也是 web 的 JS 公式（15300–15310 行），都由歌词字号推出来
        val lyricSize = lyricFontSize(maxWidth.value, maxHeight.value)
        val titleSize = minOf(lyricSize * 1.2f, maxHeight.value * 0.6f / 8.8f * 0.95f)
        val artistSize = minOf(lyricSize * 0.9f, maxHeight.value * 0.5f / 8.8f * 0.88f)

        Row(Modifier.fillMaxSize().padding(horizontal = sidePadding)) {
            Spacer(Modifier.width(leadingInset))
            // 左列：封面 + 歌名 + 歌手当成一组竖向居中（web 的 1fr / 5fr / .2fr / .6fr / .5fr / .5fr / 1fr）
            Column(
                modifier = Modifier
                    .width(coverColumnWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                CoverThumb(
                    coverUri = track?.coverUri,
                    size = coverSize,
                    shape = Shapes.coverGrid,
                    seed = track?.artSeed() ?: "msop",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = track?.title ?: "未在播放",
                    color = ink,
                    fontSize = titleSize.sp,
                    lineHeight = (titleSize * 1.16f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artist ?: "—",
                    color = ink.copy(alpha = 0.80f),
                    fontSize = artistSize.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(gutter))
            // 歌词列铺满整列高度（web 的 `.lyrics-container` 占 `grid-row: 1 / -1`）：
            // 工具条是浮层、盖在歌词上，不再给它让出顶部高度。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                LyricsBody(
                    lyrics = lyrics,
                    loading = loading,
                    positionMs = positionMs,
                    showUnsyncedHint = false,
                    offsetMs = offsetMs,
                    wordMode = wordMode,
                    centerAligned = false,
                    // web 横屏歌词行 `max-width: 13em` + `margin: auto`（3280 行）
                    lineMaxWidthEm = 13f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(trailing))
        }

        // 2026-11-15 用户指令：工具条不占布局，浮在歌词区右上角（web `m2l-tools` 是
        // `position: fixed; right: max(2vw, …); top: max(12px, …)`，会盖住歌词 —— 预期行为）。
        ChromeOverlay(
            visible = chrome.visible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp),
        ) {
            LyricsToolsRow(
                ink = ink,
                wordToggleVisible = lyrics?.lines?.any { it.words.isNotEmpty() } == true,
                wordMode = wordMode,
                onToggleWordMode = { wordMode = !wordMode },
                onAdjustOffset = { offsetDialogOpen = true },
                onReloadLyrics = { vm.reload(track) },
                isLandscape = true,
                onToggleOrientation = { setLyricsOrientation(context, toLandscape = false) },
                // 横屏没有单独的信息行，三点菜单直接并进工具条这一排（依旧贴着三点弹出）
                trailing = {
                    LyricsOverflowMenu(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                },
            )
        }

        // 迷你控制条：底部浮窗，宽度贴着封面列（web 用 `--m2l-ctrl-left / --m2l-ctrl-w`
        // 实测封面位置与宽度，15324–15331 行），所以会盖住左列下方的文字。
        ChromeOverlay(
            visible = chrome.visible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sidePadding + leadingInset, bottom = 14.dp)
                .width(coverSize),
        ) {
            LyricsGlassControls(
                isPlaying = isPlaying,
                fraction = fraction,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                ink = ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 2026-11-16 晚：横屏偏移同样用浮层，压在歌词列底部（web 全屏 `m2l-cal-open.m2l-fs`
        // 把面板贴在歌词区底部、控制条相应上移）。左列封面与玻璃迷你卡不被盖住。
        if (offsetDialogOpen) {
            LyricsOffsetPanel(
                offsetMs = offsetMs,
                onOffsetChange = { offsetMs = it },
                onDismiss = { offsetDialogOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sidePadding + trailing + 12.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * 横屏迷你控制卡（液态玻璃）：进度条 + 上一首 / 播放 / 下一首。
 *
 * 2026-11-15 用户报「背景框怎么还在？显示效果要的是液态玻璃效果」——原来这里是
 * 一块 `Color(0x80080A14)` 实心灰底，现在改成半透明渐变 + 1dp 高光描边，
 * 后面那张模糊封面透上来就是玻璃观感（项目其它玻璃卡用的是 Kyant0 真折射，
 * 但播放页是遮住外层采样的 overlay，这里用同色系的「透光玻璃」等价实现）。
 *
 * 2026-11-16 晚用户需求「歌词横屏 迷你控制条窗口透明度改成和迷你条一样」：
 * 原来这片白 0.34→0.12 的渐变在横屏模糊封面上几乎透底，跟迷你条/导航栏
 * 那种 ShuYin 垫色不是一个观感。这里直接改用与 `MiniPlayer` 完全相同的
 * 浅 `FAFAFA 0.72` / 深 `121212 0.74` 垫色（2 dp 高光描边保留），两处看起来是同一块玻璃。
 */
@Composable
private fun LyricsGlassControls(
    isPlaying: Boolean,
    fraction: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
    // 与 MiniPlayer / ShuYinNavBar 同一组垫色，保证「迷你条」和「横屏迷你卡」是同一块玻璃。
    val glassFill = if (isLightTheme) Color(0xFFFAFAFA).copy(0.72f) else Color(0xFF121212).copy(0.74f)
    Column(
        modifier = modifier
            .clip(shape)
            .background(glassFill)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.12f)),
                ),
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 进度：细线 + 渐变已播放段 + 圆形滑块
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
            val trackShape = RoundedCornerShape(2.dp)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(trackShape)
                    .background(ink.copy(alpha = 0.20f)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(trackShape)
                    .background(Brush.horizontalGradient(listOf(Palette.Gold, Palette.BlueLight))),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (maxWidth * fraction) - 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, ink.copy(alpha = 0.20f), CircleShape),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricsGlassIconButton(PlayerIcons.SkipPrevious, "上一首", onPrevious, ink)
            // 播放键：比两侧大一圈、带一圈柔光，web 的 `.control-main` 就是这颗
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.10f))
                    .border(1.dp, ink.copy(alpha = 0.28f), CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            LyricsGlassIconButton(PlayerIcons.SkipNext, "下一首", onNext, ink)
        }
    }
}

@Composable
private fun LyricsGlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    ink: Color,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = ink.copy(alpha = 0.92f), modifier = Modifier.size(20.dp))
    }
}
