package com.msop.player.feature.local.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.msop.player.core.common.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * 读音频技术参数（P7-06）。
 *
 * 两条路各管一段：
 * - `MediaExtractor` + `MediaFormat`：码率、采样率（所有 API 都有）
 * - `MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE`：位深（API 31+ 才给，
 *   而且 `MediaFormat` 在 compileSdk 37 里**没有** `KEY_BITS_PER_SAMPLE`，别去找）
 *
 * 失败的常见原因是文件损坏或容器不报字段，返回 null / 部分字段为 null，不抛异常。
 * 位深读不到是常态（MP3/AAC 本来就没有），不能因为缺位深就丢掉整条音质。
 */
class AudioQualityReader(private val context: Context) {

    fun read(uri: String): AudioQuality? {
        val fromFormat = readFormat(uri)
        val bitDepth = when {
            fromFormat?.bitDepth != null -> fromFormat.bitDepth
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> readBitDepth(uri)
            else -> null
        }

        val quality = AudioQuality(
            bitrateKbps = fromFormat?.bitrateKbps,
            sampleRateHz = fromFormat?.sampleRateHz,
            bitDepth = bitDepth,
        )
        return quality.takeIf { !it.isEmpty }
    }

    private fun readFormat(uri: String): AudioQuality? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val audioIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(audioIndex)
            val bitrateBps = format.intOrNull(MediaFormat.KEY_BIT_RATE)
            val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)

            AudioQuality(
                // MediaFormat 单位是 bit/s，模型与 UI 用 kbps，四舍五入
                bitrateKbps = bitrateBps?.takeIf { it > 0 }?.let { (it / 1000.0).roundToInt() },
                sampleRateHz = sampleRate?.takeIf { it > 0 },
            ).takeIf { !it.isEmpty }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLog.i("音质读取失败 uri=$uri 原因=${throwable.javaClass.simpleName}")
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** API 31+：retriever 是唯一能稳定报位深的入口。 */
    private fun readBitDepth(uri: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        runCatching { getInteger(key) }.getOrNull()
}
