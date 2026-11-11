package com.primuse.lite.feature.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * ExoPlayer 的唯一构造点。
 *
 * 三个容易被忽略但必须现在就配的开关：
 * 1. **音频焦点**（`setAudioAttributes(..., handleAudioFocus = true)`）——来电/导航播报时
 *    ExoPlayer 自己暂停，焦点回来自己续播。不配的话会和导航抢声音。
 * 2. **耳机拔出暂停**（`setHandleAudioBecomingNoisy(true)`）——拔耳机时声音不该从外放喷出来。
 * 3. **唤醒锁**（`setWakeMode`）——息屏后 CPU 睡下去会把播放掐断。
 *
 * [ResolvingDataSource] 现在是恒等转发，留着是给 P3 的接缝：远程音源要在这里改写
 * DataSpec（拼鉴权参数、处理重定向），而不是去改每个 MediaItem 的 URI。
 */
class PlayerFactory(private val context: Context) {

    fun create(): ExoPlayer {
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context),
            ResolvingDataSource.Resolver { dataSpec -> dataSpec },
        )

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private companion object {
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
