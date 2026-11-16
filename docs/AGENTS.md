# AGENTS.md — 模型交接契约

> **接手本项目：先读 `INDEX.md`（一页目录导航，约 2 分钟），再读本文件 §0 第 1–7 条（约 60 行），即可开工。**
> 本文件是唯一的状态声明；`INDEX.md` 只做「读什么/改哪里/怎么跑」的导航，冲突时以本文件与 `TASKS.md` 为准。
> **不要通读本文件全文**（第 2 节「最后更新」是几百 KB 的历史流水）：查历史用 `Grep` 定位，不要从头读。同理 `05-progress.md` 1861 行，从文件尾往前搜日期。

---

## 0. 五分钟接手

如果你只想看六条：

1. **这是什么**：安卓音乐播放器，Kotlin + Compose + Media3，只读本机文件 + 对接公网 OpenList，自用不上架。
2. **做到哪了**：**核心功能全部打通并真机验收**——12 模块骨架、本地扫描入库、播放内核、UI、歌单、歌词、均衡器、后台保活、**OpenList 远程音源（原生 REST 同步 + 流播 + seek，Subsonic 补艺术家/专辑/真实封面）+ SimpleCache 边播边缓存**、release 自签名包、`USAGE.md`。本机具 Microsoft OpenJDK 21 + Android SDK，设备可用 `adb -s emulator-5554`（或 `127.0.0.1:5555`）。**该目录是 git 仓库**。
3. **立刻该做什么**：**P7 视觉三连（P7-09 Haze 标题栏 / P7-10 液态玻璃导航 / P7-11 iOS 式转场）已全部完成并真机验收（含 release 包：R8 后磨砂存活）**。剩余都是需要外部资源的：P7-03 断网保护、P7-04 备份 SAF、P7-07 WebDAV 需远程音源/真实 WebDAV 服务器/SAF 全流程；P3-04 缓存命中需远程曲目；P7-12 libFLAC 缺 NDK 阻塞。另两个可选项：P5-04 大库（5 万首）性能、P6-01 走查清单逐条勾。要做远程相关改动前**先读 `03-openlist-api.md` §6 / §7 / §8**（Subsonic 与原生 REST 各管一段别混用；缓存链顺序与缓存 key 约束见 §8）。真机调试：`adb -s 127.0.0.1:5555`（PGEM10 / Android 15 / 900×1600 竖屏；设备装 debug 包，权限已授；当前设备曲库 LOCAL 18 / OPENLIST 60 / WEBDAV 187，OpenList 与 WebDAV 各有 2～3 台已保存配置，见 `03-openlist-api.md` §9）。**2026-11-16 B 类任务（音源多实例化 + HTTP 明文 + 状态 + 手动删除）已完成真机验收**，要点见下表「最后更新」，细节 `03-openlist-api.md` §9。**2026-11-14 用户截图需求批次 UI 改造已收官并真机验收（主题两态/搜索页/曲库歌手轮播/迷你条定稿/播放页互换/SleepDialog/歌词 ShuYin 化/默认封面，含 release R8 包复验，见 `05-progress.md` 同日条）**。**同日晚间 HOTFIX**：修复 21:50 装包后的启动闪退（标题栏 Backdrop 自引用 → RenderThread 栈溢出），标题栏改回 Haze 自建源，debug 包三次冷启动 / Tab 切换 / 滚动无崩溃且磨砂生效，见 `05-progress.md` 同日 HOTFIX 条。**2026-11-15**：播放页手势收敛——删「上滑开队列」，下滑改为「整页一起下滑、后面正常露出曲库」，顶部异色块根因定位为 `PlayerBackdrop` 1.18 倍缩放溢出（根 Box 加 `clipToBounds()` 修掉），真机（PGEM10 / Android 15）深/浅两主题复验通过，详见 `05-progress.md` 2026-11-15 条。
4. **什么能碰**：`app/src/main/java/com/msop/player/ui/` 是正式 UI，可以直接改。`feature/*/ui/` 仍然空着——UI 没有按模块拆，全部集中在 app 模块（原因见 §5 的说明）。
5. **可以并行做什么**：剩余可选项互不依赖。远程音源主通道是 **OpenList 原生 REST**（不是 Subsonic），别按旧文档写客户端；WebDAV 是独立第三音源。
6. **最容易踩的坑**：AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`；URL 拼接**不要**用字符串；Room 迁移**不要**开 destructive；`ffmpeg -metadata lyrics=` 写的是 `TXXX:USLT`；**KDoc 里出现 `/*` 会开启块注释**（写 `/@rest/*` 这种字面量会一路吞到文件末尾）；Media3 1.11 已删 `setEnableAudioOffload`；Android SDK 里**没有** `NET_CAPABILITY_METERED`（只有 `NET_CAPABILITY_NOT_METERED`）；缓存 key 绝不能用带 tempauth 的 raw_url；**标题栏禁止 `drawBackdrop(LocalLayerBackdrop.current)`**——`HazeTitleScaffold` 位于外壳 `layerBackdrop()` 捕获的内容子树内，消费同一 backdrop 会造成 RenderNode 自引用递归、RenderThread 栈溢出闪退（2026-11-14 实测 512 帧 `prepareTreeImpl` 后 SIGSEGV）；标题栏固定走 Haze 且**自建** `hazeSource`（共用外壳共享源会让滚动后捕获层不刷新、透字）；**播放页下滑位移只能整层搬、且必须 `clipToBounds()`**——`PlayerBackdrop` 的模糊封面带 `scale 1.18`，绘制会溢出节点上下各约 9% 屏高（1600px 屏约 144px），遮罩只铺面板自身范围，所以面板顶边之上会露出约 100px **未经压暗的亮色模糊带**（2026-11-15 用户截图报的「顶部异常色块」，真机像素采样确认）；根 Box 上必须同时有 `.offset{…}`（整层跟手，用户要求「整页一起下滑、后面露出曲库」）和 `.clipToBounds()`（裁掉溢出）；**不要再往顶部盖 Void 实底**；上滑已按用户指令**不做任何事**（不要恢复「上滑开队列」）；**扇形菜单不要用 `Popup`**——`Popup(alignment, offset)` 的窗口会被系统钳制到屏幕内，实测整组项落到播放键与底部 dock 上，必须走「页面内全屏覆盖层 + 按钮 `boundsInRoot` 上报锚点」；`PlayMode` 现有 4 档（含 `REPEAT_ALL`），任何 `when(PlayMode)` 都要补分支；**进度条有两份状态**——`PlayerSheet.dragValue`（拖动预览，-1 才回落）与 `LibraryViewModel._positionMs`（真实位置，**只在播放中**每秒轮询），只改一个修不掉「切歌后进度条停在上一首位置」：抬手要无条件 `onCommit`、按 `track.id` 归位 `dragValue`、并在 ViewModel 的 `init` 里跟随 `currentTrackId` 同步 `_positionMs`；**封面↔歌词用 `HorizontalPager`，竖直收起手势只能挂在封面页**——歌词页的 LazyColumn 要自己消费竖直滚动，外层再挂 `detectVerticalDragGestures` 会抢事件把页面误收起；**扇形菜单必须保持「等半径圆弧」**——用户 2026-11-15 明确回退过「链式累加」方案（第一项贴按钮但第三项跑到 178dp，扇形变成一条链），要拉近只能**扩大张角**：等半径弧相邻弦长 `2R·sin(Δθ/4)`，R=130dp/55°/52dp 与 R=86dp/80°/52dp 都能不重叠，后者统一近 34% 且形态不变。**二级页 overlay 不能放进 `HazeTitleScaffold` 的 content 里**——主内容压缩渐暗的 `graphicsLayer` 会连 overlay 一起压成半透明（用户报「文件夹打开雾蒙蒙」），overlay 必须是根 `Box` 的兄弟节点；**`LazyListItemInfo.offset` 不含 `contentPadding`**——实际绘制 Y = `contentPadding.top + offset`，算歌词 36% 锚点时必须把 top padding 加回去，否则当前句稳定停在 ~60%。
7. **排障工作法（用户指定 2026-11-14，许可边界 2026-11-15 解除）**：遇到实现问题（UI 效果、API 用法、难 bug）**先检索 GitHub 开源项目找现成解法，能复制就复制**，不要凭空自拟。**2026-11-15 用户指令：「删掉 GPL-3.0 约束，项目是开源的」**——Apache-2.0 / MIT / BSD / GPL-3.0 等开源项目的代码与资源均可直接采用，只需在完成记录里标注来源与许可证（见 D26）。注意跨语言/跨平台项目（如 BetterLyrics 是 C# / WinUI3）源码无法搬到 Kotlin / Compose，能照抄的是视觉形态与实现思路。

## 0.5 第十五轮（2026-11-16 深夜，r16/r17/r18 = A 组补验 + 耳机图标）

> 设备仍是**安卓模拟器**（`emulator-5554`，Android 15 / SDK 35 / 900×1600）。本轮没有新功能，只做「A 组五项补验」+ 一条图标替换；**唯一代码改动是 1 个 XML**。

1. **P3-04 缓存命中 → `[x]`**：三条验收全拿到设备证据。① 清理后归零：设置页 `106.8 MB · 5 首` → 二次确认 → `0 B · 0 首`。② 同曲重播命中：首播一次 `远程缓存未命中，解析播放地址 uri=…`，重播 12 秒窗口内**零未命中**（`RemoteCacheDataSource.open()` 用 `CacheDecision.stableKey(uri)` 作 key，剥掉带签名的 `?`/`#`）。③ 计费网络占用不涨：用 D29 的手法造出 metered Wi-Fi，播 30 MB 未缓存 FLAC，日志 `远程缓存策略 wifiOnly=true metered=true 写入=false`，`du` 前后都是 `2364 KB`；已缓存的 `指尖缠绕 - ZIV` 在 metered 下继续正常播、占用不涨。整首只缓存约 1.3 MB 属 `SimpleCache` 环形分配的正常行为，不是全量缓存。
2. **P7-04 备份与恢复 → `[x]`**：SAF 导出 → 删歌单 → 导入复活完整闭环 + 坏 zip 用例。导出落 `/storage/emulated/0/msop-music/msop-backup-20261116-2133.zip`（888 B），zip 内 `manifest.json`(129 B) / `playlists.json`(277 B) / `settings.json`(637 B)；删掉 `BatchTest` 后导入同一 zip → 「已恢复 2 个歌单、1 首曲目；OpenList / WebDAV 配置已恢复」→ 歌单页三项齐全。坏 zip：推一个只含 `notes.txt` 的 zip 导入 → 「导入失败：不是 msop 备份：缺少 manifest.json」，原数据未动。**口径修正**：验收行原写「密码不导出」，实现是**导出明文 `password`**（设置页副标题与成功弹窗都写「含服务器密码 / 含全部设置与服务器凭据」），已按实现改写验收行，不当缺陷。**坑**：`shell` 在 `/sdcard` 顶层 `find/ls` 看不到 SAF 目录，必须用全路径 `/storage/emulated/0/msop-music/`。
3. **P7-03 断网保护 → `[~]`（三项实测、缓冲提示仍缺）**：用 D29 的设备端 `nohup` 脚本在断网窗口自取证。① 云端曲 `指尖缠绕 - ZIV` 播到 1:10 断网 → 播放页顶栏橙点「网络已断开」+ 浮层「网络已断开，播放已暂停」，播放键变 ▶，日志 `网络断开，远程曲目已自动暂停`。② Wi-Fi 恢复 → 顶栏「已暂停」、提示消失、停在 1:10 **不自动续播**，日志 `网络已恢复，等待用户手动继续播放`。③ 本地 FLAC `Love Paradise` 断网 → 继续播（0:32 仍在走）、日志无网络行。**「缓冲中…」/「网络较慢，仍在缓冲…」未验**：本机没有「不掉 adb 又能限速」的手段（系统代理只能造连接失败 → ERROR，不是 BUFFERING）。
4. **P4-02 搜索合并 → `[x]`**：同一关键词 `Music` 返回 `36 首`，本机行（`世界が終るまでは…` / WANDS · `Music` 专辑）与云端行（`musicweb` 歌手）同屏；`ZIV` / `Nivis` 只命中云端条。`LibraryViewModel.searchResults` = `combine(_searchQuery, tracks)` + `debounce`，`tracks` 本身含 OPENLIST / WEBDAV 行。
5. **P5-01 远程歌词 → 仍 `[~]`，查明是「完全未实现」**：`LyricsRepository.readEmbedded()` 对 `track.source != LOCAL` 直接 `return null`；旁挂 `.lrc` 只查 SAF 兄弟文件 / 授权树 / 裸路径，云端 uri 走不到。素材也没有：OpenList `/yd/musicweb` 递归 60 条 **0 个 `.lrc`/`.ttml`**，WebDAV `/cd18/Music` PROPFIND 33 href **0 个歌词文件**。要落地 = 新写「远程旁挂 lrc 拉取 + 缓存」+ 往服务器放测试 `.lrc`（改用户服务器，须先拍板）。
6. **通知栏 / 锁屏小图标换耳机**（`feature/player/src/main/res/drawable/ic_stat_msop.xml` 整文件重写）：音符 → 纯白描边耳机矢量，形态照桌面图标 `app/src/main/res/drawable-nodpi/msop_icon.png`——头梁弧 `M4.2,15.2 A7.8,7.8 0 0 1 19.8,15.2`（strokeWidth 2.2 round cap）、左右耳罩 `4.2,15→4.2,18.6` / `19.8,15→19.8,18.6`（3.4）、三根电平柱 x=9.6/12/14.4（1.6），全部 `fillColor="#00000000"`。`PlaybackService.kt` 第 70 行 `notificationProvider.setSmallIcon(R.drawable.ic_stat_msop)` 不变（1.11 Builder 无此方法，小图标是实例方法），**Kotlin 零改动**。装机后通知栏与锁屏都确认是耳机剪影（图标 6 倍放大核对）。

验证：`:app:assembleDebug` **21,143,938 B** 装机；截图 `%TEMP%\r13shots\r16-01…r16-18*.png`（通知栏/锁屏/缓存/搜索）、`r17-01…r17-10*.png`（备份导出→删除→导入恢复）、`r18-01…r18-18*.png`（计费网络、断网三场景、合并搜索）。

## 0.4 第十四轮（2026-11-16 夜，r14）

> 设备仍是**安卓模拟器**（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。本轮 5 条用户反馈，全部改完并逐条实测。

1. **搜索 / 设置页返回后导航栏按钮不跟随**（`ui/components/ShuYinNavBar.kt` 的 `LiquidGlassNavBar`）：根因是 `LaunchedEffect(Unit)` 里 `snapshotFlow { selectedTabIndex() }` 捕获的是**首次组合那个 lambda 实例**，闭包里的 `currentDestination` 永远是旧值，系统 `popBackStack` 改了路由也读不到；另配的一段 `LaunchedEffect(dampedDragAnimation)` 又把「外部导航」误当用户点击再 `onTabSelected` 一次，与 `popBackStack` 打架。改法：`rememberUpdatedState(selectedTabIndex/onTabSelected)` 取最新 lambda；外部导航效应只 `currentIndex = it` + `animateToValue`，**不再回调 onTabSelected**；用户点击入口显式 `currentIndex = index; currentOnTabSelected(index)`；`onDragStopped` 按「是否真拖动 / target 是否等于当前项」分流。实测设置页返回后滑块回到「音乐库」。
2. **三点菜单「下载歌曲」提到第一**（`ui/components/TrackRow.kt`）：原在末尾的下载块整体上移到 `DropdownMenu {` 后第一位，云端曲顺序变成 下载歌曲 / 歌曲信息 / 加入歌单 / 移除曲库。
3. **文件夹云端只显示设置里填写的路径**（`LibraryViewModel` + `LibraryScreen`）：构造注入 `ServerConfigStore` / `WebDavConfigStore`（SharedPreferences 实现、**无可观察 Flow**，所以只能主动拉），新增 `cloudRoots: StateFlow<Map<String,String>>` + `refreshCloudRoots()`，键 `tag:profileId`、值 `rootPath`；`LibraryScreen` 用 `LaunchedEffect(Unit)` 刷新。`folderOf(track, cloudRoots)` 先取 `remoteProfileId(uri)`（`scheme:profileId:/path` 第二段）查表，命中就用 `normalizeCloudRoot(rootPath)`（空串或 `/` → `/`），查不到才回退旧的按 uri 子目录切分。实测文件夹 Tab 由「一堆云端子目录」收敛成 `OpenList /yd/music`(23) + `OpenList /yd/musicweb`(42) + `WebDAV /cd18/Music`(181)（两台 OpenList 各一条，因为两台配置的 rootPath 不同）。
4. **歌单「移动」删掉**（与「加歌单」重复）：`TrackMenuActions` 去掉 `currentPlaylistId` / `onMoveToPlaylist` 两个字段，菜单块删除，`TrackPickerState` 保留但弹窗标题固定「加入歌单」。批量操作条的「移动」保留（那是多选语义）。
5. **收藏歌曲菜单增加「加入歌单」**：`PlaylistScreen` 里收藏类目 `onAddToPlaylist` 走 `PlaylistViewModel.addTracksToPlaylist`（普通歌单仍不给）。实测收藏曲菜单 = 歌曲信息 / 取消收藏 / 加入歌单 / 移除曲库，选 BatchTest 落库成功。**坑**：验证写库必须把 `databases/msop.db` **和 `msop.db-wal` 一起**拉到本机再看——只 `cat msop.db` 读到的是 checkpoint 前的旧快照，会误判「没写进去」。

验证：`:app:assembleDebug` **21,143,938 B** 装机逐条实测（截图 `%TEMP%\r14shots\r14-01…r14-16*.png`），`test` 0 失败。**补验（r13 欠的下载卡两态）**：DONE = 绿 ✓ +「已保存到 …」、FAILED = 红 ✕ + 红字错误，均已实测截图（`%TEMP%\r13shots\r15-bf-04/-08.png`、`r15-fail-05.png`）。制造失败别用飞行模式——`cmd connectivity airplane-mode enable` 会把 MuMu 的 adbd 打离线、`adb devices` 全 offline，只能靠人手关飞行模式恢复；改用 `settings put global http_proxy 127.0.0.1:1` 制造连接失败，验完 `settings put global http_proxy :0` 还原。设备 adb 端口现在是 `emulator-5554` / `127.0.0.1:16384` / `7555`（旧 `127.0.0.1:5555` 已不在）。

## 0.3 第十三轮（2026-11-16 晚，r13）

> 设备仍是**安卓模拟器**（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。本轮 7 条用户反馈，6 条已改并实测，第 7 条（通知栏/锁屏）等用户选型。

1. **曲库重复（截图里 `Love Paradise` 三次）**：两个独立成因，都在 `ui/library/TrackMerger.kt`。旧实现只做「云端命中本地即丢云端」，同源重复完全不处理。现在 ①键从单一 `标题|歌手` 改成**候选键集合**——本机条是「歌名 + 歌手」两列，云端条常把 `歌名 - 歌手` 整串写进标题、歌手留空，于是额外生成一个拆开后的键，两边有交集即同曲；②同源去重按 `键|时长|体积` 只留第一条（并不同名不同版本）。实测设备曲库 279 → 264。**移除曲库也要连坐**：列表只有一行，库里可能还有同一首歌的第二份，`PlaylistViewModel.removeFromLibrary` 现在用 `TrackMerger.twinsOf` 把同键且（时长差 ≤1s 或体积相同）的条目一起写墓碑，恢复也一起复原（否则「移除的又回来了」）。
2. **封面白块**：根因是 `coverUri` 指向 `files/covers/<sha1>.jpg`，重装 / 清缓存后文件没了，而 `warmCovers()` 只吃 `coverUri IS NULL` 的条目，「有 URI 没文件」永远修不好。两层修：数据层 `LocalMetadataEnricher.repairBrokenCovers()`（`CoverCache.existing()` 判存活 → `TrackDao.clearCover` → 立刻 `warmCovers`），首屏预热协程改成先修再补；UI 层 `TrackRow.usableCoverUri()` 对 `file://` 先查文件存在，不存在就当无封面走程序化封面，不再留白块。实测本地 17 首封面全部回来（云端封面的空白属于网络，`/@rest/getCoverArt` 拿不到图）。
3. **下载没反馈**：原来同步阻塞下完才弹一句 toast。新增 `core/network/DownloadCenter`（`@Singleton`，RUNNING/DONE/FAILED + 字节进度 + `StateFlow`，完成停 4s、失败停 6s 自动移除，同一首在途去重），`RemoteFileDownloader.download` 加 `onProgress` 回调（64KB 缓冲、每 256KB 报一次，`Content-Length` 缺失时 total=null）。UI 是新组件 `ui/components/DownloadCard.kt` 的 `DownloadTaskCard`，挂在 `MsopApp` 底部浮层迷你条上方（迷你条玻璃投影外扩 20dp，故中间留 12dp 否则盖住进度条）。实测点「下载歌曲」立刻出卡，`198.5MB / 212.0MB · 93%` 一路走到底，落盘 `Android/data/com.msop.player/files/Music/…flac`（222,306,561 B）。
4. **删掉下载路径提示词**：`SettingsScreen.kt` 里那段常驻 `SettingsHint` 整块移除（只留注释说明是用户指令）。
5. **「已移除歌曲」页点空白会误触播放**：二级页 overlay 是根 Box 的兄弟节点，页面根节点原先没有 pointer 处理，空白点击直接穿透到下面仍在的曲库列表。修法是 `Surfaces.kt` 的 `EnteringPage` 根 Box 上挂无涟漪 `clickable` 吃掉空白点击。实测移除一首 → 进已移除页 → 点空白，迷你条与播放状态不变。
6. **文件夹加云端**：`folderOf()` 原来只取 `localPath`，云端 `localPath=null` 且 `FolderList` 还有 `filter { localPath != null }`，整批云端被排除。现在文件夹键 = `来源标签:目录`（`local:` / `openlist:` / `webdav:` + 目录，云端目录从 `scheme:profileId:/path` 的第三段取），列表行副标题显示「本机 / OpenList / WebDAV · 路径」，排序按来源 + 目录，云端行图标走 `Palette.BlueLight`。实测「文件夹」Tab 同时出现本机 Music / msop-music 与 OpenList 各目录。
7. **通知栏 / 锁屏：用户拍板方案 B，已换回系统默认媒体大卡**。检索 `wangchenyan/ponymusic`（`DefaultMediaNotificationProvider`）、`cwuom/NeriPlayer`（原生 `Notification.MediaStyle`）、`cosmictaserdev-creator/Convx`（用户给截图的来源，源码里同样只有 `DefaultMediaNotificationProvider` + 小图标，**没有任何自绘卡**）——结论：主流项目都不自绘通知，截图那两张是系统 skin 渲染的媒体卡。现 `PlaybackService` 改回 `DefaultMediaNotificationProvider.Builder(this).setChannelId("msop_playback_v3").setChannelName(R.string.notif_channel_playing).setNotificationId(1001).build()` 再 `setSmallIcon(R.drawable.ic_stat_msop)`（注意 1.11 的 Builder **没有** `setSmallIcon`，小图标是实例方法），自绘的 `ShuYinNotificationProvider.kt` 与 `res/layout/notif_player_small.xml` 已删除。实测通知栏与锁屏都是系统大封面卡（大图封面 + 歌名/歌手 + 上一首/播放暂停/下一首 + 可拖进度），截图 `%TEMP%\r13shots\r13-27-notif.png`、`r13-28-lock.png`。

## 0.2 第十二轮（2026-11-16 晚，r12-b）

> 设备仍是**安卓模拟器**（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。本轮 12 条用户需求，全部实测通过。

1. **「移除曲库」可恢复且重扫不复活**（本轮最大的结构改动）：新增 **Room v5** + `MIGRATION_4_5`，只加一张墓碑表 `hidden_tracks(id TEXT PK, removedAt INTEGER)`；`tracks` 表与实体**一点没动**。原因：曲目入库是 `@Upsert` 整行覆盖，在 `TrackEntity` 上加 `removed` 列会被重扫重建成默认值而失效——独立表不参与 upsert，这条是方案选型的硬约束。`TrackDao` 的四个展示查询（`observeAll` / `observeAllByAddedDesc` / `observeBySource` / `observeBySourceAddedDesc`）统一加 `AND id NOT IN (SELECT id FROM hidden_tracks)`；扫描路径（`allIds()` / `upsertAll`）不过滤，所以条目还在库里。`PlaylistViewModel.removeFromLibrary` 从 `deleteById` 改为「清歌单关联（`PlaylistDao.removeTrackFromAllPlaylists`）+ 清收藏 + 写墓碑」；`restoreToLibrary(id)` 删墓碑行即恢复，不用重扫。**实测曲库 279 → 278 → 重扫仍 278 → 已移除页恢复 → 279**。
2. **「已移除歌曲」页**：曲库右上角三点菜单新增入口（`LibrarySecondary.RemovedTracks` 二级页，`ui/library/RemovedTracksScreen.kt`），行内「恢复」胶囊。空态文案与副标题都写清「点恢复放回曲库」。
3. **三点菜单语义**：曲目菜单「查看文件信息」改名「歌曲信息」（`TrackInfoDialog` 从 `private` 放开，播放页也复用）；「移除曲库」加 `AlertDialog` 二次确认（写明「文件不删 / 重扫不回来 / 去已移除歌曲恢复」）；收藏虚拟歌单的曲目菜单**删掉「从歌单移除」**（`onRemoveFromPlaylist` 与行级 `onRemove` 都给 null，取消收藏仍由「取消收藏」承担）。
4. **播放页顶栏菜单**：删「收藏」→ 换「歌曲信息」+「下载歌曲」（后者仅云端曲出现，`PlayerSheet.onDownload` 由 `MsopApp` 用 `LibraryViewModel.download` 注入）。收藏入口只剩控制条那颗爱心。
5. **歌词偏移改成浮层**（用户 bug：`AlertDialog` 把歌词整片盖住没法边调边对齐）：照 `music-web-cf-main` 的 `buildCalPop()`（`index.html` 15407–15445 + CSS 2756–2830）重做成**页面内非模态玻璃卡** `LyricsOffsetPanel`——标题 + 当前值 + ✕ + 说明 + `Slider(-10..+10s)` + 五个步进钮（−0.5/−0.1/归零/+0.1/+0.5），竖屏贴歌词区底部、横屏贴歌词列右下；不加遮罩，上面歌词照常可见可点。
6. **横屏迷你玻璃卡**：`LyricsGlassControls` 的白 0.34→0.12 渐变改成与 `MiniPlayer` 完全相同的垫色（浅 `FAFAFA 0.72` / 深 `121212 0.74`，描边降到 0.42/0.12），两处看过去是同一块玻璃。
7. **歌单副标题只留曲目数**：`PlaylistScreen` 的标题栏副标题与封面信息卡都去掉「· N 分钟」（`AlbumDetail` 的分钟胶囊不动，用户只说歌单）。
8. **设置页**：下载路径行右侧按钮「选择」→「选择目录」/「更换目录」，下面加常驻说明段（新 `SettingsHint`：选目录会发生什么、不选走应用私有 Music、下载完重扫入库）；顶部应用卡文案改成「本机 + OpenList + WebDAV」；两个 ConfigScreen 的「请求并发数」加 `note` 常驻说明（`ConfigField` 新增 `note` 参数——placeholder 只在空框时可见，填了数就消失，所以解释必须常驻）：留空=4、填 1 最稳、内网 8~16、公网被限流/429/5xx 降到 2~3、范围 1~16。

验证：`test` 254 例 / 0 失败；`:app:assembleDebug` **20,287,537 B** 装机逐条实测；`:app:assembleRelease` **3,595,218 B**（`BUILD SUCCESSFUL in 8m 17s`）。设备 DB v4→v5 迁移实测成功、曲库 279 首未丢。截图 `%TEMP%\r12shots\r12-01…r12-26*.png`。细节见 `05-progress.md` 2026-11-16 第十二轮条。

## 0.1 第十一轮（2026-11-16 晚，r12）

> 设备仍是**安卓模拟器**（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。

已落地并实测通过：

1. **三点菜单收敛**：曲库/歌手曲目菜单删「收藏」（`TrackMenuActions.showFavoriteItem` 默认 false，只有收藏虚拟歌单给 true）；播放页顶栏共用三点菜单与横屏 `LyricsOverflowMenu` 都删「重新加载歌词」（重载仍在歌词工具条的 ⇄ 钮）；「加入歌单」图标换 `PlayerIcons.PlaylistAddCircle`；歌单详情曲目菜单删「加入歌单」。
2. **下载**：`RemoteFileDownloader`（`StreamUrlResolver` → OkHttp 直连；SAF `createDocument` 重名加序号，否则 `getExternalFilesDir(DIRECTORY_MUSIC)`）；曲目菜单「下载歌曲」只在云端曲出现；设置页「播放」组「下载路径」行（`DownloadConfigStore` + `OpenDocumentTree` + 持久授权 + 恢复默认）。
3. **并发数**：`ServerConfig` / `WebDavConfig` 的 `concurrency`（0=默认 4，钳 1..16）；两个 ConfigScreen 加「请求并发数（可选）」；两个 Syncer 的目录 BFS 与 `fillMissingDurations` 都按并发并行（`Semaphore` + `AtomicInteger`）。实测两台 OpenList「写入 83 首 · 补全 11 首 · 30586 ms」。
4. **歌单批量**：详情页「选择歌曲」→ 多选 `SelectableTrackRow` + 底部玻璃 `BatchActionBar`（移除 / 移动 / 加歌单 / 下载）+ `BatchPlaylistPicker`；DAO 加 `@Transaction removeTracks` / `moveTracks`；收藏虚拟歌单只有「选择歌曲」+「取消收藏」。
5. **爱心→收藏列表**：根因是两套数据（爱心写 SharedPreferences、歌单读 Room `favorites`）。`LibraryViewModel` 改读写 `FavoriteDao`（`observeIds()` / `toggle`），实测点爱心后收藏详情「1 首 · 冬眠」。
6. **本地优先合并**：`ui/library/TrackMerger` 按「规范化歌名 + 歌手」比对，命中丢云端条保本机条（仅展示层，DB 两边都在），曲库 283 → 278。
7. **玻璃**：迷你条/导航栏垫色 0.55/0.60 → 浅 `FAFAFA 0.72` / 深 `121212 0.74`。
8. **横屏歌词右移**：`LandscapeLyricsPane` 补 web `.player-panel` 的 4% 内边距 + 列间距 4.5%→7.5%（右留白 10.4%→9%），正文左缘 40.5% → ≈42.5% 屏宽（对齐用户附件 2048×922 的 43.9%）。
9. **通知栏 / 锁屏播放器**：`feature/player/…/notification/ShuYinNotificationProvider` 实现 Media3 `MediaNotification.Provider`，按用户附件两张参考图重做成**自绘紧凑卡**（封面取色的圆角底图 + 48dp 方形封面 + 歌名/歌手 + 上一首/播放暂停/下一首，无进度条），字色按 `Color.luminance(tint)` 自动取深/浅；**频道必须自建**（换 id 才能改重要性）：`msop_playback_v2` / IMPORTANCE_DEFAULT / `setSound(null)` / 锁屏可见。**已踩明的死路**：`MediaStyle` 与 `DecoratedMediaCustomViewStyle` 一律被 SystemUI 接管成「系统大封面 + 波浪进度条」大卡，`setCustomContentView` 被忽略，改 `setColor`/`setProgress`/动作数都不改布局；能用自绘视图的只有**普通通知 + `DecoratedCustomViewStyle`**（代价：系统不再当媒体通知，传输键只能靠自绘视图里的 `PendingIntent`，且卡外多一圈系统白底）。`setColorized(true)` 在锁屏被系统重新套白底 → 底图必须自己画（`cardBackground()`）。实测截图 `%TEMP%\r12shots\74-shade14.png`（通知栏）、`75-lockcard7.png`（锁屏）。
10. **旧歌单已清空**（设备 DB `playlists/playlist_tracks/favorites = 0`，`tracks` 283）。
11. **三件遗留已补验**：SAF 自定义下载目录实选 `msop-music`（`DocumentsContract.createDocument` 落盘 22,658,255 B，扫描后曲库多一条「Love Parade · msop-music」）；WebDAV 编辑器并发数实测发现**写入漏字段**（`WebDavConfigStore.upsert` 没 `putInt(FIELD_CONCURRENCY)`，输入框形同摆设，已修）→ 现 `p_p1_concurrency=8` 落盘；`:app:assembleRelease` 已重打 **3,578,834 B**。全量 `test`（debug+release 两变体）0 失败。

验证：`test` 28 类 / 235 例 / 0 失败；`:app:assembleDebug` 20,016,296 B、`:app:assembleRelease` 3,578,834 B 均构建通过。细节与遗留见 `05-progress.md` 2026-11-16 第十一轮条 + 追加条、`03-openlist-api.md` §9.6–§9.8。

## 1. 项目是什么

一个只做两件事的安卓音乐播放器：读本机音频 + 对接 OpenList 音乐库。目标是「自己天天用」，不是「做一个产品」。

来源：想做一个「只读本机文件 + 对接自己的 OpenList」的安卓播放器，供自己日常使用。
项目为**独立实现**，Kotlin + Compose + Media3 从零编写，不含任何第三方播放器的代码或资源；
界面与交互按自己的使用习惯设计，不追求还原某个既有产品。

## 2. 当前状态

| 项 | 值 |
|---|---|
| 阶段 | **P0–P4 完成 · P3 全链路含边播边缓存（原生 REST 流播 + Subsonic 元数据/封面 + SimpleCache）· P5-03/05 完成 · P6-02/03 完成 · P7 进行中（8 完成 / 3 待验证 / 1 阻塞）**。P7 视觉三连（09 Haze 标题栏、10 液态玻璃导航、11 iOS 式转场）全部完成并真机验收（含 release 包）；剩余 P7-03/04/07 需远程资源、P7-12 缺 NDK 阻塞。另有 P3-04 缓存命中真机复验、P5-04 性能、P6-01 清单走查 |
| 代码量 | Kotlin 源码 **158 个**（main 130 + test 28） |
| 测试 | ✅ `.\gradlew.bat test` → **28 个测试类 / 235 个用例，0 失败**（按类名去重；debug + release 双变体都跑）。含 `ServerUrlTest` 11、`EqualizerTuningTest` 14、`RemoteTrackMapperTest` 9、`QueueReorderTest` 6、`SubsonicMetadataTest` 13、`MediaCachePolicyTest` 5、`TtmlParserTest` 13、`TrackQualityTest` 5、**`DesktopLyricsTextTest` 6**、**`TrackIndexTest` 7**、`WebDavUrlTest` 7、`WebDavMultiStatusParserTest` 5、`WebDavTrackMapperTest` 5 |
| 真机验证 | ✅ 历轮已验：三 Tab、扫描入库、通知、SAF、幽灵清理、标签/封面、MP3/WAV/FLAC 播放、后台/通知栏/媒体键、循环与随机、耳机拔出、来电打断续播、歌单持久化、播放页手势、歌词、封面清理。**本轮新增**：OpenList 配置/测试/同步（23 首 454ms）、远程 FLAC 播放与拖动 seek（68s→208s）、均衡器预设实时生效（设备 5 段）、目录管理弹窗、release 包全套回归；**Subsonic 启用后**：23 首全部补全艺术家/专辑/封面（8476ms）、真实 JPEG 封面（129KB，Coil 缓存 180KB）、播放 `state=3` 元数据正确。**P3-04 未真机复验**（需远程曲目，当前设备只有本地 509 首；步骤见 `03-openlist-api.md` §8.4）。**2026-11-14 本轮新增（PGEM10 / Android 15 / 900×1600，APK 与本地 debug 包 md5 一致）**：P7-08 桌面悬浮歌词全套（服务/窗口/逐行更新/拖动吸附/锁定/样式/关闭/杀进程不重启/重启按需恢复）；P7-01 进度条（点 50%→60003ms、拖 20%→23634ms）；P7-02 播放恢复（强停重启 `PAUSED(2)/43340ms` 不出声、点播放续播）；P7-05 TTML（四行、逐字高亮、点行→45000ms）；P7-06 音质（v3、509 首入库、弹窗 `16bit/44.1kHz/129kbps`）。**2026-11-15 本轮新增（emulator-5554 / 900×1600 / 深色主题）**：文件夹详情页雾感修复——「文件夹」Tab → Music 目录 → 整页不透明、自带 Music 大标题、无渐暗/无残留「音乐库」标题栏；歌词页照抄 Primuse——当前句稳定在视口 36%、540ms 平滑跟随换句、非当前句按行距递增模糊+压暗、上下 12% 渐隐、手动滚动后 3 秒恢复跟随；深/浅两主题各验一次。**2026-11-16 本轮新增（B 类，`127.0.0.1:5555` / PGEM10）**：五台真实服务器——内网 OpenList `http` 明文二次确认 → 同步 60 首 → 远程流播 `state=PLAYING(3)`；内网 WebDAV `http` 同步 187 首；`https://dav.2dland.cn` 同步 3 首；koyeb 两台（OpenList + WebDAV）在本设备解析到 `[::1]:443` 显示 FAIL（PC 端 5 台全部 200/207，属设备网络）；多台列表 + 启用中徽章 + 状态行、切换启用项 187→3→187（各自计数保留）、删除非启用项曲库仍 60 首。详见 `03-openlist-api.md` §9 |
| 构建状态 | ✅ 2026-11-16（B 类）复跑 `test`（debug 28 类 235 例 + release 19 例，**0 失败**）、`:app:assembleDebug`（**20,021,630 字节**）BUILD SUCCESSFUL 并装机 `127.0.0.1:5555`；`:app:assembleRelease`（3.48 MB 自签名）为第八轮结果，本轮未重打。⚠️ 构建必须 `& "$env:JAVA_HOME\bin\java.exe" -Xmx4g -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain <task> --console=plain`：`gradlew.bat` 在 PowerShell 下静默失败；改了源码仍报 UP-TO-DATE 时加 `--rerun-tasks`；两个 Gradle 会话同时跑会撞 `classes.jar 被占用`，先 `--stop`。SDK：android-37 / build-tools 37.0.0 |
| 版本控制 | ✅ GitHub 公开仓库 <https://github.com/longda33/msop>（默认分支 `main`）。`app/release.jks` 与 `keystore.properties` 已加入 `.gitignore`，**不要提交** |
| UI 状态 | ✅ **视觉重构完成（2026-11-12，Aurora Glass）**：深空墨 + 极光渐变、玻璃卡片、悬浮胶囊导航、程序化封面兜底、全屏播放页重做；三 Tab / 播放 / 队列 / 歌单 / 歌词 / 均衡器 / OpenList 配置行为不变。debug + release 构建通过；**2026-11-14 已在 PGEM10 / Android 15 截图复核曲库、设置、播放页歌词态与桌面悬浮窗，视觉正常**。**同日 UI 改造批次收官（用户截图需求清单）**：主题深/浅两档（默认深色、无跟随系统、切换即时生效且重启保持）；`liquidGlass` 迁 Kyant0 Backdrop 2.0.1（Apache-2.0）；迷你条按参考图定稿两键版；播放页喜欢入顶栏、队列移底部 dock；SleepDialog 预设+自定义；歌词 ShuYin 化；搜索页「共 N 首」删统计块；曲库删随机/艺术家 Tab、增横向歌手轮播；无封面程序化封面。debug + release（R8 磨砂存活）双包真机验收通过（横屏与其余二级页仍待专项走查）。**2026-11-15 收尾**：歌词页按用户指令照抄 Primuse iOS（AGPL-3.0-only）——26sp Semibold 左对齐、36% 锚点、540ms 换句、当前句 1.08 倍、非当前句按距离递增模糊与压暗、上下 12% 渐隐；背景去掉粉色渐变，改透出播放页模糊封面 + 主题感知压暗，深/浅两主题真机验收。文件夹详情页改「主内容压缩渐暗 + 二级页作为兄弟节点全屏覆盖」，雾感与残留标题栏消失 |
| 设计资产 | ✅ **2026-11-12 UI 评审清理**：删除 43 个失效过程截图（20.3 MB）与 v1.0 历史原型 `docs/design/prototype.html`；`07-ui-design.md` 从 484 行旧提案重写为「当前实现说明 v2.0」（真实令牌 / 组件表 / 实测缺口 / P1·P2 优化待办）；3 张当前视觉基线归档到 `../preview/baseline/`；`../preview/index.html` 加历史警示条；移除 3 个零引用渐变并让 `Dim.navHeight` 落地 |
| 下一步 | **P7 视觉三连全部收官（2026-11-14）**。剩余均需外部资源：P7-03 断网保护、P7-04 备份 SAF、P7-07 WebDAV、P3-04 缓存命中（都缺远程音源/服务器）；P7-12 需 NDK 阻塞。可选：P5-04 大库性能、P6-01 清单走查。另见 §8 |
| 本机音乐位置 | 默认 MediaStore 标准目录，可追加 / **移除** SAF 自定义目录 |
| OpenList 部署 | **已实测**：OpenList v4.2.9 公网 HTTPS。Subsonic 后已启用，鉴权只认 `apiKey`，负责元数据与封面；流播仍走原生 REST。详见 `03-openlist-api.md` §6（原生 REST）与 §7（Subsonic） |
| 最后更新 | 2026-11-16（**最新（第十轮 / r11：多台音源同时启用 + 云端时长与封面 + 歌单体系补全 + 歌词页收尾）**，**测试设备是安卓模拟器**（`127.0.0.1:5555`、伪装 `PGEM10` / Android 15 / 900×1600、density 2，`ro.kernel.qemu` 被清空但 `ping 10.0.2.2` 通 QEMU NAT 网关可判定；文档旧描述里的「真机 PGEM10」一律按模拟器理解）：① **多台同时启用**——`ServerConfigStore` / `WebDavConfigStore` 改成 `enabled_ids` 集合（不再单条 `active_id`），配置页行内菜单补「取消启用」，同步时逐台跑并各自 `markSynced(id, own)` 计数（实测 LAN 60 / koyeb 23，汇总「扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 · 清理 0 条 · 26800 ms」）；② **跨服务器去重**——`RemoteDeduper` 按规范化标题合并（保留体积更大者），复同步幂等；③ **云端时长**——OpenList v4.2.9 的 OpenSubsonic 完全不返回 `duration`（`search3`/`getSong`/`getAlbum` 都没有、`album.duration` 写死 0），改客户端读文件头：`core/network/RemoteDurationProbe.kt`（Range 64KB，FLAC STREAMINFO / WAV / MP3 Xing 或首帧 / M4A mvhd，含「ID3v2 synchsafe 跳标签后再判格式」以覆盖「.mp3 实为 FLAC」），OPENLIST 83/83 全部拿到；两个 Syncer 在 upsert 前合并旧 `durationMs>0`，复同步不再被 0 覆盖；④ **koyeb 改走 OpenSubsonic apiKey**（`olmp_fi9…`，REST 前缀 `/@rest`）补艺术家/专辑/封面，索引命中 23 首 → 封面 11 首（`covers|11`，覆盖不均属服务端索引所限）；⑤ **歌单体系补全**（Room v4：`playlists.sortOrder` + `favorites(trackId PK)`）——收藏虚拟歌单置顶（`FAVORITES_ID=-1`）、歌单列表与歌单内曲目都能长按右侧手柄拖动排序（`ui/components/Reorder.kt` 的 `ReorderState`/`ReorderHandle`，整行跟手）、三点菜单全项带图标（查看文件信息/收藏/加入歌单→**已加歌单**/移动到其它歌单/从歌单移除/移除曲库），「加入歌单」在 0 个歌单时也显示（点开提示先新建）；⑥ **歌手改列表点开详情**（不再点了就播），头像 + 播放全部 + 曲目同一套菜单；⑦ **歌词页收尾**——三点菜单并入播放页顶栏（播放/歌词共用，菜单里删掉「切换为横屏」）、点歌词行**不再 seek**、横屏挂 `FLAG_KEEP_SCREEN_ON` 不息屏、工具条与玻璃迷你卡 3 秒自动淡出且悬浮可盖住其它、横屏左封面 + 右歌词 + 玻璃卡压在封面列、系统状态栏隐藏；⑧ **设置页长提示词不再被省略**（`SettingRow` / `SettingSwitchRow` 改 `heightIn(min=66dp)` + 副标题 `maxLines=3`）。设备实测：多台启用/取消启用、跨服务器去重、OPENLIST 时长 83/83、WebDAV 补时长 53/182（该 `/dav` 服务端对多数文件 GET 返回 403，只有 PROPFIND 正常，属服务端限制）、收藏/加入歌单/已加歌单/拖动排序/移动/从歌单移除/移除曲库（`288→287` 曲库条数实降）全部走通。⚠️ 本机不给模拟器写代理：`settings put global http_proxy 10.0.2.2:10808` 只在设备侧生效，**不进项目代码**。详见 `05-progress.md` 2026-11-16 第十轮条。**此前（B 类任务：音源配置多实例化 + HTTP 明文 + 状态 + 手动删除）**：① **多配置**——`ServerConfigStore` / `WebDavConfigStore` 整文件重写为 `profile_ids` 有序列表 + `active_id` + `p_{id}_{field}` 前缀 + 按 profile 的 JWT 与同步计数，旧单配置 `init` 里一次性迁移成 `p1`；② **HTTP 明文**——`network_security_config` 放开 sys 层，真门禁在应用层 `allowPlainHttp` + `PlainHttpNotConfirmed`（见 D27）；③ **状态**——`ServerStatus`（UNTESTED/OK/FAIL）+ 状态点/文案/「曲库 N 首 · 上次同步 …」，同步后回写「已同步 N 首」；④ **手动删除**——行内 ⋮ 菜单（编辑/测试连接/启用并同步/删除）+ 删除确认「只删这条配置，曲库与其它服务器不受影响」+「清除全部」；⑤ **音乐目录 `rootPath`**——内网盘三个目录服务端 500，改从配置的音乐目录起 BFS + 单目录失败跳过（`skippedDirs`）。设备暴露并修掉：主线程网络崩溃（`测试失败：null` = `NetworkOnMainThreadException`，改 `withContext(Dispatchers.IO)`）、`保存并同步` 后 busy 卡死、编辑器结果文案被迷你条压住（上移 + `LocalHazeBottomInset + 28dp`）。模拟器 `127.0.0.1:5555` 五台：内网 OpenList http 60 首（远程流播 `PLAYING(3)`）、内网 WebDAV http 187 首、`dav.2dland.cn` https 3 首、koyeb 两台因设备解析到 `[::1]:443` 显示 FAIL；切换启用项 187→3→187、删除非启用项曲库仍 60 首全部实测。`test` 0 失败、`assembleDebug` 20,021,630 字节。详见 `03-openlist-api.md` §9 与 `05-progress.md` 2026-11-16 条。**此前 2026-11-15（第九轮，用户四条指令）：① 歌词页工具条与迷你控制条改「自动隐藏」**（照 web `HIDE_CLASS` / `showChrome()` 的 3 秒语义，不是第七轮那种「删掉」）——`LyricsScreen.kt` 新增 `CHROME_IDLE_MS=3000` / `CHROME_FADE_MS=380` / `CHROME_TICK_MS=120`、`ChromeVisibility` + `rememberChromeVisibility(paused)`（120ms 轮询 `lastTouch`，暂停/浮层打开时恒显）、`Modifier.wakeChrome()`（Initial pass 只观察 pointer 事件、不消费，所以点歌词 seek 与滚动不受影响）、`ChromeOverlay`（`animateFloatAsState` 380ms 淡出 + 隐藏时 `detectTapGestures{}` 吞点击）；竖屏工具条从「独占一行」改成浮层（`padding top 52dp`，会压住顶部第一条暗歌词，属 web 预期行为），横屏工具条与玻璃卡各包一层 overlay，**3 秒后一起淡出、一碰就回来**；② **横屏照 web 矮窗栅格重写**——`(max-height:560px) and (min-aspect-ratio:1.15/1)` 的 13.4 份栅格（左留白 7.5% / 封面列 25.4% / 间距 4.5% / 歌词 52.2% / 右留白 10.4%），封面 `min(列宽, H*0.56)`、左列 `Center` 分组（封面 + `titleSize`/`artistSize` 公式算的歌名歌手）、歌词行 `lineMaxWidthEm=13`（用 `padding(horizontal)` 等价 web `max-width + margin:auto`，长句照 web 折行）、工具条浮 `TopEnd`（`padding 12/16dp`）、玻璃卡浮 `BottomStart`（`padding start=leadingInset / bottom 14dp / width=coverSize`，正好压在封面列并盖住歌名，与用户附件截图一致），歌词列不再给工具条让 46dp；③ **「播放列表」改名「歌单」并挪到「文件夹」前面**——曲库 Tab 改 5 项「歌曲/歌手/专辑/歌单/文件夹」，`PlaylistScreen.kt` 拆成 `PlaylistTab`（内嵌列表：首行「歌单 / N 个歌单 · 点条目查看或管理」+ 圆形玻璃新建钮，自带新建/重命名/删除三弹窗）与 `PlaylistDetailPage`（`BackHandler` + 删除后 `onBack()`），`LibrarySecondary.Playlists` → `PlaylistDetail(id)` + 页面级 `detailPlaylistId: Long?`，右上角菜单删掉「播放列表」只留「重新扫描曲库」；④ **重扫要反馈**——`SettingsViewModel` 注入 `LocalScanScheduler` 暴露 `ScanState(running/scanned/written/finished/elapsedMs/failed)`，设置页该行按 `WorkInfo` 实时换副标题（「正在扫描… 已扫到 N 首（写入 M 条）」/「扫描完成 · 共 N 首 · 用时 X.X 秒」8 秒后回落默认 / 失败重试提示），点击同时弹 toast「已开始重新扫描曲库…」。模拟器 `127.0.0.1:5555`（900×1600）验证：竖屏 3 秒后工具条整组消失、点头部立刻回来（胶囊 x≈117、三钮靠右，浮在信息行下 y≈290px）；横屏 `SurfaceOrientation: 1`，封面 400×408px（`min()` 取列宽）、歌名粗体、歌手被玻璃卡盖住、歌词 13em 居中、浮层 4 秒后与卡一起淡出且状态栏隐藏；曲库 Tab 行与菜单项、歌单 Tab 空态→新建→详情→添加歌曲→移除→删除回落、设置页 toast +「扫描完成 · 共 18 首 · 用时 0.1 秒」及 8 秒回落全部截图确认；`test` 28 类 235 例 0 失败、`:app:assembleDebug`（19,997,230 字节）通过；截图 `../preview/verification/2026-11-15-r9-*.png`（8 张）。**此前第八轮（用户四条指令）：① 歌词工具条**删掉「全屏」按钮**（`PlayerIcons.Collapse` 那颗 ⤢ 整颗移除，竖屏横屏都没有），`LyricsPage` / `LandscapeLyricsPane` 的 `onExitLyrics` 参数一并删掉；② **横屏隐藏系统状态栏**——新增 `HideStatusBar`（`WindowCompat.getInsetsController` + `WindowInsetsCompat.Type.statusBars()`，行为 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，组件离场 `show` 恢复；只藏 statusBars、留 navigationBars 否则横屏返回手势条一起没）；③ **工具条与迷你条改悬浮**——横屏从「右列里占一行」改成「底层 Row（左封面列 + 右歌词列）+ 上层两 overlay」：工具条 `align(TopEnd)`、迷你玻璃卡 `align(BottomStart)`，歌词列只加 `padding(top = 46.dp)` 让位（照 web `m2l-tools` 的 `position:absolute`）；④ **竖屏逐字胶囊靠左**——`LyricsToolsRow` 新增 `spreadChip`：竖屏 `Arrangement.SpaceBetween`（胶囊左、三圆钮右），横屏仍整组贴右上。模拟器 `127.0.0.1:5555`（900×1600）验证：竖屏胶囊 x≈90、右侧只剩准星/⇄/横屏；横屏 `SurfaceOrientation: 1` 且**顶部状态栏消失**、工具条浮右上、玻璃卡浮左下、歌词首行不被压；回竖屏状态栏恢复；深色横屏复验后切回**浅色**；`test` 28 类 235 例 0 失败、`:app:assembleDebug` 通过；截图 `../preview/verification/2026-11-15-r8-*.png`。**此前第七轮（用户八条反馈）：① 歌词「重复行」是数据层 bug——把设备上 `从前说 - 小阿七.flac` 拉到本机读 Vorbis Comment，发现内嵌 `lyrics=` 是**方括号逐字 LRC**（`[00:44.23]哭[00:44.55]着…`），旧 `LrcParser` 把每个方括号当一行、文本取整句，同一句按字级时间点被复制 20 次；改按「标签之间有没有文字」分流——有文字且 ≥2 截 → 合成一行逐字时间轴（每截一个 `LyricWord`、`wordJoiner = 空串` 否则中文逐字裂开），无文字（`[00:10][00:20]歌词`）→ 保留同一句多时间点多行；这条同时落地**逐字歌词**能力，新增 4 单测；② 横竖屏按钮挪进**独占一行**的工具条（`LyricsToolsRow` = 逐字胶囊 `WordModeChip` + `Target/Swap/Collapse/Rotate` 四个 34dp 圆钮，位置照 web `m2l-tools`；胶囊只在真存在逐字轴时显示），三点菜单**删掉「切换为横屏」**（横屏页删「切回竖屏」）；③ 迷你控制**常驻**——删掉整个 chrome 3 秒淡出（`chromeVisible/chromeAlpha/pointerInput` 全移除）；④ 原实心灰背景框改**液态玻璃**：`LyricsGlassControls` 换白色半透明 `White 0.34→0.12` + 白描边 `0.62→0.18`（墨色半透明实机是灰板），`PlayerSheet` 遮罩降到 `0x33FFFFFF→0x9EF4F2FB→0xF2F4F2FB`（深 `0x40→0xB3 黑→0xE607080D`）并**删掉 `LyricsScrim()`**，整屏透出彩色模糊封面；⑤ 音乐库标题下方「N 首 · 本机 · 云端」删除（`HazeTitleScaffold` `subtitle = null`，`SectionHeader` 那行保留）；⑥ `TrackRow` 的 `DropdownMenu` 从「整行 Box」挪进三点自己的 `Box(30dp)`，弹窗贴三点；⑦ 横屏按 web 5 列栅格 `1fr 3.4fr .6fr 7fr 1.4fr` 重排（`leadingInset 7.5%` / 封面列 `25.4%` / `gutter 4.5%` / `trailing 10.4%`，歌词列从屏宽 37.4% 起），三点作为工具条 `trailing` 并进同一排；⑧ 偏移微调 `LyricsOffsetDialog`（±0.1s / ±0.5s / 归零）。模拟器 `127.0.0.1:5555`（900×1600）深/浅两主题 + 横竖屏 + 逐字开关 + 两处三点菜单全部截图验收（`../preview/verification/2026-11-15-r7-*.png` 共 9 张），验收后把主题切回用户原来的**浅色**；`test`（debug+release 双变体）**28 类 235 例 0 失败**、`:app:assembleDebug` BUILD SUCCESSFUL。**此后第六轮（用户五条指令）：① 歌词页右上角「喜欢」删掉、改成三个点**（`LyricsTrackHeader` 右侧改「横竖屏」+「三点」两颗 36dp 圆按钮；`DropdownMenu` 与三点同居一个 `Box`，由 Material3 以该 Box 为锚点向下弹出——用户要求「弹窗和三点在一块」，实测菜单正好在三点正下方、右边缘对齐；菜单项：收藏/取消收藏、切换为横屏、重新加载歌词）；**② 歌词区加「横竖屏切换」按钮**（新图标 `PlayerIcons.Rotate`，形状取自用户自有项目 `music-web-cf-main` 全屏工具条 `ICON.land` 的 SVG——躺倒圆角手机 + 箭头弧；点一下 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`、横屏页同图标点回 `SCREEN_ORIENTATION_PORTRAIT`，横屏右上角同时补了同款三点菜单）；**③ 歌词显示效果换成 music-web-cf-main 全屏歌词规格（横屏同源复用）**——字号 `clamp(24dp, 3.2·vmin+15dp, 46dp)` 且 `h/4.6` 兜底、行样式 `line-height 1.32 / margin 0 0 .42em / weight 700 / letter-spacing .01em`、非当前句 `scale .82`（窄屏）`.75`（宽屏）→ 当前句 `1.0`、300ms `cubic-bezier(.22,.61,.36,1)`、白色三档 `30%/72%/100%` + 当前句 `0 0 22px` 光晕、**逐行按「到视口中心距离」`(1-d)/0.25` 淡出（不再是整块 DstIn 蒙版，且在 `graphicsLayer` 里算，滚动不触发重组）**、竖屏居中 / 横屏左对齐且缩放锚点跟着从中心挪到左边缘（照 web 原注释的坑）、逐字行「已唱纯白 / 未唱 38%」且不加光晕；**但明确不采用它那套歌词背景色**——继续透出播放页模糊封面（用户第三条指令的后半句）；当前句锚点沿用上一轮用户要求的**正中**（web 是 45%）；`LyricsViewModel` 补 `reload()`（`load()` 同曲幂等早退，不清 id 点了菜单没反应）；**④ 音乐库「全部歌曲」标题随右上角筛选（漏斗）联动**——`TrackList` 收 `filter`，标题/副标题按 `SourceFilter` 换：全部来源「全部歌曲 · 本机 + 云端」/ 只看本机「本机歌曲 · 只显示本机文件」/ 只看云端「云端歌曲 · OpenList / WebDAV」，都带「共 N 首」；**⑤ 三点菜单贴着三点弹出**（同上）。模拟器 `127.0.0.1:5555`（900×1600）验收：筛选三档标题依次「全部歌曲/本机歌曲/云端歌曲」；歌词页三点菜单落在三点正下方；旋转按钮 竖→横（`SurfaceOrientation: 1`）→ 竖（`0`），横屏为「左封面 + 右左对齐歌词 + 迷你条」；深/浅两主题各验一次（深色白字三档 + 光晕、浅色 TextPrimary 三档），验收后把主题切回用户原来的**浅色**；`test` **28 类 231 例 0 失败**（debug+release 双变体）、`:app:assembleDebug` BUILD SUCCESSFUL。详见 `05-progress.md` 同日第六条**。此前第五轮（用户四条指令）：① 迷你播放条改 16dp 圆角矩形（`MiniPlayer.kt` `glassShape` 34→16dp，底部细进度线内缩 20→16dp 对齐新圆角、端点仍不被裁；玻璃/拉伸/频谱未动）；**② 音乐库「歌手」「专辑」位置互换**（`TABS` = 歌曲/歌手/专辑/文件夹，`AnimatedContent` 分支同步换位）；**③ 歌手 Tab 改 Primuse 式列表 + 顶部搜索框**（删 2 列网格 `ArtistGrid`，新增 `ArtistList`/`ArtistSearchField`：44dp 圆形头像 + 「N 张专辑 · N 首」、行高 64dp、48dp 搜索框按名字即时过滤、搜索词提到页面级 `rememberSaveable` 防切 Tab 丢失、排序改「未知艺术家沉底 + 字母序」、点行仍直接播该歌手全部歌）；**④ 歌词页恢复 msop 原始形态并居中**（`LyricsScreen.kt` 整文件重写回 HEAD `PlayerSheet.kt` 的 `LyricsPane`/`LyricLines`：42dp 封面信息行 + 当前句 28sp 加粗居中 + 蓝色柔光、邻行 17/15sp 空间透视淡出（`0.55/(1+0.5·(d-1))` 钳 0.14–0.55）、上下 12% `DstIn` 渐隐、视口**正中**锚点（上下各半屏 padding + `contentPadding` 计入的滚差量算法）、逐字卡拉 OK/点行 seek/手动 5s 宽限照原版；`PlayerSheet` 顶栏与 `PlaybackNotice` 恢复常驻、进度条与控制键在封面·歌词两页都显示；唯一适配是按 D23 主题取墨色，原版写死白色浅色主题不可读）。模拟器 `127.0.0.1:5555`（900×1600）深/浅两主题验收：歌手列表+搜索（`zhou` → 空态、点行起播）、迷你条圆角矩形、歌词页当前句居中（660px vs 视口 685px）、点第 4 行 `position=59947ms`；`test`（debug+release 双变体）与 `assembleDebug` BUILD SUCCESSFUL。详见 `05-progress.md` 同日第五条**。此前：歌词页按用户指令「照抄 Primuse」重写 + 文件夹详情页雾感修复。歌词：参照 `chenqi92/primuse` iOS `LyricsScrollView`（AGPL-3.0-only，D26 允许）——26sp Semibold 左对齐、左右 24dp / 行内 5dp / 行间 12dp、当前句 1.08 倍、已唱行 0.32（深）/0.40（浅）、未来行 0.46/0.56、按行距 +1.25dp 递增模糊（上限 5dp，已唱过再加一档）、36% 视口锚点、540ms `FastOutSlowInEasing`、上下 12% 渐隐、250ms 提前切句、手动滚动 3 秒宽限；背景去掉粉色渐变，改透出模糊封面 + 主题感知压暗（深黑 0.30/0.46/0.60，浅白 0.58/0.70/0.80）。修锚点漂移：`LazyListItemInfo.offset` 不含 `contentPadding`，实际绘制 Y = top padding + offset（之前当前句稳定停在 60%）。文件夹详情：二级页 overlay 从 `HazeTitleScaffold` 内容层挪到根 `Box` 兄弟层，主内容单独吃 alpha 0.55 + scale 0.96，详情页不再被压成半透明、也不残留「音乐库」标题栏。全量 `test` **28 类 231 例 0 失败**、`assembleDebug` 通过；模拟器深/浅两主题验收，测试曲已删、曲库回落 18 首**。此前：图标按确认替换 11 个（Library 否决、Lyrics/Order/Cloud 保持原样）；歌词页曾直接移植用户自有 music-web-cf-main 全屏歌词到 `LyricsScreen.kt`，纯净竖屏删顶部信息行 + 隐藏 PlayerTopBar/PlayerTransport，纯渐变背景不用 blur，工具条与控制条 3 秒一起淡出，模拟器验收通过**。更早本批次：4 项落地 + 2 份预览待确认——音乐库删「最近」Tab、文件夹点击改详情不误播、迷你条进度条内缩 + 统一 #9A8CFF、设置关于文案与备份含明文密码；`../preview/icons-redesign.html` 与 `../preview/lyrics-redesign.html` 两份预览**等用户确认后才动图标代码 / 写歌词 Compose**。**D26：用户指令删除 GPL-3.0 复制约束，项目开源不再按许可证类型设限**；播放页三处修复并验收：① 进度条切歌卡住——`dragValue` 抬手未清 + `_positionMs` 只在播放中轮询，两处都改；② 扇形菜单恢复等半径圆弧形态（第一次误改成链式累加已被用户回退），R=130→86dp、张角 55°→80°，三项仍在同一弧上；③ 封面↔歌词改 `HorizontalPager` 跟手翻页。同日早些：播放页控制条重排（删右下循环、喜欢移到右下无圆圈、左下「顺序」+ JellyFab 扇形菜单、顶栏标题随播放状态、队列拖动手柄换 6 点且整行跟手、定时改 5/15/30 + 手动输入；`PlayMode` 新增 `REPEAT_ALL`）；播放页手势收敛（删「上滑开队列」、下滑整页跟手、顶部异色块 = `PlayerBackdrop` 1.18 倍溢出加 `clipToBounds()` 修掉）。此前 2026-11-14：P7 视觉三连全部完成并验收；全量 27 类 224 用例 0 失败） |真机 `127.0.0.1:5555` 五台：内网 OpenList http 60 首（远程流播 `PLAYING(3)`）、内网 WebDAV http 187 首、`dav.2dland.cn` https 3 首、koyeb 两台因设备解析到 `[::1]:443` 显示 FAIL；切换启用项 187→3→187、删除非启用项曲库仍 60 首全部实测。`test` 0 失败、`assembleDebug` 20,021,630 字节。详见 `03-openlist-api.md` §9 与 `05-progress.md` 2026-11-16 条。**此前 2026-11-15（第九轮，用户四条指令）：① 歌词页工具条与迷你控制条改「自动隐藏」**（照 web `HIDE_CLASS` / `showChrome()` 的 3 秒语义，不是第七轮那种「删掉」）——`LyricsScreen.kt` 新增 `CHROME_IDLE_MS=3000` / `CHROME_FADE_MS=380` / `CHROME_TICK_MS=120`、`ChromeVisibility` + `rememberChromeVisibility(paused)`（120ms 轮询 `lastTouch`，暂停/浮层打开时恒显）、`Modifier.wakeChrome()`（Initial pass 只观察 pointer 事件、不消费，所以点歌词 seek 与滚动不受影响）、`ChromeOverlay`（`animateFloatAsState` 380ms 淡出 + 隐藏时 `detectTapGestures{}` 吞点击）；竖屏工具条从「独占一行」改成浮层（`padding top 52dp`，会压住顶部第一条暗歌词，属 web 预期行为），横屏工具条与玻璃卡各包一层 overlay，**3 秒后一起淡出、一碰就回来**；② **横屏照 web 矮窗栅格重写**——`(max-height:560px) and (min-aspect-ratio:1.15/1)` 的 13.4 份栅格（左留白 7.5% / 封面列 25.4% / 间距 4.5% / 歌词 52.2% / 右留白 10.4%），封面 `min(列宽, H*0.56)`、左列 `Center` 分组（封面 + `titleSize`/`artistSize` 公式算的歌名歌手）、歌词行 `lineMaxWidthEm=13`（用 `padding(horizontal)` 等价 web `max-width + margin:auto`，长句照 web 折行）、工具条浮 `TopEnd`（`padding 12/16dp`）、玻璃卡浮 `BottomStart`（`padding start=leadingInset / bottom 14dp / width=coverSize`，正好压在封面列并盖住歌名，与用户附件截图一致），歌词列不再给工具条让 46dp；③ **「播放列表」改名「歌单」并挪到「文件夹」前面**——曲库 Tab 改 5 项「歌曲/歌手/专辑/歌单/文件夹」，`PlaylistScreen.kt` 拆成 `PlaylistTab`（内嵌列表：首行「歌单 / N 个歌单 · 点条目查看或管理」+ 圆形玻璃新建钮，自带新建/重命名/删除三弹窗）与 `PlaylistDetailPage`（`BackHandler` + 删除后 `onBack()`），`LibrarySecondary.Playlists` → `PlaylistDetail(id)` + 页面级 `detailPlaylistId: Long?`，右上角菜单删掉「播放列表」只留「重新扫描曲库」；④ **重扫要反馈**——`SettingsViewModel` 注入 `LocalScanScheduler` 暴露 `ScanState(running/scanned/written/finished/elapsedMs/failed)`，设置页该行按 `WorkInfo` 实时换副标题（「正在扫描… 已扫到 N 首（写入 M 条）」/「扫描完成 · 共 N 首 · 用时 X.X 秒」8 秒后回落默认 / 失败重试提示），点击同时弹 toast「已开始重新扫描曲库…」。真机 `127.0.0.1:5555`（900×1600）验证：竖屏 3 秒后工具条整组消失、点头部立刻回来（胶囊 x≈117、三钮靠右，浮在信息行下 y≈290px）；横屏 `SurfaceOrientation: 1`，封面 400×408px（`min()` 取列宽）、歌名粗体、歌手被玻璃卡盖住、歌词 13em 居中、浮层 4 秒后与卡一起淡出且状态栏隐藏；曲库 Tab 行与菜单项、歌单 Tab 空态→新建→详情→添加歌曲→移除→删除回落、设置页 toast +「扫描完成 · 共 18 首 · 用时 0.1 秒」及 8 秒回落全部截图确认；`test` 28 类 235 例 0 失败、`:app:assembleDebug`（19,997,230 字节）通过；截图 `../preview/verification/2026-11-15-r9-*.png`（8 张）。**此前第八轮（用户四条指令）：① 歌词工具条**删掉「全屏」按钮**（`PlayerIcons.Collapse` 那颗 ⤢ 整颗移除，竖屏横屏都没有），`LyricsPage` / `LandscapeLyricsPane` 的 `onExitLyrics` 参数一并删掉；② **横屏隐藏系统状态栏**——新增 `HideStatusBar`（`WindowCompat.getInsetsController` + `WindowInsetsCompat.Type.statusBars()`，行为 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，组件离场 `show` 恢复；只藏 statusBars、留 navigationBars 否则横屏返回手势条一起没）；③ **工具条与迷你条改悬浮**——横屏从「右列里占一行」改成「底层 Row（左封面列 + 右歌词列）+ 上层两 overlay」：工具条 `align(TopEnd)`、迷你玻璃卡 `align(BottomStart)`，歌词列只加 `padding(top = 46.dp)` 让位（照 web `m2l-tools` 的 `position:absolute`）；④ **竖屏逐字胶囊靠左**——`LyricsToolsRow` 新增 `spreadChip`：竖屏 `Arrangement.SpaceBetween`（胶囊左、三圆钮右），横屏仍整组贴右上。真机 `127.0.0.1:5555`（900×1600）验证：竖屏胶囊 x≈90、右侧只剩准星/⇄/横屏；横屏 `SurfaceOrientation: 1` 且**顶部状态栏消失**、工具条浮右上、玻璃卡浮左下、歌词首行不被压；回竖屏状态栏恢复；深色横屏复验后切回**浅色**；`test` 28 类 235 例 0 失败、`:app:assembleDebug` 通过；截图 `../preview/verification/2026-11-15-r8-*.png`。**此前第七轮（用户八条反馈）：① 歌词「重复行」是数据层 bug——把设备上 `从前说 - 小阿七.flac` 拉到本机读 Vorbis Comment，发现内嵌 `lyrics=` 是**方括号逐字 LRC**（`[00:44.23]哭[00:44.55]着…`），旧 `LrcParser` 把每个方括号当一行、文本取整句，同一句按字级时间点被复制 20 次；改按「标签之间有没有文字」分流——有文字且 ≥2 截 → 合成一行逐字时间轴（每截一个 `LyricWord`、`wordJoiner = 空串` 否则中文逐字裂开），无文字（`[00:10][00:20]歌词`）→ 保留同一句多时间点多行；这条同时落地**逐字歌词**能力，新增 4 单测；② 横竖屏按钮挪进**独占一行**的工具条（`LyricsToolsRow` = 逐字胶囊 `WordModeChip` + `Target/Swap/Collapse/Rotate` 四个 34dp 圆钮，位置照 web `m2l-tools`；胶囊只在真存在逐字轴时显示），三点菜单**删掉「切换为横屏」**（横屏页删「切回竖屏」）；③ 迷你控制**常驻**——删掉整个 chrome 3 秒淡出（`chromeVisible/chromeAlpha/pointerInput` 全移除）；④ 原实心灰背景框改**液态玻璃**：`LyricsGlassControls` 换白色半透明 `White 0.34→0.12` + 白描边 `0.62→0.18`（墨色半透明实机是灰板），`PlayerSheet` 遮罩降到 `0x33FFFFFF→0x9EF4F2FB→0xF2F4F2FB`（深 `0x40→0xB3 黑→0xE607080D`）并**删掉 `LyricsScrim()`**，整屏透出彩色模糊封面；⑤ 音乐库标题下方「N 首 · 本机 · 云端」删除（`HazeTitleScaffold` `subtitle = null`，`SectionHeader` 那行保留）；⑥ `TrackRow` 的 `DropdownMenu` 从「整行 Box」挪进三点自己的 `Box(30dp)`，弹窗贴三点；⑦ 横屏按 web 5 列栅格 `1fr 3.4fr .6fr 7fr 1.4fr` 重排（`leadingInset 7.5%` / 封面列 `25.4%` / `gutter 4.5%` / `trailing 10.4%`，歌词列从屏宽 37.4% 起），三点作为工具条 `trailing` 并进同一排；⑧ 偏移微调 `LyricsOffsetDialog`（±0.1s / ±0.5s / 归零）。真机 `127.0.0.1:5555`（900×1600）深/浅两主题 + 横竖屏 + 逐字开关 + 两处三点菜单全部截图验收（`../preview/verification/2026-11-15-r7-*.png` 共 9 张），验收后把主题切回用户原来的**浅色**；`test`（debug+release 双变体）**28 类 235 例 0 失败**、`:app:assembleDebug` BUILD SUCCESSFUL。**此后第六轮（用户五条指令）：① 歌词页右上角「喜欢」删掉、改成三个点**（`LyricsTrackHeader` 右侧改「横竖屏」+「三点」两颗 36dp 圆按钮；`DropdownMenu` 与三点同居一个 `Box`，由 Material3 以该 Box 为锚点向下弹出——用户要求「弹窗和三点在一块」，实测菜单正好在三点正下方、右边缘对齐；菜单项：收藏/取消收藏、切换为横屏、重新加载歌词）；**② 歌词区加「横竖屏切换」按钮**（新图标 `PlayerIcons.Rotate`，形状取自用户自有项目 `music-web-cf-main` 全屏工具条 `ICON.land` 的 SVG——躺倒圆角手机 + 箭头弧；点一下 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`、横屏页同图标点回 `SCREEN_ORIENTATION_PORTRAIT`，横屏右上角同时补了同款三点菜单）；**③ 歌词显示效果换成 music-web-cf-main 全屏歌词规格（横屏同源复用）**——字号 `clamp(24dp, 3.2·vmin+15dp, 46dp)` 且 `h/4.6` 兜底、行样式 `line-height 1.32 / margin 0 0 .42em / weight 700 / letter-spacing .01em`、非当前句 `scale .82`（窄屏）`.75`（宽屏）→ 当前句 `1.0`、300ms `cubic-bezier(.22,.61,.36,1)`、白色三档 `30%/72%/100%` + 当前句 `0 0 22px` 光晕、**逐行按「到视口中心距离」`(1-d)/0.25` 淡出（不再是整块 DstIn 蒙版，且在 `graphicsLayer` 里算，滚动不触发重组）**、竖屏居中 / 横屏左对齐且缩放锚点跟着从中心挪到左边缘（照 web 原注释的坑）、逐字行「已唱纯白 / 未唱 38%」且不加光晕；**但明确不采用它那套歌词背景色**——继续透出播放页模糊封面（用户第三条指令的后半句）；当前句锚点沿用上一轮用户要求的**正中**（web 是 45%）；`LyricsViewModel` 补 `reload()`（`load()` 同曲幂等早退，不清 id 点了菜单没反应）；**④ 音乐库「全部歌曲」标题随右上角筛选（漏斗）联动**——`TrackList` 收 `filter`，标题/副标题按 `SourceFilter` 换：全部来源「全部歌曲 · 本机 + 云端」/ 只看本机「本机歌曲 · 只显示本机文件」/ 只看云端「云端歌曲 · OpenList / WebDAV」，都带「共 N 首」；**⑤ 三点菜单贴着三点弹出**（同上）。真机 `127.0.0.1:5555`（900×1600）验收：筛选三档标题依次「全部歌曲/本机歌曲/云端歌曲」；歌词页三点菜单落在三点正下方；旋转按钮 竖→横（`SurfaceOrientation: 1`）→ 竖（`0`），横屏为「左封面 + 右左对齐歌词 + 迷你条」；点歌词行 `position=237660ms`（seek 生效）；深/浅两主题各验一次（深色白字三档 + 光晕、浅色 TextPrimary 三档），验收后把主题切回用户原来的**浅色**；`test` **28 类 231 例 0 失败**（debug+release 双变体）、`:app:assembleDebug` BUILD SUCCESSFUL。详见 `05-progress.md` 同日第六条**。此前第五轮（用户四条指令）：① 迷你播放条改 16dp 圆角矩形（`MiniPlayer.kt` `glassShape` 34→16dp，底部细进度线内缩 20→16dp 对齐新圆角、端点仍不被裁；玻璃/拉伸/频谱未动）；**② 音乐库「歌手」「专辑」位置互换**（`TABS` = 歌曲/歌手/专辑/文件夹，`AnimatedContent` 分支同步换位）；**③ 歌手 Tab 改 Primuse 式列表 + 顶部搜索框**（删 2 列网格 `ArtistGrid`，新增 `ArtistList`/`ArtistSearchField`：44dp 圆形头像 + 「N 张专辑 · N 首」、行高 64dp、48dp 搜索框按名字即时过滤、搜索词提到页面级 `rememberSaveable` 防切 Tab 丢失、排序改「未知艺术家沉底 + 字母序」、点行仍直接播该歌手全部歌）；**④ 歌词页恢复 msop 原始形态并居中**（`LyricsScreen.kt` 整文件重写回 HEAD `PlayerSheet.kt` 的 `LyricsPane`/`LyricLines`：42dp 封面信息行 + 当前句 28sp 加粗居中 + 蓝色柔光、邻行 17/15sp 空间透视淡出（`0.55/(1+0.5·(d-1))` 钳 0.14–0.55）、上下 12% `DstIn` 渐隐、视口**正中**锚点（上下各半屏 padding + `contentPadding` 计入的滚差量算法）、逐字卡拉 OK/点行 seek/手动 5s 宽限照原版；`PlayerSheet` 顶栏与 `PlaybackNotice` 恢复常驻、进度条与控制键在封面·歌词两页都显示；唯一适配是按 D23 主题取墨色，原版写死白色浅色主题不可读）。真机 `127.0.0.1:5555`（900×1600）深/浅两主题验收：歌手列表+搜索（`zhou` → 空态、点行起播）、迷你条圆角矩形、歌词页当前句居中（660px vs 视口 685px）、点第 4 行 `position=59947ms`；`test`（debug+release 双变体）与 `assembleDebug` BUILD SUCCESSFUL。详见 `05-progress.md` 同日第五条**。此前：歌词页按用户指令「照抄 Primuse」重写 + 文件夹详情页雾感修复。歌词：参照 `chenqi92/primuse` iOS `LyricsScrollView`（AGPL-3.0-only，D26 允许）——26sp Semibold 左对齐、左右 24dp / 行内 5dp / 行间 12dp、当前句 1.08 倍、已唱行 0.32（深）/0.40（浅）、未来行 0.46/0.56、按行距 +1.25dp 递增模糊（上限 5dp，已唱过再加一档）、36% 视口锚点、540ms `FastOutSlowInEasing`、上下 12% 渐隐、250ms 提前切句、手动滚动 3 秒宽限；背景去掉粉色渐变，改透出模糊封面 + 主题感知压暗（深黑 0.30/0.46/0.60，浅白 0.58/0.70/0.80）。修锚点漂移：`LazyListItemInfo.offset` 不含 `contentPadding`，实际绘制 Y = top padding + offset（之前当前句稳定停在 60%）。文件夹详情：二级页 overlay 从 `HazeTitleScaffold` 内容层挪到根 `Box` 兄弟层，主内容单独吃 alpha 0.55 + scale 0.96，详情页不再被压成半透明、也不残留「音乐库」标题栏。全量 `test` **28 类 231 例 0 失败**、`assembleDebug` 通过；真机深/浅两主题验收，测试曲已删、曲库回落 18 首**。此前：图标按确认替换 11 个（Library 否决、Lyrics/Order/Cloud 保持原样）；歌词页曾直接移植用户自有 music-web-cf-main 全屏歌词到 `LyricsScreen.kt`，纯净竖屏删顶部信息行 + 隐藏 PlayerTopBar/PlayerTransport，纯渐变背景不用 blur，工具条与控制条 3 秒一起淡出，真机验收通过**。更早本批次：4 项落地 + 2 份预览待确认——音乐库删「最近」Tab、文件夹点击改详情不误播、迷你条进度条内缩 + 统一 #9A8CFF、设置关于文案与备份含明文密码；`../preview/icons-redesign.html` 与 `../preview/lyrics-redesign.html` 两份预览**等用户确认后才动图标代码 / 写歌词 Compose**。**D26：用户指令删除 GPL-3.0 复制约束，项目开源不再按许可证类型设限**；播放页三处修复并真机验收：① 进度条切歌卡住——`dragValue` 抬手未清 + `_positionMs` 只在播放中轮询，两处都改；② 扇形菜单恢复等半径圆弧形态（第一次误改成链式累加已被用户回退），R=130→86dp、张角 55°→80°，三项仍在同一弧上；③ 封面↔歌词改 `HorizontalPager` 跟手翻页。同日早些：播放页控制条重排（删右下循环、喜欢移到右下无圆圈、左下「顺序」+ JellyFab 扇形菜单、顶栏标题随播放状态、队列拖动手柄换 6 点且整行跟手、定时改 5/15/30 + 手动输入；`PlayMode` 新增 `REPEAT_ALL`）；播放页手势收敛（删「上滑开队列」、下滑整页跟手、顶部异色块 = `PlayerBackdrop` 1.18 倍溢出加 `clipToBounds()` 修掉）。此前 2026-11-14：P7 视觉三连全部完成并真机验收；全量 27 类 224 用例 0 失败） |

