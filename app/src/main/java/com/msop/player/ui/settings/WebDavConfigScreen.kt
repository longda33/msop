package com.msop.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msop.player.core.network.ServerUrl
import com.msop.player.ui.components.AmbientBackground
import com.msop.player.ui.components.LocalHazeBottomInset
import com.msop.player.ui.components.IconAction
import com.msop.player.ui.components.glassSurface
import com.msop.player.ui.theme.Dim
import com.msop.player.ui.theme.Palette
import com.msop.player.ui.theme.Shapes

/**
 * WebDAV 服务器管理页（P7-07，2026-11-16 起多配置）。
 *
 * 与 OpenList 的差别只有两处：
 * 1. 多一个「根路径」——音乐库在服务器上的目录，同步只从这里往下走；
 * 2. 没有应用密钥，元数据只能取目录名（WebDAV 本身不给标签）。
 *
 * 同一时刻只有一条「启用中」，切换或删除启用项会清理 WEBDAV 来源的曲目，
 * 本机与 OpenList 曲目不受影响。
 */
@Composable
fun WebDavConfigScreen(
    vm: WebDavViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { if (state.editorOpen) vm.closeEditor() else onBack() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        if (state.editorOpen) {
            WebDavEditor(vm = vm, state = state)
        } else {
            WebDavServerList(vm = vm, state = state, onBack = onBack)
        }
    }
}

// ---------------------------------------------------------------- 列表

@Composable
private fun WebDavServerList(
    vm: WebDavViewModel,
    state: WebDavViewModel.UiState,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = Dim.screen, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Text(
                text = "WebDAV 服务器",
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = Icons.Filled.Add,
                contentDescription = "新增服务器",
                onClick = { vm.openAdd() },
                enabled = !state.busy,
            )
        }

        Text(
            text = "独立第三音源，与 OpenList 互不影响。可保存多台，多台能同时启用，" +
                "启用中的都会同步进曲库；行内菜单随时可以取消启用。",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
        )

        if (state.profiles.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dim.screen, vertical = 12.dp)
                    .glassSurface(Shapes.cardSmall, Palette.Surface2)
                    .padding(18.dp),
            ) {
                Column {
                    Text(
                        text = "还没有 WebDAV 服务器",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextSecondary,
                    )
                    Text(
                        text = "点右上角「＋」或下面的按钮新增一台。填根地址与音乐目录即可。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            state.profiles.forEach { row ->
                ServerCard(
                    title = row.config.displayName,
                    urlText = row.config.baseUrl +
                if (row.config.rootPath.isBlank()) "" else " · " + row.config.rootPath,
                    status = row.status,
                    enabled = row.enabled,
                    syncedCount = row.syncedCount,
                    lastSyncAt = if (row.enabled) state.lastSyncAt else 0L,
                    busy = state.busy,
                    onOpen = { vm.openEdit(row.config.id) },
                    actions = buildWebDavActions(vm, row, onDelete = { pendingDelete = row.config.id }),
                )
            }
        }

        if (state.busy) {
            Row(
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Palette.BlueLight,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.busyLabel.ifBlank { "处理中…" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) Palette.Red else Palette.Green,
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 8.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { vm.openAdd() },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue),
                modifier = Modifier.weight(1f),
            ) { Text("新增服务器") }
            if (state.profiles.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { confirmClearAll = true }, enabled = !state.busy) {
                    Text("清除全部", color = Palette.TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(LocalHazeBottomInset.current + 28.dp))
    }

    pendingDelete?.let { id ->
        val name = state.profiles.firstOrNull { it.config.id == id }?.config?.displayName.orEmpty()
        val isActive = state.profiles.firstOrNull { it.config.id == id }?.enabled == true
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("删除这台 WebDAV 服务器？") },
            text = {
                Text(
                    if (isActive) {
                        "「$name」正在启用中，删除后会同时从曲库移除已同步的 WebDAV 歌曲；本机与 OpenList 歌曲不受影响。"
                    } else {
                        "只会删除「$name」这条配置，曲库与其它服务器都不受影响。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.delete(id)
                    },
                ) { Text("删除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Palette.TextSecondary)
                }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("清除全部 WebDAV 配置？") },
            text = { Text("会删除所有 WebDAV 配置并从曲库移除已同步的歌曲；本机与 OpenList 歌曲不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        vm.clearConfig()
                    },
                ) { Text("清除", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("取消", color = Palette.TextSecondary)
                }
            },
        )
    }
}

private fun buildWebDavActions(
    vm: WebDavViewModel,
    row: WebDavViewModel.ProfileRow,
    onDelete: () -> Unit,
): List<ServerMenuAction> = buildList {
    add(ServerMenuAction("编辑") { vm.openEdit(row.config.id) })
    add(ServerMenuAction("测试连接") { vm.test(row.config.id) })
    if (row.enabled) {
        add(ServerMenuAction("立即同步") { vm.syncActiveNow() })
        add(ServerMenuAction("取消启用") { vm.toggle(row.config.id) })
    } else {
        add(ServerMenuAction("启用并同步") { vm.toggle(row.config.id) })
    }
    add(ServerMenuAction("删除", danger = true) { onDelete() })
}

// ---------------------------------------------------------------- 编辑器

