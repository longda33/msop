package com.msop.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.msop.player.core.datastore.ThemePreferences
import com.msop.player.feature.local.saf.SafDirectoryStore
import com.msop.player.feature.local.work.LocalScanScheduler
import com.msop.player.ui.MsopApp
import com.msop.player.ui.components.SplashScreen
import com.msop.player.ui.library.LibraryViewModel
import com.msop.player.ui.permission.AudioPermissionGate
import com.msop.player.ui.theme.MsopTheme
import com.msop.player.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 启动画面进度条的起点：留一道可见的细头，0% 时不会看起来像「没画出来」。 */
private const val SPLASH_START_PROGRESS = 0.06f

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scanScheduler: LocalScanScheduler

    @Inject
    lateinit var safDirectories: SafDirectoryStore

    @Inject
    lateinit var themePreferences: ThemePreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // 2026-11-14 深浅色（仅两档，默认深色）：状态放在 setContent 根，
            // 设置页改主题后回调刷新，立即全局生效。不读“跟随系统”。
            var themeMode by remember {
                mutableStateOf(
                    themePreferences.themeModeName
                        ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                        ?: ThemeMode.DARK,
                )
            }
            val onThemeChange: (ThemeMode) -> Unit = { mode ->
                themeMode = mode
                themePreferences.setThemeModeName(mode.name)
            }

            // 2026-11-18：原来是 Activity 实例字段，旋转/重建后归零，回前台会
            // 再 enqueue 一次。rememberSaveable 让「本次已触发扫描」跨配置变更保留。
            var scanTriggered by rememberSaveable { mutableStateOf(false) }

            MsopTheme(themeMode = themeMode) {
                // 2026-11-17：启动画面是覆盖层，盖住「首帧 → 曲库就绪」之间的空窗。
                // 它压在权限门之上（权限系统弹窗是独立窗口，仍在最上），
                // 到点淡出后整棵子树卸载，不参与后续任何状态。
                Box(Modifier.fillMaxSize()) {
                    // 2026-11-17：ViewModel 提到权限门之外——启动画面的进度条要读它的
                    // 真实加载状态（曲库什么时候从数据库读出来），不能自己编进度。
                    val viewModel: LibraryViewModel = hiltViewModel()
                    AudioPermissionGate(
                        onGranted = {
                            if (!scanTriggered) {
                                scanTriggered = true
                                scanScheduler.enqueue()
                            }
                        },
                    ) {
                        var directories by remember { mutableStateOf(safDirectories.treeUris()) }

                        MsopApp(
                            viewModel = viewModel,
                            directories = directories,
                            themeMode = themeMode,
                            onThemeChange = onThemeChange,
                            onRescan = { scanScheduler.enqueue(full = true) },
                            onDirectoryPicked = { uri ->
                                safDirectories.add(uri.toString())
                                directories = safDirectories.treeUris()
                                scanScheduler.enqueue(full = true)
                            },
                            onRemoveDirectory = { treeUri ->
                                safDirectories.remove(treeUri)
                                // 授权要一起还回去，否则系统会一直保留这个 tree URI 的读权限
                                runCatching {
                                    contentResolver.releasePersistableUriPermission(
                                        Uri.parse(treeUri),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                                directories = safDirectories.treeUris()
                                // 只有全量重扫才会清理不在库里的幽灵条目
                                scanScheduler.enqueue(full = true)
                            },
                        )
                    }

                    // 最短展示 1.6s（2026-11-17 用户指令「启动画面延到 1.6s」，原 1.1s），
                    // 再 0.42s 淡出。字距收拢要 1.5s，给足动画讲完的时间；
                    // 冷启动本来就要等扫描，这段等待有东西看。
                    var splashVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1_600)
                        splashVisible = false
                    }
                    // 2026-11-18：tracks 是 StateFlow，初值就是空列表，原来的 first()
                    // 立刻返回、进度条瞬间满格。改为等第一批真实数据；空曲库或读取
                    // 失败时最多等 3 秒就当就绪，不让启动画面卡住。
                    val libraryLoaded by produceState(initialValue = false) {
                        withTimeoutOrNull(3_000L) {
                            viewModel.tracks.first { it.isNotEmpty() }
                        }
                        value = true
                    }
                    AnimatedVisibility(
                        visible = splashVisible,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(420)),
                    ) {
                        SplashScreen(progress = if (libraryLoaded) 1f else SPLASH_START_PROGRESS)
                    }
                }
            }
        }
    }
}
