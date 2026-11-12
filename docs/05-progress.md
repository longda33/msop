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
- 做了什么：完成 GitHub 实测调研（原参考项目 / NeriPlayer / Auxio / OuterTune / RetroMusic / dav4jvm / OpenList），确定技术栈与 OpenList 对接通道，建立 7 阶段 35 任务的分段任务表与模型交接契约。
- 改了哪些文件：新建 `msop-android/` 下 `README.md`、`AGENTS.md`、`TASKS.md`、`docs/01–05`（01 后续已删除）。
- 验证方式：GitHub API 实采数据，见 `docs/01` 第五节复查命令（该文档后续已删除）。
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
  - app：`app/build.gradle.kts`、`app/proguard-rules.pro`、`AndroidManifest.xml`、`res/values/{strings,themes}.xml`、`MsopApplication.kt`、`MainActivity.kt`、`ui/MsopApp.kt`、`ui/theme/Theme.kt`
  - 11 个库模块：`core/{model,common,database,network,datastore}`、`feature/{local,remote,player,library,lyrics,settings}`，各含 `build.gradle.kts` + `ModuleMarker.kt`
  - 新增 `BUILD-NOTES.md`
- 验证方式：**未验证**。生成环境无 JDK / Gradle / Android SDK，无法执行 `assembleDebug`。已把 P0-01～03 标记为 `[?]`（已实现待验证）。
- 遗留问题：
  1. 首次构建需用户在装有 Android Studio 的机器上跑通，四条降级方案见 `BUILD-NOTES.md` §4。
  2. Hilt + KSP 与 AGP 9 内置 Kotlin 的兼容性存在不确定性，已备好 2 分钟移除方案。
- 给下一位的提醒：**不要在没有跑通构建前就往下写 P1**。骨架没验证，后面所有代码都建立在未验证的地基上。先让它 `assembleDebug` 通过、装上真机看到三个 Tab，再动 P1。

### 2026-11-10 · P0-00d · 撤销误写的 UI 代码，补充还原度评估
- 做了什么：用户指出「UI 没有确定，不要写」。已删除 `feature/library/ui/` 下两个界面文件，并把三个 feature 模块的 build 文件还原为不含 Compose 的纯 library 模板。工程回到「UI 零代码」状态。同时基于原参考项目的源码与截图完成 UI 还原度评估。
- 改了哪些文件：
  - 删除：`feature/library/.../ui/LibraryScreen.kt`、`MockLibraryData.kt`
  - 还原：`feature/{library,player,settings}/build.gradle.kts`
  - 重写：`docs/06-ui-spec.md` §8（待确认 Q1–Q4）、§9（还原度评估）（该文档后续已删除）
  - `AGENTS.md`：决策表移除 D13–D15，改为「UI 全部未定」；禁止事项新增「UI 未确认前不写界面代码」
  - 新增：`docs/reference/reference-ios/` 四张官方截图（后续已删除）
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
- 改了哪些文件：`app/build.gradle.kts`（testOptions + androidComponents）、`app/src/main/java/.../ui/MsopApp.kt`（图标）、`.gitignore`（加 `*.log`）、`TASKS.md`、`AGENTS.md` §2
- 验证方式：
  - `./gradlew tasks` → **BUILD SUCCESSFUL in 30s**
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 1m**，无 `w:` 警告，APK 12 MB（10:13 重新产出）
  - `git init` + 首次提交
- 遗留问题：**无 adb 设备、无模拟器**（`adb devices` 为空，SDK 未装 emulator），P0-02 的「能启动」与 P0-03 的「真机可见底部 Tab」仍无法验收，两项状态分别为 `[x]`（仅构建）与 `[~]`。
- 给下一位的提醒：**AGP 9 的 `beforeVariants` 静态类型不完整**，遇到类似 `Unresolved reference` 先打印 `variantBuilder.javaClass.methods` 确认属性是否存在，再考虑强转接口，不要盲目改写法。另外装个模拟器（SDK Manager → Emulator + system image）就能解锁 P0-03 和后续所有 UI 验收。

### 2026-11-11 · P0-04 + P1-02 · 日志/崩溃捕获 + Room 数据层
- 做了什么：
  1. **P0-04**：`core:common` 增加 `AppLog`（统一 TAG `Msop`）与 `CrashReporter`（记录后交回系统默认处理，不吞异常），`MsopApplication` 在 `onCreate` 安装。
  2. **P1-02**：`core:database` 落地 Room——`TrackEntity`（17 字段 / 4 索引）、`TrackDao`（Flow 观察 + Upsert + 增量扫描用的 `latestModified`）、`MsopDatabase`、`Converters`、`TrackMapper`，并配 `room.schemaLocation` 导出 schema。
  3. 补 `TrackMapperTest` 4 个纯 JVM 单测（本地/远程/来源类型/可空字段往返），让 `./gradlew test` 真正有东西可跑。
  4. 新增 `.gitattributes` 固定换行符，避免 `gradlew` 在非 Windows 环境被 CRLF 破坏。
- 改了哪些文件：
  - 新增：`core/common/.../log/AppLog.kt`、`core/common/.../crash/CrashReporter.kt`
  - 新增：`core/database/.../entity/TrackEntity.kt`、`dao/TrackDao.kt`、`MsopDatabase.kt`、`Converters.kt`、`mapper/TrackMapper.kt`、`src/test/.../TrackMapperTest.kt`
  - 新增：`core/database/schemas/.../1.json`（自动生成，入库）
  - 修改：`MsopApplication.kt`、`core/database/build.gradle.kts`（ksp + room + junit）、`gradle/libs.versions.toml`（junit 4.13.2）、`.gitattributes`
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
  - `core/database/.../di/DatabaseModule.kt` — 提供 `MsopDatabase`（`fallbackToDestructiveMigration`）与 `TrackDao`
  - `feature/local/.../di/LocalModule.kt` — 提供 `TrackPageSource` / `LocalTrackStore` / `LocalLibrarySyncer`
  - `feature/local/.../work/LocalScanWorker.kt` — `@HiltWorker` CoroutineWorker，失败重试上限 3 次
  - `feature/local/.../work/ScanNotifier.kt` — 进度/完成/失败通知，含 Android 13+ 通知权限判断
  - `feature/local/.../work/LocalScanScheduler.kt` — `enqueueUniqueWork + KEEP`，避免叠加任务
  - `feature/local/src/main/res/` — 通知渠道文案与 `ic_scan.xml` 图标
  - 修改：`LocalLibrarySyncer.sync` 增加 `onProgress` 回调；`MsopApplication` 实现 `Configuration.Provider`；`MainActivity` 启动时按权限触发扫描；`AndroidManifest` 移除 WorkManager 默认初始化
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
- 给下一位的提醒：想真机验证这条链路，需要先装模拟器，然后手动授权 `READ_MEDIA_AUDIO`（或先在 P1-04 把申请流程做出来），再 `adb logcat -s Msop:V` 看「已排入扫描任务」→「本地库同步开始/结束」两对日志。若看到 `WorkerFactory` 相关异常，说明 manifest 的 initializer 移除没生效。

