# 代码审查报告（2026-11-18）

> 审查对象：当前工作树（相对 `367ac93` 有 57 个已跟踪文件改动 + 16 个新增文件，约 +2600/-540 行），覆盖 app / core / feature 共 12 个模块。
> 基线：`docs/CODE-REVIEW-2026-11-16.md`（21 项）与 `docs/CODE-FIXES-2026-11-17.md`（18 文件）。本报告只列**当前代码仍成立或本轮新引入**的问题，不重复已修复项。
> 验证：`sh scripts/check.sh` 自检通过；`./gradlew.bat test` BUILD SUCCESSFUL（debug + release 两个变体，0 失败）；`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL。编译告警仅有 3 条非阻断项（`MainActivity.kt:81` 与 `LibraryScreen.kt:136` 的 `hiltViewModel` 旧包名已废弃、`LibraryViewModel.kt:228` 使用 FlowPreview 未加 `@OptIn`）。未做真机验证。

## 结论摘要

| 级别 | 数量 | 最需要处理 |
|---|---|---|
| 阻断 | 0 | — |
| 高 | 2 | 新增探测读整首歌进堆；WebDAV 同步触顶后仍清理曲库 |
| 中 | 6 | 明文 HTTP 闸门绕过、清缓存与重建并发、提取器无互斥、缓存 key 碰撞、备份丢多服务器配置、扫描统计跑主线程 |
| 低 | 5 | Vorbis 注释数越界异常、桥接会话无界增长、定时关闭状态不保存、启动进度卡住、重复入队扫描 |

整体架构与模块边界仍符合 `docs/AGENTS.md` 的硬约束，上一轮高危项（破坏性迁移、`allowBackup`、明文门禁、同步误删、批量 SQL 变量、Range OOM、SAF 歌词路径、备份事务等）在本轮代码中均已落地修复；但本轮新增/改动的代码里出现了**两处同类回归**：云端探测重复了已修的 `body.bytes()` 模式，WebDAV 同步漏了 OpenList 同步已有的「触顶不清理」保护。应优先处理这两项。

---

## 高优先级

### H1. 云端封面 / 歌词探测在服务器忽略 Range 时会读完整首歌进堆

**位置**
- `core/network/src/main/java/com/msop/player/core/network/RemoteCoverProbe.kt:221-231`（`range()`，第 229 行 `resp.body.bytes()`）
- `core/network/src/main/java/com/msop/player/core/network/RemoteLyricsProbe.kt:219-233`（同样模式，第 226 行）

**问题**：两个新文件先 `body.bytes()` 全量读入，之后才判断 `bytes.size > length * 8L`。`HEAD_BYTES` = 512 KB，阈值 4 MB；不支持 Range 的服务器（或反代把 206 改成 200）返回整首 FLAC/MP3，几十 MB 一次进堆。这**正是上一轮 H3 在 `RemoteDurationProbe.kt:97-121` 已修掉的模式**，新代码等于把修复漏掉了：封面提取用 `Semaphore(4)` 并发（`RemoteCoverExtractor.kt:70,78`），歌词探测在 UI 触发，两者可同时跑，OOM 是 `Error`，`catch (Exception)` 不会兜住，会直接打断同步协程。另外，这两个 `range()` 缺少 `RemoteDurationProbe.kt:102-104` 的尾部响应校验：服务器返回 200 + 小文件时，会把文件开头当成尾部解析，`mp4Picture()` 可能从错误位置取图。

**触发路径**：配置一个不返回 206 的 WebDAV/OpenList 直链 → 冷启动封面提取 / 打开歌词页 → 1~4 路并发拉整首歌。

**修复**：直接复用 `RemoteDurationProbe.range()` 的写法——`byteStream()` + `ByteArrayOutputStream` 只读到 `length*8` 上限即停；尾部请求再补 `resp.code != 206` 判断。两个 `range()` 实现建议下沉成 `core:network` 内的公共私有函数，避免第三次分叉。补一条 MockWebServer 测试：服务器返回 200 + 大于阈值的响应体时，函数应快速返回 null 且内存不增长。

### H2. WebDAV 同步触顶 20000 个目录后仍会清理幽灵条目，可能整批误删曲库

**位置**：`feature/remote/src/main/java/com/msop/player/feature/remote/WebDavLibrarySyncer.kt:77-122, 140-143`（对照 `RemoteLibrarySyncer.kt:204-208` 已修的同款问题）

**问题**：`while (pendingDirs.isNotEmpty() && directories.get() < MAX_DIRECTORIES)`（第 77 行）触顶后直接退出循环，剩余目录根本没扫描；第 121 行的 `localDirs.get() > MAX_DIRECTORIES` 只是 `break`，不会标记「扫描不完整」。但清理判据只有 `found.isNotEmpty() && failedDirectories.get() == 0`（第 140 行），没有「目录数触顶」这一项——未扫描目录里的曲目会进入 `stale` 并被 `deleteByIdsChunked` 清掉。上一轮修复 OpenList 同步时已专门加了 `pagesExhausted` 保护（`RemoteLibrarySyncer.kt:205-206`），WebDAV 这条同构代码漏改。虽然 2 万目录门槛较高，但一次误删不可恢复，且 `deleted` 统计仍会把它当成正常清理（第 158 行），日志无异常。

**触发路径**：WebDAV 根目录层级/目录数超过 2 万（或 `${dir}/ subtree` 极深）→ 同步到上限后停止 → 剩余目录里的全部曲目被当作幽灵删除。

**修复**：把 `val exhausted = directories.get() >= MAX_DIRECTORIES` 纳入 `canClean`：`found.isNotEmpty() && failedDirectories.get() == 0 && !exhausted`，并让 `deleted` 统计与之一致。建议把「目录数上限」和「分页上限」的清理禁令抽成共用小函数，避免第三个同步器再漏。

---

## 中优先级

### M1. WebDAV→OpenList 桥接公开方法不校验明文 HTTP，可把账号密码发到未确认的 `http://`

