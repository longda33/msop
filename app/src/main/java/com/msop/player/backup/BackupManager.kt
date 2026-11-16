package com.msop.player.backup

import android.content.Context
import android.net.Uri
import com.msop.player.core.database.dao.PlaylistDao
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.entity.PlaylistEntity
import com.msop.player.core.datastore.CachePreferences
import com.msop.player.core.datastore.ServerConfig
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.datastore.WebDavConfig
import com.msop.player.core.datastore.WebDavConfigStore
import com.msop.player.feature.player.equalizer.EqualizerController
import com.msop.player.feature.player.equalizer.EqualizerTuningSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份与恢复（P7-04）。
 *
 * 2026-11-15 用户指令：导出**包含所有数据**（含服务器密码），导入后直接可用。
 * 备份文件含明文凭据，需自己保管。
 * - 歌单（名称、创建时间、曲目 id 顺序）
 * - 播放偏好（仅 Wi-Fi 缓存）
 * - 均衡器（开关、预设、10 段曲线）
 * - OpenList 地址 / 用户名 / 密码 / 应用密钥
 * - WebDAV 地址 / 用户名 / 密码 / 根路径（P7-07）
 *
 * 不备份：曲库本身（重扫即可）、封面缓存、播放快照、收藏/最近播放（属于本机行为，
 * 换机后保留反而奇怪）。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val serverConfigStore: ServerConfigStore,
    private val webDavConfigStore: WebDavConfigStore,
    private val cachePreferences: CachePreferences,
    private val equalizer: EqualizerController,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val playlists = playlistDao.allPlaylists().map { entity ->
                PlaylistBackup(
                    name = entity.name,
                    createdAt = entity.createdAt,
                    trackIds = playlistDao.trackIds(entity.id),
                )
            }
            val server = serverConfigStore.load()
            val webDav = webDavConfigStore.load()
            val tuning = equalizer.exportTuning()
            val settings = SettingsBackup(
                wifiOnlyCache = cachePreferences.wifiOnlyCaching,
                equalizerEnabled = tuning.enabled,
                equalizerPreset = tuning.presetName,
                equalizerLevels = tuning.levelsMb,
                server = server?.let {
                    ServerBackup(
                        baseUrl = it.baseUrl,
                        username = it.username,
                        allowPlainHttp = it.allowPlainHttp,
                        appKey = it.appKey,
                        password = it.password,
                    )
                },
                webDav = webDav?.let {
                    WebDavBackup(
                        baseUrl = it.baseUrl,
                        username = it.username,
                        rootPath = it.rootPath,
                        allowPlainHttp = it.allowPlainHttp,
                        password = it.password,
                    )
                },
            )
            val manifest = BackupManifest(
                createdAt = System.currentTimeMillis(),
                playlistCount = playlists.size,
                trackCount = playlists.sumOf { it.trackIds.size },
            )
            writeZip(uri, manifest, playlists, settings)
            BackupResult.Success(
                "已导出 ${playlists.size} 个歌单、${manifest.trackCount} 首曲目；含全部设置与服务器凭据",
            )
        }.getOrElse { BackupResult.Failure("导出失败：${it.readable()}") }
    }

    suspend fun import(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readEntries(uri)
            val manifestRaw = entries[FILE_MANIFEST]
                ?: error("不是 msop 备份：缺少 manifest.json")
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestRaw)
            if (manifest.format != BackupManifest.FORMAT) {
                error("备份格式不匹配：${manifest.format}")
            }
            val playlists = entries[FILE_PLAYLISTS]?.let {
                json.decodeFromString(ListSerializer(PlaylistBackup.serializer()), it)
            } ?: emptyList()
            val settings = entries[FILE_SETTINGS]?.let {
                json.decodeFromString(SettingsBackup.serializer(), it)
            }

            // 先算清楚再写库：曲库里不存在的 id 直接跳过，避免外键失败中途留下半个歌单
            val knownIds = trackDao.allIds().toHashSet()
            var restoredPlaylists = 0
            var usableTracks = 0
            var skippedTracks = 0

            playlists.forEach { backup ->
                val usable = backup.trackIds.filter { it in knownIds }
                skippedTracks += backup.trackIds.size - usable.size
                val existing = playlistDao.findByName(backup.name)
                val playlistId = existing?.id
                    ?: playlistDao.insertPlaylist(
                        PlaylistEntity(name = backup.name, createdAt = backup.createdAt),
                    )
                // addTracks 内部用 IGNORE：同名歌单里已有曲目不会再插一遍
                playlistDao.addTracks(playlistId, usable, System.currentTimeMillis())
                usableTracks += usable.size
                restoredPlaylists++
            }

            settings?.let(::applySettings)

            val text = buildString {
                append("已恢复 $restoredPlaylists 个歌单、$usableTracks 首曲目")
                if (skippedTracks > 0) append("；$skippedTracks 首曲库中不存在，已跳过")
                if (settings?.server != null) append("；OpenList 配置已恢复，可直接使用")
                if (settings?.webDav != null) append("；WebDAV 配置已恢复，可直接使用")
            }
            BackupResult.Success(text)
        }.getOrElse { BackupResult.Failure("导入失败：${it.readable()}") }
    }

    private fun applySettings(settings: SettingsBackup) {
        cachePreferences.setWifiOnlyCaching(settings.wifiOnlyCache)
        equalizer.importTuning(
            EqualizerTuningSnapshot(
                enabled = settings.equalizerEnabled,
                presetName = settings.equalizerPreset,
                levelsMb = settings.equalizerLevels,
            ),
        )
        settings.server?.let { server ->
            // 2026-11-15：密码随备份一起恢复，导入后无需再手动输入。
            // 旧备份没有 password 字段 → 默认 ""，此时保留本机已有的密码更友好。
            val current = serverConfigStore.load()
            serverConfigStore.save(
                ServerConfig(
                    baseUrl = server.baseUrl,
                    username = server.username,
                    password = server.password.ifEmpty { current?.password.orEmpty() },
                    allowPlainHttp = server.allowPlainHttp,
                    appKey = server.appKey,
                ),
            )
        }
        settings.webDav?.let { webDav ->
            val current = webDavConfigStore.load()
            webDavConfigStore.save(
                WebDavConfig(
                    baseUrl = webDav.baseUrl,
                    username = webDav.username,
                    password = webDav.password.ifEmpty { current?.password.orEmpty() },
                    rootPath = webDav.rootPath,
                    allowPlainHttp = webDav.allowPlainHttp,
                ),
            )
        }
    }

    private fun writeZip(
        uri: Uri,
        manifest: BackupManifest,
        playlists: List<PlaylistBackup>,
        settings: SettingsBackup,
    ) {
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
        output.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                fun putJson(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                putJson(FILE_MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest))
                putJson(
                    FILE_PLAYLISTS,
                    json.encodeToString(ListSerializer(PlaylistBackup.serializer()), playlists),
                )
                putJson(FILE_SETTINGS, json.encodeToString(SettingsBackup.serializer(), settings))
            }
        }
    }

    private fun readEntries(uri: Uri): Map<String, String> {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
        val result = mutableMapOf<String, String>()
        input.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name !in ALLOWED_ENTRIES) continue
                    result[name] = readLimited(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }

    /** 防止有人把超大文件改名成备份 zip：单条目最多 4 MB。 */
    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) error("备份条目过大，已中止（上限 ${limit / 1024 / 1024} MB）")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun Throwable.readable(): String = when (this) {
        is java.util.zip.ZipException -> "文件不是有效的 zip 备份"
        else -> message ?: javaClass.simpleName
    }

    private companion object {
        const val FILE_MANIFEST = "manifest.json"
        const val FILE_PLAYLISTS = "playlists.json"
        const val FILE_SETTINGS = "settings.json"
        const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
        val ALLOWED_ENTRIES = setOf(FILE_MANIFEST, FILE_PLAYLISTS, FILE_SETTINGS)
    }
}
