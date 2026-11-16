# 08 — ShuYin 功能融入计划（独立重写）

> 参考项目：<https://github.com/power690/ShuYin>（GPL-3.0）
> 本文件是本次功能融入的**唯一计划与进度看板**。每完成一项，先更新本文件状态，再同步
> `TASKS.md` 与 `docs/05-progress.md`，不要等全部做完再补记录。

---

## 0. 边界（先读）

1. ~~**只借鉴功能与交互，不复制代码/资源**（ShuYin 是 GPL-3.0，msop 无 LICENSE）~~
   **该边界已于 2026-11-15 解除**（用户：「删掉 GPL-3.0 约束，项目是开源的」，见 `AGENTS.md` D26）。
   现在 Apache-2.0 / MIT / BSD / GPL-3.0 代码与资源均可直接采用，只需在完成记录标注来源与许可证。
   本计划当时的实现仍按 msop 风格独立编写，属历史事实，不追溯改写。
2. **保留 msop 现有硬约束**（`AGENTS.md` §3）：常见音频格式、公网 HTTPS、不引入 FFmpeg、
   本机扫描双通道去重、依赖版本集中在 Version Catalog。
3. **只做用户确认过的范围。** 明确不做：40 语言、动态取色/主题色、用户资料、
   首页推荐、更新检查、启动向导、APE、经典/现代双播放页、登录 streak。
   **边界修订（2026-11-14）**：①原列在「不做」里的**深浅色主题**被用户明确要求推翻——
   现为深色/浅色**两档**、默认深色、不跟随系统（见 §5 收官记录与 `AGENTS.md` D23）；
   ②原 P7-10「自绘实现不引第三方」放宽为允许 **Apache-2.0 同源库** Kyant0 Backdrop
   2.0.1 配合独立代码还原效果。原「GPL-3.0 代码零引入」条款随 2026-11-15 D26 解除。
4. **UI 继续放在 `app/src/main/java/com/msop/player/ui/`**，不迁入 `feature/*`。
5. **每次改动必须可验证**：编译 → 单测 → 真机（`adb -s 127.0.0.1:5555`）→ 记录。
   没有可观测验收标准的条目不得标 `[x]`。

---

## 1. 已确认范围

| 功能 | 用户决定 | 实现口径 |
|---|---|---|
| 播放状态恢复 | 融入 | 持久化队列 + 当前曲 + 进度 + 播放模式；冷启动恢复但**不自动出声** |
| 桌面悬浮歌词 | 完整版 | 独立悬浮窗，可拖动，单/双行，字号/颜色/透明度/开关 |
| 断网保护 + 缓冲提示 | 融入 | 远程曲目断网自动暂停并提示；缓冲转圈；恢复网络不自动续播 |
| 备份/恢复 | 融入 | SAF 导出/导入 zip（JSON 清单）：歌单、设置、服务器地址（不含密码） |
| TTML | 融入 | 解析 TTML（含 `tt` 命名空间、嵌套 span、`begin/end/dur`、帧率/tick） |
| 音质信息 | 融入 | 码率 / 采样率 / 位深；本地按需读取，远程随同步入库 |
| Haze 标题栏毛玻璃 | 融入 | 滚动时标题栏实时模糊；Android 12+，低版本降级纯色 |
| 液态玻璃导航 | 融入 | 底部胶囊导航 + 迷你条：模糊、折射高光、按压光斑；自绘实现，不引第三方 |
| iOS 式转场 | 融入 | 二级页右滑入 + 缩放 + 圆角；背景压缩/模糊；弹簧曲线 |
| WebDAV | **作为独立第三音源** | 新 `SourceType.WEBDAV`；单账号；HTTPS 优先；`dav4jvm` 优先 |
| libFLAC 扩展 | **保留** | 需 NDK + CMake；Media3 官方未发布 AAR，需 vendor Apache-2.0 源码（见 §3.3） |

**新增硬约束例外**：`AGENTS.md` §3 第 2 条由「只支持本机 + OpenList」改为
「只支持本机、OpenList、WebDAV 三种音源」。WebDAV 不得扩展为 SMB/FTP/其他网盘。

---

## 2. 实施顺序（P7）

> 依赖关系决定顺序：先修阻塞项，再做低风险数据/播放能力，最后做重 UI 与需要
> native 工具链的 libFLAC。每项完成后把状态从 `[ ]` 改为 `[?]`（待真机）或 `[x]`。
>
> **2026-11-14 顺序修正**：P7-09 / 10 / 11 三者没有真实技术耦合（Haze 作用于标题栏、
> 液态玻璃作用于底部导航、转场作用于二级页 overlay，互不依赖），按视觉层递进排期
> **09 → 10 → 11**。`TASKS.md` 原先把 P7-09 标成「依赖 P7-11」、P7-11 标成「依赖 P7-01」
> 是排期笔误，已改为 09 依赖 P7-08、10 依赖 09、11 依赖 10。

### P7-01 播放页进度条回归修复（阻塞项）

- **现象**：Aurora Glass 重构后，播放页自绘进度条点击/拖动疑似不生效；旧版 P2-05 已验收。
- **排查**：`SeekBar` 的 `pointerInput` 是否收到事件、`widthPx` 是否有效、父布局是否抢手势、
  `onCommit` 是否真的调到 `PlaybackController.seekTo`。
- **修复原则**：保留点击跳转 + 拖动跟手；触摸热区不小于 48dp；不与上下滑手势冲突。
- **验收**：真机竖屏，播放 `FLAC 测试曲`（150s），点进度条 50% → `dumpsys media_session`
  的 `position` ≈ 75000ms；拖动到 20% → ≈ 30000ms；拖动过程中进度点跟手。
- **文件**：`ui/player/PlayerSheet.kt`、必要时 `ui/MsopApp.kt`。

### P7-02 播放状态恢复

- **数据**：新增 `PlaybackSnapshot`（JSON，SharedPreferences 或 DataStore）：
  `trackIds: List<String>`、`currentIndex`、`positionMs`、`playMode`、`savedAt`、`wasPlaying`。
- **写入时机**：切歌、暂停、每 5 秒（播放中节流）、`onTaskRemoved`、App 进后台。
- **恢复时机**：`PlaybackController` 首次连接后，若队列为空且快照未过期（默认 7 天），
  从 `TrackDao.findByIds` 重建 MediaItem 队列并 `prepare()` + `seekTo(position)`，
  **不调用 play()**。找不到的曲目跳过并顺延当前索引。
- **UI**：迷你播放条显示恢复的曲目；播放页可继续播放。
- **验收**：播放 30s → 强停 App → 重启 → 队列/当前曲/进度恢复且未自动出声；
  点播放从恢复位置继续；卸载重装/清数据后不恢复。
- **文件**：`feature/player/PlaybackController.kt`、新增 `PlaybackSnapshotStore.kt`、
  `feature/player/build.gradle.kts`（依赖 `core:database`、serialization）。

### P7-03 断网保护 + 缓冲提示

- **网络监听**：`ConnectivityManager.NetworkCallback`，只在「远程曲目正在播放/缓冲」时响应。
- **行为**：断网 → 立即 `pause()`，状态置 `networkLost=true`，播放页/迷你条提示
  「网络已断开，播放已暂停」；恢复网络只清除提示，**不自动续播**。
- **缓冲**：`PlaybackState` 增加 `isBuffering`（`Player.STATE_BUFFERING`）；播放页显示
  转圈 + 「缓冲中…」；缓冲超过 15s 且网络可用时提示「网络较慢」。