**位置**：`core/network/src/main/java/com/msop/player/core/network/OpenListWebDavBridge.kt:67-86, 114-125`；测试 `core/network/src/test/java/com/msop/player/core/network/OpenListWebDavBridgeTest.kt:70-80, 113-118`

**问题**：`rawUrlOrNull()`/`loginBlocking()` 直接用 `config.baseUrl` 发 `POST /api/auth/login`，body 是明文 `username/password`，**没有**检查 `WebDavConfig.allowPlainHttp`。当前生产调用点 `WebDavClient.resolve():112` 和 `headers():130` 都先做了 `ensureAllowed()`，所以暂时不构成在线泄露；但这是一个 `@Singleton` 的公开 API，测试本身就在 `allowPlainHttp=false` 的 `http://127.0.0.1:.../dav` 配置下成功发出了登录请求，说明该类自己并未遵守 `docs/AGENTS.md` §3 第 7 条。任何新调用方、或将来调整调用顺序（例如从 `headers()` 里预取直链）都会绕过门禁。与上一轮 M8（`WebDavClient.headers()`）同类问题，当时已按纵深防御修复，这里应保持一致。

**修复**：在 `rawUrlOrNull()` 入口按 `WebDavClient.ensureAllowed()` 同规则检查（`baseUrl` 以 `http://` 开头且 `!allowPlainHttp` → 抛 `WebDavException.PlainHttpNotConfirmed()` 或直接返回 null）；测试里给明文配置显式加上 `allowPlainHttp=true`，并补一条「未确认时不发请求」的用例。

### M2. 清封面缓存时「删除」与「重建」在两个并发协程里跑，可能重建出指向已删除文件的 `coverUri`

**位置**：`app/src/main/java/com/msop/player/ui/settings/SettingsViewModel.kt:144-163`

**问题**：`clearCache()` 启动两个 `viewModelScope.launch`：第一个做 `coverCache.clear()`，第二个做 `clearProbeHistory()` → `enricher.rebuildCovers()` → `remoteCovers.extract()`。第一个在 `Main.immediate` 上刚进入就经 `withContext(IO)` 切到工作线程，第二个持有 `Dispatchers.IO` 会立刻并行开始，二者没有先后保证。`CoverCache.clear()`（`feature/local/.../CoverCache.kt:63-72`）先对 `rootDir.listFiles()` 拍快照再逐个删，完全可能删掉重建刚写好的文件，而数据库里已写入 `file://...` 引用——这正是这段新代码声称要修的「清缓存后封面不显示」。同时第一个协程的 `loadCache(false)` 会在重建完成前读到缓存大小 0，界面数字也是错的。

