# AGENTS.md — 模型交接契约

> **接手本项目，先读完本文件（约 5 分钟），再动任何代码。**
> 本文件是唯一的状态声明。读完你应该能直接开工，不需要追问背景。

---

## 0. 五分钟接手

如果你只想看六条：

1. **这是什么**：安卓音乐播放器，Kotlin + Compose + Media3，只读本机文件 + 对接公网 OpenList，自用不上架。
2. **做到哪了**：Gradle 多模块骨架写完（12 模块）；**2026-11-11 首次 `assembleDebug` 已在本机验证通过**（COMMAND 见 §1 与 `docs/05-progress.md`）。环境旧述「生成环境无 JDK/SDK」已过时——本机具 Microsoft OpenJDK 21 与 Android SDK。注意：**该目录不是 git 仓库**（无历史可恢复；`.git` 缺失）。
3. **立刻该做什么**：`scripts/check.sh` 与首次 `./gradlew :app:assembleDebug` **均已于 2026-11-11 跑通**（§1 有命令与结果）。下一步按 §8 推进 **A 本地数据线 P1-02 → P1-03 → P1-04 → P1-05 → P1-06 → P1-07 → P1-08**（这三条线彼此独立、与 UI 无关），然后 B 播放内核 / C 远程音源。UI 相关任务（P1-09、P2-05、P4-*、P5-02）**仍挂起**，等 `docs/06` §8 的 Q1–Q4 定稿。
4. **什么不能碰**：`feature/*/ui/` 下**一行代码都不要写**——UI 还没确认（见 `docs/06` §8 的 Q1–Q4）。
5. **可以并行做什么**：P1 扫描入库、P2 播放内核、P3 Subsonic 客户端，这三块与 UI 无关。
6. **最容易踩的坑**：AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`；URL 拼接**不要**用字符串。

## 1. 项目是什么

一个只做两件事的安卓音乐播放器：读本机音频 + 对接 OpenList 音乐库。目标是「自己天天用」，不是「做一个产品」。

来源：用户看到 [Primuse](https://github.com/chenqi92/primuse)（iOS 音乐播放器，253★）后想做个安卓版。经调研确认 Primuse 是纯 Swift 生态（1954 个 Swift 文件、41.6 MB），**代码复用率为 0**，只借鉴产品形态。

## 2. 当前状态

| 项 | 值 |
|---|---|
| 阶段 | **P0 全部完成 · P1 进行中：P1-01/02/03/05/08 完成，P1-04 部分完成** |
| 代码量 | Kotlin 源码 **46 个**（单测 7 个文件 / 45 个用例）：app 5、core:common 3、core:database 8、core:model 2、feature:local 21、其余 7 个模块各 1 占位 |
| 测试 | ✅ `./gradlew test` → **45 个测试，0 失败**（core:database 4 + feature:local 41） |
| 真机验证 | ✅ 已在 **OPPO PJB110 / Android 12 (API 32)** 上验证：三 Tab、日志、权限门禁、扫描入库、通知。性能：1003 首全量 376ms、增量 35ms |
| 构建状态 | ✅ **已验证**：`./gradlew tasks` 与 `./gradlew :app:assembleDebug` 均 **BUILD SUCCESSFUL**（首次约 1h4m，增量约 1m；APK 12 MB）。本机具 Microsoft OpenJDK 21 + Android SDK（android-37 / build-tools 37.0.0）。⚠️ **无 adb 设备、无模拟器**，装机启动与界面验收仍未验证 |
| 版本控制 | ✅ 已 `git init` 并提交首个 commit（2026-11-11），此前无历史 |
| UI 状态 | ⚠️ **未确认**，`docs/06` §8 的 Q1–Q4 全待定，**禁止写界面代码** |
| 下一步 | 见 §8 |
| 本机音乐位置 | 默认 MediaStore 标准目录，用户可追加 SAF 自定义目录 |
| OpenList 部署 | 公网域名 + HTTPS，可能有反代子路径、可能自签证书，两种挂法都要支持 |
| 最后更新 | 2026-11-11（首次构建验证日） |

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
:app                      单 Activity 壳、导航图、DI 装配
:core:model               Track / Album / Artist / SourceType     ✅ 已实现
:core:common              工具、常量、Result 封装                  ⬜ 仅占位
:core:database            Room 实体、DAO、迁移                     ⬜ 仅占位
:core:network             Subsonic 客户端、WebDAV 兜底、鉴权        ⬜ 仅占位
:core:datastore           设置项（服务器地址、账号、扫描目录）       ⬜ 仅占位
:feature:local            MediaStore + SAF 扫描入库                ⬜ 仅占位
:feature:remote           OpenList 音源                           ⬜ 仅占位
:feature:player           Media3 服务、播放控制、通知               ⬜ 仅占位
:feature:library          列表页（UI 未定，暂不实现）               ⬜ 仅占位
:feature:lyrics           LRC 解析与滚动                          ⬜ 仅占位
:feature:settings         设置页（UI 未定，暂不实现）               ⬜ 仅占位
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
app/src/main/              单 Activity + 三 Tab 骨架（PrimuseApp.kt）+ 主题 + 清单
core/model/Track.kt        Track / Album / Artist / SourceType + toDurationLabel
scripts/check.sh           完整性自检，不需要 JDK 就能跑
每个模块的 ModuleMarker.kt  占位对象，验证依赖图接通，有真实实现后可删
```

