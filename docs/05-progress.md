# 05 — 进度日志

> **只追加，不修改历史条目。** 每次完成任务后在这里加一条，然后同步 `TASKS.md` 的勾选。
> 格式固定，方便任何模型快速读懂「做过什么、卡在哪」。

## 记录格式

```
### YYYY-MM-DD · {任务号} · {一句话结果}
- 做了什么：
- 改了哪些文件：
- 验证方式：（命令 + 实际输出摘要）
- 遗留问题：
- 给下一位的提醒：
```

---

## 日志

### 2026-11-10 · P0-00 · 项目立项，文档与任务表就绪
- 做了什么：完成 GitHub 实测调研（Primuse / NeriPlayer / Auxio / OuterTune / RetroMusic / dav4jvm / OpenList），确定技术栈与 OpenList 对接通道，建立 7 阶段 35 任务的分段任务表与模型交接契约。
- 改了哪些文件：新建 `primuse-android/` 下 `README.md`、`AGENTS.md`、`TASKS.md`、`docs/01–05`。
- 验证方式：GitHub API 实采数据，见 `docs/01` 第五节复查命令。
- 遗留问题：
  1. OpenList 的 Subsonic 挂载路径与接口完整度**尚未实测**，是 P3-01 的首要工作。
  2. 用户尚未提供本机音乐目录位置与 OpenList 部署方式（局域网/公网），P1-04 与 P3-06 开工前需要确认。
- 给下一位的提醒：**不要跳过 P3-01 的实测直接写 Subsonic 客户端**。`docs/03` 里所有标 ⚠️ 的接口都必须先用 curl 验证再落代码。

### 2026-11-10 · P0-00b · 确认部署形态，任务表扩容至 38 项
- 做了什么：用户确认两个关键参数——**本机默认使用标准目录（MediaStore）、支持自定义目录（SAF）**；**OpenList 部署在公网**。据此调整架构与任务表。
- 改了哪些文件：
  - `AGENTS.md`：状态区新增两行参数；硬约束新增第 7、8 条（强制 HTTPS、双通道去重）；决策表新增 D7–D10
  - `docs/02-architecture.md`：新增 §8 公网访问与网络安全（HTTPS 配置、反代子路径、自签证书、超时与流量）、§9 本机扫描双通道（MediaStore / SAF 性能写法 / 去重规则）；风险表新增 3 行
  - `docs/03-openlist-api.md`：新增 §1.1 公网部署四个前提；curl 模板改为公网子路径写法并加证书诊断步骤
  - `TASKS.md`：P1 从 7 项拆为 9 项（MediaStore / 权限 / SAF / 去重 独立成任务）；P3 从 6 项增至 7 项（新增 P3-06 网络安全配置）；总任务 35 → 38，工期 8–10 周 → 9–11 周
- 验证方式：文档一致性自查，任务号与依赖引用已全部对齐。
- 遗留问题：OpenList 的 Subsonic 挂载路径仍待 P3-01 实测；公网域名与是否有反代子路径需用户提供后填进 `docs/03` 的 curl 模板。
- 给下一位的提醒：**写 URL 一律用 `HttpUrl.newBuilder().addPathSegments()`，禁止字符串拼接**——公网带子路径时字符串拼接必错，这是本项目最容易踩的坑。

### 2026-11-10 · P0-00c · 澄清「反代子路径」，补 URL 规范化规则
- 做了什么：解释反向代理子路径的两种挂法（独立域名根路径 / 域名下的 `/openlist` 前缀），并决定**不依赖用户预先判断属于哪种**，改为在配置页做 URL 规范化，两种情况通吃。
- 改了哪些文件：
  - `docs/03-openlist-api.md`：新增 §1.2 URL 规范化规则（补协议、去尾斜杠、去重复斜杠、剥误粘的 `index.html`），附 `normalizeBaseUrl()` 参考实现
  - `TASKS.md`：P3-07 验收标准加入规范化用例
