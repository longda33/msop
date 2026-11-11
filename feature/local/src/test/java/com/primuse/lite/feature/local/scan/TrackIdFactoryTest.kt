package com.primuse.lite.feature.local.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdFactoryTest {

    @Test
    fun `同一路径生成同一 id`() {
        val a = TrackIdFactory.localId("/storage/emulated/0/Music/晴天.flac")
        val b = TrackIdFactory.localId("/storage/emulated/0/Music/晴天.flac")
        assertEquals(a, b)
    }

    @Test
    fun `不同路径生成不同 id`() {
        val a = TrackIdFactory.localId("/storage/emulated/0/Music/晴天.flac")
        val b = TrackIdFactory.localId("/storage/emulated/0/Music/晴天.mp3")
        assertNotEquals(a, b)
    }

    @Test
    fun `id 带 local 前缀`() {
        assertTrue(TrackIdFactory.localId("/a/b/c.flac").startsWith("local:"))
    }

    @Test
    fun `反斜杠与重复斜杠被归一化`() {
        val unix = TrackIdFactory.localId("/storage/emulated/0/Music/a.flac")
        val windows = TrackIdFactory.localId("\\storage\\\\emulated\\0\\Music\\a.flac")
        val doubled = TrackIdFactory.localId("/storage//emulated///0/Music/a.flac")
        assertEquals(unix, windows)
        assertEquals(unix, doubled)
    }

    @Test
    fun `首尾空白被忽略`() {
        val clean = TrackIdFactory.localId("/a/b.flac")
        val padded = TrackIdFactory.localId("  /a/b.flac  ")
        assertEquals(clean, padded)
    }

    @Test
    fun `大小写不同视为不同文件`() {
        // 大小写敏感存储上 A.flac 与 a.flac 是两个文件，不能合并
        assertNotEquals(
            TrackIdFactory.localId("/a/A.flac"),
            TrackIdFactory.localId("/a/a.flac"),
        )
    }

    @Test
    fun `归一化不会留下重复斜杠`() {
        assertEquals("/a/b/c.flac", TrackIdFactory.normalizePath("//a\\\\b///c.flac"))
    }
}
