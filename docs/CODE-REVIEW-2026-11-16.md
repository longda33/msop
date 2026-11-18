# 代码审查报告（2026-11-16）

> 审查对象：`primuse-android` 当前工作区源码（12 个模块、约 160 个 Kotlin 文件、Manifest 与 Gradle 配置）。
> 方式：分模块逐文件静态审查 + 对高风险结论逐条回读代码复核。
> 本次**未执行** Gradle 构建与单测；行号基于当前工作树。

## 结论摘要

| 级别 | 数量 | 最需要优先处理的 |
|---|---|---|
| 阻断 | 0 | — |
| 高 | 6 | 明文门禁遗漏、Room 破坏性迁移兜底、远程同步误删、离线起播保护失效 |
| 中 | 9 | 快照恢复标志卡死、歌词重载竞态、备份恢复非事务 |
| 低 | 6 | 解析器边界、批量 SQL 变量上限、重建后状态丢失 |

整体架构（模块边界、缓存链、URI 稳定 key、双通道扫描）与项目文档一致，未发现必然崩溃的阻断问题；但存在**两处会直接丢失数据/泄露凭据的高风险问题**（H1、H2）和**一处会把云端曲目误删的问题**（H4），建议优先修复。

---

## 高优先级

### H1. Room 配置了破坏性迁移兜底，违反项目硬约束
**位置**：`core/database/src/main/java/com/msop/player/core/database/di/DatabaseModule.kt:32`

`.fallbackToDestructiveMigration(dropAllTables = true)` 会在任何一次漏写迁移时静默清空曲库、歌单、收藏与墓碑表。项目 `AGENTS.md` §3 明确禁止 destructive 迁移；当前注释「宁可重建也不能启动崩溃」与硬约束冲突。

**修复**：移除该 fallback；漏迁移时让启动失败并补齐 Migration。如需兜底，用 `fallbackToDestructibleMigration()` 之外的显式版本检查/备份恢复流程。

### H2. SubsonicClient 完全绕过明文 HTTP 门禁，apiKey 可能明文外发
**位置**：`core/network/src/main/java/com/msop/player/core/network/SubsonicClient.kt:52-56, 92-99, 110-122, 154-161`

`candidateConfig()/requireConfig()/apiUrl()/coverArtUrl()` 均不检查 `ServerConfig.allowPlainHttp`。`network_security_config.xml:22` 已全局放开 `cleartextTrafficPermitted="true"`，应用层门禁是唯一防线；`OpenListClient` 与 `WebDavClient` 都有校验，唯独此类遗漏。配置了 `http://` 且未二次确认时，`apiKey` 会作为 query 参数明文发出；封面 URL 还会被 Coil 再请求一次。

**修复**：在 `requireConfig()` 与 `coverArtUrl()` 入口按 `OpenListClient.requireConfig()` 同样逻辑检查 `baseUrl` 与 `allowPlainHttp`，未确认则抛 `OpenListException.PlainHttpNotConfirmed()`；同步更新类注释中「network_security_config 禁明文」的过时表述。

### H3. 时长探测先把整个响应读入内存，服务器不支持 Range 时可 OOM
**位置**：`core/network/src/main/java/com/msop/player/core/network/RemoteDurationProbe.kt:97-101`

`resp.body.bytes()` 先完整读 body，之后才判断 `bytes.size > length * 8`。不支持 Range 的服务器会返回整首 FLAC；叠加 `effectiveConcurrency` 最高 16 路并发探测，大文件场景下可能 OOM。

**修复**：改用 `byteStream()` 最多读取 `length*8+1` 字节；或先检查 `Content-Length`，超限直接放弃，不分配大数组。

### H4. OpenList 同步在部分目录失败时仍执行差集清理，可能误删云端曲目
**位置**：`feature/remote/src/main/java/com/msop/player/feature/remote/RemoteLibrarySyncer.kt:102-126, 188-191`

列目录普通失败仅 `skipped++` 后 `break`，但删除 stale 前只检查 `found.isNotEmpty()`，未检查 `skipped`。某目录 500/超时，或 `MAX_PAGES` 触顶导致剩余目录不再扫描时，这些目录里的曲目会全部进入 `stale` 被 `deleteByIds` 清掉。`pages` 还是跨所有 profile 共享的全局计数，大库多服务器时更易触顶。对照 `WebDavLibrarySyncer.kt:125` 已有 `failedDirectories.get()==0` 保护。

