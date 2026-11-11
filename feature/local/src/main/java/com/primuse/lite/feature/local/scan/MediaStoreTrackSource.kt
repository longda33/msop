package com.primuse.lite.feature.local.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.primuse.lite.core.model.SourceType
import com.primuse.lite.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.TimeUnit

/**
 * 从 MediaStore 读取音频，分页回调。
 *
 * 两个关键设计：
 * 1. **分页用 `QUERY_ARG_LIMIT` / `QUERY_ARG_OFFSET`**（API 26+ 原生支持），
 *    不用 `sortOrder + " LIMIT n OFFSET m"` 那种非官方 hack。
 * 2. **`lastModified` 单位是秒**（MediaStore `DATE_MODIFIED` 的原生单位），
 *    不做毫秒转换，避免增量比较时两边单位不一致。
 */
class MediaStoreTrackSource(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TrackPageSource {

    override suspend fun scan(
        since: Long,
        pageSize: Int,
        onPage: suspend (List<Track>) -> Unit,
    ): Int = withContext(io) {
        val collection = collectionUri()
        val projection = buildProjection()

        val selection = buildString {
            append(MediaStore.Audio.Media.IS_MUSIC).append(" != 0")
            if (since > 0L) append(" AND ").append(MediaStore.Audio.Media.DATE_MODIFIED).append(" > ?")
        }
        val selectionArgs = if (since > 0L) arrayOf(since.toString()) else null

        var offset = 0
        var total = 0

        while (true) {
            val page = queryPage(collection, projection, selection, selectionArgs, offset, pageSize)
            if (page.isEmpty()) break

            total += page.size
            onPage(page)

            // 不足一页说明已到末尾
            if (page.size < pageSize) break
            offset += pageSize
            yield()
        }
        total
    }

    private fun collectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private fun buildProjection(): Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.DATA)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
    }.toTypedArray()

    private fun queryPage(
        collection: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>?,
        offset: Int,
        limit: Int,
    ): List<Track> {
        val queryArgs = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            selectionArgs?.let {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it)
            }
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Audio.Media.DATE_MODIFIED} ASC, ${MediaStore.Audio.Media._ID} ASC",
            )
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val cursor = context.contentResolver.query(collection, projection, queryArgs, null)
            ?: return emptyList()

        return cursor.use {
            buildList(it.count) {
                while (it.moveToNext()) add(readTrack(collection, it))
            }
        }
    }

    private fun readTrack(collection: Uri, cursor: Cursor): Track {
        val mediaId = cursor.longOrZero(MediaStore.Audio.Media._ID)
        val displayName = cursor.stringOrNull(MediaStore.Audio.Media.DISPLAY_NAME).orEmpty()
        val dataPath = cursor.stringOrNull(MediaStore.Audio.Media.DATA)
        val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.stringOrNull(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            null
        }

        val absolutePath = dataPath ?: fallbackPath(relativePath, displayName)

        val albumIdValue = cursor.longOrZero(MediaStore.Audio.Media.ALBUM_ID)

        return Track(
            id = TrackIdFactory.localId(absolutePath),
            source = SourceType.LOCAL,
            uri = ContentUris.withAppendedId(collection, mediaId).toString(),
            title = cursor.stringOrNull(MediaStore.Audio.Media.TITLE).clean()
                ?: displayName.substringBeforeLast('.', displayName),
            artist = cursor.stringOrNull(MediaStore.Audio.Media.ARTIST).clean(),
            album = cursor.stringOrNull(MediaStore.Audio.Media.ALBUM).clean(),
            albumId = if (albumIdValue > 0L) albumIdValue.toString() else null,
            durationMs = cursor.longOrZero(MediaStore.Audio.Media.DURATION),
            trackNo = cursor.intOrNull(MediaStore.Audio.Media.TRACK),
            discNo = null,
            year = cursor.intOrNull(MediaStore.Audio.Media.YEAR),
            coverUri = null,
            sizeBytes = cursor.longOrZero(MediaStore.Audio.Media.SIZE),
            mimeType = cursor.stringOrNull(MediaStore.Audio.Media.MIME_TYPE).clean(),
            localPath = absolutePath,
            // 单位：秒。与 ScanContract 的 since 保持一致。
            lastModified = cursor.longOrZero(MediaStore.Audio.Media.DATE_MODIFIED),
            addedAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
        )
    }

    private fun fallbackPath(relativePath: String?, displayName: String): String =
        TrackIdFactory.normalizePath(
            listOfNotNull("/storage/emulated/0", relativePath, displayName).joinToString("/"),
        )
}

private fun Cursor.columnIndexOrNull(column: String): Int? =
    getColumnIndex(column).takeIf { it >= 0 }

private fun Cursor.stringOrNull(column: String): String? {
    val index = columnIndexOrNull(column) ?: return null
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.longOrZero(column: String): Long {
    val index = columnIndexOrNull(column) ?: return 0L
    return if (isNull(index)) 0L else getLong(index)
}

private fun Cursor.intOrNull(column: String): Int? {
    val index = columnIndexOrNull(column) ?: return null
    return if (isNull(index)) null else getInt(index)
}

/**
 * MediaStore 用字面量 `<unknown>` 表示「标签缺失」，而不是 NULL。
 * 直接入库会让 UI 显示出 "&lt;unknown&gt;" 这种东西，必须在边界处转成 null。
 */
internal fun String?.clean(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
