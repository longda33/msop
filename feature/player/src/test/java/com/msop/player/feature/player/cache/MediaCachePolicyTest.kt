package com.msop.player.feature.player.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

/**
 * P3-04 的两个纯判定。都不碰 Android 类，能在 JVM 上直接跑。
 *
 * 重点防的是两个回归：缓存 key 被换成带 tempauth 的直链（同一首歌永远命不中）、
 * 以及「仅 Wi-Fi 缓存」把蜂窝下的已有缓存也一起禁掉（反而更费流量）。
 */
class MediaCachePolicyTest {

    @Test
    fun `关闭开关时任何网络都写入缓存`() {
        assertTrue(CacheDecision.shouldWriteCache(wifiOnly = false, metered = true))
        assertTrue(CacheDecision.shouldWriteCache(wifiOnly = false, metered = false))
        assertTrue(CacheDecision.shouldWriteCache(wifiOnly = false, metered = null))
    }

    @Test
    fun `开启开关时只有蜂窝不写入，Wi-Fi 与未知网络放行`() {
        assertFalse(CacheDecision.shouldWriteCache(wifiOnly = true, metered = true))
        assertTrue(CacheDecision.shouldWriteCache(wifiOnly = true, metered = false))
        // 拿不到网络能力信息时放行：边播边缓存不会多耗一份流量
        assertTrue(CacheDecision.shouldWriteCache(wifiOnly = true, metered = null))
    }

    @Test
    fun `只有远程内部 URI 被判定为需要缓存`() {
        assertTrue(CacheDecision.isRemote("openlist:/Butter-Fly - 和田光司.flac"))
        assertTrue(CacheDecision.isRemote("webdav:/music/a.flac"))
        assertFalse(CacheDecision.isRemote("content://media/external/audio/media/42"))
        assertFalse(CacheDecision.isRemote("file:///sdcard/Music/a.mp3"))
        assertFalse(CacheDecision.isRemote("https://sharepoint.example/download.aspx?tempauth=v1.eyJ"))
        assertFalse(CacheDecision.isRemote(""))
        assertFalse(CacheDecision.isRemote("OPENLIST:/a.flac"))
    }

    @Test
    fun `缓存 key 只由稳定内部 URI 决定，与一次性签名无关`() {
        val plain = "openlist:/music/Butter-Fly.flac"
        val signedA = "$plain?tempauth=v1.aaa"
        val signedB = "$plain?tempauth=v1.bbb#frag"

        assertEquals(CacheDecision.stableKey(plain), CacheDecision.stableKey(signedA))
        assertEquals(CacheDecision.stableKey(signedA), CacheDecision.stableKey(signedB))
        assertNotEquals(
            CacheDecision.stableKey("openlist:/music/a.flac"),
            CacheDecision.stableKey("openlist:/music/b.flac"),
        )
    }

    @Test
    fun `缓存 key 带命名空间前缀，且拒绝本地 URI`() {
        assertTrue(CacheDecision.stableKey("openlist:/a.flac").startsWith("msop:media:"))
        assertThrows(IllegalArgumentException::class.java) {
            CacheDecision.stableKey("content://media/external/audio/media/42")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CacheDecision.stableKey("https://sharepoint.example/download.aspx?tempauth=x")
        }
    }
}
