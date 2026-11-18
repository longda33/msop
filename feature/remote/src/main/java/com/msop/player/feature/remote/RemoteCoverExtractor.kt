package com.msop.player.feature.remote

import android.content.Context
import android.content.SharedPreferences
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.dao.clearCoverChunked
import com.msop.player.core.database.entity.TrackEntity
import com.msop.player.core.model.SourceType
import com.msop.player.core.network.RemoteCoverProbe
import com.msop.player.core.network.StreamUrlResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云端曲目内嵌封面提取（2026-11-17）。
 *
 * OpenList / WebDAV 不返回音频标签，所以 73 首 OpenList + 182 首 WebDAV 在列表里
 * 全是占位图。这里对每首没有 coverUri 的云端曲发小范围 Range 请求，解析
 * ID3 APIC / FLAC PICTURE / MP4 covr，落盘到与本机封面**同一个** `files/covers`
 * 目录（SHA-1 内容命名，天然去重），再把 `file://` 写回 tracks.coverUri。
 *
 * **请求约束**（用户明确要求「限制并发请求」）：
 * - 并发硬上限 [MAX_CONCURRENCY] = 4，用 Semaphore 控制，不把服务器打爆；
 * - 单次最多 [DEFAULT_LIMIT] = 300 首，剩余的下次启动继续；
 * - 探测失败的曲目记 24 小时跳过，不让没有内嵌封面的歌每次启动都重试。
 *
 * 播放链路、缓存 key、数据库 id 一律不动；失败只留占位图，不影响播放。
 */
