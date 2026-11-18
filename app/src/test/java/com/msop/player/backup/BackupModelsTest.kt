package com.msop.player.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 备份文件格式（2026-11-18 多 profile 支持）。纯 JVM 序列化，不碰 Android。 */
class BackupModelsTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun multipleProfilesSurviveRoundTrip() {
        val original = SettingsBackup(
            servers = listOf(
                ServerBackup(
                    id = "p1", name = "家里", baseUrl = "https://a.example", username = "u1",
                    password = "pw1", rootPath = "/music", concurrency = 6, enabled = true,
                ),
                ServerBackup(
                    id = "p2", name = "公司", baseUrl = "https://b.example", username = "u2",
                    password = "pw2", allowPlainHttp = true, enabled = false,
                ),
            ),
            webDavs = listOf(
                WebDavBackup(
                    id = "p1", baseUrl = "https://dav.example", username = "u3",
                    password = "pw3", rootPath = "/dav/music", concurrency = 2,
                ),
            ),
        )

        val text = json.encodeToString(original)
        val decoded = json.decodeFromString<SettingsBackup>(text)

        assertEquals(2, decoded.servers.size)
        assertEquals("p2", decoded.servers[1].id)
        assertEquals("https://b.example", decoded.servers[1].baseUrl)
        assertTrue(decoded.servers[1].allowPlainHttp)
        assertEquals(false, decoded.servers[1].enabled)
        assertEquals("pw3", decoded.webDavs.single().password)
        // 真实导出由 BackupManager 另外填单条字段；这里只验证列表本身可往返。
        assertNull(decoded.server)
    }

    @Test
    fun legacySingleServerBackupStillReads() {
        val old = """
            {"wifiOnlyCache":true,"equalizerEnabled":false,"equalizerLevels":[],
             "server":{"baseUrl":"https://old.example","username":"u","password":"pw"}}
        """.trimIndent()

        val decoded = json.decodeFromString<SettingsBackup>(old)

        assertEquals("", decoded.server?.id)
        assertEquals("https://old.example", decoded.server?.baseUrl)
        assertEquals("pw", decoded.server?.password)
        assertTrue(decoded.servers.isEmpty())
        assertTrue(decoded.webDavs.isEmpty())
    }
}
