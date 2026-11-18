package com.msop.player.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.msop.player.core.database.MsopDatabase
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
    private val database: MsopDatabase,
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
            // 2026-11-18：多台服务器可以同时启用，必须整表导出。曲目的 id/uri 带
            // profile 前缀，只存第一台的旧备份恢复后会对不上号。
            val serverProfiles = serverConfigStore.profiles()
            val webDavProfiles = webDavConfigStore.profiles()
            val server = serverConfigStore.load()
            val webDav = webDavConfigStore.load()
            val tuning = equalizer.exportTuning()
            val settings = SettingsBackup(
                wifiOnlyCache = cachePreferences.wifiOnlyCaching,
                equalizerEnabled = tuning.enabled,
                equalizerPreset = tuning.presetName,
                equalizerLevels = tuning.levelsMb,
                // 单条字段保留给旧版本 App 读；新版本优先读 servers / webDavs。
                server = server?.toBackup(serverConfigStore.isEnabled(server.id)),
                webDav = webDav?.toBackup(webDavConfigStore.isEnabled(webDav.id)),
                servers = serverProfiles.map { it.toBackup(serverConfigStore.isEnabled(it.id)) },
                webDavs = webDavProfiles.map { it.toBackup(webDavConfigStore.isEnabled(it.id)) },
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

            // 2026-11-17 修复：整批歌单在一个事务里落库。原来逐个 insert，第 30 个歌单
            // 撞上约束/磁盘错误时，前 29 个已经写进去了，用户看到的是「恢复了一半」，
            // 而且重试会把它们再插一遍（同名会被 findByName 认出来，但曲目顺序已乱）。
            database.withTransaction {
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
        restoreServers(settings)
        restoreWebDavs(settings)
    }

    /** 整表恢复 OpenList 配置；旧备份的单条 server 字段也能读。 */
    private fun restoreServers(settings: SettingsBackup) {
        val existing = serverConfigStore.profiles().associateBy { it.id }
        val backups = settings.servers.ifEmpty { listOfNotNull(settings.server) }
        val used = HashSet<String>()
        val configs = backups.mapIndexed { index, backup ->
            val preferred = backup.id.takeIf { it.isNotBlank() && it in existing }
                ?: existing.values.firstOrNull {
                    it.baseUrl == backup.baseUrl && it.username == backup.username
                }?.id
                ?: "p${index + 1}"
            var id = preferred
            var suffix = existing.size + 2
            while (!used.add(id)) id = "p${suffix++}"
            ServerConfig(
                id = id,
                name = backup.name,
                baseUrl = backup.baseUrl,
                username = backup.username,
                // 旧备份 password 缺省为 ""，此时沿用本机同 id 配置里的密码。
                password = backup.password.ifEmpty { existing[preferred]?.password.orEmpty() },
                allowPlainHttp = backup.allowPlainHttp,
                appKey = backup.appKey,
                rootPath = backup.rootPath,
                concurrency = backup.concurrency,
            )
        }
        val enabled = backups.zip(configs).filter { (backup, _) -> backup.enabled }.map { (_, c) -> c.id }
        serverConfigStore.restoreAll(configs, enabled)
    }

    /** 整表恢复 WebDAV 配置；语义同 [restoreServers]。 */
    private fun restoreWebDavs(settings: SettingsBackup) {
        val existing = webDavConfigStore.profiles().associateBy { it.id }
        val backups = settings.webDavs.ifEmpty { listOfNotNull(settings.webDav) }
        val used = HashSet<String>()
        val configs = backups.mapIndexed { index, backup ->
            val preferred = backup.id.takeIf { it.isNotBlank() && it in existing }
                ?: existing.values.firstOrNull {
                    it.baseUrl == backup.baseUrl && it.username == backup.username
                }?.id
                ?: "p${index + 1}"
            var id = preferred
            var suffix = existing.size + 2
            while (!used.add(id)) id = "p${suffix++}"
            WebDavConfig(
                id = id,
                name = backup.name,
                baseUrl = backup.baseUrl,
                username = backup.username,
                password = backup.password.ifEmpty { existing[preferred]?.password.orEmpty() },
                rootPath = backup.rootPath.ifBlank { "/" },
                allowPlainHttp = backup.allowPlainHttp,
                concurrency = backup.concurrency,
            )
        }
        val enabled = backups.zip(configs).filter { (backup, _) -> backup.enabled }.map { (_, c) -> c.id }
        webDavConfigStore.restoreAll(configs, enabled)
    }

    private fun ServerConfig.toBackup(enabled: Boolean) = ServerBackup(
        baseUrl = baseUrl,
        username = username,
        allowPlainHttp = allowPlainHttp,
        appKey = appKey,
        password = password,
        id = id,
        name = name,
        rootPath = rootPath,
        concurrency = concurrency,
        enabled = enabled,
    )

    private fun WebDavConfig.toBackup(enabled: Boolean) = WebDavBackup(
        baseUrl = baseUrl,
        username = username,
        rootPath = rootPath,
        allowPlainHttp = allowPlainHttp,
        password = password,
        id = id,
        name = name,
        concurrency = concurrency,
        enabled = enabled,
    )

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
                var entryCount = 0
                var totalBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    // 2026-11-17 修复：只限制单条目时，一个塞了 10 万个空条目的 zip
                    // 仍能把循环拖到天荒地老（每个条目都要走一次 inflate）。这里同时限条数。
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        error("备份条目过多，已中止（上限 $MAX_ENTRIES 个）")
                    }
                    val name = entry.name.substringAfterLast('/')
                    if (name !in ALLOWED_ENTRIES) continue
                    val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        error("备份解压后过大，已中止（上限 ${MAX_TOTAL_BYTES / 1024 / 1024} MB）")
                    }
                    result[name] = String(bytes, Charsets.UTF_8)
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
        /** 三个条目各自的实际上限都很小，12 MB 总上限只用于挡住畸形压缩包。 */
        const val MAX_TOTAL_BYTES = 12L * 1024 * 1024
        const val MAX_ENTRIES = 32
        val ALLOWED_ENTRIES = setOf(FILE_MANIFEST, FILE_PLAYLISTS, FILE_SETTINGS)
    }
}