### 2026-11-11 · P1-04 · 权限申请与门禁（逻辑完成，真机流程待验证）
- 做了什么：把音频权限的申请、拒绝处理、永久拒绝引导全部做完，并接成 App 启动门禁。扫描现在只有在授权后才会触发。
- 新增/修改文件：
  - `feature/local/.../permission/AudioPermission.kt` — 重写。新增 `PermissionUiState` 四态、`requestable()`、`hasAskedBefore/markAsked`（SharedPreferences）、纯函数 `resolvePermissionState`
  - `feature/local/src/test/.../PermissionStateTest.kt` — 6 个单测覆盖四态判定
  - `app/.../ui/permission/AudioPermissionGate.kt` — Compose 门禁，含请求、去设置、回前台重判
  - `app/.../MainActivity.kt` — 用门禁包裹 `MsopApp`，授权后才触发扫描（`scanTriggered` 防重复）
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
- 给下一位的提醒：**测试权限流程要准备一个干净的模拟器**。`askedBefore` 存在 SharedPreferences 里，反复测试时记得清数据（`adb shell pm clear com.msop.player`），否则会一直走「永久拒绝」分支，看起来像 bug。

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
  - 修复后真机确认：`track01|<NULL>|MsopTest|3000`
- 关键技术细节（下次调试直接用）：
  - **媒体库扫描**：Android 11+ 已废弃 `MEDIA_SCANNER_SCAN_FILE` 广播（`result=0` 无人接收）；`content call --method scan_file` 返回 `STREAM=null` 也无效；**唯一可用的是** `adb shell content call --uri content://media --method scan_volume --arg external_primary`
  - **数据库检查**：`adb shell run-as com.msop.player sqlite3 databases/msop.db "..."`
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
  - `MsopApp` / `MainActivity`：设置占位页加「重新扫描曲库」按钮（临时入口）
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

### 2026-11-11 · P1-05 · SAF 自定义目录（含一次真机抓到的设计缺陷）
- 做了什么：支持用户通过 SAF 添加媒体库之外的目录，扫描时与 MediaStore 合并。
- 新增文件：
  - `saf/SafDirectoryStore.kt` — tree URI 持久化（SharedPreferences）
  - `saf/SafPaths.kt` — `documentIdToAbsolutePath()`，把 `primary:Music/x.flac` 还原成绝对路径
  - `saf/SafTrackSource.kt` — BFS 遍历目录树，`ContentResolver.query(childrenUri)` 批量取一层
  - `scan/CompositeTrackSource.kt` — 串联多个来源
  - `LocalModule` 改为提供组合来源；`MsopApp` / `MainActivity` 加「添加音乐目录」入口
  - 单测：`SafPathsTest`（7）、`CompositeTrackSourceTest`（5）
- **真机抓到的设计缺陷（重要）**：
  - 第一版把 MediaStore 放前面、SAF 放后面。结果 SAF 那份「只有路径、无 album、时长 0」的记录把 MediaStore 的丰富元数据**覆盖**了：
    ```
    扫描=1006 写入=1006 库内总数=503
    track01|<NULL>|0        ← album 丢了，时长变 0
    ```
  - 根因：`CompositeTrackSource` 按顺序发出，后写入者覆盖前者，而我把**信息更少**的来源放在了最后。
  - 修复：顺序改为 **SAF 在前、MediaStore 在后**，并补了 `CompositeTrackSourceTest` 固定这个约束（含一个「顺序反过来会退化」的反向用例）。
  - 修复后：`track01|MsopTest|3000`、`bulk0000|bulk|50`，元数据恢复。
- 关键技术点：
  1. **不用 `DocumentFile.listFiles()`** —— 逐层 IPC，几万文件要几分钟。直接对 children URI 做 `query`，一次拿一层全部子项。
  2. **BFS + 显式队列** —— 深目录递归会爆栈。
  3. **ID 必须跨通道一致** —— MediaStore 与 SAF 都基于绝对路径哈希，所以同一文件两条通道扫到也只入库一条（实测扫描 1006、入库 503）。
  4. **`takePersistableUriPermission` 必须紧跟 picker 回调** —— 否则重启后 tree URI 失效。
- 验证方式：
  - `./gradlew test` → feature:local **41 个测试全过**
  - 真机：设置页「添加音乐目录」→ 系统选择器选 Music → 授权弹窗允许 → 自动全量重扫
  - 持久化：`shared_prefs/reference_saf_dirs.xml` 确认存了 tree URI；force-stop 重启后全量扫描仍读到 1006 条
  - 性能：503 个 SAF 文件遍历 878ms（外推 1 万约 18s，目标 40s）
- 遗留问题：
  1. **同一目录被重复添加**不会去重（Set 去重了，但用户看不出来）；也没有「移除目录」的入口。
  2. SAF 来源拿不到标签（artist/album/duration），完全依赖 MediaStore 覆盖。若某文件只在 SAF 里、不在 MediaStore，元数据就是空的——P1-07 用 `MediaMetadataRetriever` 补。
  3. 横屏下选择器与设置页布局可用但未适配。
- 给下一位的提醒：**`CompositeTrackSource` 的顺序不是随意的**，`CompositeTrackSourceTest` 里有一条反向用例专门锁这个。要加新来源时先想清楚「谁的元数据更全」，把它放最后。

### 2026-11-11 · P1-06 去重收尾 · P1-07 元数据与封面

- 做了什么：
  1. **P1-06**：去重计数从「批内」改成「全程」。原来同一文件被 SAF 与 MediaStore 各返回一次时落在不同页/不同来源，`deduped` 永远是 0，日志看起来像「扫描 1006 写入 1006」，实际库里只有 503 条。
  2. **P1-07**：新增 `feature/local/metadata/`，用 `MediaMetadataRetriever` 补标签、按内容哈希把内嵌封面落盘、`coverUri` 回写数据库；全量扫描后回收没人引用的封面。
