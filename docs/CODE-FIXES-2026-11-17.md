# 代码审查与修复记录 · 2026-11-17

> 触发：用户指令「审查项目代码，修复发现问题」。
> 基线：`CODE-REVIEW-2026-11-16.md` 的 21 项静态发现（6 高 / 9 中 / 6 低），本轮**逐项回读当前代码**确认是否仍存在，只修仍然成立的，并补了几处审查时新发现的问题。
> 验证：`:app:assembleDebug` BUILD SUCCESSFUL；`test` 共 **277 例 0 失败 0 跳过**（debug + release 两个变体）。
> 未做：真机/模拟器手测（本轮全部是代码层修改，行为验证靠编译 + 单测 + 代码推演，见文末「未验证项」）。

## 一、高危：数据丢失与凭据泄露

### 1. 数据库漏迁移会静默清库
- 位置：`core/database/.../di/DatabaseModule.kt`
- 问题：`.fallbackToDestructiveMigration(dropAllTables = true)` 兜底。任何一次漏写 Migration，用户 509 首本机曲、云端曲目、歌单、收藏、播放计数全部消失，界面只表现为「曲库空了」，且不可恢复。
- 修复：删除该兜底。v1→v5 五条迁移已齐备，以后加版本号必须补 Migration；宁可启动抛 `IllegalStateException`（开发阶段立刻暴露）也不静默删数据。

### 2. 明文凭据进系统云备份
- 位置：`app/src/main/AndroidManifest.xml`、新增 `app/src/main/res/xml/data_extraction_rules.xml`
- 问题：`android:allowBackup="true"`，而 `msop_openlist` / `msop_webdav` 两个 SharedPreferences 存的是**明文密码 + 媒体应用密钥**，会随 Google 云备份/换机迁移离开设备。
- 修复：`allowBackup=false` + `fullBackupContent=false`，并新增 `data_extraction_rules.xml` 显式排除两个 prefs 文件（防将来有人把 allowBackup 改回 true）。

### 3. 云端同步会在服务端异常时误删曲库
- 位置：`feature/remote/.../RemoteLibrarySyncer.kt`
- 问题：清理幽灵条目只判断「这次扫到了至少一首」。但单个目录列目录失败（403/500/挂载点异常）会被 `catch` 掉并跳过，该目录下的歌就落到差集里 → 一次服务端 500 能删掉半个曲库；`MAX_PAGES` 用尽时后面的目录压根没扫，同样被当成「已删除」。`WebDavLibrarySyncer` 早已有 `canClean` 保护，OpenList 这条没有。
- 修复：补 `canClean = found.isNotEmpty() && skipped == 0 && !pagesExhausted`，不满足时只 upsert、不删除，统计里的 `deleted` 也同步为 0。

### 4. 批量删库撞 SQL 绑定变量上限
- 位置：新增 `core/database/.../dao/TrackDaoChunks.kt`；调用点 5 处
- 问题：`DELETE ... WHERE id IN (:ids)` 单语句绑定参数上限为 999（老系统）/ 32766（新系统）。曲库安全上限 2 万条，换服务器或目录大改时一次传几千上万个 id 会抛 `too many SQL variables`，同步在「清理」这一步整体失败。
- 修复：新增 `deleteByIdsChunked` / `clearCoverChunked`（400 条一片），替换 `RemoteLibrarySyncer`、`WebDavLibrarySyncer`（各 2 处）、`RemoteCoverExtractor`、`RoomMetadataStore`、`RoomLocalTrackStore` 的直连调用。

### 5. 时长时间探测会把整首歌读进内存
- 位置：`core/network/.../RemoteDurationProbe.kt` → `range()`
- 问题：`resp.body.bytes()` 先全量读入再判断大小。服务器不支持 Range（返回 200 + 全文件）时，一首无损 FLAC 几十 MB 直接进堆；同步是 4 路并发，两三首就能 OOM（OOM 是 Error，`catch (Exception)` 兜不住，整个同步协程挂掉）。
- 修复：改为按上限流式读取（`declared` 与 8×buffer 取小），读够即停并关流；尾部 Range 请求遇到 200/超大 Content-Length 直接放弃（原来会把文件开头误当 moov 解析）。

### 6. 明文 HTTP 闸门只装了三分之一
- 位置：`core/network/.../SubsonicClient.kt`、`core/network/.../WebDavClient.kt`
- 问题：`OpenListClient` 有 `ensureAllowed`（用户在设置里点过「仍然继续」才允许 `http://`），但 Subsonic 与 WebDAV 没有：Subsonic 会把 `apiKey`、WebDAV 会把 Basic 凭据发到明文地址；WebDAV 的 `headers()` 连 `resolve()` 已有的闸门都绕过了——播放走 `resolve`，而时长探测/歌词探测走 `headers`。
- 修复：`SubsonicClient.execute(config, url)` 统一走 `ensureAllowed(config)`；`WebDavClient.headers()` 在取配置时一并 `ensureAllowed`。

