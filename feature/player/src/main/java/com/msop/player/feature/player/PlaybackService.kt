package com.msop.player.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.msop.player.core.common.log.AppLog
import com.msop.player.feature.player.equalizer.EqualizerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 后台播放服务。
 *
 * 用 `MediaSessionService` 而不是自己写前台服务：通知栏、锁屏控制、耳机按键、
 * 车机/手表控制全都由 Media3 通过系统 MediaSession 自动接好，我们只需要交出
 * 一个 Player 和一个 MediaSession。
 *
 * 服务生命周期由 Media3 管：控制器连上来就创建，播放结束且任务被移除才停。
 *
 * **均衡器挂在这里（P5-03）**：`Equalizer` 必须绑到 ExoPlayer 的 audioSessionId，
 * 而 UI 侧只有 MediaController，拿不到 session。服务和 UI 同进程，两边注入同一个
 * [EqualizerController] 单例，在 `onAudioSessionIdChanged` 时 attach 即可。
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var equalizer: EqualizerController

    @Inject
    lateinit var playerFactory: PlayerFactory

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.i("PlaybackService onCreate")
        val player = playerFactory.create()

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                equalizer.attach(audioSessionId)
            }
        })
        // 少数 ROM 在监听器注册之前就分配了 sessionId，兜一次底
        if (player.audioSessionId > 0) equalizer.attach(player.audioSessionId)

        val builder = MediaSession.Builder(this, player)
        // 点通知要能回到应用；拿不到启动 Intent 时就不设，Media3 会用默认行为
        val activityIntent = launchIntent()
        activityIntent?.let(builder::setSessionActivity)
        mediaSession = builder.build()

        // 2026-11-16 第十三轮：用户拍板「换回系统默认媒体大卡」（方案 B）。
        // 之前的自绘卡（普通通知 + DecoratedCustomViewStyle + RemoteViews）布局可控，
        // 但卡外要套一圈系统白底、锁屏还会被重新套白，跟截图里的系统媒体卡是两种东西；
        // 那两个截图的来源项目 Convx 本身也只用 DefaultMediaNotificationProvider。
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notif_channel_playing)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        // 1.11 的 Builder 没有 setSmallIcon，小图标是实例方法（默认是 Media3 自带的播放图标）
        notificationProvider.setSmallIcon(R.drawable.ic_stat_msop)
        setMediaNotificationProvider(notificationProvider)
    }

    private companion object {
        /** 每次换 id 才能改通知的渠道设置；MediaStyle 由系统渲染成大卡 + 传输键。 */
        const val CHANNEL_ID = "msop_playback_v3"
        const val NOTIFICATION_ID = 1001
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * 用户从最近任务里划掉应用时：正在播就继续（后台播放是本分），
     * 没在播就把服务停掉，别留一个空进程。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            AppLog.i("PlaybackService 未在播放且任务被移除，停止服务")
            stopSelf()
        }
    }

    override fun onDestroy() {
        AppLog.i("PlaybackService onDestroy")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        // AudioEffect 是全局资源，服务没了必须释放；设置本身留在 SharedPreferences 里
        equalizer.release()
        super.onDestroy()
    }

    /** 点通知回到应用。用包名取启动 Intent，避免 feature 模块反向依赖 app 的 Activity。 */
    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
