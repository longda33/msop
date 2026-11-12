package com.msop.player.feature.local.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataValuesTest {

    @Test
    fun `缺失标签统一转 null`() {
        assertNull(MetadataValues.clean(null))
        assertNull(MetadataValues.clean(""))
        assertNull(MetadataValues.clean("   "))
        // MediaMetadataRetriever 与 MediaStore 一样会吐这个字面量
        assertNull(MetadataValues.clean("<unknown>"))
        assertNull(MetadataValues.clean("<UNKNOWN>"))
    }

    @Test
    fun `正常标签去首尾空白后保留`() {
        assertEquals("周杰伦", MetadataValues.clean("  周杰伦 "))
        assertEquals("叶惠美", MetadataValues.clean("叶惠美"))
    }

    @Test
    fun `音轨号支持 3 与 3 斜杠 12 两种写法`() {
        assertEquals(3, MetadataValues.parseNumber("3"))
        assertEquals(3, MetadataValues.parseNumber("3/12"))
        assertEquals(12, MetadataValues.parseNumber(" 12 / 20 "))
    }

    @Test
    fun `音轨号 0 与非法值视为未设置`() {
        assertNull(MetadataValues.parseNumber("0"))
        assertNull(MetadataValues.parseNumber("0/12"))
        assertNull(MetadataValues.parseNumber(""))
        assertNull(MetadataValues.parseNumber("A面"))
    }

    @Test
    fun `年份从完整日期里截取`() {
        assertEquals(2021, MetadataValues.parseYear("2021"))
        assertEquals(2021, MetadataValues.parseYear("2021-05-01"))
        assertEquals(1997, MetadataValues.parseYear("1997年"))
    }

    @Test
    fun `非法年份视为未设置`() {
        assertNull(MetadataValues.parseYear("0"))
        assertNull(MetadataValues.parseYear("abcd"))
        assertNull(MetadataValues.parseYear(""))
    }

    @Test
    fun `时长只接受正数`() {
        assertEquals(269_000L, MetadataValues.parseDuration("269000"))
        assertEquals(0L, MetadataValues.parseDuration("0"))
        assertEquals(0L, MetadataValues.parseDuration("-1"))
        assertEquals(0L, MetadataValues.parseDuration(null))
    }
}