- **验收**：飞行模式开 → 远程曲目 1s 内暂停并提示；关飞行模式提示消失、不自动播；
  本地曲目断网不受影响；弱网下缓冲提示可见。
- **文件**：`feature/player/PlaybackController.kt`、新增 `NetworkMonitor.kt`、
  `ui/components/MiniPlayer.kt`、`ui/player/PlayerSheet.kt`。

### P7-04 备份与恢复

- **格式**：zip（`manifest.json` + `playlists.json`），版本号 `msop-backup-v1`。
- **导出内容**：歌单（名称、创建时间、曲目 id 与顺序）、设置项（均衡器、缓存开关等
  非敏感偏好）、OpenList 服务器地址 + 用户名 + 是否允许明文 + 应用密钥（**不含密码**）。
- **恢复**：校验 manifest → 歌单按名称合并（同名同曲目跳过）→ 设置写回 → 服务器地址
  更新后清空旧 token。恢复前弹确认；失败保留原数据。
- **UI**：设置页「备份与恢复」分组，SAF `CreateDocument("application/zip")` /
  `OpenDocument`；显示成功/失败结果与条目数。
- **验收**：导出 → 改歌单/设置 → 恢复 → 歌单与设置回到导出时状态；服务器密码仍为空，
  重新输入密码后可连接；坏 zip 不破坏现有数据。
- **文件**：新增 `app/backup/BackupManager.kt`、`BackupViewModel.kt`、
  `ui/settings/SettingsScreen.kt`、`core/datastore`（配置导入导出接口）。

### P7-05 TTML 歌词

- **解析器**：`feature/lyrics/TtmlParser.kt`，纯 Kotlin + `javax.xml` DOM。
  支持：`<tt>` 根、`tt` 命名空间、`<p begin end dur>`、嵌套 `<span>`、`br`、
  时间表达式 `h/m/s/ms/f/t`、`ttp:frameRate/subFrameRate/tickRate`、`xml:lang` 忽略。
  输出复用 `Lyrics` / `LyricLine` / `LyricWord`。
- **接入**：旁挂歌词查找顺序改为 `.lrc` → `.ttml` → `.xml`（仅内容像 TTML 时）；
  内嵌歌词若内容像 TTML 也走 TTML；TTML 解析失败回退纯文本。
- **验收**：单测覆盖根命名空间/嵌套 span/帧时间/tick 时间/无 end 用下一行 begin；
  真机放一首带 `.ttml` 的曲目，逐字高亮与点击跳转正确。
- **文件**：`feature/lyrics/TtmlParser.kt`、`LyricsRepository.kt`、新增单测。

### P7-06 音质信息

- **模型/库**：`Track` 增加 `bitrateKbps: Int?`、`sampleRateHz: Int?`、`bitDepth: Int?`；
  `TrackEntity` 同步加列；Room **v2→v3 迁移**（ALTER TABLE，默认 NULL）。
- **本地**：`MediaExtractor` / `MediaFormat` 读 `KEY_BIT_RATE`、`KEY_SAMPLE_RATE`；
  位深用 `MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE`（API 31+）。
  **compileSdk 37 的 `MediaFormat` 没有 `KEY_BITS_PER_SAMPLE`**，不要再去找。
  MediaStore 通道用「文件大小 × 8 / 时长」估算码率，采样率/位深由扫描后的补齐流程读文件。
- **远程**：Subsonic `search3` 的 `bitRate`、`samplingRate`、`bitDepth` 写入；
  OpenList 原生 REST 没有这些字段时保持 NULL。
- **UI**：曲目「文件信息」弹窗单独一行「音质」，如 `24bit / 96kHz / 2304kbps`；
  字段全空时不显示，列表不占空间。
- **验收**：本地 FLAC/MP3 信息正确；远程 23 首同步后能显示码率/采样率；
  旧库升级 v3 不丢数据。
- **文件**：`core/model/Track.kt`、`core/database/*`、`core/database/MsopDatabase.kt`、
  `feature/local`、`feature/remote`、`ui/components/TrackRow.kt`。

### P7-07 WebDAV 第三音源

- **音源类型**：`SourceType.WEBDAV`；id `webdav:{sha1(path)}`；uri `webdav:/path`；
  不存带签名的临时 URL。
- **配置**：设置页新增「WebDAV 服务器」，字段：地址、用户名、密码、根路径（可选）。
  单账号。保存前做 `PROPFIND Depth: 0/1` 连通性测试；`http://` 二次确认；密码只存本地，
  不进备份。
- **列目录**：`PROPFIND Depth: 1`，解析 `d:response/d:href/d:propstat/d:prop`，
  取 `displayname`、`getcontentlength`、`getlastmodified`、`getcontenttype`、`resourcetype`。
  只收音频扩展名；按目录递归同步入库。
- **播放**：不做自定义 Range 客户端。`ResolvingDataSource` 把 `webdav:/path` 换成
  「服务器地址 + 根路径 + 文件路径」，Basic Auth 通过 `DataSpec.setHttpRequestHeaders`
  注入，Range/断点续传由 Media3 的 `DefaultHttpDataSource` 完成；
  缓存 key 仍用稳定的 `webdav:/path`，缓存链顺序与 OpenList 相同（Cache 外层）。
- **库**：`SourceType.WEBDAV` 参与扫描、清理幽灵、搜索、来源筛选；不污染 OpenList 清理。
- **验收**：真实 WebDAV（或本机 rclone/nginx）列目录、同步、播放、seek、缓存命中；
  断网保护生效；错误有中文提示；删除服务器配置后该来源曲目清空。
- **依赖**：优先 `dav4jvm`，但实测不可用：JitPack 2.2.1 的 `Response` 直接引用
  `okhttp3.internal.http.StatusLine`（OkHttp 5 已删除），5.0.0 改用 Ktor 客户端栈。
  按硬约束允许的最小实现，用 OkHttp + DOM 解析实现 `PROPFIND Depth: 0/1`，
  偏差记录在 `AGENTS.md` D4。
- **文件**：`core/model`（SourceType）、`core/network`（`WebDavUrl.kt`、
  `WebDavMultiStatusParser.kt`、`WebDavClient.kt`、`CompositeStreamUrlResolver.kt`）、
  `core/datastore`（`WebDavConfig.kt`、`WebDavConfigStore.kt`）、
  `feature/remote`（`WebDavTrackMapper.kt`、`WebDavLibrarySyncer.kt`）、
  `feature/player`（`PlayerFactory.kt`、`MediaCachePolicy.kt`）、
  `ui/settings/*`、`backup/*`、`TASKS.md` P3-05。

### P7-08 桌面悬浮歌词（完整版）

- **权限**：`SYSTEM_ALERT_WINDOW`；设置页开关，未授权时跳 `ACTION_MANAGE_OVERLAY_PERMISSION`。
- **服务**：`DesktopLyricsService`（前台服务，通知渠道「桌面歌词」；不播放音频）。
  `WindowManager` + `TYPE_APPLICATION_OVERLAY`，可拖动，贴边吸附。
- **内容**：订阅 `PlaybackController.state` + `LyricsViewModel` 同源歌词；显示当前行 +
  下一行（单/双行可切）；无歌词时显示歌名。
- **样式**：字号 12–32sp、文字颜色（预设 6 色 + 白色）、背景透明度 0–80%、锁定/解锁拖动。
- **生命周期**：播放停止 10 分钟后自动隐藏；App 内开关可关闭；进程被杀后不自动重启。
- **验收**：真机授权后开启，桌面可见并随播放逐行更新；拖动/锁定/样式实时生效；
  关闭开关立即消失；重启 App 状态保持（但需再次授权才显示）。
