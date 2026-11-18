package com.msop.player.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 路径切分口径的锁（2026-11-17 深夜用户需求「路径支持填写多条，一行一条」）。
 *
 * 规则与 `WebDavUrl.normalizePath` 同口径但手写了两份（core:datastore 不能依赖 core:network），
 * 所以这里把行为钉成测试：改任何一边，先让这些断言告诉你差在哪。
 */
class RootPathsTest {

    @Test
    fun `空文本回落默认根目录`() {
        assertEquals(listOf("/"), splitRootPaths("", default = "/"))
        assertEquals(listOf("/"), splitRootPaths("   \n \n\t\n", default = "/"))
    }

    @Test
    fun `单条路径补齐前导斜杠并去尾斜杠`() {
        assertEquals(listOf("/music"), splitRootPaths("/music", default = "/"))
        assertEquals(listOf("/music"), splitRootPaths("music", default = "/"))
        assertEquals(listOf("/music"), splitRootPaths("  /music/  ", default = "/"))
        assertEquals(listOf("/music"), splitRootPaths("/music////", default = "/"))
    }

    @Test
    fun `反斜杠当作分隔符`() {
        assertEquals(listOf("/yd/music"), splitRootPaths("\\yd\\music", default = "/"))
    }

    @Test
    fun `多行各算一条且空行被丢掉`() {
        assertEquals(
            listOf("/yd/music", "/yd/musicweb"),
            splitRootPaths("/yd/music\n\n   \n/yd/musicweb\n", default = "/"),
        )
    }

    @Test
    fun `重复目录只留一条`() {
        assertEquals(
            listOf("/yd/music"),
            splitRootPaths("/yd/music\n/yd/music/\nyd//music\n  /yd/music  ", default = "/"),
        )
        // 没有前导斜杠的 `music` 指的是服务器根的 `/music`，不是 `/yd/music`
        assertEquals(
            listOf("/yd/music", "/music"),
            splitRootPaths("/yd/music\nmusic", default = "/"),
        )
    }

    @Test
    fun `单行根斜杠算根目录`() {
        assertEquals(listOf("/"), splitRootPaths("/", default = "/"))
        assertEquals(listOf("/"), splitRootPaths("/\n//", default = "/"))
    }

    @Test
    fun `WebDavConfig 多目录时换算根改成服务器根`() {
        val single = WebDavConfig(baseUrl = "https://h", username = "u", password = "p", rootPath = "/music")
        assertEquals(listOf("/music"), single.rootPaths)
        assertEquals("/music", single.effectiveRootPath)

        val multi = single.copy(rootPath = "/yd/music\n/yd/musicweb")
        assertEquals(listOf("/yd/music", "/yd/musicweb"), multi.rootPaths)
        // 多目录时库里存的是「相对服务器根」的绝对路径，换算根必须是 `/`
        assertEquals("/", multi.effectiveRootPath)
    }

    @Test
    fun `ServerConfig 共用同一套切分`() {
        val config = ServerConfig(
            baseUrl = "https://h",
            username = "u",
            password = "p",
            rootPath = "/yd/music\n/yd/musicweb",
        )
        assertEquals(listOf("/yd/music", "/yd/musicweb"), config.rootPaths)

        // 留空 = 整个网盘根目录
        assertEquals(listOf("/"), config.copy(rootPath = "").rootPaths)
    }
}
