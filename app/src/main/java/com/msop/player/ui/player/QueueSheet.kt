package com.msop.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.core.model.Track
import com.msop.player.core.model.toDurationLabel
import com.msop.player.ui.components.CoverThumb
import com.msop.player.ui.components.GlassPill
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.artSeed
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.LocalMsopPalette
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * 播放队列。用 ModalBottomSheet 而不是自己写拖拽面板：
 * 这里不需要跟手的复杂手势，M3 的面板已经够用，还自带返回键处理。
 *
 * 排序用手柄**长按**拖动（2026-11-17 深夜用户澄清：要的是「一个手指按住拖动按钮，
 * 另一个手指可以滑动列表」——按住=抓取，不能一碰就把列表滚动抢走，已回滚成长按版）。
 * **拖动过程中不重排列表**——每跨一行就重组 LazyColumn
 * 会让 pointerInput 重启、手势被吞掉；改为累计位移 + 高亮目标行，松手才调
 * [onMove] 一次。目标位置由纯函数 [QueueReorder.targetIndex] 计算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentTrackId: String?,
    /** 播放进度（2026-11-18 用户指令）：队列里正在播那一行右侧显示剩余时长并逐秒递减。 */
    positionMs: Long = 0L,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var accumulatedY by remember { mutableStateOf(0f) }
    // 单行占位 = 56dp 内容高 + 上下各 1dp 内边距 = 58dp
    val rowHeightPx = with(LocalDensity.current) { 58.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = Shapes.sheetTop,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF)),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.74f)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "播放队列",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                GlassPill(
                    label = "${queue.size} 首",
                    color = Palette.TextSecondary,
                    background = Color.White.copy(alpha = 0.055f),
                )
                Spacer(Modifier.weight(1f))
                if (dragTarget != null && dragFrom != null && dragTarget != dragFrom) {
                    Text(
                        text = "移到 ${dragTarget!! + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.BlueLight,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconAction(Icons.Filled.Close, "收起", onDismiss)
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(items = queue, key = { _, t -> t.id }) { index, track ->
                    val current = track.id == currentTrackId
                    // pointerInput 用 Unit 作 key，避免拖动中重组重启手势；
                    // 用 rememberUpdatedState 保证回调里读到的永远是最新 index。
                    val latestIndex by rememberUpdatedState(index)
                    val isDragging = dragFrom == index
                    val isDropTarget = dragTarget == index && !isDragging

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 2026-11-15（用户指令）：拖动时整行跟着手指走。
                            // 只做绘制位移、不动列表顺序——顺序留到松手时一次性 move，
                            // 否则每跨一行就重组 LazyColumn，pointerInput 会重启吞手势。
                            .graphicsLayer {
                                translationY = if (isDragging) accumulatedY else 0f
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                            .height(56.dp)
                            // 拖动中给一点浮起感，压住下面的行
                            .shadow(
                                elevation = if (isDragging) 12.dp else 0.dp,
                                shape = Shapes.cardSmall,
                                spotColor = Color.Black.copy(alpha = 0.6f),
                            )
                            .clip(Shapes.cardSmall)
                            .clickable { onSelect(index) }
                            .background(
                                when {
                                    // 拖动中的行必须不透明，否则会和被盖住那行的文字叠在一起
                                    isDragging -> Palette.Surface2
                                    isDropTarget -> Color(0x1A9A8CFF)
                                    current -> Palette.Blue.copy(alpha = 0.14f)
                                    else -> Color.Transparent
                                },
                            )
                            .padding(start = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (current) Palette.BlueLight else Palette.TextTertiary,
                            modifier = Modifier.width(24.dp),
                        )
                        CoverThumb(
                            coverUri = track.coverUri,
                            size = 38.dp,
                            seed = track.artSeed(),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (current) Palette.BlueLight else Palette.TextPrimary,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(track.artist, track.album).joinToString(" · ")
                                    .ifBlank { "—" },
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        Text(
                            // 2026-11-18 用户指令：正在播那一行显示「剩余」并逐秒递减
                            text = if (current && track.durationMs > 0L) {
                                val sec = ((track.durationMs - positionMs).coerceAtLeast(0L) + 999L) / 1000L
                                "-%d:%02d".format(sec / 60, sec % 60)
                            } else {
                                track.durationMs.toDurationLabel()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextTertiary,
                        )
                        Spacer(Modifier.width(4.dp))
                        // 2026-11-17（用户报「浅色模式 队列 拖动按钮看不见」+ 深夜澄清要
                        // 「按住手柄、另一手指滑动列表」）：颜色不再写死白色 33%（浅底等于隐形），
                        // 改主题感知；手势保持长按 500ms 起拖，不抢列表滚动。
                        val isDark = LocalMsopPalette.current.isDark
                        val idleHandleTint =
                            if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF20223A).copy(alpha = 0.42f)
                        Icon(
                            imageVector = PlayerIcons.DragHandle,
                            contentDescription = "长按拖动排序",
                            tint = if (isDragging) Palette.BlueLight else idleHandleTint,
                            modifier = Modifier
                                .size(30.dp)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragFrom = latestIndex
                                            dragTarget = latestIndex
                                            accumulatedY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatedY += dragAmount.y
                                            val from = dragFrom
                                            if (from != null) {
                                                dragTarget = QueueReorder.targetIndex(
                                                    from, accumulatedY, rowHeightPx, queue.size,
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val from = dragFrom
                                            val to = dragTarget
                                            if (from != null && to != null && from != to) onMove(from, to)
                                            dragFrom = null
                                            dragTarget = null
                                            accumulatedY = 0f
                                        },
                                        onDragCancel = {
                                            dragFrom = null
                                            dragTarget = null
                                            accumulatedY = 0f
                                        },
                                    )
                                },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "长按右侧手柄拖动排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "点击歌曲立即播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}