- **文件**：新增 `app/desktop/DesktopLyricsService.kt`、`DesktopLyricsPreferences.kt`、
  `ui/settings/DesktopLyricsSettings.kt`、`AndroidManifest.xml`、`SettingsScreen.kt`。

### P7-09 Haze 标题栏毛玻璃

- **依赖**：`dev.chrisbanes.haze:haze:1.7.3`（Maven Central 已确认存在；Compose BOM 不兼容时升 2.1.0）。
- **实现**：`ScreenTitle` 外层加 `hazeSource`，页面内容滚动时标题栏 `hazeEffect`；
  Android 12+ 实时模糊，低版本降级半透明纯色。曲库/搜索/设置/歌单/专辑详情统一。
- **验收**：滚动列表时标题栏后方内容模糊且不闪烁；低版本（模拟器 API 30）为纯色；
  不影响点击与性能（滚动 ≥ 50fps）。
- **文件**：`gradle/libs.versions.toml`、`ui/components/Surfaces.kt`、各 Screen。

### P7-10 液态玻璃导航

- **实现**：升级 `BottomNav` / `MiniPlayer` 为自绘液态玻璃：
  - API 31+：`RenderEffect.createBlurEffect` 对背景采样（或 `graphicsLayer` + 模糊层）；
  - 全版本：双层渐变高光 + 1dp 内描边 + 按压径向光斑 + 果冻挤压（`animateFloatAsState` 弹簧）；
  - 保留现有三 Tab 与迷你条交互，不改变导航语义。
- **验收**：真机按压有光斑与轻微挤压；滚动内容经过胶囊时呈磨砂；低版本降级不崩；
  触摸目标 ≥ 48dp。
- **文件**：`ui/MsopApp.kt`、`ui/components/MiniPlayer.kt`、`ui/components/Surfaces.kt`。

### P7-11 iOS 式转场

- **实现**：二级页面（专辑详情、歌单详情、设置子页、OpenList/WebDAV 配置）进入时
  右滑入 + `scale 0.94→1` + 圆角 24→0 + 背景压缩模糊；返回反向。用
  `AnimatedContent` / `NavHost` 的 `enterTransition`/`exitTransition`，弹簧
  `spring(dampingRatio=0.85, stiffness=380)`。
- **验收**：真机进入/返回动画连贯，无闪烁、无手势冲突；快速连点不崩；返回键行为不变。
- **文件**：`ui/MsopApp.kt`、`ui/library/AlbumDetail.kt`、`ui/playlist/PlaylistScreen.kt`、
  `ui/settings/*`。

### P7-12 libFLAC 扩展（Native，最后做）

- **现状核实（2026-11-12）**：
  - `androidx.media3:media3-decoder-flac` **未发布到 Google Maven**（group-index 无此 artifact，
    1.11.1 POM 404）。官方 FLAC 扩展需要从 Media3 源码的 `libraries/decoder_flac` 构建。
  - 本机 SDK **没有 NDK / CMake**（`$LOCALAPPDATA\Android\Sdk` 下只有 build-tools/platforms/platform-tools）。
- **方案**：安装 NDK + CMake → vendor Media3 `decoder_flac`（Apache-2.0）与 libFLAC（BSD）
  源码到 `feature/player/src/main/cpp` + `CMakeLists.txt` → `externalNativeBuild` 产出
  `libflacJNI.so` → `DefaultRenderersFactory.setExtensionRendererMode(PREFER)`。
- **许可**：保留 Apache-2.0 / BSD 版权与 NOTICE，不与 GPL-3.0 的 ShuYin 代码混用。
- **验收**：`./gradlew :app:assembleDebug` 产出含 `libflacJNI.so`（arm64-v8a）的 APK；
  真机播放 FLAC 正常，日志确认走扩展解码器；MP3/WAV/OGG 不回退失败。
- **阻塞**：NDK 未安装、体积大；待前 11 项完成后再执行。若用户不接受下载 NDK，
  该项保持 `[!]` 并记录原因。
- **可行性复核（2026-11-16，核实 `power690/ShuYin`）**：
  - 上游仍然是同一套东西：`androidx.media3:media3-decoder-flac` 在 Google Maven **依旧 404**
    （实测 `maven-metadata.xml` 与 `1.11.1/*.pom` 都返回 404），本机 `$LOCALAPPDATA\Android\Sdk`
    下**没有** `ndk/` 与 `cmake/`（只有 build-tools / platforms / platform-tools / cmdline-tools）。
  - ShuYin（GPL-3.0，Kotlin + Compose）的 FLAC 扩展 = **vendor 了 Media3 的 `libraries/decoder_flac`**：
    `app/src/main/cpp/CMakeLists.txt` 里有 `flacJNI` 目标，源码是
    `cpp/flac/{flac_jni.cc, flac_parser.cc, libflac/, include/}`，Java 侧在
    `app/src/main/java/androidx/media3/decoder/flac/`；构建配置 `ndkVersion = "30.0.14904198"`、
    CMake 4.1.2、ABI `arm64-v8a`（release 再加 `armeabi-v7a`），所有 native 目标都加了
    `-Wl,-z,max-page-size=16384`（Android 15+ 16KB 页对齐）。
  - 结论：**结构可直接移植**（与本节方案一致），但三件事不能省/不能照抄：
    ① NDK + CMake 必须装（移植后仍要编译 .so）；② Java 与 JNI 必须取**同一 Media3 版本**，
    msop 锁 1.11.1 → 从 Media3 1.11.1 的 `libraries/decoder_flac` 取，而不是从 ShuYin 取
    （ShuYin 未写死 media3 版本）；③ 不要照抄它 `packaging` 里排除 `META-INF/LICENSE*` / `NOTICE*`
    的做法（Apache-2.0 / BSD 的署名义务要求保留）。它的 APE（`cpp/ape`、`io.github.eugenedibtsev.media3.ape`）
    与 msop 无关，不搬。
  - 接线改动很小：`PlayerFactory.create()` 增加
    `DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)`
    并用 `.setRenderersFactory(...)` 挂上；`feature/player/build.gradle.kts` 加 `ndkVersion` 与
    `externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }`。
  - 「不装 NDK」的旁路（从 ShuYin release APK 抽 `lib/arm64-v8a/libflacJNI.so` 放 `jniLibs`）
    技术上可行但脆：class/FQN 必须与媒体库版本一致、16KB 对齐不可控、且等同分发 GPL 产物 →
    仅作实验，不作为正式方案。
  - 价值判断：msop 当前 FLAC 走系统 MediaCodec 且已真机验过播放/seek，本项是**兼容性保险**
    （缺 FLAC 解码器的魔改 ROM、24bit / 多声道、16KB 页设备），不是功能缺口，因此仍列最后。
  - **收益评估（2026-11-16，结论：收益很小，不建议做）**：
    1. 它只替换 **FLAC 这一颗解码器**，不增加任何新格式——能播什么由 extractor + 解码器共同决定，
       FLAC 之外的格式（APE/ALAC/WavPack/TAK/TTA/DSD/WMA）完全不沾边。所以「支持更多解码」
       这个期待它给不了。
    2. 真正能感知的差异只有两类：① 24bit FLAC 的精度——平台 `MediaCodec` 的 flac 输出 16bit PCM，
       24bit 会被截到 16bit；Media3 扩展输出 float，保留 24bit 精度。② 缺 FLAC 解码器的 ROM 上
       从「打不开」变成「能播」。
    3. 本机曲库里的 FLAC 实测是 **16bit / 44.1kHz**（P7-06 音质弹窗数据），平台解码本来就是
       逐样本无损，换成 libFLAC **听不出任何差别**；设备（PGEM10 / Android 15）自带解码器也正常。
       → 在当前设备与当前曲库上，收益 ≈ 0。
    4. 代价是硬的：NDK+CMake 约 1–1.5 GB、每 ABI 1–2 MB、Media3 版本升级要同步 vendor 源码，
       且 qc/兼容风险落在自己身上。
    5. 若目标真是「多格式」：那是一个新任务线，不是 P7-12 的延伸——需要额外 native 解码器 +
       对应 extractor（如 APE 需 maclib 一类 GPL/BSD 混合库、ALAC/DSD 需各自实现），
       且 `AGENTS.md` 硬约束第 3 条**禁止引入 FFmpeg**，只能逐个自建，成本远高于本项。

