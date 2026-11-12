package com.msop.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.msop.player.feature.player.equalizer.EqualizerBand
import com.msop.player.feature.player.equalizer.EqualizerState
import com.msop.player.feature.player.equalizer.EqualizerTuning
import com.msop.player.ui.components.GroupTitle
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * 均衡器全屏浮层。跟歌单页一样挂在设置页上方，不进导航返回栈。
 *
 * 段数**按设备实际报告画**：不少 ROM 的 `Equalizer` 只给 5 段，写死 10 段滑杆
 * 会有一半拖动没反应。持久化始终用标准 10 段曲线，由 `EqualizerTuning` 换算。
 */
@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onPreset: (EqualizerTuning.Preset) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = Dim.screen, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Text(
                text = "均衡器",
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.Blue,
                    uncheckedThumbColor = Palette.TextTertiary,
                    uncheckedTrackColor = Palette.Surface3,
                ),
            )
        }

        Text(
            text = if (state.attached) {
                "已连接播放器 · ${state.bands.size} 段 · 已关闭 audio offload"
            } else {
                "尚未开始播放，设置已保存，开始播放后自动生效"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.attached) Palette.Green else Palette.TextTertiary,
            modifier = Modifier.padding(horizontal = Dim.screen, vertical = 4.dp),
        )

        GroupTitle("预设")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dim.screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqualizerTuning.PRESETS.forEach { preset ->
                val selected = state.presetName == preset.name
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Palette.Blue else Palette.Surface2)
                        .clickable { onPreset(preset) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else Palette.TextSecondary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dim.screen, end = Dim.screen, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "频段",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) {
                Text("恢复平直", color = Palette.BlueLight)
            }
        }

        val min = state.minLevelMb.toFloat()
        val max = state.maxLevelMb.toFloat()
        // 设备若返回了退化范围（min >= max），Slider 会抛 IllegalArgumentException
        val range = if (max > min) min..max else -1500f..1500f

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dim.screen),
        ) {
            items(items = state.bands, key = { it.index }) { band ->
                BandSliderRow(
                    band = band,
                    enabled = state.enabled,
                    range = range,
                    onBandChange = onBandChange,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BandSliderRow(
    band: EqualizerBand,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onBandChange: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = EqualizerTuning.formatFrequency(band.frequencyHz),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.width(40.dp),
        )
        Slider(
            value = band.levelMb.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onBandChange(band.index, it.roundToInt()) },
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Palette.BlueLight,
                activeTrackColor = Palette.Blue,
                inactiveTrackColor = Palette.Surface3,
                disabledThumbColor = Palette.TextTertiary,
                disabledActiveTrackColor = Palette.Surface3,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = EqualizerTuning.formatGain(band.levelMb),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Palette.TextPrimary else Palette.TextTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(60.dp),
        )
    }
}