**每次完成任务后必须更新三处**：本节、`TASKS.md` 对应勾选框、`05-progress.md` 追加一条。

## 3. 硬约束（违反即返工）

1. **不上架、不分发**。不引入应用商店签名流程、隐私政策、审核适配。
2. **只支持三种音源**：本机文件、OpenList、WebDAV。不要加 SMB/FTP/Subsonic 直连/其他网盘。WebDAV 是 P7 新增的独立第三音源，不得扩展为通用网盘框架。
3. **只用常见格式**：MP3 / AAC / M4A / FLAC / WAV / OGG / Opus。**不引入 FFmpeg**，Media3 原生够用。
4. **采用第三方代码/资源必须在完成记录里标注来源与许可证**。2026-11-15 用户指令解除原「不复制任何第三方播放器代码或资源」与 GPL-3.0 禁令：项目是开源的，不再按许可证类型设限（见 D26）。
5. **不自己写 WebDAV 客户端**，优先 `dav4jvm`；若依赖仓库不可用，只允许实现 `PROPFIND` + `Range` 的最小客户端，并在 `AGENTS.md` D4 记录偏差。WebDAV 仅用于第三音源，不替代 OpenList 主通道。
6. **所有依赖版本集中在 `gradle/libs.versions.toml`**，禁止在模块 `build.gradle.kts` 里写死版本号。
7. **默认走 HTTPS，http 明文必须显式确认**。用户填 `http://` 时弹「确认使用明文 HTTP？」要求二次确认；确认后只对**这一条配置**生效（`allowPlainHttp`），未确认的 `http://` 请求在客户端直接抛 `PlainHttpNotConfirmed` 发不出去。`network_security_config.xml` 已放开 sys 层明文（静态配置无法按域名动态放行，见 D27），但 `usesCleartextTraffic` 仍未开。地址不带协议默认补 `https://`。
8. **本机扫描双通道且必须去重**：MediaStore 为主，SAF 为辅，同一文件只能有一条记录。
9. **UI 未确认前不写界面代码**。数据层、网络层、播放内核不受此限。