- 验证方式：文档自查。
- 遗留问题：仍建议用户提供实际公网地址，用于把 `docs/03` 的 curl 模板填成可直接执行的版本（P3-01 要用）。
- 给下一位的提醒：URL 规范化必须在**保存配置时**做一次并落库，不要每次请求都算。

### 2026-11-10 · P0-01 ~ P0-03 · 骨架代码落地（待首次构建验证）
- 做了什么：按 AGP 9.5.1 + 内置 Kotlin 的新规范搭出完整多模块工程，含 1 个 app 模块与 11 个 library 模块、Compose 三 Tab 空壳、Hilt 装配。所有依赖版本从 Google Maven / Maven Central 实采。
- 改了哪些文件：
  - 构建：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`gradle/wrapper/*`、`gradlew`、`gradlew.bat`、`local.properties.example`、`.gitignore`
  - app：`app/build.gradle.kts`、`app/proguard-rules.pro`、`AndroidManifest.xml`、`res/values/{strings,themes}.xml`、`PrimuseApplication.kt`、`MainActivity.kt`、`ui/PrimuseApp.kt`、`ui/theme/Theme.kt`
  - 11 个库模块：`core/{model,common,database,network,datastore}`、`feature/{local,remote,player,library,lyrics,settings}`，各含 `build.gradle.kts` + `ModuleMarker.kt`
  - 新增 `BUILD-NOTES.md`
- 验证方式：**未验证**。生成环境无 JDK / Gradle / Android SDK，无法执行 `assembleDebug`。已把 P0-01～03 标记为 `[?]`（已实现待验证）。
- 遗留问题：
  1. 首次构建需用户在装有 Android Studio 的机器上跑通，四条降级方案见 `BUILD-NOTES.md` §4。
  2. Hilt + KSP 与 AGP 9 内置 Kotlin 的兼容性存在不确定性，已备好 2 分钟移除方案。
- 给下一位的提醒：**不要在没有跑通构建前就往下写 P1**。骨架没验证，后面所有代码都建立在未验证的地基上。先让它 `assembleDebug` 通过、装上真机看到三个 Tab，再动 P1。

### 2026-11-10 · P0-00d · 撤销误写的 UI 代码，补充还原度评估
- 做了什么：用户指出「UI 没有确定，不要写」。已删除 `feature/library/ui/` 下两个界面文件，并把三个 feature 模块的 build 文件还原为不含 Compose 的纯 library 模板。工程回到「UI 零代码」状态。同时基于 Primuse 源码与官方截图完成 UI 还原度评估。
- 改了哪些文件：
  - 删除：`feature/library/.../ui/LibraryScreen.kt`、`MockLibraryData.kt`
  - 还原：`feature/{library,player,settings}/build.gradle.kts`
  - 重写：`docs/06-ui-spec.md` §8（待确认 Q1–Q4）、§9（还原度评估）
  - `AGENTS.md`：决策表移除 D13–D15，改为「UI 全部未定」；禁止事项新增「UI 未确认前不写界面代码」
  - 新增：`docs/reference/primuse-ios/` 四张官方截图
- 验证方式：`find` 确认工程内无 `*Screen*.kt` / `*Player*.kt` / `*Mock*.kt`。
- 遗留问题：UI 路线（像素级复刻 vs 保留视觉语言 + Material 3）未定，这是工作量差 2 倍的分叉点。
- 给下一位的提醒：**UI 未定不等于不能干活**。P1 的扫描入库、P2 的播放内核、P3 的 Subsonic 客户端都与 UI 无关，可以先做。只有 `feature/*/ui/` 下的东西要等。

