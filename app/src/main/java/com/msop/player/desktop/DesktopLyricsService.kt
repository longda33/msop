package com.msop.player.desktop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.msop.player.MainActivity
import com.msop.player.R
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.Track
import com.msop.player.feature.lyrics.Lyrics
import com.msop.player.feature.lyrics.LyricsRepository
import com.msop.player.feature.player.PlaybackController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 桌面悬浮歌词（P7-08）。
 *
 * 为什么不用 Compose：悬浮窗需要独立于 Activity 的 View 树，直接用
 * `WindowManager` + `TextView` 最稳，也避免在 Service 里再起一套 Compose 生命周期。
 *
 * 三条生命周期约定：
 * - `START_NOT_STICKY`：进程被杀后系统**不会**自动重启悬浮窗；
 * - 暂停/停止播放满 10 分钟自动隐藏（`GONE`，不销毁窗口，恢复播放立即显示）；
 * - 设置页关闭开关会 `stopService`，通知栏也提供「关闭」。
 *
 * 锁定时窗口加 `FLAG_NOT_TOUCHABLE`，不会挡住桌面和其他 App 的点击；
 * 解锁后整个胶囊可拖动，松手自动吸附到左右边。
 */
@AndroidEntryPoint
class DesktopLyricsService : Service() {

    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var lyricsRepository: LyricsRepository
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var prefs: DesktopLyricsPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var currentView: TextView
    private lateinit var nextView: TextView
    private lateinit var params: WindowManager.LayoutParams

    private var loadJob: Job? = null
    private var hideJob: Job? = null

