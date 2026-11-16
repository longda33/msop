package com.msop.player.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.LocalMsopPalette
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.LayerBackdrop

/** 全 App 的底：深空底色 + 两团极光（浅色为香芋紫云雾），避免大块纯黑显得廉价。 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    // 色值先在组合上下文取，再进 Canvas 的 draw 块（draw 不是 composable）。
    val ink = Palette.Ink
    val void = Palette.Void
    val blue = Palette.Blue
    val teal = Palette.Teal
    // 深色：上压深一层；浅色：顶部提亮一层，形成同样的纵深。
    val mid = if (LocalMsopPalette.current.isDark) Color(0xFF0D1020) else Color(0xFFF8F7FE)
    Box(modifier.fillMaxSize().background(ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(mid, ink, void),
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(blue.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.86f, -h * 0.02f),
                    radius = w * 0.95f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(teal.copy(alpha = 0.075f), Color.Transparent),
                    center = Offset(w * 0.02f, h * 0.62f),
                    radius = w * 0.75f,
                ),
            )
        }
    }
}

/**
 * 统一的玻璃卡片：深色渐变 + 1dp 高光描边 + 可选阴影。
 * 不做真实 blur（每屏都开 RenderEffect 会掉帧），用低对比渐变模拟。
 * 2026-11-14：随主题取色（@Composable 修饰，调用点均在组合期内）。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = Shapes.card,
    color: Color = Palette.Surface,
    elevation: Dp = 0.dp,
    borderAlpha: Float = 1f,
): Modifier {
    // 色值先取再构造（默认参数求值也在组合期，但内部引用需外提）。
    val deep = Palette.Surface.copy(alpha = 0.92f)
    var base = this
    if (elevation > 0.dp) {
        base = base.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.55f),
            spotColor = Color.Black.copy(alpha = 0.55f),
        )
    }
    return base
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(color.copy(alpha = 0.98f), deep),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.13f * borderAlpha),
                    Color.White.copy(alpha = 0.03f * borderAlpha),
                ),
            ),
            shape = shape,
        )
}

/** 分组标题：左侧标题 + 可选右侧动作。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        action?.invoke()
    }
}

/** 主按钮：极光渐变胶囊。 */
@Composable
fun PrimaryPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(Shapes.pill)
            .background(if (enabled) Gradients.accent else Brush.linearGradient(listOf(Palette.Surface3, Palette.Surface3)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color.White else Palette.TextTertiary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Color.White else Palette.TextTertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 玻璃描边胶囊，用于角标、状态、次级筛选。 */
@Composable
fun GlassPill(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.TextSecondary,
    background: Color = Color(0x66000000),
) {
    Box(
        modifier = modifier
            .clip(Shapes.pill)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.10f), Shapes.pill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** 圆形玻璃按钮，用于播放页顶栏与各处返回键。 */
@Composable
fun CircleGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    tint: Color = Palette.TextPrimary,
    container: Color = Color(0x24FFFFFF),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
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

/** 设置页/详情页里的图标底板。 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** 分割线之前：液态玻璃（P7-10 → 2026-11-14 换 Kyant0 Backdrop）——底部导航胶囊与迷你条的统一底。 */

/**
 * 液态玻璃底（Kyant0 Backdrop，Apache-2.0；与 ShuYin 同一上游库）。
 *
 * API 33+：折射透镜 + 色差 + 磨砂 + 饱和度提升（ShuYin 的完整观感）；
 * API 31–32：磨砂 + 饱和度（无透镜）；低版本：高不透明渐变底（不透明主题色），
 * 不触发 RenderEffect，不崩溃。叠加顶部高光带 + 1dp 内描边。
 *
 * 底部胶囊与迷你条从 [backdrop]（挂在全屏内容上的 [LayerBackdrop]）采样，
 * 与标题栏的 Haze 毛玻璃是两套独立机制，互不干扰。
 * [backdrop] 为 null（独立预览等）时走降级底。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop?,
    shape: Shape = Shapes.pill,
    elevation: Dp = 14.dp,
): Modifier {
    val p = LocalMsopPalette.current
    // 色值先在组合期取，再进 draw 块（draw 非组合上下文）。
    val topHighlight = Color.White.copy(alpha = if (p.isDark) 0.16f else 0.55f)
    val bottomGlow = p.BlueLight.copy(alpha = if (p.isDark) 0.05f else 0.10f)
    val surfaceTop = p.Surface2.copy(alpha = if (p.isDark) 0.50f else 0.60f)
    val surfaceBottom = p.Surface.copy(alpha = if (p.isDark) 0.42f else 0.55f)
    val lensOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val glassBase = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 效果顺序：color filter ⇒ blur ⇒ lens（官方约束）。
                // 2026-11-14 调参：折射透镜过强（40dp）会把胶囊后方内容扭成
                // 鱼眼泡泡，观感像渲染 bug（用户反馈「显示有问题」）。
                // 对照 ShuYin 实拍：磨砂为主 + 轻微边缘折射——
                // blur 提到 10dp 加强磨砂，lens 降到 12/18dp 只做边缘弯光。
                vibrancy()
                blur(10.dp.toPx())
                if (lensOk) lens(
                    refractionHeight = 12.dp.toPx(),
                    refractionAmount = 18.dp.toPx(),
                    chromaticAberration = true,
                )
            },
            onDrawSurface = {
                // 可读性垫色：透镜/磨砂再美，也要保证胶囊上文字可读。
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(surfaceTop, surfaceBottom),
                    ),
                )
            },
        )
    } else {
        // 降级：高不透明双层渐变，观感接近但不采样。
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    p.Surface2.copy(alpha = 0.93f),
                    p.Surface.copy(alpha = 0.97f),
                ),
            ),
        )
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.55f),
            spotColor = Color.Black.copy(alpha = 0.55f),
        )
        .then(glassBase)
        .clip(shape)
        .drawWithContent {
            drawContent()
            // 顶部折射高光带：亮→透明，模拟玻璃上缘的光线弯曲。
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topHighlight,
                        topHighlight.copy(alpha = topHighlight.alpha * 0.12f),
                        Color.Transparent,
                    ),
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height * 0.42f),
            )
            // 底部回光：向上渐隐的冷色提亮，加厚玻璃感。
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, bottomGlow),
                ),
                topLeft = Offset(0f, size.height * 0.72f),
                size = Size(size.width, size.height * 0.28f),
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (p.isDark) 0.20f else 0.65f),
                    Color.White.copy(alpha = if (p.isDark) 0.06f else 0.25f),
                ),
            ),
            shape = shape,
        )
}

/**
 * P7-11 iOS 式二级页转场：主内容常驻（把压缩/渐暗动画挂在其根节点 modifier 上，
 * 例如 `HazeTitleScaffold(modifier = Modifier.graphicsLayer { … })`），二级页以
 * overlay `AnimatedContent` 形式右滑入。用「常驻主内容 + overlay」而不是把主内容
 * 塞进 AnimatedContent：主内容的滚动位置与内部状态在打开/关闭二级页时完全保留，
 * 与改造前 `if/return` 分支之外的行为一致；快速连点时 AnimatedContent 打断在途
 * 动画，不会叠加崩溃；返回键行为由各二级页自己的 BackHandler 决定。
 *
 * 典型用法（页面内）：
 * ```
 * val secondary = when { … }                       // null = 主内容
 * val open = secondary != null
 * val bgScale by animateFloatAsState(if (open) 0.96f else 1f, stackSpring, label = "bg")
 * val bgAlpha by animateFloatAsState(if (open) 0.55f else 1f, tween(180), label = "dim")
 * MainScreen(modifier = Modifier.graphicsLayer { scaleX = bgScale; scaleY = bgScale; alpha = bgAlpha })
 * AnimatedContent(secondary, transitionSpec = { iosStackSpec(targetState != null) }) { page ->
 *     if (page != null) EnteringPage { SecondaryScreen(page) } else Box(Modifier.fillMaxSize())
 * }
 * ```
 */
fun iosStackSpec(opening: Boolean): ContentTransform = if (opening) {
    // 打开：二级页右滑入 + 0.94→1 放大 + 淡入；旧层（空占位）直接消失。
    (
        slideInHorizontally(pageSpringOffset) { it } +
            scaleIn(pageSpringScale, initialScale = 0.94f) +
            fadeIn(tween(170))
        ) togetherWith ExitTransition.None
} else {
    // 关闭：二级页右滑出 + 缩回 0.94 + 淡出；空占位即时就位。
    EnterTransition.None togetherWith (
        slideOutHorizontally(pageSpringOffset) { it } +
            scaleOut(pageSpringScale, targetScale = 0.94f) +
            fadeOut(tween(130))
        )
}

/** P7-11 统一弹簧：与 `docs/08` §2 的 iOS 转场参数一致（位移动画需要 IntOffset 版）。 */
private val pageSpringScale = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
private val pageSpringOffset = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 380f)

/** 进入页容器：24dp 圆角收拢到 0（P7-11）。仅作用于进场，离场保持直角滑出。
 *
 * 半径动画必须用**临界阻尼**（dampingRatio = 1）：欠阻尼弹簧会让 24dp→0 过冲到
 * 负值，`RoundedCornerShape(负数)` 会抛 IllegalArgumentException 直接崩溃（真机
 * 2026-11-14 实测踩过）；再叠一层 coerceAtLeast(0.dp) 兜底。位移动画（slide/scale）
 * 的过冲无害，仍用 0.85 弹簧。 */
@Composable
fun EnteringPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val radius by animateDpAsState(
        targetValue = if (settled) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "pageCorner",
    )
    Box(
        modifier
            .fillMaxSize()
            // 2026-11-16 晚 BUGFIX（用户报「已移除歌曲界面点空白处怎么会点到歌曲播放」）：
            // 二级页是压在主内容上的兄弟节点，页面根节点本身没有任何 pointer 处理时，
            // Compose 在空白处命中不到它，就把事件交给底下还在的曲库列表 —— 于是点页面
            // 空处会误触发一首歌。根节点挂一个「无涟漪、什么都不做」的 clickable 后，
            // 整个页面成为命中的目标，空白点击被吃掉；页面内的按钮/列表仍是子节点，
            // 事件优先给它们，行为不变。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .graphicsLayer {
                shape = RoundedCornerShape(radius.coerceAtLeast(0.dp))
                clip = true
            },
    ) {
        // 不透明页面底：二级页原先以「整页替换」形态存在、背景透出应用环境即可；
        // P7-11 改为叠在主内容之上后，必须自带底色压住被压缩的主内容，
        // 否则会透出半透明鬼影。复用 AmbientBackground 保持全 App 视觉一致。
        AmbientBackground()
        content()
    }
}

/** 细分割线，默认从图标右侧开始，不顶到卡片边缘。 */
@Composable
fun InsetDivider(modifier: Modifier = Modifier, startPadding: Dp = 64.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.055f)),
    )
}
