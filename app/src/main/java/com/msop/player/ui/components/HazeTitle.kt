package com.msop.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import dev.chrisbanes.haze.HazeState

/**
 * 统一的页面标题栏外壳（名字沿用 P7-09 的 Haze 时期，现已是纯透明）。
 *
 * **2026-11-14 用户指令「顶部改回透明」**：标题栏不绘制任何磨砂、垫色、渐变或模糊，
 * 直接透出 AmbientBackground。为避免滚动内容跑到标题文字背后，结构从原来的
 * 「内容铺满 + 标题栏覆盖」改成「标题栏占位 + 内容占剩余空间」的 Column：
 * 列表内容永远在标题栏下方，文字始终清晰，也不需要任何 scrim 兜底。
 *
 * 各页面传的 [contentTopInset] 与 [LocalHazeTopInset] 现在一律按 0 处理——它们原本
 * 用于给覆盖式标题栏让位，占位布局已不需要。保留参数与 CompositionLocal 是为了
 * 不改动全部调用点，页面读到的仍是合法的 0.dp。
 *
 * **禁止**在本组件内 `drawBackdrop(LocalLayerBackdrop.current)`：外壳的共享
 * LayerBackdrop 用 `layerBackdrop()` 记录了整个内容子树，标题栏在其中时消费同一
 * 对象会造成 RenderNode 自引用递归、RenderThread 栈溢出闪退（2026-11-14 实测
 * 512 帧 prepareTreeImpl 后 SIGSEGV）。底部液态玻璃仍走 Kyant0 Backdrop。
 *
 * 本组件只管视觉，不改导航与点击语义。
 */
val HazeTitleHeight: Dp = 72.dp

/**
 * 历史遗留：覆盖式标题栏时期给滚动内容留出的顶部高度。
 * 标题栏改回透明占位布局后恒为 0.dp，保留供旧页面代码读取。
 */
val LocalHazeTopInset = compositionLocalOf { 0.dp }

/**
 * P7-10：底部液态玻璃层（迷你条 + 导航胶囊）占住的高度。滚动列表的
 * `contentPadding.bottom` 要加上它，内容才能从胶囊后方经过并被磨砂。
 * 由 `MsopApp` 提供（是否显示迷你条会改变它）；无外壳时为 0。
 */
val LocalHazeBottomInset = compositionLocalOf { 0.dp }

/**
 * P7-10：外壳提供的共享 HazeState。标题栏改回透明后当前无消费者，
 * 保留定义与外壳的 provider 仅为兼容，不要再据此给标题栏加 Haze 模糊。
 */
val LocalSharedHazeState = compositionLocalOf<HazeState?> { null }

/**
 * P7-10：外壳提供的共享 Kyant0 LayerBackdrop。底部液态玻璃胶囊/迷你条从它
 * 采样；null（独立预览等）时各组件走降级底。标题栏不使用它。
 */
val LocalLayerBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
fun HazeTitleScaffold(
    title: String,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** 二级页传它即可在标题栏左侧获得统一的返回键。 */
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    /** 标题栏下方不随列表滚动的固定 UI（分类 Tab、搜索框等）。 */
    pinnedHeader: @Composable () -> Unit = {},
    /** [contentModifier] 只提供填充尺寸；顶部留白已由占位布局接管。 */
    content: @Composable (contentModifier: Modifier, topInset: Dp) -> Unit,
) {
    // contentTopInset 仅为兼容旧调用点保留：透明 + 占位布局不需要任何顶部让位。
    // 不画背景、不加 blur、不加渐隐，标题栏就是页面背景本身。
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            ScreenTitle(
                title = title,
                subtitle = subtitle,
                leading = onBack?.let { back ->
                    { IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = back) }
                },
                actions = actions,
            )
            pinnedHeader()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalHazeTopInset provides 0.dp) {
                content(Modifier.fillMaxSize(), 0.dp)
            }
        }
    }
}
