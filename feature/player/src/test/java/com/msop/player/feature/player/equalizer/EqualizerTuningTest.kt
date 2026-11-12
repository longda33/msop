package com.msop.player.feature.player.equalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerTuningTest {

    @Test
    fun `所有预设都是 10 段且电平在设备可用范围内`() {
        assertTrue(EqualizerTuning.PRESETS.isNotEmpty())
        EqualizerTuning.PRESETS.forEach { preset ->
            assertEquals("${preset.name} 段数", EqualizerTuning.BAND_COUNT, preset.gainsMb.size)
            preset.gainsMb.forEach { gain ->
                assertTrue(
                    "${preset.name} 的 $gain 越界",
                    gain in EqualizerTuning.LEVEL_MIN_MB..EqualizerTuning.LEVEL_MAX_MB,
                )
            }
        }
    }

    @Test
    fun `第一个预设是平直且不改变声音`() {
        val flat = EqualizerTuning.PRESETS.first()
        assertEquals("平直", flat.name)
        assertEquals(List(10) { 0 }, flat.gainsMb)
    }

    @Test
    fun `按名字取预设`() {
        assertNotNull(EqualizerTuning.preset("摇滚"))
        assertNull(EqualizerTuning.preset("不存在的预设"))
    }

    @Test
    fun `clamp 把任意电平夹到 -1500 到 1500 mB`() {
        assertEquals(-1500, EqualizerTuning.clamp(-99_999))
        assertEquals(1500, EqualizerTuning.clamp(99_999))
        assertEquals(350, EqualizerTuning.clamp(350))
        assertEquals(-1500, EqualizerTuning.clamp(-1500))
        assertEquals(1500, EqualizerTuning.clamp(1500))
    }

    @Test
    fun `10 段设备原样输出曲线`() {
        // 标准 10 段中心频率，API 单位是 mHz
        val mhz = EqualizerTuning.CENTER_FREQUENCIES_HZ.map { it * 1000 }
        val gains = EqualizerTuning.preset("摇滚")!!.gainsMb
        assertEquals(gains, EqualizerTuning.mapToBands(gains, mhz))
    }

    @Test
    fun `5 段设备按对数频率最近邻映射`() {
        // 60Hz / 230Hz / 910Hz / 3600Hz / 14000Hz（单位 mHz）
        val device = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
        val gains = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        val mapped = EqualizerTuning.mapToBands(gains, device)
        assertEquals(5, mapped.size)
        // 60Hz 落在 62Hz 段；230Hz 落在 250Hz 段；910Hz 落在 1kHz 段
        assertEquals(listOf(200, 400, 600, 800, 1000), mapped)
    }

    @Test
    fun `映射会钳制越界电平且容忍空设备列表`() {
        assertEquals(emptyList<Int>(), EqualizerTuning.mapToBands(emptyList(), emptyList()))
        // 31Hz / 62Hz 两段分别对应标准模型的前两段
        val out = EqualizerTuning.mapToBands(
            listOf(99_999, -99_999),
            listOf(31_000, 62_000),
        )
        assertEquals(listOf(1500, -1500), out)
    }

    @Test
    fun `曲线不足 10 段时缺失段按 0 处理`() {
        val mapped = EqualizerTuning.mapToBands(listOf(500), listOf(16_000_000))
        assertEquals(listOf(0), mapped)
    }

    @Test
    fun `nearestIndex 对每个标准频率都返回自身`() {
        EqualizerTuning.CENTER_FREQUENCIES_HZ.forEachIndexed { index, hz ->
            assertEquals("$hz Hz", index, EqualizerTuning.nearestIndex(hz))
        }
    }

    @Test
    fun `nearestIndex 对非标准频率取对数最近`() {
        assertEquals(1, EqualizerTuning.nearestIndex(60)) // 62Hz
        assertEquals(3, EqualizerTuning.nearestIndex(230)) // 250Hz
        assertEquals(5, EqualizerTuning.nearestIndex(900)) // 1kHz
        assertEquals(0, EqualizerTuning.nearestIndex(1)) // 下边界不越界
        assertEquals(9, EqualizerTuning.nearestIndex(100_000)) // 上边界不越界
    }

    @Test
    fun `频率格式化`() {
        assertEquals("31", EqualizerTuning.formatFrequency(31))
        assertEquals("500", EqualizerTuning.formatFrequency(500))
        assertEquals("1k", EqualizerTuning.formatFrequency(1000))
        assertEquals("4k", EqualizerTuning.formatFrequency(4000))
        assertEquals("16k", EqualizerTuning.formatFrequency(16000))
        assertEquals("2.5k", EqualizerTuning.formatFrequency(2500))
    }

    @Test
    fun `增益格式化把 mB 转成 dB 文案`() {
        assertEquals("0 dB", EqualizerTuning.formatGain(0))
        assertEquals("+3.5 dB", EqualizerTuning.formatGain(350))
        assertEquals("-1.5 dB", EqualizerTuning.formatGain(-150))
        assertEquals("+15.0 dB", EqualizerTuning.formatGain(1500))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `段数不对的预设直接拒绝`() {
        EqualizerTuning.Preset("坏预设", listOf(0, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `电平越界的预设直接拒绝`() {
        EqualizerTuning.Preset("坏预设", List(10) { 99_999 })
    }
}
