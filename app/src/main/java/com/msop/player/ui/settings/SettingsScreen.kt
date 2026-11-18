package com.msop.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.msop.player.feature.player.cache.MediaCache
import com.msop.player.ui.components.EnteringPage
import com.msop.player.ui.components.GlassPill
import com.msop.player.ui.components.HazeTitleHeight
import com.msop.player.ui.components.HazeTitleScaffold
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.iosStackSpec
import com.msop.player.ui.components.IconTile
import com.msop.player.ui.components.InsetDivider
import com.msop.player.ui.components.PrimaryPill
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.icons.PlayerIcons
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Gradients
import com.msop.player.ui.theme.ThemeMode
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置。只放**已经能用**的入口，不放点了没反应的假开关。
 * 视觉上按「卡片分组」重排，每组一张玻璃卡，行内分隔线从图标右侧开始。
 */
@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(
    directories: List<String>,
    onAddDirectory: () -> Unit,
    onRemoveDirectory: (String) -> Unit,
    onRescan: () -> Unit,
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeChange: (ThemeMode) -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
    openListVm: OpenListViewModel = hiltViewModel(),
    webDavVm: WebDavViewModel = hiltViewModel(),
    desktopLyricsVm: DesktopLyricsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val cache by vm.cache.collectAsStateWithLifecycle()
    val equalizer by vm.equalizerState.collectAsStateWithLifecycle()
    val openList by openListVm.state.collectAsStateWithLifecycle()
    val webDav by webDavVm.state.collectAsStateWithLifecycle()
    val desktopLyrics by desktopLyricsVm.state.collectAsStateWithLifecycle()
    val backupMessage by vm.backupMessage.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearMedia by remember { mutableStateOf(false) }
    var equalizerOpen by remember { mutableStateOf(false) }
    var openListOpen by remember { mutableStateOf(false) }
    var webDavOpen by remember { mutableStateOf(false) }
    var desktopLyricsOpen by remember { mutableStateOf(false) }
    var directoryManagerOpen by remember { mutableStateOf(false) }
    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }

    // 2026-11-15 第九轮：重新扫描曲库的反馈——点过之后才把 Worker 的结果显示出来，
    // 免得冷启动时把「上次扫描」的旧结果当成刚点的那次。
    val scan by vm.scanState.collectAsStateWithLifecycle()
    var rescanRequested by remember { mutableStateOf(false) }
    var scanResultVisible by remember { mutableStateOf(false) }
    LaunchedEffect(scan.finished, rescanRequested) {
        if (scan.finished && rescanRequested) {
            scanResultVisible = true
            delay(8_000)
            scanResultVisible = false
        }
    }

    // 从系统设置页返回时重新判定；仅靠 LaunchedEffect(Unit) 状态不会刷新
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { batteryExempt = BatteryOptimization.isExempt(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = BatteryOptimization.isExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.refreshCache() }

    // P7-04：SAF 导出/导入。导出用 CreateDocument 让用户选位置，导入用 OpenDocument。
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importBackup(uri) }

    // 2026-11-16 晚：自定义下载目录（OpenDocumentTree，权限持久化给下载器复用）
    val downloadVersion by vm.downloadPathVersion.collectAsStateWithLifecycle()
    val downloadLabel = remember(downloadVersion) { vm.downloadPathLabel() }
    val hasCustomDownloadTree = remember(downloadVersion) { vm.isDownloadTreeCustom() }
    val downloadTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setDownloadTree(uri, uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() })
            Toast.makeText(context, "下载目录已更新", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // P7-11：设置子页 iOS 式转场——主内容压缩渐暗，子页右滑入。
        val subPage = when {
            equalizerOpen -> SettingsSubPage.Equalizer
            openListOpen -> SettingsSubPage.OpenList
            webDavOpen -> SettingsSubPage.WebDav
            desktopLyricsOpen -> SettingsSubPage.DesktopLyrics
            else -> null
        }
        val bgScale by animateFloatAsState(
            targetValue = if (subPage != null) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            label = "setBgScale",
        )
        val bgAlpha by animateFloatAsState(
            targetValue = if (subPage != null) 0.55f else 1f,
            animationSpec = tween(180),
            label = "setBgDim",
        )

        // P7-09：标题栏浮在滚动内容之上，内容首项用 Spacer 让开，滚动时会经过标题栏后方。
        // P7-11：modifier 上的压缩/渐暗在子页打开时生效（背景压缩）。
        HazeTitleScaffold(
            modifier = Modifier.graphicsLayer {
                scaleX = bgScale
                scaleY = bgScale
                alpha = bgAlpha
            },
            title = "设置", contentTopInset = HazeTitleHeight) { sourceModifier, topInset ->
        Column(sourceModifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(topInset))

            AppHero(versionName)

            // 2026-11-14：外观两档——深色 / 浅色（默认深色），改完立即全局生效。
            SettingsGroup("外观") {
                ThemeModeRow(selected = themeMode, onPick = onThemeChange)
            }

            SettingsGroup("音乐来源") {
                SettingRow(
                    icon = PlayerIcons.Folder,
                    tint = Palette.BlueLight,
                    title = "本机目录",
                    subtitle = "MediaStore 标准目录 + ${directories.size} 个自定义目录",
                    value = "管理",
                    onClick = { directoryManagerOpen = true },
                )
                InsetDivider()
                SettingRow(
                    // 2026-11-18 用户指令：「openlist 服务器图标改为 openlist 图标」
                    icon = PlayerIcons.OpenList,
                    tint = Palette.Teal,
                    title = "OpenList 服务器",
                    subtitle = if (openList.configured) {
                        val enabledCount = openList.profiles.count { it.enabled }
                        "${openList.profiles.size} 台已保存 · $enabledCount 台启用中 · ${openList.syncedCount} 首远程歌曲"
                    } else {
                        "填地址、账号，测试连通后同步远程曲库"
                    },
                    value = if (openList.configured) "已连接" else "未配置",
                    valueColor = if (openList.configured) Palette.Green else Palette.TextTertiary,
                    onClick = { openListOpen = true },
                )
                InsetDivider()
                SettingRow(
                    icon = PlayerIcons.Cloud,
                    tint = Palette.Gold,
                    title = "WebDAV 服务器",
                    subtitle = if (webDav.configured) {
                        val enabledCount = webDav.profiles.count { it.enabled }
                        "${webDav.profiles.size} 台已保存 · $enabledCount 台启用中 · ${webDav.syncedCount} 首 WebDAV 歌曲"
                    } else {
                        "独立第三音源，填地址与音乐目录后同步"
                    },
                    value = if (webDav.configured) "已连接" else "未配置",
                    valueColor = if (webDav.configured) Palette.Green else Palette.TextTertiary,
                    onClick = { webDavOpen = true },
                )
                InsetDivider()
                // 2026-11-15 第九轮（用户指令：「重新扫描曲库增加反馈」）：
                // 点下去先弹 toast，行内按 Worker 的 progress/outputData 实时显示进度与结果。
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    tint = Palette.Gold,
                    title = "重新扫描曲库",
                    subtitle = when {
                        scan.running ->
                            "正在扫描… 已扫到 ${scan.scanned} 首（写入 ${scan.written} 条）"
                        scan.failed -> "上次扫描失败，可以再点一次重试"
                        scanResultVisible && scan.libraryTotal > 0 ->
                            // 2026-11-18（用户报「重扫曲库只显示本机歌曲数量」）：扫的是本机，
                            // 但结果里要带上曲库总数（本机 + 云端），否则看起来像云端全丢了。
                            "扫描完成 · 本机 ${scan.scanned} 首 · 曲库共 ${scan.libraryTotal} 首" +
                                " · 用时 ${"%.1f".format(scan.elapsedMs / 1000f)} 秒"
                        scanResultVisible ->
                            "扫描完成 · 共 ${scan.scanned} 首 · 用时 ${"%.1f".format(scan.elapsedMs / 1000f)} 秒"
                        else -> "全量重扫会重新读取所有文件的标签与封面"
                    },
                    value = when {
                        scan.running -> "扫描中"
                        scan.failed -> "失败"
                        scanResultVisible -> "完成"
                        else -> null
                    },
                    valueColor = when {
                        scan.running -> Palette.BlueLight
                        scan.failed -> Palette.Red
                        else -> Palette.Green
                    },
                    onClick = {
                        onRescan()
                        rescanRequested = true
                        Toast.makeText(context, "已开始重新扫描曲库…", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            SettingsGroup("播放") {
                SettingRow(
                    icon = PlayerIcons.Disc,
                    tint = Palette.BlueLight,
                    title = "均衡器",
                    subtitle = "${equalizer.bands.size} 段 · 预设与自定义曲线",
                    value = if (equalizer.enabled) "已启用" else "未启用",
                    valueColor = if (equalizer.enabled) Palette.BlueLight else Palette.TextTertiary,
                    onClick = { equalizerOpen = true },
                )
                InsetDivider()
                SettingRow(
                    icon = PlayerIcons.Lyrics,
                    tint = Palette.Teal,
                    title = "歌词",
                    subtitle = "同名 .lrc 优先，内嵌歌词兜底",
                    value = "已启用",
                    valueColor = Palette.Green,
                )
                InsetDivider()
                // 2026-11-16 晚用户需求：「设置界面增加自定义下载路径」。
                // 云端歌曲三点菜单里的「下载歌曲」落到这里；不选就走应用私有 Music 目录。
                SettingRow(
                    icon = PlayerIcons.Download,
                    tint = Palette.BlueLight,
                    title = "下载路径",
                    subtitle = downloadLabel,
                    value = if (hasCustomDownloadTree) "更换目录" else "选择目录",
                    valueColor = Palette.BlueLight,
                    onClick = { downloadTreeLauncher.launch(null) },
                )
                // 2026-11-16 晚用户指令「下载路径新加的提示词删掉」：那两段常驻说明撤掉。

                if (hasCustomDownloadTree) {
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Filled.Refresh,
                        tint = Palette.TextSecondary,
                        title = "恢复默认下载目录",
                        subtitle = "改回应用私有 Music 目录",
                        value = "重置",
                        onClick = { vm.clearDownloadTree() },
                    )
                }
            }

            SettingsGroup("桌面歌词") {
                SettingRow(
                    // 2026-11-17 深夜（续）用户纠正：「桌面悬浮歌词」这颗**保持新形态**
                    // （与歌词页信息行的悬浮钮一致），只有「播放」组里的「歌词」和播放页 dock
                    // 那颗才改回原始四条横线。
                    icon = PlayerIcons.LyricsFilled,
                    tint = Palette.BlueLight,
                    title = "桌面悬浮歌词",
                    subtitle = "在桌面显示当前歌词，可拖动、锁定与调样式",
                    value = if (desktopLyrics.enabled) "已开启" else "未开启",
                    valueColor = if (desktopLyrics.enabled) Palette.Green else Palette.TextTertiary,
                    onClick = { desktopLyricsOpen = true },
                )
            }

            SettingsGroup("后台播放") {
                SettingRow(
                    // 2026-11-18 用户指令：「电池优化白名单图标改为电池图标」
                    icon = PlayerIcons.Battery,
                    tint = Palette.Gold,
                    title = "电池优化白名单",
                    subtitle = if (batteryExempt) {
                        "已豁免，息屏后台播放不会被系统冻结"
                    } else {
                        "未豁免，国产 ROM 可能息屏后杀掉播放"
                    },
                    value = if (batteryExempt) "已加入" else "去设置",
                    valueColor = if (batteryExempt) Palette.Green else Palette.BlueLight,
                    onClick = {
                        val request = BatteryOptimization.requestIgnoreIntent(context)
                        val target = request ?: BatteryOptimization.appDetailsIntent(context)
                        runCatching { settingsLauncher.launch(target) }
                            .onFailure {
                                runCatching {
                                    settingsLauncher.launch(BatteryOptimization.batterySettingsIntent())
                                }
                            }
                    },
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Filled.Settings,
                    tint = Palette.TextSecondary,
                    title = "系统应用设置",
                    subtitle = "自启动、后台运行等厂商开关在这里手动开启",
                    onClick = {
                        runCatching {
                            settingsLauncher.launch(BatteryOptimization.appDetailsIntent(context))
                        }
                    },
                )
            }

            SettingsGroup("存储") {
                SettingRow(
                    icon = PlayerIcons.Disc,
                    tint = Palette.Teal,
                    title = "封面缓存",
                    subtitle = "按内容哈希落盘；清理后播放时自动重建",
                    value = if (cache.loading) {
                        "统计中…"
                    } else {
                        "${cache.count} 张 · ${formatBytes(cache.bytes)}"
                    },
                    onClick = { confirmClear = true },
                )
                InsetDivider()
                SettingRow(
                    // 2026-11-18 用户指令：「远程播放缓存图标改为硬盘图标」
                    icon = PlayerIcons.HardDrive,
                    tint = Palette.BlueLight,
                    title = "远程播放缓存",
                    subtitle = if (cache.loading) {
                        "边播边缓存 · 上限 ${formatBytes(MediaCache.MAX_BYTES)}"
                    } else {
                        "边播边缓存 · ${cache.mediaCount} 首 · 上限 ${formatBytes(MediaCache.MAX_BYTES)}"
                    },
                    value = if (cache.loading) "统计中…" else formatBytes(cache.mediaBytes),
                    onClick = { confirmClearMedia = true },
                )
                InsetDivider()
                SettingSwitchRow(
                    // 2026-11-18 用户指令：「仅 wifi 缓存图标改为 wifi 图标」
                    icon = PlayerIcons.Wifi,
                    tint = Palette.Gold,
                    title = "仅 Wi-Fi 缓存",
                    subtitle = if (cache.wifiOnly) {
                        "蜂窝下不写入缓存，已缓存内容仍可播放"
                    } else {
                        "任何网络都写入缓存"
                    },
                    checked = cache.wifiOnly,
                    onCheckedChange = vm::setWifiOnlyCaching,
                )
            }

            SettingsGroup("备份与恢复") {
                SettingRow(
                    icon = Icons.Filled.Share,
                    tint = Palette.Teal,
                    title = "导出备份",
                    subtitle = "包含所有数据（含服务器密码），导入后直接可用",
                    value = "导出",
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(Date())
                        exportBackupLauncher.launch("msop-backup-$stamp.zip")
                    },
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    tint = Palette.BlueLight,
                    title = "导入备份",
                    subtitle = "同名歌单合并；曲库中不存在的曲目自动跳过；服务器配置直接生效",
                    value = "选择文件",
                    onClick = {
                        importBackupLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                        )
                    },
                )
            }

            SettingsGroup("关于") {
                SettingRow(
                    // 2026-11-18 用户指令：「版本图标改成 i 图标」
                    icon = PlayerIcons.InfoCircle,
                    tint = Palette.TextSecondary,
                    title = "版本",
                    value = versionName,
                )
                InsetDivider()
                SettingRow(
                    icon = PlayerIcons.Lyrics,
                    tint = Palette.TextSecondary,
                    title = "声明",
                    subtitle = "不修改、不上传任何数据",
                )
            }

            Spacer(Modifier.height(LocalHazeBottomInset.current + 30.dp))
        } // Column
        } // HazeTitleScaffold content

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Palette.Surface2,
                shape = Shapes.card,
                titleContentColor = Palette.TextPrimary,
                textContentColor = Palette.TextSecondary,
                title = { Text("清理封面缓存？") },
                text = {
                    Text(
                        "将删除 ${cache.count} 张缓存封面（${formatBytes(cache.bytes)}）。" +
                            "不会删除音频文件，清理后立刻按文件内嵌封面重建；" +
                            "云端歌曲需要重新探测，大约一两分钟陆续回来。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClear = false
                            vm.clearCache()
                        },
                    ) {
                        Text("清理", color = Palette.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text("取消", color = Palette.TextSecondary)
                    }
                },
            )
        }

        if (confirmClearMedia) {
            AlertDialog(
                onDismissRequest = { confirmClearMedia = false },
                containerColor = Palette.Surface2,
                shape = Shapes.card,
                titleContentColor = Palette.TextPrimary,
                textContentColor = Palette.TextSecondary,
                title = { Text("清理远程播放缓存？") },
                text = {
                    Text(
                        "将删除 ${cache.mediaCount} 首远程曲目的缓存" +
                            "（${formatBytes(cache.mediaBytes)}）。不会删除服务器上的音频文件；" +
                            "正在播放的远程曲目可能需要重新缓冲。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClearMedia = false
                            vm.clearMediaCache()
                        },
                    ) {
                        Text("清理", color = Palette.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearMedia = false }) {
                        Text("取消", color = Palette.TextSecondary)
                    }
                },
            )
        }

        backupMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { vm.clearBackupMessage() },
                containerColor = Palette.Surface2,
                shape = Shapes.card,
                titleContentColor = Palette.TextPrimary,
                textContentColor = Palette.TextSecondary,
                title = { Text("备份与恢复") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { vm.clearBackupMessage() }) {
                        Text("知道了", color = Palette.BlueLight)
                    }
                },
            )
        }

        if (directoryManagerOpen) {
            DirectoryManagerDialog(
                directories = directories,
                onDismiss = { directoryManagerOpen = false },
                onAdd = {
                    directoryManagerOpen = false
                    onAddDirectory()
                },
                onRemove = onRemoveDirectory,
            )
        }

        // P7-11：四个子页统一 iOS 式转场 overlay——右滑入 + 缩放 + 圆角收拢；返回反向滑出。
        // 空占位层不拦截触摸（无 pointerInput），主内容点击不受影响。
        AnimatedContent(
            targetState = subPage,
            transitionSpec = { iosStackSpec(targetState != null) },
            label = "settingsSub",
        ) { page ->
            if (page != null) {
                EnteringPage {
                    when (page) {
                        SettingsSubPage.Equalizer -> EqualizerScreen(
                            state = equalizer,
                            onEnabledChange = vm::setEqualizerEnabled,
                            onPreset = vm::applyEqualizerPreset,
                            onBandChange = vm::setEqualizerBand,
                            onReset = vm::resetEqualizer,
                            onBack = { equalizerOpen = false },
                        )

                        SettingsSubPage.OpenList -> OpenListConfigScreen(
                            vm = openListVm,
                            onBack = { openListOpen = false },
                        )

                        SettingsSubPage.WebDav -> WebDavConfigScreen(
                            vm = webDavVm,
                            onBack = { webDavOpen = false },
                        )

                        SettingsSubPage.DesktopLyrics -> DesktopLyricsScreen(
                            vm = desktopLyricsVm,
                            onBack = { desktopLyricsOpen = false },
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

/** P7-11：设置页四个子页的身份，用作 AnimatedContent 的目标状态。 */
private enum class SettingsSubPage { Equalizer, OpenList, WebDav, DesktopLyrics }

/**
 * 外观两档分段选择（2026-11-14）：深色 / 浅色，默认深色。
 * 选中段用极光渐变胶囊，风格与分类 Tab 一致。
 */
@Composable
private fun ThemeModeRow(selected: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.DARK to "深色",
        ThemeMode.LIGHT to "浅色",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(40.dp)
            .clip(Shapes.pill)
            .background(Palette.Surface2.copy(alpha = 0.75f)),
    ) {
        options.forEach { (mode, label) ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Shapes.pill)
                    .then(
                        if (active) {
                            Modifier.background(Gradients.accent)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color.White else Palette.TextSecondary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AppHero(versionName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.screen, vertical = 10.dp)
            .height(88.dp)
            .glassSurface(Shapes.card, Palette.Surface2, elevation = 14.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Gradients.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PlayerIcons.Disc,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "msop",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                GlassPill(
                    label = "v$versionName",
                    color = Palette.TextSecondary,
                    background = Color.White.copy(alpha = 0.06f),
                )
            }
            Text(
                text = "本机 + OpenList + WebDAV",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Palette.Green),
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 26.dp, top = 20.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen)
                .glassSurface(Shapes.card, Palette.Surface.copy(alpha = 0.78f), elevation = 8.dp),
        ) {
            content()
        }
    }
}

/** 列出 / 移除已经通过 SAF 授权的自定义目录。移除后由 MainActivity 触发全量重扫。 */
@Composable
private fun DirectoryManagerDialog(
    directories: List<String>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        shape = Shapes.card,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextSecondary,
        title = { Text("本机目录") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "MediaStore 标准目录始终包含在内。下面是额外添加的目录，移除后会重新全量扫描。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(10.dp))
                if (directories.isEmpty()) {
                    Text(
                        "还没有自定义目录",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    directories.forEach { treeUri ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(
                                icon = PlayerIcons.Folder,
                                tint = Palette.BlueLight,
                                size = 32.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = formatTreeUri(treeUri),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemove(treeUri) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "移除目录 ${formatTreeUri(treeUri)}",
                                    tint = Palette.Red,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryPill(label = "添加目录", onClick = onAdd, icon = Icons.Filled.Add)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Palette.TextSecondary)
            }
        },
    )
}

/** tree URI 形如 content://.../tree/primary%3AMusic，取出 documentId 里可读的路径部分。 */
private fun formatTreeUri(treeUri: String): String = runCatching {
    val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
    val path = documentId.substringAfter(':', documentId).trim('/')
    path.ifBlank { "外部存储根目录" }
}.getOrDefault(treeUri)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** 与 [SettingRow] 同一套视觉，右侧换成开关。整行也可点，方便单手操作。 */
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tint = tint, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                // 同 SettingRow：窄屏长提示词不要被一行省略号切掉
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 设置项下方的常驻说明段（2026-11-16 晚「目录选择提示词」用）。
 *
 * 和 `SettingRow.subtitle` 的区别：副标题只有一行、说「是什么」；这里是整段、
 * 说「点了会发生什么」，长句按 3 行以内换行，不与右侧按钮抢宽度。
 */
@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp + 40.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Palette.TextTertiary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tint = tint, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                // 2026-11-16 用户报「长提示词在窄屏上尾部被省略」：原来这一行固定 66dp 高、
                // 副标题 maxLines=1，长文案直接被省略号切掉。改成行高自适应 + 副标题最多三行，
                // 窄屏（450dp）也能完整读到提示词。
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
                fontWeight = FontWeight.Medium,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Palette.TextTertiary.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