## 4. 技术栈（已锁定，不要改）

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 | Kotlin 2.4.21 | 协程 + Flow，不用 RxJava |
| UI | Jetpack Compose + Material 3 | 单 Activity，Navigation Compose |
| 播放 | androidx.media3 1.11.1 | `MediaSessionService` 做后台 |
| 图片 | Coil 3.6.3 | 专辑封面异步解码 |
| 数据库 | Room 2.8.5 | 单一 `Track` 表打通本地与远程 |
| 网络 | OkHttp 5.5.1 + kotlinx.serialization | Subsonic 走 JSON |
| DI | Hilt 2.60.1 | 不要用 Koin |
| 后台任务 | WorkManager 2.12.0 | 扫描、增量同步 |
| 构建 | AGP 9.5.1 + Gradle 9.6.0 + Version Catalog | **AGP 9 内置 Kotlin** |

完整版本表与来源见 `BUILD-NOTES.md` §2。

## 5. 模块结构（已建好，按此填充）

```
:app                      单 Activity 壳、导航图、DI 装配、全部 UI    ✅ 已实现
:core:model               Track / Album / Artist / SourceType     ✅ 已实现
:core:common              工具、日志、SAF 授权根目录                 ✅ 已实现
:core:database            Room 实体、DAO、v1→v2 迁移               ✅ 已实现
:core:network             OpenList 原生 REST + Subsonic 元数据客户端  ✅ 已实现；WebDAV 客户端 P7 落这里
:core:datastore           服务器配置 + JWT 缓存                    ✅ 已实现
:feature:local            MediaStore + SAF 扫描入库                ✅ 已实现
:feature:remote           远程曲库同步入库                         ✅ 已实现
:feature:player           ExoPlayer + MediaSessionService         ✅ 播放/通知/队列已实现
:feature:library          列表页（UI 已落在 app/ui，本模块暂空）      ⬜ 仅占位
:feature:lyrics           LRC + 内嵌歌词解析                       ✅ 已实现
:feature:settings         设置页（UI 已落在 app/ui，本模块暂空）      ⬜ 仅占位
```