- 新增文件：`metadata/TrackMetadata.kt`、`MetadataValues.kt`、`MetadataMerge.kt`、`MetadataReader.kt`、`CoverCache.kt`、`MetadataStore.kt`、`RoomMetadataStore.kt`、`CoverProvider.kt`、`LocalMetadataEnricher.kt`、`scan/ScanMerge.kt`
- 改动的文件：`LocalLibrarySyncer`（全程去重 + written 语义）、`RoomLocalTrackStore`（入库前合并）、`TrackDao`（4 个查询）、`LocalScanWorker`（扫描后补齐 + 回收）、`LocalScanScheduler`（全量用 REPLACE）、`ScanNotifier` + `strings.xml`、`LocalModule`（装配）、`MsopApp` / `MainActivity`（临时「预热封面」入口）

- **真机抓到的真 bug（重要）**：重扫会把封面和 SAF 标签一起抹掉。
  - 现象：预热封面后 `coverUri` 有值，点「重新扫描曲库」→ 504 行全部变回 `coverUri=NULL`，缓存文件被当成孤儿删掉，等于白提。
  - 根因：入库是整行 `@Upsert`，而扫描器只看得见文件系统，给的是 `coverUri=null`；SAF 通道的 artist/album/duration 也一直是 null。每次重扫都是「用更少的信息覆盖更全的信息」。
  - 修复：`RoomLocalTrackStore.upsertAll` 先 `findByIds` 取本页已有记录，用 `mergedWithExisting` 合并——**扫描值优先，空缺才用库里的**。合并只发生在扫描入库路径，`RoomMetadataStore` 的写入不受影响。
  - 代价：用户在文件里删掉某个标签时同步不过来（扫描给 null，会保留旧值）。两害相权，保住补出来的数据更重要，记入已知缺口。

- 关键决策：
  1. **`durationMs = 0` 作为「还没读过标签」的标记** —— 不加列、不升数据库版本。时长是任何能解析的音频都有的字段，读不到就说明我们没读过；而「artist 为空」不能当判据（有些文件本来就没标签，会每次扫描重读一遍，永远补不完）。读取失败时时长仍是 0，下次扫描自动重试。
  2. **封面按内容哈希命名，不按曲目 id** —— 一张专辑 12 首歌内嵌同一张图，按曲目存 12 份；按内容存天然合并。实测同一张图两次预热命中同一个文件名。
  3. **封面按需提取，不进扫描主流程** —— 1000 首各开一次 retriever 要几十秒，而用户可能只看前 20 行。标签是列表必须先有的数据，封面不是。
  4. **补标签失败不影响扫描结果** —— Worker 里包了 try/catch，标签只是锦上添花，曲库能列出歌才是主任务。
  5. **全量扫描用 `REPLACE`，增量用 `KEEP`** —— 原来全量也走 KEEP：用户点「重新扫描」时若正好有增量在跑，请求会被静默丢弃，点了没反应。打断扫描是安全的（按确定性 ID upsert）。
  6. **封面回收只在全量扫描后做** —— 那是唯一会删曲目的路径，增量不可能产生新的孤儿文件。

- 验证方式（全部真机，OPPO PJB110 / Android 12）：
  - `./gradlew test` → **86 个测试，0 失败**（新增 31 个：`MetadataValuesTest` 7、`MetadataMergeTest` 6、`CoverCacheTest` 9、`LocalMetadataEnricherTest` 10、`ScanMergeTest` 6、`LocalLibrarySyncerTest` +3）
  - **去重可观测**：全量 `扫描=1006 写入=503 删除=0 去重=503 耗时=635ms`
  - **标签补齐**：用 ffmpeg 造了一个带完整 ID3 + 300×300 PNG 封面的 MP3，推到 `/sdcard/Music/MsopTest/` 且**不让媒体库索引**（模拟 SAF-only 文件）。扫描后 `元数据补齐 候选=1 更新=1 耗时=416ms`，库里得到 `封面测试曲|测试歌手|测试专辑|3030|7|2024`（trackNo 从 `7/12` 解析、year 从 `2024` 解析）
  - **封面落盘**：`封面预热 候选=30 成功=1 无封面=29 耗时=400ms`，DB 得到 `file:///data/user/0/.../covers/1594...jpg`，`exec-out` 取回校验为 **936 字节的 300×300 PNG**，与源文件内嵌图一致
  - **重扫不丢**：全量重扫后 `coverUri` 与标签全部存活，缓存文件没被回收
  - **删除回收**：删掉文件 → `scan_volume` → 全量重扫 `清理幽灵条目 1 条`、`清理孤立封面 1 张`，库里 503 条、缓存目录空
  - 顺带确认一个现象：文件删了但 MediaStore 还索引着时，全量扫描是 `删除=0` 的——因为 MediaStore 仍然返回它。这类「幽灵」要等系统重新扫描媒体库才会消失，不是我们的清理逻辑漏了。

- 遗留问题：
  1. 封面缓存没有容量上限，只有孤儿回收；设置页的清理入口待 P4-05。
  2. 标签删除不同步（见上）。
  3. 只实测了 MP3（带封面）与 WAV（无标签），FLAC / M4A 未实测。
  4. 列表页封面显示与滚动性能要等 P1-09，所以 P1-07 只能标 `[?]`。
- 给下一位的提醒：**`mergedWithExisting` 不是可选的优化，是防止重扫退化的补丁**。以后往扫描器里加字段时，问一句「这个字段扫描器拿得到吗」，拿不到就得进合并列表，否则每次重扫都会被抹掉。

### 2026-11-11 · P2-01 / P2-02 · 播放内核与后台服务

- 做了什么：
  1. **P2-01**：`feature:player` 接入 Media3，`PlayerFactory` 统一构造 ExoPlayer，`Track → MediaItem` 映射带上通知栏要用的标题/歌手/专辑/封面。
  2. **P2-02**：`PlaybackService`（`MediaSessionService`）+ `PlaybackController`（`MediaController` 封装，暴露 `StateFlow<PlaybackState>`）；清单注册服务与 `WAKE_LOCK` 权限。
  3. 顺带把 P2-03 的队列与模式、P2-04 的音频焦点/拔出暂停配置一起落地。
- 新增文件：`PlayMode.kt`、`TrackMediaItem.kt`、`PlayerFactory.kt`、`PlaybackService.kt`、`PlaybackController.kt`（删掉了该模块的 `ModuleMarker`）
- 改动的文件：`feature/player/build.gradle.kts`（media3 + hilt + ksp）、`AndroidManifest.xml`（service + WAKE_LOCK）、`MsopApp` / `MainActivity`（临时播放/暂停/下一首/模式入口）

