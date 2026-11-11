package com.primuse.lite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.primuse.lite.feature.local.metadata.LocalMetadataEnricher
import com.primuse.lite.feature.local.saf.SafDirectoryStore
import com.primuse.lite.feature.local.work.LocalScanScheduler
import com.primuse.lite.ui.PrimuseApp
import com.primuse.lite.ui.library.LibraryViewModel
import com.primuse.lite.ui.permission.AudioPermissionGate
import com.primuse.lite.ui.theme.PrimuseLiteTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scanScheduler: LocalScanScheduler

    @Inject
    lateinit var safDirectories: SafDirectoryStore

    @Inject
    lateinit var metadataEnricher: LocalMetadataEnricher

    /** 保证每个 Activity 实例只触发一次扫描，避免每次回前台都重扫。 */
    private var scanTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PrimuseLiteTheme {
                AudioPermissionGate(
                    onGranted = {
                        if (!scanTriggered) {
                            scanTriggered = true
                            scanScheduler.enqueue()
                        }
                    },
                ) {
                    val viewModel: LibraryViewModel = hiltViewModel()
                    var directoryCount by remember {
                        mutableIntStateOf(safDirectories.treeUris().size)
                    }

                    PrimuseApp(
                        viewModel = viewModel,
                        directoryCount = directoryCount,
                        onRescan = { scanScheduler.enqueue(full = true) },
                        onDirectoryPicked = { uri ->
                            safDirectories.add(uri.toString())
                            directoryCount = safDirectories.treeUris().size
                            scanScheduler.enqueue(full = true)
                        },
                        onWarmCovers = {
                            // 调试入口：封面预热要真机验证落盘与耗时。
                            // 正式版应在首屏静默触发，这里的按钮只是给验收用。
                            lifecycleScope.launch {
                                val stats = metadataEnricher.warmCovers(limit = 30)
                                toast(
                                    "封面预热：成功 ${stats.covers}/${stats.candidates}，" +
                                        "耗时 ${stats.elapsedMs}ms",
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
