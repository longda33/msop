package com.msop.player.feature.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.msop.player.core.common.log.AppLog
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    /** 当前曲目来源；本地曲目断网不受影响，远程曲目要触发保护。 */
    val currentSource: SourceType? = null,
    /** `Player.STATE_BUFFERING`：播放页显示转圈，超过阈值提示网络慢。 */
    val isBuffering: Boolean = false,
    /** 远程曲目因断网被自动暂停；恢复网络只清除提示，不自动续播。 */
    val networkLost: Boolean = false,
)

/** 冷启动恢复出来的播放现场；UI 用它填队列与进度，不负责起播。 */
data class RestoredSession(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
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
    private val trackDao: TrackDao,
    private val snapshots: PlaybackSnapshotStore,
    private val networkMonitor: NetworkMonitor,
) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val connectLock = Mutex()

    /** P7-02：播放中每 5 秒落一次快照，暂停/切歌时立即落。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotTicker: Job? = null
    private var lastSnapshotAt = 0L
    private var snapshotTrackId: String? = null
    private var snapshotWasPlaying = false
    private var suppressSnapshotSave = false

    /** 队列里每一首的来源，用来判断断网时是否需要保护。 */
    private var sourceById: Map<String, SourceType> = emptyMap()
    private var networkLost = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        // P7-03：断网只暂停远程曲目；恢复网络只清提示，不自动续播。
        scope.launch {
            networkMonitor.online.collect { online ->
                if (online) {
                    if (networkLost) {
                        networkLost = false
                        _state.value = _state.value.copy(networkLost = false)
                        AppLog.i("网络已恢复，等待用户手动继续播放")
                    }
                } else {
                    pauseRemoteIfOffline()
                }
            }
        }
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
        sourceById = tracks.associate { it.id to it.source }
        applyMode(controller, mode)
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        controller.setMediaItems(
            tracks.map { it.toMediaItem() },
            index,
            0L,
        )
        controller.prepare()
        controller.play()
        // 2026-11-17 修复：起播瞬间就按「这次要播的那一首」判断来源。
        // 原来读 `controller.currentMediaItem`，而 MediaController 的队列状态是异步同步的，
        // 此时还是 null → 直接 return，离线点播远程曲目要等几秒才被兜住。
        pauseRemoteIfOffline(source = tracks[index].source, requestedPlaying = true)
    }

    suspend fun toggle() = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        if (controller.mediaItemCount == 0) return@withContext
        val willPlay = !controller.isPlaying
        if (willPlay) controller.play() else controller.pause()
        // 明确告诉断网判定「用户这次是想播还是想停」：想播才提示网络问题，
        // 主动暂停时不弹「网络断开」
        val source = _state.value.currentSource
            ?: controller.currentMediaItem?.mediaId?.let { sourceById[it] }
        pauseRemoteIfOffline(source = source, requestedPlaying = willPlay)
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
        pauseRemoteIfOffline()
    }

    /** 上一首。`seekToPrevious` 自带「播放超过 3 秒先回到本曲开头」的惯例。 */
    suspend fun previous() = withContext(Dispatchers.Main.immediate) {
        val controller = requireController()
        if (controller.mediaItemCount > 0) controller.seekToPrevious()
        pauseRemoteIfOffline()
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

    /** 按 顺序 → 列表循环 → 单曲 → 随机 → 顺序 轮转，返回切换后的模式。 */
    suspend fun cycleMode(): PlayMode = withContext(Dispatchers.Main.immediate) {
        val next = when (_state.value.playMode) {
            PlayMode.SEQUENTIAL -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
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
        saveSnapshot(force = true)
        snapshotTicker?.cancel()
        snapshotTicker = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /**
     * 恢复上次的播放现场（P7-02）。
     *
     * 只在控制器还没有队列时执行，避免覆盖用户刚点的播放。恢复后**只 prepare、
     * 不 play**——冷启动自动出声是很讨厌的行为。队列里已经不存在的曲目会被跳过，
     * 当前曲被删时顺延到原来的位置。
     */
    suspend fun restoreLastSession(): RestoredSession? = withContext(Dispatchers.Main.immediate) {
        val snapshot = snapshots.load() ?: return@withContext null
        controller?.let { if (it.mediaItemCount > 0) return@withContext null }

        val byId = trackDao.findByIds(snapshot.trackIds).associateBy { it.id }
        val restored = snapshot.resolve(byId.keys, System.currentTimeMillis())
        if (restored == null) {
            snapshots.clear()
            return@withContext null
        }

        val tracks = restored.trackIds.mapNotNull { byId[it]?.toTrack() }
        if (tracks.isEmpty()) {
            snapshots.clear()
            return@withContext null
        }

        val index = restored.index.coerceIn(0, tracks.lastIndex)
        val controller = requireController()
        sourceById = tracks.associate { it.id to it.source }
        // 2026-11-17 修复：原来手工置 true/置 false，中间任何一个 setMediaItems/prepare
        // 抛异常，suppressSnapshotSave 就永远停在 true —— 之后所有快照都被静默丢弃，
        // 冷启动再也恢复不了播放现场。改成 try/finally 保证一定复位。
        suppressSnapshotSave = true
        try {
            applyMode(
                controller,
                runCatching { PlayMode.valueOf(snapshot.playMode) }.getOrDefault(PlayMode.SEQUENTIAL),
            )
            controller.setMediaItems(tracks.map { it.toMediaItem() }, index, restored.positionMs)
            controller.playWhenReady = false
            controller.prepare()
            publish(controller)
        } finally {
            suppressSnapshotSave = false
        }
        AppLog.i("已恢复播放现场：${tracks.size} 首，第 ${index + 1} 首，${restored.positionMs}ms")
        RestoredSession(tracks, index, restored.positionMs)
    }

    /** 播放中每 5 秒落一次快照；暂停/切歌时立即落。 */
    private fun syncSnapshotTicker(player: Player) {
        val trackId = player.currentMediaItem?.mediaId
        val force = trackId != snapshotTrackId || player.isPlaying != snapshotWasPlaying
        snapshotTrackId = trackId
        snapshotWasPlaying = player.isPlaying

        if (player.isPlaying) {
            if (snapshotTicker?.isActive == true) {
                // 2026-11-17 修复：播放中切歌/暂停时 ticker 还活着，原来直接 return，
                // 切歌那一刻的现场要等下一个 5 秒才落盘——这期间被杀进程就只能恢复成上一首。
                if (force) saveSnapshot(force = true)
                return
            }
            snapshotTicker = scope.launch {
                while (isActive) {
                    saveSnapshot(force = true)
                    delay(SNAPSHOT_INTERVAL_MS)
                }
            }
        } else {
            snapshotTicker?.cancel()
            snapshotTicker = null
            saveSnapshot(force = force)
        }
    }

    private fun saveSnapshot(force: Boolean) {
        if (suppressSnapshotSave) return
        val player = controller ?: return
        if (player.mediaItemCount == 0) return

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSnapshotAt < SNAPSHOT_MIN_GAP_MS) return
        lastSnapshotAt = now

        val ids = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.takeIf { it.isNotBlank() }
        }
        if (ids.isEmpty()) return

        snapshots.save(
            PlaybackSnapshot(
                trackIds = ids,
                currentTrackId = player.currentMediaItem?.mediaId,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                playMode = player.playMode().name,
                wasPlaying = player.isPlaying,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun applyMode(controller: MediaController, mode: PlayMode) {
        when (mode) {
            PlayMode.SEQUENTIAL -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlayMode.REPEAT_ALL -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ALL
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
            currentSource = item?.mediaId?.let { sourceById[it] },
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            networkLost = networkLost,
        )
        syncSnapshotTicker(player)
    }

    /**
     * 断网保护（P7-03）：只有「正在播放/缓冲的远程曲目」需要暂停。
     * 本地文件断网照常播；用户主动暂停时断网也不弹提示。
     * 起播/切歌时也会主动调一次，覆盖「离线状态下点播放」的情况。
     */
    private fun pauseRemoteIfOffline(source: SourceType? = null, requestedPlaying: Boolean? = null) {
        if (networkMonitor.isOnline()) {
            if (networkLost) {
                networkLost = false
                _state.value = _state.value.copy(networkLost = false)
            }
            return
        }
        // 来源优先用调用方给的「这次操作针对的那一首」，拿不到才回落到控制器/状态里的当前曲
        val target = source
            ?: controller?.currentMediaItem?.mediaId?.let { sourceById[it] }
            ?: _state.value.currentTrackId?.let { sourceById[it] }
        if (target == null || target == SourceType.LOCAL) return

        // 用户主动暂停时断网不弹提示；没传意图才读控制器
        val wantsPlay = requestedPlaying ?: controller?.playWhenReady ?: false
        if (!wantsPlay) return

        controller?.pause()
        networkLost = true
        _state.value = _state.value.copy(isPlaying = false, networkLost = true)
        AppLog.i("网络断开，远程曲目已自动暂停 source=$target")
    }

    private fun Player.playMode(): PlayMode = when {
        shuffleModeEnabled -> PlayMode.SHUFFLE
        repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
        repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.REPEAT_ALL
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

    private companion object {
        const val SNAPSHOT_INTERVAL_MS = 5_000L
        const val SNAPSHOT_MIN_GAP_MS = 1_000L
    }
}
