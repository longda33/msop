package com.primuse.lite.feature.local.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import com.primuse.lite.feature.local.scan.TrackIdFactory
import com.primuse.lite.feature.local.scan.TrackPageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.TimeUnit

/**
 * 遍历用户通过 SAF 添加的目录。
 *
 * 性能要点：**不用 `DocumentFile.listFiles()`**。那是逐层 IPC，几万文件要几分钟。
 * 这里直接对 children URI 做 `ContentResolver.query`，一次拿一层全部子项，快一个数量级。
 *
 * 遍历方式用广度优先 + 显式队列，避免深目录递归爆栈。
 */
class SafTrackSource(
    private val context: Context,
    private val directories: SafDirectoryStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TrackPageSource {

    override suspend fun scan(
        since: Long,
        pageSize: Int,
        onPage: suspend (List<Track>) -> Unit,
    ): Int = withContext(io) {
        var total = 0
        for (uriString in directories.treeUris()) {
            val treeUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: continue
            total += scanTree(treeUri, since, pageSize, onPage)
        }
        total
    }

    private suspend fun scanTree(
        treeUri: Uri,
        since: Long,
        pageSize: Int,
        onPage: suspend (List<Track>) -> Unit,
    ): Int {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return 0

        val pendingDirectories = ArrayDeque<String>().apply { add(rootId) }
        val buffer = ArrayList<Track>(pageSize)
        var count = 0

        while (pendingDirectories.isNotEmpty()) {
            val directoryId = pendingDirectories.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)

            val cursor = runCatching {
                context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
            }.getOrNull() ?: continue

            cursor.use { c ->
                while (c.moveToNext()) {
                    val documentId = c.getString(0) ?: continue
                    val displayName = c.getString(1).orEmpty()
                    val mimeType = c.getString(2).orEmpty()

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories.add(documentId)
                        continue
                    }
                    if (!isAudio(mimeType, displayName)) continue

                    val modifiedSeconds = TimeUnit.MILLISECONDS.toSeconds(c.getLong(3))
                    if (since > 0L && modifiedSeconds <= since) continue

                    val absolutePath = documentIdToAbsolutePath(documentId) ?: continue

                    buffer += Track(
                        id = TrackIdFactory.localId(absolutePath),
                        source = SourceType.LOCAL,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            .toString(),
                        title = displayName.substringBeforeLast('.', displayName),
                        // SAF 拿不到标签，元数据留待 P1-07 用 MediaMetadataRetriever 补齐
                        artist = null,
                        album = null,
                        albumId = null,
                        durationMs = 0L,
                        trackNo = null,
                        discNo = null,
                        year = null,
                        coverUri = null,
                        sizeBytes = c.getLong(4),
                        mimeType = mimeType,
                        localPath = absolutePath,
                        lastModified = modifiedSeconds,
                        addedAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    )
                    count++

                    if (buffer.size >= pageSize) {
                        onPage(buffer.toList())
                        buffer.clear()
                        yield()
                    }
                }
            }
        }

        if (buffer.isNotEmpty()) onPage(buffer.toList())
        return count
    }

    private fun isAudio(mimeType: String, displayName: String): Boolean {
        if (mimeType.startsWith("audio/")) return true
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus")
    }
}
