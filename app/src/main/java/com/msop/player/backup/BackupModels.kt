package com.msop.player.backup

import kotlinx.serialization.Serializable

/**
 * 备份文件格式（P7-04）。
 *
 * zip 内固定三个条目：
 * - `manifest.json`：格式标识、版本、导出时间与数量
 * - `playlists.json`：歌单名称、创建时间、曲目 id 顺序
 * - `settings.json`：缓存开关、均衡器曲线、服务器地址**与密码**
 *
 * 2026-11-15 用户指令：导出包含所有数据、导入后直接可用、不用再手动输入密码。
 * password 字段给了默认值 ""，旧备份（不含密码）仍能正常反序列化。
 */
@Serializable
data class BackupManifest(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long = 0L,
    val playlistCount: Int = 0,
    val trackCount: Int = 0,
) {
    companion object {
        const val FORMAT = "msop-backup-v1"
    }
}

@Serializable
data class PlaylistBackup(
    val name: String,
    val createdAt: Long,
    val trackIds: List<String> = emptyList(),
)

@Serializable
data class ServerBackup(
    val baseUrl: String,
    val username: String,
    val allowPlainHttp: Boolean = false,
    val appKey: String = "",
    /** 2026-11-15 起随备份导出；旧备份无此字段，反序列化为 ""。 */
    val password: String = "",
    /** 2026-11-18 多 profile：保留 id 才能让曲目里的 openlist:{id}:... 继续解析。 */
    val id: String = "",
    val name: String = "",
    val rootPath: String = "",
    val concurrency: Int = 0,
    val enabled: Boolean = true,
)

/**
 * WebDAV 第三音源（P7-07）。2026-11-15 起与 [ServerBackup] 一样**含密码**，
 * 导入后直接可用，不用再手动输入。
 */
@Serializable
data class WebDavBackup(
    val baseUrl: String,
    val username: String,
    val rootPath: String = "/",
    val allowPlainHttp: Boolean = false,
    val password: String = "",
    /** 2026-11-18 多 profile，同 [ServerBackup]。 */
    val id: String = "",
    val name: String = "",
    val concurrency: Int = 0,
    val enabled: Boolean = true,
)

@Serializable
data class SettingsBackup(
    val wifiOnlyCache: Boolean = true,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String? = null,
    val equalizerLevels: List<Int> = emptyList(),
    /** 单条形式保留给旧备份 / 旧版本 App 读取；新备份同时写 servers/webDavs。 */
    val server: ServerBackup? = null,
    /** P7-07 新增；旧备份没有这个字段，反序列化为 null。 */
    val webDav: WebDavBackup? = null,
    /** 2026-11-18：多台服务器同时启用，必须整表备份，否则恢复后 id 与曲目脱节。 */
    val servers: List<ServerBackup> = emptyList(),
    val webDavs: List<WebDavBackup> = emptyList(),
)

/** 备份/恢复的可展示结果。 */
sealed interface BackupResult {
    data class Success(val message: String) : BackupResult
    data class Failure(val message: String) : BackupResult
}

fun BackupResult.displayMessage(): String = when (this) {
    is BackupResult.Success -> message
    is BackupResult.Failure -> message
}