- 关键决策：
  1. **播放器活在 Service 里，UI 只拿 MediaController** —— 页面来去、Activity 重建都不影响播放；控制器断开重连也不打断声音。
  2. **`PlayerFactory` 是唯一构造点**，三个开关写死在这里：音频焦点（来电/导航自动暂停续播）、耳机拔出暂停、`setWakeMode`（息屏不断）。散落在各处配这些开关，早晚漏一个。
  3. **用 `MediaSessionService` 而不是自己写前台服务** —— 通知栏、锁屏、耳机按键、车机控制全由 Media3 通过系统 MediaSession 接好，自己写要重复实现一遍还容易漏 `foregroundServiceType`。
  4. **`mediaId` 用 `Track.id`，不用 uri** —— 远程地址以后会带一次性签名，每次都不一样；`Track.id` 是稳定的，状态恢复和队列对账都靠它。
  5. **`next()` 在队尾回到第一首** —— 用户按了按钮总得有反应；自动续播仍按 repeat 设置走（顺序模式播完就停），两者不冲突。
  6. **`ResolvingDataSource` 现在恒等转发** —— 留着是给 P3 的接缝：远程鉴权/重定向在 DataSpec 层改写，不去污染 MediaItem 的 URI。
  7. **`onTaskRemoved`：在播就继续，没播就停服务** —— 后台播放是本分，但别留一个空进程。

- 验证方式（真机 OPPO PJB110 / Android 12）：
  - 单测仍是 **86 个 0 失败**（播放层靠真机验收，没有加单测——MediaController 需要真实服务，纯 JVM 测不出东西）
  - **三种格式**：MP3（120s，带内嵌封面）、WAV（5s）、FLAC（150s）均 `state=3` 且 position 持续推进，无 `ExoPlaybackException`
  - **通知栏**：`channel=default_channel_id, category=transport, vis=PUBLIC`，三个动作「上一项/暂停/下一项」，`android.largeIcon=Icon(typ=BITMAP size=72x72)`（封面解码成功），标题/歌手正确
  - **后台播放**：按 HOME 后 6 秒仍 `state=3`，`dumpsys activity services` 显示 `isForeground=true foregroundId=1001`
  - **外部控制**：媒体键 85 → `state=2`（暂停）、再按 → `state=3`、87 → `active item id=1`（下一首）
  - **单曲循环**：5 秒曲目切到 REPEAT_ONE，9 秒后仍 `state=3`、同一 item、`position=974ms`（已重头）
  - **随机**：SHUFFLE 下连按三次下一首，item id `1 → 432 → 428 → 283`，明显非顺序
- 踩到的坑：
  1. `cmd media_session dispatch pause` 在这台 ROM 上抛 `packageName may not be empty`，改用 `input keyevent 85/87`（走的是同一条媒体键通路，反而更接近锁屏/耳机真实行为）。
  2. `MediaSession.Builder.setSessionActivity()` 参数非空，`getLaunchIntentForPackage` 可能返回 null，要 `?.let`。
  3. 播放测试曲（120s MP3 + 150s FLAC）留在设备 `/sdcard/Music/MsopTest/`，下次验播放直接用，别删。

- 遗留问题：
  1. **P2-04 三个场景没测**：拔耳机、来电打断、导航播报，adb 模拟不了，需要手动。配置已经就位（焦点/拔出开关都在 `PlayerFactory`）。
  2. **队列拖拽排序没有 UI**：`PlaybackController.moveInQueue` 已实现，等 P2-05/P4-04。
  3. **锁屏没截图验证**：走的是同一条 MediaSession 通路且通知 `vis=PUBLIC`，媒体键已实测，但没敢锁屏——设备若设了密码就回不来了。
  4. 通知用的是 Media3 默认小图标与默认通道名，正式化留给 P4-05。
- 给下一位的提醒：**要改播放行为，先看 `PlayerFactory`**，那里三个开关决定了后台/焦点/耳机的表现。调试时 `dumpsys media_session | grep -E "state=PlaybackState|metadata:"` 一眼能看出在播什么、暂停没有。

### 2026-11-11 · P2-04 收尾 · 耳机拔出与来电打断（真机实测）

- 背景：`PlayerFactory` 里两个开关（`setAudioAttributes(handleAudioFocus=true)`、`setHandleAudioBecomingNoisy(true)`）早就配好了，但「三个场景实测通过」这条一直空着——adb 没法拔耳机，设备上也没装任何音乐/视频应用能来抢焦点。
- 本轮补的是**验收手段**，不是功能：
  1. **耳机拔出**：`AUDIO_BECOMING_NOISY` 是系统保护广播，shell 直接发会被 `SecurityException` 拒掉；设备有 `su`，`adb root` 后以 uid 0 发送即可。实测播放中 `state=3 → 2`，立刻暂停。
  2. **来电打断**：新增 `AudioFocusProbe`（**临时验收物**）——本进程的另一个焦点客户端申请 `AUDIOFOCUS_GAIN_TRANSIENT`（来电用的就是这个类型），AudioService 会给正在播放的 ExoPlayer 发 `LOSS_TRANSIENT`。实测 `state=3 → 2`。
  3. **挂断恢复**：5 秒后探针 `abandonAudioFocusRequest`，AudioService 回 `GAIN`，ExoPlayer 自己续播。实测 `state=2 → 3`，`dumpsys audio` 里能看到 `AudioFocusManager ... gain: GAIN ... notified: true`。
- 踩到的两个坑（都会静默失败，很难查）：
  1. action 写成了 `android.intent.action.AUDIO_BECOMING_NOISY`——**正确的常量值是 `android.media.AUDIO_BECOMING_NOISY`**。广播发出去 result=0，播放毫无反应，因为根本没有接收器匹配。
  2. 改成正确 action 后报 `Permission Denial ... from uid=2000`。保护广播只认系统/root，必须 `adb root`。
- 验证完已 `adb unroot` 把 adbd 还原成 shell，别在用户设备上留 root 状态。
- 遗留：`AudioFocusProbe` 和「模拟来电打断」按钮是临时的，P4-05 重写设置页时一起删。
- 给下一位的提醒：这两条命令都在 `AGENTS.md` §8「真机调试速查」里，验播放回归时直接抄。

---

### 2026-11-11 · UI 设计 · 定稿提案 v1.0 + 可交互原型（9 屏）