### 2026-11-10 · P0-00e · 整理交接包，准备移交其它模型
- 做了什么：用户决定把项目交给其它模型继续。重写 `AGENTS.md` 为可独立接手的契约，补齐文件清单、三条并行工作线、已知风险表，并在 `TASKS.md` 标注 UI 挂起项。
- 改了哪些文件：
  - 重写 `AGENTS.md`：新增 §0 五分钟接手、§6 已存在文件清单、§8 三条并行线、§12 已知风险表；数据契约章节由 §5 移到 §7 并与 `core/model/Track.kt` 实际字段对齐
  - `TASKS.md`：新增「⏸ 挂起」说明（P1-09、P2-05、P4-01～05、P5-02）；P1-01 改为 `[?]`；总览 P1 状态更新
  - `README.md`：顶部加当前进度提示；目录树补全 BUILD-NOTES、docs/05–06、reference
  - 新增 `scripts/check.sh`：无需 JDK 的完整性自检（构建文件、TOML 解析、模块目录、源码存在性、禁用插件、UI 代码状态）
- 验证方式：`sh scripts/check.sh` 六项全部通过；`find` 核对 53 个文件与文档描述一致；`grep` 确认无残留的 `AGENTS.md §5` 失效引用。
- 遗留问题：无新增。原有三项风险（未编译、Hilt/KSP 兼容性、Subsonic 接口完整度）已写入 `AGENTS.md` §12。
- 给下一位的提醒：**先跑构建，再写代码**。当前工程 53 个文件全部未经编译，任何功能开发都应等 `assembleDebug` 通过之后再开始。

### 2026-11-11 · P0-01/02 · 构建链路打通，`gradlew tasks` 修复，建立版本控制
- 做了什么：上一轮另一模型已跑通 `assembleDebug`（1h4m，产出 12 MB APK），但 `./gradlew tasks` 实际是**失败**的，P0-01 的验收标准并未满足。本轮定位并修掉两个问题，然后建立 git 仓库。
- 问题与修复：
  1. **`:app:generateDebugComposePreviewRunfiles` 失败** — AGP 9 的 Compose Preview 任务要求单测包含 Android 资源。修复：`app/build.gradle.kts` 加 `testOptions.unitTests.isIncludeAndroidResources = true`。
  2. **`:app:generateReleaseComposePreviewRunfiles` 仍失败** — release 变体默认 `enableUnitTest = false`。第一次尝试 `variantBuilder.enableUnitTest` 报 `Unresolved reference`；用诊断打印确认属性存在于运行时类（`getEnableUnitTest` / `setEnableUnitTest` 均在），但**静态类型上看不到**，需强转到 `HasUnitTestBuilder`。最终写法：
     ```kotlin
     androidComponents {
         beforeVariants(selector().withBuildType("release")) { variantBuilder ->
             (variantBuilder as? com.android.build.api.variant.HasUnitTestBuilder)?.enableUnitTest = true
         }
     }
     ```
  3. **图标弃用警告** — `Icons.Filled.List` → `Icons.AutoMirrored.Filled.List`，警告消失。
- 改了哪些文件：`app/build.gradle.kts`（testOptions + androidComponents）、`app/src/main/java/.../ui/PrimuseApp.kt`（图标）、`.gitignore`（加 `*.log`）、`TASKS.md`、`AGENTS.md` §2
- 验证方式：
  - `./gradlew tasks` → **BUILD SUCCESSFUL in 30s**
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 1m**，无 `w:` 警告，APK 12 MB（10:13 重新产出）
  - `git init` + 首次提交
- 遗留问题：**无 adb 设备、无模拟器**（`adb devices` 为空，SDK 未装 emulator），P0-02 的「能启动」与 P0-03 的「真机可见底部 Tab」仍无法验收，两项状态分别为 `[x]`（仅构建）与 `[~]`。
- 给下一位的提醒：**AGP 9 的 `beforeVariants` 静态类型不完整**，遇到类似 `Unresolved reference` 先打印 `variantBuilder.javaClass.methods` 确认属性是否存在，再考虑强转接口，不要盲目改写法。另外装个模拟器（SDK Manager → Emulator + system image）就能解锁 P0-03 和后续所有 UI 验收。

