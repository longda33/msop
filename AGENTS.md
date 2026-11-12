# AGENTS.md — 模型交接契约

> **接手本项目，先读完本文件（约 5 分钟），再动任何代码。**
> 本文件是唯一的状态声明。读完你应该能直接开工，不需要追问背景。

---

## 0. 五分钟接手

如果你只想看六条：

1. **这是什么**：安卓音乐播放器，Kotlin + Compose + Media3，只读本机文件 + 对接公网 OpenList，自用不上架。
2. **做到哪了**：**核心功能全部打通并真机验收**——12 模块骨架、本地扫描入库、播放内核、UI、歌单、歌词、均衡器、后台保活、**OpenList 远程音源（原生 REST 同步 + 流播 + seek，Subsonic 补艺术家/专辑/真实封面）**、release 自签名包、`USAGE.md`。本机具 Microsoft OpenJDK 21 + Android SDK，设备可用 `adb -s emulator-5554`（或 `127.0.0.1:5555`）。**该目录是 git 仓库**。
3. **立刻该做什么**：没有阻塞项了。剩余都是可选项：P3-04 `SimpleCache` 缓存 / Wi-Fi 预加载、P3-05 WebDAV 兜底、P5-04 大库性能、P6-01 走查清单逐条勾。要做远程相关改动前**先读 `docs/03` §6 与 §7 的实测结论**（Subsonic 与原生 REST 各管一段，别把两者混着用）。
4. **什么能碰**：`app/src/main/java/com/reference/lite/ui/` 是正式 UI，可以直接改。`feature/*/ui/` 仍然空着——UI 没有按模块拆，全部集中在 app 模块（原因见 §5 的说明）。
5. **可以并行做什么**：上面四项互不依赖。远程音源主通道是 **OpenList 原生 REST**（不是 Subsonic），别按旧文档写客户端。
6. **最容易踩的坑**：AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`；URL 拼接**不要**用字符串；Room 迁移**不要**开 destructive；`ffmpeg -metadata lyrics=` 写的是 `TXXX:USLT`；**KDoc 里出现 `/*` 会开启块注释**（写 `/@rest/*` 这种字面量会一路吞到文件末尾）；Media3 1.11 已删 `setEnableAudioOffload`。

## 1. 项目是什么

一个只做两件事的安卓音乐播放器：读本机音频 + 对接 OpenList 音乐库。目标是「自己天天用」，不是「做一个产品」。

来源：想做一个「只读本机文件 + 对接自己的 OpenList」的安卓播放器，供自己日常使用。
项目为**独立实现**，Kotlin + Compose + Media3 从零编写，不含任何第三方播放器的代码或资源；
界面与交互按自己的使用习惯设计，不追求还原某个既有产品。

## 2. 当前状态

| 项 | 值 |
|---|---|
| 阶段 | **P0–P4 完成 · P3 远程音源核心链路全通（原生 REST 流播 + Subsonic 元数据/封面）· P5-03/05 完成 · P6-02/03 完成**。只剩 4 个非阻塞可选项（P3-04 缓存、P3-05 WebDAV、P5-04 性能、P6-01 清单走查） |
| 代码量 | Kotlin 源码 **109 个**（main 90 + test 19） |
| 测试 | ✅ `.\gradlew.bat test` → **debug 19 个文件 / 165 个用例，0 失败**（另 release 变体复跑 app 的 6 个用例）。本轮新增 `ServerUrlTest` 11、`EqualizerTuningTest` 14、`RemoteTrackMapperTest` 9、`QueueReorderTest` 6、`SubsonicMetadataTest` 11 |
| 真机验证 | ✅ 历轮已验：三 Tab、扫描入库、通知、SAF、幽灵清理、标签/封面、MP3/WAV/FLAC 播放、后台/通知栏/媒体键、循环与随机、耳机拔出、来电打断续播、歌单持久化、播放页手势、歌词、封面清理。**本轮新增**：OpenList 配置/测试/同步（23 首 454ms）、远程 FLAC 播放与拖动 seek（68s→208s）、均衡器预设实时生效（设备 5 段）、目录管理弹窗、release 包全套回归；**Subsonic 启用后**：23 首全部补全艺术家/专辑/封面（8476ms）、真实 JPEG 封面（129KB，Coil 缓存 180KB）、播放 `state=3` 元数据正确 |
| 构建状态 | ✅ `./gradlew tasks`、`test`、`:app:assembleDebug`（19.3 MB）、`:app:assembleRelease`（**3.0 MB**，R8 + V2 自签名）全部成功。release 首轮约 5m。SDK：android-37 / build-tools 37.0.0 |
| 版本控制 | ✅ git 仓库。`app/release.jks` 与 `keystore.properties` 已加入 `.gitignore`，**不要提交** |
| UI 状态 | ✅ **`app/ui/` 22 个界面文件**，本轮新增 `EqualizerScreen`、`OpenListConfigScreen`、`QueueReorder`。所有设置行都能点出真实功能 |
| 下一步 | 见 §8（4 个可选打磨项） |
| 本机音乐位置 | 默认 MediaStore 标准目录，可追加 / **移除** SAF 自定义目录 |
| OpenList 部署 | **已实测**：OpenList v4.2.9 公网 HTTPS。Subsonic 后已启用，鉴权只认 `apiKey`，负责元数据与封面；流播仍走原生 REST。详见 `docs/03` §6（原生 REST）与 §7（Subsonic） |
| 最后更新 | 2026-11-11 晚（Subsonic 元数据增强 + 真实封面 + Coil 网络依赖修复） |

**每次完成任务后必须更新三处**：本节、`TASKS.md` 对应勾选框、`docs/05-progress.md` 追加一条。

## 3. 硬约束（违反即返工）

1. **不上架、不分发**。不引入应用商店签名流程、隐私政策、审核适配。
2. **只支持两种音源**：本机文件、OpenList。不要加 SMB/FTP/Subsonic 直连/其他网盘。
3. **只用常见格式**：MP3 / AAC / M4A / FLAC / WAV / OGG / Opus。**不引入 FFmpeg**，Media3 原生够用。
4. **不复制任何第三方播放器的代码或资源**。
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
:core:network             OpenList 原生 REST + Subsonic 元数据客户端  ✅ 已实现
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
scripts/check.sh           完整性自检，不需要 JDK 就能跑
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
| **C 远程音源** | P3-01 ✅ 实测 → P3-02 ✅ → P3-03 ✅ → P3-06 ✅ → P3-07 ✅ | **主链路全通**：23 首远程 FLAC 同步 + 播放 + seek 真机验证。剩 P3-04 `SimpleCache`、P3-05 WebDAV 未做 |
| **D 打磨打包** | P5-03 ✅ → P5-05 ✅ → P6-02 ✅ → P6-03 ✅ | **收工** |

**剩下的四件事，互不依赖，挑一个做即可**：P3-04 边播边缓存 + 仅 Wi-Fi 预加载；P3-05 WebDAV 兜底；P5-04 5 万首大库性能；P6-01 把 `docs/05` 的走查清单逐条勾完。

### 已知缺口 / 注意事项

1. **权限拒绝路径未真机验证** —— 设备自动授权；release 包首次启动的门禁页与「授予」放行已验证，但「点拒绝」测不了。
2. **横屏未专门适配** —— 竖屏布局，横屏可用但没走查。
3. **标签被删除时同步不过来** —— 扫描给 null，`mergedWithExisting` 保留旧值。代价换来「重扫不丢补出来的数据」，见 `docs/05`。
4. **M4A / OGG 的内嵌封面与标签未实测** —— MP3 `USLT` 与远程 FLAC 播放已验证。
5. **远程曲目列表时长显示 `--:--`** —— 服务端列目录不给时长；播放后 Media3 能解析，播放页显示正确。
6. **远程封面/艺术家依赖应用密钥** —— 填了 OpenList「媒体应用密钥」才有真实封面与艺术家/专辑；不填则 `coverUri` 为 null、专辑取目录名。Subsonic 的 `getLyricsBySongId` 返回 `code=70`，远程歌词暂时没有。
7. **`./gradlew test` 偶发 KSP 竞态** —— 并行构建时偶见 `kspReleaseKotlin` 报 `OpenListClient could not be resolved`，**重跑一次即过**（已复现并确认不是代码问题）。别为此改依赖结构。
8. **release 的 `lintVitalRelease` 会误报 `Instantiatable`** —— `PlaybackService` 在 feature 模块、父类来自 implementation 依赖；manifest 已局部 `tools:ignore`，别删。
9. **Coil 3 的远程封面有两道坎** —— ① 只依赖 `coil-compose` 时本地 `file://` 正常、远程 `https://` **静默失败**，必须加 `coil-network-okhttp`；② **release 的 R8 + `shrinkResources` 会删掉 Coil 的 `META-INF/services` 注册文件与 fetcher 实现类**，表现是 debug 封面正常、release 全是占位图。已在 `MsopApplication` 里显式实现 `SingletonImageLoader.Factory` 并 `add(OkHttpNetworkFetcherFactory())`，`proguard-rules.pro` 里另加了 keep 兜底。排查封面问题先看这两处。

### UI 已落地并真机验收（2026-11-11）

界面代码在 **`app/src/main/java/com/reference/lite/ui/`**，共 22 个文件。本轮新增：
`settings/EqualizerScreen.kt`（均衡器全屏浮层）、`settings/OpenListConfigScreen.kt` +
`OpenListViewModel.kt`（服务器配置与同步）、`settings/BatteryOptimization.kt`（保活引导）、
`player/QueueReorder.kt`（拖拽目标位置纯函数）。主要文件：

```
MsopApp.kt          三 Tab 外壳 + 迷你播放条 + 播放页浮层 + 队列
theme/Theme.kt         色板 / 字体 / 尺寸令牌（深色，蓝主色）
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
adb logcat -s Msop:V AndroidRuntime:E
```

**验收标准写在 `TASKS.md` 每个任务里，以那里为准。** 没有可观测的验收标准，就不要标记完成。
状态标记：`[ ]` 未开始 · `[~]` 进行中 · `[?]` 已实现待验证 · `[x]` 已完成。

## 10. 已定决策（不要再讨论，除非有实测反证）

| # | 决策 | 理由 | 详见 |
|---|---|---|---|
| D1 | Kotlin + Compose，不用 Flutter | 音频生态只有原生，跨平台方案需额外插件桥接 | docs/02 |
| D2 | Media3，不引入 FFmpeg | 常见格式原生够用，省 2 个月 | docs/02 |
| D3 | **混合**：原生 REST 管同步与流播，Subsonic（`apiKey`）管元数据与封面 | 首测 Subsonic 未启用 → 全走原生 REST；用户启用后复测：Subsonic 只认 `apiKey`、`stream` 302 到 http 明文不可用、`path` 与 `/api/fs/get` 不通用，但艺术家/专辑/封面是原生 REST 给不了的 | docs/03 §6 §7 |
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
| D15 | 远程曲库只存 `openlist:/路径`，**不存 raw_url** | raw_url 带有时效 tempauth；起播/seek 时由 `ResolvingDataSource` 现取新鲜直链 | docs/03 §6 |
| D16 | Media3 1.11 用 `AudioTrackAudioOutputProvider` 上报「不支持 offload」 | 旧 `setEnableAudioOffload` 已删除；不关 offload 时 Equalizer 会静默失效 | PlayerFactory |
| D17 | 服务器密码用普通 SharedPreferences，不用 EncryptedSharedPreferences | `security-crypto` 停在 alpha，AGP 9 / targetSdk 37 下兼容风险大；自用不上架，应用私有目录已满足威胁模型 | ServerConfigStore |

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
| OpenList Subsonic 接口完整度 | ✅ **已实测（不可用）** | 2026-11-11 实测 v4.2.9 未启用，改走原生 REST 全通；见 `docs/03` §6 |

## 13. 文档索引

| 文件 | 什么时候读 |
|---|---|
| `README.md` | 了解范围与背景（为什么重写、为什么这样选型） |
| `USAGE.md` | **用户手册**：安装、权限、目录增删、OpenList 配置、播放/歌词/均衡器、构建与重新生成签名、已知限制 |
| `BUILD-NOTES.md` | **动手构建前必读**：环境要求、版本表、四条降级方案 |
| `TASKS.md` | **每次开工前**：38 个任务、验收标准、依赖关系 |
| `docs/02-architecture.md` | 写代码前：模块边界、数据流、公网安全、双通道扫描 |
| `docs/03-openlist-api.md` | **做远程改动前必读 §6**（2026-11-11 真实服务器实测：Subsonic 不可用、原生 REST 全通、raw_url 约束）；§1–5 是早期设计存档 |
| `docs/04-reference-index.md` | 不知道某功能怎么实现时：该抄哪个仓库的哪个文件 |
| `docs/05-progress.md` | 想知道之前做过什么、踩过什么坑、下一步提醒 |
| `docs/07-ui-design.md` | **UI 定稿提案**：Q1–Q4 决策、色彩/字体/间距令牌、9 屏规格、交互、横屏适配、Compose 落地映射 |
| `docs/design/prototype.html` | 可交互高保真原型（浏览器打开，9 屏可点，支持 `#player` 这类锚点直达） |