- 背景：`docs/06` 从 11-10 起就停在「草案 · 未确认」，Q1–Q4 悬着，P1-09 / P2-05 / P4-\* / P5-02 全部挂起。数据层和播放内核已经把能做的都做完了，UI 成了唯一瓶颈。
- 本轮产出两份东西，都是**设计物，不是代码**（`feature/*/ui/` 依旧没动）：
  1. `docs/07-ui-design.md` —— 定稿提案，回答 Q1–Q4，给出色彩/字体/间距令牌、9 屏规格、手势总表、横屏适配、无障碍要求、Compose 落地映射与验收清单。
  2. `docs/design/prototype.html` —— 单文件离线原型，8 屏手机 + 1 屏横屏，点击屏幕内的导航/播放条能真的走一遍。支持 `#player`、`#queue` 这类锚点直达。
- 四个决策：**3 Tab**（音乐库/搜索/设置，歌单移到音乐库顶栏）/ **歌曲行显示 44dp 封面**（另给紧凑模式）/ **播放页用自定义全屏浮层**（不是 `ModalBottomSheet`）/ **B 路线**（Material 3 承载既定视觉语言）。
- 两个自有主张，是这版和 06 草案最大的区别：
  1. **双空间**：浏览态跟随系统主题、浅色密集；播放态**恒定深色**，不受系统主题影响。收益是三点——大封面在深底上对比最强、OLED 息屏省电、从任何入口进播放页都是同一个「房间」。
  2. **封面主色驱动**：播放页背景不用模糊封面（Compose 在 API 31 以下做不了实时模糊，且切歌掉帧），改成从封面取一个主色做 12% 径向渐晕。**一行代码，视觉收益 80%**。
- 顺带修掉一个 06 没意识到的问题：**真机是 1280×720 横屏**，而 06 只写了手机竖屏。07 补了三档断点（<600 / 600–839 / ≥840），横屏走 `NavigationRail` + 双栏，队列面板改成右侧抽屉。
- 验证方式：浏览器直接打开 `docs/design/prototype.html`。已用 Edge 无头模式逐屏截图核对过，修掉了三个渲染问题——首屏透明度动画不收敛导致整体发灰、队列页继承了浅色主题的深色文字在黑底上不可见、手机外框尺寸公式漏算 padding 导致高度被压。
- 给下一位的提醒：
  1. **用户没点头之前仍然不要写界面代码**，`docs/07` §10 的验收清单是给实现阶段的，不是开工许可。
  2. 实现顺序别改：`UiTokens.kt` → `TrackRow` + 歌曲页 → `MiniPlayer` + `PlayerSheet`。先把「点歌能放、能收起」跑通，再铺其他屏。
  3. 三个技术风险点写在 07 §9.3，其中**共享元素转场**最容易翻车——`SharedTransitionLayout` 不稳就降级成「淡入 + scale 0.92→1」，视觉损失可接受，别在这上面耗三天。

---

### 2026-11-11 · UI 落地 · 11 个文件，编译通过，APK 20 MB

- 用户确认「修改项目UI」后开工。**视觉基准是 `preview/index.html`**（照搬既定设计的深色 + 蓝色 + 胶囊形态，结构照搬 NeriPlayer / MusicFree），不是 `docs/07` 那版浅色双主题。
- 新增 11 个文件，全部在 `app/src/main/java/com/reference/lite/ui/`：
  `MsopApp.kt`、`theme/Theme.kt`、`icons/PlayerIcons.kt`、`components/TrackRow.kt`、`components/MiniPlayer.kt`、`library/LibraryScreen.kt`、`library/AlbumDetail.kt`、`library/LibraryViewModel.kt`、`player/PlayerSheet.kt`、`player/QueueSheet.kt`、`search/SearchScreen.kt`、`settings/SettingsScreen.kt`。
- 三个值得记的决定：
  1. **UI 不拆到 feature 模块**。`feature:library` / `feature:settings` 是纯 Kotlin 库，没有 Compose / Hilt / Coil 依赖；为 UI 单独配一遍会引入跨模块 KSP 风险。自用 App 先在 app 模块跑通，需要复用时再下沉。
  2. **图标自绘，不引 `material-icons-extended`**。那个包未混淆时给 APK 加约 8 MB、给编译加几十秒，而播放器只需要 10 个图标（暂停、上下曲、随机、循环、队列、歌词、云、文件夹、唱片）。`PlayerIcons.kt` 用 `ImageVector.Builder` 描路径，零依赖。
  3. **歌词页显示「暂无歌词」而不是假歌词**。LRC 解析是 P5-01，还没做。`LyricsPane` 的结构留着，接上时间轴即可，但绝不放假数据骗自己。
- 顺手清理：删掉 `AudioFocusProbe` 与「模拟来电打断」按钮（P2-04 已验收，AGENTS 里早写明 P4-05 重写设置页时删）；`PlaybackController` 新增 `currentPositionMs()`，给进度条轮询用——不放进 `PlaybackState` 是因为 `Player.Listener` 只在事件时回调，播放中不推位置，让可见的页面自己拉更省。
- 编译踩的坑：`ImageVector.Builder.path` 是扩展函数，必须 `import androidx.compose.ui.graphics.vector.path`；`Icons.Filled.PlayArrow` 也要单独 import（`Icons.Filled` 只提供对象本身，不导出成员）。
- **未验证**：设备当时不在线（`adb connect 127.0.0.1:5555` 拒绝连接），只做到编译通过 + 单测通过，**一次都没装到真机上跑**。验收清单写在 AGENTS §8「UI 已落地，待真机验收」，第一件事是装机。
- 给下一位：`scripts/check.sh` 第 6 项已改，现在检查 `app/ui` 而不是 `feature/*/ui`，别再被旧提示误导。

---

### 2026-11-11 · UI 真机验收 · 三 Tab / 播放页 / 队列 / 搜索 / 设置全部走通

- 设备：`emulator-5554`，PJB110，900×1600 @320dpi（450×800dp），竖屏。**注意这台是模拟器，不是之前的无线真机**，所以 adb 用 `-s emulator-5554`，不需要 `adb connect`。
- 逐项结果：
  1. 三 Tab 切换、选中胶囊、内容正确。
  2. 点歌 → `state=3` 播放中，胶囊迷你条带封面/标题/暂停键/进度线。
  3. **拖动进度条 seek：拖到 66.7% → `position=100008ms`（曲长 150s），误差 < 1s，P2-05 的验收标准达成。**
  4. 播放页封面/歌词双态切换正常。
  5. 队列面板 505 首，当前曲高亮。
  6. 搜索输入 FLAC 即时过滤出 1 条，当前播放行蓝色高亮 + 三根音柱。
  7. 专辑网格 2 列等宽，**真实封面（粉红色那张）能正常加载**——Coil 读 `file://` 没问题。
  8. 设置页分组、目录数、版本号（0.1.0）正确。