### 2026-11-11 · P0-04 + P1-02 · 日志/崩溃捕获 + Room 数据层
- 做了什么：
  1. **P0-04**：`core:common` 增加 `AppLog`（统一 TAG `PrimuseLite`）与 `CrashReporter`（记录后交回系统默认处理，不吞异常），`PrimuseApplication` 在 `onCreate` 安装。
  2. **P1-02**：`core:database` 落地 Room——`TrackEntity`（17 字段 / 4 索引）、`TrackDao`（Flow 观察 + Upsert + 增量扫描用的 `latestModified`）、`PrimuseDatabase`、`Converters`、`TrackMapper`，并配 `room.schemaLocation` 导出 schema。
  3. 补 `TrackMapperTest` 4 个纯 JVM 单测（本地/远程/来源类型/可空字段往返），让 `./gradlew test` 真正有东西可跑。
  4. 新增 `.gitattributes` 固定换行符，避免 `gradlew` 在非 Windows 环境被 CRLF 破坏。
- 改了哪些文件：
  - 新增：`core/common/.../log/AppLog.kt`、`core/common/.../crash/CrashReporter.kt`
  - 新增：`core/database/.../entity/TrackEntity.kt`、`dao/TrackDao.kt`、`PrimuseDatabase.kt`、`Converters.kt`、`mapper/TrackMapper.kt`、`src/test/.../TrackMapperTest.kt`
  - 新增：`core/database/schemas/.../1.json`（自动生成，入库）
  - 修改：`PrimuseApplication.kt`、`core/database/build.gradle.kts`（ksp + room + junit）、`gradle/libs.versions.toml`（junit 4.13.2）、`.gitattributes`
- 验证方式：
  - `./gradlew :core:database:assembleDebug` → **BUILD SUCCESSFUL in 7m44s**（KSP 编译期校验 DAO 的 SQL 语法与实体映射）
  - schema 导出确认：`tracks` 表 17 字段、4 个索引（source/album/artist/lastModified）、主键 `id`
  - `./gradlew :core:database:testDebugUnitTest` → **BUILD SUCCESSFUL**，`tests=4 failures=0 errors=0`
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 1m12s**，APK 17 MB（Room 使体积从 12 MB 增至 17 MB）
- 遗留问题：
  1. **仍无 adb 设备/模拟器**，P0-03（真机可见 Tab）与 P0-04（真机日志输出）保持 `[~]` / `[?]`。
  2. `TrackDao` 的 SQL 只经过编译期校验，**未在真实数据库上执行过**。P1-03 接入扫描后需补一次真机验证。
- 给下一位的提醒：Room 的 KSP 校验很有价值但**不等于跑通**——它只保证 SQL 语法和映射正确，不保证查询结果符合预期。P1-03 第一次写入真实数据后，务必用 `adb shell run-as` 或 Database Inspector 看一眼 `tracks` 表。

### 2026-11-11 · P1-03 · MediaStore 扫描入库（逻辑完成，性能待真机验证）
- 做了什么：`feature:local` 落地本机扫描。刻意把「Android 数据读取」和「同步逻辑」拆开，让核心逻辑能在无设备的 JVM 单测里跑。
- 新增文件：
  - `scan/ScanContract.kt` — `TrackPageSource` / `LocalTrackStore` 两个接口 + `ScanStats`
  - `scan/TrackIdFactory.kt` — 路径归一化 + SHA-1 生成 `local:` 前缀 ID
  - `scan/LocalLibrarySyncer.kt` — 分页、批内去重、补 `addedAt`、统计
  - `scan/MediaStoreTrackSource.kt` — MediaStore 分页查询（`QUERY_ARG_LIMIT/OFFSET`，API 26+ 原生支持）
  - `scan/RoomLocalTrackStore.kt` — `LocalTrackStore` 的 Room 实现
  - `permission/AudioPermission.kt` — 13+ 用 `READ_MEDIA_AUDIO`，≤12 用 `READ_EXTERNAL_STORAGE`
  - 单测：`TrackIdFactoryTest`（7）、`LocalLibrarySyncerTest`（8）