**修复**：把删除、重建、统计刷新放进同一个 `viewModelScope.launch(Dispatchers.IO)`，按 `clear() → clearProbeHistory() → rebuildCovers() → extract() → withContext(Main.immediate) { loadCache(false) }` 顺序执行；界面 loading 状态在唯一协程结束时清除。

### M3. `RemoteCoverExtractor.extract()` 无互斥，4 个调用点可并发跑同一批曲目

**位置**：`feature/remote/src/main/java/com/msop/player/feature/remote/RemoteCoverExtractor.kt:54-108, 176-188`；调用点 `app/.../ui/library/LibraryViewModel.kt:301`、`app/.../ui/settings/OpenListViewModel.kt:439`、`app/.../ui/settings/WebDavViewModel.kt:389`、`app/.../ui/settings/SettingsViewModel.kt:159`

**问题**：`extract()` 是 `@Singleton` 上的 suspend 方法，内部只用一个 **per-call** `Semaphore(4)` 限制单次并发，多个调用之间没有任何互斥或 in-flight 去重。冷启动时 LibraryViewModel 的后台提取、用户在设置里添加服务器、以及「清缓存」重建三条路径可同时命中同一批曲目：重复发 Range 请求（叠加 H1 时风险放大）、`temp = File(parent, "${sha1}.tmp")`（第 179 行）被两路同时写/重命名；`prefs.edit()` 的失败跳过时间戳也会互相覆盖。临时文件竞态在完全相同封面（同专辑）时尤其容易发生，可能导致 Coil 短暂读到正在被替换的目标文件。

**修复**：在类内加一个 `Mutex`，`extract()` 整段 `withLock`；临时文件改成带随机后缀（如 `File.createTempFile`），重命名成功后只删自己的临时文件，不要先 `file.delete()` 再重命名（第 182 行会短暂移除已发布文件）。

### M4. 稳定缓存 key 对内部 URI 剥离 `?` / `#`，文件名含这两个字符时可能命中另一首歌的缓存

**位置**：`feature/player/src/main/java/com/msop/player/feature/player/cache/MediaCachePolicy.kt:32-39`（`CacheDecision.stableKey()`）

**问题**：这是上一轮 L9 提出但未修的项目。注释说「剥掉一次性签名」，但函数入参已经是干净的**内部 URI**（`openlist:{id}:/path` / `webdav:{id}:/path`），签名只存在于解析后的 raw_url。Linux/远程文件名可以包含 `?` 或 `#`（如 `A?1.flac`、`A#2.flac`），`substringBefore('?').substringBefore('#')` 会把它们截成同一个 key，导致缓存命中后播放另一首歌的音频字节。

**修复**：内部 URI 不做 query/fragment 剥离，直接 `KEY_PREFIX + internalUri`；若确实需要兼容历史 key，可在 resolver 生成 raw_url 时做规范化，而不是在这里对稳定 id 下手。补单测：两个仅 `?`/`#` 不同的内部 URI 应产生不同 key。

### M5. 备份/恢复只处理一台服务器，多 profile 配置会在导出时丢失、导入时变成重复项

**位置**：`app/src/main/java/com/msop/player/backup/BackupManager.kt:73-99, 174-205`；模型 `app/src/main/java/com/msop/player/backup/BackupModels.kt:60+`

**问题**：`SettingsBackup` 只有单数 `server` / `webDav` 字段，导出用 `serverConfigStore.load()` / `webDavConfigStore.load()`，只拿到当前选中那一条；而 `ServerConfigStore` / `WebDavConfigStore` 已支持多 profile（`activeConfig()` / `profiles()`）。导入时 `serverConfigStore.save(ServerConfig(...))` 没带原 id → `upsert()` 分配新 id，旧配置仍在，结果是「备份恢复后又多出一台服务器」，其余 profile 完全丢失。

