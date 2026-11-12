package com.msop.player.feature.local.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoverCacheTest {

    private lateinit var dir: File
    private lateinit var cache: CoverCache

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("msop-covers-test").toFile()
        cache = CoverCache(dir, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `同样内容只落一份文件`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)

        val first = cache.ensure(bytes)
        val second = cache.ensure(bytes)

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, cache.count())
    }

    @Test
    fun `不同内容落成不同文件`() = runTest {
        cache.ensure(byteArrayOf(1, 2, 3))
        cache.ensure(byteArrayOf(4, 5, 6))

        assertEquals(2, cache.count())
    }

    @Test
    fun `空字节不落盘`() = runTest {
        assertNull(cache.ensure(ByteArray(0)))
        assertEquals(0, cache.count())
    }

    @Test
    fun `existing 只认真正存在的文件`() = runTest {
        val file = cache.ensure(byteArrayOf(9, 9, 9))!!

        assertEquals(file, cache.existing(file.toCoverUri()))
        assertNull(cache.existing(null))
        assertNull(cache.existing(""))

        // 模拟「设置页清缓存」：文件没了，数据库里的 coverUri 就成了悬空指针
        file.delete()
        assertNull(cache.existing(file.toCoverUri()))
    }

    @Test
    fun `existing 也接受裸路径`() = runTest {
        val file = cache.ensure(byteArrayOf(7, 7))!!

        assertEquals(file, cache.existing(file.absolutePath))
    }

    @Test
    fun `clear 删掉全部文件并返回数量`() = runTest {
        cache.ensure(byteArrayOf(1))
        cache.ensure(byteArrayOf(2))

        assertEquals(2, cache.clear())
        assertEquals(0, cache.count())
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `sizeBytes 统计所有文件大小`() = runTest {
        cache.ensure(ByteArray(100) { 1 })
        cache.ensure(ByteArray(50) { 2 })

        assertTrue(cache.sizeBytes() >= 150L)
    }

    @Test
    fun `sweep 保留被引用的文件并删掉孤儿与残留临时文件`() = runTest {
        val keep = cache.ensure(byteArrayOf(1))!!
        val orphan = cache.ensure(byteArrayOf(2))!!
        // 写入过程中被杀进程会留下 .tmp，它永远不会被引用，必须一起清掉
        val leftover = File(dir, "half-written.jpg.tmp").apply { writeBytes(byteArrayOf(9)) }

        val removed = cache.sweep(setOf(keep.toCoverUri()))

        assertEquals(2, removed)
        assertTrue(keep.isFile)
        assertTrue(!orphan.exists())
        assertTrue(!leftover.exists())
    }

    @Test
    fun `sweep 遇到悬空引用不会崩溃，孤儿照常清理`() = runTest {
        val cached = cache.ensure(byteArrayOf(3))!!

        val removed = cache.sweep(setOf("file:///not/exists.jpg"))

        assertEquals(1, removed)
        assertTrue(!cached.exists())
    }
}
