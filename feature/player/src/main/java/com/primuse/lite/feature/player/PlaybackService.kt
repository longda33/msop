package com.primuse.lite.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.primuse.lite.core.common.log.AppLog

/**
 * 后台播放服务。
 *
 * 用 `MediaSessionService` 而不是自己写前台服务：通知栏、锁屏控制、耳机按键、
 * 车机/手表控制全都由 Media3 通过系统 MediaSession 自动接好，我们只需要交出
 * 一个 Player 和一个 MediaSession。
 *
 * 服务生命周期由 Media3 管：控制器连上来就创建，播放结束且任务被移除才停。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.i("PlaybackService onCreate")
        val player = PlayerFactory(this).create()
        val builder = MediaSession.Builder(this, player)
        // 点通知要能回到应用；拿不到启动 Intent 时就不设，Media3 会用默认行为
        launchIntent()?.let(builder::setSessionActivity)
        mediaSession = builder.build()
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