---

## 3. 技术预研结论（2026-11-12 实测）

### 3.1 可用的依赖

| 依赖 | 结论 |
|---|---|
| Haze | Maven Central 有 1.7.3 / 2.1.0，可直接加 |
| kotlinx.serialization | 已有 1.12.0，播放快照/备份 JSON 可直接用 |
| Room | 已有 2.8.5，v2→v3 迁移可控 |
| dav4jvm | 待确认仓库坐标；JitPack metadata 401，需改用 Maven Central 或最小 PROPFIND 实现 |

### 3.2 不能直接用的

- `media3-decoder-flac`：不存在公开 AAR（见 P7-12）。
- ShuYin 的 `MANAGE_EXTERNAL_STORAGE`、`usesCleartextTraffic`、APE 解析、45KB 悬浮窗服务：
  与 msop 约束或独立重写原则冲突，不采用。

### 3.3 风险

1. Haze 1.7.3 与 Compose BOM 2026.09 的 API 兼容性需首轮编译验证。
2. Room v3 迁移必须写 `ALTER TABLE`，禁止 destructive。
3. WebDAV 服务器差异大（Nextcloud/nginx/rclone），PROPFIND 的 `href` 编码与
   相对路径要按 `docs/03` §4 实测处理；先做单账号，避免范围膨胀。
4. 桌面歌词是独立窗口，必须严格跟随播放状态，避免成为第二个播放器。
5. libFLAC 需要 native 工具链，是唯一可能长时间阻塞的项。

---

## 4. 进度看板

> 状态：`[ ]` 未开始 · `[~]` 进行中 · `[?]` 已实现待真机 · `[x]` 完成 · `[!]` 阻塞

| # | 任务 | 状态 | 完成日期 | 备注 |
|---|---|---|---|---|
| P7-01 | 进度条回归修复 | `[x]` | 2026-11-14 | 真机：120s 曲目点 50% → `position=60003ms`，拖到 20% → `23634ms` |
| P7-02 | 播放状态恢复 | `[x]` | 2026-11-14 | 真机：快照 `43340ms` → 强停重启 `PAUSED(2)/position=43340`、队列 index=501，点播放从恢复点继续 |
| P7-03 | 断网保护 + 缓冲提示 | `[?]` | — | NetworkMonitor + 远程曲目自动暂停 + 缓冲/断网提示；**需远程曲目，本机设备未配置 OpenList/WebDAV** |
| P7-04 | 备份与恢复 | `[?]` | — | zip（manifest/playlists/settings）+ 设置页 SAF 入口；编译通过，SAF 导出/导入未真机走完 |
| P7-05 | TTML 歌词 | `[x]` | 2026-11-14 | 真机：`.ttml` 四行、4.8s 时逐字高亮前两段、点第三行 → `position=45000ms`；13 个单测通过 |
| P7-06 | 音质信息 | `[x]` | 2026-11-14 | 真机：`user_version=3`、509/509 首码率与采样率、505 首位深；弹窗显示 `16bit / 44.1kHz / 129kbps` |
| P7-07 | WebDAV 第三音源 | `[?]` | — | 最小 PROPFIND 客户端 + 同步/播放/缓存 + 配置页；27 类 224 用例通过，真实服务器待验 |
| P7-08 | 桌面悬浮歌词 | `[x]` | 2026-11-14 | 真机全套：可见/逐行/拖动吸附/锁定/字号颜色透明度/单行/关闭/杀进程不重启/重启状态保持 |
| P7-09 | Haze 标题栏毛玻璃 | `[x]` | 2026-11-14 | 曲库/搜索/设置/专辑详情/歌单三屏统一 HazeTitleScaffold；真机滚动模糊/交互/性能实测（见 §5） |
| P7-10 | 液态玻璃导航 | `[x]` | 2026-11-14 | 全屏内容 + 底部悬浮液态玻璃层；共享 HazeState；按压光斑/果冻挤压；debug + release 双包验收（见 §5） |
| P7-11 | iOS 式转场 | `[x]` | 2026-11-14 | iosStackSpec/EnteringPage；曲库/歌单/设置子页右滑入+缩放+圆角；修复圆角负值崩溃与透明页鬼影（见 §5） |
| P7-12 | libFLAC 扩展 | `[!]` | — | NDK 未安装；Media3 未发布 AAR |

---

## 5. 完成记录（按时间追加）

> 每项完成后在这里写「做了什么 / 改了哪些文件 / 验证命令与结果 / 遗留问题」，
> 同时同步 `TASKS.md` 与 `docs/05-progress.md`。

### 2026-11-12 · 计划建立
- 完成 ShuYin 功能盘点、用户逐项确认、依赖可用性初查。
- 建立 P7 十二项任务与验收标准。
- 当前代码状态：Aurora Glass 重构未提交；设备 `127.0.0.1:5555` 可用。
- 下一步：P7-01 真机定位进度条实际坐标并修复。

### 2026-11-14 · P7-01 进度条回归修复（代码完成，真机待验）

- 根因判断：Aurora Glass 把 Material3 `Slider` 换成了自绘 `SeekBar`，旧实现
  只在抬手时 `onCommit`，且触摸坐标按整个 Box 宽度换算（视觉轨道左右各内缩 8dp），
  一旦 up 事件被父布局/系统手势吞掉就表现为「点了没反应」；同时 30dp 高度低于
  48dp 触摸目标。
- 改动：
  1. 命中区 30dp → 44dp，语义节点补 `progressBarRangeInfo` / `setProgress`；
  2. 触摸换算改用轨道可视区间（左右各内缩 8dp），点两端不再偏移；
  3. **按下立即提交一次 seek**，拖动只更新预览，抬手位置不同再补一次最终 seek；
  4. `pointerInput` 不再以 `widthPx` 为 key，避免尺寸回调触发手势重启。