- 关键技术决策：
  1. **ID 用路径哈希，不用 MediaStore `_ID`** — `_ID` 在媒体库重建/换卡后会变，会让播放记录和歌单全部失联
  2. **分页用 `QUERY_ARG_LIMIT`/`QUERY_ARG_OFFSET`** — API 26+ 原生支持，不用 `sortOrder + " LIMIT n"` 那种非官方 hack
  3. **`lastModified` 单位是秒** — 与 MediaStore `DATE_MODIFIED` 原生单位一致，不做毫秒转换，避免增量比较时两边单位错位
  4. **路径归一化不做大小写转换** — 大小写敏感存储上 `A.flac` 与 `a.flac` 是两个文件
- 踩的坑：
  - `Cursor.columnIndexOrNull` 返回类型写成 `Int`，`takeIf` 实际返回 `Int?`，编译报 `Return type mismatch`
  - 8 个同步测试首轮全挂：`android.util.Log not mocked`。用 `testOptions.unitTests.isReturnDefaultValues = true` 解决（已在 build 文件注明：将来需要真实 Android 行为时改 Robolectric）
- 验证方式：
  - `./gradlew :feature:local:testDebugUnitTest` → **15 个测试全过**（7 + 8）
  - `./gradlew test` → **19 个测试，0 失败 0 错误**（含 database 的 4 个）
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**，APK 13 MB
- 遗留问题：
  1. **性能指标未验证**：P1-03 要求「1 万首首次 < 30s、二次 < 3s」，需要真机 + 真实音乐库，当前无设备。
  2. `MediaStoreTrackSource` 与 `RoomLocalTrackStore` **没有单测覆盖**（依赖 Android 运行时），只过了编译。
  3. 尚未接入 Hilt 与 WorkManager，扫描还不能从 App 触发——这是 P1-08 的活。
- 给下一位的提醒：**`LocalLibrarySyncer` 是全项目第一个有真实测试覆盖的核心逻辑**，改它之前先跑 `./gradlew :feature:local:testDebugUnitTest`。另外 `MediaStoreTrackSource` 里 `DATA` 列在 Android 11+ 已被废弃但仍可读，若某些机型返回 null 会走 `RELATIVE_PATH + DISPLAY_NAME` 兜底，这条兜底路径目前没有验证过。

### 2026-11-11 · P1-08 · Hilt + WorkManager 接线，扫描可从 App 触发
- 做了什么：把 P1-03 的扫描逻辑真正接进 App——Hilt 提供依赖、WorkManager 调度、通知显示进度、启动时自动触发一次增量扫描。
- 新增文件：
  - `core/database/.../di/DatabaseModule.kt` — 提供 `PrimuseDatabase`（`fallbackToDestructiveMigration`）与 `TrackDao`
  - `feature/local/.../di/LocalModule.kt` — 提供 `TrackPageSource` / `LocalTrackStore` / `LocalLibrarySyncer`
  - `feature/local/.../work/LocalScanWorker.kt` — `@HiltWorker` CoroutineWorker，失败重试上限 3 次
  - `feature/local/.../work/ScanNotifier.kt` — 进度/完成/失败通知，含 Android 13+ 通知权限判断
  - `feature/local/.../work/LocalScanScheduler.kt` — `enqueueUniqueWork + KEEP`，避免叠加任务
  - `feature/local/src/main/res/` — 通知渠道文案与 `ic_scan.xml` 图标
  - 修改：`LocalLibrarySyncer.sync` 增加 `onProgress` 回调；`PrimuseApplication` 实现 `Configuration.Provider`；`MainActivity` 启动时按权限触发扫描；`AndroidManifest` 移除 WorkManager 默认初始化
- 关键技术决策：
  1. **不用前台服务，用普通通知** — 扫描幂等（确定性 ID upsert），被杀重跑无副作用，不值得为它申请 `FOREGROUND_SERVICE_DATA_SYNC` 并承担 Android 14+ 的类型声明成本
  2. **`onProgress` 必须是 suspend lambda** — 普通 lambda 里不能调 `setProgress()`（挂起函数）
  3. **`enqueueUniqueWork + ExistingWorkPolicy.KEEP`** — 重复触发不叠加、不打断正在跑的任务
  4. **manifest 必须移除 `WorkManagerInitializer`** — 否则 `@HiltWorker` 因找不到工厂而实例化失败