**修复**：`SettingsBackup` 改为保存 `List<ServerBackup>` / `List<WebDavBackup>`（保留 id、name、rootPath、并发数、启用状态），导出 `profiles()`；导入按 id upsert 并重建 enabled 列表，旧格式单数字段做兼容读取。补一条「两台 OpenList + 两台 WebDAV 备份往返」测试。

### M6. 扫描完成态在主线程把全库实体映射成 Track 并做去重统计

**位置**：`app/src/main/java/com/msop/player/ui/settings/SettingsViewModel.kt:245-256`（`scanState` 的 `.map { }`）；对照正确做法 `feature/local/.../LocalScanWorker.kt:108-113`

**问题**：`scanState` 用 `stateIn(viewModelScope, ...)`，没有 `flowOn(Dispatchers.IO)`；`WorkInfo.State.SUCCEEDED` 分支里的 `observeAll().first().map { it.toTrack() } + TrackMerger.merge()` 在收集侧线程执行（UI 收集即主线程），几千条全库记录 + 嵌套键规范化会造成扫描完成瞬间的掉帧。Worker 里同样的统计已经在后台线程算过（`LocalScanWorker.kt:84, 111`），只是没有写入 `outputData`。

**修复**：在 `LocalScanWorker` 的 `Result.success(workDataOf(...))` 里加 `KEY_LIBRARY_TOTAL to libraryTotal()`（并在通知处复用同一个值，避免算两遍），ViewModel 直接读 `info.outputData.getInt(KEY_LIBRARY_TOTAL, 0)`；若必须在 ViewModel 算，用 `withContext(Dispatchers.IO)` 包住并在 `stateIn` 前加 `flowOn(Dispatchers.IO)`。

---

## 低优先级

| # | 位置 | 问题与修复 |
|---|---|---|
| L1 | `core/network/.../RemoteLyricsProbe.kt:179-181` | `readUInt32(...).toInt()` 得到的 `count` 最高位为 1 时是负数，`repeat(minOf(count, 4096))` 抛 `IllegalArgumentException`。调用方 `LyricsRepository.kt:70-77` 的 `runCatching` 会吞掉，表现为「有歌词但不显示」且日志只有类名。改为 `val count = readUInt32(...); if (count <= 0L) return null`，再 `repeat(minOf(count, 4096L).toInt())`。 |
| L2 | `core/network/.../OpenListWebDavBridge.kt:52-55` | `sessions` 与 `noBridgeUntil` 只在命中时读写，从不清理：反复改密码/换服务器会在进程生命周期内不断累积旧 fingerprint 和 JWT（含已失效 token）。可在 `hasSession()`/`rawUrlOrNull()` 里顺带清理过期条目，或改用带大小上限的 LRU。 |
| L3 | `app/.../ui/player/PlayerSheet.kt:208-241` | 定时关闭的 `sleepEndsAt` 用 `remember`（非 `rememberSaveable`），也没有持久化到 DataStore。Activity 重建/进程被杀后倒计时归零，用户以为设了定时，实际不会停。改为 `rememberSaveable` 至少覆盖配置变更；进程死亡需要落盘 + 启动时校验。 |
| L4 | `app/.../MainActivity.kt:131-146` | 启动进度取 `viewModel.tracks.first()`，但 `tracks` 是 `stateIn` 的 StateFlow，若首个数据库读取出错/一直未发射，`produceState` 会永久停在 6% 的「刚开始」档；淡出后看不到，但期间进度条是假的。给 `first()` 加超时兜底（如 `withTimeoutOrNull(3_000) { first() }`）并置为就绪。 |
| L5 | `app/.../MainActivity.kt:53, 83-86` | `scanTriggered` 是 Activity 实例字段，旋转/进程重建后重置，`AudioPermissionGate.onGranted` 会再次 `enqueue()`。WorkManager 的唯一工作名使其幂等，但每次重建仍多一次调度。移到 ViewModel 或 `rememberSaveable`。 |

---

## 复查后确认无问题的重点项

