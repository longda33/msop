package com.msop.player.core.network

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.datastore.DownloadConfigStore
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 云端曲目下载（2026-11-16 晚用户需求「云端歌曲三点菜单增加 下载歌曲」）。
 *
 * 走和播放同一条直链解析链 [StreamUrlResolver]：OpenList 拿到带 tempauth 的 raw_url，
 * WebDAV 拿到文件 URL + Basic 头。所以「能播的歌一定能下」，不会出现
 * 「播放正常但下载 401」的两套凭据。
 *
 * 落地位置看 [DownloadConfigStore]：
 * - 用户选过 SAF 目录 → 用 `DocumentsContract.createDocument` 写进去（重名自动加序号）；
 * - 没选 → 应用外部私有目录 `Android/data/com.msop.player/files/Music`，不需要权限。
 *
 * 失败只抛 [DownloadException]，调用方弹 toast；不做断点续传（自用场景，一首歌几十 MB）。
 */
@Singleton
class RemoteFileDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: StreamUrlResolver,
    private val config: DownloadConfigStore,
) {
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 下载一首云端曲目，返回落盘后的可读路径（给 toast 用）。 */
    suspend fun download(
        track: Track,
        onProgress: (written: Long, total: Long?) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        if (track.source == SourceType.LOCAL) throw DownloadException("本机曲目不需要下载")
        val url = resolver.resolve(track.uri)
        val headers = resolver.headers(track.uri)
        val fileName = fileNameOf(track)

        val request = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> request.header(k, v) }
        http.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("下载失败：服务器返回 HTTP ${response.code}")
            }
            val body = response.body
            // 2026-11-16 晚用户报「点击下载 反馈没有 进度也没有」：原来只有一句结束后的
            // toast，服务器慢的时候几十秒毫无动静。现在把 Content-Length 和已写字节回传，
            // 由 DownloadCenter 驱动进度条。服务器不给长度时 total = null（只显示已下载量）。
            val total = body.contentLength().takeIf { it > 0L }
            val written = writeStream(fileName, track.mimeType, body.byteStream(), total, onProgress)
            AppLog.i("下载完成 ${track.title} -> $written")
            written
        }
    }

    private fun writeStream(
        fileName: String,
        mimeType: String?,
        input: java.io.InputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): String {
        val tree = config.treeUri()
        return if (tree != null) {
            writeToTree(tree, fileName, mimeType, input, total, onProgress)
        } else {
            writeToAppDir(fileName, input, total, onProgress)
        }
    }

    private fun writeToAppDir(
        fileName: String,
        input: java.io.InputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueFile(dir, fileName)
        target.outputStream().use { out -> copyWithProgress(input, out, total, onProgress) }
        return target.absolutePath
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        var lastNotified = -1L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            written += read
            // 每 256KB 报一次，避免几十 MB 的文件把主线程刷屏
            if (written - lastNotified >= PROGRESS_STEP_BYTES) {
                lastNotified = written
                onProgress(written, total)
            }
        }
        out.flush()
        onProgress(written, total)
        return written
    }

    private fun writeToTree(
        treeUri: String,
        fileName: String,
        mimeType: String?,
        input: java.io.InputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ): String {
        val tree = Uri.parse(treeUri)
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val mime = mimeType ?: "application/octet-stream"

        // 同名文件已存在时换个名字，不覆盖用户已有的下载
        var name = fileName
        var created: Uri? = null
        var attempt = 1
        while (created == null) {
            val existing = findExisting(tree, docId, name)
            if (existing != null) {
                attempt++
                name = suffixName(fileName, attempt)
                continue
            }
            created = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                mime,
                name,
            )
            if (created == null && attempt > 3) throw DownloadException("无法在所选目录创建文件")
            if (created == null) {
                attempt++
                name = suffixName(fileName, attempt)
            }
        }
        context.contentResolver.openOutputStream(created)?.use { out ->
            copyWithProgress(input, out, total, onProgress)
        } ?: throw DownloadException("无法写入所选目录")
        return "$treeUri/$name"
    }

    private fun findExisting(tree: Uri, docId: String, name: String): String? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == name) return@use name
            }
            null
        }
    }.getOrNull()

    /** 「歌名 - 歌手.flac」，去掉文件系统不接受的字符。 */
    private fun fileNameOf(track: Track): String {
        val base = buildString {
            append(track.title.ifBlank { "未命名" })
            track.artist?.takeIf { it.isNotBlank() }?.let { append(" - ").append(it) }
        }
        val ext = track.mimeType?.let { mimeToExt(it) }
            ?: track.uri.substringAfterLast('.', "").takeIf { it.length in 2..5 }
            ?: "mp3"
        return sanitize(base).take(120) + "." + ext
    }

    private fun suffixName(fileName: String, attempt: Int): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) "${fileName}_$attempt"
        else fileName.substring(0, dot) + "_" + attempt + fileName.substring(dot)
    }

    private fun sanitize(name: String): String =
        name.map { ch ->
            if (ch in "\\/:*?\"<>|" || ch.code < 32) '_' else ch
        }.joinToString("").trim()

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var attempt = 1
        while (candidate.exists()) {
            attempt++
            candidate = File(dir, suffixName(name, attempt))
        }
        return candidate
    }

    private fun mimeToExt(mime: String): String = when (mime.lowercase(Locale.ROOT)) {
        "audio/mpeg" -> "mp3"
        "audio/flac" -> "flac"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/wav" -> "wav"
        "audio/ogg" -> "ogg"
        else -> "mp3"
    }
}

/** 下载失败的用户可读原因。 */
class DownloadException(message: String) : Exception(message)

/** 进度回调的最小间隔：256KB 报一次，够 10 帧/秒的观感，也不会把主线程刷爆。 */
private const val PROGRESS_STEP_BYTES = 256L * 1024L
