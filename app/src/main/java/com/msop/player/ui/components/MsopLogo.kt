package com.msop.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MSOP 应用图标（矢量重绘）。
 *
 * 与 `res/drawable-nodpi/msop_icon.png` 同一套几何与色值：黄橙渐变圆底 +
 * 粉蓝渐变的耳机（半圆头梁 + 左右胶囊耳罩 + 三根均衡器竖条）。
 *
 * 为什么不用位图：启动画面要让三根竖条各自跳动，位图做不到「只动其中一根」；
 * 而且 256×256 的位图放大到 92dp 在 3x 屏上会糊。这里全部用 Canvas 画，
 * 坐标沿用图标的 100×100 虚拟画布，按实际尺寸等比缩放。
 *
 * 动画：三根竖条都以底边为轴做 scaleY，收缩幅度与起始延迟各不相同
 * （0.34/0.60·0s、0.40/0.74·0.19s、0.30/0.55·0.38s），读起来是一道
 * 从左往右走的波形；1.15s 一循环，中间那根振幅最大，是视觉主心骨。
 * 图标本体固定尺寸，不做呼吸缩放（用户 2026-11-17 指定）。
 */
@Composable
fun MsopLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 92.dp,
) {
    val transition = rememberInfiniteTransition(label = "msopLogo")
    val barLeft = transition.barBounce(low = 0.34f, mid = 0.60f, delayMs = 0, label = "barLeft")
    val barMid = transition.barBounce(low = 0.40f, mid = 0.74f, delayMs = 190, label = "barMid")
    val barRight = transition.barBounce(low = 0.30f, mid = 0.55f, delayMs = 380, label = "barRight")

    Canvas(modifier.size(logoSize)) {
        // 100×100 虚拟画布 → 实际像素的换算系数。
        val u = size.minDimension / 100f

        // ---- 圆底：左上 #FFE89F → 中 #F8C54B → 右下 #F0B33A ----
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(LogoYellowTop, LogoYellowMid, LogoYellowBottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            // 半径收 0.3：让抗锯齿的那半个像素完整落在画布内，边缘不发毛。
            radius = u * 49.7f,
            center = Offset(u * 50f, u * 50f),
        )

        // ---- 头梁：圆心 (49.8, 53.9)、外径 27.9 / 内径 20.3 的环，只取上半圈 ----
        // 用「半径 24.1 的圆弧 + 7.6 的描边」等价实现这个环，比两条 arcTo 拼路径更稳。
        drawArc(
            brush = iconBrush(u, 21.9f, 26f, 77.7f, 53.9f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(u * 25.7f, u * 29.8f),
            size = Size(u * 48.2f, u * 48.2f),
            style = Stroke(width = u * 7.6f, cap = StrokeCap.Butt),
        )

        // ---- 左右耳罩：11 × 18.5 的胶囊，y 52 → 70.5 ----
        drawRoundRect(
            brush = iconBrush(u, 19.5f, 52f, 30.5f, 70.5f),
            topLeft = Offset(u * 19.5f, u * 52f),
            size = Size(u * 11f, u * 18.5f),
            cornerRadius = CornerRadius(u * 5.5f),
        )
        drawRoundRect(
            brush = iconBrush(u, 69.1f, 52f, 80.1f, 70.5f),
            topLeft = Offset(u * 69.1f, u * 52f),
            size = Size(u * 11f, u * 18.5f),
            cornerRadius = CornerRadius(u * 5.5f),
        )

        // ---- 三根竖条：宽 5.9、底边统一 70.3，各自按 scaleY 伸缩 ----
        drawBar(u, cx = 39.6f, baseHeight = 16f, factor = barLeft.value)
        drawBar(u, cx = 49.8f, baseHeight = 24f, factor = barMid.value)
        drawBar(u, cx = 60.0f, baseHeight = 19f, factor = barRight.value)
    }
}

/** 每根竖条的循环：低位 → 45% 处顶到满高 → 72% 处回落 → 回到低位。 */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.barBounce(
    low: Float,
    mid: Float,
    delayMs: Int,
    label: String,
): State<Float> = animateFloat(
    initialValue = low,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1150
            low at 0
            1f at 520
            mid at 830
            low at 1150
        },
        repeatMode = RepeatMode.Restart,
        initialStartOffset = StartOffset(delayMs),
    ),
    label = label,
)

/** 单根竖条：以底边为轴向上伸缩，宽度与圆角恒定（等价于 CSS 的 scaleY + transform-origin: bottom）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    u: Float,
    cx: Float,
    baseHeight: Float,
    factor: Float,
) {
    val w = 5.9f
    val h = baseHeight * factor
    val top = 70.3f - h
    drawRoundRect(
        brush = iconBrush(u, cx - w / 2f, top, cx + w / 2f, 70.3f),
        topLeft = Offset(u * (cx - w / 2f), u * top),
        size = Size(u * w, u * h),
        cornerRadius = CornerRadius(u * (w / 2f)),
    )
}

/** 每个形状各套一份 45° 粉→蓝渐变（对应原图标 objectBoundingBox 渐变）。 */
private fun iconBrush(u: Float, l: Float, t: Float, r: Float, b: Float): Brush = Brush.linearGradient(
    colors = listOf(LogoPink, LogoPurple, LogoBlue),
    start = Offset(u * l, u * t),
    end = Offset(u * r, u * b),
)

private val LogoYellowTop = Color(0xFFFFE89F)
private val LogoYellowMid = Color(0xFFF8C54B)
private val LogoYellowBottom = Color(0xFFF0B33A)
private val LogoPink = Color(0xFFEA5F98)
private val LogoPurple = Color(0xFFA969BD)
private val LogoBlue = Color(0xFF5B77EA)