## 二、中危：功能错误

### 7. 断网保护在起播瞬间失效
- 位置：`feature/player/.../PlaybackController.kt`
- 问题：`pauseRemoteIfOffline()` 读 `controller.currentMediaItem`。`MediaController` 的队列状态是异步同步的，`setMediaItems`/`play()` 刚返回时它还是 null → 直接 return。离线状态下点播一首云端歌，保护要等几秒后才被 `NetworkMonitor` 的**后续**事件兜住，而断网那一刻不会再发事件。
- 修复：签名改为 `pauseRemoteIfOffline(source, requestedPlaying)`。`playQueue` 直接传入 `tracks[index].source` 与「这次要播」；`toggle` 用 `willPlay` 判断用户意图（主动暂停时不弹网络提示）；`next/previous` 仍回落到控制器/状态。

### 8. 播放快照的抑制标志可能永久卡住
- 位置：`feature/player/.../PlaybackController.kt` → `restoreLastSession()`
- 问题：`suppressSnapshotSave = true` 后手工复位。中间的 `setMediaItems` / `prepare` / `publish` 任一抛异常，标志永远停在 true，之后**所有**快照被静默丢弃，冷启动再也恢复不了播放现场，日志里毫无痕迹。
- 修复：包 `try/finally` 保证复位。

### 9. 播放中切歌的快照要等 5 秒
- 位置：同上 → `syncSnapshotTicker()`
- 问题：ticker 活着时，`onEvents` 进来的「切歌/暂停」直接 return，要等下一个 5 秒周期才落盘；这期间被杀进程，恢复出来的是上一首。
- 修复：ticker 活跃但 `force == true`（曲目变化或播放态变化）时立即 `saveSnapshot(force = true)`。

### 10. SAF 旁挂歌词永远找不到（MediaStore 曲目）
- 位置：`feature/lyrics/.../LyricsRepository.kt` → `readTreeSibling()`
- 问题：子文档 id 拼成 `"$treeDocumentId:$sibling"`，得到 `primary:Music:MsopTest/a.lrc`。DocumentsProvider 要求「父 id + `/` + 子路径」，多冒号会抛 `IllegalArgumentException`，被 `runCatching` 吞掉，表现为「目录里明明有同名 `.lrc` 却显示暂无歌词」。
- 修复：改成 `"$treeDocumentId/$sibling"`。（曲目本身是 SAF 文档 URI 的路径一直是对的，只有 MediaStore 来源走这条，所以问题不容易被发现。）

### 11. 歌词切歌/重载会被旧任务覆盖
- 位置：`app/.../ui/player/LyricsViewModel.kt`
- 问题：两处。① 每次 `load` 都新起协程且不取消旧的，云端歌词要发 Range 请求，慢请求回来时只比对 `currentTrackId`；② `reload()` 先清 id 再 `load()`，旧协程恢复后看到的 id 又等于「当前曲」，照样覆盖。用户点「重新加载歌词」时旧结果可能把新结果盖掉。
- 修复：加 `loadJob` 显式取消 + `loadToken` 自增序号双重校验（序号比 id 更可靠，因为 id 会被复用）。

### 12. 旁挂歌词读取没有上限
- 位置：`feature/lyrics/.../LyricsRepository.kt`
- 问题：三处 `readBytes()` 全量读入。一个被误命名成 `.lrc` 的百兆文件就能把歌词线程堆撑爆。
- 修复：统一走 `readLimited()`，上限 8 MB，超限当作文本不存在。

### 13. 备份导入不是原子的
- 位置：`app/.../backup/BackupManager.kt` → `import()`
- 问题：逐个歌单 `insertPlaylist` + `addTracks`，中途失败会留下「恢复了一半」的曲库；重试时同名歌单虽能靠 `findByName` 认出来，但曲目顺序已乱。
- 修复：注入 `MsopDatabase`，整批放进 `database.withTransaction { }`（为此在 `app` 模块显式声明 `room-ktx`，`core:database` 里的它是 `implementation` 依赖，不会传递）。

### 14. 备份 zip 只限单条目大小
- 位置：同上 → `readEntries()`
- 问题：单条目 4 MB 上限存在，但 zip 里塞 10 万个空条目时，循环仍要把每个条目 inflate 一遍，导入能卡到 ANR 级。
- 修复：新增条目数上限 32 与解压总量上限 12 MB，超限直接报错中止。