- **上一轮高危全部仍成立修复**：`DatabaseModule.kt` 已无 `fallbackToDestructiveMigration`；`AndroidManifest.xml` 已 `allowBackup=false` + `dataExtractionRules`；`SubsonicClient` / `WebDavClient.headers()` 已有明文闸门；`RemoteLibrarySyncer` / `WebDavLibrarySyncer` 都有 `canClean` 保护（后者缺目录触顶判断，见 H2）；`TrackDaoChunks` 分块删除已接入；`RemoteDurationProbe` 已流式读取。
- **SAF 旁挂歌词与读取上限**：`LyricsRepository.kt:198-205` 的 `treeDocumentId/$sibling` 与三处 `readLimited()` 均正确。
- **播放快照/断网保护**：`PlaybackController` 的 `try/finally` 复位、`force` 立即落快照、`pauseRemoteIfOffline(source, requestedPlaying)` 已按修复记录落地。
- **备份导入**：`BackupManager` 已用 `database.withTransaction`，zip 条目数/总量上限、白名单文件名均在。
- **配置存储并发**：`ServerConfigStore` / `WebDavConfigStore` 的写方法已 `@Synchronized`。
- **UI 侧已修项**：`LyricsViewModel` 已用 `loadJob` + `loadToken` 双重防串歌；`ExitConfirmDialog`/`SplashScreen` 等新增组件编译通过；设置页对 `http://` 的保存/测试/同步三按钮都接了二次确认（`OpenListConfigScreen.kt:424-437`、`WebDavConfigScreen.kt` 同构）；`TrackMerger` 移到 `core:model` 后调用点与测试 import 一致，`assembleDebug` 与 `test` 均通过。
- **硬约束自检**：`scripts/check.sh` 全绿；模块 `build.gradle.kts` 无硬编码依赖版本；无字符串拼 URL（新增桥接走 `ServerUrl.api()` / `WebDavUrl`）。

## 验证与建议顺序

1. 先修 **H1 → H2**：把两处 `range()` 换成 `RemoteDurationProbe` 的流式实现；给 WebDAV 同步补「目录触顶不清理」保护。两者都是「上轮已修模式的回归」，优先补回归测试。
2. 再修 **M1 → M2 → M3**：安全门禁、清缓存顺序、提取互斥，三处都可加单测/组合验证；**M4** 随缓存模块一起修并补 key 单测；**M5/M6** 属于本轮新功能（多服务器 + 扫描总数）的收尾一致性问题，建议同批修。
3. **L1–L5** 随上述改动顺手清理。
4. 真机补验：`http://` 未确认的 WebDAV 配置不应发出登录请求；清封面缓存后不应出现「URI 在、文件不在」；WebDAV 目录触顶时不应删除曲目。

---

## 修复记录（同日完成）

| # | 修复落地 |
|---|---|
| H1 | 新增 `core/network/.../RemoteRangeReader.kt`：统一流式限长 Range + 尾部 200 拒绝；`RemoteDurationProbe` / `RemoteCoverProbe` / `RemoteLyricsProbe` 全部改用，三处私有实现删除。新增 `RemoteRangeReaderTest` 4 例（本地 HttpServer 验证上限、尾部 200、尾部 206、请求头转发）。 |
| H2 | `WebDavLibrarySyncer` 增加 `directoriesExhausted`，纳入 `canClean` 与 `markSynced` 判据，触顶不再删库、不再误记同步数。 |
| M1 | `OpenListWebDavBridge.rawUrlOrNull()` 入口按 `allowPlainHttp` 抛 `PlainHttpNotConfirmed`；旧测试补 `allowPlainHttp=true`，新增「未确认不发登录请求」用例。 |
| M2 | `SettingsViewModel.clearCache()` 改为单个 IO 协程：清文件 → 清跳过记录 → 本机重建 → 云端重探 → 刷新统计。 |
| M3 | `RemoteCoverExtractor` 加实例级 `Mutex` 串行整轮提取；临时文件改 `File.createTempFile`，rename 冲突时复用已发布文件，不再先删。 |
| M4 | `CacheDecision.stableKey()` 完整保留内部 URI；`MediaCachePolicyTest` 改为验证 `?`/`#` 不串曲。 |
| M5 | `ServerBackup` / `WebDavBackup` 增加 id/name/rootPath/concurrency/enabled，`SettingsBackup` 增加 `servers`/`webDavs` 并兼容旧单数字段；两个 Store 新增 `restoreAll()` 按原 profile id 整表恢复；`BackupModelsTest` 2 例覆盖多 profile 往返与旧格式。 |
| M6 | `LocalScanWorker` 在后台算一次 `libraryTotal` 写入 `KEY_LIBRARY_TOTAL`，通知与设置页共用；`SettingsViewModel` 移除全库映射与 `trackDao` 依赖。 |
| L1 | `RemoteLyricsProbe.lyricsFromVorbis()` 用 Long 判断 comment count，负数直接返回 null；补损坏字节测试。 |
| L2 | `OpenListWebDavBridge` 清理过期 `noBridgeUntil`，改密码时清除同服务器旧 JWT。 |
| L3 | `PlayerSheet.sleepEndsAt` 改 `rememberSaveable`。 |
| L4 | `MainActivity` 启动进度改为等 `tracks.first { isNotEmpty() }`，3 秒超时兜底。 |
| L5 | `scanTriggered` 从 Activity 字段改为 `rememberSaveable`。 |

