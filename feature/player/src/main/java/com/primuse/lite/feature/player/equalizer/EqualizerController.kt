package com.primuse.lite.feature.player.equalizer

import android.content.Context
import android.media.audiofx.Equalizer
import com.primuse.lite.core.common.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val levelMb: Int,
)

data class EqualizerState(
    val enabled: Boolean = false,
    /** null 表示「自定义」——用户拖过滑杆，不再属于任何预设。 */
    val presetName: String? = EqualizerTuning.PRESETS.first().name,
    val bands: List<EqualizerBand> = EqualizerTuning.CENTER_FREQUENCIES_HZ.mapIndexed { i, hz ->
        EqualizerBand(i, hz, 0)
    },
    /** 是否已经拿到播放器的真实 audio session。false 时改动会保存，下次播放自动生效。 */
    val attached: Boolean = false,
    val minLevelMb: Int = EqualizerTuning.LEVEL_MIN_MB,
    val maxLevelMb: Int = EqualizerTuning.LEVEL_MAX_MB,
)

/**
 * 10 段均衡器的唯一状态源，UI 与 [com.primuse.lite.feature.player.PlaybackService] 共用同一个单例。
 *
 * 为什么不放进 `PlaybackController`：UI 那边只有 `MediaController`，拿不到 ExoPlayer 的
 * audioSessionId，而 `android.media.audiofx.Equalizer` 必须绑到具体的 session 才生效。
 * 两边同处一个进程，用 Hilt 单例直连比走 Media3 custom command 简单得多，也不用担心
 * 序列化。播放器活在服务里，服务创建时把这个单例 attach 上去即可。
 *
 * 线程：`onAudioSessionIdChanged` 回调在 ExoPlayer 的播放线程，UI 在主线程，
 * 所以所有读写方法都加 [Synchronized]，SharedPreferences 自身也是线程安全的。
 */
