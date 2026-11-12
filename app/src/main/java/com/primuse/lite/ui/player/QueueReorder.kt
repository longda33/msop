package com.primuse.lite.ui.player

import kotlin.math.round

/**
 * 队列拖拽的目标位置计算，抽成纯函数方便 JVM 单测。
 *
 * 拖拽期间**不**实时重排列表：每跨过一行就重组 `LazyColumn` 会让
 * `pointerInput` 重启、手势被吞掉（Compose 拖拽排序最常见的坑）。
 * 所以只在拖动中累计位移并高亮目标行，松手时一次性调用 `moveInQueue`。
 */
object QueueReorder {
    fun targetIndex(
        startIndex: Int,
        dragPixels: Float,
        rowHeightPx: Float,
        itemCount: Int,
    ): Int {
        if (itemCount <= 0 || rowHeightPx <= 0f) return startIndex
        val steps = round(dragPixels / rowHeightPx).toInt()
        return (startIndex + steps).coerceIn(0, itemCount - 1)
    }
}
