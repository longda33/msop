# AGENTS.md — 模型交接契约

> **接手本项目，先读完本文件（约 5 分钟），再动任何代码。**
> 本文件是唯一的状态声明。读完你应该能直接开工，不需要追问背景。

---

## 0. 五分钟接手

如果你只想看六条：

1. **这是什么**：安卓音乐播放器，Kotlin + Compose + Media3，只读本机文件 + 对接公网 OpenList，自用不上架。
2. **做到哪了**：Gradle 多模块骨架（12 模块）、本地扫描入库、播放内核、UI、歌单、歌词都已完成并真机验收。本机具 Microsoft OpenJDK 21 + Android SDK，设备可通过 `adb connect 127.0.0.1:5555` 或 `-s emulator-5554` 连接。**该目录是 git 仓库**（2026-11-11 首个 commit，之后有若干未提交改动）。
3. **立刻该做什么**：唯一的大块是 **C 远程音源 P3-01**，但它**被真实 OpenList 地址与账号阻塞**——没有服务器就不要写 Subsonic 客户端。可先做的不依赖远程的任务：P5-03 均衡器、P5-05 后台保活、P6-02 release 构建、P6-03 使用说明。
4. **什么能碰**：`app/src/main/java/com/primuse/lite/ui/` 是正式 UI，可以直接改。`feature/*/ui/` 仍然空着——UI 没有按模块拆，全部集中在 app 模块（原因见 §5 的说明）。
5. **可以并行做什么**：P5 打磨（均衡器/保活）与 P6 打包文档，这两块与远程音源无关。
6. **最容易踩的坑**：AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`；URL 拼接**不要**用字符串；Room 迁移**不要**开 destructive。`ffmpeg -metadata lyrics=` 写的是 `TXXX:USLT`，不是真正的 `USLT` 帧。

## 1. 项目是什么

一个只做两件事的安卓音乐播放器：读本机音频 + 对接 OpenList 音乐库。目标是「自己天天用」，不是「做一个产品」。

来源：用户看到 [Primuse](https://github.com/chenqi92/primuse)（iOS 音乐播放器，253★）后想做个安卓版。经调研确认 Primuse 是纯 Swift 生态（1954 个 Swift 文件、41.6 MB），**代码复用率为 0**，只借鉴产品形态。

## 2. 当前状态

| 项 | 值 |
|---|---|
| 阶段 | **P0 / P1 / P2 / P4 完成 · P5 本地部分完成（P5-01 远程待 P3）· P3 被 OpenList 服务器阻塞** |
| 代码量 | Kotlin 源码 **88 个**（main 74 + test 14；单测 14 个文件 / 114 个用例） |
| 测试 | ✅ `.\gradlew.bat test` → **114 个测试，0 失败**（core:database 4 + feature:local 82 + feature:lyrics 28；播放层靠真机验收，未加单测） |
| 真机验证 | ✅ 已在 **OPPO PJB110 / Android 12 (API 32)** 上验证：三 Tab、日志、权限门禁、扫描入库、通知、SAF 目录、幽灵清理、标签补齐、封面落盘与回收、**MP3/WAV/FLAC 播放、后台播放、通知栏与媒体键控制、单曲循环与随机、耳机拔出暂停、来电打断与挂断续播**；**歌单持久化（含 v1→v2 迁移）、播放页模糊背景与滑动收起、歌词高亮/跳转/手动回中**。性能：1003 首全量 376ms、增量 35ms；1006 条双通道去重后 503 首 635ms；30 首封面预热 400ms |
| 构建状态 | ✅ **已验证**：`./gradlew tasks`、`:app:assembleDebug`、`installDebug` 全部成功（增量约 1m；APK 19.3 MB）。本机具 Microsoft OpenJDK 21 + Android SDK（android-37 / build-tools 37.0.0）。真机连接方式见 §8「真机调试速查」 |
| 版本控制 | ✅ 是 git 仓库（2026-11-11 首个 commit）；UI、歌词、歌单与播放页手势为最近一轮改动，提交状态见 `git status` |
| UI 状态 | ✅ **已真机验收**（2026-11-11，emulator-5554 / PJB110 / 450×800dp）：三 Tab、五种列表视图、专辑详情、播放页（封面/歌词双态）、队列、搜索、设置全部走通；歌单新建/改名/加歌/删歌并在重启后保持；播放页模糊封面、上滑队列、下滑收起；歌词高亮与点击 seek；设置页封面缓存大小与清理。APK 19.3 MB |
| 下一步 | 见 §8 |
| 本机音乐位置 | 默认 MediaStore 标准目录，用户可追加 SAF 自定义目录 |
| OpenList 部署 | 公网域名 + HTTPS，可能有反代子路径、可能自签证书，两种挂法都要支持 |
| 最后更新 | 2026-11-11（P4-03 / P4-04 / P5-01 本地部分 / P5-02 完成并真机验收） |

**每次完成任务后必须更新三处**：本节、`TASKS.md` 对应勾选框、`docs/05-progress.md` 追加一条。

## 3. 硬约束（违反即返工）

1. **不上架、不分发**。不引入应用商店签名流程、隐私政策、审核适配。
2. **只支持两种音源**：本机文件、OpenList。不要加 SMB/FTP/Subsonic 直连/其他网盘。
3. **只用常见格式**：MP3 / AAC / M4A / FLAC / WAV / OGG / Opus。**不引入 FFmpeg**，Media3 原生够用。
4. **不复制 Primuse 的代码**（AGPL-3.0），只参考产品形态。
5. **不自己写 WebDAV 客户端**，优先 Subsonic API；兜底用 dav4jvm。
6. **所有依赖版本集中在 `gradle/libs.versions.toml`**，禁止在模块 `build.gradle.kts` 里写死版本号。
7. **公网必须 HTTPS**。用户填 `http://` 时弹警告并要求二次确认。禁止全局 `usesCleartextTraffic=true`。
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
:core:network             Subsonic 客户端、WebDAV 兜底、鉴权        ⬜ 仅占位
:core:datastore           设置项（服务器地址、账号、扫描目录）       ⬜ 仅占位
:feature:local            MediaStore + SAF 扫描入库                ✅ 已实现
:feature:remote           OpenList 音源                           ⬜ 仅占位
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
scripts/check.sh           完整性自检，不需要 JDK 就能跑
每个模块的 ModuleMarker.kt  占位对象，验证依赖图接通，有真实实现后可删
```

**`app/src/main/java/.../ui/` 是正式 UI，不是占位**。三 Tab、五种列表视图、播放页、队列、搜索、设置、歌单全部已落地并真机验收；改 UI 直接从这里改。

## 7. 数据契约（与代码一致）

```kotlin
enum class SourceType { LOCAL, OPENLIST }