**修复**：引入 `canClean = found.isNotEmpty() && skipped.get() == 0 && !pagesExhausted`，仅在 `canClean` 时删除；`pages` 改为按目录或按 profile 计数，触顶时禁止清理。

### H5. 离线起播保护读取到尚未回填的 MediaController 状态，可能失效
**位置**：`feature/player/src/main/java/com/msop/player/feature/player/PlaybackController.kt:137-139, 145-146, 365-382`

`pauseRemoteIfOffline()` 在 `play()`/`setMediaItems()` 之后同步调用，却读 `player.currentMediaItem` 与 `player.playWhenReady`。`MediaController` 的这些状态由会话事件异步回填，刚建连/刚切歌时仍可能是 null/false，两个 `?: return` 会提前退出——与第 363 行注释承诺的「覆盖离线状态下点播放」相反。网络 Flow 因状态未变化也不会补发。

**修复**：起播时直接根据目标 `Track.source` 判断并强制 `controller.pause()`；或在 `publish()` 检测到新 mediaId/起播状态时复检。用本地意图标志替代读取 controller 的 `playWhenReady`。

### H6. SAF 歌词旁挂文件 documentId 拼接错误，恒找不到歌词
**位置**：`feature/lyrics/src/main/java/com/msop/player/feature/lyrics/LyricsRepository.kt:160-168`

以 `treeDocumentId = "primary:Music"`、相对路径 `MsopTest/a.mp3` 为例，代码拼出 `primary:Music:MsopTest/a.lrc`；而 SAF 完整 documentId 应为 `primary:Music/MsopTest/a.lrc`（见同文件 `readDirectDocumentSibling` 与 `SafPaths.kt:9` 的格式说明）。结果是 MediaStore 来源曲目通过授权树找 `.lrc/.ttml` 永远失败，回退裸路径在 Android 10+ 又会被 EACCES 拒绝。

**修复**：改为 `"$treeDocumentId/$sibling"`。

---

## 中优先级

### M1. `suppressSnapshotSave` 异常后永久卡死
`feature/player/.../PlaybackController.kt:259-268`：恢复现场时先置 `true`，仅在全部步骤成功后复位。中间任一步抛异常，此后整个进程不再写任何播放快照，恢复功能彻底失效。**修复**：`try { ... } finally { suppressSnapshotSave = false }`。

### M2. 播放中切歌不会立即落快照
`feature/player/.../PlaybackController.kt:274-293`：`syncSnapshotTicker` 算出 `force = trackId != snapshotTrackId`，但 `isPlaying == true` 且 ticker 已激活时直接 `return`，`force` 被丢弃，要等下一个 5s 周期；进程在此期间被杀会恢复到上一首。**修复**：playing 分支在 `force` 时立即 `saveSnapshot(force = true)` 再续 ticker。

### M3. 歌词重载存在竞态与协程泄漏
`app/src/main/java/com/msop/player/ui/player/LyricsViewModel.kt:29-60`：`load()` 每次新起协程但不取消旧 Job；`reload()` 先把 `currentTrackId` 置 null 再 `load()`，旧 Job 完成时会发现 id 又等于当前 id，从而用旧结果覆盖新歌词。快速切歌/重载时可能显示错歌歌词。**修复**：保存并取消上次 Job；用递增 token 取代 id 比较。

### M4. 备份导入非事务，异常会留下半恢复状态
`app/src/main/java/com/msop/player/backup/BackupManager.kt:109-155`：循环插入歌单后再 `applySettings`，全程没有 Room 事务。中途异常（如 `equalizer.importTuning` 抛错）时已写库歌单不回滚。**修复**：写库部分用 `withTransaction` 包住，settings 应用放在事务成功后；失败时给出明确回滚结果。

### M5. 备份 zip 解压条目数无上限
`app/src/main/java/com/msop/player/backup/BackupManager.kt:219-235`：单条目限制 4 MB 且按文件名白名单读取（无 zip slip 风险），但条目总数不限，恶意包可用海量同名条目耗尽 IO/CPU。**修复**：限制总条目数与累计读取字节数。

### M6. Manifest 开启 allowBackup 且无备份规则
`app/src/main/AndroidManifest.xml:30`：`allowBackup="true"`，未配置 `dataExtractionRules`。在 targetSdk 37 / Android 12+ 下，系统云备份可能把含服务器凭据的 SharedPreferences 一并带离设备，绕开应用内受控导出路径。自用且含凭据，建议 `allowBackup="false"` 或显式规则排除凭据文件。