- 验收中修掉两个真问题：
  1. **封面占位图标按比例缩放是个坑**。`fillMaxSize(0.36f)` 在 44dp 列表封面上是 16dp（合适），在 300dp 播放页封面上变成 108dp 的大圆环，喧宾夺主。改成 `iconSize` 参数，播放页传固定 56dp。
  2. **播放页缺 `navigationBarsPadding()`**。它是全屏 overlay，不走 Scaffold 的 inset 处理，全面屏手势条会压住底部「歌词/封面/队列」。补上。
- 踩的坐标坑：`screencap` 出的图是 900×1600，但 `ViewImage` 显示时会缩到 608×1080（0.675）。**按截图目测的坐标去 `input tap` 会全部偏移**，第一次点歌点到了「艺术家」Tab。正确做法是先 `uiautomator dump` 拿真实 `bounds`，或者按 0.675 换算。
- adb 在这台机器上每次新 shell 都要 `adb start-server`，否则 `device offline`。所有 adb 命令要串在同一个 bash 调用里。
- 未做（属后续任务，不是 bug）：队列拖拽排序、播放页下滑收起手势、封面模糊背景、歌词解析。

---

### 2026-11-11 · P4-03 / P4-04 · 歌单持久化 + 播放页手势与模糊背景

- P4-03 歌单：
  - Room 升到 v2，新增 `playlists` / `playlist_tracks` 两张表，外键 `CASCADE`。**写显式 `MIGRATION_1_2`，不 destructive**——设备上已有 505 首歌，升级后实测全在。
  - 入口在音乐库右上角「更多 → 播放列表」，全屏浮层展示，不占导航返回栈。支持新建、重命名、加歌、删歌；`TrackRow` 菜单多一项「从歌单移除」。
  - 真机流程：新建 `TestMix` → 加 2 首 → 移除 1 首 → 改名 `Mix2` → 强停进程重启 → 歌单与剩余曲目都在。
- P4-04 播放页：
  - 背景改为 Coil `AsyncImage` + `Modifier.blur(64.dp)` + 暗色竖向渐变；无封面时保留原来的金/蓝径向渐晕。
  - 手势**只挂在封面区、顶栏和歌词头部**：上滑打开队列，下滑收起播放页。挂根布局会抢走歌词 `LazyColumn` 的滚动，实测会把页面误收起——这个坑已经踩过。
  - 收起动画用 `Animatable` 位移 + 透明度，返回键只收起浮层，不进系统返回栈。
- 验证方式：`emulator-5554` 上逐项操作并截图；歌单数据用强停重启核对，播放页手势用真实滑动验证（歌词页内滚动不触发收起）。
- 遗留问题：队列拖拽排序仍未做（P2-03 的 UI 部分）；封面缓存容量上限仍属 P4-05。
- 给下一位的提醒：Room 迁移必须继续写显式版本，别为了省事开 `fallbackToDestructiveMigration()`。

---

### 2026-11-11 · P5-01 / P5-02 · 内嵌歌词 + 歌词滚动高亮真机验收

- 新增 `EmbeddedLyrics.kt`：纯 JVM 解析 MP3 的 ID3v2.3/v2.4 `USLT` 与 FLAC 的 Vorbis Comment `LYRICS`。只读到元数据块就停，不把整首音频读进内存；任何异常返回 `null`，不影响播放。
- `LyricsRepository` 改为两级查找：**同名 `.lrc` 优先，内嵌歌词兜底**。内嵌文本若带 `[mm:ss]` 时间标签，按 LRC 解析；没有时间标签则用 `Lyrics.plain()` 退化静态全文。
- `Lyrics` 增加 `synced` 标志：静态歌词不高亮、不自动滚动、点击不 seek；播放页显示「歌词未同步 · 仅展示全文」。
- 单测新增 13 个：ID3v2.3/v2.4、ISO-8859-1 / UTF-16 / UTF-8、内容描述跳过、非 USLT 帧、ID3v2.2 拒绝、FLAC 多块跳过、无歌词、损坏数据、静态歌词拆分。全量 `.\gradlew.bat test` → **114 个用例，0 失败**；`:app:assembleDebug` 通过。
- 真机验证（`emulator-5554`）：
  1. 构造带真实 `USLT` 的 `EmbeddedSynced.mp3`（无 `.lrc`）→ 播放页显示 6 行歌词，5 秒时第一行蓝色高亮；点第五行 → `position=42.7s`，高亮跳到第五行。
  2. `EmbeddedPlain.mp3` 的 `USLT` 没有时间标签 → 显示「歌词未同步 · 仅展示全文」+ 四行静态歌词，无高亮。
  3. `SidecarWins.mp3` 同时有内嵌歌词和同名 `.lrc` → 显示 `.lrc` 的两行，内嵌内容没有出现，优先级正确。
- **踩坑**：ffmpeg 9 的 `-metadata lyrics=...` 写出来的是 `TXXX:USLT`，不是真正的 `USLT` 帧，解析器读不到。真机验收用的文件是手写 ID3v2.3 标签（Python 脚本在 `%TEMP%\reference-lyrics-test\build_tags.py`，未入库）。以后造测试素材别信 ffmpeg 的 lyrics 元数据。
- 遗留问题：**远程歌词未做**，依赖 P3 远程音源；P5-01 因此保持 `[~]`。FLAC 内嵌歌词只有单测，设备上没有造 FLAC Vorbis 样本。
- 给下一位的提醒：真实世界里有标签器把 UTF-16 空描述写成单字节 `0` 后直接跟 BOM，解析器已兼容；改 `decodeUslt` 时别把这个分支删掉。

---

### 2026-11-11 · P4-05（本地部分）· 设置页封面缓存大小与清理

