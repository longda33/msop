package com.msop.player.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 启动画面（2026-11-17 定稿「极光呼吸」）。
 *
 * 极光背景（复用 [AmbientBackground]，跟随 App 的深浅色主题）→ 中央应用图标固定 92dp，
 * 只有图标里的三根竖条在跳动 → 品牌字字距从 0.90em 收到 0.40em 落定 →
 * 底部一条细进度条承接真实初始化。
 *
 * 不做整枚图标的呼吸缩放（用户指定）：缩放会让人误以为「卡住了在重试」，
 * 固定尺寸 + 局部微动效更稳。
 *
 * **不针对 Android 12+ 的系统启动图做任何适配**（用户 2026-11-17 指定）：
 * 系统启动图是进程启动前由系统按 manifest 主题画好的，改不动，真机上也感知不到；
 * 这里就按设计稿本来的样子做，跟随主题，不为它扭曲视觉。
 *
 * 这是覆盖层，由 MainActivity 在首帧盖在整个 App 之上，淡出后卸载；
 * 它不持有任何业务状态，也不阻塞权限申请。
 *
 * **进度条（2026-11-17 用户指令「改为实际进度，不要反复进度」）**：
 * 不再用 `rememberInfiniteTransition` 每 2.6s 从头扫一遍——那种循环读起来像
 * 「永远加载不完」。现在进度由调用方按真实加载状态传入（[progress]，0..1），
 * 这里只把它单调地追上去，到位就停住，不回落、不重播。
 */
@Composable
fun SplashScreen(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // 首帧先按「起始态」组合，再翻到终态，动画才会真正跑起来（否则直接是终态）。
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    // 时长照设计稿原值，不自作主张调快（用户 2026-11-17：其它不要修改）：
    // 字距 1.5s 收完、品牌字 1.1s 淡入、副标题 0.9s 后起淡入。
    val spacing by animateFloatAsState(
        targetValue = if (started) 0.40f else 0.90f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
        ),
        label = "splashSpacing",
    )
    val brandAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, delayMillis = 220),
        label = "splashBrandAlpha",
    )
    val subAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 900),
        label = "splashSubAlpha",
    )

    // 真实进度只做一次单调推进：目标变了就追过去，追到就停，不循环。
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 420,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "splashProgress",
    )

    Box(
        modifier
            .fillMaxSize()
            // 覆盖层要吃掉这段时间的所有触摸：否则用户在启动画面还在的 1.1s 里
            // 点到下面已经渲染出来的列表，会「看不见地」触发一次播放。
            // 用无涟漪 clickable 而不是 pointerInput，和 EnteringPage 的做法保持一致。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MsopLogo(logoSize = 92.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                text = "MSOP",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.TextPrimary,
                letterSpacing = spacing.em,
                modifier = Modifier.graphicsLayer { alpha = brandAlpha },
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = "私人音乐库",
                fontSize = 10.5.sp,
                color = Palette.TextSecondary,
                letterSpacing = 0.26.em,
                modifier = Modifier.graphicsLayer { alpha = subAlpha },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(2.5.dp)
                    .clip(Shapes.pill)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shown)
                        .fillMaxHeight()
                        .clip(Shapes.pill)
                        .background(Gradients.accent),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = "正在载入曲库",
                fontSize = 9.5.sp,
                color = Palette.TextTertiary,
                letterSpacing = 0.1.em,
            )
        }
    }
}
