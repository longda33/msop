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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * 列表拖动排序（2026-11-16 用户需求「歌单和歌曲条目支持拖动排序」「按住拖动按钮 同时可以滑动条目」）。
 *
 * 做法：按住右侧拖动手柄 → 整行跟手（`graphicsLayer` 位移，不动布局），
 * 越过半行就把目标索引 +1/-1，同时其它行整体让位一格；抬手时把「谁移到哪」交给调用方落库。
 *
 * 为什么不用 `LazyListState` 的自动滚动：歌单一般一屏就能看完，先把「跟着手指走」做对，
 * 长列表的贴边滚动留到真的不够用时再说。
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

    /** 挂在拖动手柄上的手势：长按后整行跟手，抬手把 (from, to) 交给 [onDrop]。 */
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
 */
@Composable
fun ReorderHandle(
    state: ReorderState,
    index: Int,
    count: () -> Int,
    onDrop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .then(state.handleModifier(index, count, onDrop)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PlayerIcons.DragHandle,
            contentDescription = "拖动排序",
            tint = Palette.TextTertiary.copy(alpha = if (state.from == index) 0.9f else 0.5f),
            modifier = Modifier.size(17.dp),
        )
    }
}