### M7. 配置存储并发读改写非原子
`core/datastore/.../ServerConfigStore.kt:67-80, 112-143`、`WebDavConfigStore.kt:50-62, 84-113`：`setEnabled/upsert` 先读再 `apply()`，UI 线程与 IO 线程都可能触达，并发时会丢失更新，或 `nextId()` 算出重复 id 覆盖另一条配置。**修复**：相关方法加 `@Synchronized` 或用统一 Mutex。

### M8. WebDavClient.headers() 缺少明文门禁（纵深防御）
`core/network/src/main/java/com/msop/player/core/network/WebDavClient.kt:108-113`：与 `resolve()` 不同，公开方法 `headers()` 直接产出 Basic 凭据而不检查 `allowPlainHttp`。当前播放链路先调用 `resolve()`（有门禁），暂不可直接利用；但作为公开入口应补齐检查，避免后续调用顺序变化导致明文泄露。

### M9. 稳定缓存 key 对内部 URI 剥离 query/fragment，可能碰撞
`feature/player/.../cache/MediaCachePolicy.kt:36-39`：该 key 的入参是干净的内部 URI（`openlist:{id}:/路径`），签名的 query/fragment 只存在于解析后的 raw_url。`substringBefore('?')/substringBefore('#')` 在文件名本身含 `?`/`#` 时会把不同曲目映射到同一 key，导致缓存命中后播放另一首。**修复**：内部 URI 不剥离，或仅对原始 raw_url 做规范化。

---

## 低优先级

| # | 位置 | 问题与修复 |
|---|---|---|
| L1 | `feature/lyrics/.../TtmlParser.kt:159-162` | `end` 的 offset-time 按规范相对段落 `begin`，此处传 `parentBeginMs=0L`，`end="2s"` 会被当成绝对 2 秒。改为传 `beginMs`。 |
| L2 | `feature/lyrics/.../LrcParser.kt:65` | 仅凭「两个非空片段」判定逐字行，普通增强 LRC（一行两个时间戳各带文字）会被错误合并。增加「每段为单字/极短」等约束。 |
| L3 | `core/database/.../dao/TrackDao.kt:116-117` | `DELETE ... IN (:ids)` 未分块；幽灵清理一次传入数千 id 时，在 SQLite 变量上限较低的设备上会抛异常。按 ≤500 分块删除。 |
| L4 | `feature/local/.../scan/LocalLibrarySyncer.kt:15, 53-56` | 默认 `now()` 用毫秒，而 MediaStore/SAF 的 `addedAt` 是秒；`addedAt==0` 兜底分支会写入毫秒值污染排序。统一为秒。 |
| L5 | `feature/player/.../PlaybackController.kt:85, 223-230` | `release()` 未取消 `scope`，网络监听 Job 随单例继续存活。若实例会被重建，应在 `release()` 末尾 `scope.cancel()`。 |
| L6 | `app/src/main/java/com/msop/player/MainActivity.kt:38-65` | `scanTriggered` 是 Activity 实例字段，配置变更重建后会重置并重复 `enqueue`。依赖 WorkManager 唯一工作幂等或移入 ViewModel/持久化。 |

---

## 已排除的误报与按决策豁免

- `OpenListViewModel.kt:381` 的 `subsonic.testConnection()` **不是**主线程阻塞：`SubsonicClient.testConnection()` 自身用 `withContext(Dispatchers.IO)`。
- `BackupManager.readEntries()` 使用 `entry.name.substringAfterLast('/')` + 白名单，**不存在 zip slip**。
- 按项目已接受决策不列为缺陷：D17 普通 SharedPreferences 存密码、备份导出明文密码、D27 全局明文 HTTP + 应用层门禁、UI 集中在 app 模块、D18/D19 缓存链设计。

## 建议修复顺序

1. **H2 → H1 → H4**：先止住凭据泄露与数据丢失风险。
2. **H5 → H3 → H6**：修复播放核心与歌词加载的确定性缺陷。
3. **M1/M3/M4**：状态一致性问题，建议同批补单测（快照恢复、歌词重载、备份导入）。
4. 中低其余项按模块逐步清理。

> 本报告为静态审查；未运行 `./gradlew test` 或 assemble。上线前建议对 H1/H2/H4/H5 各补一条回归测试或真机验证路径。
