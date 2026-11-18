package com.msop.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
 * OpenList 服务器管理页（P3-07，2026-11-16 起多配置）。
 *
 * 进入是**列表**：每台一行，显示「启用中」标记、上次连接结果、曲库首数与上次同步时间，
 * 右侧三点菜单可编辑、测试、启用、删除；底部可直接新增。点一行进入编辑器，
 * 编辑器里除地址/账号/密码/应用密钥外还能填一个显示名（列表里用它区分服务器）。
 *
 * 同一时刻只有一条「启用中」——同步与播放都走它，切换或删除启用项会连带清理
 * OpenList 来源的曲目（见 [OpenListViewModel]）。
 */
@Composable
fun OpenListConfigScreen(
    vm: OpenListViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { if (state.editorOpen) vm.closeEditor() else onBack() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        if (state.editorOpen) {
            OpenListEditor(vm = vm, state = state)
        } else {
            OpenListServerList(vm = vm, state = state, onBack = onBack)
        }
    }
}

// ---------------------------------------------------------------- 列表

@Composable
private fun OpenListServerList(
    vm: OpenListViewModel,
    state: OpenListViewModel.UiState,
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
                text = "OpenList 服务器",
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
            text = "可以保存多台服务器，多台还能同时启用：启用中的服务器会一起同步进曲库，" +
                "同名歌曲自动去重；行内菜单随时可以取消启用。",
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
                        text = "还没有服务器",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextSecondary,
                    )
                    Text(
                        text = "点右上角「＋」或下面的按钮新增一台 OpenList 服务器。",
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
                    actions = buildActions(vm, row, onDelete = { pendingDelete = row.config.id }),
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
            title = { Text("删除这台服务器？") },
            text = {
                Text(
                    if (isActive) {
                        "「$name」正在启用中，删除后会同时从曲库移除已同步的 OpenList 歌曲，本机歌曲不受影响。"
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
            title = { Text("清除全部 OpenList 配置？") },
            text = { Text("会删除全部 OpenList 服务器配置，并从曲库移除已同步的远程歌曲；本机歌曲不受影响。") },
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

private fun buildActions(
    vm: OpenListViewModel,
    row: OpenListViewModel.ProfileRow,
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
private fun OpenListEditor(
    vm: OpenListViewModel,
    state: OpenListViewModel.UiState,
) {
    val isPlainHttp = remember(state.url) {
        runCatching { ServerUrl.normalize(state.url).isPlainHttp }.getOrDefault(false)
    }
    // "save" / "test" / "sync"：确认明文后要执行哪一个
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
                text = if (state.editingId == null) "新增 OpenList 服务器" else "编辑服务器",
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.TextPrimary,
            )
        }

        Text(
            text = "连接公网或内网 OpenList，把远程音频同步进曲库，与本机歌曲混排播放。" +
                "本机部署若未启用 Subsonic，会自动走 OpenList 原生接口。",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
            modifier = Modifier.padding(horizontal = Dim.screen, vertical = 6.dp),
        )

        ConfigField(
            label = "显示名（可选）",
            value = state.name,
            hint = "留空则用主机名，例如「家里的 OpenList」",
            onValueChange = vm::onNameChange,
            keyboardType = KeyboardType.Text,
        )
        ConfigField(
            label = "服务器地址",
            value = state.url,
            hint = "music.example.com 或 https://example.com/openlist",
            onValueChange = vm::onUrlChange,
            keyboardType = KeyboardType.Uri,
        )
        ConfigField(
            label = "账号",
            value = state.username,
            hint = "OpenList 用户名",
            onValueChange = vm::onUsernameChange,
            keyboardType = KeyboardType.Text,
        )
        ConfigField(
            label = "密码",
            value = state.password,
            hint = "OpenList 密码",
            onValueChange = vm::onPasswordChange,
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )
        ConfigField(
            label = "音乐目录（可选）",
            value = state.rootPath,
            hint = "例如 /yd/music；留空则同步整个网盘",
            onValueChange = vm::onRootPathChange,
            keyboardType = KeyboardType.Uri,
            multiLine = true,
            note = "可以填多条，一行一个目录，例如 /yd/music 与 /yd/musicweb 各占一行；" +
                "留空则同步整个网盘。",
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
        ConfigField(
            label = "Subsonic 应用密钥（可选）",
            value = state.appKey,
            hint = "olmp_ 开头；填了才有真实封面与艺术家/专辑",
            onValueChange = vm::onAppKeyChange,
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )

        if (isPlainHttp) {
            Text(
                text = "⚠ 当前是 http:// 明文地址：账号密码会在网络上裸传，仅内网/自用场景使用",
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
                    "明文传输会暴露账号密码。App 允许 http 是为了内网/自用服务器，" +
                        "公网地址请优先改成 https://。",
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
 * placeholder 只在空框时显示，填了值说明就消失；需要「默认值 / 范围 / 填大了会怎样」
 * 这类常驻解释的字段改用 `note` 放在框下方。
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