    private var currentTrackId: String? = null
    private var currentTrack: Track? = null
    private var lyrics: Lyrics? = null
    private var isPlaying = false
    private var overlayAttached = false

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (!prefs.enabled) {
                stopSelf()
            } else {
                applyStyle()
                showOverlay()
            }
        }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            // 权限被撤销时不要带着一个空通知栏挂着
            stopSelf()
            return
        }

        addOverlay()
        if (!overlayAttached) return
        applyStyle()
        prefs.registerListener(preferenceListener)
        observePlayback()
        startPositionTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        prefs.unregisterListener(preferenceListener)
        loadJob?.cancel()
        hideJob?.cancel()
        scope.cancel()
        if (::root.isInitialized) {
            runCatching { windowManager.removeView(root) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ------------------------------------------------------------ 窗口

    private fun addOverlay() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnTouchListener(dragListener)
        }
        currentView = TextView(this).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        nextView = TextView(this).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setShadowLayer(5f, 0f, 2f, Color.argb(140, 0, 0, 0))
        }
        root.addView(currentView)
        root.addView(nextView)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // 先量一次，才能在默认位置把胶囊摆到屏幕下方居中
        root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bounds = screenBounds()
        if (prefs.x == DesktopLyricsPreferences.UNSET_POSITION) {
            params.x = ((bounds.left + bounds.width() - root.measuredWidth) / 2).coerceAtLeast(0)
            params.y = (bounds.top + bounds.height() * 3 / 4).coerceAtLeast(0)
        } else {
            params.x = prefs.x
            params.y = prefs.y
        }

        try {
            windowManager.addView(root, params)
            overlayAttached = true
        } catch (throwable: Throwable) {
            com.msop.player.core.common.log.AppLog.e("桌面歌词窗口无法添加，检查悬浮窗权限", throwable)
            stopSelf()
            return
        }
        currentView.text = "msop"
        nextView.text = ""
    }

    private fun applyStyle() {
        if (!::root.isInitialized) return
        val color = DesktopLyricsPreferences.TEXT_COLORS[prefs.colorIndex]
        val fontSp = prefs.fontSp

        currentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
        currentView.setTextColor(color)
        nextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (fontSp - 4).coerceAtLeast(11).toFloat())
        nextView.setTextColor(color)
        nextView.visibility = if (prefs.singleLine) View.GONE else View.VISIBLE

        val alpha = (prefs.backgroundPercent.coerceIn(0, 80) * 255 / 100)
        root.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(alpha, 0, 0, 0))
            setStroke(dp(1), Color.argb(36, 255, 255, 255))
        }

        params.flags = if (prefs.locked) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        updateLayout()
    }

    private fun updateLayout() {
        if (root.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }
    }

    private val dragListener = object : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (prefs.locked) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).roundToInt()
                    params.y = startY + (event.rawY - downRawY).roundToInt()
                    clampToScreen()
                    updateLayout()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    snapToEdge()
                    updateLayout()
                    prefs.savePosition(params.x, params.y)
                    return true
                }
            }
            return false
        }
    }

    private fun clampToScreen() {
        val bounds = screenBounds()
        val maxX = (bounds.width() - root.width).coerceAtLeast(0)
        val maxY = (bounds.height() - root.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun snapToEdge() {
        val bounds = screenBounds()
        val maxX = (bounds.width() - root.width).coerceAtLeast(0)
        val center = params.x + root.width / 2
        params.x = if (center < bounds.width() / 2) 0 else maxX
        params.y = params.y.coerceIn(0, (bounds.height() - root.height).coerceAtLeast(0))
    }

    private fun screenBounds(): android.graphics.Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
            android.graphics.Rect(0, 0, point.x, point.y)
        }
    }

    private fun showOverlay() {
        if (::root.isInitialized && root.visibility != View.VISIBLE) {
            root.visibility = View.VISIBLE
        }
    }

    private fun hideOverlay() {
        if (::root.isInitialized && root.visibility != View.GONE) {
            root.visibility = View.GONE
        }
    }

    // ------------------------------------------------------------ 播放/歌词

    private fun observePlayback() {
        scope.launch {
            playback.state.collect { state ->
                if (state.currentTrackId != currentTrackId) {
                    loadTrack(state.currentTrackId, state.currentTitle)
                }
                onPlayingChanged(state.isPlaying)
            }
        }
    }

    private fun loadTrack(trackId: String?, fallbackTitle: String?) {
        loadJob?.cancel()
        currentTrackId = trackId
        currentTrack = null
        lyrics = null
        if (trackId == null) {
            currentView.text = fallbackTitle ?: "msop"
            nextView.text = ""
            return
        }

        currentView.text = fallbackTitle ?: "正在加载歌词…"
        nextView.text = ""
        loadJob = scope.launch {
            val track = try {
                trackDao.findById(trackId)?.toTrack()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (currentTrackId != trackId) return@launch
            if (track == null) {
                currentView.text = fallbackTitle ?: "msop"
                return@launch
            }

            currentTrack = track
            currentView.text = track.title
            nextView.text = track.artist.orEmpty()
            val loaded = try {
                lyricsRepository.load(track)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (currentTrackId != trackId) return@launch
            lyrics = loaded
            renderAt(playback.currentPositionMs())
        }
    }

    private fun startPositionTicker() {
        scope.launch {
            while (isActive) {
                if (currentTrack != null && this@DesktopLyricsService.lyrics != null) {
                    renderAt(playback.currentPositionMs())
                }
                delay(if (isPlaying) 200L else 1_000L)
            }
        }
    }

    private fun renderAt(positionMs: Long) {
        val track = currentTrack ?: return
        // 文本规则抽在 DesktopLyricsText 里：Service 只负责把结果贴到两个 TextView 上
        val content = DesktopLyricsText.at(
            title = track.title,
            artist = track.artist,
            lyrics = lyrics,
            positionMs = positionMs,
        )
        if (currentView.text.toString() != content.current) currentView.text = content.current
        if (nextView.text.toString() != content.next) nextView.text = content.next
    }

    private fun onPlayingChanged(playing: Boolean) {
        isPlaying = playing
        hideJob?.cancel()
        if (playing) {
            showOverlay()
        } else {
            hideJob = scope.launch {
                delay(HIDE_AFTER_PAUSE_MS)
                hideOverlay()
            }
        }
    }

    // ------------------------------------------------------------ 通知

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "桌面歌词",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "桌面悬浮歌词的运行状态"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DesktopLyricsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.msop_icon)
            .setContentTitle("桌面歌词已开启")
            .setContentText("在桌面上显示当前播放歌词")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.msop_icon),
                    "关闭",
                    stop,
                ).build(),
            )
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "desktop_lyrics"
        private const val NOTIFICATION_ID = 0x4D53
        private const val ACTION_STOP = "com.msop.player.desktop.STOP"
        private const val HIDE_AFTER_PAUSE_MS = 10 * 60 * 1000L

        /** 从设置页启动；调用前必须已拿到 `SYSTEM_ALERT_WINDOW`。 */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DesktopLyricsService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DesktopLyricsService::class.java))
        }
    }
}
