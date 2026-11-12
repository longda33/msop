package com.msop.player.feature.player.equalizer

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/**
 * 均衡器调音的**纯计算层**，不碰 Android API，所以能在 JVM 单测里完整覆盖。
 *
 * 单位说明：Android `Equalizer` 的电平是 **mB（毫贝）**，不是 dB——`+1000 mB = +10 dB`。
 * 中心频率在 API 里是 **mHz（毫赫兹）**，`60000` 表示 60 Hz。这两处单位错位是
 * 写 EQ 时最容易犯的错，统一在这个文件里换算，别在 UI 里各算各的。
 *
 * 设备实际给的 band 数不一定是 10（很多 ROM 只给 5 段），所以我们始终以
 * [CENTER_FREQUENCIES_HZ] 这套 10 段曲线作为**持久化模型**，用到具体设备时
 * 再用 [mapToBands] 按对数频率最近邻映射过去。换手机也不会丢调音。
 */
object EqualizerTuning {

    const val BAND_COUNT = 10
    const val LEVEL_MIN_MB = -1500
    const val LEVEL_MAX_MB = 1500

    /** 标准 10 段中心频率（Hz）。持久化电平按这个顺序存，与设备无关。 */
    val CENTER_FREQUENCIES_HZ: List<Int> =
        listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    data class Preset(val name: String, val gainsMb: List<Int>) {
        init {
            require(gainsMb.size == BAND_COUNT) {
                "预设 $name 必须有 $BAND_COUNT 段，实际 ${gainsMb.size} 段"
            }
            require(gainsMb.all { it in LEVEL_MIN_MB..LEVEL_MAX_MB }) {
                "预设 $name 的电平超出 ${LEVEL_MIN_MB}..${LEVEL_MAX_MB} mB"
            }
        }
    }

    val PRESETS: List<Preset> = listOf(
        Preset("平直", List(BAND_COUNT) { 0 }),
        Preset("流行", listOf(300, 200, 0, -150, -150, 0, 150, 250, 300, 250)),
        Preset("摇滚", listOf(500, 350, 150, -100, -200, 100, 250, 350, 400, 400)),
        Preset("爵士", listOf(250, 150, 50, 150, -100, -100, 50, 150, 250, 250)),
        Preset("古典", listOf(400, 300, 200, 100, -100, -100, 0, 150, 250, 250)),
        Preset("低音", listOf(900, 700, 450, 200, 0, -100, -100, 0, 0, 0)),
        Preset("人声", listOf(-300, -200, -100, 100, 350, 450, 350, 150, 0, -100)),
    )

    fun preset(name: String): Preset? = PRESETS.firstOrNull { it.name == name }

    fun clamp(levelMb: Int): Int = levelMb.coerceIn(LEVEL_MIN_MB, LEVEL_MAX_MB)

    fun defaultLevels(): List<Int> = List(BAND_COUNT) { 0 }

    /**
     * 把 10 段曲线映射到设备实际 band。
     *
     * 用**对数频率**距离而不是线性距离：31→62 和 8k→16k 都是一倍频程，
     * 线性距离下高频会被极端拉偏。
     *
     * @param gainsMb 按 [CENTER_FREQUENCIES_HZ] 排列的 10 段电平
     * @param deviceCenterFreqsMhz 设备 `getCenterFreq()` 的返回值，单位 mHz
     */
    fun mapToBands(gainsMb: List<Int>, deviceCenterFreqsMhz: List<Int>): List<Int> =
        deviceCenterFreqsMhz.map { freqMhz ->
            val hz = maxOf(1, freqMhz / 1000)
            clamp(gainsMb.getOrElse(nearestIndex(hz)) { 0 })
        }

    /** 找出某个频率在 10 段模型里最接近的索引，供「拖动设备 band 后回写持久化」用。 */
    fun nearestIndex(frequencyHz: Int): Int {
        val target = ln(maxOf(1, frequencyHz).toDouble())
        return CENTER_FREQUENCIES_HZ.indices.minByOrNull { i ->
            abs(ln(CENTER_FREQUENCIES_HZ[i].toDouble()) - target)
        } ?: 0
    }

    /** 频率显示：60000 mHz → "60"，1000000 mHz → "1k"，4000000 mHz → "4k"。 */
    fun formatFrequency(frequencyHz: Int): String = when {
        frequencyHz >= 1000 -> {
            val k = frequencyHz / 1000.0
            if (k == k.toInt().toDouble()) "${k.toInt()}k"
            else String.format(Locale.US, "%.1fk", k)
        }
        else -> frequencyHz.toString()
    }

    /** mB 转显示用的 dB 文案，例如 350 → "+3.5 dB"。 */
    fun formatGain(levelMb: Int): String {
        val db = levelMb / 100.0
        return when {
            levelMb == 0 -> "0 dB"
            levelMb > 0 -> String.format(Locale.US, "+%.1f dB", db)
            else -> String.format(Locale.US, "%.1f dB", db)
        }
    }
}
