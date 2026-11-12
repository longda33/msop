package com.primuse.lite.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.primuse.lite.core.network.ServerUrl
import com.primuse.lite.core.network.StreamUrlResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExoPlayer 的唯一构造点。
 *
 * 四个容易被忽略但必须现在就配的开关：
 * 1. **音频焦点**（`setAudioAttributes(..., handleAudioFocus = true)`）——来电/导航播报时
 *    ExoPlayer 自己暂停，焦点回来自己续播。不配的话会和导航抢声音。
 * 2. **耳机拔出暂停**（`setHandleAudioBecomingNoisy(true)`）——拔耳机时声音不该从外放喷出来。
 * 3. **唤醒锁**（`setWakeMode`）——息屏后 CPU 睡下去会把播放掐断。
 * 4. **关闭 audio offload**（P5-03）——offload 时音频绕过应用进程直接送到 DSP，
 *    `android.media.audiofx.Equalizer` 收不到数据，10 段 EQ 会「开着却完全没效果」。
 *    这是均衡器最隐蔽的坑，必须在构造播放器时就关，运行时改不回来。
 *    Media3 1.11 已删掉 `DefaultRenderersFactory.setEnableAudioOffload`，
 *    改由 `AudioTrackAudioOutputProvider` 上报「不支持 offload」来表达同一意图。
 *
 * [ResolvingDataSource] 是 P3 的接缝：数据库里存的是稳定的 `openlist:/路径`，
 * 每次起播（含 seek 后重新打开）才去 `/api/fs/get` 换一条带 tempauth 的新鲜直链。
 * 绝不能把 raw_url 存库——实测它的签名有时效，过期整个曲库都会播不了。
 */
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamResolver: StreamUrlResolver,
) {

    fun create(): ExoPlayer {
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context),
            ResolvingDataSource.Resolver { dataSpec -> resolveRemote(dataSpec) },
        )

        // 上报「设备不支持 offload」，ExoPlayer 就不会走 DSP 直通路径。
        // 不能简单地不设置：默认 provider 会在支持的格式上自动开启 offload。
        val audioOutputProvider = AudioTrackAudioOutputProvider.Builder(context)
            .setAudioOffloadSupportProvider(
                DefaultAudioSink.AudioOffloadSupportProvider { _, _ ->
                    AudioOffloadSupport.DEFAULT_UNSUPPORTED
                },
            )
            .build()

        return ExoPlayer.Builder(context)
            .setAudioOutputProvider(audioOutputProvider)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** 本地 `content://` 原样放行；远程换成新鲜直链。同步网络请求在 DataSource 线程执行。 */
    private fun resolveRemote(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri.toString()
        if (!uri.startsWith(ServerUrl.SCHEME)) return dataSpec
        val fresh = streamResolver.resolve(uri)
        return dataSpec.buildUpon().setUri(Uri.parse(fresh)).build()
    }

    private companion object {
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