- 文件：`app/src/main/java/com/msop/player/ui/player/PlayerSheet.kt`。
- 验证：`.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL（1m13s）。
- 遗留：真机 `127.0.0.1:5555` 拒绝连接，`adb devices` 为空，按 `docs/08` 的
  验收步骤（点 50% → `position≈75000ms`）尚未执行，状态记 `[?]`。

### 2026-11-14 · P7-02 播放状态恢复（代码完成，真机待验）

- 数据：新增 `PlaybackSnapshot`（JSON / SharedPreferences）：
  `trackIds`、`currentTrackId`、`currentIndex`、`positionMs`、`playMode`、`wasPlaying`、`savedAt`。
- 写入：`PlaybackController` 在播放中每 5 秒落一次，暂停/切歌时立即落；列表用
  `Player.getMediaItemAt(i).mediaId` 还原，不存 `uri`。写入有 1 秒节流 + 恢复期间抑制，
  避免把恢复前的 0 位置覆盖回去。
- 恢复：冷启动 `LibraryViewModel.restoreSession()` → `PlaybackController.restoreLastSession()`，
  按 id 回库取 `Track`，不存在的曲目跳过、当前曲被删时索引顺延；`prepare()` 后
  `playWhenReady=false`，**不自动出声**；快照超过 7 天或一首不剩时清空。
- 文件：新增 `PlaybackSnapshot.kt`、`PlaybackSnapshotStore.kt`、
  `feature/player/src/test/.../PlaybackSnapshotTest.kt`；修改
  `PlaybackController.kt`、`feature/player/build.gradle.kts`（serialization + core:database）、
  `ui/library/LibraryViewModel.kt`、`ui/MsopApp.kt`。
- 验证：`.\gradlew.bat :feature:player:testDebugUnitTest :app:compileDebugKotlin` →
  **BUILD SUCCESSFUL**（42s），`PlaybackSnapshotTest` 8 用例 0 失败。
- 遗留：真机验收（播放 30s → 强停 → 重启 → 队列/进度恢复且不出声）未执行，记 `[?]`。

### 2026-11-14 · P7-03 断网保护 + 缓冲提示（代码完成，真机待验）

- 新增 `NetworkMonitor`：`registerDefaultNetworkCallback` 收敛成 `StateFlow<Boolean>`，
  切换网络时用 `activeNetwork` 复核，避免 Wi-Fi → 蜂窝误触发暂停。
- `PlaybackState` 增加 `currentSource` / `isBuffering` / `networkLost`。
- 断网保护：仅当当前曲目来源非 LOCAL 且 `playWhenReady` 时 `pause()`，置
  `networkLost=true`；恢复网络只清提示，**不自动续播**。起播/切歌时也主动复核一次，
  覆盖「离线时点播放」。
- UI：播放页顶部新增 `PlaybackNotice`（断网黄色提示 / 缓冲转圈，延迟 1.2s 出现，
  15s 后改「网络较慢」）；迷你条副标题在断网时替换为「网络已断开，播放已暂停」。
- 文件：新增 `feature/player/.../NetworkMonitor.kt`；修改 `PlaybackController.kt`、
  `ui/player/PlayerSheet.kt`、`ui/components/MiniPlayer.kt`、`ui/MsopApp.kt`。
- 验证：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（12s）。
- 遗留：飞行模式/弱网真机验收未执行，记 `[?]`。

### 2026-11-14 · P7-04 备份与恢复（代码完成，真机待验）

- 格式：zip 内固定 `manifest.json`（`msop-backup-v1`）、`playlists.json`、`settings.json`。
- 导出：歌单（名称/创建时间/曲目 id 顺序）、仅 Wi-Fi 缓存、均衡器（开关/预设/10 段曲线）、
  OpenList 地址 + 用户名 + 是否允许明文 + 应用密钥；**不含密码**。
- 恢复：先校验 manifest 与 JSON 再写库；歌单按名称合并（同名追加、`IGNORE` 去重），
  曲库不存在的 id 跳过；设置写回后服务器 `save()` 自动清 token；坏 zip 在写库前失败。
- 单条目上限 4 MB，防超大 zip。
- 文件：新增 `app/.../backup/BackupModels.kt`、`BackupManager.kt`；修改
  `core/database/.../PlaylistDao.kt`（3 个查询）、`EqualizerController.kt`
  （`exportTuning` / `importTuning`）、`ui/settings/SettingsViewModel.kt`、
  `ui/settings/SettingsScreen.kt`（「备份与恢复」分组 + SAF 入口 + 结果弹窗）、
  `app/build.gradle.kts`（serialization）。
- 验证：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（39s）。
- 遗留：真机 SAF 导出/导入验收未执行，记 `[?]`。

### 2026-11-14 · P7-05 TTML 歌词（代码完成，真机待验）

- 解析器：新增 `TtmlParser`（纯 Kotlin + `javax.xml` DOM，禁用 DOCTYPE/外部实体）。
  支持 TTML 命名空间与无命名空间根、`<p begin/end/dur>`、嵌套 `<span begin>`、
  `<br/>`、`hh:mm:ss.mmm` / `hh:mm:ss:ff` / `mm:ss.mmm`、`1.5s` / `500ms` / `10f` /
  `1500t`、`ttp:frameRate` / `subFrameRate` / `tickRate`。clock-time 视为绝对时间，
  offset-time 相对父元素 begin；无 `end` 时用下一段 `begin`；完全没有时间轴时返回
  `synced=false` 的静态文本。
- 模型：`LyricLine` 增加 `endMs`（TTML 用）与 `wordJoiner`（LRC 空格、TTML 空串），
  播放页逐字高亮改用 `line.wordJoiner`，中文不再被插空格。
- 接入：`LyricsRepository` 旁挂查找改为 `.lrc` → `.ttml` → `.xml`（`.xml` 仅当内容
  像 TTML）；内嵌 `USLT` / `LYRICS` 若是 TTML 也走同一解析器。
- 文件：新增 `feature/lyrics/.../TtmlParser.kt`、
  `feature/lyrics/src/test/.../TtmlParserTest.kt`；修改 `Lyrics.kt`、
  `LyricsRepository.kt`、`ui/player/PlayerSheet.kt`。
- 验证：`.\gradlew.bat :feature:lyrics:testDebugUnitTest :app:compileDebugKotlin` →
  **BUILD SUCCESSFUL**；`TtmlParserTest` **13 用例 0 失败**（连同既有 LRC 18、
  内嵌 13，共 44 用例）。
- 遗留：真机放一首带 `.ttml` 的曲目，逐字高亮与点击 seek 未验，记 `[?]`。

### 2026-11-14 · P7-06 音质信息（代码完成，真机待验）

- 模型与库：`Track` 增加 `bitrateKbps` / `sampleRateHz` / `bitDepth` 与
  `audioQualityLabel()`；`TrackEntity`、`TrackMapper` 同步；Room 升 **v3**，
  `MIGRATION_2_3` 三条 `ALTER TABLE`（三列均可空），`DatabaseModule` 已注册；
  `core/database/schemas/.../3.json` 已生成并确认三列 nullable INTEGER。
- 本地：新增 `AudioQualityReader`。码率/采样率走 `MediaExtractor` + `MediaFormat`，
  位深走 `MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE`（API 31+ 才给）。
  `MetadataReader.readQuality` 默认空实现，`AndroidMetadataReader` 实现；
  `LocalMetadataEnricher.enrichQuality` 只处理 `sampleRateHz IS NULL AND durationMs > 0`
  的曲目，单次上限 500，失败下次重试；`LocalScanWorker` 在标签补齐后独立跑一遍。
  MediaStore 通道用「文件大小 × 8 / 时长」估算平均码率。
- 远程：`SubsonicSong` 增加 `bitRate` / `samplingRate` / `bitDepth`，
  `SubsonicMetadata` 映射并 enrich；`0` 视为缺失，不清空本地已有值。
- 合并：`MetadataMerge` 与 `ScanMerge` 都按「空缺才补」保留音质，重扫不会抹掉。
- UI：`TrackRow` 文件信息弹窗新增「音质」行；三字段全空时整行隐藏。
- 文件：`core/model/Track.kt`、`core/database/{entity/TrackEntity,mapper/TrackMapper,
  MsopDatabase,dao/TrackDao,di/DatabaseModule}`、`feature/local/.../{TrackMetadata,
  MetadataReader,AudioQualityReader,LocalMetadataEnricher,MetadataMerge,RoomMetadataStore,
  MediaStoreTrackSource,LocalScanWorker}`、`core/network/SubsonicDtos.kt`、
  `feature/remote/SubsonicMetadata.kt`、`ui/components/TrackRow.kt`；
  新增 `core/model/src/test/.../TrackQualityTest.kt`。
- 验证：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（23s）；
  `.\gradlew.bat test` → **BUILD SUCCESSFUL**（1m16s），全量 **23 个测试类 /
  201 用例 / 0 失败**（新增 `TrackQualityTest` 5、`SubsonicMetadata` 音质用例 2）。
- 偏差：计划的 `MediaFormat.KEY_BITS_PER_SAMPLE` 在 compileSdk 37 里**不存在**，
  改用 API 31+ 的 retriever key；API 26–30 位深保持 NULL，不阻塞码率/采样率。
- 遗留：真机本地 FLAC/MP3 与远程 23 首的音质显示、v2→v3 升级不丢数据未验，记 `[?]`。

### 2026-11-14 · P7-07 WebDAV 第三音源（代码完成，真实服务器待验）

- 依赖决策：`dav4jvm` **不可用**。JitPack 2.2.1 的 `at.bitfire.dav4jvm.Response`
  构造参数直接是 `okhttp3.internal.http.StatusLine`，OkHttp 5 已删除该内部类；
  5.0.0 改用 Ktor 客户端（需引入第二套 HTTP 栈）。按硬约束第 5 条的最小实现，
  只用 OkHttp + DOM 解析 `PROPFIND`，偏差记入 `AGENTS.md` D4。
- 数据与配置：`SourceType.WEBDAV`；稳定 id `webdav:{sha1(path)}`、uri `webdav:/path`；
  新增 `WebDavConfig` / `WebDavConfigStore`（独立 `msop_webdav`，与 OpenList 完全隔离）。
- 网络：`WebDavUrl`（路径/URL/href 换算，支持反代子路径）、
  `WebDavMultiStatusParser`（按 localName 解析，禁 DOCTYPE）、
  `WebDavClient`（PROPFIND Depth:1、Basic Auth、中文错误文案）、
  `CompositeStreamUrlResolver`（按 scheme 分发 OpenList / WebDAV）。
- 播放：没有自定义 Range 客户端。`ResolvingDataSource` 拼 URL，
  `DataSpec.setHttpRequestHeaders` 注入 Basic Auth，Range 由 `DefaultHttpDataSource`
  处理；缓存 key 用 `webdav:/path`，Cache 仍在 Resolving 外层。
- 同步：`WebDavTrackMapper` + `WebDavLibrarySyncer`（BFS、只清 WEBDAV 来源、
  一个文件没扫到或有目录失败时不清理、2 万目录上限）。
- UI：设置页新增「WebDAV 服务器」入口与 `WebDavConfigScreen`（地址/账号/密码/根路径、
  http 二次确认、测试连接、保存并同步、清除只清 WEBDAV）；曲库「只看云端」
  统计纳入 WEBDAV；歌曲行与播放页来源标签区分 WebDAV。
- 备份：`SettingsBackup.webDav`（地址/用户名/根路径/allowPlainHttp，**不含密码**），
  恢复时保留本机已有密码。
- 单测：新增 `WebDavUrlTest` 7、`WebDavMultiStatusParserTest` 5、
  `WebDavTrackMapperTest` 5，共 17 例。
- 验证：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（43s）；
  `.\gradlew.bat test` → **BUILD SUCCESSFUL**（1m23s），全量 **26 个测试类 /
  218 用例 / 0 失败**。
- 遗留：真实 WebDAV（Nextcloud/rclone/nginx）的列目录、同步、播放、seek、
  缓存命中、断网保护与配置清除未验，记 `[?]`。

### 2026-11-14 · P7-08 桌面悬浮歌词（实现 + 真机验收完成）

- 做了什么：
  1. `DesktopLyricsService`：`TYPE_APPLICATION_OVERLAY` 独立窗口（不引 Compose，
     用 `WindowManager` + 两个 `TextView`），前台服务 `specialUse` + 独立通知渠道
     「桌面歌词」+「关闭」动作；`START_NOT_STICKY`，杀进程不自动重启。
  2. 内容订阅 `PlaybackController.state` 与 `LyricsRepository` 同源歌词，200ms
     刷新当前行 + 下一行；无歌词显示歌名/艺术家。文本规则抽成纯函数
     `DesktopLyricsText`，6 个 JVM 单测覆盖同步/静态/空歌词/首句前。
  3. 交互：拖动 + 左右贴边吸附 + 位置持久化；锁定加 `FLAG_NOT_TOUCHABLE`
     不挡桌面点击；字号 12–32sp、7 色、背景 0–80%、单行模式实时生效
     （SharedPreferences 同步读 + 监听器，服务里不用 `runBlocking`）。
  4. 生命周期：暂停 10 分钟后 `GONE`（代码路径 `HIDE_AFTER_PAUSE_MS`，未计时实测）；
     设置页关闭立即 `stopService`；重启 App 状态保持，打开设置页时按需恢复窗口。
  5. 权限：`SYSTEM_ALERT_WINDOW` + 设置页 `ACTION_MANAGE_OVERLAY_PERMISSION`，
     `ON_RESUME` 复核授权后自动补上开关；权限被撤销时自动关服务。
- 改了哪些文件：新增 `app/desktop/{DesktopLyricsService,DesktopLyricsPreferences,
  DesktopLyricsText}.kt`、`ui/settings/{DesktopLyricsScreen,DesktopLyricsViewModel}.kt`、
  `app/src/test/.../DesktopLyricsTextTest.kt`；修改 `AndroidManifest.xml`、
  `ui/settings/SettingsScreen.kt`（「桌面歌词」分组入口）。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin test :app:assembleDebug` →
  **BUILD SUCCESSFUL**，全量 **27 个测试类 / 224 用例 / 0 失败**（去重后，含新增 6 例）。
  设备 APK 与本地 `app-debug.apk` **md5 一致**（`6e0156fb…`），真机 PGEM10 / Android 15：
  - 服务 `isForeground=true`、`types=0x40000000`，窗口 `ty=APPLICATION_OVERLAY`；
  - 播放中歌词从「故事的小黄花」→「童年的荡秋千」→「为你翘课的那一天」逐行更新；
  - 字号 17→30sp、白色→金黄、背景 30→80% 均在悬浮窗上实时可见；
  - 拖动 `x:0→660`（右边缘吸附）、`y:1318→584`，prefs 与 `dumpsys window` 一致；
  - 锁定后 flags 出现 `NOT_TOUCHABLE`；单行模式第二行消失；
  - 关闭开关 → 服务记录 0、悬浮窗从窗口列表消失；
  - `am force-stop` → 服务与窗口都消失；重启 App `enabled=true` 保持但**不自动起服务**，
    打开桌面歌词设置页才恢复（符合按需启动约定）。