**修复后验证**：`scripts/check.sh` 通过；`./gradlew.bat test` BUILD SUCCESSFUL（295 例，0 失败 / 0 错误 / 0 跳过，debug + release）；`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL。真机项仍需按上节清单确认。

---

## 真机验证（2026-11-18，MuMu 安卓 15 模拟器）

安装最新 `app-debug.apk` 后逐项验证；操作前已备份设备 `msop.db`（含 WAL）与配置 prefs 到 `.tmp-device-verify/`，测试产生的 `.lrc`、导出 zip 与 SAF 目录均已清理，Wi-Fi 已恢复。

| 项 | 结果 | 实际观测 |
|---|---|---|
| 明文 HTTP 二次确认 | **通过** | WebDAV 新增表单填 `http://127.0.0.1:1/dav`，页面先显示红色警告；点「测试连接」先弹「确认使用明文 HTTP？」（改用 HTTPS / 仍然继续），未确认时 WebDAV profile 数保持 3，没有落库或发登录请求；点「改用 HTTPS」后表单仍在。 |
| SAF 旁挂歌词 | **通过** | 先只在 `/sdcard/Music` 放同名 `.lrc`：只有 `READ_MEDIA_AUDIO`、无 SAF 树时读不到，显示内嵌歌词；给 `msop-music` 授权 SAF 目录并把 `.lrc` 放进该树后，歌词页显示「旁挂歌词验证第一/二/三行」。证明修复后的文档 URI 构造正确；裸路径回退在 Android 15 上仍受 EACCES 限制（已知边界）。 |
| 清缓存后封面引用一致 | **通过** | 清理前 188 条 `file://` coverUri、缺失 0；清理 167 张并重建后 192 条引用、缺失 0。日志：`封面缓存已清空 删除=167` → `封面重建 清空引用=17 重补=17` → `云端封面提取 候选=240 写入=161`，补跑 79 首；UI 从「统计中…」恢复到「171 张 · 11.2 MB」。 |
| 备份多服务器往返 | **通过** | 导出 zip 的 `settings.json` 含 `servers=[p2,p3,p4]`、`webDavs=[p1,p2,p3]`，并保留旧单条字段；重新导入后 `profile_ids`/`enabled_ids`/`active_id` 与导入前完全一致，未产生重复配置。导入只有 `notes.txt` 的 `bad.zip` 报「导入失败：不是 msop 备份：缺少 manifest.json」，配置未变。 |
| 断网起播保护 | **按用户要求跳过** | 该 MuMu 模拟器一断网 adb 即掉线，无法可靠采集 UI/状态；跳过，不作为通过或失败结论。 |

**关于列表里的「负时长」**：曾作为附带发现记录，排查后确认**是设计而非 bug**。`TrackRow.remainingLabel()`（`app/.../ui/components/TrackRow.kt:858-866`）按 2026-11-18 用户指令在剩余时间前加 `-` 表示「还剩」；数值已 `coerceAtLeast(0L)`，起播显示 `-3:16`、播完显示 `-0:00`，数据库的 `durationMs` 与播放页总时长均正常。已补充代码注释说明负号含义，避免再次误判。
