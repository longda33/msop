package com.msop.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msop.player.core.model.Track
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Palette

/**
 * 播放队列。用 ModalBottomSheet 而不是自己写拖拽面板：
 * 这里不需要跟手的复杂手势，M3 的面板已经够用，还自带返回键处理。
 *
 * 排序用手柄长按拖动。**拖动过程中不重排列表**——每跨一行就重组 LazyColumn
 * 会让 pointerInput 重启、手势被吞掉；改为累计位移 + 高亮目标行，松手才调
 * [onMove] 一次。目标位置由纯函数 [QueueReorder.targetIndex] 计算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentTrackId: String?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var accumulatedY by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 54.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Surface,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x2EFFFFFF)),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "播放队列",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " ${queue.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (dragTarget != null && dragFrom != null && dragTarget != dragFrom) {
                    Text(
                        text = "移到 ${dragTarget!! + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.BlueLight,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = "收起",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.BlueLight,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
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
                            .height(54.dp)
                            .clickable { onSelect(index) }
                            .background(
                                when {
                                    isDragging -> Palette.BlueWash
                                    isDropTarget -> Color(0x1A5B76FF)
                                    current -> Palette.BlueWash
                                    else -> Color.Transparent
                                },
                            )
                            .padding(start = 20.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (current) Palette.BlueLight else Palette.TextTertiary,
                            modifier = Modifier.width(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (current) Palette.BlueLight else Palette.TextPrimary,
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
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = PlayerIcons.Queue,
                            contentDescription = "长按拖动排序",
                            tint = if (isDragging) Palette.BlueLight else Color(0x55FFFFFF),
                            modifier = Modifier
                                .size(28.dp)
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

            Text(
                text = "长按右侧手柄可拖动排序",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}