@Singleton
class EqualizerController @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var effect: Equalizer? = null
    private var sessionId: Int? = null
    private var deviceFreqsMhz: List<Int> = emptyList()

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /** 播放器建好 AudioTrack 后调用。重复 attach 同一个 session 会被忽略。 */
    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || (sessionId == audioSessionId && effect != null)) return
        detachInternal()

        val eq = runCatching { Equalizer(EFFECT_PRIORITY, audioSessionId) }
            .onFailure { AppLog.e("设备不支持 Equalizer，均衡器不可用", it) }
            .getOrNull() ?: run {
            _state.value = _state.value.copy(attached = false)
            return
        }

        val freqsMhz = (0 until eq.numberOfBands.toInt()).map { band ->
            eq.getCenterFreq(band.toShort())
        }
        val range = eq.bandLevelRange
        val minMb = range.getOrElse(0) { EqualizerTuning.LEVEL_MIN_MB.toShort() }.toInt()
        val maxMb = range.getOrElse(1) { EqualizerTuning.LEVEL_MAX_MB.toShort() }.toInt()

        effect = eq
        sessionId = audioSessionId
        deviceFreqsMhz = freqsMhz

        // 用持久化的 10 段曲线映射到设备实际段数，再把设备允许的电平范围夹一遍
        val levels = EqualizerTuning.mapToBands(savedLevels(), freqsMhz)
            .map { it.coerceIn(minMb, maxMb) }
        levels.forEachIndexed { band, level ->
            runCatching { eq.setBandLevel(band.toShort(), level.toShort()) }
        }
        runCatching { eq.enabled = _state.value.enabled }
            .onFailure { AppLog.e("启用 Equalizer 失败", it) }

        _state.value = _state.value.copy(
            bands = freqsMhz.mapIndexed { band, mhz ->
                EqualizerBand(band, mhz / 1000, levels[band])
            },
            attached = true,
            minLevelMb = minMb,
            maxLevelMb = maxMb,
        )
        AppLog.i("均衡器已绑定 audioSession=$audioSessionId 段数=${freqsMhz.size} " +
            "范围=[$minMb,$maxMb] enabled=${_state.value.enabled}")
    }

    /** 服务销毁时释放 AudioEffect，避免占着全局音频效果链。 */
    @Synchronized
    fun release() {
        detachInternal()
        _state.value = _state.value.copy(attached = false)
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        effect?.let { runCatching { it.enabled = enabled } }
        _state.value = _state.value.copy(enabled = enabled)
        AppLog.i("均衡器开关=$enabled")
    }

    @Synchronized
    fun setBandLevel(index: Int, levelMb: Int) {
        val state = _state.value
        val band = state.bands.getOrNull(index) ?: return
        val clamped = clampToDevice(levelMb, state)

        effect?.let { eq -> runCatching { eq.setBandLevel(index.toShort(), clamped.toShort()) } }

        // 设备段数可能是 5，回写时按频率映射回标准 10 段模型，换设备也能保住曲线
        val target = EqualizerTuning.nearestIndex(band.frequencyHz)
        prefs.edit().apply {
            putInt(KEY_LEVEL_PREFIX + target, EqualizerTuning.clamp(levelMb))
            putString(KEY_PRESET, null)
        }.apply()

        _state.value = state.copy(
            bands = state.bands.toMutableList().also { it[index] = band.copy(levelMb = clamped) },
            presetName = null,
        )
    }

    /** 选预设会顺带打开均衡器——用户点了「摇滚」却没声音变化，是最难排查的「假开关」。 */
    @Synchronized
    fun applyPreset(preset: EqualizerTuning.Preset) {
        val state = _state.value
        val displayed = if (deviceFreqsMhz.isEmpty()) {
            preset.gainsMb
        } else {
            EqualizerTuning.mapToBands(preset.gainsMb, deviceFreqsMhz)
        }.map { clampToDevice(it, state) }

        effect?.let { eq ->
            displayed.forEachIndexed { band, level ->
                runCatching { eq.setBandLevel(band.toShort(), level.toShort()) }
            }
            runCatching { eq.enabled = true }
        }

        prefs.edit().apply {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_PRESET, preset.name)
            preset.gainsMb.forEachIndexed { i, gain -> putInt(KEY_LEVEL_PREFIX + i, gain) }
        }.apply()

        _state.value = state.copy(
            enabled = true,
            presetName = preset.name,
            bands = state.bands.mapIndexed { i, band ->
                band.copy(levelMb = displayed.getOrElse(i) { 0 })
            },
        )
        AppLog.i("均衡器预设=${preset.name}")
    }

    fun reset() = applyPreset(EqualizerTuning.PRESETS.first())

    private fun clampToDevice(levelMb: Int, state: EqualizerState): Int =
        EqualizerTuning.clamp(levelMb).coerceIn(state.minLevelMb, state.maxLevelMb)

    private fun detachInternal() {
        effect?.let { eq -> runCatching { eq.release() } }
        effect = null
        sessionId = null
        deviceFreqsMhz = emptyList()
    }

    private fun savedLevels(): List<Int> =
        EqualizerTuning.CENTER_FREQUENCIES_HZ.indices.map { i ->
            EqualizerTuning.clamp(prefs.getInt(KEY_LEVEL_PREFIX + i, 0))
        }

    private fun loadState(): EqualizerState {
        val levels = savedLevels()
        return EqualizerState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            presetName = prefs.getString(KEY_PRESET, EqualizerTuning.PRESETS.first().name),
            bands = EqualizerTuning.CENTER_FREQUENCIES_HZ.mapIndexed { i, hz ->
                EqualizerBand(i, hz, levels[i])
            },
        )
    }

    private companion object {
        const val PREFS_NAME = "primuse_equalizer"
        const val KEY_ENABLED = "enabled"
        const val KEY_PRESET = "preset"
        const val KEY_LEVEL_PREFIX = "level_"
        const val EFFECT_PRIORITY = 0
    }
}
