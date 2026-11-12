package com.msop.player.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** UI 只需要这些就能画出播放条与播放页。位置/时长由 UI 自己按帧轮询，不从这里推。 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: String? = null,
    val currentTitle: String? = null,
    val queueSize: Int = 0,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * 播放控制的唯一入口。
 *
 * 不直接持有 ExoPlayer，而是通过 [MediaController] 连到 [PlaybackService]：
 * 播放器活在服务里，UI 进程的页面来去不影响播放；控制器断开重连也不会打断声音。
 *
 * 所有方法都是 suspend 并切到主线程——[MediaController] 只能在构造它的 Looper 上调用，
 * 而构造发生在主线程。以后从任意协程调用都不会踩线程问题。
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val connectLock = Mutex()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /**
     * 用给定队列开始播放。
     *
     * @param startIndex 从队列第几首开始，越界会被夹到合法范围
     * @param mode 播放模式，默认沿用当前模式
     */
    suspend fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        mode: PlayMode = _state.value.playMode,
    ) = withContext(Dispatchers.Main.immediate) {
        if (tracks.isEmpty()) return@withContext
        val controller = requireController()
        applyMode(controller, mode)
        controller.setMediaItems(
            tracks.map { it.toMediaItem() },
            startIndex.coerceIn(0, tracks.lastIndex),
            0L,
        )
        controller.prepare()
        controller.play()
    }

    suspend fun toggle() = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        if (controller.mediaItemCount == 0) return@withContext
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    /**
     * 下一首。顺序模式播到队尾时**回到第一首**——用户按了按钮总得有反应，
     * 自动续播仍然按 repeat 设置走（顺序模式播完就停）。
     */
    suspend fun next() = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        when {
            controller.mediaItemCount == 0 -> Unit
            controller.hasNextMediaItem() -> controller.seekToNextMediaItem()
            else -> controller.seekToDefaultPosition(0)
        }
    }

    /** 上一首。`seekToPrevious` 自带「播放超过 3 秒先回到本曲开头」的惯例。 */
    suspend fun previous() = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        if (controller.mediaItemCount > 0) controller.seekToPrevious()
    }

    suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main.immediate) {
        requireController().seekTo(positionMs.coerceAtLeast(0L))
    }

    /**
     * 读当前播放位置。UI 的进度条靠它轮询（约 500ms 一次）。
     *
     * 为什么不放进 [PlaybackState]：`Player.Listener` 只在事件发生时回调，
     * 播放中不会每帧推送位置。与其在服务里起一个定时器推 Flow，
     * 不如让正在显示进度条的那个页面自己按需拉——不看播放页时零开销。
     */
    suspend fun currentPositionMs(): Long = withContext(Dispatchers.Main.immediate) {
        controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    suspend fun setMode(mode: PlayMode) = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        applyMode(controller, mode)
        publish(controller)
    }

    /** 按 顺序 → 单曲 → 随机 → 顺序 轮转，返回切换后的模式。 */
    suspend fun cycleMode(): PlayMode = withContext(Dispatchers.Main.immediate) {
        val next = when (_state.value.playMode) {
            PlayMode.SEQUENTIAL -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SEQUENTIAL
        }
        val controller = requireController()
        applyMode(controller, next)
        publish(controller)
        next
    }

    /**
     * 队列内拖动排序。给 P2-05 / P4-04 的拖拽用，逻辑现在就位。
     *
     * 注意：当前播放项的位置会随之变化，ExoPlayer 会保持同一个 item 继续播，
     * 不需要手动 seek。
     */
    suspend fun moveInQueue(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        if (fromIndex !in 0 until controller.mediaItemCount) return@withContext
        if (toIndex !in 0 until controller.mediaItemCount) return@withContext
        if (fromIndex == toIndex) return@withContext
        controller.moveMediaItem(fromIndex, toIndex)
    }

    suspend fun pause() = withContext(Dispatchers.Main.immediate) {
        controller?.pause()
    }

    suspend fun release() = withContext(Dispatchers.Main.immediate) {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun applyMode(controller: MediaController, mode: PlayMode) {
        when (mode) {
            PlayMode.SEQUENTIAL -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlayMode.REPEAT_ONE -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ONE
            }
            PlayMode.SHUFFLE -> {
                controller.repeatMode = Player.REPEAT_MODE_ALL
                controller.shuffleModeEnabled = true
            }
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        _state.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrackId = item?.mediaId,
            currentTitle = item?.mediaMetadata?.title?.toString(),
            queueSize = player.mediaItemCount,
            playMode = player.playMode(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    private fun Player.playMode(): PlayMode = when {
        shuffleModeEnabled -> PlayMode.SHUFFLE
        repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
        else -> PlayMode.SEQUENTIAL
    }

    private suspend fun requireController(): MediaController {
        controller?.let { return it }
        return connectLock.withLock {
            controller ?: connect().also { created ->
                created.addListener(listener)
                controller = created
                publish(created)
                AppLog.i("PlaybackController 已连接到 PlaybackService")
            }
        }
    }

    /**
     * 建控制器会**顺带启动服务**（bind + start），服务 onCreate 里才创建 ExoPlayer。
     * 所以不要在应用启动时就连接，等用户真的按播放再连。
     */
    private suspend fun connect(): MediaController = suspendCancellableCoroutine { continuation ->
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { if (continuation.isActive) continuation.resume(it) }
                    .onFailure {
                        AppLog.e("PlaybackController 连接失败", it)
                        if (continuation.isActive) continuation.resumeWithException(it)
                    }
            },
            ContextCompat.getMainExecutor(context),
        )

        continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
    }
}
