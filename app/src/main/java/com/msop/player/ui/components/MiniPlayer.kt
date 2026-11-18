package com.msop.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.msop.player.ui.glass.inspectDragGestures
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/*
 * 移植自 ShuYin（power690/ShuYin，GPL-3.0）ui/MiniPlayerBar.kt。
 * 用户 2026-11-14 明确授权直接复制 ShuYin 代码（本项目开源）。
 *
 * 玻璃/拉伸/按钮/排版照抄 ShuYin：圆角（2026-11-15 用户指令由 34dp 胶囊改为 16dp 圆角矩形）、
 * 随主题垫色（浅 FAFAFA / 深 121212）、vibrancy + blur(8/14dp) + lens(24dp, depth, 色差)、
 * Highlight 1dp、Shadow 20dp、InnerShadow；标题/副标题跑马灯；拖拽果冻拉伸（miniPlayerStretch）。
 * 适配差异（均为用户本日明确指令，非自拟）：
 * - 只保留一个播放/暂停键（42dp 圆钮，onSurface 色；上一曲/下一曲进播放页）；
 * - 保留频谱动效与卡片底部细进度线（positionMs/durationMs 驱动）。
 */

private fun Modifier.miniPlayerStretch(
    stretchOffset: Animatable<Offset, AnimationVector2D>,
    stretchScope: CoroutineScope,
    stretchSpring: SpringSpec<Offset>,
    onStretchActive: () -> Unit,
    onStretchSettled: () -> Unit
): Modifier = this
    .graphicsLayer {
        val ox = stretchOffset.value.x
        val oy = stretchOffset.value.y
        val edgeBudget = 40.dp.toPx()
        val growY = (abs(oy) / (density * 26f)).coerceIn(0f, 0.35f)
        val growPx = (abs(ox) * 0.35f).coerceAtMost(
            (edgeBudget - abs(ox) * 0.3f).coerceAtLeast(0f)
        )
        val growX = if (size.width > 0f) growPx / size.width else 0f
        translationX = ox * 0.3f
        translationY = oy * 0.3f
        scaleX = 1f + growX
        scaleY = 1f + growY
        transformOrigin = TransformOrigin(
            if (ox >= 0f) 0f else 1f,
            if (oy >= 0f) 0f else 1f
        )
    }
    .pointerInput(Unit) {
        inspectDragGestures(
            directionalLock = true,
            onDragEnd = {
                stretchScope.launch {
                    stretchOffset.animateTo(Offset.Zero, stretchSpring)
                    onStretchSettled()
                }
            },
            onDragCancel = {
                stretchScope.launch {
                    stretchOffset.animateTo(Offset.Zero, stretchSpring)
                    onStretchSettled()
                }
            }
        ) { change, dragAmount ->
            if (dragAmount == Offset.Zero) return@inspectDragGestures
            val maxX = 110.dp.toPx()
            val maxY = 44.dp.toPx()
            val vx = stretchOffset.value.x + dragAmount.x
            val vy = stretchOffset.value.y + dragAmount.y
            val next = Offset(
                maxX * vx / (maxX + abs(vx)),
                maxY * vy / (maxY + abs(vy))
            )
            if (next != Offset.Zero) onStretchActive()
            change.consume()
            stretchScope.launch {
                stretchOffset.snapTo(next)
            }
        }
    }