依赖方向：`feature:* → core:*`，**feature 之间不互相依赖**，共享逻辑下沉到 core。

## 6. 已存在的文件（不要重建）

```
settings.gradle.kts        12 个 include，已配好 pluginManagement
build.gradle.kts           根插件声明，全部 apply false
gradle/libs.versions.toml  19 个版本 / 33 个 library / 6 个 plugin
gradle/wrapper/            gradle-wrapper.jar（48KB，取自 gradle v9.6.0 标签）+ properties
gradlew / gradlew.bat      精简版启动脚本（非官方完整版，见 BUILD-NOTES §4.4）
gradle.properties          jvmargs、parallel、caching、useAndroidX
local.properties.example   复制成 local.properties 后填 sdk.dir
app/build.gradle.kts       Compose + Hilt + KSP + 11 个 project 依赖
app/src/main/              单 Activity + 全部 Compose UI + 主题 + 清单
core/model/Track.kt        Track / Album / Artist / SourceType + toDurationLabel
../scripts/check.sh           完整性自检，不需要 JDK 就能跑
每个模块的 ModuleMarker.kt  占位对象；已落地的 core:network / core:datastore /
                           feature:remote 三个已删，其余模块保留
USAGE.md                   使用说明（P6-03）
app/proguard-rules.pro     R8 规则（P6-02）
app/src/main/res/xml/network_security_config.xml  禁明文 + user CA（P3-06）
```