- 遗留：暂停 10 分钟自动隐藏未计时实测（代码为 `delay(10min)` 后 `GONE`）。

### 2026-11-14 · P7-09 Haze 标题栏毛玻璃（完成 + 真机验收）

- 接手状态：上一轮已在 `libs.versions.toml` 加入 `dev.chrisbanes.haze:haze`/`haze-blur` 2.1.0 并在
  app 模块声明依赖；`HazeTitle.kt`（稳定 Box 源捕获 + `HazeInput.Backdrop` 修正版）与
  曲库/搜索/设置三页接入已存在，但看板未更新且遗留 `HazeLogger.enabled = true` 调试开关，
  `gfxinfo` 采样 0 帧说明性能测量未做完即中断。
- 本轮补齐：
  1. **二级页接入**：`ScreenTitle` 新增 `leading` 槽，`HazeTitleScaffold` 新增 `onBack` 参数
     （标题栏左侧统一返回键）；`AlbumDetailScreen`、`PlaylistList`、`PlaylistDetail`、
     `AddTracksScreen` 全部改为「全屏滚动 + 固定毛玻璃标题栏」（iOS 大标题折叠形态），
     封面信息卡/播放全部行随列表滚动并经过标题栏后方被模糊；旧 `PlaylistHeader` 删除。
  2. **调试开关满退**：移除 `HazeLogger.enabled = true`（Backdrop 路径已真机确认生效）。
  3. **性能调参**：60Hz 云真机实测，`blurRadius 20dp + noiseFactor 0.08` 快滑 p50=24ms;
     降到 `12dp` 去 `noise` 后 A/B 复测：无模糊基线 p50=26ms/jank 7.3%，12dp 模糊
     中速滑 p50=27ms/jank 8.3%、快滑 p50=32ms/jank 17%；同机系统设置应用基线
     p50=21ms/jank 2.4%（≈47fps 设备上限，即原生 UI 也达不到 50fps）。
     模糊边际开销约 5–6ms/帧；观感靠 0.72 垫色 + 0.35 tint 维持，12dp 下视觉验收「轮廓可见、
     文字清晰」。
