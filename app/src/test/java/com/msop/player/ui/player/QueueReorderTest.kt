package com.msop.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueReorderTest {

    @Test
    fun `不动时保持原位`() {
        assertEquals(3, QueueReorder.targetIndex(3, 0f, 100f, 10))
    }

    @Test
    fun `下拖整行跨到下一项`() {
        assertEquals(4, QueueReorder.targetIndex(3, 100f, 100f, 10))
        assertEquals(5, QueueReorder.targetIndex(3, 200f, 100f, 10))
    }

    @Test
    fun `上拖整行跨到上一项`() {
        assertEquals(2, QueueReorder.targetIndex(3, -100f, 100f, 10))
        assertEquals(0, QueueReorder.targetIndex(3, -999f, 100f, 10))
    }

    @Test
    fun `半行以内四舍五入`() {
        assertEquals(3, QueueReorder.targetIndex(3, 49f, 100f, 10))
        assertEquals(4, QueueReorder.targetIndex(3, 51f, 100f, 10))
        assertEquals(2, QueueReorder.targetIndex(3, -51f, 100f, 10))
    }

    @Test
    fun `越界被夹到队列两端`() {
        assertEquals(9, QueueReorder.targetIndex(3, 10_000f, 100f, 10))
        assertEquals(0, QueueReorder.targetIndex(3, -10_000f, 100f, 10))
    }

    @Test
    fun `退化输入不崩`() {
        assertEquals(3, QueueReorder.targetIndex(3, 100f, 0f, 10))
        assertEquals(3, QueueReorder.targetIndex(3, 100f, 100f, 0))
        assertEquals(-1, QueueReorder.targetIndex(-1, 100f, 100f, 0))
    }
}
