package com.msop.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.msop.player.feature.player.PlayMode
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 弧形菜单的布局与动效照抄开源项目 JellyFab（Apache-2.0）：
 *   https://github.com/iprashantpanwar/JellyFab
 *   jellyfab/src/commonMain/kotlin/io/github/iprashantpanwar/jellyfab/JellyArcLayout.kt
 *   jellyfab/src/commonMain/kotlin/io/github/iprashantpanwar/jellyfab/JellyFab.kt（SecondaryFabItem）
 *   jellyfab/src/commonMain/kotlin/io/github/iprashantpanwar/jellyfab/JellyPhysics.kt（expandSecondaryLayer）
 * 采用的部分：`evenArcAngles` 均分弧角、`arcOffset` 极坐标→笛卡尔（Y 取负）、
 * `lerpOffset` 链式起点、"先过冲到 1.06 再用 MediumBouncy 回落到 1"的展开曲线，
 * 以及 `alpha = p / scale = 0.8 + 0.2p` 的出现效果。
 * 只保留这三段纯函数与动效参数，未引入该库其余代码（jelly 表面、拖拽、blob 等）。
 */

/** 播放模式选项（扇形菜单里的一项）。 */
internal data class PlayModeOption(
    val mode: PlayMode,
    val icon: ImageVector,
    val label: String,
    val description: String,
)

internal val playModeOptions = listOf(
    PlayModeOption(PlayMode.SEQUENTIAL, PlayerIcons.Order, "顺序", "顺序播放"),
    PlayModeOption(PlayMode.SHUFFLE, PlayerIcons.Shuffle, "随机", "随机播放"),
    PlayModeOption(PlayMode.REPEAT_ALL, PlayerIcons.Repeat, "循环", "列表循环"),
    PlayModeOption(PlayMode.REPEAT_ONE, PlayerIcons.RepeatOne, "单曲", "单曲循环"),
)

internal fun modeIcon(mode: PlayMode): ImageVector =
    playModeOptions.first { it.mode == mode }.icon

internal fun modeDescription(mode: PlayMode): String =
    playModeOptions.first { it.mode == mode }.description

/**
 * 弧上均分角度（抄自 JellyFab `evenArcAngles`）。
 * 例：start=90, end=35, count=3 → [90, 62.5, 35]。
 */
internal fun evenArcAngles(startDeg: Double, endDeg: Double, count: Int): List<Double> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(startDeg)
    val step = (endDeg - startDeg) / (count - 1)
    return List(count) { index -> startDeg + index * step }
}

/**
 * 极坐标 → 笛卡尔（抄自 JellyFab `arcOffset`）。
 * Y 取负是因为 Compose 的坐标系向下为正。
 */
internal fun arcOffset(angleDeg: Double, radius: Float): Offset {
    val rad = angleDeg * PI / 180.0
    return Offset(
        (cos(rad) * radius).toFloat(),
        (-sin(rad) * radius).toFloat(),
    )
}

/** 两点线性插值（抄自 JellyFab `lerpOffset`），用于「从上一项当前位置冒出来」的链式展开。 */
internal fun lerpOffset(a: Offset, b: Offset, t: Float): Offset {
    val c = t.coerceIn(0f, 1f)
    return Offset(a.x + (b.x - a.x) * c, a.y + (b.y - a.y) * c)
}

/**
 * 播放模式扇形菜单（2026-11-15 用户指令）。
 *
 * - **只列「当前模式以外」的三档**，当前档不重复出现在菜单里。
 * - **形态固定为等半径圆弧扇形**：三项均匀分布在 90° → 10° 的同一段弧上，
 *   圆心就是播放模式按钮。先前为了「拉近」改成链式累加，把扇形整个改没了
 *   （用户明确要求修复），已恢复。
 * - **「离按钮太远」的正确解法是扩大张角、不是改形态**：等半径弧上相邻弦长
 *   `2R·sin(Δθ/4)`，旧参数 R=130dp / Δθ=55° / 项 52dp 得到弦长 61.8dp；
 *   现在 R=86dp / Δθ=80° / 项 52dp 得到 58.8dp（间隙 6.8dp，仍不重叠），
 *   但所有项到按钮的距离统一从 130dp 缩到 **86dp**。
 *   选项尺寸、图标、文字、链式展开动效全部保持原样，只动了张角与半径。
 * - 动效照抄 JellyFab：逐项 stagger 70ms，先过冲到 1.06 再弹簧回落到 1；
 *   每一项的起点是「前一项此刻的位置」，所以看起来是从按钮里一段一段抽出来的。
 *
 * @param anchor 按钮中心在弹层坐标系里的位置（px）。
 * @param radiusDp 扇形半径（= 每一项到按钮中心的距离）。
 */
@Composable
internal fun PlayModeFan(
    options: List<PlayModeOption>,
    anchor: Offset,
    radiusDp: Dp = 86.dp,
    itemSize: Dp = 52.dp,
    onPick: (PlayMode) -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }
    val halfPx = with(density) { itemSize.toPx() } / 2f

    // 等半径圆弧：三项落在同一段 90°→10° 的弧上，才是扇形（不是链条）。
    val targets = remember(options, radiusPx) {
        val angles = evenArcAngles(90.0, 10.0, options.size)
        angles.map { arcOffset(it, radiusPx) }
    }
    val progress = remember(options) { options.map { Animatable(0f) } }

    LaunchedEffect(options) {
        progress.forEachIndexed { index, anim ->
            launch {
                delay(index * 70L)
                anim.animateTo(1.06f, tween(320, easing = FastOutSlowInEasing))
                anim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }

    options.forEachIndexed { index, option ->
        Box(
            modifier = Modifier
                .offset {
                    // 链式起点：前一项当前所在位置，逐段逼近本项的目标点。
                    var origin = Offset.Zero
                    for (i in 0 until index) {
                        origin = lerpOffset(origin, targets[i], progress[i].value)
                    }
                    val current = lerpOffset(origin, targets[index], progress[index].value)
                    IntOffset(
                        (anchor.x + current.x).roundToInt() - halfPx.roundToInt(),
                        (anchor.y + current.y).roundToInt() - halfPx.roundToInt(),
                    )
                }
                .size(itemSize)
                .graphicsLayer {
                    val p = progress[index].value.coerceIn(0f, 1f)
                    alpha = p
                    scaleX = 0.8f + 0.2f * p
                    scaleY = 0.8f + 0.2f * p
                }
                .clip(CircleShape)
                .background(Palette.Surface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                )
                .clickable { onPick(option.mode) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.description,
                    tint = Palette.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