@Singleton
class RemoteCoverExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackDao,
    private val resolver: StreamUrlResolver,
) {
    /**
     * 2026-11-18：四个调用点（曲库首屏、OpenList/WebDAV 添加服务器、清缓存）可能
     * 同时触发 extract。Semaphore 只限制单次内部并发，不能阻止两轮任务互相踩
     * 同一个临时文件与失败跳过表，这里用实例级锁把整轮串起来。
     */
    private val extractMutex = Mutex()

    data class Stats(
        val candidates: Int = 0,
        val covers: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val elapsedMs: Long = 0L,
    )

    suspend fun extract(limit: Int = DEFAULT_LIMIT): Stats = withContext(Dispatchers.IO) {
        extractMutex.withLock {
            val started = System.currentTimeMillis()
            val now = started
            val prefs = probePrefs()
            // 先修历史数据：之前按 ID3 picture type 取字节、且不管是不是图片就把文件写成 .jpg，
            // 部分曲目 Coil 解码失败露出白底。这里按魔数校验并改扩展名/清空重抓。
            repairExistingCovers(prefs)
            val candidates = (dao.bySource(SourceType.OPENLIST) + dao.bySource(SourceType.WEBDAV))
                .asSequence()
                .filter { it.coverUri == null }
                .filterNot { skippedRecently(prefs, it.id, now) }
                .sortedByDescending { it.addedAt }
                .take(limit)
                .toList()
            if (candidates.isEmpty()) return@withLock Stats(elapsedMs = now - started)

            val gate = Semaphore(MAX_CONCURRENCY)
            val updated = ArrayList<TrackEntity>(candidates.size)
            val failures = ArrayList<String>()
            val lock = Any()

            coroutineScope {
                candidates.map { entity ->
                    async {
                        gate.withPermit {
                            val result = runCatching { probeAndSave(entity) }
                            val uri = result.getOrNull()
                            synchronized(lock) {
                                if (uri != null) updated += entity.copy(coverUri = uri)
                                else failures += entity.id
                            }
                        }
                    }
                }.awaitAll()
            }

            if (updated.isNotEmpty()) dao.upsertAll(updated)
            if (failures.isNotEmpty()) {
                prefs.edit().apply {
                    failures.forEach { putLong(KEY_PREFIX + it, now + SKIP_TTL_MS) }
                    apply()
                }
            }
            val stats = Stats(
                candidates = candidates.size,
                covers = updated.size,
                failed = failures.size,
                elapsedMs = System.currentTimeMillis() - started,
            )
            AppLog.i(
                "云端封面提取 候选=${stats.candidates} 写入=${stats.covers} " +
                    "无封面=${stats.failed} 耗时=${stats.elapsedMs}ms 并发上限=$MAX_CONCURRENCY",
            )
            stats
        }
    }

    /** 失败记录过期后清掉，避免 prefs 无限增长。 */
    fun clearProbeHistory() {
        probePrefs().edit().clear().apply()
    }

    /**
     * 修复已写盘的云端封面：魔数不合法（Coil 解码失败 → 白块）就清空 coverUri 重抓；
     * 扩展名与真实格式不符（PNG 存成 .jpg）就改名并回写 URI。
     */
    private suspend fun repairExistingCovers(prefs: SharedPreferences) {
        val remote = dao.bySource(SourceType.OPENLIST) + dao.bySource(SourceType.WEBDAV)
        val invalid = ArrayList<String>()
        val renamed = ArrayList<TrackEntity>()
        remote.forEach { entity ->
            val uri = entity.coverUri ?: return@forEach
            if (!uri.startsWith(FILE_SCHEME)) return@forEach // Subsonic 的 http 封面不归这里管
            val file = File(uri.removePrefix(FILE_SCHEME))
            if (!file.isFile) {
                invalid += entity.id
                return@forEach
            }
            val header = ByteArray(12)
            val read = file.inputStream().use { it.read(header) }
            val ext = RemoteCoverProbe.imageExtension(header.copyOfRange(0, maxOf(read, 0)))
            if (ext == null) {
                invalid += entity.id
                return@forEach
            }
            val expected = File(file.parentFile, "${file.nameWithoutExtension}.$ext")
            if (expected.absolutePath != file.absolutePath) {
                // 2026-11-18（用户报「添加服务器后本机封面也不显示」）：
                // 封面按内容哈希落盘，本机与云端曲目**共用同一个文件**。这里原来是
                // `renameTo`——把 .jpg 改成 .png 的同时，所有指向旧文件的本机行全部失效，
                // 列表立刻退回占位图。改成「按正确扩展名复制一份」，旧文件留给其它引用
                // （不再被引用的那份由全量扫描后的 sweep 清掉）。
                val copied = runCatching {
                    if (!expected.isFile) file.copyTo(expected, overwrite = false)
                    expected.isFile
                }.getOrDefault(false)
                if (copied) {
                    renamed += entity.copy(coverUri = FILE_SCHEME + expected.absolutePath)
                } else {
                    invalid += entity.id
                }
            }
        }
        if (invalid.isNotEmpty()) {
            dao.clearCoverChunked(invalid)
            prefs.edit().apply {
                invalid.forEach { remove(KEY_PREFIX + it) }
                apply()
            }
            AppLog.i("云端封面自愈 清空无效引用=${invalid.size}")
        }
        if (renamed.isNotEmpty()) {
            dao.upsertAll(renamed)
            AppLog.i("云端封面自愈 扩展名修正=${renamed.size}")
        }
    }

    private fun probeAndSave(entity: TrackEntity): String? {
        val url = resolver.resolve(entity.uri)
        val headers = resolver.headers(entity.uri)
        val bytes = RemoteCoverProbe.picture(url, headers) ?: return null
        // 按真实图片格式命名；PNG 不能再写成 .jpg，否则部分设备/解码器会露白底
        val ext = RemoteCoverProbe.imageExtension(bytes) ?: return null
        val file = File(File(context.filesDir, COVER_DIR), "${sha1(bytes)}.$ext")
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            // 2026-11-18：同一个 sha1 可能被多首歌并发探测到，固定名字的 .tmp
            // 会被两路同时写/重命名。用系统生成的唯一临时名，且 rename 失败时
            // 只要目标文件已经是同样内容的图片就接受，绝不先删已发布文件。
            val temp = File.createTempFile("${file.nameWithoutExtension}-", ".tmp", file.parentFile)
            temp.writeBytes(bytes)
            val published = temp.renameTo(file) ||
                (file.isFile && file.length() == bytes.size.toLong())
            if (!published) {
                temp.delete()
                return null
            }
            temp.delete()
        }
        return FILE_SCHEME + file.absolutePath
    }

    private fun probePrefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun skippedRecently(prefs: SharedPreferences, id: String, now: Long): Boolean {
        val until = prefs.getLong(KEY_PREFIX + id, 0L)
        return until > now
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private companion object {
        const val DEFAULT_LIMIT = 300
        const val MAX_CONCURRENCY = 4
        const val COVER_DIR = "covers"
        const val FILE_SCHEME = "file://"
        const val PREFS_NAME = "msop_remote_cover"
        const val KEY_PREFIX = "fail_"
        const val SKIP_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