- 踩的坑：`Suspension functions can only be called within coroutine body`（LocalScanWorker:35），原因是 `onProgress` 声明为普通函数类型。改成 `(suspend (Int, Int) -> Unit)?` 后解决。
- 验证方式：
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**，APK 13 MB（Hilt 依赖图缺绑定会在 KSP 阶段失败，编译通过即证明图完整）
  - `./gradlew test` → **19 个测试，0 失败**
  - **合并后 manifest 核对**：`InitializationProvider` 仍在（保留 emoji2 / lifecycle / profileinstaller），但 `androidx.work.WorkManagerInitializer` **已不存在**，确认按需初始化生效
- 遗留问题：
  1. **进度与恢复未在真机验证**——「杀进程后任务可恢复」「进度条实时」都需要设备。
  2. **没有权限申请 UI**（P1-04 未做），首次启动会走「跳过自动扫描」分支并打日志，实际扫不到任何东西。
  3. `ScanNotifier` 与 `LocalScanWorker` 无单测（依赖 Android 运行时）。
- 给下一位的提醒：想真机验证这条链路，需要先装模拟器，然后手动授权 `READ_MEDIA_AUDIO`（或先在 P1-04 把申请流程做出来），再 `adb logcat -s PrimuseLite:V` 看「已排入扫描任务」→「本地库同步开始/结束」两对日志。若看到 `WorkerFactory` 相关异常，说明 manifest 的 initializer 移除没生效。

### 2026-11-11 · P1-04 · 权限申请与门禁（逻辑完成，真机流程待验证）
- 做了什么：把音频权限的申请、拒绝处理、永久拒绝引导全部做完，并接成 App 启动门禁。扫描现在只有在授权后才会触发。
- 新增/修改文件：
  - `feature/local/.../permission/AudioPermission.kt` — 重写。新增 `PermissionUiState` 四态、`requestable()`、`hasAskedBefore/markAsked`（SharedPreferences）、纯函数 `resolvePermissionState`
  - `feature/local/src/test/.../PermissionStateTest.kt` — 6 个单测覆盖四态判定
  - `app/.../ui/permission/AudioPermissionGate.kt` — Compose 门禁，含请求、去设置、回前台重判
  - `app/.../MainActivity.kt` — 用门禁包裹 `PrimuseApp`，授权后才触发扫描（`scanTriggered` 防重复）
- 关键决策：
  1. **权限判定做成纯函数** `resolvePermissionState(granted, shouldShowRationale, askedBefore)` — Android 的三态判断很绕，抽出来后能在无设备环境完整单测
  2. **区分「可再问」与「永久拒绝」** — 前者还能弹系统对话框，后者只能跳设置页。混为一谈会让用户点了按钮没反应
  3. **音频权限与通知权限一次请求** — 13+ 没有 `POST_NOTIFICATIONS` 扫描进度通知不显示，分两次问打扰用户
  4. **`askedBefore` 存 SharedPreferences 而非 DataStore** — 只为一个布尔值引入 DataStore 不划算，等 `core:datastore` 正式落地再迁
  5. **门禁放在 `:app` 而非 `feature:local`** — `:app` 已有 Compose，不必为一个临时门禁给 feature 模块加 Compose 插件
- 界面说明：门禁页是**功能性临时界面**，不是最终设计，已在文件头标注。UI 定稿后应替换为正式空状态页，判定逻辑可保留。
- 验证方式：
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**
  - `./gradlew test` → **25 个测试，0 失败**（原 19 + 权限 6）
- 遗留问题：
  1. **真机流程未验证**：拒绝一次能否再弹、永久拒绝是否跳设置、从设置返回后是否自动放行——三条都要设备。
  2. 门禁界面是临时的，UI 定稿后要重做。
  3. SAF 目录授权（`ACTION_OPEN_DOCUMENT_TREE`）仍未实现，属 P1-05。
