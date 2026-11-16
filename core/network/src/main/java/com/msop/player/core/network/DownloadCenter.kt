package com.msop.player.core.network

import com.msop.player.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 下载中心（2026-11-16 晚用户需求「点击下载 反馈没有 进度也没有」）。
 *
 * 之前下载是「点一下 → 后台默默跑 → 几十秒后弹一句 toast」，服务器慢的时候用户完全
 * 不知道有没有在下载。现在下载任务集中在这里，谁点都能看到同一条进度：
 * - `start(track)` 立刻建一条 RUNNING 任务（字节数随回调增长），UI 马上有反馈；
 * - 完成 / 失败各停留几秒后自动从列表移除（不弹 toast，避免和在途任务重复提示）；
 * - 同一首歌正在下载时再点一次会直接忽略，不会下载两份。
 */
@Singleton
class DownloadCenter @Inject constructor(
    private val downloader: RemoteFileDownloader,
) {

    enum class State { RUNNING, DONE, FAILED }

    data class Task(
        val trackId: String,
        val title: String,
        val artist: String?,
        val state: State,
        val bytes: Long = 0L,
        val total: Long? = null,
        val savedPath: String? = null,
        val error: String? = null,
    ) {
        /** 0..1；服务器没给 Content-Length 时返回 null（UI 用不确定进度条）。 */
        val progress: Float?
            get() = total?.takeIf { it > 0L }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    fun start(track: Track) {
        if (_tasks.value.any { it.trackId == track.id && it.state == State.RUNNING }) return
        upsert(
            Task(
                trackId = track.id,
                title = track.title.ifBlank { "未命名" },
                artist = track.artist,
                state = State.RUNNING,
            ),
        )
        scope.launch {
            val result = runCatching {
                downloader.download(track) { written, total ->
                    update(track.id) { it.copy(bytes = written, total = total ?: it.total) }
                }
            }
            val task = result.fold(
                onSuccess = { path ->
                    Task(
                        trackId = track.id,
                        title = track.title.ifBlank { "未命名" },
                        artist = track.artist,
                        state = State.DONE,
                        savedPath = path,
                    )
                },
                onFailure = { throwable ->
                    Task(
                        trackId = track.id,
                        title = track.title.ifBlank { "未命名" },
                        artist = track.artist,
                        state = State.FAILED,
                        error = throwable.message ?: "下载失败",
                    )
                },
            )
            upsert(task)
            // 结束态停留一会儿再消失：3 秒不够看清「已保存到哪」，失败给 6 秒。
            delay(if (task.state == State.DONE) 4_000L else 6_000L)
            _tasks.value = _tasks.value.filterNot { it.trackId == task.trackId }
        }
    }

    /** 主动收起某条（UI 上的 ✕）。 */
    fun dismiss(trackId: String) {
        _tasks.value = _tasks.value.filterNot { it.trackId == trackId }
    }

    private fun upsert(task: Task) {
        _tasks.value = _tasks.value.filterNot { it.trackId == task.trackId } + task
    }

    private inline fun update(trackId: String, transform: (Task) -> Task) {
        _tasks.value = _tasks.value.map { if (it.trackId == trackId) transform(it) else it }
    }
}