- 新增 `SettingsViewModel`：注入 `CoverCache`，异步统计张数与字节数，清理后重新统计。`count()` / `sizeBytes()` / `clear()` 都是现成的，没有改缓存实现。
- 设置页「存储 → 封面缓存」显示「N 张 · X MB」，点击弹确认框；确认后清理并刷新。顺手把「歌词」行从「未接入」改成「已启用」，副标题写清「同名 .lrc 优先，内嵌歌词兜底」。
- 删掉验收用的「封面预热」调试入口：`MainActivity` 不再注入 `LocalMetadataEnricher`，也不再弹 Toast；`warmCovers()` 方法保留，留给以后首屏静默预热。
- 踩坑：第一版 `refreshCache()` 调 `loadCache(loading = true)` 后没有把 loading 置回 false，界面永远停在「统计中…」。改成先置 loading、读完置 false。
- 验证方式：`emulator-5554` 上进入设置 → 显示「1 张 · 1 KB」→ 点开确认框 → 清理 → 显示「0 张 · 0 B」；`run-as ... ls files/covers` 目录为空。
- 遗留问题：P4-05 的服务器配置部分仍待 P3-07，因此任务保持 `[~]`。均衡器仍标「未接入」，待 P5-03。

---

### 2026-11-11 · P3-01 ~ P3-07 · OpenList 远程音源端到端打通（阻塞解除）

- 做了什么：
  1. 用户提供真实公网地址与账号，P3 阻塞解除。**先实测再写码**：本机默认代理对 `*.koyeb.app` 返回 502、直连超时，只有 `--proxy http://127.0.0.1:10808` 通。结论：OpenList v4.2.9 **未启用 Subsonic**（真实路由 `@rest` 前缀返回 404，`media_enabled=false`，账号 `role=0` 非 admin 无法开启）；WebDAV `PROPFIND /dav/` 返回 207 可用但 GET 是 302；**原生 REST 全通**且 `raw_url` 支持 206 Range。据实测反证把决策 D3 从 Subsonic 修订为「OpenList 原生 REST 主通道」，完整响应样例见 `docs/03` §6。
  2. `core:datastore` 落地服务器配置 + JWT 缓存；`core:network` 落地 `ServerUrl`（补协议/去尾斜杠/剥误粘页面名 + `HttpUrl.addPathSegments` 拼路径）、`OpenListClient`（登录、列目录、取直链、401 重登、HTTP 码与业务码双层映射、中文错误）、`StreamUrlResolver` + Hilt `@Binds`。
  3. `feature:remote` 落地 `RemoteIdFactory`、`RemoteTrackMapper`、`RemoteLibrarySyncer`（BFS 递归列目录、音频后缀过滤、按 `idsBySource` 做差集清理、扫到 0 首时跳过清理的安全阀）。
  4. `PlayerFactory` 的 `ResolvingDataSource` 从恒等转发换成真实接缝：起播 / seek 重新打开 DataSpec 时把 `openlist:/路径` 换成新鲜 `raw_url`；`PlayerFactory` 改由 Hilt 注入，`PlaybackService` 用 `@Inject`。
  5. `network_security_config.xml` 全局禁明文 + 允许 user CA；配置页支持 URL 规范化、`http://` 二次确认、测试连接、保存并同步、清除配置与远程曲目。
- 改了哪些文件：新增 `core/datastore/{ServerConfig,ServerConfigStore}.kt`、`core/network/{ServerUrl,OpenListDtos,OpenListException,StreamUrlResolver,OpenListClient}.kt` + `di/NetworkModule.kt`、`feature/remote/{RemoteIdFactory,RemoteTrackMapper,RemoteLibrarySyncer}.kt`、`app/ui/settings/{OpenListConfigScreen,OpenListViewModel,BatteryOptimization}.kt`、`app/src/main/res/xml/network_security_config.xml`、`USAGE.md`；修改 `PlayerFactory.kt`、`PlaybackService.kt`、`MsopApp.kt`、`SettingsScreen.kt`、`MainActivity.kt`、`AndroidManifest.xml`、4 个模块的 `build.gradle.kts`、`docs/03-openlist-api.md`、`AGENTS.md`、`TASKS.md`。删除三个已落地模块的 `ModuleMarker.kt`。
- 验证方式：
  - `./gradlew test` → debug **18 文件 / 154 用例 0 失败**（新增 40 个：URL 规范化 11、均衡器 14、远程映射 9、队列拖拽 6）。
  - 真机（emulator-5554 / Android 12，设 `settings put global http_proxy 10.0.2.2:10808` 借道主机代理）：保存并同步 → `扫描 23 项 · 写入 23 首 · 清理 0 条 · 454 ms`，DB `LOCAL|508 / OPENLIST|23`；搜索「Butter-Fly」命中远程歌；点播 → `state=3`、`CCodecConfig: audio/flac`、无 `ExoPlaybackException`；拖动进度条 → position `68129 → 208546` 且仍 `state=3`（Range + 直链刷新都生效）。
- 遗留问题：P3-04 的 `SimpleCache` 与「仅 Wi-Fi 预加载」、P3-05 WebDAV 兜底未做；服务端无封面、列目录无时长（列表显示 `--:--`，播放页由 Media3 解析正确）；模拟器留了全局 `http_proxy` 设置（可 `adb shell settings delete global http_proxy` 还原）。
- 给下一位的提醒：**三条硬规律别破**——① `raw_url` 带 tempauth 有时效，绝不入库，只存 `openlist:/路径`；② GET 直链**不能**带 `Authorization`（实测加 JWT 会 401），完全交给 Media3；③ HTTP 200 也可能是业务码 401，两层都要判。另外 **KDoc 里写 `/@rest/*` 这种字面量会开启 Kotlin 块注释**，一路吞到文件末尾、报错显示在最后一行且 KSP 会伪装成「类无法解析」——本轮踩过一次。

---

### 2026-11-11 · P5-03 / P5-05 / 已知缺口 / P6-02 / P6-03 · 打磨与打包

- 做了什么：
  1. **均衡器**：`EqualizerTuning`（纯 JVM：10 段标准曲线、7 个预设、对数频率最近邻映射、mB 单位钳制）+ `EqualizerController`（SharedPreferences 持久化、`@Synchronized`、attach/release）。`PlaybackService` 在 `onAudioSessionIdChanged` attach；UI 按**设备实际 band 数**画滑杆（这台只有 5 段），回写时映射回标准 10 段模型。
  2. **关 offload**：Media3 1.11 **删掉了** `DefaultRenderersFactory.setEnableAudioOffload`，改用 `AudioTrackAudioOutputProvider.Builder.setAudioOffloadSupportProvider` 上报 `AudioOffloadSupport.DEFAULT_UNSUPPORTED`。不关的话 EQ 会「开着没效果」。
  3. **后台保活**：`stopWithTask=false`、`MODIFY_AUDIO_SETTINGS`、设置页显示电池优化豁免状态并跳系统弹窗，回前台用 `LifecycleEventObserver(ON_RESUME)` 重判。
  4. **补两个已知缺口**：设置页「本机目录」改成可列出/移除目录（移除时释放 persistableUriPermission + 全量重扫）；队列加长按手柄拖拽（拖动中只累计位移+高亮，松手才 `moveInQueue`，避免 Compose 重组吞手势）。
  5. **release 打包**：生成自签名 `app/release.jks` + `keystore.properties`（都进 `.gitignore`），`isMinifyEnabled/isShrinkResources = true`，补 R8 规则（重点是 kotlinx.serialization 的 `$$serializer`）；`USAGE.md` 交付。
