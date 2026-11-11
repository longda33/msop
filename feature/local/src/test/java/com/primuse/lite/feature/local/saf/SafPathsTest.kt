package com.primuse.lite.feature.local.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafPathsTest {

    @Test
    fun `内部存储的 documentId 还原为绝对路径`() {
        assertEquals(
            "/storage/emulated/0/Music/周杰伦/晴天.flac",
            documentIdToAbsolutePath("primary:Music/周杰伦/晴天.flac"),
        )
    }

    @Test
    fun `SD 卡的 documentId 还原为对应卷路径`() {
        assertEquals(
            "/storage/1A2B-3C4D/Music/a.flac",
            documentIdToAbsolutePath("1A2B-3C4D:Music/a.flac"),
        )
    }

    @Test
    fun `重复斜杠被归一化`() {
        assertEquals(
            "/storage/emulated/0/Music/a.flac",
            documentIdToAbsolutePath("primary:/Music//a.flac"),
        )
    }

    @Test
    fun `缺少分隔符时返回 null`() {
        assertNull(documentIdToAbsolutePath("no-colon-here"))
    }

    @Test
    fun `冒号后为空时返回 null`() {
        assertNull(documentIdToAbsolutePath("primary:"))
    }

    @Test
    fun `冒号在开头时返回 null`() {
        assertNull(documentIdToAbsolutePath(":Music/a.flac"))
    }

    @Test
    fun `primary 大小写不敏感`() {
        assertEquals(
            "/storage/emulated/0/Music/a.flac",
            documentIdToAbsolutePath("PRIMARY:Music/a.flac"),
        )
    }
}
