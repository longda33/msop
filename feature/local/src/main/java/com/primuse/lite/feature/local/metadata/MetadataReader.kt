package com.primuse.lite.feature.local.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.primuse.lite.core.common.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读取音频文件的标签与内嵌封面。
 *
 * 拆成两个方法而不是一次返回全部，是为了让调用方只为需要的东西付 IO：
 * 补标签时不必把封面字节读进内存，预热封面时也不必解析一堆用不到的标签。
 */
interface MetadataReader {

    /** 读取标签。文件损坏/格式不支持/URI 无权访问时返回 null，不抛异常。 */
    suspend fun readTags(uri: String): TrackMetadata?

    /** 读取内嵌封面原始字节（未解码）。没有封面返回 null。 */
    suspend fun readCover(uri: String): ByteArray?
}

/**
 * [MetadataReader] 的 Android 实现。
 *
 * 每次都新建 `MediaMetadataRetriever`：复用实例虽然省掉一次 native 初始化，
 * 但 `setDataSource` 失败后实例状态不可靠，且并发读要加锁。这里读一个文件才几十毫秒，
 * 不值得我们为省这点开销引入状态机。
 */
class AndroidMetadataReader(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MetadataReader {

    override suspend fun readTags(uri: String): TrackMetadata? = withContext(io) {
        withRetriever(uri) { retriever ->
            TrackMetadata(
                title = MetadataValues.clean(retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE)),
                artist = MetadataValues.clean(retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)),
                album = MetadataValues.clean(retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)),
                year = MetadataValues.parseYear(retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR)),
                trackNo = MetadataValues.parseNumber(
                    retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                ),
                discNo = MetadataValues.parseNumber(
                    retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                ),
                durationMs = MetadataValues.parseDuration(
                    retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                ),
            )
        }
    }

    override suspend fun readCover(uri: String): ByteArray? = withContext(io) {
        withRetriever(uri) { retriever ->
            val picture = runCatching { retriever.embeddedPicture }.getOrNull()
            picture?.takeIf { it.isNotEmpty() }
        }
    }

    private inline fun <T> withRetriever(uri: String, block: (MediaMetadataRetriever) -> T): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            block(retriever)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // 单个坏文件不能让整批补齐中断：跳过它，下一次扫描还会再试
            AppLog.w("读取标签失败 uri=$uri 原因=${throwable.javaClass.simpleName}: ${throwable.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String? =
        runCatching { extractMetadata(key) }.getOrNull()
}