- 改了哪些文件：`ui/components/HazeTitle.kt`、`ui/components/TrackRow.kt`（ScreenTitle
  leading 槽）、`ui/library/AlbumDetail.kt`、`ui/playlist/PlaylistScreen.kt`（三屏重构，
  删 PlaylistHeader）。
- 验证方式：`compileDebugKotlin` + `test` + `assembleDebug` 全绿（27 类 224 用例 0 失败）；
  真机 PGEM10 / Android 15（900×1600，仅 7 首本地曲）：
  - 设置页滚动中截图：标题栏后方内容呈模糊轮廓、无黑块/错位；
  - 专辑详情：返回键 + 专辑名/艺术家副标题 + 封面头滚过标题栏被模糊，点播全部可用；
  - 歌单全流程：新建 → 详情（信息卡+2 曲）→ 选歌页（搜索框固定在标题栏下方不随列表滚）
    → 添加 2 首 → 返回 → 删除，全部走通；标题栏返回/添加/更多按钮均可点；
  - 性能数据见上；截图归档 `preview/verification/p7-09-*.png`。
- 遗留与口径：
  - 「滚动 ≥50fps」验收线在本可用云真机上不可达（系统应用自身 ≈47fps）；已按「模糊边际
    开销 ≤6ms、中速滑 jank 8.3%」记录，在常规手机上预期可回 50fps+；
  - API 30 纯色降级仅代码路径验证（`Build.VERSION.SDK_INT < S` 时不挂 hazeBlur，直接纯色）；
    本机无 API 30 设备/模拟器；
  - 曲库页滚动模糊证据取自上一会话 524 首曲库时期的截图（`p7-09-fix-library2.png`），
    当前设备仅 7 首无法滚动实测；机制与已验证页面同源（同一 HazeTitleScaffold）。
- 给下一位：做 P7-11 时注意二级页已是「全屏 overlay 分支」形态（AlbumDetail / PlaylistScreen / 设置子页各自 return 替换整屏），转场用 AnimatedContent 按页面分支做右滑入+缩放；P7-09 的坑（`hazeSource` 挂
  AnimatedContent 上不刷新、编辑器对 import 块做模糊匹配会吞掉中间行）都已写进本节。

### 2026-11-14 · P7-10 液态玻璃导航（完成 + debug/release 双包验收）

- 做了什么：
  1. **结构改造**：`MsopApp` 去掉 Scaffold bottomBar，改为「全屏内容（statusBarsPadding +
     hazeSource）+ 底部悬浮液态玻璃浮层（navigationBarsPadding）」。列表铺满全屏、
     从胶囊后方经过被磨砂；各屏列表 `contentPadding.bottom` 接入新的
     `LocalHazeBottomInset`（曲库 5 处、搜索 2 处、设置底部 Spacer、专辑详情、
     歌单 3 屏 + 选歌页底栏，共 9 处）。
  2. **共享 Haze 源**：新增 `LocalSharedHazeState`（CompositionLocal）；`MsopApp` 的全屏
     Box 挂唯一 hazeSource，`HazeTitleScaffold` 有共享源时不再自建（避免双重捕获），
     底部胶囊/迷你条也同一份采样。
  3. **`Modifier.liquidGlass`**（`Surfaces.kt`）：API 31+ `hazeBlur` 磨砂（radius 可配）+
     双层渐变高光 + 顶部折射亮边 + 1dp 内描边；低版本/无 state 降级高不透明渐变，
     不触发 RenderEffect。底部胶囊用 8dp（性能实测见下）。
  4. **`LiquidTab`**：按压跟随触点的径向光斑（drawWithContent + radialGradient）+
     果冻挤压（`spring(0.55, Medium)` 回弹过冲）；热区 48dp；导航语义不变。
  5. **`MiniPlayer`**：liquidGlass 底 + 整卡轻挤压/光斑；点卡展开、播放键独立响应不变。
- 性能（60Hz 云真机，中速 600ms 滑）：仅标题磨砂 27ms → +底部双胶囊 12dp 36ms/jank 25%
  → 底部降 8dp 后 32–34ms/jank 16–20%。设备上限本身 ≈47fps（系统设置应用 21ms）。
  P7-10 验收无 fps 线，如实记录；常规手机上双磨砂成本 ~5–7ms 可承受。
- 验证方式：`compileDebugKotlin` / `test` / `assembleDebug` / `assembleRelease` 全绿
  （27 类 224 用例 0 失败）；真机 PGEM10：
  - `input motionevent DOWN` 按住导航 Tab 抓帧：光斑以触点为中心可见、Tab 明显挤压
    变窄，其他 Tab 不受影响；松手回弹正常；
  - 设置页滚动中：标题栏与底部胶囊/迷你条后方内容均呈磨砂轮廓，列表末项不被遮挡；
  - Tab 切换、迷你条点开播放页、搜索输入过滤（「love」→1 首）全部正常；
  - **release（R8，3.3MB）**：mapping 确认 `HazeState/HazeBlurStyle/HazeInput` 保留；
    装真机（卸 debug 装 release + `pm grant READ_MEDIA_AUDIO/POST_NOTIFICATIONS`）后
    标题与底部磨砂、迷你条、封面全部正常——Haze 无 Coil 那种 ServiceLoader 风险；
  - 设备最终已恢复 debug 包（release 验证后重装）。
- 遗留与口径：
  - API<31 降级仅代码路径验证（本机无低版本设备）；
  - 一次性伪影：曲库页 Tab 行出现过一次「胶囊左偏+残影」截图，冷启动与 3 次
    Tab 往返均无法复现，判为云真机合成器瞬时伪影（非代码回归）；再出现时专项排查；
  - 搜索页键盘弹出时机观察到一次迟滞（焦点正常、输入过滤正常），不阻塞。
