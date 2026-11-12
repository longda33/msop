package com.msop.player.feature.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.common.saf.SafRootProvider
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
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
 * 1. **同名 `.lrc` 旁挂文件**（优先，通常带时间轴）
 * 2. **音频文件内嵌歌词**（MP3 的 ID3 `USLT`、FLAC 的 Vorbis Comment `LYRICS`）
 *
 * 为什么不直接用 `File(path)` 读 `.lrc`：Android 10+ 的分区存储只放行**媒体文件**的裸路径，
 * `.lrc` 不是媒体，按路径读会 EACCES。而扫描时 SAF 目录是有持久授权的，所以走 SAF：
 * 把 `xxx.mp3` 的 documentId 换成 `xxx.lrc`，用同一棵树的授权打开。
 *
 * 旁挂文件的查找顺序：
 * 1. 曲目本身是 SAF 文档 URI → 直接换扩展名找兄弟文档
 * 2. 曲目来自 MediaStore（只有绝对路径）→ 在已授权的目录树里找它属于哪棵树，再换扩展名
 * 3. 裸路径兜底（只在没有分区存储限制的场景有效）
 *
 * 内嵌歌词没有时间标签时退化为静态文本，见 [Lyrics.synced]。
 * 远程曲目的内嵌歌词要先把文件拉到本地，等 P3 远程音源落地后再接。
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safRoots: SafRootProvider,
) {

    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val sidecar = readDirectDocumentSibling(track.uri)
            ?: readTreeSibling(track.localPath)
            ?: readLocalSibling(track.localPath)

        if (sidecar != null) {
            val parsed = LrcParser.parse(LrcParser.decode(sidecar))
            if (!parsed.isEmpty) return@withContext parsed
        }

        val embedded = readEmbedded(track)
        if (embedded.isNullOrBlank()) {
            AppLog.i("未找到歌词 track=${track.title}")
            return@withContext null
        }

        // USLT 里也常见带时间轴的 LRC 文本，能解析出时间就用时间轴，否则静态展示
        LrcParser.parse(embedded).takeIf { !it.isEmpty }
            ?: Lyrics.plain(embedded).takeIf { !it.isEmpty }
    }

    /** 直接从音频文件里读内嵌歌词；远程曲目不在这一步处理。 */
    private fun readEmbedded(track: Track): String? {
        if (track.source != SourceType.LOCAL) return null

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

    /** `content://authority/tree/<treeId>/document/<docId>` → 同目录的 `<name>.lrc`。 */
    private fun readDirectDocumentSibling(uri: String): ByteArray? {
        val documentUri = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (documentUri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val segments = documentUri.pathSegments
        if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return null

        val lrcDocumentId = withLrcExtension(segments[3]) ?: return null
        return runCatching {
            val treeUri = DocumentsContract.buildTreeDocumentUri(documentUri.authority, segments[1])
            val lrcUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, lrcDocumentId)
            context.contentResolver.openInputStream(lrcUri)?.use { it.readBytes() }
        }.onFailure {
            AppLog.i("SAF 歌词读取失败 uri=$uri 原因=${it.javaClass.simpleName}")
        }.getOrNull()
    }

    /**
     * MediaStore 来的曲目只有绝对路径，没有文档 URI。把它对齐到某棵已授权的树：
     * `/storage/emulated/0/Music/MsopTest/a.mp3` + 树根 `primary:Music`
     * → documentId `primary:Music:MsopTest/a.lrc`。
     */
    private fun readTreeSibling(localPath: String?): ByteArray? {
        if (localPath.isNullOrBlank()) return null

        for (treeUriString in safRoots.treeUris()) {
            val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: continue
            val treeDocumentId = runCatching {
                DocumentsContract.getTreeDocumentId(treeUri)
            }.getOrNull() ?: continue

            val rootPath = absolutePathOf(treeDocumentId) ?: continue
            if (!localPath.startsWith("$rootPath/")) continue

            val relative = localPath.removePrefix("$rootPath/")
            val sibling = withLrcExtension(relative) ?: continue
            val lrcUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                "$treeDocumentId:$sibling",
            )

            val bytes = runCatching {
                context.contentResolver.openInputStream(lrcUri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) return bytes
        }
        return null
    }

    private fun readLocalSibling(localPath: String?): ByteArray? {
        if (localPath.isNullOrBlank()) return null
        return runCatching {
            val audio = File(localPath)
            File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
                .takeIf { it.isFile }
                ?.readBytes()
        }.getOrNull()
    }

    /** `a/b/song.mp3` → `a/b/song.lrc`；没有扩展名时返回 null（不猜）。 */
    private fun withLrcExtension(path: String): String? {
        val dot = path.lastIndexOf('.')
        if (dot <= path.lastIndexOf('/')) return null
        return path.substring(0, dot) + ".lrc"
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
}