@Composable
fun MiniPlayer(
    title: String,
    subtitle: String,
    coverUri: String?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    seed: String? = null,
    statusText: String? = null,
    glassBackdrop: Backdrop? = null,
) {
    val backdrop = glassBackdrop ?: LocalLayerBackdrop.current
    // 2026-11-15 用户指令：迷你播放条改成**圆角矩形**（原 34dp 接近胶囊）。
    // 条高约 70dp，16dp 圆角在视觉上明确是「圆角矩形」而不是药丸。
    val glassShape = RoundedCornerShape(16.dp)
    val fullEffects = LiquidGlassFullEffects
    val stretchOffset = remember {
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    }
    val stretchScope = rememberCoroutineScope()
    val suppressClick = remember { mutableStateOf(false) }
    val stretchSpring = spring(
        dampingRatio = 0.3f,
        stiffness = 380f,
        visibilityThreshold = Offset.VisibilityThreshold
    )
    val openPlayer = {
        if (!suppressClick.value) {
            stretchScope.launch { stretchOffset.snapTo(Offset.Zero) }
            onExpand()
        }
    }
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
    val surfaceColor =
        if (LiquidGlassMidEffects) {
            // 2026-11-17 回滚：这层垫色曾被提到浅 0.90 / 深 0.92（用户先报「太透明」），
            // 结果整条迷你条变成不透明色块，`vibrancy + blur + lens` 全部被盖住，
            // 液态玻璃观感彻底消失。上游 ShuYin `MiniPlayerBar.kt` 用的是 0.55 / 0.60，
            // 可读性靠 vibrancy 与纯黑/纯白字色保证，不靠垫色。
            if (isLightTheme) Color(0xFFFAFAFA).copy(0.55f)
            else Color(0xFF121212).copy(0.60f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .miniPlayerStretch(
                stretchOffset,
                stretchScope,
                stretchSpring,
                { suppressClick.value = true },
                { suppressClick.value = false }
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { glassShape },
                        effects = {
                            vibrancy()
                            blur(if (fullEffects) 8.dp.toPx() else 14.dp.toPx())
                            if (fullEffects) {
                                lens(
                                    24.dp.toPx(),
                                    24.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            }
                        },
                        // 2026-11-17 晚回滚：曾被加粗到 2dp 边 / 1dp 柔化 / White 0.62，
                        // 用户看实物否掉（「效果难看」），回到上游原参数 1dp。
                        highlight = { Highlight.Default.copy(width = 1.dp) },
                        shadow = { Shadow(radius = 20.dp, alpha = 0.8f) },
                        innerShadow = {
                            InnerShadow(
                                radius = 10.dp,
                                offset = DpOffset(0.dp, 5.dp),
                                alpha = 0.55f
                            )
                        },
                        onDrawSurface = { drawRect(surfaceColor) }
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface, glassShape)
                }
            )
            .clip(glassShape)
            .clickable(onClick = openPlayer)
    ) {
        MiniPlayerContent(
            title = title,
            subtitle = subtitle,
            coverUri = coverUri,
            seed = seed,
            isPlaying = isPlaying,
            statusText = statusText,
            onPlayPause = { if (!suppressClick.value) onToggle() }
        )
        MiniProgress(positionMs = positionMs, durationMs = durationMs)
    }
}

@Composable
private fun MiniPlayerButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MiniPlayerContent(
    title: String,
    subtitle: String,
    coverUri: String?,
    seed: String?,
    isPlaying: Boolean,
    statusText: String?,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverThumb(
            coverUri = coverUri,
            size = 40.dp,
            shape = RoundedCornerShape(7.dp),
            seed = seed,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText ?: subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (statusText != null) Color(0xFFC08A2E) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
        Spacer(Modifier.width(8.dp))
        MiniSpectrum(isPlaying = isPlaying)
        Spacer(Modifier.width(8.dp))
        MiniPlayerButton(
            onClick = onPlayPause,
            icon = if (isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "暂停" else "播放"
        )
    }
}

/**
 * 频谱动效：四根小柱随播放跳动，暂停时静止回落（用户保留指令）。
 * 纯视觉实现——相位错开的正弦高度，不读音频数据。
 */
@Composable
private fun MiniSpectrum(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "miniSpectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
        ),
        label = "miniSpectrumPhase",
    )
    val active by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(500),
        label = "miniSpectrumActive",
    )
    // 2026-11-15 用户指令：进度条与频谱统一为浅紫蓝（Palette.BlueLight，深色 #9A8CFF）。
    // 原来频谱用 primary / tertiary 两套颜色、进度线又用 primary，三处色相不一致。
    // draw 块不是组合上下文，色值先在这里取好。
    val barColor = Palette.BlueLight

    Canvas(
        modifier = Modifier
            .width(26.dp)
            .height(22.dp),
    ) {
        val barWidth = size.width / (2f * 4f - 1f)
        for (i in 0 until 4) {
            val wave = if (isPlaying) abs(sin(phase + i * (Math.PI / 2f).toFloat())) else 0.22f
            val level = 0.22f + (wave - 0.22f) * active
            val h = size.height * level.coerceIn(0.18f, 0.96f)
            val x = i * 2f * barWidth
            drawRoundRect(
                color = barColor.copy(alpha = 0.55f + 0.35f * level),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * 卡片底部细进度线。
 *
 * 2026-11-15 BUGFIX（用户报「进度条首尾被盖住」）：原来整宽贴着 34dp 大圆角卡片的
 * 底边，左右两端正好落在圆角里被裁掉。改成左右各内缩 + 整条圆角，端点完整可见。
 * 2026-11-15 第二轮：卡片改 16dp 圆角矩形后，内缩量从 20dp 收到 16dp，与圆角弧度对齐。
 * 颜色统一为 Palette.BlueLight（浅紫蓝，深色 #9A8CFF），未播轨道是同色 22% 透明，
 * 不再用 surfaceVariant / primary 两套色相。
 */
@Composable
private fun MiniProgress(
    positionMs: Long,
    durationMs: Long,
) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val accent = Palette.BlueLight
    val track = accent.copy(alpha = 0.22f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        Box(Modifier.fillMaxSize().background(track))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
    }
}