**`app/src/main/java/.../ui/` 是正式 UI，不是占位**。三 Tab、五种列表视图、播放页、队列、搜索、设置、歌单全部已落地并真机验收；改 UI 直接从这里改。

## 7. 数据契约（与代码一致）

```kotlin
enum class SourceType { LOCAL, OPENLIST }

data class Track(
    val id: String,            // 本地 "local:{sha1(绝对路径)}"；远程 "openlist:{sha1(路径)}"
    val source: SourceType,
    val uri: String,           // 本地 content://；远程 "openlist:/路径"（起播时才换成 raw_url）
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,   // 远程取封面用
    val durationMs: Long = 0L,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val year: Int? = null,
    val coverUri: String? = null,  // 本地：file:// 缓存路径；远程：带 apiKey 的 getCoverArt URL（填了应用密钥才有）
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val localPath: String? = null, // 仅 LOCAL
    val lastModified: Long = 0L,   // 增量扫描依据
    val addedAt: Long = 0L,
)

data class Album(val id: String, val title: String, val artist: String?, val coverUri: String?, val trackCount: Int, val source: SourceType)
data class Artist(val id: String, val name: String, val albumCount: Int, val trackCount: Int, val source: SourceType)
```

**统一原则**：本地与远程共用 `Track`，靠 `source` + `uri` 区分。UI 层不关心来源，只消费 `Track`。

