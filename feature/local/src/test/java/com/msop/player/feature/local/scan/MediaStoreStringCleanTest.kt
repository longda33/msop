package com.msop.player.feature.local.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 真机测试发现的 bug 回归测试：
 * MediaStore 用字面量 `<unknown>` 表示缺失标签，不转成 null 会直接显示在 UI 上。
 */
class MediaStoreStringCleanTest {

    @Test
    fun `unknown 占位符被转成 null`() {
        assertNull("<unknown>".clean())
    }

    @Test
    fun `空白字符串被转成 null`() {
        assertNull("".clean())
        assertNull("   ".clean())
        assertNull(null.clean())
    }

    @Test
    fun `正常值保留并去掉首尾空格`() {
        assertEquals("周杰伦", "  周杰伦  ".clean())
        assertEquals("Queen", "Queen".clean())
    }

    @Test
    fun `大小写不同的 unknown 不是占位符`() {
        // 只有全小写的 <unknown> 才是 MediaStore 的哨兵值
        assertEquals("<UNKNOWN>", "<UNKNOWN>".clean())
    }
}