- 给下一位：P7-11 是最后一项视觉任务；转场用 spring(0.85, 380) 按 `docs/08` §2。
  编辑器 import 块模糊匹配会吞行——本轮已在 `docs/05` 2026-11-14 P7-09 条里记过，
  改 import 后立即编译核对。

### 2026-11-14 · P7-11 iOS 式二级页转场（完成 + 真机验收，视觉三连收官）

- 做了什么：
  1. **基础设施**（`Surfaces.kt`）：`iosStackSpec(opening)`——打开时二级页
     `slideInHorizontally(spring 0.85/380)` 右滑入 + `scaleIn(0.94→1)` + 淡入，返回反向
     滑出；`EnteringPage`——进入页 24dp 圆角临界阻尼收拢到 0 + 内置 AmbientBackground
     不透明底。采用「**常驻主内容 + overlay**」而非把主内容塞进 AnimatedContent：
     主内容滚动位置与内部状态在开/关二级页时完全保留，与改造前 `if/return` 行为一致。
  2. **曲库页**：`LibrarySecondary`（Playlists/Album）分支 overlay；主内容把压缩渐暗
     动画挂在 `HazeTitleScaffold(modifier = Modifier.graphicsLayer { … })` 上，
     **不改主内容块的缩进**，改动面最小。
  3. **歌单页**：列表↔详情（转场状态用稳定的 `selectedId`，避免重命名触发误转场）+
     详情↔选歌页（pickerOpen）两层；选歌页底栏补底部 inset。
  4. **设置页**：四个子页（均衡器/OpenList/WebDAV/桌面歌词）统一
     `SettingsSubPage` overlay，替换原先四个 `if` 平铺分支。
- 真机暴露并修复的两个 bug：
  1. **圆角负值崩溃**：`EnteringPage` 的半径动画用了 `spring(0.85)` 欠阻尼，
     24dp→0 会过冲到 -0.19dp，`RoundedCornerShape(负数)` 抛
     `IllegalArgumentException` 直接崩（点专辑即复现）。修复：半径改临界阻尼
     `spring(1f, 380)` + `coerceAtLeast(0.dp)` 双保险；**位移动画（slide/scale）
     的过冲无害，仍用 0.85 弹簧**——尺寸/半径这类不允许负值的动画绝不能用欠阻尼。
  2. **透明页鬼影**：二级页原先以「整页替换」形态存在、背景透出应用环境即可；
     改 overlay 后叠在（被压缩至 0.55 透明的）主内容上，稳定态仍透出半透明鬼影。
     修复：`EnteringPage` 内置 `AmbientBackground()` 不透明底。
- 验证方式：`compileDebugKotlin` / `test` / `assembleDebug` / `assembleRelease` 全绿
  （27 类 224 用例 0 失败）；真机 PGEM10：
  - 专辑详情转场中帧（设备内低延迟抓拍 + adb 抓拍各一次）：缩放展开/滑入中途状态可见；
    稳定帧干净、四角直角、无鬼影；
  - 歌单列表 / 歌单详情 / 选歌页（入口可走通）/ OpenList 子页稳定帧均干净；
  - 返回键单步验证四条链路：专辑→网格、歌单详情→列表、歌单列表→曲库、
    OpenList 子页→设置主页；
  - 快速连点（进/退 ×4 轮）：应用存活、末态正确；
  - 测试歌单 P711Test 已删净，设备保留 debug 包。
- 遗留：转场中帧抓拍受 adb/合成器延迟影响偶尔跳过（专辑页两次中帧证据确凿）；
  转场期间双屏合成为瞬时开销（~300ms），未单独测帧率。
- 给下一位：**P7 视觉三连（09/10/11）已全部收官**。剩余：P7-03/04/07 + P3-04 需要
  远程音源/真实 WebDAV 服务器/SAF 全流程；P7-12 需 NDK。可选：P5-04 大库性能、
  P6-01 走查清单。二次开发注意：给 `EnteringPage` 类尺寸动画选弹簧时永远用临界阻尼
  或钳位，欠阻尼 + 不可为负的量 = 必崩。

### 2026-11-14 · P7-01/02/05/06 真机复验（转 `[x]`）

- 同一台 PGEM10 / Android 15 / 900×1600，同一 debug 包（md5 一致），本地 509 首。
- **P7-01**：播放页进度条点 50%（x=450）→ `position=60003ms`（120s 曲目，误差 3ms）；
  拖到 20%（x=216）→ `23634ms`（按轨道内缩换算 = 19.7%，精确）。父布局上下滑手势未抢事件。
- **P7-02**：播放至 43.3s → `am force-stop` → 重启，快照
  `positionMs=43340 / currentIndex=501 / wasPlaying=true`；恢复后
  `state=PAUSED(2) position=43340`、迷你条显示 `EmbeddedPlain` 且按钮为「播放」（不出声）；
  点播放 `state=PLAYING(3)` 且位置从 43340 继续，未归零。
- **P7-05**：推送 `primuse-flac-test.ttml`（与 150s FLAC 同名，无 `.lrc`）后播放
  「FLAC 测试曲」：歌词页显示 4 行 TTML 文本，第一行「TTML逐字第一行」无中文插空格；
  4.8s 时前两个 span 高亮、第三个仍暗（逐字生效）；暂停态点第三行 →
  `position=45000ms`。注意歌词列表会跟随当前行滚动，点行前要重新取坐标。
- **P7-06**：`PRAGMA user_version=3`；`COUNT(bitrateKbps)=509`、`sampleRateHz=509`、
  `bitDepth=505`（4 首 MP3 无位深，符合预期）；WAV `705kbps/44.1kHz/16bit`、
  bulk `359kbps/22.05kHz/16bit`；文件信息弹窗「音质 `16bit / 44.1kHz / 129kbps`」。
- 仍未能验的：P7-03（需远程曲目）、P7-04（SAF 导出/导入未走完）、P7-07（无 WebDAV 服务器）、
  P3-04 缓存命中（需远程曲目）、P7-12（无 NDK），保持 `[?]` / `[!]`。

### 2026-11-14 · 用户截图需求批次 UI 改造收官（接手断点恢复，含主题边界修订）

- 背景：用户附截图批量提出 UI 改造（搜索页/曲库页/迷你条/播放页/歌词页/主题/默认封面）；
  上一会话实施完成后在收尾阶段挂死（编译落盘后无活动），本轮接手完成验收与记录。
  详细清单、验收数据与遗留见 `docs/05-progress.md` 同日条；此处只记 P7 语境要点：
  1. 主题两态化推翻本文件 §1「不做深浅色」旧边界（用户明确指令，D23）；
  2. `liquidGlass` 从自绘迁移到 Kyant0 Backdrop 2.0.1（Apache-2.0，D24），
     GPL 代码仍零引入；
  3. 迷你条最终形态按用户参考图定稿为两键版（此前频谱+三按钮版被参考图取代）；
  4. 播放页队列/喜欢互换、SleepDialog 预设+自定义、歌词 ShuYin 化、搜索页
     「共 N 首」、曲库歌手轮播、程序化默认封面全部真机验收通过（含 release R8 包）。
- 验证：全量 test / assembleDebug / assembleRelease 全绿；真机 PGEM10 逐项通过；
  release 包磨砂存活、默认深色回落正确；设备已恢复 debug 包。
- 给下一位：P7 看板状态不变（8 完成 / 3 待验证 / 1 阻塞），剩余项依赖外部资源。

> AI生成