## 8. 下一步做什么

### 已完成的地基（不要再重复做）

骨架、构建、`gradlew tasks`、Room 数据层（含 v1→v2 歌单迁移）、MediaStore 扫描、SAF 目录、双通道去重、标签补齐、封面落盘与回收、Hilt + WorkManager 接线、权限门禁、启动触发扫描、全部 UI、歌单、播放页手势与模糊背景、歌词（旁挂 + 内嵌）——全部已落地，**并且已在真机（OPPO PJB110 / Android 12）上验证通过**。

### 真机调试速查（这些坑都踩过了）

```bash
# adb daemon 会随 shell 退出被杀，每次调用都要重连
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
adb start-server && adb connect 127.0.0.1:5555

# 三台 transport 指向同一设备，必须用 -s 指定
adb -s 127.0.0.1:5555 install -r -t app/build/outputs/apk/debug/app-debug.apk

# Git Bash 会把 /sdcard/x 当本地路径，截图前必须关掉路径转换
export MSYS_NO_PATHCONV=1

# 让媒体库索引新推的文件（11+ 的广播和 scan_file 都失效了，只有这个能用）
# ——但 2026-11-14 起可以不跑：App 的全量重扫会先用 MediaScannerConnection 请系统
# 重扫 Music/Download（SystemMediaIndexer），点「重新扫描曲库」即可自愈；
# 这条命令只在 App 无法触发的极端场景下备用。
adb shell content call --uri content://media --method scan_volume --arg external_primary

# 直接看数据库
adb shell run-as com.msop.player sqlite3 databases/msop.db "SELECT * FROM tracks LIMIT 5;"

# 取二进制文件（封面等）必须用 exec-out：shell cat 会把 \n 翻成 \r\n，字节全错
adb exec-out run-as com.msop.player cat files/covers/<hash>.jpg > cover.jpg

# 点 UI 用 uiautomator 拿真实坐标，别照截图猜（这台机器是横屏 1280x720）
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml

# 看播放状态：state=3 播放 / 2 暂停，metadata 就是通知栏显示的内容
adb shell dumpsys media_session | grep -E "state=PlaybackState|metadata:"

# 模拟锁屏/耳机按键（这台 ROM 的 cmd media_session 要包名，用 keyevent 更省事）
adb shell input keyevent 85   # 播放/暂停
adb shell input keyevent 87   # 下一首

# 模拟耳机拔出：AUDIO_BECOMING_NOISY 是**保护广播**，shell 发会被 SecurityException 拒掉
# 注意 action 是 android.media.*，不是 android.intent.action.*（写错会静默无效）
adb root && adb shell am broadcast -a android.media.AUDIO_BECOMING_NOISY && adb unroot
```

