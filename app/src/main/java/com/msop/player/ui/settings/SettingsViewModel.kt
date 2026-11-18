package com.msop.player.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.msop.player.backup.BackupManager
import com.msop.player.backup.displayMessage
import android.content.Context
import com.msop.player.core.datastore.CachePreferences
import com.msop.player.core.datastore.DownloadConfigStore
import com.msop.player.feature.local.metadata.CoverCache
import com.msop.player.feature.local.metadata.LocalMetadataEnricher
import com.msop.player.feature.local.work.LocalScanScheduler
import com.msop.player.feature.local.work.LocalScanWorker
import com.msop.player.feature.player.cache.MediaCache
import com.msop.player.feature.player.equalizer.EqualizerController
import com.msop.player.feature.player.equalizer.EqualizerTuning
import com.msop.player.feature.remote.RemoteCoverExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 设置页的状态：封面缓存、远程播放缓存、仅 Wi-Fi 缓存开关、均衡器、后台保活。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val coverCache: CoverCache,
    private val enricher: LocalMetadataEnricher,
    private val remoteCovers: RemoteCoverExtractor,
    private val equalizer: EqualizerController,
    private val mediaCache: MediaCache,
    private val cachePreferences: CachePreferences,
    private val backupManager: BackupManager,
    private val scanScheduler: LocalScanScheduler,
    private val downloadConfig: DownloadConfigStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** 与 PlaybackService 共用同一个单例，服务 attach 后段数会自动刷新。 */
    val equalizerState: StateFlow<com.msop.player.feature.player.equalizer.EqualizerState> =
        equalizer.state

    fun setEqualizerEnabled(enabled: Boolean) = equalizer.setEnabled(enabled)
    fun applyEqualizerPreset(preset: EqualizerTuning.Preset) = equalizer.applyPreset(preset)
    fun setEqualizerBand(index: Int, levelMb: Int) = equalizer.setBandLevel(index, levelMb)
    fun resetEqualizer() = equalizer.reset()

    data class CacheState(
        /** 封面缓存：张数、字节。 */
        val count: Int = 0,
        val bytes: Long = 0L,
        /** 远程音频缓存：资源数、字节。 */
        val mediaCount: Int = 0,
        val mediaBytes: Long = 0L,
        /** 「仅 Wi-Fi 缓存」开关。 */
        val wifiOnly: Boolean = true,
        val loading: Boolean = true,
    )


    // ---------------------------------------------------------------- 下载路径（2026-11-16 晚）

    /**
     * 自定义下载目录：`treeUri` 空 → 用应用私有 `Android/data/.../files/Music`。
     *  `<data>/files/Music` 不需要任何权限，卸载即清；用户选了 SAF 目录才写到那里。
     *
     * 2026-11-17（用户需求「设置界面 下载路径下方提示词改为显示当前路径」）：
     * 副标题原来只是目录名（`Music` / `Download`）或干脆一句「已选目录」，
     * 看不出文件究竟落在哪。现在直接把**当前路径**拼出来：
     * - 默认：`内部存储/Android/data/com.msop.player/files/Music`
     * - SAF：把 `content://…/tree/primary%3AMusic%2FSub` 解成 `内部存储/Music/Sub`
     *   （拿不到卷名时才回落到之前存的显示名，再不行才给「已选目录」）
     */
    fun downloadPathLabel(): String {
        val tree = downloadConfig.treeUri()
        if (tree != null) {
            readableTreePath(tree)?.let { return it }
            downloadConfig.displayName()?.let { return it }
            return "已选目录"
        }
        return "内部存储/Android/data/${context.packageName}/files/" + downloadConfig.defaultDirName
    }

    /** SAF tree URI → 人类可读路径（`内部存储/Music/Sub`）；解析不出来返回 null。 */
    private fun readableTreePath(treeUri: String): String? {
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
        val documentId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull() ?: return null
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1).trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) "内部存储" else "存储卡 $volume"
        return if (relative.isBlank()) root else "$root/$relative"
    }

    fun setDownloadTree(uri: Uri, displayName: String?) {
        downloadConfig.saveTree(uri.toString(), displayName)
        downloadPathVersion.value++
    }

    /** 当前是否用的是用户自选的 SAF 目录（设置页据此显示「重置」）。 */
    fun isDownloadTreeCustom(): Boolean = downloadConfig.treeUri() != null

    fun clearDownloadTree() {
        downloadConfig.clearTree()
        downloadPathVersion.value++
    }

    /** 设置页那一行要随目录变化重组，用一个自增版本号触发。 */
    val downloadPathVersion: MutableStateFlow<Int> = MutableStateFlow(0)
    private val _cache = MutableStateFlow(CacheState())
    val cache: StateFlow<CacheState> = _cache.asStateFlow()

    fun refreshCache() {
        viewModelScope.launch {
            _cache.value = _cache.value.copy(loading = true)
            loadCache(loading = false)
        }
    }

    /**
     * 清封面缓存（2026-11-18 用户报「清缓存后封面不显示，点播放也不显示，要重扫+重启才回来」）。
     *
     * 原来只删文件：数据库里的 `coverUri` 仍然指向已删除的路径，列表和播放页所有封面
     * 立刻退回占位图；重建流程只在冷启动时跑一次，所以只能靠重启恢复。
     * 现在删完就地重建——本机按内嵌图重新提取，云端清掉探测失败记录后重探一轮。
     */
    fun clearCache() {
        // 2026-11-18 修复：删除、重建、统计必须串行。原来两个协程并行——
        // coverCache.clear() 会拍快照后删文件，而另一路可能已经把重建的封面写盘并
        // 回写 coverUri，结果又变成「URI 在、文件不在」，正是这里想修的问题。
        viewModelScope.launch(Dispatchers.IO) {
            _cache.value = _cache.value.copy(loading = true)
            coverCache.clear()
            runCatching { remoteCovers.clearProbeHistory() }
            runCatching { enricher.rebuildCovers() }
            repeat(2) { round ->
                if (round > 0) runCatching { remoteCovers.clearProbeHistory() }
                val stats = runCatching { remoteCovers.extract() }.getOrNull() ?: return@repeat
                if (stats.failed == 0 || stats.candidates == 0) return@repeat
            }
            loadCache(loading = false)
        }
    }

    fun clearMediaCache() {
        viewModelScope.launch {
            _cache.value = _cache.value.copy(loading = true)
            mediaCache.clear()
            loadCache(loading = false)
        }
    }

    /**
     * 只改偏好，不通知播放层：MediaCachePolicy 在每次创建 DataSource 时重新读一次，
     * 所以下一次切歌 / seek 就按新开关决策，正在播的曲目不受影响。
     */
    fun setWifiOnlyCaching(enabled: Boolean) {
        cachePreferences.setWifiOnlyCaching(enabled)
        _cache.value = _cache.value.copy(wifiOnly = enabled)
    }

    // P7-04：备份/恢复结果用弹窗展示，成功和失败走同一条通道。
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = backupManager.export(uri).displayMessage()
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = backupManager.import(uri).displayMessage()
            // 歌单/缓存/均衡器都可能变了，重新统计一次
            loadCache(loading = false)
        }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    private suspend fun loadCache(loading: Boolean) = withContext(Dispatchers.IO) {
        val count = coverCache.count()
        val bytes = coverCache.sizeBytes()
        val mediaCount = mediaCache.resourceCount()
        val mediaBytes = mediaCache.sizeBytes()
        _cache.value = _cache.value.copy(
            count = count,
            bytes = bytes,
            mediaCount = mediaCount,
            mediaBytes = mediaBytes,
            wifiOnly = cachePreferences.wifiOnlyCaching,
            loading = loading,
        )
    }

    /**
     * 「重新扫描曲库」的实时状态（2026-11-15 第九轮用户指令：点了要有反馈）。
     *
     * 直接观察 [LocalScanScheduler] 那个唯一任务：Worker 会 `setProgress(scanned, written)`
     * 并在结束时把 `scanned / written / elapsedMs` 放进 outputData，
     * 所以设置页能显示「正在扫描… 已扫到 N 首」和「扫描完成 · 共 N 首 · 用时 Xs」。
     */
    data class ScanState(
        val running: Boolean = false,
        val scanned: Int = 0,
        val written: Int = 0,
        val finished: Boolean = false,
        val elapsedMs: Long = 0L,
        val failed: Boolean = false,
        /** 2026-11-18（用户报「重扫曲库只显示本机歌曲数量」）：扫描结束后曲库总条数（含云端）。 */
        val libraryTotal: Int = 0,
    )

    val scanState: StateFlow<ScanState> = scanScheduler.observe()
        .map { info ->
            when (info?.state) {
                WorkInfo.State.RUNNING -> ScanState(
                    running = true,
                    scanned = info.progress.getInt(LocalScanWorker.KEY_SCANNED, 0),
                    written = info.progress.getInt(LocalScanWorker.KEY_WRITTEN, 0),
                )
                WorkInfo.State.SUCCEEDED -> ScanState(
                    finished = true,
                    scanned = info.outputData.getInt(LocalScanWorker.KEY_SCANNED, 0),
                    written = info.outputData.getInt(LocalScanWorker.KEY_WRITTEN, 0),
                    elapsedMs = info.outputData.getLong(LocalScanWorker.KEY_ELAPSED_MS, 0L),
                    // 扫描只覆盖本机（MediaStore），这里补上曲库总数，
                    // 免得「共 N 首」看起来像云端歌曲全没了。
                    // 2026-11-18：Worker 已在后台线程算好并写进 outputData，
                    // 这里只读整数，不再在主线程把全库映射成 Track 做去重。
                    libraryTotal = info.outputData.getInt(LocalScanWorker.KEY_LIBRARY_TOTAL, 0),
                )
                WorkInfo.State.FAILED -> ScanState(failed = true)
                else -> ScanState()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState())
}