### 15. 配置 Store 的读改写竞态
- 位置：`core/datastore/.../ServerConfigStore.kt`、`WebDavConfigStore.kt`
- 问题：`setEnabled` / `upsert` / `delete` / `markSynced` 都是「读 enabled_ids → 拼新串 → 写回」。同步协程、设置页、备份导入并发调用时会互相覆盖：典型表现是「勾上第二台服务器，第一台被悄悄取消」或「删了一台，同步计数错位」。
- 修复：所有写方法加 `@Synchronized`（实例级锁，读操作不加锁，SharedPreferences 读本身是线程安全快照）。

### 16. 缓存策略的「查不到网络」分支是死代码
- 位置：`feature/player/.../cache/MediaCachePolicy.kt`
- 问题：`runCatching { ... return null ... }` 里的 `return null` 是**非局部返回**，直接跳出函数，`getOrElse` 的兜底永远不会执行；`connectivity` 为 null、`activeNetwork` 为 null 时也不会记日志。
- 修复：改成显式 `try/catch`，语义与注释一致（查不到就返回 null，日志进 `AppLog.w`）。

## 三、顺带修掉的小问题

- `RemoteDurationProbe.fromWav()`：chunk 长度来自文件字节，损坏/伪造的 WAV 可能让 `size.toInt()` 变负 → `i` 反向跑成死循环。加「超出本次读取范围即放弃」。
- `RemoteDurationProbe.fromMp4()`：`mvhd` 正好落在缓冲区最后 4 字节时 `b[idx + 4]` 越界。加边界检查。
- `ServerConfigStore` / `WebDavConfigStore` 的 `FIELD_CONCURRENCY` 常量缩进错位（8 空格 vs 4 空格），仅格式问题。

## 四、复核后确认「不需要改」的项

- **TTML 解析的 XXE**：`TtmlParser.parseDocument()` 已设 `disallow-doctype-decl`、关闭外部通用/参数实体、`isExpandEntityReferences=false`，不必再动。
- **`PlaybackController.release()` 没有取消协程作用域**：表面上是泄漏隐患，但该方法当前**无调用点**，而作用域里跑的是断网监听；若真的 cancel，之后 `requireController()` 重连时断网保护会永久失效。保持现状并在方法注释里说明更安全。
- **`updateSuspension` 类「报告有、代码已改」的项**：P3-04 缓存命中、P7-04 备份 SAF 都在 11-16 实测过，不在本轮范围。

## 五、未验证项（需要真机/模拟器才能确认）

| 项 | 为什么没验 | 怎么验 |
|---|---|---|
| 断网保护起播竞态 | 需要「飞行模式 + 点播云端曲」的时序 | 关网 → 点云端歌 → 应立即暂停并弹「网络断开」，不等数秒 |
| SAF 旁挂歌词 | 需要 MediaStore 曲目 + 授权树外的 `.lrc` | 模拟器放 `/sdcard/Music` 一首 mp3 + 同名 lrc，授权该目录后看歌词页 |
| 备份事务 | 需要构造导入中途失败 | 备份 zip 里把 playlists.json 改成非法 JSON 片段，导入应整体回滚、歌单数量不变 |
| 漏迁移清库 | 需要造一次 v6 无迁移 | 临时把版本号 +1 不加 Migration，启动应抛异常而不是清库（验完即回滚） |

## 六、变更文件清单（18 个源文件 + 2 个新增资源/模块文件）

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/xml/data_extraction_rules.xml            (新增)
app/src/main/java/com/msop/player/backup/BackupManager.kt
app/src/main/java/com/msop/player/ui/player/LyricsViewModel.kt
core/database/src/main/java/com/msop/player/core/database/di/DatabaseModule.kt
core/database/src/main/java/com/msop/player/core/database/dao/TrackDaoChunks.kt   (新增)
core/network/src/main/java/com/msop/player/core/network/SubsonicClient.kt
core/network/src/main/java/com/msop/player/core/network/WebDavClient.kt
core/network/src/main/java/com/msop/player/core/network/RemoteDurationProbe.kt
core/datastore/src/main/java/com/msop/player/core/datastore/ServerConfigStore.kt
core/datastore/src/main/java/com/msop/player/core/datastore/WebDavConfigStore.kt
feature/remote/src/main/java/com/msop/player/feature/remote/RemoteLibrarySyncer.kt
feature/remote/src/main/java/com/msop/player/feature/remote/WebDavLibrarySyncer.kt
feature/remote/src/main/java/com/msop/player/feature/remote/RemoteCoverExtractor.kt
feature/player/src/main/java/com/msop/player/feature/player/PlaybackController.kt
feature/player/src/main/java/com/msop/player/feature/player/cache/MediaCachePolicy.kt
feature/lyrics/src/main/java/com/msop/player/feature/lyrics/LyricsRepository.kt
feature/local/src/main/java/com/msop/player/feature/local/metadata/RoomMetadataStore.kt
feature/local/src/main/java/com/msop/player/feature/local/scan/RoomLocalTrackStore.kt
```