**⚠️ 这台设备会自动授予权限**，系统弹窗不会出现。想测拒绝流程必须换设备。

### 四条线（全部打通，只剩可选项）

| 线 | 任务 | 状态 |
|---|---|---|
| **A 本地数据** | P1-02 ✅ → … → P1-09 ✅ | **收工**，只剩 P1-04 拒绝路径（本机测不了） |
| **B 播放内核** | P2-01 ✅ → … → P2-05 ✅ | **收工**，队列拖拽排序已接（真机手势未单独验） |
| **C 远程音源** | P3-01 ✅ 实测 → P3-02 ✅ → P3-03 ✅ → P3-04 ✅ 代码/构建（`[?]` 真机待验） → P3-05 `[?]`（并入 P7-07） → P3-06 ✅ → P3-07 ✅ | **主链路全通**：23 首远程 FLAC 同步 + 播放 + seek 真机验证；`SimpleCache` 边播边缓存与「仅 Wi-Fi 缓存」已落地；WebDAV 第三音源代码完成，真实服务器待验 |
| **D 打磨打包** | P5-03 ✅ → P5-05 ✅ → P6-02 ✅ → P6-03 ✅ | **收工** |

**剩下的可选项，互不依赖，挑一个做即可**：P5-04 5 万首大库性能；P6-01 把 `05-progress.md` 的走查清单逐条勾完。
**2026-11-14 真机复验结论**：P7-01/02/05/06/08 已在 PGEM10 上跑通并转 `[x]`（数据见 `08-shuyin-integration.md` §5）；P7-03（断网）、P7-04（SAF 备份）、P7-07（WebDAV）、P3-04（缓存命中）仍需要远程曲目或真实服务器——当前设备曲库 509 首全为 LOCAL，OpenList/WebDAV 都显示「未配置」，不要为了勾任务伪造数据。P3-04 缓存命中的复验步骤照 `03-openlist-api.md` §8.4 跑。

### 已知缺口 / 注意事项

1. **权限拒绝路径未真机验证** —— 设备自动授权；release 包首次启动的门禁页与「授予」放行已验证，但「点拒绝」测不了。
2. **横屏适配未完全走查** —— 播放页已改为左封面右歌词；曲库、设置等其他页面仍未专项验证。
3. **标签被删除时同步不过来** —— 扫描给 null，`mergedWithExisting` 保留旧值。代价换来「重扫不丢补出来的数据」，见 `05-progress.md`。
4. **M4A / OGG 的内嵌封面与标签未实测** —— MP3 `USLT` 与远程 FLAC 播放已验证。
5. **远程曲目列表时长显示 `--:--`** —— 服务端列目录不给时长；播放后 Media3 能解析，播放页显示正确。
6. **远程封面/艺术家依赖应用密钥** —— 填了 OpenList「媒体应用密钥」才有真实封面与艺术家/专辑；不填则 `coverUri` 为 null、专辑取目录名。Subsonic 的 `getLyricsBySongId` 返回 `code=70`，远程歌词暂时没有。
7. **`./gradlew test` 偶发 KSP 竞态** —— 并行构建时偶见 `kspReleaseKotlin` 报 `OpenListClient could not be resolved`，**重跑一次即过**（已复现并确认不是代码问题）。别为此改依赖结构。
8. **release 的 `lintVitalRelease` 会误报 `Instantiatable`** —— `PlaybackService` 在 feature 模块、父类来自 implementation 依赖；manifest 已局部 `tools:ignore`，别删。
9. **Coil 3 的远程封面有两道坎** —— ① 只依赖 `coil-compose` 时本地 `file://` 正常、远程 `https://` **静默失败**，必须加 `coil-network-okhttp`；② **release 的 R8 + `shrinkResources` 会删掉 Coil 的 `META-INF/services` 注册文件与 fetcher 实现类**，表现是 debug 封面正常、release 全是占位图。已在 `MsopApplication` 里显式实现 `SingletonImageLoader.Factory` 并 `add(OkHttpNetworkFetcherFactory())`，`proguard-rules.pro` 里另加了 keep 兜底。排查封面问题先看这两处。
10. **P3-04 缓存命中未真机复验** —— 本机无设备；编译、单测、debug + release 构建都通过，但「二次播放不调 `fs/get`」「蜂窝下不写盘」「清理后占用归零」仍要在设备上按 `03-openlist-api.md` §8.4 复验。缓存类都是代码直接 `new` 出来的，不存在 Coil 那种 ServiceLoader 被 R8 裁掉的风险。

### UI 已落地并真机验收（2026-11-11）· 视觉重构（2026-11-12）

界面代码在 **`app/src/main/java/com/msop/player/ui/`**，共 24 个文件。

**2026-11-12 视觉重构（Aurora Glass）**：按用户「UI 太丑」反馈整体重做视觉层，功能与交互语义不变。
- 设计令牌：`theme/Theme.kt` 换成深空墨底 + 极光紫强调、玻璃渐变表面、圆角/字号层级。
- 共享组件：新增 `components/Surfaces.kt`（极光背景 / 玻璃卡 / 胶囊按钮 / 图标底板 / 分割线）与 `icons/Artwork.kt`（按 seed 生成渐变封面与艺术家头像）。
- 外壳：底部导航改悬浮胶囊，迷你播放条改玻璃卡；播放页改为模糊/生成背景 + 大圆角封面 + 自绘进度条 + 白色主播放键。
- 曲库 / 搜索 / 设置 / 歌单 / 专辑 / 队列 / 权限页全部重排；均衡器与 OpenList 配置页补极光背景与玻璃卡。
- 已知限制：**本机无模拟器/真机**，只做了编译、单测与 release 构建验证，纯视觉需设备复验。

**历史（2026-11-11）新增文件**：
`settings/EqualizerScreen.kt`（均衡器全屏浮层）、`settings/OpenListConfigScreen.kt` +
`OpenListViewModel.kt`（服务器配置与同步）、`settings/BatteryOptimization.kt`（保活引导）、
`player/QueueReorder.kt`（拖拽目标位置纯函数）。主要文件：

```
MsopApp.kt          三 Tab 外壳 + 迷你播放条 + 播放页浮层 + 队列
theme/Theme.kt         色板 / 字体 / 尺寸 / 形状 / 渐变令牌（Aurora Glass）
components/Surfaces.kt 极光背景、玻璃卡、胶囊按钮、图标底板、分割线
icons/Artwork.kt       按 seed 生成渐变封面 / 艺术家头像
icons/PlayerIcons.kt   10 个自绘播放图标（不引 material-icons-extended）
components/TrackRow.kt 歌曲行、封面、空态、标题、文件信息弹窗
components/MiniPlayer.kt 胶囊播放条 + 底部进度线
library/LibraryScreen.kt 五种视图（歌曲/最近/专辑/艺术家/文件夹）
library/AlbumDetail.kt   专辑详情
library/LibraryViewModel.kt 曲库 + 播放共享状态
player/PlayerSheet.kt    全屏播放页（封面/歌词两态 + 模糊背景 + 滑动手势）
player/QueueSheet.kt     队列面板
player/LyricsViewModel.kt 歌词加载状态
playlist/PlaylistScreen.kt  歌单列表 / 详情 / 选歌 / 重命名
playlist/PlaylistViewModel.kt 歌单 CRUD
search/SearchScreen.kt   搜索
settings/SettingsScreen.kt 设置
permission/AudioPermissionGate.kt 权限门禁
```

**为什么没按模块拆到 `feature:library` / `feature:settings`**：那几个模块目前是纯 Kotlin 库，没有 Compose / Hilt / Coil 依赖，为 UI 单独配置一遍会引入跨模块 KSP 风险。当前是自用 App，先在 app 模块跑通；等 UI 稳定、需要复用时再下沉。

**验收结果**（2026-11-11，emulator-5554 / PJB110 / 900×1600 @320dpi = 450×800dp）：

| # | 项 | 结果 |
|---|---|---|
| 1 | 三 Tab 切换 | ✅ 选中胶囊、图标变蓝、内容正确 |
| 2 | 点歌 → 迷你条 → 播放页 | ✅ `state=3` 播放中，胶囊条带封面/标题/暂停键/进度线 |
| 3 | 拖动进度条 seek | ✅ 拖到 66.7% → `position=100008ms`（曲长 150s），误差 < 1s |
| 4 | 播放页封面/歌词切换 | ✅ 两种形态切换正常；模糊封面背景与暗色渐变生效 |
| 5 | 队列面板 | ✅ 505 首，当前曲高亮逻辑正确 |
| 6 | 搜索过滤 | ✅ 输入 FLAC 即时出 1 条，当前播放行蓝色高亮 + 音柱 |
| 7 | 专辑网格 → 详情 → 播放全部 | ✅ 2 列等宽、真实封面能加载（Coil 读 `file://` 正常） |
| 8 | 设置页 | ✅ 分组、目录数、版本号正确 |
| 9 | 歌单持久化 | ✅ 新建 `TestMix` → 加 2 首 → 移除 1 首 → 改名 `Mix2`；强停重启后仍在，505 首歌未被迁移清空 |
| 10 | 播放页手势 | ✅ 封面区上滑打开队列、下滑收起；歌词页内滚动不误触发收起 |
| 11 | 内嵌同步歌词 | ✅ 无 `.lrc` 的 `USLT` 文件显示 6 行；5 秒时第一行高亮；点第五行 seek 到 42.7s 且高亮跟随 |
| 12 | 内嵌静态歌词 | ✅ 无时间标签时显示「歌词未同步 · 仅展示全文」，四行静态文本，无高亮、不 seek |
| 13 | 旁挂歌词优先 | ✅ 同名 `.lrc` 与内嵌歌词同时存在时，显示 `.lrc` 内容，内嵌内容不出现 |
| 14 | 设置页缓存清理 | ✅ 显示「1 张 · 1 KB」；确认弹窗后清理，列表变「0 张 · 0 B」，`files/covers` 目录为空 |

**说明**：上表是 2026-11-11 重构前的功能/交互验收记录。2026-11-12 只改视觉层，功能路径未改；重构后的纯视觉尚未在设备上复验。