- 给下一位的提醒：**测试权限流程要准备一个干净的模拟器**。`askedBefore` 存在 SharedPreferences 里，反复测试时记得清数据（`adb shell pm clear com.primuse.lite`），否则会一直走「永久拒绝」分支，看起来像 bug。

### 2026-11-11 · 真机验证 · 首次在设备上跑通全链路，修掉一个真 bug
- 设备：**OPPO PJB110，Android 12（API 32），1280×720 横屏**，通过 `adb connect 127.0.0.1:5555` 连接（另有 `:16384` 与 `emulator-5554` 两个 transport 指向同一台）。
- 验证结论：

| 任务 | 结论 | 证据 |
|---|---|---|
| P0-03 | ✅ 通过 | 截图 `docs/reference/device-verification/02-main-tabs.png`：三 Tab 可见可切换 |
| P0-04 | ✅ 通过 | logcat：`Application onCreate 开始` / `崩溃捕获已安装，启动完成` |
| P1-03 | ✅ 通过 | 1003 首全量 **376ms**、增量 **35ms**；外推 1 万首约 3.8s |
| P1-04 | ⚠️ 部分 | 门禁渲染与授权放行已验证（截图 `01-permission-gate.png`）；**拒绝/重试无法验证** |
| P1-08 | ✅ 通过 | 通知栏确认「扫描完成：共 3 首，写入 3 首」；Worker 日志 SUCCESS |

- **发现并修复的真 bug**：MediaStore 用字面量 `<unknown>` 表示缺失标签，而不是 NULL。第一轮入库后数据库里 artist 字段是字符串 `<unknown>`，直接显示到 UI 会很怪。
  - 修复：`MediaStoreTrackSource` 增加 `String?.clean()`，把 `<unknown>`、空白、null 统一转 null
  - 回归测试：`MediaStoreStringCleanTest` 4 个用例
  - 修复后真机确认：`track01|<NULL>|PrimuseTest|3000`
- 关键技术细节（下次调试直接用）：
  - **媒体库扫描**：Android 11+ 已废弃 `MEDIA_SCANNER_SCAN_FILE` 广播（`result=0` 无人接收）；`content call --method scan_file` 返回 `STREAM=null` 也无效；**唯一可用的是** `adb shell content call --uri content://media --method scan_volume --arg external_primary`
  - **数据库检查**：`adb shell run-as com.primuse.lite sqlite3 databases/primuse-lite.db "..."`
  - **截图**：Git Bash 会把 `/sdcard/x.png` 当本地路径，必须 `export MSYS_NO_PATHCONV=1`
  - **ADB 连接**：每次 Bash 调用结束后 adb daemon 会随 shell 退出被杀，必须每次重新 `adb start-server && adb connect`
