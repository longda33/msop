package com.msop.player.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.LocalMsopPalette
import com.msop.player.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * 列表拖动排序（2026-11-16 用户需求「歌单和歌曲条目支持拖动排序」「按住拖动按钮 同时可以滑动条目」）。
 *
 * 做法：按住右侧拖动手柄 → 整行跟手（`graphicsLayer` 位移，不动布局），
 * 越过半行就把目标索引 +1/-1，同时其它行整体让位一格；抬手时把「谁移到哪」交给调用方落库。
 *
 * 2026-11-17（用户报「浅色模式 队列 拖动按钮看不见」/ 深夜「按住拖动按钮，另一个手指滑动列表」）：
 * - 颜色改主题感知：深色白 45%，浅色墨 42%，被按住那行亮成强调紫（原来 `TextTertiary 50%` 浅底几乎看不见）；
 * - 手势保持/恢复 `detectDragGesturesAfterLongPress`——用户要的是「按住手柄 = 抓取」，不是一碰就抢手势。
 */
@Stable
class ReorderState(private val rowHeightPx: Float) {

    var from by mutableStateOf(-1)
        private set
    var to by mutableStateOf(-1)
        private set
    private var offset by mutableStateOf(0f)

    val active: Boolean get() = from >= 0

    fun start(index: Int) {
        from = index
        to = index
        offset = 0f
    }

    fun drag(dy: Float, count: Int) {
        if (from < 0) return
        offset += dy
        val shift = (offset / rowHeightPx).roundToInt()
        to = (from + shift).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun reset() {
        from = -1
        to = -1
        offset = 0f
    }

    /** 抬手：返回 (from, to)，没动过返回 null。 */
    fun finish(): Pair<Int, Int>? {
        val f = from
        val t = to
        reset()
        return if (f >= 0 && t >= 0 && f != t) f to t else null
    }

    /** 每行的位移：被按住的那行跟手，被挤开的其余行各让一格。 */
    fun translationFor(index: Int): Float = when {
        from < 0 -> 0f
        index == from -> offset
        from < to && index in (from + 1)..to -> -rowHeightPx
        from > to && index in to until from -> rowHeightPx
        else -> 0f
    }

    /**
     * 挂在拖动手柄上的手势：**长按 500ms 起拖**，抬手把 (from, to) 交给 [onDrop]。
     *
     * 2026-11-17 深夜用户回滚说明：「要的是一个手指按住拖动按钮，另一个手指可以滑动列表」
     * ——用户要的是「按住 = 抓取」，不是「一碰就抢手势」。所以把白天改成
     * `detectDragGestures` 的那次改动撤回，恢复长按版本；手柄尺寸也回到 28dp。
     * 保留的只有颜色：浅色主题下原来的 `TextTertiary 50%` 在浅底上几乎看不见。
     */
    fun handleModifier(
        index: Int,
        count: () -> Int,
        onDrop: (Int, Int) -> Unit,
    ): Modifier = Modifier.pointerInput(index, rowHeightPx) {
        detectDragGesturesAfterLongPress(
            onDragStart = { start(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                drag(dragAmount.y, count())
            },
            onDragEnd = { finish()?.let { (f, t) -> onDrop(f, t) } },
            onDragCancel = { reset() },
        )
    }
}

/**
 * 拖动手柄。按住它时整行跟手——用户明确要求「按住拖动按钮 同时可以滑动条目」，
 * 所以手柄只是抓手，位移挂在整行上。
 *
 * 颜色：浅色主题下原来那档「灰 50%」几乎看不见，改成墨色 42%；深色仍用白 45%
 * （原来的 `TextTertiary` 在深底上偏灰，稍提亮更清楚）。
 */
@Composable
fun ReorderHandle(
    state: ReorderState,
    index: Int,
    count: () -> Int,
    onDrop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalMsopPalette.current.isDark
    val idleTint = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF20223A).copy(alpha = 0.42f)
    Box(
        modifier = modifier
            .size(28.dp)
            .then(state.handleModifier(index, count, onDrop)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PlayerIcons.DragHandle,
            contentDescription = "长按拖动排序",
            tint = if (state.from == index) Palette.BlueLight else idleTint,
            modifier = Modifier.size(17.dp),
        )
    }
}
