package com.msop.player.feature.local.metadata

/**
 * 从音频文件里读出来的标签。
 *
 * 字段全部可空：标签缺失是常态而不是异常。真正缺得多的只有 SAF 通道——
 * MediaStore 那一路已经由系统解析过一遍，只有「只在 SAF 里出现」的文件需要这里补。
 */
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val durationMs: Long = 0L,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
) {
    /** 一条标签都没读到（文件损坏、格式不支持、或本来就没写标签）。 */
    val isEmpty: Boolean
        get() = title == null && artist == null && album == null &&
            year == null && trackNo == null && discNo == null && durationMs <= 0L &&
            bitrateKbps == null && sampleRateHz == null && bitDepth == null
}

/** 音频技术参数，与标签分开读：老文件可能没标签，但码率/采样率仍然可读。 */
data class AudioQuality(
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
) {
    val isEmpty: Boolean
        get() = bitrateKbps == null && sampleRateHz == null && bitDepth == null
}