@Composable
private fun WebDavEditor(
    vm: WebDavViewModel,
    state: WebDavViewModel.UiState,
) {
    val isPlainHttp = remember(state.url) {
        runCatching { ServerUrl.normalize(state.url).isPlainHttp }.getOrDefault(false)
    }
    var pendingHttpAction by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = Dim.screen, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = { vm.closeEditor() },
            )
            Text(
                text = if (state.editingId == null) "新增 WebDAV 服务器" else "编辑服务器",
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.TextPrimary,
            )
        }

        Text(
            text = "填 WebDAV 根地址与音乐目录，同步后可播放与 seek。" +
                "WebDAV 不提供标签：标题取文件名，专辑取父目录。",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
        )

        ConfigField(
            label = "显示名（可选）",
            value = state.name,
            hint = "留空则用主机名，例如「NAS WebDAV」",
            onValueChange = vm::onNameChange,
            keyboardType = KeyboardType.Text,
        )
        ConfigField(
            label = "服务器地址",
            value = state.url,
            hint = "https://dav.example.com/dav",
            onValueChange = vm::onUrlChange,
            keyboardType = KeyboardType.Uri,
        )
        ConfigField(
            label = "账号",
            value = state.username,
            hint = "WebDAV 用户名",
            onValueChange = vm::onUsernameChange,
            keyboardType = KeyboardType.Text,
        )
        ConfigField(
            label = "密码",
            value = state.password,
            hint = "WebDAV 密码（只存本机，不进备份）",
            onValueChange = vm::onPasswordChange,
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )
        ConfigField(
            label = "根路径",
            value = state.rootPath,
            hint = "/music 或 /（不填按根目录）",
            onValueChange = vm::onRootPathChange,
            keyboardType = KeyboardType.Uri,
            multiLine = true,
            note = "可以填多条，一行一个目录；不填按根目录 /。",
        )
        ConfigField(
            label = "请求并发数（可选）",
            value = state.concurrency,
            hint = "留空 = 默认 4",
            onValueChange = vm::onConcurrencyChange,
            keyboardType = KeyboardType.Number,
            note = "同时向这个服务器发几个请求。留空按 4 个；填 1 最稳但最慢，" +
                "内网自己的服务器可以填 8~16 提速，公网/共享盘被限流或报 429、5xx 就往小调到 2~3。范围 1~16。",
        )

        if (isPlainHttp) {
            Text(
                text = "⚠ 当前是 http:// 明文地址：Basic Auth 会裸传账号密码，仅内网/自用场景使用",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Red,
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 4.dp),
            )
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) Palette.Red else Palette.Green,
                modifier = Modifier.padding(horizontal = Dim.screen, vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (isPlainHttp) pendingHttpAction = "test" else vm.testCurrent()
                },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Surface3,
                    contentColor = Palette.TextPrimary,
                ),
                modifier = Modifier.weight(1f),
            ) { Text("测试连接") }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    if (isPlainHttp) pendingHttpAction = "sync" else vm.saveAndSync()
                },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue),
                modifier = Modifier.weight(1f),
            ) { Text("保存并同步") }
        }

        TextButton(
            onClick = {
                if (isPlainHttp) pendingHttpAction = "save" else vm.save()
            },
            enabled = !state.busy,
            modifier = Modifier.padding(horizontal = Dim.screen - 8.dp),
        ) {
            Text("只保存，稍后同步", color = Palette.TextSecondary)
        }

        if (state.busy) {
            Row(
                modifier = Modifier.padding(horizontal = Dim.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Palette.BlueLight,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.busyLabel.ifBlank { "正在处理…" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }


        Spacer(Modifier.height(LocalHazeBottomInset.current + 28.dp))
    }

    if (pendingHttpAction != null) {
        AlertDialog(
            onDismissRequest = { pendingHttpAction = null },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("确认使用明文 HTTP？") },
            text = {
                Text(
                    "WebDAV 的账号密码用 Basic Auth 发送，明文 HTTP 下等同于裸奔。" +
                        "内网/自用可以继续，公网地址请优先改成 https://。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingHttpAction
                        pendingHttpAction = null
                        when (action) {
                            "test" -> vm.testCurrent(allowPlainHttp = true)
                            "sync" -> vm.saveAndSync(allowPlainHttp = true)
                            else -> vm.save(allowPlainHttp = true)
                        }
                    },
                ) { Text("仍然继续", color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttpAction = null }) {
                    Text("改用 HTTPS", color = Palette.BlueLight)
                }
            },
        )
    }
}

/**
 * 配置项输入框。
 *
 * `note`：2026-11-16 晚用户需求「请求并发数提示词进行完善，都不知道怎么填写」——
 * placeholder（`hint`）只在空框时可见，填了数字说明就没了；需要解释「范围 / 默认值 /
 * 填大了会怎样」的字段用 `note` 常驻在框下方。
 */
@Composable
private fun ConfigField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    note: String? = null,
    /** 2026-11-17 深夜（续）用户需求「路径支持填写多条，一行一条」：路径框要能换行。 */
    multiLine: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = !multiLine,
            minLines = if (multiLine) 3 else 1,
            textStyle = TextStyle(color = Palette.TextPrimary),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            // 2026-11-17 深夜（五）：多行时必须换 Text 键盘——Uri 键盘只有「前往」键、
            // 打不出换行，多条目录就没法一行一条输入。
            keyboardOptions = KeyboardOptions(
                keyboardType = if (multiLine) KeyboardType.Text else keyboardType,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.screen, vertical = 6.dp),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(horizontal = Dim.screen + 4.dp),
            )
        }
    }
}