**`app/src/main/java/.../ui/PrimuseApp.kt` 是骨架占位**，三个 Tab 都是空文案。它不是最终 UI，UI 定稿后整体重写。

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
    val coverUri: String? = null,  // 本地：封面缓存路径；远程：getCoverArt URL
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

骨架、构建、`gradlew tasks`、Room 数据层、MediaStore 扫描、Hilt + WorkManager 接线、权限门禁、启动触发扫描——全部已落地，**并且已在真机（OPPO PJB110 / Android 12）上验证通过**。

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
```

**⚠️ 这台设备会自动授予权限**，系统弹窗不会出现。想测拒绝流程必须换设备。

### 三条可并行的线（都不依赖 UI）

| 线 | 任务 | 状态 |
|---|---|---|
| **A 本地数据** | P1-02 ✅ → P1-03 ✅ → P1-04 ⚠️ → P1-05 ✅ → **P1-06 / P1-07（下一个）** → P1-08 ✅ | 双通道扫描与去重已跑通，缺封面提取与标签补齐 |
| **B 播放内核** | P2-01 → P2-02 → P2-03 → P2-04 | 未开工 |
| **C 远程音源** | P3-01 → P3-02 → P3-03 → P3-04 → P3-05 → P3-06 | 未开工 |

**推荐顺序**：P1-07（封面与标签提取，SAF 来源目前元数据为空）→ P1-06（去重收尾）→ P2（播放内核）→ C（远程音源）。
C 的第一步 P3-01 必须实测 OpenList 的真实响应，不能照着文档写。

### 已知缺口（真机测试发现的，尚未修）

1. **权限拒绝路径未真机验证** —— 设备自动授权，只有单测覆盖。
2. **横屏未适配** —— 当前界面是竖屏布局，横屏可用但不好看。
3. **SAF 来源元数据为空**（P1-07）—— SAF 拿不到标签，只在 SAF 里出现的文件没有 artist/album/时长。
4. **没有「移除目录」入口** —— 只能添加，不能删。

### UI 相关的任务（P1-09、P2-05、P4-*、P5-02）全部挂起

等用户定 `docs/06` §8 的 Q1–Q4，尤其是 Q4（像素级复刻 / 同设计语言 / 完全自定义），这条决定工作量差 2 倍。

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
| — | **UI 全部未定** | 用户明确要求确定前不写界面代码 | docs/06 §8 |

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
| Primuse UI 还原度 | 🟡 已评估 | 骨架层 88–92%，播放层 78–85%，特效层 30–50%，详见 `docs/06` §9 |

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
| `docs/06-ui-spec.md` | UI 草案与还原度评估。**当前未定稿，只作讨论用** |
| `docs/reference/primuse-ios/` | Primuse 官方截图 4 张，评估视觉还原时对照用 |