**验收中发现并修掉的两个问题**：
1. 播放页封面无图时占位图标按 36% 缩放，在 300dp 封面上变成 100dp 的大圆环 → 改成固定 56dp。
2. 播放页缺 `navigationBarsPadding()`，全面屏手势条会压住底部入口 → 已补。

**还没做的**（不算 bug，属后续任务）：远程歌词（待 P3）、播放页以外的横屏专项适配。

## 9. 如何验证

### 9.1 不需要任何环境的自检（先跑这个）

```bash
sh ../scripts/check.sh
```

它检查：关键构建文件是否齐全、Version Catalog 能否解析、12 个模块目录是否都在、每个模块有没有源码、有没有误用被禁的插件、`feature/` 下有没有混进界面代码。**没有 JDK 也能跑**，接手第一件事就跑它。

### 9.2 需要 JDK + Android SDK

```bash
./gradlew :app:assembleDebug     # 编译
./gradlew test                   # 单测
./gradlew :app:installDebug      # 装机（需 adb）
adb logcat -s Msop:V AndroidRuntime:E
```

**验收标准写在 `TASKS.md` 每个任务里，以那里为准。** 没有可观测的验收标准，就不要标记完成。
状态标记：`[ ]` 未开始 · `[~]` 进行中 · `[?]` 已实现待验证 · `[x]` 已完成。

## 10. 已定决策（不要再讨论，除非有实测反证）

| # | 决策 | 理由 | 详见 |
|---|---|---|---|
| D1 | Kotlin + Compose，不用 Flutter | 音频生态只有原生，跨平台方案需额外插件桥接 | 02-architecture.md |
| D2 | Media3，不引入 FFmpeg | 常见格式原生够用，省 2 个月 | 02-architecture.md |
| D3 | **混合**：原生 REST 管同步与流播，Subsonic（`apiKey`）管元数据与封面 | 首测 Subsonic 未启用 → 全走原生 REST；用户启用后复测：Subsonic 只认 `apiKey`、`stream` 302 到 http 明文不可用、`path` 与 `/api/fs/get` 不通用，但艺术家/专辑/封面是原生 REST 给不了的 | 03-openlist-api.md §6 §7 |
| D4 | 保留 WebDAV 兜底，并升级为独立第三音源 | Subsonic 实现可能不全，WebDAV 一定能列目录。**实测 `dav4jvm` 不可用**：2.2.1 依赖 OkHttp 内部类 `StatusLine`（OkHttp 5 已删），5.0.0 改用 Ktor；按硬约束第 5 条实现最小 `PROPFIND` 客户端（OkHttp + DOM），播放 Range 交给 Media3 | 03-openlist-api.md / 08-shuyin-integration.md §2 P7-07 |
| D5 | 远程不落盘，边播边缓存 | `SimpleCache` 省流量，二次播放秒开 | 02-architecture.md |
| D6 | 不做登录体系 | 自用，密码存 EncryptedSharedPreferences | 02-architecture.md |
| D7 | 按公网 HTTPS 设计 | 用户实际部署在公网，要处理子路径、自签证书、高延迟 | 03-openlist-api.md §1.1 |
| D8 | 自签证书用 `network_security_config.xml` + 用户导入 CA | 比 trust-all 安全，比换证书省事 | 02-architecture.md §8 |
| D9 | 本机扫描默认 MediaStore，SAF 补充 | MediaStore 快 10 倍 | 02-architecture.md §9 |
| D10 | 公网流播加「仅 Wi-Fi 预加载」 | 蜂窝下自动缓存烧流量 | 02-architecture.md §8 |
| D12 | 反代子路径两种形态都支持，靠 URL 规范化通吃 | 不依赖用户预判部署形态 | 03-openlist-api.md §1.2 |
| D13 | UI 走「双空间」：浏览浅色密集，播放恒定深色 + 封面主色渐晕 | 大封面在深底上对比最强，OLED 省电，切歌换氛围色成本极低 | 07-ui-design.md §3.1 |
| D14 | 播放页用自定义全屏浮层，不用 `ModalBottomSheet` | 避免与左右滑切歌抢手势，且不污染返回栈 | 07-ui-design.md §2 Q3 |
| — | **UI 已落地在 app 模块**，不拆到 feature | 那几个模块没有 Compose/Hilt 依赖，单独配置风险高；自用 App 优先跑通 | `app/ui/` |
| D15 | 远程曲库只存 `openlist:/路径`，**不存 raw_url** | raw_url 带有时效 tempauth；起播/seek 时由 `ResolvingDataSource` 现取新鲜直链 | 03-openlist-api.md §6 |
| D16 | Media3 1.11 用 `AudioTrackAudioOutputProvider` 上报「不支持 offload」 | 旧 `setEnableAudioOffload` 已删除；不关 offload 时 Equalizer 会静默失效 | PlayerFactory |
| D17 | 服务器密码用普通 SharedPreferences，不用 EncryptedSharedPreferences | `security-crypto` 停在 alpha，AGP 9 / targetSdk 37 下兼容风险大；自用不上架，应用私有目录已满足威胁模型 | ServerConfigStore |
| D18 | `CacheDataSource` **包在** `ResolvingDataSource` 外面，缓存 key 只用稳定 `openlist:/路径` | raw_url 带 tempauth，拿它当 key 永远命不中；放外层还省掉缓存命中时的 `fs/get` 往返 | 03-openlist-api.md §8.1 |
| D19 | 「仅 Wi-Fi 缓存」只禁写不禁读，用构造 `CacheDataSource` 时传 `DataSink = null` 实现只读 | 蜂窝下已缓存内容照常从磁盘读反而省流量；边播边缓存本身不额外耗流量，真正烧流量的是主动预下载（本版未做） | 03-openlist-api.md §8.2 |
| D20 | 缓存偏好用 SharedPreferences 同步读，每次创建 DataSource 重新判定 | DataSource 线程要同步拿开关，不能 `runBlocking`；顺带让开关在下次切歌 / seek 立即生效 | CachePreferences |
| D21 | WebDAV 作为**独立第三音源**，单账号，稳定 key `webdav:/路径`，Basic Auth 走 HTTPS | 用户确认；与 OpenList 主通道隔离，删除配置只清 WEBDAV 来源；不存临时直链 | `08-shuyin-integration.md` §2 P7-07 |
| D22 | ~~ShuYin 只做功能级独立重写，不复制 GPL-3.0 代码/资源~~ **已解除（D26）** | 历史决定：当时 msop 无 LICENSE、怕全项目被迫 GPL。2026-11-15 用户确认项目开源，不再设此限制 | `08-shuyin-integration.md` §0 |
| D23 | 主题仅深/浅**两档**，默认深色，**不做跟随系统**（推翻 `08-shuyin-integration.md` §1 旧「不做深浅色」边界） | 用户 2026-11-14 明确指令：两档、默认深色、去掉跟随系统；`ThemePreferences` 持久化 + 切换即时生效，强停重启保持 | `05-progress.md` / `08-shuyin-integration.md` §5 同日条 |
| D24 | 液态玻璃引入 Kyant0 Backdrop 2.0.1（Apache-2.0）实现折射/磨砂（放宽 P7-10「自绘不引第三方」） | 用户确认采用同源 Apache-2.0 库 + 独立代码像素级还原；与 Haze 同为无 ServiceLoader 风险的 Compose 库，release R8 磨砂存活已真机实测。原「GPL 零引入」条款随 D26 解除 | `08-shuyin-integration.md` §0 / `05-progress.md` 同日条 |
| D25 | **排障工作法：先检索 GitHub 现成解法，能复制就复制**（标注来源与许可证） | 用户 2026-11-14 指定写入 MD；避免凭空自拟方案踩坑。原「GPL 只借鉴形态」子条款随 D26 解除 | AGENTS.md §0 第 7 条 |
| D26 | **删除 GPL-3.0 复制约束，项目开源不再按许可证类型设限** | 用户 2026-11-15 明确指令。Apache/MIT/BSD/GPL 代码与资源均可直接采用，只需在完成记录标注来源与许可证；取代 D22 的禁令与 D24/D25 中的「GPL 零引入/只借鉴形态」表述。**待办：仓库目前无 LICENSE 文件，若实际引入 GPL 代码需补 GPL-3.0 LICENSE** | AGENTS.md §0 第 7 条 / §3 第 4 条 |
| D27 | **音源多实例化 = 可保存多条、每类单选启用**；http 明文的 sys 层放开 + 应用层 `allowPlainHttp` 门禁 | ① 曲目 id 仍按 `openlist:{sha1(path)}` / `webdav:`，多 profile 不建新 id 空间，Room 迁移与播放解析零改动；真要多台同时启用并合并曲库，必须把 profile 前缀写进 id，属另一规模改造。② network security config 是静态的，无法按用户运行时输入的域名动态放行，内网自建服务器又要用 http，只能 sys 层放开、拦在 `requireConfig()` / `ensureAllowed()`。**这是对 §3 第 7 条的偏差，已征得用户默认接受（B 类任务明说「支持 HTTP」）** | `03-openlist-api.md` §9 / `05-progress.md` 2026-11-16 条 |
| D29 | **模拟器上「造计费网络」与「造断网」的正确手法（都不需要人手救 adb）** | ① 计费网络：`adb shell cmd wifi add-network <SSID> open -m`（第 2 次连接会短暂掉 adb 约 30s，随后自动回连）→ `cmd netpolicy list wifi-networks` 显示 `;true`、`dumpsys connectivity` 里 WIFI 的 `Capabilities` 不再含 `NOT_METERED`。还原：`cmd wifi add-network <SSID> open`（不带 `-m`），别用 `cmd netpolicy set metered-network <id>`——开放网络没有 `meteredOverride` 落点，命令返回 True 但不生效。② 断网：**不要**从 PC 侧 `svc wifi disable`（会立刻掉 adb，任务卡在 waiting-for-device）。改用设备端 `nohup` 脚本：写 `/data/local/tmp/p7.sh`（`logcat -c; svc wifi disable; sleep 5; screencap -p /sdcard/off.png; sleep 5; logcat -d -s Msop:V > /sdcard/off.log; screencap -p /sdcard/off2.png; svc wifi enable`）→ `adb shell "nohup sh … >/dev/null 2>&1 &"`；脚本在断网窗口里自己截屏、自己 dump 日志，Wi-Fi 恢复后 adb 自动回连，再从 `/sdcard` 拉取证。断网期间的 UI 提示（如「网络已断开，播放已暂停」）只有这样才拍得到 | `05-progress.md` 第十五轮条 |

## 11. 禁止事项

- ❌ 不要应用 `org.jetbrains.kotlin.android` 插件——AGP 9 内置 Kotlin，加上会冲突
- ❌ 不要用字符串拼 URL，一律 `HttpUrl.newBuilder().addPathSegments()`
- ❌ 不要引入 `libs.versions.toml` 之外的版本管理方式
- ❌ 不要在 UI 线程调 `MediaMetadataRetriever` / 文件遍历 / 网络
- ❌ 不要假设 OpenList 的 Subsonic 实现是完整的——**每个接口都要实测后再写进代码**
- ❌ 不要跳过 `TASKS.md` 的依赖顺序
- ❌ 不要写 `feature/*/ui/` 下的任何东西
- ❌ 不要为了让代码「更好看」而重构已通过验收的模块

## 12. 已知风险与未验证项

| 项 | 状态 | 说明 |
|---|---|---|
| **整个工程首次编译** | ✅ **2026-11-11 已验证**：`gradlew.bat :app:assembleDebug --no-daemon` BUILD SUCCESSFUL（约 1h4m；java 21；Kotlin/JVM 全量编译 + KSP/Hilt 全线通过；APK 出）。原「生成环境无 JDK/SDK」前提已失效；`BUILD-NOTES.md` §4 四条降级本轮**均未触发**。装机启动待设备；任务图有两条已知小坑，见 `05-progress.md` 本日条 |
| Hilt + KSP 与 AGP 9 兼容性 | ✅ **本轮已跑通**（`kspDebugKotlin` / `hiltCollectClassesDebug` / `hiltJavaCompileDebug` 等执行成功） | 回退路径保留未动（`BUILD-NOTES.md` §4.1） |
| AGP 9 内置 Kotlin | 🟡 存疑 | 出问题按 §4.2 关闭内置 Kotlin（AGP 10 起不可关） |
| compileSdk 37 | ✅ **已确认** | 本机 SDK 含 `platforms/android-37.0` + `build-tools/37.0.0` |
| OpenList Subsonic 接口完整度 | ✅ **已实测（不可用）** | 2026-11-11 实测 v4.2.9 未启用，改走原生 REST 全通；见 `03-openlist-api.md` §6 |

## 13. 文档索引

| 文件 | 什么时候读 |
|---|---|
| `../README.md` | 了解范围与背景（为什么重写、为什么这样选型） |
| `USAGE.md` | **用户手册**：安装、权限、目录增删、OpenList 配置、播放/歌词/均衡器、构建与重新生成签名、已知限制 |
| `BUILD-NOTES.md` | **动手构建前必读**：环境要求、版本表、四条降级方案 |
| `TASKS.md` | **每次开工前**：38 个任务、验收标准、依赖关系 |
| `02-architecture.md` | 写代码前：模块边界、数据流、公网安全、双通道扫描 |
| `03-openlist-api.md` | **做远程改动前必读 §6 / §7 / §8**（实测：Subsonic 不可用、原生 REST 全通、raw_url 约束；§8 是缓存链顺序、缓存 key、Wi-Fi 开关与真机复验步骤）；§1–5 是早期设计存档 |
| `04-reference-index.md` | 不知道某功能怎么实现时：该抄哪个仓库的哪个文件 |
| `05-progress.md` | 想知道之前做过什么、踩过什么坑、下一步提醒 |
| `07-ui-design.md` | **当前 UI 设计说明（v2.0）**：Aurora Glass 实际令牌、组件清单、已知缺口与优化待办；**改 UI 前先读** |
| `08-shuyin-integration.md` | **P7 功能融入的唯一计划与进度看板**：12 项任务、验收标准、依赖预研、libFLAC 阻塞说明；**做 P7 前先读** |
| `../preview/baseline/` | 当前视觉基线截图（库页 / 播放中 / 播放页），改视觉后对照更新 |
| `../archive/` | 历史设计稿、旧验收截图、参考素材与交付包（**不入库**，2026-11-16 起仓库只留源码 + Markdown 文档 + `../preview/baseline/` 三张基线图） |

> AI生成