data class Track(
    val id: String,            // 本地 "local:{sha1(绝对路径)}"；远程 "subsonic:{songId}"
    val source: SourceType,
    val uri: String,           // content:// 或 https://.../rest/stream.view?id=
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,   // 远程取封面用
    val durationMs: Long = 0L,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val year: Int? = null,
    val coverUri: String? = null,  // 本地：file:// 封面缓存路径（按内容哈希命名，可跨曲目复用）；远程：getCoverArt URL
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
adb shell content call --uri content://media --method scan_volume --arg external_primary

# 直接看数据库
adb shell run-as com.primuse.lite sqlite3 databases/primuse-lite.db "SELECT * FROM tracks LIMIT 5;"

# 取二进制文件（封面等）必须用 exec-out：shell cat 会把 \n 翻成 \r\n，字节全错
adb exec-out run-as com.primuse.lite cat files/covers/<hash>.jpg > cover.jpg

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

### 三条可并行的线（都不依赖 UI）

| 线 | 任务 | 状态 |
|---|---|---|
| **A 本地数据** | P1-02 ✅ → P1-03 ✅ → P1-04 ⚠️ → P1-05 ✅ → P1-06 ✅ → P1-07 ✅（数据侧）→ P1-08 ✅ → P1-09 ✅ | **收工**，只剩 P1-04 拒绝路径（本机测不了） |
| **B 播放内核** | P2-01 ✅ → P2-02 ✅ → P2-03 ⚠️（拖拽 UI 待做）→ P2-04 ✅ → P2-05 ✅ | 播放/通知/后台/队列/模式/焦点/拔出耳机/播放页已跑通；只剩 P2-03 的队列拖拽排序 |
| **C 远程音源** | **P3-01（被阻塞）** → P3-02 → P3-03 → P3-04 → P3-05 → P3-06 → P3-07 | **未开工**。必须先拿到真实 OpenList 地址与账号并实测响应 |
| **D 打磨** | P5-03 均衡器 → P5-05 保活 → P6-02 release → P6-03 使用说明 | 可立即开工，不依赖远程 |

**推荐顺序**：C 被服务器阻塞，先做 D 线；拿到 OpenList 凭据后立刻回到 C 的第一步 P3-01。
P3-01 必须实测 OpenList 的真实响应，不能照着文档写。

### 已知缺口（真机测试发现的，尚未修）

1. **权限拒绝路径未真机验证** —— 设备自动授权，只有单测覆盖。
2. **横屏未专门适配** —— 当前界面是竖屏布局，横屏可用但没走查（模拟器 900×1600 竖屏）。
3. **没有「移除目录」入口** —— 只能添加，不能删。
4. **标签被删除时同步不过来** —— 扫描给 null，`mergedWithExisting` 会保留旧值。代价换来的是「重扫不丢补出来的数据」，见 `docs/05`。
5. **M4A / OGG 的内嵌封面与标签未实测** —— MP3 的 `USLT` 已在设备上验证，FLAC 的 Vorbis Comment 只有 JVM 单测；P6-01 走查时补。
6. **队列拖拽排序未做** —— `moveInQueue` 已就位，UI 没接（P2-03 剩余部分）。

### UI 已落地并真机验收（2026-11-11）

界面代码在 **`app/src/main/java/com/primuse/lite/ui/`**，共 16 个文件：

```
PrimuseApp.kt          三 Tab 外壳 + 迷你播放条 + 播放页浮层 + 队列
theme/Theme.kt         色板 / 字体 / 尺寸令牌（深色，Primuse 配色）
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

**验收中发现并修掉的两个问题**：
1. 播放页封面无图时占位图标按 36% 缩放，在 300dp 封面上变成 100dp 的大圆环 → 改成固定 56dp。
2. 播放页缺 `navigationBarsPadding()`，全面屏手势条会压住底部入口 → 已补。

**还没做的**（不算 bug，属后续任务）：队列拖拽排序、远程歌词（待 P3）、横屏专门适配。

## 9. 如何验证

### 9.1 不需要任何环境的自检（先跑这个）

```bash
sh scripts/check.sh
```

它检查：关键构建文件是否齐全、Version Catalog 能否解析、12 个模块目录是否都在、每个模块有没有源码、有没有误用被禁的插件、`feature/` 下有没有混进界面代码。**没有 JDK 也能跑**，接手第一件事就跑它。

### 9.2 需要 JDK + Android SDK

```bash
./gradlew :app:assembleDebug     # 编译
./gradlew test                   # 单测
./gradlew :app:installDebug      # 装机（需 adb）
adb logcat -s PrimuseLite:V AndroidRuntime:E
```

**验收标准写在 `TASKS.md` 每个任务里，以那里为准。** 没有可观测的验收标准，就不要标记完成。
状态标记：`[ ]` 未开始 · `[~]` 进行中 · `[?]` 已实现待验证 · `[x]` 已完成。

## 10. 已定决策（不要再讨论，除非有实测反证）

| # | 决策 | 理由 | 详见 |
|---|---|---|---|
| D1 | Kotlin + Compose，不用 Flutter | 音频生态只有原生，参考项目也全是原生 | docs/02 |
| D2 | Media3，不引入 FFmpeg | 常见格式原生够用，省 2 个月 | docs/02 |
| D3 | OpenList 走 Subsonic API | OpenList 源码有 `server/handles/subsonic.go`，语义面向音乐 | docs/03 |
| D4 | 保留 WebDAV 兜底 | Subsonic 实现可能不全，WebDAV 一定能列目录 | docs/03 |
| D5 | 远程不落盘，边播边缓存 | `SimpleCache` 省流量，二次播放秒开 | docs/02 |
| D6 | 不做登录体系 | 自用，密码存 EncryptedSharedPreferences | docs/02 |
| D7 | 按公网 HTTPS 设计 | 用户实际部署在公网，要处理子路径、自签证书、高延迟 | docs/03 §1.1 |
| D8 | 自签证书用 `network_security_config.xml` + 用户导入 CA | 比 trust-all 安全，比换证书省事 | docs/02 §8 |
| D9 | 本机扫描默认 MediaStore，SAF 补充 | MediaStore 快 10 倍 | docs/02 §9 |
| D10 | 公网流播加「仅 Wi-Fi 预加载」 | 蜂窝下自动缓存烧流量 | docs/02 §8 |
| D12 | 反代子路径两种形态都支持，靠 URL 规范化通吃 | 不依赖用户预判部署形态 | docs/03 §1.2 |
| D13 | UI 走「双空间」：浏览浅色密集，播放恒定深色 + 封面主色渐晕 | 大封面在深底上对比最强，OLED 省电，切歌换氛围色成本极低 | docs/07 §3.1 |
| D14 | 播放页用自定义全屏浮层，不用 `ModalBottomSheet` | 避免与左右滑切歌抢手势，且不污染返回栈 | docs/07 §2 Q3 |
| — | **UI 已落地在 app 模块**，不拆到 feature | 那几个模块没有 Compose/Hilt 依赖，单独配置风险高；自用 App 优先跑通 | `app/ui/` |

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
| **整个工程首次编译** | ✅ **2026-11-11 已验证**：`gradlew.bat :app:assembleDebug --no-daemon` BUILD SUCCESSFUL（约 1h4m；java 21；Kotlin/JVM 全量编译 + KSP/Hilt 全线通过；APK 出）。原「生成环境无 JDK/SDK」前提已失效；`BUILD-NOTES.md` §4 四条降级本轮**均未触发**。装机启动待设备；任务图有两条已知小坑，见 `docs/05-progress.md` 本日条 |
| Hilt + KSP 与 AGP 9 兼容性 | ✅ **本轮已跑通**（`kspDebugKotlin` / `hiltCollectClassesDebug` / `hiltJavaCompileDebug` 等执行成功） | 回退路径保留未动（`BUILD-NOTES.md` §4.1） |
| AGP 9 内置 Kotlin | 🟡 存疑 | 出问题按 §4.2 关闭内置 Kotlin（AGP 10 起不可关） |
| compileSdk 37 | ✅ **已确认** | 本机 SDK 含 `platforms/android-37.0` + `build-tools/37.0.0` |
| OpenList Subsonic 接口完整度 | 🔴 未知 | 无公开文档，P3-01 必须实测 |
| Primuse UI 还原度 | 🟡 已评估 | 骨架层 88–92%，播放层 78–85%，特效层 30–50%，详见 `docs/06` §9；落地方案见 `docs/07` |

## 13. 文档索引

| 文件 | 什么时候读 |
|---|---|
| `README.md` | 了解范围与背景（为什么重写、为什么这样选型） |
| `BUILD-NOTES.md` | **动手构建前必读**：环境要求、版本表、四条降级方案 |
| `TASKS.md` | **每次开工前**：38 个任务、验收标准、依赖关系 |
| `docs/01-research.md` | 想换技术方案时，看 GitHub 实测数据与选型依据 |
| `docs/02-architecture.md` | 写代码前：模块边界、数据流、公网安全、双通道扫描 |
| `docs/03-openlist-api.md` | 做 OpenList 对接时：Subsonic 接口、URL 规范化、WebDAV 兜底 |
| `docs/04-reference-index.md` | 不知道某功能怎么实现时：该抄哪个仓库的哪个文件 |
| `docs/05-progress.md` | 想知道之前做过什么、踩过什么坑、下一步提醒 |
| `docs/06-ui-spec.md` | UI 草案与还原度评估。**已被 `docs/07` 取代**，仅作讨论存档 |
| `docs/07-ui-design.md` | **UI 定稿提案**：Q1–Q4 决策、色彩/字体/间距令牌、9 屏规格、交互、横屏适配、Compose 落地映射 |
| `docs/design/prototype.html` | 可交互高保真原型（浏览器打开，9 屏可点，支持 `#player` 这类锚点直达） |
| `docs/reference/primuse-ios/` | Primuse 官方截图 4 张，评估视觉还原时对照用 |
