package com.msop.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 音质摘要的拼装规则（P7-06）。纯字符串处理，不需要 Android 环境。 */
class TrackQualityTest {

    private fun track(
        bitrateKbps: Int? = null,
        sampleRateHz: Int? = null,
        bitDepth: Int? = null,
    ) = Track(
        id = "local:test",
        source = SourceType.LOCAL,
        uri = "content://media/external/audio/media/1",
        title = "测试",
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        bitDepth = bitDepth,
    )

    @Test
    fun `字段全空时不显示音质`() {
        assertNull(track().audioQualityLabel())
    }

    @Test
    fun `无损三件套按位深采样率码率排列`() {
        val label = track(bitrateKbps = 2_304, sampleRateHz = 96_000, bitDepth = 24)
            .audioQualityLabel()
        assertEquals("24bit / 96kHz / 2304kbps", label)
    }

    @Test
    fun `非整千采样率保留一位小数`() {
        assertEquals("44.1kHz", track(sampleRateHz = 44_100).audioQualityLabel())
        assertEquals("48kHz", track(sampleRateHz = 48_000).audioQualityLabel())
    }

    @Test
    fun `有损格式只有码率`() {
        assertEquals("320kbps", track(bitrateKbps = 320).audioQualityLabel())
    }

    @Test
    fun `零值按缺省处理`() {
        assertNull(track(bitrateKbps = 0, sampleRateHz = 0, bitDepth = 0).audioQualityLabel())
        assertEquals("16bit", track(sampleRateHz = 0, bitDepth = 16).audioQualityLabel())
    }
}
