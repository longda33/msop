package com.primuse.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.primuse.lite.feature.local.saf.SafDirectoryStore
import com.primuse.lite.feature.local.work.LocalScanScheduler
import com.primuse.lite.ui.PrimuseApp
import com.primuse.lite.ui.library.LibraryViewModel
import com.primuse.lite.ui.permission.AudioPermissionGate
import com.primuse.lite.ui.theme.PrimuseLiteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scanScheduler: LocalScanScheduler

    @Inject
    lateinit var safDirectories: SafDirectoryStore

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
                    )
                }
            }
        }
    }
}