- 验证方式：
  - `./gradlew test` → **29 个测试，0 失败**（新增 4 个 `<unknown>` 回归测试）
  - `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
  - 真机日志、数据库内容、通知内容均已逐项核对
- **新发现的缺口（未修，记入待办）**：
  1. **删除文件不会从库中移除**——`LocalLibrarySyncer` 只做 upsert，没有清理逻辑。用户删歌后曲库里会留幽灵条目。需要一次「全量扫描后对比库内 ID 集合，删除不存在的」。
  2. **该设备自动授予权限**，系统弹窗不出现，所以拒绝路径的状态机只有单测覆盖，没有真机验证。
  3. 横屏下界面可用但未做适配（当前是竖屏设计的居中布局）。
- 给下一位的提醒：**这台机器会跳过权限弹窗直接授予**，别以为是自己代码写错了。要测拒绝流程必须换一台正常手机或模拟器。

### 2026-11-11 · P1-03 收尾 · 幽灵条目清理 + 全量重扫入口
- 做了什么：补上「文件被删除后曲库不清理」的缺口，并加了一个全量重扫入口（删除清理与排障都需要）。
- 改了哪些文件：
  - `TrackDao`：新增 `idsBySource(source)`、`deleteByIds(ids)`
  - `ScanContract`：`LocalTrackStore` 增加 `deleteByIds`，`ScanStats` 增加 `deleted` 字段
  - `RoomLocalTrackStore`：`allIds()` 改为**只返回 LOCAL 来源**
  - `LocalLibrarySyncer`：全量扫描后做差集清理，增量扫描不清理
  - `PrimuseApp` / `MainActivity`：设置占位页加「重新扫描曲库」按钮（临时入口）
  - 单测：新增 4 个删除相关用例
- 关键决策：
  1. **只有全量扫描才清理** —— 增量扫描看不到未变更文件，据此清理会把整个曲库误删
  2. **`allIds()` 必须按来源过滤** —— 原来返回全部 id，等远程曲目进来后做差集会把它们全删掉。这是个定时炸弹，顺手拆了
  3. **安全阀：全量扫描读到 0 首时跳过清理** —— 这种情况多半是权限被撤销或媒体库异常，照常清理等于清空曲库，不可逆
  4. **`ScanStats` 增加 `deleted` 字段** —— 清理了多少条必须可观测，否则出问题查不到
- 验证方式：
  - `./gradlew test` → feature:local **29 个测试全过**（含 4 个新增删除用例）
  - 真机验证：库内 1003 条 → 删除设备上 500 个文件 → 触发全量重扫
    ```
    本地库同步开始 since=0 full=true
    清理幽灵条目 500 条
    本地库同步结束 扫描=503 写入=503 删除=500 耗时=697ms 库内总数=503
    ```
    数据库复查：`SELECT COUNT(*) WHERE localPath LIKE '%bulk08%'` → **0**
- 遗留问题：重扫按钮是临时入口，UI 定稿后并入正式设置页。
- 给下一位的提醒：清理逻辑的**安全阀别删**。如果哪天真机上曲库被清空，先看日志里有没有「全量扫描结果为空，跳过幽灵条目清理」——有说明安全阀生效了，问题在扫描侧而不是清理侧。

---

## P6-01 真机走查清单

> 全部通过才能打包。每条记录「通过 / 不通过 + 现象」。

### 本机
- [ ] 首次启动授予音频权限，能列出全部歌曲
- [ ] 拒绝权限时显示引导页，不崩溃
- [ ] SAF 添加自定义目录，杀进程重启后目录仍有效
- [ ] 同一首歌既在标准目录又在自定义目录时，只出现一条（去重生效）
- [ ] 1 万首库首次扫描 < 30 秒，二次扫描 < 3 秒
- [ ] 专辑/艺术家/文件夹三种视图分组正确
- [ ] MP3 / FLAC / WAV / M4A 各播一首，均正常
- [ ] 封面全部正确显示，快速滚动不掉帧

### 播放
- [ ] 播放中切到后台，通知栏出现控制
- [ ] 锁屏可暂停 / 下一首
- [ ] 拔耳机自动暂停
- [ ] 来电时暂停，挂断后恢复
- [ ] 顺序 / 单曲 / 随机三种模式行为正确
- [ ] 息屏 30 分钟不断播

### OpenList
- [ ] 填入**公网 HTTPS 地址（含反代子路径）**，测试连接成功
- [ ] 填 `http://` 地址时出现明文传输警告，需二次确认
- [ ] 自签证书场景下能连通（导入 CA 后）
- [ ] 能浏览远程专辑与歌曲
- [ ] 远程歌曲能播放，起播 < 3 秒
- [ ] 二次播放同一首 < 1 秒（缓存生效）
- [ ] 远程封面正确显示
- [ ] 切到蜂窝网络时不自动缓存（「仅 Wi-Fi 预加载」生效）
- [ ] 断网时给出明确提示，不崩溃
- [ ] 切到 WebDAV 通道后仍能列目录与播放

### 其他
- [ ] 歌词滚动与高亮准确
- [ ] 播放列表重启后数据不丢
- [ ] 搜索本地 + 远程均有结果
- [ ] 缓存清理后占用归零
