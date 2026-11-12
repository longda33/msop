package com.primuse.lite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
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
                    var directories by remember { mutableStateOf(safDirectories.treeUris()) }

                    PrimuseApp(
                        viewModel = viewModel,
                        directories = directories,
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
            }
        }
    }
}