- 验证方式：
  - `sh scripts/check.sh` 六项全过；`./gradlew test` 通过；`:app:assembleDebug` 19.3 MB、`:app:assembleRelease` **3.0 MB**（5m4s，V2 签名 `apksigner verify` 通过）。
  - 均衡器真机：显示「已连接播放器 · 5 段 · 已关闭 audio offload」，点「摇滚」后 5 段变为 `+3.5/-1.0/+1.0/+3.5/+4.0 dB`，logcat `均衡器预设=摇滚`，设置页变「已启用」。
  - **release 包真机回归**（先 `adb uninstall` 再装）：权限门禁 → 授予 → 本地扫描出歌 → 配置页输入域名/账号/密码 → 自动补 `https://` → 同步 `23 首 / 867 ms` → 搜索远程歌 → 播放 `state=3`、metadata 正确、`AndroidRuntime:E` 与 `ExoPlaybackException` 均无输出。R8 没有误删 Media3 / Hilt / Room / kotlinx.serialization。
- 遗留问题：P5-04 大库性能、P6-01 走查清单未逐条勾；`./gradlew test` 偶发一次 `kspReleaseKotlin` 报 `OpenListClient could not be resolved`（debug/release 并行竞态，重跑即过，非代码问题）；`lintVitalRelease` 会误报 `PlaybackService` 的 `Instantiatable`（服务在 feature 模块、父类来自 implementation 依赖），manifest 已局部 `tools:ignore`，别删。
- 给下一位的提醒：release 与 debug **签名不同**，互相覆盖安装必须 `adb uninstall`（会清曲库/歌单/服务器配置）。`keystore.properties` 与 `.jks` 绝不入库；换机器按 `USAGE.md` §6 重新生成即可，没有密钥时构建会退回 debug 签名而不是失败。

---

### 2026-11-11 晚 · P3 · Subsonic 启用后改为混合通道（元数据 + 真实封面）

- 做了什么：
  1. 用户启用 OpenList 媒体库并提供**媒体应用密钥**。先读 v4.2.9 源码 `server/handles/subsonic.go` 的 `subAuthInner`，再实测四种鉴权：**只有 `apiKey=<应用密钥>` 通**；`u`+`t`(md5) 与 `u`+`p` 都返回 `code=40`。
  2. 六接口实测：`ping/getMusicFolders/getArtists/getAlbumList2/getAlbum/getSong/search3/getCoverArt` 全通；**`search3` 空查询一次返回全部 23 首**（省掉 22 次 `getAlbum`）；`getCoverArt` 返回真实 `image/jpeg` 129 KB；`getLyricsBySongId` 返回 `code=70`。
  3. 两个否决项：`stream` 302 跳到 **`http://`** 明文 `/d/...?sign=`，被 `network_security_config` 拦；Subsonic 的 `path` 是服务器绝对路径，喂给 `/api/fs/get` 返回 `500 object not found`（要相对用户根目录的路径）。**因此流播不切 Subsonic**。
  4. 落地混合方案：`core:network` 新增 `SubsonicDtos` / `SubsonicClient`（`@rest` + `apiKey`、`search3` 分页、`HttpUrl` 拼 `getCoverArt`、错误码 40/50 归为鉴权失败）；`core:datastore` 配置加可选 `appKey`；`feature:remote` 新增 `SubsonicMetadata`（按**文件名**匹配，纯函数），同步后补全 `title/artist/album/albumId/mimeType/size/coverUri`，失败只降级；配置页加「Subsonic 应用密钥（可选）」并显示是否启用。
  5. 修掉两个「静默不显示」的封面 bug：① app 只依赖 `coil-compose`、**缺 `coil-network-okhttp`**，本地 `file://` 正常、远程 `https://` 静默失败；② 补依赖后 debug 正常、**release 又变占位**——R8 删掉了 Coil 的 `META-INF/services/coil3.util.FetcherServiceLoaderTarget` 与实现类。最终在 `MsopApplication` 显式实现 `SingletonImageLoader.Factory` 并 `add(OkHttpNetworkFetcherFactory())`，proguard 里加 keep 兜底。
- 改了哪些文件：新增 `core/network/{SubsonicDtos,SubsonicClient}.kt`、`feature/remote/SubsonicMetadata.kt`、`feature/remote/src/test/.../SubsonicMetadataTest.kt`；修改 `core/datastore/{ServerConfig,ServerConfigStore}.kt`、`feature/remote/RemoteLibrarySyncer.kt`、`app/ui/settings/{OpenListViewModel,OpenListConfigScreen}.kt`、`app/build.gradle.kts`（coil 依赖）、`docs/03`（新增 §7）、`AGENTS.md`、`TASKS.md`、`USAGE.md`。
- 验证方式：
  - `:core:network:testDebugUnitTest`、`:feature:remote:testDebugUnitTest`、`:app:assembleDebug` 全过；`SubsonicMetadataTest` 11 个用例。
  - 真机（emulator-5554）：配置页输入 45 位密钥 → `同步完成：扫描 23 项 · 写入 23 首 · 补全 23 首 · 清理 0 条 · 8476 ms`；DB `23 首 / 23 有封面 / 23 有艺术家 / 23 有 albumId`；样本 `Rage Your Dream · m.o.v.e · 頭文字D Final Best Collection`；列表显示真实 JPEG 封面，Coil 磁盘缓存 180 KB；点播 `state=3`、metadata 正确、无 `ExoPlaybackException`。
- 遗留问题：远程歌词服务端没有；`duration` 仍拿不到（列表 `--:--`，播放页由 Media3 解析）；P3-04/05、P5-04、P6-01 未做。
- 给下一位的提醒：**Subsonic 与原生 REST 各管一段，不要混用**——Subsonic 只用来取元数据和封面，流播一律走 `/api/fs/get`。配置里的「密码」是 OpenList 登录密码，「应用密钥」是 Subsonic 的 `apiKey`，两者不能互换。排查封面问题时先看 `coil-network-okhttp` 是否还在依赖里。

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
