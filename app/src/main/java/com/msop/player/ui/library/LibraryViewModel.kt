package com.msop.player.ui.library

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msop.player.core.database.dao.FavoriteDao
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.mapper.toTrack
import com.msop.player.core.datastore.ServerConfigStore
import com.msop.player.core.datastore.WebDavConfigStore
import com.msop.player.core.model.SourceType
import com.msop.player.core.model.Track
import com.msop.player.core.model.TrackMerger
import com.msop.player.core.network.DownloadCenter
import com.msop.player.core.network.RemoteFileDownloader
import com.msop.player.feature.local.metadata.LocalMetadataEnricher
import com.msop.player.feature.local.saf.SafDirectoryStore
import com.msop.player.feature.local.saf.rootPaths
import com.msop.player.feature.player.PlaybackController
import com.msop.player.feature.remote.RemoteCoverExtractor
import com.msop.player.feature.player.PlaybackState
import com.msop.player.feature.player.PlayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 曲库与播放的共享状态。
 *
 * 为什么队列放在这里而不是问 [PlaybackController] 要：
 * 控制器里只有 `MediaItem`，而播放页要显示封面、时长、来源，需要完整的 [Track]。
 * 与其把 MediaItem 反解回 Track（信息不全，还得查库），不如在入队时把这份列表记下来。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    trackDao: TrackDao,
    private val playback: PlaybackController,
    private val enricher: LocalMetadataEnricher,
    private val remoteCovers: RemoteCoverExtractor,
    private val favoriteDao: FavoriteDao,
    private val downloader: RemoteFileDownloader,
    private val downloadCenter: DownloadCenter,
    private val serverConfigStore: ServerConfigStore,
    private val webDavConfigStore: WebDavConfigStore,
    private val safDirectories: SafDirectoryStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("msop_ui_prefs", Context.MODE_PRIVATE)

    /**
     * 云端音源「设置里填写的音乐目录」：`openlist:p2` / `webdav:p1` → `rootPath`。
     *
     * 2026-11-16 第十四轮用户反馈「文件夹 云端怎么有那么多路径？只显示设置界面中填写的路径」：
     * 上一轮按 uri 里的子目录归文件夹，OpenList 的 83 首散成十几个专辑目录，看着像一堆噪音。
     * 现在云端一律按配置页填的 `rootPath` 聚合成**一个**文件夹，一个音源一条。
     *
     * 两个 Store 都是 SharedPreferences 实现、没有可观察的 Flow，所以这里存一份快照：
     * 进曲库时 [refreshCloudRoots] 重读一次，改完配置回到音乐库即生效。
     */
    private val _cloudRoots = MutableStateFlow(cloudRootsSnapshot())
    val cloudRoots: StateFlow<Map<String, String>> = _cloudRoots.asStateFlow()

    /**
     * 本机「设置里添加的音乐目录」根路径（长的在前），文件夹 Tab 用它聚合。
     *
     * 2026-11-17（用户报「文件夹 不要有那么多路径，显示设置界面填写的路径就行」）：
     * 之前本机按曲目 `localPath` 的上级目录切，Music/周杰伦、Music/林俊杰 每条一个文件夹。
     * 现在改成「曲目归属哪一个已授权目录」，一个目录一条。
     */
    private val _localRoots = MutableStateFlow(localRootsSnapshot())
    val localRoots: StateFlow<List<String>> = _localRoots.asStateFlow()

    /** 重读配置页里填的音乐目录（云端 rootPath + 本机 SAF 目录）。 */
    fun refreshLibraryRoots() {
        val freshCloud = cloudRootsSnapshot()
        if (freshCloud != _cloudRoots.value) _cloudRoots.value = freshCloud
        val freshNames = cloudNamesSnapshot()
        if (freshNames != _cloudNames.value) _cloudNames.value = freshNames
        val freshLocal = localRootsSnapshot()
        if (freshLocal != _localRoots.value) _localRoots.value = freshLocal
    }

    /** 兼容旧调用点：过去只刷云端。 */
    fun refreshCloudRoots() = refreshLibraryRoots()

    private fun localRootsSnapshot(): List<String> =
        runCatching { safDirectories.rootPaths() }.getOrDefault(emptyList())

    private fun cloudRootsSnapshot(): Map<String, String> = buildMap {
        serverConfigStore.profiles().forEach { put("openlist:${it.id}", it.rootPath) }
        webDavConfigStore.profiles().forEach { put("webdav:${it.id}", it.rootPath) }
    }

    /**
     * 云端音源的**显示名**，键与文件夹聚合用的 key 完全一致（`openlist:/yd/musicweb`）。
     *
     * 2026-11-17 深夜用户指令：「openlist webdav 名称使用设置界面填写的名称」——
     * 文件夹 Tab 原来显示目录末段 / 「根目录」，用户看不出这是哪台服务器。
     * 注意键必须用 `tag + 归一化 rootPath`：文件夹的键里**没有 profile id**
     * （见 `LibraryScreen.folderOf`），按 id 拼出来的键永远对不上。
     */
    private val _cloudNames = MutableStateFlow(cloudNamesSnapshot())
    val cloudNames: StateFlow<Map<String, String>> = _cloudNames.asStateFlow()

    private fun cloudNamesSnapshot(): Map<String, String> = buildMap {
        // 2026-11-17 深夜（续）：「路径支持填写多条（一行一条）」——一个配置可能有多条目录，
        // 每条目录在文件夹 Tab 里都是独立的一条，所以每个 root 都要写一个键（同名也算）。
        serverConfigStore.profiles().forEach { profile ->
            profile.rootPaths.forEach { root ->
                put("openlist:" + normalizeCloudRootPath(root), profile.name)
            }
        }
        webDavConfigStore.profiles().forEach { profile ->
            profile.rootPaths.forEach { root ->
                put("webdav:" + normalizeCloudRootPath(root), profile.name)
            }
        }
    }

    /** 与 `LibraryScreen.normalizeCloudRoot` 同口径：空串 = 整个盘 → `/`。 */
    private fun normalizeCloudRootPath(root: String): String {
        val trimmed = root.trim()
        if (trimmed.isEmpty()) return "/"
        val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withLeading.trimEnd('/').ifEmpty { "/" }
    }

    /** `playTrack` 用的 id→下标索引，按列表引用失效（P5-04）。 */
    private val trackIndex = TrackIndex()

    /** 最近搜索，新的在前。 */
    private val _recentSearches = MutableStateFlow(readList(KEY_RECENT_SEARCHES))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /** 最近播放的 trackId，新的在前。 */
    private val _recentPlayedIds = MutableStateFlow(readList(KEY_RECENT_PLAYED))
    val recentPlayedIds: StateFlow<List<String>> = _recentPlayedIds.asStateFlow()

    /**
     * 收藏的 trackId。
     *
     * 2026-11-16 晚 BUGFIX（用户报「播放界面 爱心按钮点击后 歌曲没有出现在收藏列表中」）：
     * 这里原来读的是 SharedPreferences 里的 `favorites` 集合——那是「最近改版前」的旧实现，
     * 歌单页的「收藏歌曲」虚拟歌单读的是 Room 的 `favorites` 表，两套数据互不相通，
     * 所以在播放页点爱心只会写进一笔谁都不看的 pref，收藏列表永远是空的。
     * 现在统一走 [FavoriteDao]（Room 单表），爱心、三点菜单、收藏歌单三者同源。
     */
    val favorites: StateFlow<Set<String>> = favoriteDao.observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 全量曲库流（P5-04）。
     *
     * 两个关键点，5 万首库才不至于卡：
     * 1. `flowOn(Dispatchers.Default)`——实体到 [Track] 的映射是 5 万次对象构造，
     *    原来跑在 `viewModelScope`（Main.immediate）上，扫描入库时直接压主线程。
     * 2. `conflate()`——扫描是 500 条一批 upsert，每批都会让 Room 重发整表。
     *    合并后只在主线程空闲时交付最新一版，不会排队重映射几十次。
     *
     * 注意：`stateIn` 的 scope 仍是 `viewModelScope`，`flowOn` 只影响上游。
     */
    val tracks: StateFlow<List<Track>> = trackDao.observeAll()
        // 2026-11-16 晚：本地与云端同名合并、本地优先（见 [TrackMerger]）
        .map { entities -> TrackMerger.merge(entities.map { it.toTrack() }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「最近」Tab 数据源（P5-04）：排序下推到 SQLite（`addedAt` 有索引），
     * 且只取前 80 条，5 万首库里不再整表排序 + 整表映射。
     * 来源筛选跟随 [sourceFilterFlow] 由 UI 侧切换到对应流。
     */
    private val sourceFilterFlow = MutableStateFlow(SourceFilter.ALL)

    val recentTracks: StateFlow<List<Track>> = combine(
        sourceFilterFlow,
        trackDao.observeAllByAddedDesc(),
        trackDao.observeBySourceAddedDesc(SourceType.LOCAL),
        trackDao.observeBySourceAddedDesc(SourceType.OPENLIST),
    ) { filter, all, local, cloud ->
        val entities = when (filter) {
            SourceFilter.ALL -> all
            SourceFilter.LOCAL -> local
            SourceFilter.CLOUD -> cloud
        }
        entities.take(RECENT_LIMIT).map { it.toTrack() }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSourceFilter(filter: SourceFilter) {
        sourceFilterFlow.value = filter
    }

    /**
     * 搜索（P5-04）。
     *
     * 原来在 Composable 里 `remember(tracks, query) { tracks.filter { ... } }`，
     * 每敲一个字符就在主线程全表扫一遍。5 万首 × 三个 `contains(ignoreCase)` 是
     * 十几万次不区分大小写的字符串比较，输入框会明显发涩。
     *
     * 现在：查询词进 Flow → 120ms 防抖（打字过程中不扫）→ Default 线程过滤 →
     * 只取前 [SEARCH_LIMIT] 条。UI 只消费结果。
     */
    private val _searchQuery = MutableStateFlow("")

    /** 当前查询词。UI 双向绑定用。 */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Track>> = combine(_searchQuery, tracks) { q, list ->
        q.trim() to list
    }
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { (q, list) ->
            if (q.isEmpty()) {
                emptyList()
            } else {
                val out = ArrayList<Track>(SEARCH_LIMIT)
                for (track in list) {
                    if (track.title.contains(q, ignoreCase = true) ||
                        track.artist?.contains(q, ignoreCase = true) == true ||
                        track.album?.contains(q, ignoreCase = true) == true
                    ) {
                        out.add(track)
                        if (out.size >= SEARCH_LIMIT) break
                    }
                }
                out
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    val sourceFilter: StateFlow<SourceFilter> = sourceFilterFlow.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playback.state

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    /** 播放进度。只在播放页可见时由页面轮询刷新，不在后台空转。 */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    init {
        // 2026-11-15 BUGFIX（用户报「拖动进度条后切下一首，进度条还停在上一首拖到的位置」）：
        // 根因不只在播放页——`positionMs` 原来**只在播放中**由 MsopApp 每秒轮询刷新
        // （`while (isPlaying) refreshPosition()`），暂停状态下切歌就再也不刷新，
        // 进度条于是永远停在上一首 seek 到的位置。这里跟随 `currentTrackId` 变化主动同步一次，
        // 手动切歌 / 播完自动切歌 / 恢复现场三条路径都覆盖到。
        viewModelScope.launch {
            var lastTrackId: String? = null
            playback.state.collect { st ->
                if (st.currentTrackId != lastTrackId) {
                    lastTrackId = st.currentTrackId
                    _positionMs.value = playback.currentPositionMs()
                }
            }
        }

        // 2026-11-14：封面提取接线——[LocalMetadataEnricher.warmCovers] 原设计为
        // 「UI 首屏触发」，但从未接上，导致内置封面从未落盘、列表长期只有程序化
        // 渐变图（本次修复）。进程级幂等：重启后重跑，命中已补齐的直接空转。
        // 注意：init 必须放在全部属性初始化之后（viewModelScope 是 Main.immediate，
        // 构造期内联执行时早于后续属性声明会读到 null，首轮实测就是这崩的）。
        // P5-04：`warmCovers` 逐首读文件（上限 100 首），原来跑在 viewModelScope
        // 即 Main.immediate 上——冷启动首屏那几百毫秒的卡顿就是它。
        // 挪到 IO 线程，并且**不阻塞首屏**：先等列表出来、再等 600ms 让首帧画完。
        viewModelScope.launch(Dispatchers.IO) {
            tracks.first { it.isNotEmpty() }
            delay(600)
            if (coversWarmed.not()) {
                coversWarmed = true
                // 2026-11-16 晚（用户报「封面怎么是白色」）：先修「有 coverUri 但文件
                // 没了」的条目，再预热。重装 / 清缓存之后封面缓存目录是空的，只跑
                // warmCovers 永远修不好这批。
                runCatching { enricher.repairBrokenCovers() }
                runCatching { enricher.warmCovers() }
                // 2026-11-17 用户报云端歌曲内嵌封面不显示：本地预热完再补云端。
                // 并发上限 4 在 RemoteCoverExtractor 内部用 Semaphore 控制，
                // 不阻塞首屏（这段本来就在 Dispatchers.IO 上、延后 600ms）。
                runCatching { remoteCovers.extract() }
            }
        }
    }

    /**
     * 播放一个列表，从第 [index] 首开始。
     *
     * 点歌行为按国内播放器惯例：**整个当前列表入队**，而不是只播这一首。
     */
    fun play(list: List<Track>, index: Int) {
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(0, list.lastIndex)
        _queue.value = list
        recordPlayed(list[safeIndex].id)
        viewModelScope.launch {
            playback.playQueue(list, safeIndex)
            _positionMs.value = 0L
        }
    }

    /**
     * 在当前曲库列表里找到这首歌并播放。
     *
     * P5-04：`indexOfFirst` 在 5 万首上是 5 万次字符串比较，点一下歌就掉帧。
     * 改成先用 id 建索引（一次 O(n)），后续点击 O(1)。索引按列表引用缓存，
     * 列表换了（扫描入库 / 筛选变化）才重建。
     */
    fun playTrack(track: Track, from: List<Track> = tracks.value) {
        val index = indexOf(from, track.id)
        play(from, if (index >= 0) index else 0)
    }

    /** 按 id 取列表下标，命中缓存时 O(1)。 */
    fun indexOf(list: List<Track>, id: String): Int = trackIndex.indexOf(list, id)

    fun toggle() = viewModelScope.launch { playback.toggle() }

    // 2026-11-15：切歌时先把进度归零，进度条不会闪一下上一首的位置
    // （init 里的 state 收集是兜底，这里保证点击瞬间就归位）。
    fun next() = viewModelScope.launch {
        _positionMs.value = 0L
        playback.next()
    }

    fun previous() = viewModelScope.launch {
        _positionMs.value = 0L
        playback.previous()
    }

    fun seekTo(positionMs: Long) {
        _positionMs.value = positionMs
        viewModelScope.launch { playback.seekTo(positionMs) }
    }

    /**
     * 冷启动恢复上次的播放现场（P7-02）。
     * 只把队列与进度填回来，**不自动播放**；用户按播放键才出声。
     */
    fun restoreSession() = viewModelScope.launch {
        val restored = playback.restoreLastSession() ?: return@launch
        _queue.value = restored.tracks
        _positionMs.value = restored.positionMs
    }

    fun cycleMode() = viewModelScope.launch { playback.cycleMode() }

    /** 2026-11-15：播放页左下角扇形菜单直接选模式（不再靠轮转）。 */
    fun setPlayMode(mode: PlayMode) = viewModelScope.launch { playback.setMode(mode) }

    fun moveInQueue(from: Int, to: Int) = viewModelScope.launch {
        playback.moveInQueue(from, to)
        _queue.value = _queue.value.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** 播放页每 500ms 调一次。页面不可见时不要调。 */
    fun refreshPosition() = viewModelScope.launch {
        _positionMs.value = playback.currentPositionMs()
    }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = (listOf(q) + _recentSearches.value.filter { it != q }).take(12)
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("\n")).apply()
    }

    fun clearSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    /**
     * 收藏 / 取消收藏（Room 单表，和歌单页「收藏歌曲」同源）。
     *
     * 用 [FavoriteDao.toggle] 而不是先读后写：点得快的时候两次点击不会互相覆盖。
     */
    fun toggleFavorite(id: String) = viewModelScope.launch {
        favoriteDao.toggle(id, System.currentTimeMillis())
    }

    /**
     * 下载一首云端曲目（2026-11-16 晚用户需求）：直链解析 + 落盘在 IO 线程。
     *
     * 2026-11-16 晚追加（用户报「点击下载 反馈没有 进度也没有」）：交给 [DownloadCenter]
     * 统一管理——点下去立刻出现进度卡片，完成/失败都在卡片里显示，不再只靠结束后的 toast。
     */
    fun download(track: Track) = downloadCenter.start(track)

    /** 在途/刚结束的下载任务，全 App 共用一份（下载中心）。 */
    val downloads: StateFlow<List<DownloadCenter.Task>> = downloadCenter.tasks

    fun dismissDownload(trackId: String) = downloadCenter.dismiss(trackId)

    private fun recordPlayed(id: String) {
        val next = (listOf(id) + _recentPlayedIds.value.filter { it != id }).take(30)
        _recentPlayedIds.value = next
        prefs.edit().putString(KEY_RECENT_PLAYED, next.joinToString("\n")).apply()
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun readSet(key: String): Set<String> =
        prefs.getStringSet(key, null) ?: emptySet()

    private companion object {
        /** 「最近」Tab 最多展示的条数（与旧 UI 行为一致）。 */
        const val RECENT_LIMIT = 80

        /** 搜索结果上限（与旧 UI 行为一致）。 */
        const val SEARCH_LIMIT = 80

        /** 搜索防抖窗口。打字时不必每键都扫全表。 */
        const val SEARCH_DEBOUNCE_MS = 120L

        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_RECENT_PLAYED = "recent_played"
        const val KEY_FAVORITES = "favorites"

        /** 封面预热进程级标记，避免多 VM 实例重复跑。 */
        @Volatile
        private var coversWarmed = false
    }
}

/** 曲库来源筛选（P5-04 从 UI 层上移，供 DAO 流切换用）。 */
enum class SourceFilter { ALL, LOCAL, CLOUD }

