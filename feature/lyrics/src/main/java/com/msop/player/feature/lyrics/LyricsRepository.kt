package com.msop.player.feature.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.common.saf.SafRootProvider
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.network.RemoteLyricsProbe
import com.msop.player.core.network.StreamUrlResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌词加载。两种来源，按顺序尝试：
 * 1. **同名旁挂文件**：`.lrc` → `.ttml` → `.xml`（`.xml` 只有内容像 TTML 才认）
 * 2. **音频文件内嵌歌词**（MP3 的 ID3 `USLT`、FLAC 的 Vorbis Comment `LYRICS`）
 *
 * 旁挂文件优先，因为用户手动放的歌词通常最准。扩展名按上面的顺序找，找到就停：
 * 有 `.lrc` 时不会再去读 `.ttml`。
 *
 * 为什么不直接用 `File(path)` 读旁挂歌词：Android 10+ 的分区存储只放行**媒体文件**的裸路径，
 * `.lrc`/`.ttml` 不是媒体，按路径读会 EACCES。而扫描时 SAF 目录是有持久授权的，所以走 SAF：
 * 把 `xxx.mp3` 的 documentId 换成 `xxx.lrc` / `xxx.ttml`，用同一棵树的授权打开。
 *
 * 旁挂文件的查找顺序：
 * 1. 曲目本身是 SAF 文档 URI → 直接换扩展名找兄弟文档
 * 2. 曲目来自 MediaStore（只有绝对路径）→ 在已授权的目录树里找它属于哪棵树，再换扩展名
 * 3. 裸路径兜底（只在没有分区存储限制的场景有效）
 *
 * 内嵌歌词没有时间标签时退化为静态文本，见 [Lyrics.synced]。
 * 2026-11-17：远程曲目的内嵌歌词已接通——用 [StreamUrlResolver] 拿直链，
 * [RemoteLyricsProbe] 通过 Range 读 ID3 USLT / FLAC Vorbis LYRICS / MP4 ©lyr，
 * 不再下载整首歌。此前 `readEmbedded()` 对非 LOCAL 直接 return null，等于零实现。
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safRoots: SafRootProvider,
    private val resolver: StreamUrlResolver,
) {

    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val sidecar = findSidecar(track)
        if (sidecar != null) {
            val parsed = parseSidecar(sidecar)
            if (parsed != null && !parsed.isEmpty) return@withContext parsed
        }

        val embedded = readEmbedded(track)
        if (embedded.isNullOrBlank()) {
            AppLog.i("未找到歌词 track=${track.title}")
            return@withContext null
        }

        // USLT / LYRICS 里也常见整段 TTML 或带时间轴的 LRC；都解析不出来才按纯文本展示。
        parseText(embedded)
            ?: Lyrics.plain(embedded).takeIf { !TtmlParser.looksLikeTtml(embedded) && !it.isEmpty }
    }

    /** 直接从音频文件里读内嵌歌词；本地读文件，云端走 Range 探测。 */
    private suspend fun readEmbedded(track: Track): String? {
        if (track.source != SourceType.LOCAL) {
            return runCatching {
                val url = resolver.resolve(track.uri)
                RemoteLyricsProbe.lyrics(url, resolver.headers(track.uri))
            }.onFailure {
                AppLog.i("云端内嵌歌词读取失败 track=${track.title} 原因=${it.javaClass.simpleName}")
            }.getOrNull()
        }

        return runCatching {
            openAudio(track)?.use { EmbeddedLyrics.extract(it) }
        }.onFailure {
            AppLog.i("内嵌歌词读取失败 track=${track.title} 原因=${it.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun openAudio(track: Track): InputStream? {
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull()
        if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return context.contentResolver.openInputStream(uri)
        }

        val path = track.localPath ?: return null
        val file = File(path)
        return if (file.isFile) FileInputStream(file) else null
    }

    private data class Sidecar(val extension: String, val text: String)

    /**
     * 按 `.lrc` → `.ttml` → `.xml` 查找旁挂文件。
     *
     * 每个扩展名都依次试「文档兄弟 → 授权树 → 裸路径」，避免 MediaStore 曲目因为
     * 某个目录不在树里就整个放弃。
     */
    private fun findSidecar(track: Track): Sidecar? {
        for (extension in SIDECAR_EXTENSIONS) {
            val bytes = readDirectDocumentSibling(track.uri, extension)
                ?: readTreeSibling(track.localPath, extension)
                ?: readLocalSibling(track.localPath, extension)
                ?: continue

            val text = LrcParser.decode(bytes)
            // `.xml` 可能是别的元数据；内容不像 TTML 就当它不存在。
            if (extension != "lrc" && !TtmlParser.looksLikeTtml(text)) continue
            return Sidecar(extension, text)
        }
        return null
    }

    private fun parseSidecar(sidecar: Sidecar): Lyrics? = when (sidecar.extension) {
        "lrc" -> parseText(sidecar.text)
        else -> TtmlParser.parse(sidecar.text).takeIf { !it.isEmpty }
            ?: Lyrics.plain(sidecar.text)
                .takeIf { !TtmlParser.looksLikeTtml(sidecar.text) && !it.isEmpty }
    }

    /**
     * 读旁挂文件，超过 [MAX_SIDECAR_BYTES] 当作不存在。
     *
     * 2026-11-17 新增：原来一律 `readBytes()`。旁挂文件来自用户目录/云端目录，
     * 一个被误命名成 `.lrc` 的百兆文件（或恶意构造的压缩炸弹式文本）会把
     * 歌词线程的堆直接撑爆；正常歌词几百 KB 顶天，8 MB 足够宽容。
     */
    private fun readLimited(input: InputStream?): ByteArray? {
        if (input == null) return null
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream(16 * 1024)
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_SIDECAR_BYTES) return null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /**
     * 先按 TTML 解析，再按 LRC 解析。
     *
     * `.lrc` 文件里塞 TTML 的情况真实存在（导出工具把扩展名写错），所以不只看扩展名。
     */
    private fun parseText(raw: String): Lyrics? {
        if (TtmlParser.looksLikeTtml(raw)) {
            TtmlParser.parse(raw).takeIf { !it.isEmpty }?.let { return it }
        }
        return LrcParser.parse(raw).takeIf { !it.isEmpty }
    }

    /** `content://authority/tree/<treeId>/document/<docId>` → 同目录的 `<name>.<extension>`。 */
    private fun readDirectDocumentSibling(uri: String, extension: String): ByteArray? {
        val documentUri = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (documentUri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val segments = documentUri.pathSegments
        if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return null

        val siblingDocumentId = withExtension(segments[3], extension) ?: return null
        return runCatching {
            val treeUri = DocumentsContract.buildTreeDocumentUri(documentUri.authority, segments[1])
            val siblingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, siblingDocumentId)
            context.contentResolver.openInputStream(siblingUri)?.let(::readLimited)
        }.onFailure {
            AppLog.i("SAF 歌词读取失败 uri=$uri 原因=${it.javaClass.simpleName}")
        }.getOrNull()
    }

    /**
     * MediaStore 来的曲目只有绝对路径，没有文档 URI。把它对齐到某棵已授权的树：
     * `/storage/emulated/0/Music/MsopTest/a.mp3` + 树根 `primary:Music`
     * → documentId `primary:Music:MsopTest/a.lrc`。
     */
    private fun readTreeSibling(localPath: String?, extension: String): ByteArray? {
        if (localPath.isNullOrBlank()) return null

        for (treeUriString in safRoots.treeUris()) {
            val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: continue
            val treeDocumentId = runCatching {
                DocumentsContract.getTreeDocumentId(treeUri)
            }.getOrNull() ?: continue

            val rootPath = absolutePathOf(treeDocumentId) ?: continue
            if (!localPath.startsWith("$rootPath/")) continue

            val relative = localPath.removePrefix("$rootPath/")
            val sibling = withExtension(relative, extension) ?: continue
            // 2026-11-17 修复：子文档 id 的格式是「树根 id + '/' + 相对路径」。
            // 原来写成 "$treeDocumentId:$sibling"，构造出 `primary:Music:MsopTest/a.lrc`
            // ——多冒号在 DocumentsProvider 里解析不到，openInputStream 必然抛
            // IllegalArgumentException，被 runCatching 吞掉后表现为「明明有 .lrc 却读不到」。
            val siblingUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                "$treeDocumentId/$sibling",
            )

            val bytes = runCatching {
                context.contentResolver.openInputStream(siblingUri)?.let(::readLimited)
            }.getOrNull()
            if (bytes != null) return bytes
        }
        return null
    }

    private fun readLocalSibling(localPath: String?, extension: String): ByteArray? {
        if (localPath.isNullOrBlank()) return null
        return runCatching {
            val audio = File(localPath)
            File(audio.parentFile, audio.nameWithoutExtension + "." + extension)
                .takeIf { it.isFile }
                ?.let { file -> file.inputStream().let(::readLimited) }
        }.getOrNull()
    }

    /** `a/b/song.mp3` → `a/b/song.<extension>`；没有扩展名时返回 null（不猜）。 */
    private fun withExtension(path: String, extension: String): String? {
        val dot = path.lastIndexOf('.')
        if (dot <= path.lastIndexOf('/')) return null
        return path.substring(0, dot) + "." + extension
    }

    /** `primary:Music` → `/storage/emulated/0/Music`；与 feature:local 的 SafPaths 同一套规则。 */
    private fun absolutePathOf(documentId: String): String? {
        val separator = documentId.indexOf(':')
        if (separator <= 0 || separator == documentId.lastIndex) return null

        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1).trim('/')
        if (relative.isEmpty()) return null

        val root = if (volume.equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    private companion object {
        val SIDECAR_EXTENSIONS = listOf("lrc", "ttml", "xml")
        /** 旁挂歌词的读取上限：正常歌词几百 KB，8 MB 只是拦住畸形/误命名的超大文件。 */
        const val MAX_SIDECAR_BYTES = 8 * 1024 * 1024
    }
}
