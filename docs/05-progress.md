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
- 给下一位的提醒：**不要跳过 P3-01 的实测直接写 Subsonic 客户端**。`03-openlist-api.md` 里所有标 ⚠️ 的接口都必须先用 curl 验证再落代码。

### 2026-11-10 · P0-00b · 确认部署形态，任务表扩容至 38 项
- 做了什么：用户确认两个关键参数——**本机默认使用标准目录（MediaStore）、支持自定义目录（SAF）**；**OpenList 部署在公网**。据此调整架构与任务表。
- 改了哪些文件：
  - `AGENTS.md`：状态区新增两行参数；硬约束新增第 7、8 条（强制 HTTPS、双通道去重）；决策表新增 D7–D10
  - `02-architecture.md`：新增 §8 公网访问与网络安全（HTTPS 配置、反代子路径、自签证书、超时与流量）、§9 本机扫描双通道（MediaStore / SAF 性能写法 / 去重规则）；风险表新增 3 行
  - `03-openlist-api.md`：新增 §1.1 公网部署四个前提；curl 模板改为公网子路径写法并加证书诊断步骤
  - `TASKS.md`：P1 从 7 项拆为 9 项（MediaStore / 权限 / SAF / 去重 独立成任务）；P3 从 6 项增至 7 项（新增 P3-06 网络安全配置）；总任务 35 → 38，工期 8–10 周 → 9–11 周
- 验证方式：文档一致性自查，任务号与依赖引用已全部对齐。
- 遗留问题：OpenList 的 Subsonic 挂载路径仍待 P3-01 实测；公网域名与是否有反代子路径需用户提供后填进 `03-openlist-api.md` 的 curl 模板。
- 给下一位的提醒：**写 URL 一律用 `HttpUrl.newBuilder().addPathSegments()`，禁止字符串拼接**——公网带子路径时字符串拼接必错，这是本项目最容易踩的坑。

### 2026-11-10 · P0-00c · 澄清「反代子路径」，补 URL 规范化规则
- 做了什么：解释反向代理子路径的两种挂法（独立域名根路径 / 域名下的 `/openlist` 前缀），并决定**不依赖用户预先判断属于哪种**，改为在配置页做 URL 规范化，两种情况通吃。
- 改了哪些文件：
  - `03-openlist-api.md`：新增 §1.2 URL 规范化规则（补协议、去尾斜杠、去重复斜杠、剥误粘的 `index.html`），附 `normalizeBaseUrl()` 参考实现
  - `TASKS.md`：P3-07 验收标准加入规范化用例
- 验证方式：文档自查。
- 遗留问题：仍建议用户提供实际公网地址，用于把 `03-openlist-api.md` 的 curl 模板填成可直接执行的版本（P3-01 要用）。
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
  - 重写：`06-ui-spec.md` §8（待确认 Q1–Q4）、§9（还原度评估）（该文档后续已删除）
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
  - `README.md`：顶部加当前进度提示；目录树补全 BUILD-NOTES、05-progress.md–06、reference
  - 新增 `../scripts/check.sh`：无需 JDK 的完整性自检（构建文件、TOML 解析、模块目录、源码存在性、禁用插件、UI 代码状态）
- 验证方式：`sh ../scripts/check.sh` 六项全部通过；`find` 核对 53 个文件与文档描述一致；`grep` 确认无残留的 `AGENTS.md §5` 失效引用。
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
| P0-03 | ✅ 通过 | 截图 `../archive/reference-device-verification/02-main-tabs.png`（已移出仓库）：三 Tab 可见可切换 |
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
  1. `07-ui-design.md` —— 定稿提案，回答 Q1–Q4，给出色彩/字体/间距令牌、9 屏规格、手势总表、横屏适配、无障碍要求、Compose 落地映射与验收清单。
  2. `docs/design/prototype.html` —— 单文件离线原型，8 屏手机 + 1 屏横屏，点击屏幕内的导航/播放条能真的走一遍。支持 `#player`、`#queue` 这类锚点直达。
- 四个决策：**3 Tab**（音乐库/搜索/设置，歌单移到音乐库顶栏）/ **歌曲行显示 44dp 封面**（另给紧凑模式）/ **播放页用自定义全屏浮层**（不是 `ModalBottomSheet`）/ **B 路线**（Material 3 承载既定视觉语言）。
- 两个自有主张，是这版和 06 草案最大的区别：
  1. **双空间**：浏览态跟随系统主题、浅色密集；播放态**恒定深色**，不受系统主题影响。收益是三点——大封面在深底上对比最强、OLED 息屏省电、从任何入口进播放页都是同一个「房间」。
  2. **封面主色驱动**：播放页背景不用模糊封面（Compose 在 API 31 以下做不了实时模糊，且切歌掉帧），改成从封面取一个主色做 12% 径向渐晕。**一行代码，视觉收益 80%**。
- 顺带修掉一个 06 没意识到的问题：**真机是 1280×720 横屏**，而 06 只写了手机竖屏。07 补了三档断点（<600 / 600–839 / ≥840），横屏走 `NavigationRail` + 双栏，队列面板改成右侧抽屉。
- 验证方式：浏览器直接打开 `docs/design/prototype.html`。已用 Edge 无头模式逐屏截图核对过，修掉了三个渲染问题——首屏透明度动画不收敛导致整体发灰、队列页继承了浅色主题的深色文字在黑底上不可见、手机外框尺寸公式漏算 padding 导致高度被压。
- 给下一位的提醒：
  1. **用户没点头之前仍然不要写界面代码**，`07-ui-design.md` §10 的验收清单是给实现阶段的，不是开工许可。
  2. 实现顺序别改：`UiTokens.kt` → `TrackRow` + 歌曲页 → `MiniPlayer` + `PlayerSheet`。先把「点歌能放、能收起」跑通，再铺其他屏。
  3. 三个技术风险点写在 07 §9.3，其中**共享元素转场**最容易翻车——`SharedTransitionLayout` 不稳就降级成「淡入 + scale 0.92→1」，视觉损失可接受，别在这上面耗三天。

---

### 2026-11-11 · UI 落地 · 11 个文件，编译通过，APK 20 MB

- 用户确认「修改项目UI」后开工。**视觉基准是 `../preview/index.html`**（照搬既定设计的深色 + 蓝色 + 胶囊形态，结构照搬 NeriPlayer / MusicFree），不是 `07-ui-design.md` 那版浅色双主题。
- 新增 11 个文件，全部在 `app/src/main/java/com/reference/lite/ui/`：
  `MsopApp.kt`、`theme/Theme.kt`、`icons/PlayerIcons.kt`、`components/TrackRow.kt`、`components/MiniPlayer.kt`、`library/LibraryScreen.kt`、`library/AlbumDetail.kt`、`library/LibraryViewModel.kt`、`player/PlayerSheet.kt`、`player/QueueSheet.kt`、`search/SearchScreen.kt`、`settings/SettingsScreen.kt`。
- 三个值得记的决定：
  1. **UI 不拆到 feature 模块**。`feature:library` / `feature:settings` 是纯 Kotlin 库，没有 Compose / Hilt / Coil 依赖；为 UI 单独配一遍会引入跨模块 KSP 风险。自用 App 先在 app 模块跑通，需要复用时再下沉。
  2. **图标自绘，不引 `material-icons-extended`**。那个包未混淆时给 APK 加约 8 MB、给编译加几十秒，而播放器只需要 10 个图标（暂停、上下曲、随机、循环、队列、歌词、云、文件夹、唱片）。`PlayerIcons.kt` 用 `ImageVector.Builder` 描路径，零依赖。
  3. **歌词页显示「暂无歌词」而不是假歌词**。LRC 解析是 P5-01，还没做。`LyricsPane` 的结构留着，接上时间轴即可，但绝不放假数据骗自己。
- 顺手清理：删掉 `AudioFocusProbe` 与「模拟来电打断」按钮（P2-04 已验收，AGENTS 里早写明 P4-05 重写设置页时删）；`PlaybackController` 新增 `currentPositionMs()`，给进度条轮询用——不放进 `PlaybackState` 是因为 `Player.Listener` 只在事件时回调，播放中不推位置，让可见的页面自己拉更省。
- 编译踩的坑：`ImageVector.Builder.path` 是扩展函数，必须 `import androidx.compose.ui.graphics.vector.path`；`Icons.Filled.PlayArrow` 也要单独 import（`Icons.Filled` 只提供对象本身，不导出成员）。
- **未验证**：设备当时不在线（`adb connect 127.0.0.1:5555` 拒绝连接），只做到编译通过 + 单测通过，**一次都没装到真机上跑**。验收清单写在 AGENTS §8「UI 已落地，待真机验收」，第一件事是装机。
- 给下一位：`../scripts/check.sh` 第 6 项已改，现在检查 `app/ui` 而不是 `feature/*/ui`，别再被旧提示误导。

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
  1. 用户提供真实公网地址与账号，P3 阻塞解除。**先实测再写码**：本机默认代理对 `*.koyeb.app` 返回 502、直连超时，只有 `--proxy http://127.0.0.1:10808` 通。结论：OpenList v4.2.9 **未启用 Subsonic**（真实路由 `@rest` 前缀返回 404，`media_enabled=false`，账号 `role=0` 非 admin 无法开启）；WebDAV `PROPFIND /dav/` 返回 207 可用但 GET 是 302；**原生 REST 全通**且 `raw_url` 支持 206 Range。据实测反证把决策 D3 从 Subsonic 修订为「OpenList 原生 REST 主通道」，完整响应样例见 `03-openlist-api.md` §6。
  2. `core:datastore` 落地服务器配置 + JWT 缓存；`core:network` 落地 `ServerUrl`（补协议/去尾斜杠/剥误粘页面名 + `HttpUrl.addPathSegments` 拼路径）、`OpenListClient`（登录、列目录、取直链、401 重登、HTTP 码与业务码双层映射、中文错误）、`StreamUrlResolver` + Hilt `@Binds`。
  3. `feature:remote` 落地 `RemoteIdFactory`、`RemoteTrackMapper`、`RemoteLibrarySyncer`（BFS 递归列目录、音频后缀过滤、按 `idsBySource` 做差集清理、扫到 0 首时跳过清理的安全阀）。
  4. `PlayerFactory` 的 `ResolvingDataSource` 从恒等转发换成真实接缝：起播 / seek 重新打开 DataSpec 时把 `openlist:/路径` 换成新鲜 `raw_url`；`PlayerFactory` 改由 Hilt 注入，`PlaybackService` 用 `@Inject`。
  5. `network_security_config.xml` 全局禁明文 + 允许 user CA；配置页支持 URL 规范化、`http://` 二次确认、测试连接、保存并同步、清除配置与远程曲目。
- 改了哪些文件：新增 `core/datastore/{ServerConfig,ServerConfigStore}.kt`、`core/network/{ServerUrl,OpenListDtos,OpenListException,StreamUrlResolver,OpenListClient}.kt` + `di/NetworkModule.kt`、`feature/remote/{RemoteIdFactory,RemoteTrackMapper,RemoteLibrarySyncer}.kt`、`app/ui/settings/{OpenListConfigScreen,OpenListViewModel,BatteryOptimization}.kt`、`app/src/main/res/xml/network_security_config.xml`、`USAGE.md`；修改 `PlayerFactory.kt`、`PlaybackService.kt`、`MsopApp.kt`、`SettingsScreen.kt`、`MainActivity.kt`、`AndroidManifest.xml`、4 个模块的 `build.gradle.kts`、`03-openlist-api.md`、`AGENTS.md`、`TASKS.md`。删除三个已落地模块的 `ModuleMarker.kt`。
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
  - `sh ../scripts/check.sh` 六项全过；`./gradlew test` 通过；`:app:assembleDebug` 19.3 MB、`:app:assembleRelease` **3.0 MB**（5m4s，V2 签名 `apksigner verify` 通过）。
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
- 改了哪些文件：新增 `core/network/{SubsonicDtos,SubsonicClient}.kt`、`feature/remote/SubsonicMetadata.kt`、`feature/remote/src/test/.../SubsonicMetadataTest.kt`；修改 `core/datastore/{ServerConfig,ServerConfigStore}.kt`、`feature/remote/RemoteLibrarySyncer.kt`、`app/ui/settings/{OpenListViewModel,OpenListConfigScreen}.kt`、`app/build.gradle.kts`（coil 依赖）、`03-openlist-api.md`（新增 §7）、`AGENTS.md`、`TASKS.md`、`USAGE.md`。
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

### 2026-11-12 · P4-04 · 播放页适配与 msop 启动图标
- 做了什么：按用户要求更新播放器布局：竖屏当前歌词居中；横屏左侧为封面、曲目信息和播放控制，右侧只显示歌词行。横屏静态歌词不再显示额外的未同步状态行。使用用户上传的 `mslogo.png` 配置启动器图标，并将 Gradle 项目名设为 `msop`（应用名原本已是 `msop`）。
- 改了哪些文件：`app/src/main/java/com/msop/player/ui/player/PlayerSheet.kt`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/drawable-nodpi/msop_icon.png`、`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`、`app/src/main/res/values/colors.xml`、`settings.gradle.kts`、`AGENTS.md`、`TASKS.md`、`07-ui-design.md`、`docs/design/prototype.html`。
- 验证方式：`sh ../scripts/check.sh` → 自检通过；12 个模块、Version Catalog、禁用项和 UI 结构检查均通过；图标确认为 256×256 RGBA PNG。**本机补跑 `./gradlew test` → BUILD SUCCESSFUL，165 用例 0 失败**。
- 遗留问题：改动初期有两处 `Modifier.padding(horizontal = …, top = …)` 的重载错误（Compose 没有这个组合），已改为 `start/end/top/bottom` 并通过编译；设备上的竖屏居中、横屏分栏与图标遮罩效果尚未真机确认。
- 给下一位的提醒：优先运行 `./gradlew :app:assembleDebug test`，再检查竖屏歌词居中、横屏右侧无重复曲目信息或状态文案、启动器图标遮罩效果。

---

### 2026-11-12 — 全 UI 视觉重构（Aurora Glass）

- 做了什么：
  1. 用户反馈「UI 太丑」后整体重做视觉层，功能与交互路径不变。设计方向定为 Aurora Glass：深空墨底 + 极光紫/薄荷青点缀、玻璃渐变卡片、悬浮胶囊底部导航、程序化生成封面。
  2. 设计令牌集中在 `ui/theme/Theme.kt`：`Palette`（Ink/Void/Surface/Surface2/3/Line/Blue/BlueLight/Teal/Red/Green/Gold/Text*）、`Dim`、`Shapes`、`Gradients`、`MsopTypography`。
  3. 新增 `ui/components/Surfaces.kt`：`AmbientBackground`、`Modifier.glassSurface`、`SectionHeader`、`PrimaryPill`、`GlassPill`、`CircleGlassButton`、`IconTile`、`InsetDivider`。
  4. 新增 `ui/icons/Artwork.kt`：`GeneratedArtwork` 按 seed 哈希出固定渐变 + 光斑 + 唱片圆环；`ArtistAvatar` 生成头像首字，替换灰底占位。
  5. 重写 `MsopApp`（悬浮胶囊导航 + 玻璃迷你条）、`LibraryScreen`（胶囊分类 + 漏斗来源筛选 + 自适应专辑网格 + 艺术家/文件夹卡）、`AlbumDetail`、`SearchScreen`（统计卡 + 随机入口 + 最近搜索/播放/添加）、`SettingsScreen`（App Hero + 卡片分组）、`PlayerSheet`（模糊/生成背景 + 大圆角封面 + 自绘 SeekBar + 白色主播放键 + 玻璃底栏 + 歌词逐行淡出/逐字高亮）、`QueueSheet`、`PlaylistScreen`、`MiniPlayer`、`TrackRow`。
  6. 首启 `AudioPermissionGate` 改为极光背景 + 生成封面圆盘 + 玻璃说明卡；`EqualizerScreen` / `OpenListConfigScreen` 补 AmbientBackground 与玻璃状态卡。
  7. 修复歌词「手动拖动后跟随不恢复」：原 `LaunchedEffect(dragged)` 在松手早于 5s 时被取消，`autoFollow` 永远停在 false；改为拖拽中暂停、松手后延迟 5s 恢复。
  8. 新增 `PlayerIcons.Filter` 漏斗图标。
- 涉及文件：`ui/` 下 15 个修改 + 2 个新增（`Surfaces.kt`、`Artwork.kt`）；`AGENTS.md`、`TASKS.md`、`05-progress.md`、`07-ui-design.md`。
- 验证方式：
  - `.\gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL（debug APK 19.1 MB）
  - `.\gradlew.bat test` BUILD SUCCESSFUL（19 个测试类 / 168 个用例，0 失败）
  - `.\gradlew.bat :app:assembleRelease` BUILD SUCCESSFUL（release APK 3.3 MB，R8 + V2）
  - `sh ../scripts/check.sh` 自检通过（12 模块、24 个 UI 文件、未误用 kotlin.android、未开明文流量）
- 遗留问题：本机 `adb devices` 为空，SDK 无 emulator/system-images，**无法安装 APK 做纯视觉与手势走查**；功能路径未改，但仍需在有设备时复验小屏/横屏、底部导航与迷你条重叠、播放页歌词滚动、权限页。
- 给下一位：改 UI 从 `ui/theme/Theme.kt` 的令牌开始，不要在各页面另调色值；缺封面一律走 `GeneratedArtwork(seed = track.artSeed())`，不要再加灰底占位；UI 仍未拆到 `feature/*`，别把界面挪进 feature 模块。

---

### 2026-11-12 — P3-04 远程边播边缓存（SimpleCache）+「仅 Wi-Fi 缓存」

- 做了什么：
  1. 用户「开始 P3-04」。远程歌曲播放时把读过的字节写进 `SimpleCache`（2 GB LRU），
     二次播放 / 拖回已听过的区间直接从本地读；本机文件不进缓存。
  2. **链的顺序是本任务的核心**：`CacheDataSource` 包在 `ResolvingDataSource`
     **外面**，缓存 key 用稳定的 `openlist:/路径`。这样 key 不受 raw_url 的
     tempauth 影响，且缓存全命中时连 `/api/fs/get` 都不用调（详见 `03-openlist-api.md` §8.1）。
  3. 新增「仅 Wi-Fi 缓存」开关（默认开）。开启时蜂窝网络下**不写新缓存**，
     但已缓存内容照常读、照常播：构造 `CacheDataSource` 时把写侧 `DataSink`
     传 `null`，Media3 走只读路径。开关在每次创建 DataSource 时同步重读，
     下次切歌 / seek 即生效。
  4. 设置页「存储」组新增：远程播放缓存曲目数 + 占用（含 2 GB 上限）、
     一键清理（二次确认）、开关行。清理走 `Cache.removeResource(key)`，
     不直接删文件，避免 SQLite 索引留幽灵条目。
  5. 可观测性：`PlayerFactory` 在**缓存未命中**时打一条
     `远程缓存未命中，换取新鲜直链 uri=...`，真机可用 logcat 对比第一次与第二次播放。
- 涉及文件：新增 `core/datastore/CachePreferences.kt`、
  `feature/player/.../cache/{MediaCache,MediaCachePolicy,RemoteCacheDataSource}.kt`、
  `feature/player/src/test/.../cache/MediaCachePolicyTest.kt`；
  修改 `PlayerFactory.kt`、`feature/player/build.gradle.kts`、`app/build.gradle.kts`、
  `ui/settings/{SettingsScreen,SettingsViewModel}.kt`、
  `AGENTS.md`、`TASKS.md`、`USAGE.md`、`03-openlist-api.md`（新增 §8）。
- 验证方式：
  - `sh ../scripts/check.sh` → 自检通过（12 模块、Version Catalog、禁用项）
  - `./gradlew :app:assembleDebug :app:assembleRelease test` → **BUILD SUCCESSFUL**（7m23s），
    debug APK 18 MB、release APK 3.2 MB（R8 + lintVitalRelease + V2 签名均通过）
  - JVM 单测 **20 个测试类 / 173 个用例 / 0 失败**，新增 `MediaCachePolicyTest` 5 例
    （写入决策 6 组合、远程 URI 判定、不同 tempauth 得到同一 key、本地 URI 被拒）
  - `mapping.txt` 确认 release 保留了 `SimpleCache`、`RemoteCacheDataSourceFactory`、
    `MediaCachePolicy`；debug/release dex 中都能查到缓存日志与「仅 Wi-Fi 缓存」文案
- 踩坑：`NET_CAPABILITY_METERED` 在 Android SDK 里**根本不存在**（只有正向能力
  `NET_CAPABILITY_NOT_METERED`），第一版按记忆写完 `:feature:player:compileDebugKotlin`
  直接报 unresolved reference。已改成 `TRANSPORT_CELLULAR || !hasCapability(NOT_METERED)`。
  另一个坑是 `CacheDataSource.Factory.setCacheWriteDataSinkFactory(null)` 并不能禁用写
  （Factory 会 fallback 到默认 `CacheDataSink`），必须直接构造 `CacheDataSource` 并传
  `null` DataSink 才是只读模式。
- 遗留问题：本机 `adb devices` 为空，**缓存命中效果未真机复验**，P3-04 因此标 `[?]`。
- 给下一位：按 `03-openlist-api.md` §8.4 的 adb 步骤复验三条（二次播放无「缓存未命中」日志、
  蜂窝下占用不涨但已缓存歌照常播、清理后归零），通过就把 `TASKS.md` 的 P3-04 改 `[x]`。
  别把缓存 key 换成 raw_url、别把 `CacheDataSource` 挪到 `ResolvingDataSource` 内层、
  别在 `PlaybackService.onDestroy` 里 `SimpleCache.release()`（服务会反复重建）。

### 2026-11-12 — UI 评审与失效设计资产清理

- 用户要求「评审现有 UI 设计并提出优化方案 / 删除无用部分」。
- **评审结论**：Aurora Glass 实现本身成立（`Palette` 351 次 / `Shapes` 65 次真实引用、
  `glassSurface` 用 1dp 高光描边模拟玻璃而不开 `RenderEffect`、`GeneratedArtwork` 按 seed
  哈希稳定复现），失效的是**围绕视觉的文档与资产**。`07-ui-design.md` v1.0 是重构前提案：§1 原则
  「无渐变装饰」与实现相反、§3.2 浅/深双主题 + 靛蓝 ↔ 恒定深色 + 极光紫、§3.4 圆角
  6/12/20 ↔ 11/18/30、§9.2 九条组件路径**无一存在**、§10 验收清单全空、§7 要求的横屏
  三档断点从未实现。`../preview/verify/` 里 `app8.png` / `player4.png` 是旧版灰方块视觉，
  `ui_check*.png` 才是当前版，混放会让下一位接手的人拿错参考。
- **量化依据**：24 个 UI 文件 / 7,517 行；466 处硬编码 `X.dp` vs `Dim.*` 仅 48 次（9.7:1）；
  77 处硬编码 `Color(0x...)`；`Gradients` 5 个成员里 3 个零引用；`Dim.navHeight` 零引用
  而 `MsopApp` 写死 `62.dp`；触摸目标多处 < 48dp；47 处 `contentDescription` 里 18 处 null。
  结论是**颜色/形状令牌健康、只有尺寸令牌半失效**，方向不是重建令牌体系。
- **删除**：`../preview/verify/` 43 个文件 / 20.3 MB（未跟踪临时截图 + demo 音频）、
  `docs/design/prototype.html` 58 KB、`Theme.kt` 的 `Gradients.teal/ember/coverScrim`、
  `07-ui-design.md` 的 6 个失效章节（§3 旧视觉语言、§9.1/9.2 错误令牌与路径、§10 空清单、
  §11 差异汇总、§1 冲突原则）。
- **保留并修正**：归档 `../preview/baseline/{library,library_playing,player}.png` 三张当前视觉
  基线；`../preview/index.html` 留作历史溯源但加醒目警示条 + 改 title + 改误导性令牌注释；
  `MsopApp.BottomNav` 的 `62.dp` → `Dim.navHeight`；`07-ui-design.md` 从 484 行重写为 v2.0
  「当前实现说明」（真实令牌表、组件表、7 条实测缺口、P1/P2 待办、验收建议）；同步
  `AGENTS.md` §2/§13、`README.md`、`../scripts/check.sh`、`TASKS.md` 变更记录。
- **明确不动**：`ModuleMarker.kt`（`check.sh` 与契约依赖）、模块边界（契约已定 UI 不按
  feature 拆）、Aurora Glass 全部视觉实现（P1/P2 需真机定稿，本次只做 P0 清理）、
  本文档的历史记录（描述当时发生的事，不因现状修订）。
- **验证**：`../scripts/check.sh` 自检通过；`./gradlew :app:assembleDebug test` →
  **BUILD SUCCESSFUL**（3m17s，592 tasks / 23 executed），debug APK 正常产出，
  `testDebugUnitTest` 与 `testReleaseUnitTest` 全绿。代码改动仅限 `Theme.kt`（删 3 个
  零引用成员）与 `MsopApp.kt`（令牌替换 + import），无视觉行为变化。
- **给下一位**：改视觉后重截 `../preview/baseline/` 三张，避免再出现新旧混放。P1 三项
  （横屏三档断点、云端为 0 时来源行收敛、播放页功能行定稿 4 项）需要设备确认后再动；
  **别照 `07-ui-design.md` v1.0 的浅色/双栏规格写**——那份规格从未落地，v2.0 才是当前事实。

---

### 2026-11-12 · P7-00 · ShuYin 功能融入计划建立（P7 启动）

- 做了什么：
  1. 对照 ShuYin（GPL-3.0）盘点功能，逐项询问用户后确认范围：播放状态恢复、
     桌面悬浮歌词（完整版）、断网保护 + 缓冲提示、备份/恢复、TTML + 音质信息、
     Haze 标题栏毛玻璃 + 液态玻璃导航 + iOS 式转场、WebDAV 独立第三音源、libFLAC。
     明确不做 40 语言、主题/深浅色、个人资料、推荐、更新检查、启动向导、APE、
     经典播放页、登录 streak。
  2. 定下「只借鉴功能、不复制 GPL 代码」的独立重写原则；msop 保持现有许可。
  3. 新建 `08-shuyin-integration.md` 作为 P7 唯一计划与进度看板（12 项任务、
     验收标准、依赖预研、libFLAC 阻塞说明）。
  4. `AGENTS.md` 硬约束第 2 条改为允许本机 + OpenList + WebDAV 三种音源；
     新增 D21（WebDAV 第三音源口径）与 D22（独立重写）。
  5. `TASKS.md` 新增 P7 十二项，总任务 38 → 50，P3-05 并入 P7-07。
- 改了哪些文件：`08-shuyin-integration.md`（新增）、`AGENTS.md`、`TASKS.md`、
  `05-progress.md`。
- 技术预研结论：
  - Haze 在 Maven Central 有 1.7.3 / 2.1.0，可加；
  - `androidx.media3:media3-decoder-flac` **未发布**（Google Maven group-index 无此
    artifact，1.11.1 POM 返回 404），libFLAC 必须 vendor Media3 `decoder_flac` 源码；
  - 本机 SDK **没有 NDK / CMake**，P7-12 标 `[!]` 阻塞，待前 11 项完成后再处理；
  - dav4jvm 仓库坐标待确认（JitPack metadata 401），必要时按 AGENTS D4 允许的最小
    PROPFIND + Range 客户端实现。
- 验证方式：文档一致性检查（`../scripts/check.sh` 后续补跑）；本轮未改业务代码。
- 遗留问题：P7-01 播放页进度条回归需先在真机上用 `uiautomator dump` 确认真实坐标再修；
  之前几次点击可能落在封面区，不能仅凭截图目测判定。
- 给下一位：**P7 的顺序不要改**——先修 P7-01，再做数据/播放能力，最后做重 UI 与
  libFLAC。每完成一项先更新 `08-shuyin-integration.md` 看板，再改 `TASKS.md` 与本文档，不要攒到最后。

### 2026-11-14 · P7-01 · 播放页进度条回归修复（代码完成，真机待验）

- 做了什么：
  1. 定位到回归来自 Aurora Glass 重构：Material3 `Slider` 被换成自绘 `SeekBar`，
     旧实现只在抬手时提交 seek，且触摸坐标按整个 Box 宽度换算（视觉轨道左右各内缩 8dp）。
  2. 重写 `SeekBar` 手势：命中区 30dp → 44dp；补 `progressBarRangeInfo` / `setProgress`
     无障碍语义；坐标按可视轨道区间换算；**按下立即提交一次 seek**，拖动只更新预览，
     抬手位置与按下点不同再补最终 seek；`pointerInput` 不再以 `widthPx` 为 key。
- 改了哪些文件：`app/src/main/java/com/msop/player/ui/player/PlayerSheet.kt`。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（1m13s）。
- 遗留问题：`adb connect 127.0.0.1:5555` 被拒绝、`adb devices` 为空，真机验收
  （点 50% → `position≈75000ms`，拖到 20% → ≈30000ms）未执行，P7-01 记 `[?]`。
- 给下一位：设备回来后先 `uiautomator dump` 确认 SeekBar 节点 bounds，再点 50% 并
  `dumpsys media_session` 看 position；若仍无效，优先怀疑 44dp 命中区之外的父布局
  抢事件，而不是 seek 链路（`LibraryViewModel.seekTo` 会同步刷新进度 UI）。

### 2026-11-14 · P7-02 · 播放状态恢复（代码完成，真机待验）

- 做了什么：
  1. 新增 `PlaybackSnapshot` + `PlaybackSnapshotStore`：只存 trackId 列表、当前曲、
     进度、播放模式、是否在播与时间戳，JSON 落到独立的 SharedPreferences。
  2. `PlaybackController` 播放中每 5 秒落一次、暂停/切歌立即落；恢复期间抑制写入，
     避免 `prepare()` 前的 0 位置覆盖快照；1 秒节流防止事件风暴刷盘。
  3. 冷启动 `LibraryViewModel.restoreSession()` 回库重建队列，缺失曲目跳过、当前曲
     被删时索引顺延，`prepare()` 后 `playWhenReady=false`，不自动出声；快照 7 天过期。
- 改了哪些文件：新增 `feature/player/.../PlaybackSnapshot.kt`、
  `PlaybackSnapshotStore.kt`、`feature/player/src/test/.../PlaybackSnapshotTest.kt`；
  修改 `PlaybackController.kt`、`feature/player/build.gradle.kts`、
  `ui/library/LibraryViewModel.kt`、`ui/MsopApp.kt`。
- 验证方式：`.\gradlew.bat :feature:player:testDebugUnitTest :app:compileDebugKotlin`
  → **BUILD SUCCESSFUL**（42s），`PlaybackSnapshotTest` **8 用例 0 失败**。
- 遗留问题：真机「播放 30s → 强停 → 重启」验收未做，P7-02 记 `[?]`。
- 给下一位：如果恢复后进度条显示 0，先看 `RestoredSession.positionMs` 是否被
  `publish()` 的 `currentPosition` 覆盖；恢复期靠 `suppressSnapshotSave` 保护落盘，
  不要删。另注意 `PlaybackController` 现在会注入 `TrackDao`，feature:player 已加
  `core:database` 依赖，别在 core:database 反向依赖 feature。

### 2026-11-14 · P7-03 · 断网保护 + 缓冲提示（代码完成，真机待验）

- 做了什么：
  1. 新增 `NetworkMonitor`（`registerDefaultNetworkCallback` → `StateFlow<Boolean>`），
     网络切换时用 `activeNetwork` 复核，避免 Wi-Fi → 蜂窝误暂停。
  2. `PlaybackState` 增加 `currentSource` / `isBuffering` / `networkLost`；远程曲目在
     断网且 `playWhenReady` 时自动暂停，恢复网络只清提示不续播；起播/切歌也会复核一次。
  3. 播放页新增 `PlaybackNotice`（缓冲转圈 1.2s 延迟出现、15s 后提示网络较慢；
     断网黄色提示优先）；迷你条副标题断网时替换为「网络已断开，播放已暂停」。
- 改了哪些文件：新增 `feature/player/.../NetworkMonitor.kt`；修改
  `PlaybackController.kt`、`ui/player/PlayerSheet.kt`、`ui/components/MiniPlayer.kt`、
  `ui/MsopApp.kt`。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（12s）。
- 遗留问题：飞行模式与弱网真机验收未执行，P7-03 记 `[?]`。
- 给下一位：断网判断在 `PlaybackController.pauseRemoteIfOffline()`，**不要只看
  `isPlaying`**——缓冲中 `isPlaying` 可能为 false 但 `playWhenReady` 为 true，漏掉会
  让播放器在后台空转重试。缓冲提示故意延迟 1.2s，不要改成立即显示，否则每次 seek
  都会闪一下。

### 2026-11-14 · P7-04 · 备份与恢复（代码完成，真机待验）

- 做了什么：
  1. 备份格式定为 zip + 三个 JSON：`manifest.json`（`msop-backup-v1`）、
     `playlists.json`、`settings.json`；导出歌单、仅 Wi-Fi 缓存、均衡器曲线、
     OpenList 地址/用户名/应用密钥，**不导出密码**。
  2. 恢复先完整解析校验再写库；歌单按名称合并、同名追加去重，曲库不存在的曲目跳过；
     服务器配置写回后由 `ServerConfigStore.save()` 清旧 token；单条目上限 4 MB。
  3. 设置页新增「备份与恢复」分组：`CreateDocument("application/zip")` 导出、
     `OpenDocument` 导入，结果用弹窗展示。
- 改了哪些文件：新增 `app/.../backup/{BackupModels,BackupManager}.kt`；修改
  `core/database/.../PlaylistDao.kt`、`EqualizerController.kt`、
  `ui/settings/{SettingsScreen,SettingsViewModel}.kt`、`app/build.gradle.kts`。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（39s）。
- 遗留问题：SAF 导出/导入的真机验收（含坏 zip）未执行，P7-04 记 `[?]`。
- 给下一位：恢复歌单前必须先查 `trackDao.allIds()`，否则 `playlist_tracks` 的外键会让
  整个恢复失败；这是「先算清楚再写库」的原因，不要改成边解析边写。服务器密码永远
  不写入备份，恢复时保留本机旧密码是刻意行为。

### 2026-11-14 · P7-05 · TTML 歌词（代码完成，真机待验）

- 做了什么：
  1. 新增 `TtmlParser`：DOM 解析 TTML，支持命名空间、`p` 的 begin/end/dur、嵌套
     `span`、`br`、帧/tick/offset 时间与 `ttp:frameRate` 等参数；无 end 用下一段
     begin；无任何时间轴时返回静态歌词而不是空。
  2. `LyricLine` 增加 `endMs` 与 `wordJoiner`；LRC 继续用空格拼逐字，TTML 用空串，
     播放页逐字高亮改读 `line.wordJoiner`，中文不再被插空格。
  3. 旁挂歌词查找改为 `.lrc` → `.ttml` → `.xml`（`.xml` 仅在内容像 TTML 时采纳）；
     内嵌歌词内容像 TTML 时也走 TTML 解析。
- 改了哪些文件：新增 `feature/lyrics/.../TtmlParser.kt` 与 `TtmlParserTest.kt`；
  修改 `Lyrics.kt`、`LyricsRepository.kt`、`ui/player/PlayerSheet.kt`。
- 验证方式：`.\gradlew.bat :feature:lyrics:testDebugUnitTest :app:compileDebugKotlin`
  → **BUILD SUCCESSFUL**；`TtmlParserTest` **13 用例 0 失败**，连同 LRC 18、内嵌 13
  共 44 用例全绿。
- 遗留问题：真机 `.ttml` 逐字高亮与点击 seek 未验，P7-05 记 `[?]`。
- 给下一位：TTML 的 clock-time 按绝对时间解释、offset-time 相对父 begin，这是为了
  同时兼容 Apple Music 导出与手写文件；不要改成全部相对或全部绝对。`.xml` 旁挂文件
  可能是别的元数据，`looksLikeTtml` 的守卫不能删。单测里 DOM 用的是 JDK 实现，
  Android 上同一套 `javax.xml`，但坏 XML 必须继续返回空而不是抛异常。

### 2026-11-14 · P7-06 · 音质信息（代码完成，真机待验）

- 做了什么：
  1. `Track` / `TrackEntity` 增加 `bitrateKbps` / `sampleRateHz` / `bitDepth`；
     Room 升 v3，`MIGRATION_2_3` 用三条 `ALTER TABLE` 加可空列，旧库升级不用重扫；
     `3.json` 已生成。
  2. 新增 `AudioQualityReader`：`MediaExtractor` 读码率/采样率，API 31+ 用
     `MediaMetadataRetriever` 读位深；`LocalMetadataEnricher.enrichQuality` 只补
     `sampleRateHz` 为空的曲目，扫描后独立跑，失败不影响扫描。MediaStore 通道先按
     「文件大小 × 8 / 时长」估算平均码率。
  3. Subsonic 的 `bitRate` / `samplingRate` / `bitDepth` 映射进远程曲目，`0` 当缺失；
     `MetadataMerge` / `ScanMerge` 都按「空缺才补」保留音质；文件信息弹窗新增「音质」行。
- 改了哪些文件：`core/model/Track.kt`、`core/database/*`（entity/mapper/dao/database/di）、
  `feature/local`（TrackMetadata、MetadataReader、AudioQualityReader、LocalMetadataEnricher、
  MetadataMerge、RoomMetadataStore、MediaStoreTrackSource、LocalScanWorker）、
  `core/network/SubsonicDtos.kt`、`feature/remote/SubsonicMetadata.kt`、
  `ui/components/TrackRow.kt`；新增 `core/model/.../TrackQualityTest.kt`。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**；
  `.\gradlew.bat test` → **BUILD SUCCESSFUL**，全量 **23 个测试类 / 201 用例 / 0 失败**。
- 遗留问题：真机音质显示与 v2→v3 升级未验，P7-06 记 `[?]`。
- 给下一位：`MediaFormat` 在 compileSdk 37 里**没有** `KEY_BITS_PER_SAMPLE`（试过，
  编译直接报 Unresolved reference），位深只能用 API 31+ 的
  `METADATA_KEY_BITS_PER_SAMPLE`；API 26–30 位深为空是预期行为。补齐判据用
  `sampleRateHz IS NULL`，别改成看码率——有损格式天然没有位深，看码率会反复命中。

### 2026-11-14 · P7-07 · WebDAV 第三音源（代码完成，真实服务器待验）

- 做了什么：
  1. 新增 `SourceType.WEBDAV` 与独立配置 `WebDavConfigStore`（`msop_webdav`），
     与 OpenList 的配置、同步、清理完全隔离；id `webdav:{sha1(path)}`，
     uri 只存稳定的 `webdav:/path`，不存真实 URL。
  2. `core:network` 增加 `WebDavUrl`（路径/URL/href 换算，支持反代子路径）、
     `WebDavMultiStatusParser`（按 localName 解析 Multi-Status，禁 DOCTYPE）、
     `WebDavClient`（PROPFIND Depth:1 + Basic Auth）、`CompositeStreamUrlResolver`
     （按 scheme 分发 OpenList / WebDAV）。
  3. `feature:remote` 增加 `WebDavTrackMapper` 与 `WebDavLibrarySyncer`（BFS，
     只清 WEBDAV 来源；一个文件没扫到或有目录失败时不清理）。
  4. 播放不写自定义 Range：`PlayerFactory` 拼出真实 URL，把 Basic Auth 放进
     `DataSpec.setHttpRequestHeaders`，Range 交给 `DefaultHttpDataSource`；
     缓存 key 仍是 `webdav:/path`，Cache 在 Resolving 外层。
  5. UI：设置页「WebDAV 服务器」入口 + `WebDavConfigScreen`（地址/账号/密码/根路径、
     http 二次确认、测试连接、保存并同步、清除只清 WEBDAV）；曲库云端筛选/计数
     纳入 WEBDAV；歌曲行与播放页来源标签区分。
  6. 备份：`SettingsBackup.webDav`（不含密码），恢复时保留本机已有密码。
- 改了哪些文件：`core/model/Track.kt`、`core/network/{WebDavUrl,WebDavMultiStatusParser,
  WebDavClient,CompositeStreamUrlResolver,StreamUrlResolver,di/NetworkModule}.kt`、
  `core/datastore/{WebDavConfig,WebDavConfigStore}.kt`、
  `feature/remote/{RemoteIdFactory,WebDavTrackMapper,WebDavLibrarySyncer}.kt`、
  `feature/player/{PlayerFactory,cache/MediaCachePolicy}.kt`、
  `ui/settings/{SettingsScreen,WebDavConfigScreen,WebDavViewModel}.kt`、
  `ui/library/LibraryScreen.kt`、`ui/components/TrackRow.kt`、`ui/player/PlayerSheet.kt`、
  `backup/{BackupModels,BackupManager}.kt`；新增 3 个测试类。
- 验证方式：`.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL**；
  `.\gradlew.bat test` → **BUILD SUCCESSFUL**，全量 **26 个测试类 / 218 用例 / 0 失败**
  （新增 `WebDavUrlTest` 7、`WebDavMultiStatusParserTest` 5、`WebDavTrackMapperTest` 5）。
- 遗留问题：真实 WebDAV 的列目录、同步、播放、seek、缓存、断网保护与配置清除未验，
  P7-07 / P3-05 记 `[?]`。
- 给下一位：`dav4jvm` 2.2.1 依赖 `okhttp3.internal.http.StatusLine`（OkHttp 5 已删），
  5.0.0 换 Ktor，所以按硬约束走最小 PROPFIND 客户端，偏差已写 `AGENTS.md` D4。
  PROPFIND 的 `href` 可能带反代子路径，`WebDavUrl.pathFromHref` 必须把 base 的
  pathSegments 一起比较；缓存 key 绝不能用真实 URL。播放地址与请求头都从
  `StreamUrlResolver` 走，别在播放层直接认识 WebDAV。

### 2026-11-14 · P7-08 桌面悬浮歌词完成 + 四项真机复验

- 背景：接手时 `app/desktop/`、`ui/settings/DesktopLyrics*.kt` 已有未跟踪代码，
  `AndroidManifest.xml` / `SettingsScreen.kt` 也已接入，但 `08-shuyin-integration.md` 看板还是 `[ ]`、
  `TASKS.md` / `AGENTS.md` 未同步。本轮核实代码完整后做编译、单测与真机验收，并补齐记录。
- 做了什么：
  1. P7-08 实现定稿：前台服务（`specialUse` + 独立通知渠道 + `START_NOT_STICKY`）、
     `TYPE_APPLICATION_OVERLAY` 双 TextView 窗口、`DesktopLyricsText` 纯函数（6 个单测）、
     拖动吸附 / 锁定 `NOT_TOUCHABLE` / 字号 12–32sp / 7 色 / 背景 0–80% / 单行、
     `SYSTEM_ALERT_WINDOW` 授权页与 `ON_RESUME` 复核、暂停 10 分钟隐藏。
  2. 真机（PGEM10 / Android 15 / 900×1600）复验 P7-01、P7-02、P7-05、P7-06、P7-08。
- 改了哪些文件：**本轮未改业务代码**；更新 `AGENTS.md`（§0/§2/§8）、`TASKS.md`
  （P7 五行转 `[x]` + 总览 + 变更记录）、`08-shuyin-integration.md`（看板 + §5 两条完成记录）、
  本文件。设备上新增测试素材 `/sdcard/Music/PrimuseTest/primuse-flac-test.ttml`。
- 验证方式：
  - `.\gradlew.bat :app:compileDebugKotlin test :app:assembleDebug --no-daemon`
    → **BUILD SUCCESSFUL**；全量 **27 类 / 224 用例 / 0 失败**（去重后；原始 29 类 236 例）。
  - 本地 `app-debug.apk` 与设备 `/data/app/.../base.apk` **md5 均为 `6e0156fbe05a0244a185f315ecb28e9b`**，
    确认真机跑的就是当前代码。
  - P7-01：进度条点 50% → `60003ms`（120s 曲），拖 20% → `23634ms`。
  - P7-02：快照 `43340ms/index=501` → 强停重启 `PAUSED(2)/43340`、迷你条显示该曲不出声，
    点播放从 43340 继续。
  - P7-05：`.ttml` 四行、4.8s 逐字高亮前两段、中文不插空格、点第三行 → `45000ms`。
  - P7-06：`PRAGMA user_version=3`，码率/采样率 509 首、位深 505 首，弹窗
    `16bit / 44.1kHz / 129kbps`。
  - P7-08：服务 `isForeground=true`、窗口 `ty=APPLICATION_OVERLAY`；歌词随播放逐行变化；
    字号 17→30sp / 白色→金黄 / 背景 30→80% 实时可见；拖动 `x:0→660` 吸附右边；
    锁定后 flags 含 `NOT_TOUCHABLE`；关闭后服务与窗口都消失；`force-stop` 后不自动重启，
    重启 App `enabled=true` 保持，打开设置页才按需恢复。
- 遗留问题：P7-03 / P7-04 / P7-07 / P3-04 仍需远程音源、真实 WebDAV 服务器或 SAF 全流程；
  当前设备 509 首全为 LOCAL，OpenList 与 WebDAV 均未配置，保持 `[?]`。
  P7-08 的「暂停 10 分钟自动隐藏」未计时实测（代码为 `delay(10min)` 后置 `GONE`）。
- 验收截图归档在 `../preview/verification/`：`p7-01-seek-bar.png`、`p7-05-ttml-word-highlight.png`、
  `p7-06-audio-quality.png`、`p7-08-desktop-lyrics.png`（均取自 PGEM10 / Android 15 实机）。
- 给下一位：真机 lyric 列表会跟随当前行滚动，**点歌词行验证 seek 前必须重新 dump 坐标**，
  否则会点到别的行（本轮第一次点第三行得到 70s 就是这个原因，不是解析 bug）。
  桌面歌词设置里的坐标会随页面滚动变化，脚本化验收要每次 `uiautomator dump`。
  下一步按 `08-shuyin-integration.md` 做 P7-09 Haze；`TASKS.md` 把 P7-09 标成依赖 P7-11、
  `08-shuyin-integration.md` 却把 P7-09 排在最前，动手前先统一这个顺序矛盾。


### 2026-11-14 · P7-09 Haze 标题栏毛玻璃（完成 + 真机验收）

- 背景：接手时 Haze 2.1.0 依赖与 `HazeTitle.kt`（稳定 Box 源捕获 + Backdrop 修正版）已存在，
  曲库/搜索/设置三页已接入，但 `08-shuyin-integration.md` 看板仍是 `[ ]`、遗留 `HazeLogger.enabled = true`
  调试开关，`gfxinfo` 采样 0 帧（性能测量中断）。
- 做了什么：
  1. `ScreenTitle` 加 `leading` 槽、`HazeTitleScaffold` 加 `onBack` 参数——二级页标题栏获得
     统一返回键；`AlbumDetailScreen`、`PlaylistList`、`PlaylistDetail`、`AddTracksScreen`
     重构为「全屏滚动 + 固定毛玻璃标题栏」，封面信息卡/播放全部随列表滚动并经过标题栏后方
     被模糊；删除旧 `PlaylistHeader`。
  2. 移除 `HazeLogger.enabled` 调试开关。
  3. 性能调参：60Hz 云真机 A/B——无模糊 p50=26ms/jank 7.3%；`20dp+noise` 快滑 24ms；
     `12dp` 去 noise 后中速滑 27ms/jank 8.3%、快滑 32ms/jank 17%；同机系统设置应用 21ms
     （≈47fps 设备上限）。模糊边际开销约 5–6ms，已在 `HazeTitle.kt` 注释记录调参依据。
- 改了哪些文件：`ui/components/HazeTitle.kt`、`ui/components/TrackRow.kt`、
  `ui/library/AlbumDetail.kt`、`ui/playlist/PlaylistScreen.kt`。
- 验证方式：`compileDebugKotlin` + `test` + `assembleDebug` 全绿（27 类 224 用例 0 失败）；
  真机 PGEM10：设置页/专辑详情滚动中标题栏模糊轮廓可见、文字清晰无渲染异常；歌单全流程
  （新建→详情→选歌→添加 2 首→返回→删除）走通，标题栏返回/添加/更多按钮可点；测试歌单
  已删净。截图归档 `../preview/verification/p7-09-*.png`。
- 遗留问题：「滚动 ≥50fps」验收线受云真机上限约束（系统应用自身 ≈47fps），已按边际开销
  记录；API 30 纯色降级仅代码路径验证（本机无 API 30 设备/模拟器）；曲库页滚动模糊证据
  取自上一会话 524 首时期截图，当前设备仅 7 首无法滚动复测。
- 给下一位：踩过的两个坑——① 编辑器对 import 块做模糊匹配，删 import 时会把中间的相邻行
  一起吞掉（本轮 Row/Spacer 被吞导致 12 个编译错），改 import 后必须重新编译核对；
  ② `hazeSource` 不能挂在 AnimatedContent 上（捕获层不刷新），必须挂在稳定 Box 上。
  下一步 P7-10 液态玻璃导航（自绘，不引新依赖，见 `08-shuyin-integration.md` §2）。

> AI生成

### 2026-11-14 · P7-10 液态玻璃导航（完成 + debug/release 双包验收）

- 做了什么：
  1. `MsopApp` 去 Scaffold bottomBar → 全屏内容（statusBarsPadding + 共享 hazeSource）+
     底部悬浮液态玻璃浮层；列表铺满全屏从胶囊后方经过被磨砂；新增 `LocalSharedHazeState`
     与 `LocalHazeBottomInset` 两个 CompositionLocal，各屏列表 bottom padding 接入（9 处）。
  2. `Modifier.liquidGlass`（Surfaces.kt）：API 31+ hazeBlur 磨砂 + 折射高光 + 双层渐变 +
     1dp 内描边；低版本降级高不透明渐变。`LiquidTab`：按压跟随触点径向光斑 + 果冻挤压
     （spring 0.55 回弹过冲），热区 48dp。`MiniPlayer` 升液态玻璃底 + 整卡挤压/光斑。
- 改了哪些文件：`ui/MsopApp.kt`（结构 + BottomNav/LiquidTab）、`ui/components/Surfaces.kt`
  （liquidGlass）、`ui/components/MiniPlayer.kt`、`ui/components/HazeTitle.kt`
  （共享源/inset CompositionLocal）、`ui/library/LibraryScreen.kt`、`ui/search/SearchScreen.kt`、
  `ui/settings/SettingsScreen.kt`、`ui/library/AlbumDetail.kt`、`ui/playlist/PlaylistScreen.kt`
  （bottom inset 接入）。
- 验证方式：`compileDebugKotlin` + `test` + `assembleDebug` + `assembleRelease` 全绿
  （27 类 224 用例 0 失败；release 3.3MB）；真机 PGEM10：
  - `motionevent DOWN` 按住 Tab 抓帧：光斑以触点为中心、Tab 挤压变窄，其他 Tab 不受影响；
  - 设置页滚动中：标题 + 底部胶囊/迷你条后方内容均磨砂；列表末项不被胶囊遮挡；
  - Tab 切换 / 迷你条展开播放页 / 搜索过滤（love→1 首）正常；
  - release（R8）真机实测磨砂存活（Haze 无 ServiceLoader 风险，与当年 Coil 事故不同）；
    设备验证后已恢复 debug 包。
- 性能口径（60Hz 云真机中速滑）：仅标题磨砂 27ms → 双处 12dp 36ms/jank25% → 底部 8dp
  32–34ms/jank16–20%；设备上限 ≈47fps（系统应用 21ms）。P7-10 验收无 fps 线，如实记录。
- 遗留问题：API<31 降级仅代码路径验证；曲库 Tab 行出现过一次瞬时胶囊错位截图
  （冷启动/3 次往返切换均无法复现，判为云真机合成器伪影）；搜索页键盘弹出时机观察到
  一次迟滞（不阻塞）。
- 给下一位：P7-11 iOS 式转场是最后一项视觉任务，spring(0.85, 380)，二级页已全是
  overlay 分支形态。改 import 后立即编译核对（编辑器模糊匹配吞行的坑已记两条）。

> AI生成

### 2026-11-14 · P7-11 iOS 式二级页转场（完成 + 真机验收，视觉三连收官）

- 做了什么：
  1. 基础设施（`Surfaces.kt`）：`iosStackSpec(opening)` 转场规格 + `EnteringPage`（24dp 圆角
     临界阻尼收拢 + 内置 AmbientBackground 不透明底）。采用「常驻主内容 + overlay」模式，
     主内容的滚动位置与内部状态在打开/关闭二级页时完全保留，与改造前 `if/return` 一致。
  2. 曲库页两分支（歌单/专辑详情）、歌单页两层（列表↔详情用稳定 selectedId、详情↔选歌）、
     设置页四子页统一 overlay。主内容压缩渐暗挂在 `HazeTitleScaffold(modifier=graphicsLayer)`
     上，主代码块缩进不动。
- 真机暴露并修复两个 bug：
  1. 圆角负值崩溃：半径动画 spring(0.85) 欠阻尼过冲到 -0.19dp，RoundedCornerShape 抛
     IllegalArgumentException（点专辑即复现）→ 改临界阻尼 + coerceAtLeast(0.dp)。
     教训：不允许负值的量（尺寸/半径）绝不能用欠阻尼弹簧。
  2. 透明页鬼影：二级页原为「整页替换」背景可透明；overlay 化后叠在压缩至 0.55 的主内容上
     稳定态透出鬼影 → EnteringPage 内置不透明底。
- 验证方式：compileDebugKotlin / test / assembleDebug / assembleRelease 全绿（27 类 224 用例
  0 失败）；真机：专辑详情转场中帧两次可见缩放+滑入、稳定帧干净；歌单列表/详情/选歌页、
  OpenList 子页稳定帧干净；返回键单步验证四条链路（专辑→网格、详情→列表、列表→曲库、
  子页→设置主页）；连点 ×4 存活。截图归档 `../preview/verification/p7-11-*.png`。
- 给下一位：P7 视觉三连（09/10/11）全部收官。剩余 P7-03/04/07 + P3-04 均需远程资源或
  真实 WebDAV 服务器；P7-12 需 NDK。可选 P5-04 / P6-01。

> AI生成

### 2026-11-14 · 修复「曲库扫描没用」：全量扫描前请求系统重扫标准目录

- 现象：用户往 /sdcard/Music 推了 11 首新歌（13:57–13:59 落盘），点「重新扫描曲库」
  曲库仍是 7 首。日志显示扫描链路本身完全正常（`扫描=7 写入=7`）——根因是
  adb push 落盘的文件**不会进 MediaStore**（系统索引器不会被 adb 触发，`AGENTS.md` §8
  记录过），而本地扫描主通道只能查到已索引文件。
- 立即解锁（环境侧）：`content call --method scan_volume` 后 App 重扫 → 18 首全部入库。
- 持久修复（App 侧）：新增 `SystemMediaIndexer`（feature/local/scan）——
  `MediaScannerConnection.scanFile` 传 **Music/Download 目录路径**请系统重扫，
  15s CountDownLatch 超时兜底（超时照常继续本地同步，与修复前行为一致）；
  `LocalScanWorker` 仅 `full=true` 时触发（增量扫描每次启动都跑，不能惊动系统索引器）。
- 真机实证（PGEM10 / Android 15，全部不走 adb 卷扫描）：
  - 复制探针文件 → MediaStore 仍 18 → 点重扫 → 系统回调返回有效 URI
    （`path=/storage/emulated/0/Music uri=content://...file/1000000011`）→
    `扫描=19 写入=19 库内总数=19`（未索引新文件一次点击即入库）；
  - 删探针 → 再重扫 → `清理幽灵条目 1 条`、回落 18（删除方向同样自愈）；
  - 启动增量扫描日志无「系统媒体目录重扫」行、耗时不变（96ms）——未影响增量路径。
- 结论：MediaScannerConnection 在 Android 15 存活且接受目录路径（ModernMediaScanner
  按需求扫描目录）；这条通道无需新权限、不违反任何硬约束（未枚举任意路径、
  未用 MANAGE_EXTERNAL_STORAGE）。
- 改了哪些文件：新增 `feature/local/.../scan/SystemMediaIndexer.kt`；修改
  `LocalScanWorker.kt`（full 分支前置调用）；`USAGE.md` §6 扫描说明；
  `AGENTS.md` §8 卷扫描命令注记；`TASKS.md` 变更记录。
- 验证方式：`gradlew test` 全绿（27 类 224 用例 0 失败）；上述真机双向实证。
- 给下一位：把新歌推到设备后**点 App 内「重新扫描曲库」即可**，不用再跑
  scan_volume。如果遇到「重扫后仍缺歌」的反馈，先看 logcat 里
  `系统媒体目录重扫回调` 有没有 uri=null（目录不存在或系统拒绝），再查 MediaStore。

> AI生成

### 2026-11-14 · 用户截图需求批次 UI 改造收官（含接手断点恢复）

- 背景：用户 2026-11-14 上午附截图提出一批 UI 改造需求（详见本条清单）；上一会话
  实施完成后在「收尾验收 + 文档同步」阶段挂死（17:36 编译落盘后无任何活动，
  状态停在 running）。本轮接手：核对代码完整性 → 全量构建 → 真机逐项验收 →
  补写全部文档记录。
- 改造清单与真机验收结果（PGEM10 / Android 15 / 900×1600，全部通过）：
  1. **主题两态化**：`ThemeMode { DARK, LIGHT }`，默认深色、无跟随系统；
     `ThemePreferences` 持久化 + `MainActivity` 接线即时生效；设置页「外观」分段。
     验收：默认深色 → 切浅色即时生效（玻璃质感保留、可读性正常）→ 强停重启
     仍浅色 → 恢复深色。release 新装（无偏好）回落默认深色。
  2. **迷你播放条按用户参考图定稿**：液态玻璃底 + 封面 + 歌名/艺术家两行 +
     播放暂停（实心强调圆钮）+ 下一曲（半透明圆钮）；整卡按压果冻挤压 + 触点光斑。
     （历史口径：07:31 曾按 ShuYin 做过频谱+三按钮版，用户后续给参考图二次定稿
     为当前两键版，以参考图为准。）
  3. **播放页顶栏只留喜欢**（爱心右上）；**队列移到底部 dock**（歌词 | 队列 | 定时
     三入口）；删「点封面看歌词」提示文案。
  4. **SleepDialog 重构**：预设 10/20/30/45/60/90 分钟 + 自定义分钟步进器
     （默认 30，1–300 范围）+ 开始/取消。
  5. **歌词页 ShuYin 化**：当前行大号加粗白 + 柔和光晕、非当前行小号中性色
     距离渐淡、模糊封面背景。LRC 为行级高亮；TTML 逐字高亮为 P7-05 已验能力。
  6. **搜索页**：删统计块与随机入口卡，副标题改「共 N 首」（实测「共 18 首」）。
  7. **曲库页**：删顶部随机按钮、删艺术家 Tab，新增横向歌手头像轮播
     （点卡过滤该歌手 + 紫色描边选中态，再点取消；过滤态不持久化，重启回全部歌曲）。
  8. **无封面统一程序化封面**：`GeneratedArtwork` 按 seed 哈希固定渐变（列表/迷你条可见）。
  9. **liquidGlass 迁移 Kyant0 Backdrop 2.0.1**（Apache-2.0，API 33+ 折射透镜 +
     色差、31+ 磨砂 + 饱和度、低版本不透明渐变底）；GPL 代码仍零引入。
- 验证方式：全量 `test` + `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL
  （含变体原始 29 个测试 XML / 236 用例 / 0 失败；按类名去重 27 类 224 例，与既有
  口径一致）；debug 18.6 MB / release 3.3 MB；真机 debug 包逐项验收如上；
  **release（R8）包装机复验**：磨砂存活、封面/轮播正常、默认深色回落正确——
  Kyant0 Backdrop 无 ServiceLoader 风险，与 Haze 同口径；验收后设备已恢复 debug 包。
  截图归档 `.temp/uiv-01…13`（会话临时件，未入 `../preview/verification/`）。
- 遗留与口径：
  - 横屏播放页未在本轮专项复验（P4-04 历史遗留口径不变）；
  - 歌词页验收的是 LRC 行级样式，逐字高亮依赖 TTML 源（P7-05 已验）；
  - 主题范围变更：`08-shuyin-integration.md` §1 原「明确不做深浅色」被用户 2026-11-14 明确要求推翻
    （两档、默认深色、不跟随系统），边界修订已同步记入 `08-shuyin-integration.md` §0/§1 与
    `AGENTS.md` D23/D24。
- 给下一位：P7 剩余项与外部资源依赖不变（P7-03/04/07、P3-04、P7-12 NDK 阻塞；
  可选 P5-04 / P6-01）。改 UI 前先读 `07-ui-design.md` v2.0 与 `08-shuyin-integration.md` §5 的两轮视觉记录。

### 2026-11-14 · HOTFIX · 修复启动闪退（标题栏 Backdrop 自引用）与磨砂回归
- 做了什么：21:50 装上的 debug 包启动即崩。tombstone 显示 `RenderThread` SIGSEGV、
  `Cause: stack pointer is close to top of stack; likely stack overflow`、512 帧全是
  `RenderNode::prepareTreeImpl` ↔ `SkiaDisplayList::prepareListAndChildren` 交替递归，
  21:51:03 / :28 / :54 三次启动均崩，不是 Java 异常。根因：`HazeTitleScaffold` 标题栏
  位于 `MsopApp` 全屏 Box 的 `layerBackdrop(layerBackdrop)` 所捕获的内容子树内，却用
  `drawBackdrop(同一个 layerBackdrop)` 消费它，形成「被记录的内容里又绘制该记录层」
  的 RenderNode 自引用。修复：标题栏不再消费共享 LayerBackdrop，改回 Haze
  （`HazeInput.Backdrop` + 12dp + 垫色/tint），且**自建** HazeState、`hazeSource` 挂在
  本组件内的稳定内容 Box 上——共用外壳 `LocalSharedHazeState` 会让滚动后捕获层不刷新、
  列表文字清晰透出（P7-09 真机结论，本轮复现并修掉）。中间试过「标题栏另建独立
  LayerBackdrop、源挂内容 Box」：不崩但与外层共享 backdrop 嵌套后采样为空、文字透出，
  已弃用，原因写进代码注释防止复发。
- 改了哪些文件：`app/src/main/java/com/msop/player/ui/components/HazeTitle.kt`（唯一代码改动）；
  `AGENTS.md` / `TASKS.md` / `05-progress.md`（记录）；新增验证截图
  `../preview/verification/2026-11-14-crash-fix.png`（静止）与 `...-scrolled.png`（滚动后磨砂）。
- 验证方式：`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest` 与全量
  `./gradlew.bat test` 均 BUILD SUCCESSFUL（按类名去重 27 类 / 224 用例 / 0 失败）；
  设备 `127.0.0.1:5555` 装新 debug 包后连续 3 次 `am force-stop` + 冷启动均拿到存活 PID
  （9117 / 9235 / 9348），3 个 Tab 点击 + 列表滑动后 `logcat -b crash` 无任何
  `com.msop.player` / FATAL / SIGSEGV 记录；滚动后截图确认标题栏后方内容呈模糊轮廓、
  不再透出清晰文字。
- 遗留问题：标题栏现在走 Haze 而非 Kyant0 Backdrop（与底部胶囊质感略有差异），这是避免
  自引用递归的必要取舍；若要统一质感需先把标题栏移出 `layerBackdrop()` 捕获子树。
  本轮未重打 release 包。
- 给下一位的提醒：不要在 `HazeTitleScaffold` 里用 `LocalLayerBackdrop.current` 做
  `drawBackdrop`；不要把标题栏的 Haze 源改回外壳共享 `LocalSharedHazeState`。

### 2026-11-15 · 播放页手势收敛：删「上滑开队列」+ 定位并修掉顶部异色块
- 做了什么：
  1. **删掉「上滑打开队列」**（用户指令）：`PlayerSheet` 的竖直手势不再累计上滑分量，
     上滑位移钳在 0，不位移也不触发任何行为；队列只从底部 dock 的「队列」按钮进入
     （`onOpenQueue` 仍被 FooterDock 与横屏 pane 调用，签名不变）。
  2. **顶部异色块的真正成因（真机实测确认，推翻了本轮上午的两版猜测）**：
     `PlayerBackdrop` 里那张模糊封面带 `graphicsLayer{ scaleX/scaleY = 1.18f }`，
     放大会让它的绘制溢出本节点上下各约 9% 屏高（1600px 屏约 144px），而遮罩
     （`scrimTop 0.4 → scrimMid 0.8`）只铺面板自身范围。于是**面板顶边之上露出约
     100px 未经遮罩压暗的亮色模糊带**，与面板顶形成一条硬边——正是用户 07:19 截图
     里的顶部色块。它与「背景是否跟手」无关，只是被位移放大后才显眼。
     修法：播放页根 Box 加 `clipToBounds()`，把 1.18 倍的溢出裁掉。
     上午先后试过「Void 0.94 实底盖住」与「背景固定、只内容下滑」两版，都没打到根因
     （前者只是把亮带涂暗，后者只是让亮带留在顶部不显眼），用户明确否掉了后者。
  3. **下滑手势最终形态**（用户选定）：**整页一起下滑，后面正常露出曲库**。
     位移改用 placement 阶段的 `offset { IntOffset(0, offsetY) }`（比 `graphicsLayer`
     更适合整层搬移，也不影响 blur 层），背景 / 遮罩 / 内容在同一层里整体下移。
  4. 删除 `MsopApp` 里 11-15 早间临时加的 `Void @ 0.94` 实底（含失效的
     `LocalMsopPalette` import）：曲库要正常显示，这层既是错的方向，又会在收起动画闪深色。
- 改了哪些文件：`app/src/main/java/com/msop/player/ui/player/PlayerSheet.kt`（手势 +
  `offset` + `clipToBounds`）、`app/src/main/java/com/msop/player/ui/MsopApp.kt`（删实底）；
  `AGENTS.md` / `TASKS.md` / 本文件（记录）；新增验证截图
  `../preview/verification/2026-11-15-player-drag-dark.png`、`...-drag-light.png`、
  `...-swipeup-noop.png`。
- 验证方式：`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL，装真机
  （PGEM10 / Android 15 / 900×1600，`adb -s 127.0.0.1:5555`）逐条实测：
  ① 下滑 1/3 屏停住 → 顶部完整露出曲库（音乐库标题 / 18 首统计 / 五个 Tab 全可见），
     像素采样确认面板顶边（y≈228）颜色 (41,45,62) 与静止页首行 (41,45,62) 完全一致，
     面板顶边之上是曲库底色 (25,25,59)→(21,22,50) 的平滑渐变，**无亮色横带、无硬边**；
  ② 深色 / 浅色两套主题分别复测，均无横带；
  ③ 松手超过阈值 → 收起回曲库；小幅下滑 → 弹回播放页；
  ④ 封面区上滑 → 页面纹丝不动、不弹队列（截图 `...-swipeup-noop.png`）；
  ⑤ 底部 dock「队列」→ 播放队列正常弹出。
- 遗留问题：收起动画分两段（内容 180ms 移出 → 整层滑出），背景会在中间短暂停留，
  属可接受的取舍。
- 给下一位的提醒：`PlayerBackdrop` 的 1.18 倍缩放是必要的（藏模糊边缘），但**必须**
  在根 Box 上配 `clipToBounds()`；删掉裁剪就会重新露出顶部亮带。不要再往顶部盖实底。

### 2026-11-15 · 播放页控制条重排 + 模式扇形菜单 + 队列拖动跟手 + 定时对话框
- 做了什么（用户六条指令）：
  1. **右下「循环」按钮删除**，**喜欢挪到这个位置**，且**去掉爱心外面的圆圈底**
     （顶栏的 `CircleGlassButton` 喜欢按钮一并移除，顶栏右侧改 42dp 占位保证标题居中）。
  2. **左下「随机」改为「顺序」按钮（默认）**；点一下扇形展开其余模式，选一个即生效
     并把按钮图标换成该档。`PlayMode` 新增 `REPEAT_ALL`（此前只有 顺序/单曲/随机，
     没有「列表循环」这一档），`applyMode`/`playMode()`/`cycleMode()` 同步补齐。
  3. **当前播放模式不再出现在扇形里**：菜单只列 `playModeOptions.filter { it.mode != 当前 }`，
     恒为三项。
  4. **扇形效果照抄 GitHub 开源项目 JellyFab（Apache-2.0）**，见 `PlayModeFan.kt` 顶部
     署名：`evenArcAngles` 均分弧角、`arcOffset` 极坐标→笛卡尔、`lerpOffset` 链式起点、
     「先过冲到 1.06 再用 MediumBouncy 回落」的展开曲线、`alpha=p / scale=0.8+0.2p`。
     只移植这三段纯函数与动效参数，未引入该库其余代码。
     几何：三档分布在 90°→35°、半径 130dp 的弧上（相邻圆心 ≈61dp，52dp 圆之间留 9dp），
     最右一档落在播放键上方不压键。
  5. **顶部「正在播放」改为随状态变化**：未在播放 / 网络已断开 / 缓冲中 / 正在播放 / 已暂停，
     左侧圆点颜色同步（灰 / 金 / 蓝 / 青）。
  6. **队列**：拖动手柄图标换成专用 `PlayerIcons.DragHandle`（2×3 圆点）；
     **拖动时整行跟着手指走**（`graphicsLayer.translationY = accumulatedY` + `zIndex(1f)`），
     拖动中底色改不透明 `Surface2` 并加 12dp 投影，避免和下面那行文字叠在一起；
     单行占位由 56dp 修正为 58dp（含上下各 1dp 内边距），跨行判定才准。
  7. **定时关闭**：预设改 5 / 15 / 30 分钟；自定义改为**手动输入**（数字键盘、
     只收数字最多 3 位、1–600 校验、未填时「开始」置灰），删掉原来的 ±1 步进器。
- 踩坑记录（重要）：
  - **扇形不能用 `Popup`**。`Popup(alignment = TopStart, offset = …)` 的窗口会被系统
    钳制到屏幕内，实测三项全部落到播放键与底部 dock 上，与锚点完全对不上。
    改成「页面内全屏覆盖层 + 模式按钮 `onGloballyPositioned` 上报自身中心」后位置精确。
  - 覆盖层要配 `detectTapGestures` 点空白关闭 + `BackHandler` 优先关菜单。
- 改了哪些文件：`app/.../ui/player/PlayerSheet.kt`、新增 `app/.../ui/player/PlayModeFan.kt`、
  `app/.../ui/player/QueueSheet.kt`、`app/.../ui/icons/PlayerIcons.kt`（新增 Order /
  RepeatOne / DragHandle）、`app/.../ui/library/LibraryViewModel.kt`（`setPlayMode`）、
  `app/.../ui/MsopApp.kt`（`onSelectMode`）、`feature/player/.../PlayMode.kt` 与
  `PlaybackController.kt`（新增 REPEAT_ALL 映射）。
- 验证方式：`:app:assembleDebug` BUILD SUCCESSFUL；真机 PGEM10 / Android 15（900×1600，
  density 2.0）逐条实测：① 底部左右键位与图标正确（顺序在左、爱心在右且无圆圈）；
  ② 点顺序 → 扇形展开 随机/循环/单曲三项，无重叠、不压播放键（截图
  `../preview/verification/2026-11-15-mode-fan.png`）；③ 选「随机」→ 菜单关闭、左下图标变
  随机且变蓝，再点开菜单只剩 顺序/循环/单曲（`...-mode-fan-exclude-current.png`）；
  ④ 播放中顶栏显示「● 正在播放」青点，暂停显示「已暂停」灰点（`...-player-status.png`）；
  ⑤ 队列长按手柄拖两行 → 表头实时显示「移到 4」、整行跟手且不透明浮起
  （`...-queue-drag-follow.png`），松手后 Love Paradise 由 02 落到 04；
  ⑥ 定时对话框 5/15/30 预设 + 输入 45 → 「开始」点亮 → 底部 dock 显示「45分」，
  再进对话框出现「还剩 45 分钟后暂停 / 关闭」（`...-sleep-dialog.png`）。
- 遗留问题：横屏（compact）走的是同一套组件，本轮只在竖屏实测；扇形菜单在横屏的
  锚点与弧向未单独走查。
- 给下一位的提醒：`PlayMode` 现在有 4 档，任何 `when(PlayMode)` 都要补 `REPEAT_ALL`；
  扇形菜单的锚点依赖 root 坐标，若把模式按钮挪进别的容器记得同时改 `boundsInRoot` 的
  参照（当前覆盖层与按钮同处播放页根 Box）。

### 2026-11-15 · 播放页三处修复：进度条切歌卡住 / 扇形菜单过远 / 封面↔歌词翻页动画

- 来源：用户报三个问题（原话）：「拖动进度条播放，切换下一首，进度条位置还是上一首歌曲
  拖动位置」「弹出的播放模式按钮离当前按钮太远」「封面滑动到歌词没有动画，查找 github
  项目照抄」。
- **问题 1 有两层根因，缺一层都修不掉**：
  1. `PlayerSheet` 的 `dragValue`（拖动预览值，只有 -1 才回落到真实位置）在抬手时
     **不一定被清掉**——旧 `SeekBar` 只在「值与按下点不同」时 `onCommit`，而清值就写在
     `onCommit` 里；点一下进度条（按下即抬手）之后 `dragValue` 永远停在那个位置。
     改法：抬手**无条件** `onCommit`；再加 `LaunchedEffect(track?.id) { dragValue = -1f }`
     按曲目 id 强制归位。
  2. 更关键：`LibraryViewModel._positionMs` 原来**只在播放中**由 `MsopApp` 每秒轮询
     （`while (isPlaying) refreshPosition()`）。**暂停状态下切歌就再也不刷新**，
     进度条停在上一首 seek 到的绝对毫秒数上（实测：4:03 的歌拖到 3:04 → 切到 3:16 的歌，
     进度条仍显示 3:04，因为 184s 被当成新歌的位置）。改法：`init` 里收集
     `playback.state`，`currentTrackId` 一变就同步 `_positionMs = currentPositionMs()`，
     覆盖「手动切歌 / 播完自动切歌 / 恢复现场」三条路径；`next()/previous()` 里再先把
     进度置 0，点击瞬间就归位、不闪。
- **问题 2（第一次改错了，同日用户回退）**：扇形菜单原本是「等半径圆弧」——三档同落在
  R=130dp 的 90°→35° 弧上，第一项就离按钮 130dp。第一次修复图省事改成了
  **链式累加**（每项从前一项再走一个 58dp 步长），结果第一项确实贴到 66dp，
  但第三项跑到 178dp，**整个扇形变成一条链**——用户明确反馈「扇形都被改变了 修复」。
  已回退：恢复 `evenArcAngles` + 等半径圆弧 + 链式展开动效 + 52dp 选项尺寸，
  只调**张角与半径**这两个参数。
  几何依据：等半径弧相邻弦长 `2R·sin(Δθ/4)`，旧参数 130dp/55°/52dp 得 61.8dp；
  新参数 **R=86dp / 90°→10°（Δθ=80°）/ 52dp** 得 58.8dp（间隙 6.8dp，仍不重叠），
  但三项到按钮距离统一从 130dp 缩到 86dp。**保持扇形形态的正确解法是扩大张角，
  不是改成链式排布。**
  真机复验（density 2.0）：三项角度 90° / 53° / 16°，半径 94 / 93 / 88dp
  （极差 14px，等半径特征成立；偏差来自 uiautomator 报的是内容节点中心而非 52dp 容器中心）。
- **问题 3**：竖屏封面↔歌词原来是 `detectHorizontalDragGestures` 累计位移，松手后直接
  切 `if (showLyrics)` 分支——**松手瞬间硬切，没有任何过渡**。改成
  `HorizontalPager`（Compose 官方 foundation）：跟手拖动 + 弹簧吸附 + 翻页落定后
  `snapshotFlow { settledPage }` 同步 `showLyrics`；点底部「封面/歌词」按钮走
  `animateScrollToPage` 反向联动。每页加 4% 的 `graphicsLayer` 缩放做视差。
  形态参考：YouTube Music / OuterTune 播放页的左右翻页（OuterTune 的
  `ui/player/Player.kt` 是 GPL-3.0，**只借鉴形态、代码独立实现**；它本身用的是
  `AnimatedContent` 做「点按展开」，不是跟手翻页，所以这里取 pager 的跟手形态）。
  竖直收起手势仍只挂封面页（歌词页由 LazyColumn 自己消费竖直滚动，外层再挂会抢事件）。
- 顺手：播放页左侧当前进度 `0` 时原来显示 `--:--`（`toDurationLabel()` 对 0 的全局约定
  是「时长未知」），切歌归零后看着像故障，改为显示 `0:00`。
- 改了哪些文件：`app/.../ui/player/PlayerSheet.kt`（SeekBar 抬手提交、track 变化归位、
  pager、时间标签）、`app/.../ui/player/PlayModeFan.kt`（链式排布、删 evenArcAngles）、
  `app/.../ui/library/LibraryViewModel.kt`（init 收集 state + next/previous 归零）。
- 验证方式：`:app:assembleDebug` BUILD SUCCESSFUL ×3；真机 PGEM10 / Android 15
  （900×1600，density 2.0）。**这轮没法截图**（Read 读不了 png、无 ViewImage），改用
  `uiautomator dump` 读语义树 + 解析 bounds 做数值验证：
  ① 暂停态拖到 2:28（3:16 的 75%）→ 点下一首 → 左侧变 **0:00**（修复前是 3:04）；
  ② 播放态拖到 0:59 → 等 3 秒变 1:09 → 再等变 1:20，位置正常前进不卡死；
  ③ 模式按钮中心 (90,1360)，恢复扇形后三档落点 (90,1171)/(201,1211)/(259,1313)，
     半径 94/93/88 dp、角度 90°/53°/16°（等半径圆弧）；
  ④ 左滑封面→歌词页（出现「词Lyricist：孙英男」，dock 变「封面」），右滑回封面
     （出现 FLAC 标签，dock 变「歌词」），点 dock「歌词」→ 再翻回歌词页；
  ⑤ 回归：封面页竖向下滑仍正常收起回曲库；歌词页上下滚动**不会**误收起。
- 遗留：横屏（compact）未走查；pager 的视差幅度是拍的 4%，真机上若觉得不够可以调。

### 2026-11-15 · D26：删除 GPL-3.0 复制约束（用户指令）

- 用户原话：「删掉GPL-3.0约束，项目是开源的」。此前项目按 D22/D24/D25 把 GPL-3.0
  （ShuYin、OuterTune、BetterLyrics 等）列为「只借鉴形态、禁止复制代码/资源」。
- 已解除的位置（全部改为「标注来源与许可证即可直接采用」）：
  `AGENTS.md` §0 第 7 条、§3 硬约束第 4 条、决策表 D22（划线标已解除）/ D24 / D25，
  新增 **D26**；`TASKS.md` P7 与总览两处原则注；`08-shuyin-integration.md` §0 边界第 1、3 条。
- 历史进度条目不追溯改写（当时确为独立重写，属事实）；只在当前规则处标注解除。
- **待办**：仓库根目录目前**没有 LICENSE 文件**。GitHub 仓库虽公开，但「公开」不等于
  「有开源许可证」；若后续真的引入 GPL-3.0 代码，需补 GPL-3.0 LICENSE 并保留第三方
  版权/NOTICE，否则会有合规缺口。
- 技术说明：BetterLyrics 是 C# / WinUI3，解除许可约束后源码仍无法搬进 Kotlin / Compose，
  能照抄的是纯歌词页的视觉形态、动效与布局思路。

### 2026-11-15 · 六项需求批次（4 项落地 + 2 份预览待确认）

- 用户 13:00 批次需求，其中「图标重新生成替换前需要询问确认」「歌词界面删掉重做」两项
  明确要先看预览。已在 13:0x 一次性问清四项决策（图标先出对照 / 备份只补两组密码 /
  歌词先出竖横屏预览 / 进度条沿用 #9A8CFF 浅紫）。
- **已落地（4 项）**：
  1. **音乐库删「最近」**：`TABS` 由 5 个改 4 个（歌曲/专辑/歌手/文件夹），删 `RecentList`
     与 `recentTracks` 订阅（ViewModel 里的数据源保留，无订阅即不计算）。真机 Tab 实测 4 个。
  2. **文件夹点击误播 BUG**：原 `FolderList` 是 `.clickable { onPlay(list, 0) }`——点目录名
     就直接播。改成 `onOpen(path)` 打开新的 `FolderDetailScreen`（复用 `HazeTitleScaffold` +
     `TrackRow`，带「播放全部」按钮），只有点具体曲目才播。新增 `LibrarySecondary.Folder`
     与共用切分函数 `folderOf(track)`。真机实测：点 Music 目录 → 出现
     「文件夹 · 18 首 · /storage/emulated/0/Music」与「播放全部」，播放状态未变。
  3. **迷你条进度条首尾被盖住**：`MiniProgress` 原整宽贴 34dp 大圆角卡片底边，两端落在
     圆角里被裁。改成左右内缩 20dp + 整条 `clip(2.dp)`；已播/未播/频谱统一
     `Palette.BlueLight`（深色 #9A8CFF），不再混用 `surfaceVariant`/`primary`/`tertiary`。
  4. **设置 + 备份**：导出/导入文案改为「包含所有数据（含服务器密码），导入后直接可用」；
     「版本」删「自用，不上架」；「只读声明」→「声明」；「不修改、不上传任何音频文件」
     →「不修改、不上传任何数据」。`ServerBackup`/`WebDavBackup` 加 `password: String = ""`
     （旧备份缺字段仍可反序列化），`BackupManager` 导出写入、导入直接恢复，
     结果文案去掉「密码需重新输入」。旧备份 password 为空时回退保留本机已有密码。
- **只出预览、未动代码（2 项，等确认）**：
  - `../preview/icons-redesign.html`：15 个现有图标（不是 16 个，之前口误）旧 vs 新 SVG 对照 +
    1 个新增 `Library` 图标建议，标注使用位置与问题；确认后才替换 `PlayerIcons.kt`。
  - `../preview/lyrics-redesign.html`：BetterLyrics 风格纯歌词页，竖屏 390×694 / 横屏 694×390
    两版；逐字高亮 + 拖尾光晕 + 远处透视淡出 + 取色流体背景；右上横屏按钮；
    迷你控制条 3 秒自动隐藏；横屏控制条 `left 24dp / width 232dp` 与左侧封面左边缘对齐。
    确认后才写 Compose（旧的 `LyricsPane` 是否整块删、`HorizontalPager` 是否保留一并待定）。
- 验证：`:app:compileDebugKotlin` / `:app:assembleDebug` / `gradlew test` 全绿；
  真机（PGEM10 / Android 15）用 `uiautomator dump` 核对 Tab 数、文件夹详情、设置页新旧文案。
- 风险提示：备份 zip 现在含**明文凭据**，需自己保管；仓库无 LICENSE 文件（见 D26）。

### 2026-11-15 · 图标替换落地 + 歌词页移植 music-web-cf-main 全屏歌词

- **图标（用户确认后替换）**：`PlayerIcons.kt` 重写 11 个（Disc / Queue / Filter /
  Shuffle / Repeat / RepeatOne / Folder / Timer / Pause / SkipNext / SkipPrevious），
  统一 1.9 描边、圆角端点；**Lyrics / Order / Cloud / DragHandle 保持原样**（用户指定），
  **新增 Library 图标被否决**（音乐库导航继续使用 Disc）。新增 `PathBuilder.rect()` 辅助，
  `dot()` 同时用于空心圆（stroke 块）。`../preview/icons-redesign.html` 同步为最终对照（15 行）。
- **歌词页（用户 14:07 指令）**：「直接移植我另一个项目 music-web-cf-main 全屏歌词」
  +「歌词原始界面顶部那些都不要了，需要的是纯净歌词界面」。
  参考包内 `index.html` 的 `.m2l-fs`（用户自有项目，D26 起无许可限制）。
  移植到 `app/.../ui/player/LyricsScreen.kt`（整文件重写）：
  1. **纯净竖屏**：删掉顶部封面/信息行；`PlayerSheet.kt` 里歌词页（及横屏）隐藏
     `PlayerTopBar` 与 `PlaybackNotice`；整屏只有歌词 + 右上横屏按钮 + 底部迷你条。
  2. **背景**照抄 Web：135° 深色底 + 4 团 `radial-gradient`（`--m2l-a1..a4` 默认配色），
     **不用 blur**（该仓库注释：blur(64px)+inset 会在移动端生成 1.5 倍屏纹理导致整屏黑）。
  3. **歌词行**照抄 `.lyrics-line`：非当前行 `scale(.82)` 竖屏 / `.75` 横屏 + 30% 白，
     当前行 `scale(1)` + 纯白 + 22px 光晕，已唱过行 72% 白；用 `graphicsLayer.scale`
     而不是改 fontSize（避免长句重新断行）；竖屏居中 + `TransformOrigin.Center`，
     横屏左对齐 + `TransformOrigin(0,.5)`；上下 padding 50% 视口高度。
     边缘渐隐用 `DstIn` 蒙版对应 Web `applyFade()` 的 `(1-d)/0.25`。
  4. **控制条**只留三个按钮 + 进度条；**3 秒无操作与右上工具条一起淡出**（Web 的
     `m2l-chrome-hidden`）。唤回用 `awaitFirstDown(pass = Initial)`——`detectTapGestures`
     在 Main pass 会被子 LazyColumn 吃掉，真机实测点歌词唤不回。
  5. **横屏**：左侧 168dp 大封面 + 封面下方歌名/歌手；右侧左对齐大字歌词；
     迷你条 `left 24dp / width 232dp` 与封面左边缘对齐、`zIndex(3f)` 保证与文字重叠时盖住文字。
  6. 竖屏歌词页隐藏完整 `PlayerTransport`（纯歌词界面）；退出靠返回键 / 右滑回封面页。
- **未移植**：译文独立行（Web 有 `.lyrics-translation`），Android 侧 `Lyrics.kt` 的
  `LyricLine` 没有 translation 字段；封面取色（Web 用 canvas 取 4 色写 CSS 变量），
  目前用仓库默认四色，后续可接 Palette API。
- 验证：`gradlew test` + `:app:assembleDebug` 全绿；真机（PGEM10 / Android 15）
  `uiautomator dump` 核对：竖屏歌词页无「收起 / 正在播放 / 音乐库」、
  有 16 行歌词 +「切换横屏」+ 上一首/暂停/下一首；**3.5 秒后工具条与三个按钮一起消失**、
  歌词仍在（17 行）；横屏 `rotation=1` 时左侧歌名/歌手、右侧歌词、右上「返回竖屏」、
  迷你条悬浮（上一轮已验，本轮布局代码未变）。
- 两份预览 `../preview/icons-redesign.html` / `../preview/lyrics-redesign.html` 已同步到最终实现。

### 2026-11-15 · 文件夹详情雾感修复 + 歌词页照抄 Primuse

- 用户两条反馈：「点开文件夹条目整页雾蒙蒙」；「歌词界面效果照抄
  https://github.com/chenqi92/primuse」。
- **雾感根因**：`LibraryScreen` 的二级页 `AnimatedContent` 挂在 `HazeTitleScaffold`
  的 content 插槽里，而 P7-11 的「主内容压缩渐暗」`graphicsLayer { alpha = 0.55f;
  scale = 0.96f }` 挂在**整个 scaffold** 上——二级页自己也被压成半透明，顶部还叠着
  主页面的「音乐库」标题与分类 Tab。上一批新增文件夹详情时复用了这个插槽，问题才显现
  （专辑详情/歌单同样会雾，只是没报）。
- **修法**：改成与 `SettingsScreen` / `PlaylistScreen` 相同的结构——根 `Box` =
  ① `HazeTitleScaffold`（挂压缩渐暗）+ ② 二级页 `AnimatedContent`（`EnteringPage`，
  全屏不透明）。渐暗只作用于主内容。真机：文件夹 Tab → Music → 整页不透明、自带 Music
  大标题 +「文件夹 · 19 首 · /storage/emulated/0/Music」+ 播放全部；无渐暗、无残留标题，
  返回一级页正常。
- **歌词页**：移除上一版 music-web-cf-main 的粉色渐变/居中/0.82 缩放/白色发光，按 Primuse
  iOS `LyricsScrollView` + `LyricDepthEffectPolicy` + `LyricRowLayoutPolicy` 重写
  `FullscreenLyricLines`：
  1. 排版：26sp / 34sp Semibold、左右 24dp、行内上下 5dp、行间 12dp、左对齐；行宽按
     `(maxWidth - 48dp) / 1.08` 预留，当前句放大后不会顶出右边界。
  2. 视觉：当前句 1.08、无模糊；已唱深 0.32 / 浅 0.40，未来深 0.46 / 浅 0.56；
     模糊 `距离 × 1.25dp` 上限 5dp，已唱过再加一档（`+1`）；逐字歌词未唱字 0.42。
  3. 滚动：36% 视口锚点、540ms `FastOutSlowInEasing`、250ms 提前切句、手动拖动 3s
     宽限后回锚点、上下 12% `DstIn` 渐隐、换句只滚差量不整页瞬移。
  4. **踩坑**：`LazyListItemInfo.offset` 是相对内容区、**不含 `contentPadding`**；
     实际绘制 Y = `topPadding + offset`。一开始漏加，当前句稳定停在 60–69%
     （真机实测顶部 padding 558.72px）。修正后把 `anchorPx` 加进 `LaunchedEffect` keys，
     横竖屏 `maxHeight` 变化会重新锚定。
  5. 背景：去掉自带渐变，只叠主题感知 scrim（深黑 0.30/0.46/0.60；浅白 0.58/0.70/0.80），
     透出 `PlayerBackdrop` 的模糊封面；浅色主题文字用 `Palette.TextPrimary`，
     横屏按钮底色/图标同步主题。
  6. 调试用的 `LyricsAnchor` 日志、tick 循环、`onGloballyPositioned` 探针全部删除。
- 验证：`.\gradlew.bat test` → **28 类 231 例 0 失败**（`TrackIndexTest` 7 例此前未计入
  文档统计）；`:app:assembleDebug` 通过；emulator-5554（900×1600）深/浅两主题截图核对
  锚点、跟随、手动宽限、边缘渐隐；文件夹详情截图核对不透明与标题栏。验收后删除测试曲
  `LyricsDemo.flac` 并全量重扫，曲库 19 → 18。
- 构建：`test` 28 类 231 例 0 失败；`:app:assembleDebug` 20.0 MB、`:app:assembleRelease`
  3.48 MB（R8 + lintVital）均 BUILD SUCCESSFUL。本轮 release 包**未装机复验**（需先卸载 debug
  包会清数据），留待下次发版前用真机冷启动 + 歌词页走查一遍；横屏歌词与超长歌词未专项复验。
- 来源与许可：Primuse 仓库为 **AGPL-3.0-only**，用户 D26 指令下直接采用其布局/动效常量，
  来源与推导写在 `LyricsScreen.kt` 文件头注释。

### 2026-11-15 · 第五轮需求：迷你条圆角矩形 / 歌手列表+搜索与专辑换位 / 歌词页恢复原始 msop 居中

- 用户本轮四条指令：① 迷你播放条改成**圆角矩形**；② 音乐库「歌手」「专辑」位置互换；
  ③ 音乐库「歌手」改成**列表**显示、和 `chenqi92/primuse` 一样、**带搜索框**；
  ④ 歌词界面**恢复成原始** <https://github.com/longda33/msop>，歌词**居中显示**。
- ① 迷你条：`MiniPlayer.kt` 的 `glassShape` 由 34dp 改 16dp（条高约 70dp，视觉上从药丸变
  圆角矩形）；底部细进度线的左右内缩同步 20dp → 16dp，与新的圆角弧度对齐、端点仍不被裁。
  玻璃效果（vibrancy / blur / lens / Highlight / Shadow / InnerShadow）、果冻拉伸、标题跑马灯、
  频谱动效全部未动。
- ② Tab 顺序：`TABS` = 歌曲 / 歌手 / 专辑 / 文件夹，`AnimatedContent` 分支同步换位
  （1 = 歌手，2 = 专辑）。
- ③ 歌手列表：删掉 2 列网格 `ArtistGrid`，新增 `ArtistList` + `ArtistSearchField`：
  1. 版式照 Primuse iOS `ArtistListView.swift` 的 list 形态（AGPL-3.0-only，D26）：44dp
     **圆形**头像 + 歌手名 + 「N 张专辑 · N 首」两行；行高 64dp、左右 20dp。
  2. 顶部固定 48dp 搜索框（与搜索页同一套视觉 + 清空按钮），按歌手名即时
     `contains(ignoreCase)` 过滤；搜索词提到 `LibraryScreen` 页面的 `rememberSaveable`，
     否则切 Tab 回来（`AnimatedContent` 重建子树）会丢输入。
  3. 排序改「未知艺术家沉底 + 名字字母序」——列表形态下比原先的「按曲目数降序」更好找。
  4. 点行仍**直接播放该歌手全部歌曲**（沿用原有手感，本轮未新增歌手详情页）。
- ④ 歌词页：`LyricsScreen.kt` 整文件重写，回到 msop HEAD `PlayerSheet.kt` 里 `LyricsPane`
  / `LyricLines` 的原版形态（撤销同日「第四轮截图版」的 46% 锚点 + 22sp 强调色卡拉 OK +
  页内自带底部控制条）：
  1. 头部一行：42dp 封面 + 歌名/歌手 + 喜欢，下滑收起手势挂在头部。
  2. 歌词**居中**：当前句 28sp 加粗 + 蓝色柔光（`Shadow` blur 26f），其余行 17/15sp；
     透明度按行距空间透视 `0.55/(1+0.5·(d-1))` 钳在 0.14–0.55；上下 12% 用 `DstIn` 蒙版
     真透明渐隐。
  3. 锚点 = 视口**正中**：列表上下各留半屏 `contentPadding`，跟随仍用
     「`contentPadding.top + offset + size/2` 与锚点求差」的修正算法，避免上一轮踩过的
     锚点漂移；换句走 `spring(650f, 0.9f)` 平滑，不硬跳。
  4. 逐字（TTML）卡拉 OK、点行 seek、手动拖动 5 秒后恢复跟随全部照原版。
  5. `PlayerSheet`：顶栏与 `PlaybackNotice` 恢复常驻；进度条/五个控制键/底部 dock 在
     「封面·歌词」两页都显示（dock 的「封面 / 歌词」按钮负责翻页）——与 msop 原版一致。
  6. 唯一适配：原版把歌词写死 `Color.White`，本轮必须兼容 D23 的深/浅两套主题（浅色主题
     白字不可读），墨色按主题取（深色白 / 浅色 `TextPrimary`），柔光色取 `palette.BlueLight`。
- 验证（真机 `127.0.0.1:5555`，900×1600）：
  - 歌手 Tab：列表 + 顶部搜索框截图核对；输入 `zhou` → 「没有找到歌手」空态 + 清空按钮；
    点「小阿七」行直接起播「从前说」（`PLAYING(3)`）。
  - 迷你条：浅/深两主题均呈圆角矩形，底部进度线左右端点完整。
  - 歌词页：左滑进入后为「顶栏 + 42dp 封面信息行 + 居中歌词 + 常驻控制条 + dock（封面/队列/
    定时）」；当前句中心实测约 660px、视口中心约 685px（±2%），28sp 加粗；点第 4 行后
    `position=59947ms`（seek 生效）；深/浅两主题各验一次（浅色墨字、深色白字）。验收后把
    主题切回用户原来的**浅色**。
  - 构建：`:app:compileDebugKotlin`、`:app:assembleDebug`、`test`（debug + release 双变体）
    全部 BUILD SUCCESSFUL。
- 未做：歌手详情页（点行仍是直接播放）；横屏歌词本轮未截图复验——沿用上一轮「左封面 + 右歌词 +
  迷你条」布局，只把右侧渲染器换成新的居中版（代码路径同源）。

### 2026-11-15 · 第六轮需求：歌词页三点菜单 + 横竖屏切换 + 歌词改 music-web-cf-main 效果 / 曲库标题随筛选变

用户五条指令（随附件 `music-web-cf-main.zip` 一起到达）：
① 歌词界面右上角「喜欢」删掉、改成三个点；② 歌词区增加横竖屏切换按钮；
③ 歌词显示效果改用 `music-web-cf-main.zip` 的效果（横屏同样），**但背景颜色不用它那套**；
④ 音乐库「全部歌曲」文字随右上角筛选（用户口中的「沙漏」= 漏斗图标）选择变化；
⑤ 点三点时弹窗要和三点贴在一起。

- 歌词视觉规格逐条取自 `music-web-cf-main/index.html`（用户自有项目，D26 可直接采用，来源行号
  写进 `LyricsScreen.kt` 文件头）：
  - 字号 `baseLyricFontSize`（15224 行）：`clamp(24px, 3.2vmin+15px, 46px)`，再用 `h/4.6` 兜底
    —— Compose 里用 dp 还原 vmin（900×1600 / density 2 → 450×800dp → 29.4sp）。
  - 行样式（3125 行）：`line-height 1.32`、`margin 0 0 .42em`、`weight 700`、`letter-spacing .01em`、
    所有行**同一字号**。
  - 缩放（3139 / 3180 行）：非当前句 `scale(.82)`（≤480px 窄屏）/`.75`（宽屏），当前句 `1.0`，
    过渡 `transform .3s cubic-bezier(.22,.61,.36,1)`；只改 scale 不改 font-size，所以当前句
    「变大」不会重新断行。
  - 白色三档（3132 / 3167 / 3150 行）：未唱 `rgba(255,255,255,.30)`、已唱 `.72`、当前 `#fff`；
    当前句光晕 `text-shadow 0 0 22px rgba(255,255,255,.30)`（Compose 只有一层 Shadow，
    取白 30% + `blurRadius 24f`）。
  - 边缘渐隐（15167 行 `applyFade`）：逐行按「行中心到视口中心的距离」`opacity=(1-d)/0.25`
    ——**替掉上一轮整块 `DstIn` 蒙版**。Compose 版本在这块的写法很关键：距离计算放在
    `Modifier.graphicsLayer{}` 里读 `listState.layoutInfo`，只在绘制阶段求值，滚动时不触发
    每一行的重组（否则 10 行 × 每帧重算会明显掉帧）。
  - 排版方向（3237 / 3245 行）：竖屏居中、横屏左对齐；**缩放锚点必须跟着换**（居中→中心，
    左对齐→左边缘），否则 `.82` 收缩会把整行往左推（web 原注释踩过这个坑）。
  - 逐字行（1059 / 1047 行）：等于「已唱纯白 / 未唱 38% 白」，且逐字行 `text-shadow: none`。
  - 明确**不用**它那套歌词背景（`radial-gradient` + 绿灰渐变）：继续透出播放页模糊封面。
  - 当前句锚点仍是**正中**（web 是 45%）——上一轮用户明确要求居中，这轮只换视觉不改锚点。
- 头部改造（`LyricsTrackHeader`）：删掉「喜欢」圆按钮，右侧改两颗 36dp 圆图标按钮
  「横竖屏切换」+「三个点」。三点菜单用项目既有模式 `Box { 图标; DropdownMenu }`，
  Material3 以这个 `Box` 为锚点向下弹 —— 实测菜单落在三点正下方、右边缘对齐（用户第 ⑤ 条）。
  菜单项：收藏/取消收藏、切换为横屏、重新加载歌词。
- 横竖屏切换：新图标 `PlayerIcons.Rotate`，形状直接搬 web 全屏工具条的 `ICON.land`
  （`index.html` 14871 行 SVG：躺倒圆角手机 + 带箭头弧线）。点击 → `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，
  横屏页右上角同图标点回 `SCREEN_ORIENTATION_PORTRAIT`；横屏页也补了同款三点菜单（收藏项除外，
  横屏迷你条没有收藏入口，只有竖屏页的菜单保留它）。
- `LyricsViewModel.reload()`：`load()` 对同一首是幂等早退（`track.id == currentTrackId` 直接
  return），不清 id 时点「重新加载歌词」毫无反应。
- 音乐库标题联动（`TrackList` 收 `filter`）：`SourceFilter.ALL` → 「全部歌曲 · 本机 + 云端」、
  `LOCAL` → 「本机歌曲 · 只显示本机文件」、`CLOUD` → 「云端歌曲 · OpenList / WebDAV」，
  副标题都带「共 N 首」。
- 验证（真机 `127.0.0.1:5555`，900×1600，浅色为业主当前设置）：
  - 筛选三档：默认「全部歌曲 / 本机 + 云端 · 共 18 首」；只看本机 → 「本机歌曲 / 只显示本机文件 ·
    共 18 首」；只看云端 → 「云端歌曲 / OpenList / WebDAV · 共 0 首」（当前无云端配置）。
  - 歌词页：三点菜单贴在三点正下方（截图 `2026-11-15-lyrics-dots-menu.png`）；点「切换为横屏」→
    `dumpsys input | SurfaceOrientation: 1`，横屏为「左封面 + 右左对齐歌词 + 迷你条」；再点右上角
    旋转 → `SurfaceOrientation: 0` 回竖屏且仍停在歌词页。
  - 点歌词行 → `dumpsys media_session` `position=237660`（seek 生效）。
  - 深/浅两主题各验一次：深色白色三档 + 光晕、浅色 `TextPrimary` 三档；验收后把主题切回
    用户原来的**浅色**。
  - 构建：`:app:compileDebugKotlin`、`:app:assembleDebug` BUILD SUCCESSFUL；`test`（debug+release
    双变体）**28 类 231 例 0 失败**。
- 未做 / 已知：release（R8）包本轮没打；「歌词界面上片段落被头部遮住」是列表自身滚动位置的正常
  表现，没有额外加顶部内边距；git 未提交（工作区本轮前已有大量未提交改动）。

### 2026-11-15 · 第七轮需求（歌词重复行根治 + 工具条搬 web + 液态玻璃控制卡 + 横屏形态）

用户看完第六轮真机截图后追加八条反馈，逐条落地：

1. **歌词「重复行」是 bug**（最重的一条）。从数据层定位根因：把设备上的
   `从前说 - 小阿七.flac`（54,854,986 字节）拉到本机，用 Python 读 Vorbis Comment，
   发现内嵌 `lyrics=` 字段是酷狗/QQ 那种**方括号逐字 LRC**：每行形如
   `[00:44.23]哭[00:44.55]着[00:44.72]说[01:45.21]欠…`，一行 20 多个 `[mm:ss.xx]`。
   旧 `LrcParser` 把**每个方括号都当成一行**、文本取「删掉全部方括号后的整句」，
   于是同一句按字级时间点被复制 20 次（1:44–1:52 之间 8+ 行），屏幕上就是整屏重复。
   修法（`feature/lyrics/.../LrcParser.kt`）：按「方括号之间有没有文字」分流 ——
   - 标签之间**有文字且 ≥2 截** → 逐字时间轴：合成**一行**，每截一个 `LyricWord`，
     `wordJoiner = ""`（不能补空格，否则中文逐字会被拆开）；
   - 标签之间**没有文字**（`[00:10][00:20]歌词`）→ 仍是同一句的多个时间点，保持拆多行
     （副歌复用是正常语义，不能合并）。
   这条修复同时**把逐字歌词能力落地**（用户第 ⑧ 条「融入逐字歌词」）。
   新增 4 个单测：`方括号逐字整行合成一行而不是复制多行` / `多行逐字歌词每句只出现一次` /
   `行首多个方括号仍是同一句的多个时间点` / `方括号逐字的文字拼接保留原始空格`。

2. **横竖屏按钮挪到「独占一行」的工具条**（照 web `m2l-tools`）。新组件 `LyricsToolsRow`：
   逐字胶囊 `WordModeChip`（只有真存在逐字轴时才显示，照 web `display:none` 逻辑；默认关＝「普通」，
   与附件截图一致）+ 4 个 34dp 圆钮（`Target` 偏移微调 / `Swap` 重新加载 / `Collapse` 退出全屏 /
   `Rotate` 横屏）。竖屏放在信息行**下面独占一行右对齐**；**三点菜单里删掉「切换为横屏」**
   （横屏页同理删掉「切回竖屏」），菜单只剩 收藏/取消收藏 + 重新加载歌词。
   新图标 `PlayerIcons.Target` / `Swap` / `Collapse` 形状逐条照 web `index.html` 14871 行附近的
   `ICON.target` / `ICON.swap` / `ICON.min`（项目不引 material-icons-extended，必须自绘）。

3. **迷你控制不能消失**。web 是**有**迷你条的（第二轮截图就是反驳「删掉」的误读）。
   直接**删掉整个 chrome 3 秒淡出逻辑**（`chromeVisible` / `chromeAlpha` / `pointerInput` 全移除），
   横屏控制卡常驻。

4. **原实心灰「背景框」改液态玻璃，背景不要厚白罩**。
   - `LyricsGlassControls`：第一版用 `ink.copy(0.16→0.06)` 半透明，实机呈灰板；
     改成白色半透明 `Color.White.copy(0.34→0.12)` + 白描边 `0.62→0.18`，才读作磨砂玻璃。
   - `PlayerSheet` 遮罩从浅色 `0x66FFFFFF→0xCC` 洗白降到 `0x33FFFFFF→0x9EF4F2FB→0xF2F4F2FB`
     （深色 `0x40→0xB3` 黑 → `0xE607080D`），并把 `Palette.Void` 终止色换成显式 `scrimBottom`；
     同时**删掉** `LyricsScrim()`（横屏那层厚白/黑渐变），整屏透出模糊封面。

5. **音乐库标题下方「N 首 · 本机 · 云端」删掉**：`HazeTitleScaffold` 的 `subtitle = null`，
   一并删掉不再使用的 `localCount/cloudCount`。（`SectionHeader` 那行「全部歌曲 / 本机 + 云端 ·
   共 18 首」保留 —— 用户这轮只点名删标题下方那行。）

6. **「所有歌曲」行三点弹窗必须贴住三点**：`TrackRow` 把 `DropdownMenu` 从「整行 Box」挪进三点
   自己的 `Box(size 30dp)`（原来锚点是整行左边缘，弹窗贴行首弹出）。

7. **歌词区按钮搬过来、位置同 web**：横屏 `LandscapeLyricsPane` 按 web 5 列栅格重排 ——
   `1fr 3.4fr .6fr 7fr 1.4fr`（13.4 份）折算 `leadingInset 7.5%` / `coverColumnWidth 25.4%` /
   `gutter 4.5%` / `trailing 10.4%`，歌词列正好从屏宽 37.4% 起（对齐附件截图里歌词的起始位置）；
   左列 = 封面（`min(列宽-16dp, maxHeight*0.52)`）+ 23sp 粗体歌名（2 行）+ 13sp 歌手 +
   底部液态玻璃迷你控制卡（宽＝封面宽）；右列 = 工具条一行（右对齐，三点作为 `trailing` 并进同一排）+ 左对齐歌词。

8. **「融入逐字歌词」**：`LyricLines` 收 `wordMode`，逐字行按「已唱纯白 / 未唱 38%」逐词染色，
   不加光晕（照 web `.is-played`）；`centerAligned` 两个参数决定中心/左对齐与缩放锚点
   （`.82` 窄屏 / `.75` 宽屏）。偏移微调落地为 `LyricsOffsetDialog`（±0.1s / ±0.5s / 归零，
   `offsetMs` 加到 `positionMs` 后再 `indexAt` 与逐字判定）。

真机验证（`127.0.0.1:5555`，900×1600）：
- 曲库标题只剩「音乐库」，`SectionHeader`「全部歌曲 / 本机 + 云端 · 共 18 首」保留；点歌曲行三点 →
  弹窗贴该三点（截图 `2026-11-15-r7-track-row-menu-anchored.png`）。
- 竖屏歌词页：行文本正常无重复（混音:张鸣利 / 音频编辑:蔡志成 / 和声:赫拉Hera…）、
  工具条独占一行右对齐、三点在右上、背景透出彩色模糊封面；点「普通」胶囊切「逐字」，
  开关变蓝、标签变「逐字」、当前句逐字点亮（截图各一张）。
- 横屏：点工具条横屏钮 → `SurfaceOrientation: 1`，形态为「左封面+歌名+歌手+玻璃控制卡 /
  右工具条（含三点）+左对齐歌词」，无重复行、无富余控制条、迷你控制卡常驻；
  横屏三点菜单只剩「收藏 / 重新加载歌词」，且贴三点弹出。
- 深/浅两主题各验一次（深色玻璃卡配白色描边更像目标图），验收后把主题切回用户原来的**浅色**。
- 构建：`:feature:lyrics:testDebugUnitTest` 48 tests 全绿（含新增 4 例）、`:app:assembleDebug`
  BUILD SUCCESSFUL（20,278,493 字节流式装机）。

已知 / 未做：横屏歌词列左边界当前 ≈37.4% 屏宽（目标图 ≈43%，偏差来自 web 的
`max-width:13em; margin:auto` 在 Compose 里按列宽折算）；`Collapse` 钮竖屏＝回封面页、横屏＝
收起播放页，语义不完全统一；release（R8）包本轮没打；git 未提交。
截图归档：`../preview/verification/2026-11-15-r7-*.png`（9 张）。
### 2026-11-15 · 第八轮需求（删全屏钮 / 横屏藏状态栏 / 工具条与迷你条改悬浮 / 竖屏逐字靠左）

用户看完第七轮真机截图后四条指令：

1. **歌词区删掉「全屏」按钮**：`LyricsToolsRow` 里那颗 ⤢（`PlayerIcons.Collapse`）整颗移除，
   竖屏与横屏都不再有；同时把 `LyricsPage` / `LandscapeLyricsPane` 的 `onExitLyrics` 参数
   一并删掉（`PlayerSheet` 两处调用同步去掉）。退出路径不再依赖这颗按钮：竖屏走底部 dock 的
   「封面 / 歌词」与下滑收起，横屏走「横屏钮回竖屏」。
2. **横屏顶部状态栏不显示**：新增 `HideStatusBar(active)`（`WindowCompat.getInsetsController`
   + `WindowInsetsCompat.Type.statusBars()`），只在 `LandscapeLyricsPane` 里生效；
   行为设 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`（边缘下滑仍能临时唤出），
   组件离场时 `show` 恢复，避免把状态栏永久藏掉。只藏 `statusBars`、留 `navigationBars`，
   否则横屏的手势返回条会一起没了。
3. **工具条与迷你条不再独占一行，改悬浮**（照 web `m2l-tools` 的 `position:absolute`）：
   横屏结构改成「底层 Row（左封面列 + 右歌词列）+ 上层两个 overlay」——
   工具条 `align(TopEnd)` 贴在右上、迷你控制卡 `align(BottomStart)` 贴在左下，
   两者都不再参与布局；歌词列只加 `padding(top = 46.dp)` 给工具条让位，不再被整行挤压。
4. **竖屏逐字按钮靠左**：`LyricsToolsRow` 新增 `spreadChip` 参数——竖屏 `Arrangement.SpaceBetween`，
   「普通 / 逐字」胶囊贴左、三颗圆钮贴右；横屏仍整组贴右上（`Arrangement.End`）。

真机验证（`127.0.0.1:5555`，900×1600）：
- 竖屏：胶囊在左（x≈90）、右侧只剩准星 / ⇄ / 横屏 三颗圆钮，全屏钮消失；歌词无重复行。
- 横屏：`SurfaceOrientation: 1`，**顶部状态栏消失**（无时间/电量图标），工具条浮在右上、
  迷你玻璃卡浮在左下，歌词列从屏宽 37.4% 起且首行不被工具条压住。
- 深色横屏复验一次（浮层与玻璃卡正常）；从横屏点横屏钮回竖屏后**状态栏恢复**，
  主题验完切回用户原来的**浅色**。
- 构建：`:app:compileDebugKotlin`、`:app:assembleDebug` BUILD SUCCESSFUL。
- 截图归档：`../preview/verification/2026-11-15-r8-*.png`（3 张）。

已知 / 未做：`PlayerIcons.Collapse` 图标本身留在 `PlayerIcons.kt`（当前无引用，留着备用）；
release（R8）包仍未打；git 未提交。

### 2026-11-15 · 第九轮需求（歌词浮层自动隐藏 / 横屏照 web 重排 / 歌单入口挪位 / 重扫反馈）

用户看完第八轮真机截图后四条指令：① 歌词区按钮没有自动隐藏（web 是 3 秒无操作隐藏工具条 +
迷你控制条，一动就回来）；② 横屏布局乱七八糟，要求与 `music-web-cf-main` 完全一致，按钮与迷你条
悬浮、可以盖住其它内容；③ 曲库右上角菜单「播放列表」改名「歌单」并挪到「文件夹」前面；
④「重新扫描曲库」增加反馈。

规格全部回到 web 源逐条复核（`music-web-cf-main/index.html`，911,286 字节）：

1. **自动隐藏**（web 语义是「自动淡出」，不是「删掉」——第七轮用户报的「迷你控制消失」正是这条）：
   `HIDE_CLASS = 'm2l-chrome-hidden'`，`showChrome()` 先移除类、清 `hideTimer`，再
   `setTimeout(3000)` 加类；`overlayOpen()`（偏移面板/背景/保存弹窗）期间不计时；唤醒监听挂在
   歌词区（全屏时是整个 `.player-panel`），事件 `pointermove / pointerdown / wheel / touchstart`
   加全局 `keydown`。CSS：`html.m2l-chrome-hidden .m2l-tools{opacity:0;pointer-events:none}`（2732 行）、
   `html.m2l-fs.m2l-chrome-hidden .player-controls{opacity:0;pointer-events:none}`（3091 行）——
   全屏时工具条与迷你条**一起**淡出，过渡 `opacity .38s ease`。
   Compose 落法（`LyricsScreen.kt` 新增四个东西）：
   - 常量 `CHROME_IDLE_MS = 3000` / `CHROME_FADE_MS = 380` / `CHROME_TICK_MS = 120`；
   - `ChromeVisibility(visible, wake)` + `rememberChromeVisibility(paused)`：内部 `lastTouch`
     （`mutableLongStateOf`，只在协程里读，不参与重组）+ `LaunchedEffect(paused)` 里 120ms 轮询，
     `paused` 时恒显、退出 `paused` 时重置计时（等价 web 的 `overlayOpen` 不计时）；
   - `Modifier.wakeChrome(chrome)`：`pointerInput` 的 **Initial pass** 观察全部 pointer 事件，
     只读不消费，所以歌词点击 seek / 滚动 / 下拉收起都不受影响；
   - `ChromeOverlay(visible, …)`：`animateFloatAsState` 透明度 + 隐藏时 `detectTapGestures{}`
     吞点击（等价 `pointer-events:none` 的反面：隐藏后不再误触按钮）。
   竖屏工具条从「独占一行」改成浮层：`Box{ Column{ 信息行; 歌词 }; ChromeOverlay(align TopStart,
   fillMaxWidth, padding top 52dp){ LyricsToolsRow(spreadChip = true) } }`，根 `Box` 挂
   `.wakeChrome(chrome)`；工具条盖住顶部第一条歌词属于预期行为（web 注释写明）。
   横屏工具条与迷你玻璃卡各包一层 `ChromeOverlay`（`TopEnd` / `BottomStart`），3 秒后一起淡出。

2. **横屏照 web 矮窗栅格重写 `LandscapeLyricsPane`**：
   `@media (max-height:560px) and (min-aspect-ratio:1.15/1)`（3265 行）→
   `minmax(0,1fr) minmax(0,3.4fr) minmax(0,.6fr) minmax(0,7fr) minmax(0,1.4fr)`（共 13.4 份）
   → 左留白 7.5% / 封面列 25.4% / 间距 4.5% / 歌词 52.2% / 右留白 10.4%。
   - 封面 `coverSize = min(coverColumnWidth, maxHeight*0.56)`（对齐 web `--m2l-art`
     `min(W*3.4/13.4, H*0.56)`，15315 行）；去掉了上一轮多减的 16dp 与 `0.52f`。
   - 左列改 `Column(width = coverColumnWidth, fillMaxHeight, verticalArrangement = Center)`，
     装封面 + 歌名（`titleSize = min(base*1.2, H*0.6/8.8*0.95)`）+ 歌手
     （`artistSize = min(base*0.9, H*0.5/8.8*0.88)`，15224 行公式），整组垂直居中。
   - 歌词列 `Box(weight 1f)` 不再给工具条留 46dp（浮层不占位）。
   - 行宽 `lineMaxWidthEm = 13f`（3270–3282 行，注释明确 13em 是三诉求平衡点：更窄会折行、
     更宽 `margin:auto` 失效贴左）：`LyricLines` 在 `BoxWithConstraints` 里算
     `lineInset = ((maxWidth - lyricSize*13)/2).coerceAtLeast(0.dp)`，行 `Text` 用
     `fillMaxWidth().padding(horizontal = lineInset)` 等价 web 的 `max-width + margin:auto`。
   - 工具条 `position:fixed` 语义（3169–3174）→ `ChromeOverlay(align TopEnd, padding top 12dp
     end 16dp)`，内含 `trailing = LyricsOverflowMenu`（三点仍在同一排）。
   - 迷你条（3296–3300 矮窗 `width:min(340px,70vw)`）→ 贴封面列：`ChromeOverlay(align
     BottomStart, padding start = leadingInset, bottom = 14dp, width = coverSize)`，内含
     `LyricsGlassControls(fillMaxWidth)`，正好压在封面列、会盖住歌名——与用户附件截图一致。

3. **「播放列表」改名「歌单」并挪到「文件夹」前面**：`LibraryScreen` 的 `TABS` 改 5 项
   「歌曲 / 歌手 / 专辑 / 歌单 / 文件夹」；`PlaylistScreen.kt` 拆成两个 public 组合：
   `PlaylistTab(playlistVm, topInset, onOpenDetail, modifier)`（内嵌列表：首行「歌单 / N 个歌单 ·
   点条目查看或管理」+ `CircleGlassButton` 新建，空态 `EmptyHint` + `PrimaryPill("新建歌单")`，
   自带新建/重命名/删除三弹窗）与 `PlaylistDetailPage(...)`（`BackHandler` + 内部 `PlaylistDetail`
   + 重命名/删除弹窗，删除后 `onBack()`）。二级页状态由 `LibrarySecondary.Playlists`（data object）
   换成 `LibrarySecondary.PlaylistDetail(id: Long)` + 页面级 `detailPlaylistId: Long?`
   （`rememberSaveable`），overlay 分支按 id 从 `playlistVm.playlists` 找回对象。
   文案统一「播放列表」→「歌单」（新建歌单 / 重命名歌单 / 删除歌单？ / 还没有歌单 / 歌单还是空的）。
   右上角菜单删掉「播放列表」项，只剩「重新扫描曲库」。

4. **重扫要反馈**：`SettingsViewModel` 注入 `LocalScanScheduler`（`feature:local`），新增
   `ScanState(running, scanned, written, finished, elapsedMs, failed)` 与
   `scanState: StateFlow<ScanState>`（`observe()` 的 `WorkInfo.State.RUNNING/SUCCEEDED/FAILED`
   → 读 `progress` 与 `outputData` 的 `KEY_SCANNED/KEY_WRITTEN/KEY_ELAPSED_MS`，
   `stateIn(WhileSubscribed(5000))`）。`SettingsScreen` 的「重新扫描曲库」行按实时状态换副标题：
   运行中「正在扫描… 已扫到 N 首（写入 M 条）」、失败「上次扫描失败，可以再点一次重试」、
   点过后完成 8 秒内「扫描完成 · 共 N 首 · 用时 X.X 秒」（8 秒后回落默认说明）；点击同时弹
   `Toast("已开始重新扫描曲库…")`。`rescanRequested` 标志保证只显示「刚点的那次」结果，
   冷启动不会把上次结果当成新反馈。

真机验证（`127.0.0.1:5555`，900×1600，浅色）：
- 竖屏歌词页：进入后约 3 秒工具条整组淡出（只剩信息行 + 歌词 + 控制条）；点一下头部立刻回来
  （胶囊在左 x≈117、右侧准星/⇄/横屏三钮），工具条浮在信息行下方 y≈290px、确实盖住顶部的暗歌词行。
- 横屏（`dumpsys input | SurfaceOrientation: 1`）：封面 x 125–525 / y 152–560（400×408px，
  `min(列宽, H*0.56)` 取列宽），歌名大号粗体、歌手被底部玻璃卡盖住；歌词 13em 居中、长句正常折行；
  工具条浮右上（右边缘 16dp）、玻璃卡浮左下且宽度＝封面宽；4 秒后工具条与卡**一起**淡出，
  只剩封面 + 歌名 + 歌词，状态栏隐藏。
- 曲库：Tab 行为「歌曲 歌手 专辑 歌单 文件夹」；右上角三点菜单只剩「重新扫描曲库」。
- 歌单 Tab：空态「还没有歌单 / 新建歌单」→ 新建 `TestMix` → 列表行（0 首 · 歌单 + ⋮ + ›）→
  点行进详情（返回键 + TestMix + 0 首 + 添加歌曲 + ⋮，无「音乐库」标题栏残留）→
  「添加歌曲」页（18 首可选、搜索框、页脚「选择要添加的歌曲 / 添加」）→ 加 1 首后详情变
  「1 首 · 3 分钟」+ 封面信息卡 + 行 → 行三点「查看文件信息 / 从歌单移除」→ 删除歌单确认弹窗
  「删除歌单？」→ 确认后回落列表空态（测试歌单已删，设备曲库仍 18 首）。
- 设置页：点「重新扫描曲库」→ toast「已开始重新扫描曲库…」+ 行值变绿「完成」、副标题
  「扫描完成 · 共 18 首 · 用时 0.1 秒」；8 秒后回落「全量重扫会重新读取所有文件的标签与封面」。
  （18 首增量扫 0.1 秒，「正在扫描…」态来不及截图，代码路径与完成态同一条。）
- 构建：`:app:compileDebugKotlin`、`:app:assembleDebug` BUILD SUCCESSFUL（19,997,230 字节流式装机）；
  `test`（debug + release 双变体）28 类 235 例 0 失败。
- 截图归档：`../preview/verification/2026-11-15-r9-*.png`（8 张）。

已知 / 未做：竖屏浮层工具条压在顶部第一条（最暗的那行）歌词上，是 web 的表现，未额外避让；
「播放列表」入口从右上角菜单移除后，歌单只能从 Tab 进入（用户原意若是「菜单项保留」，需回补一项）；
横屏仍没有「直接退出播放页」的入口（第八轮遗留，用户未答复）；release（R8）包未打；git 未提交。

### 2026-11-16 · B 类 · 音源配置多实例化 + HTTP 明文 + 状态 + 手动删除（真机验收）
- 做了什么：
  1. **多配置 = 可保存多条，同一时刻每类音源只有一条「启用中」**。两套 SharedPreferences
     （`msop_openlist` / `msop_webdav`）改为 `profile_ids`（逗号分隔有序）+ `active_id` +
     `p_{id}_{field}` 前缀；token 与同步计数按 profile 分开；旧单配置在 `init` 里一次性
     迁移成 `p1` 并写 `profiles_migrated=true`。`load()` 仍只返回 `activeConfig()`，
     所以下游 `OpenListClient` / `WebDavClient` / 两个 Syncer / `BackupManager` 签名零改动，
     曲目 id 沿用 `openlist:{sha1(path)}` / `webdav:` 不需要改 id 空间。
  2. **HTTP 明文**：`network_security_config.xml` 的 base-config 放开（静态配置无法按域名动态
     放行），真开关在应用层——每条配置带 `allowPlainHttp`，只有用户保存 `http://` 时在
     「确认使用明文 HTTP？」弹窗点「仍然继续」才置 true；`OpenListClient.requireConfig()` /
     `WebDavClient.ensureAllowed()` 见到 `http://` 且标记 false 直接抛
     `PlainHttpNotConfirmed`，请求发不出去。地址不带协议默认补 `https://`。（D27）
  3. **状态**：`ServerStatusInfo(status/message/atMillis)` + `UNTESTED/OK/FAIL`，列表行显示
     状态点 + 文案 + 「曲库 N 首 · 上次同步 …」；同步成功后回写「已同步 N 首」。
  4. **手动删除**：行内 ⋮ 菜单（编辑 / 测试连接 / 启用并同步·立即同步 / 删除），删除要二次确认
     「只会删除这条配置，曲库与其它服务器都不受影响」；删除非启用项不动曲库，删除启用项会
     `clearRemote()`；另有「清除全部」。
  5. **音乐目录（rootPath）**：内网盘 `od-20200305` / `od-20200439` / `od-20200441` 三个目录
     服务端返回 HTTP 500，全盘 BFS 直接失败；配置加「音乐目录（可选）」从该目录起 BFS，
     并对单个目录失败 try/catch 跳过（`RemoteSyncStats.skippedDirs`，结果文案「跳过 N 个异常目录」）。
  6. 真机暴露并修掉的问题：测试连接在主线程跑阻塞网络调用 → `NetworkOnMainThreadException`
     （报「测试失败：null」），改 `withContext(Dispatchers.IO)`；「保存并同步」后 `busy` 不清
     → 一直挂「正在同步曲库…」，两个 VM 都在拿到结果后统一清 `busy`；编辑器结果文案被悬浮
     迷你条压住 → message 块上移到按钮行之上 + 两个配置页尾部补 `LocalHazeBottomInset + 28dp`。
- 改了哪些文件：`core/datastore/{ServerConfig,WebDavConfig,ServerStatus,ServerConfigStore,WebDavConfigStore}`、
  `core/network/{OpenListClient,WebDavClient,OpenListException}`、
  `feature/remote/RemoteLibrarySyncer`、`app/src/main/res/xml/network_security_config.xml`、
  `app/.../ui/settings/{ServerListComponents,OpenListViewModel,WebDavViewModel,OpenListConfigScreen,WebDavConfigScreen,SettingsScreen}`、
  `app/.../ui/components/TrackRow`。
- 验证方式：`:app:assembleDebug` BUILD SUCCESSFUL（20,021,630 字节，装机 `127.0.0.1:5555`）。
  真机（PGEM10 / Android 15 / 900×1600）：OpenList 内网 http 同步 **60 首**、远程流播
  `state=PLAYING(3)`；WebDAV 内网 http **187 首**、`https://dav.2dland.cn` **3 首**；
  切换启用项 187 → 3 → 187（两台计数各自保留）；删除非启用项后曲库仍 60 首；koyeb 两台因
  本设备解析到 `[::1]:443` 显示 FAIL（PC 端 5 台全部 200/207，非代码问题）。
  单测本轮重跑结果见 `TASKS.md` 与答复。
- 遗留问题：
  1. 「支持添加多个」按「保存多台 + 单选启用」实现；若用户要「同时启用多台、曲库合并」，
     必须把 profile 前缀写进 track id，属另一规模改造。
  2. `network_security_config` 全局放开明文是对硬约束第 7 条的偏差（D27），待用户确认接受。
  3. koyeb 两台（OpenList + WebDAV）只能在 PC 端证明可用，本设备网络不通。
- 给下一位的提醒：改配置页先读 `ServerListComponents.kt` 与两个 VM 的 `persistAnd`；
  构建用 `java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain`，
  `gradlew.bat` 在 PowerShell 下会静默失败；`assembleDebug` 有时误判 UP-TO-DATE，加 `--rerun-tasks`。

### 2026-11-16 · 第十轮（r11）· 多台音源同时启用 + 云端时长/封面 + 歌单体系 + 歌词页收尾（模拟器验收）
- **设备口径更正**：测试设备是**安卓模拟器**（`adb -s 127.0.0.1:5555`，`ro.product.model` 伪装成
  `PGEM10`、`ro.hardware=OPPO`、Android 15、900×1600、density 2；`ro.kernel.qemu` /
  `ro.boot.qemu` 被清空，但 `ping 10.0.2.2` 通 QEMU NAT 网关、`ttl=255` 可判定为模拟器）。
  此前文档里所有「真机 PGEM10」一律按模拟器理解。模拟器网络经宿主机代理
  `10.0.2.2:10808`（`adb shell settings put global http_proxy 10.0.2.2:10808`，仅设备侧设置，
  **不写进项目代码**，撤销用 `settings put global http_proxy :0`）。
- 做了什么：
  1. **多台同时启用**：`ServerConfigStore` / `WebDavConfigStore` 从「单选 active_id」改成
     `enabled_ids` 集合，同步时逐台跑、各自 `markSynced(id, ownCount)`；配置行菜单补
     **「取消启用」**。实测 OPENLIST 两台同时启用：LAN 60 首 + koyeb 23 首，汇总
     「扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 · 清理 0 条 · 26800 ms」。
  2. **跨服务器去重**：`RemoteDeduper` 按规范化标题合并、保留体积更大者；复同步幂等
     （第二次 `清理 0 条`）。设备 DB 实查 `SELECT uri` 无重复，历史重复来自「同名不同路径/
     不同格式」（OPENLIST `发如雪 (史诗版)` flac+m4a 两份；WEBDAV `你那么爱她` / `单车 李克勤` /
     `有你在 梁静茹` / `醉拳 (国语/粤语) 成龙` 各两份）。**本地与云端同名不合并**（如
     `多远都要在一起` 同时有 LOCAL 与 OPENLIST 条目），歌手详情页会出现两条同名列，属未决口径。
  3. **云端无时长**：OpenList v4.2.9 的 OpenSubsonic 完全不返回 `duration`
     （`search3` / `getSong` / `getAlbum` 的 song 都没有，`album.duration` 写死 0）。改客户端
     读文件头：`core/network/.../RemoteDurationProbe.kt`（`Range: bytes=0-65535`，解析 FLAC
     STREAMINFO / WAV / MP3 Xing 或首帧码率 / M4A mvhd，moov 在尾部时再取尾部一次；
     先按 ID3v2 synchsafe 跳标签再判格式，覆盖「.mp3 实为 FLAC」）。两个 Syncer 在 upsert 前
     把旧 `durationMs>0` 合并回新 Track（用 `TrackDao.bySource(source)` 取旧值），否则复同步会
     被 `durationMs=0` 覆盖。实测 OPENLIST 时长 **83/83**。
  4. **koyeb 走 OpenSubsonic apiKey**：`olmp_fi9…`（完整 key 不入库，本地备份在 `../archive/` 之外的密码管理器），
     REST 前缀 `/@rest`，补艺术家/专辑/封面；该机索引只覆盖 `/od-20200439/musicweb` 的 23 首，
     `covers` 只报到 11 首（覆盖不均属服务端索引所限）。
  5. **歌单体系补全**（Room 升 v4，`MIGRATION_3_4` 非 destructive）：`playlists` 加
     `sortOrder` 并按 `createdAt` 倒序回填；新建 `favorites(trackId PK, addedAt)` 带 CASCADE
     与索引。`PlaylistDao` 补 `observeMemberships/minSortOrder/setSortOrder/reorderPlaylists/
     reorderTracks/moveTrack/observePlaylistIdsOf/playlistIdsOf`，`DatabaseModule` 注册
     `provideFavoriteDao`。UI：收藏虚拟歌单 `FAVORITES_ID=-1` / `FAVORITES_NAME="收藏歌曲"`
     置顶；歌单列表与歌单内曲目都支持长按右侧手柄拖动排序（`ui/components/Reorder.kt` 的
     `ReorderState` / `ReorderHandle`，`detectDragGesturesAfterLongPress`、整行跟手、越过半行
     换位）；三点菜单全项带图标（查看文件信息 / 收藏 / 加入歌单 → **已加歌单** / 移动到其它
     歌单 / 从歌单移除 / 移除曲库），`PlaylistPickerDialog` 内置；「加入歌单」在 0 个歌单时也
     显示（点开提示先新建）。
  6. **歌手点开详情**（不再点了就播）：`ArtistDetailScreen` = 头像 + 播放全部 + 曲目列表 +
     同一套三点菜单；`ArtistList` 的 `onPlay` 改 `onOpen`。
  7. **歌词页收尾**：三点菜单并入播放页顶栏（播放/歌词共用，菜单项删掉「切换为横屏」）；
     点歌词行**不再 seek**（`LyricsScreen.kt` 的 `onSeek` 已无调用点）；横屏
     `PlayerSheet.kt` 的 `DisposableEffect` 给 window 加 `FLAG_KEEP_SCREEN_ON`（`dumpsys window
     windows` 实测 `fl=KEEP_SCREEN_ON`）；工具条与玻璃迷你卡 3 秒自动淡出、悬浮可盖住其它；
     横屏布局 = 左封面 + 歌名歌手 + 右歌词列 + 右上工具条浮层 + 左下玻璃卡压住封面列，系统
     状态栏隐藏。
  8. **设置页长提示词不再被省略**：`SettingRow` / `SettingSwitchRow` 都改
     `heightIn(min = 66.dp)` + 副标题 `maxLines = 3`（原来是固定 `height(66.dp)` + `maxLines=1`，
     窄屏 450dp 下尾部会被省略号切掉）。
- 验证方式：`test`（debug + release 双变体）**28 类 / 254 例 / 0 失败**；`:app:assembleDebug`
  BUILD SUCCESSFUL 并装机冷启动无崩溃；模拟器（900×1600）逐项截图——
  曲库 288 首、歌手详情、歌单置顶 + 拖动换序（`input draganddrop` 后 DB 顺序与 UI 一致）、
  加入歌单 →「已加歌单」流转、曲目拖动落库、移动到其它歌单、从歌单移除、移除曲库
  （tracks 288→287、WEBDAV 187→186）、WebDAV 立即同步（219 项 / 182 首 / 去重 5 / 清理 186 /
  20361 ms）、来源筛选标题联动、歌词页工具条与三点、横屏布局与不息屏。
- 遗留问题：
  1. **WEBDAV 时长只到 53/182**：宿主机实测该 OpenList WebDAV `/dav` 对 `GET` 文件一律
     `403 Forbidden`（`PROPFIND` 207、匿名 401），`WebDavLibrarySyncer.fillMissingDurations`
     逐首探测失败即 null、无日志无重试 → 疑为服务端限制，设备端能否 GET 未取到直接证据。
  2. **多台同时启用后 track id 变成 `openlist:{profileId}:{sha1}`**，旧 `openlist:{sha1}` 条目
     被差集清理，歌单里引用旧 id 的远程曲目会失效（是否做 id 迁移未决）。
  3. 去重策略「按标题合并、保留体积更大者」会合并同名不同歌；本地 vs 云端同名不合并。
  4. 歌单内「移动到其它歌单」后源歌单 position 留空隙（未重排为 0..n-1）；收藏虚拟歌单详情
     未提供「移动到其它歌单」。
  5. koyeb WebDAV 那台仍 FAIL（设备解析到 `[::1]:443`），模拟器代理下未复验。
- 给下一位的提醒：`gradlew.bat` 在 PowerShell 下静默失败，用
  `java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain`；
  两个 Gradle 会话并发会撞 `classes.jar 被占用`，先 `--stop`；查 DB 用
  `run-as com.msop.player sqlite3 databases/msop.db < /data/local/tmp/qN.sql`（SQL 先落本地文件
  再 push，直接传带括号的 SELECT 会报 incomplete input）；点菜单项前用 `uiautomator dump`
  取中心坐标，别照截图猜。
> AI生成

## 2026-11-16 第十一轮（r12）：菜单体系 / 收藏链路 / 下载 / 并发 / 批量 / 通知栏

> 用户十条指令一次性落地。测试设备仍是**安卓模拟器**（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。

### 一、做了什么

1. **清空旧歌单**（数据侧）：设备 `playlists / playlist_tracks / favorites` 全清（`tracks` 保留 283），验收从 0 个歌单开始。
2. **三点菜单体系**：
   - 曲库/歌手/歌手详情所有曲目菜单**删掉收藏**（`TrackMenuActions.showFavoriteItem` 默认 `false`，只有收藏虚拟歌单里才给 `true`）；
   - 播放页顶栏三点菜单**删掉「重新加载歌词」**（`PlayerSheet` 共用菜单与横屏 `LyricsOverflowMenu` 同步删除，重载能力仍在歌词工具条那颗 ⇄ 按钮上）；
   - 「加入歌单」图标换 `PlayerIcons.PlaylistAddCircle`（圆圈加号，与「移除曲库」圆圈减号同笔画）；
   - 歌单详情曲目菜单**删掉「加入歌单」**（已在歌单里）。
3. **云端下载**：`ui/components/TrackRow` 尾部对 `source != LOCAL` 追加「下载歌曲」（`onDownload`）；`RemoteFileDownloader` 解析直链后 OkHttp 直连写盘——有 SAF 目录走 `DocumentsContract.createDocument`（重名自动加序号），否则 `getExternalFilesDir(DIRECTORY_MUSIC)`；设置页「播放」组新增**下载路径**行（`DownloadConfigStore` + `OpenDocumentTree` + 持久授权 + 「恢复默认下载目录」）。
4. **并发数**：`ServerConfig` / `WebDavConfig` 加 `concurrency`（0=默认，钳 1..16，默认 4）；OpenList / WebDAV 编辑器「音乐目录」下加「请求并发数（可选）」；两个 Syncer 的目录 BFS 与 `fillMissingDurations` 都按并发数并行（`Semaphore` + `AtomicInteger`）。
5. **歌单批量操作**：详情页「选择歌曲」进多选，顶栏换「全选 / 完成」，列表换 `SelectableTrackRow`，底部浮玻璃 `BatchActionBar`（移除 / 移动 / 加歌单 / 下载）；收藏虚拟歌单只有「选择歌曲」+「取消收藏」。DAO 新增 `@Transaction removeTracks` / `moveTracks`。
6. **爱心不进收藏**——根因是两套数据：播放页爱心写 SharedPreferences，歌单页读 Room `favorites`。`LibraryViewModel` 改读写 `FavoriteDao`（`observeIds()` / `toggle`），现在爱心与收藏虚拟歌单同一份数据。
7. **本地与云端同名合并**（`ui/library/TrackMerger`）：按「规范化歌名 + 歌手（小写去空白去标点）」比对，命中丢云端条、保本机条；**只影响展示**，DB 两边都在。实测 283 → 278。
8. **玻璃更实**：迷你条与导航栏垫色 `0.55/0.60` → 浅 `FAFAFA 0.72` / 深 `121212 0.74`。
9. **横屏歌词右移**：`LandscapeLyricsPane` 补 web `.player-panel` 的 4% 内边距（`sidePadding`），并把列间距由 4.5% 提到 7.5%（右留白 10.4% → 9%），歌词正文左缘从 40.5% 屏宽移到约 42.5%，对齐用户附件（2048×922，正文 x≈43.9%）。
10. **通知栏重做**（参考 power690/ShuYin）：`ShuYinNotificationProvider` 实现 Media3 `MediaNotification.Provider`——自建 `msop_playback` 频道（**必须自建**，否则系统 `No Channel found` 直接丢通知）、`Notification.MediaStyle` 大封面 + 三键紧凑视图 + 末尾「关闭」、`CATEGORY_TRANSPORT`、`setColor(0xFF7C6CFF)`、真实进度条；封面通过 file/content 或 http 后台下载后 `Callback.onNotificationChanged` 刷新（HashMap LRU + AtomicBoolean 防重）。

### 二、设备实测（逐条）

| 项 | 结果 |
|---|---|
| 旧歌单 | `playlists=0 / playlist_tracks=0 / favorites=0 / tracks=283` |
| 曲库三点菜单 | 「查看文件信息 / 加入歌单（圆圈加号）/ 移除曲库 / 下载歌曲」，贴三点弹出，无收藏 |
| 下载歌曲 | toast「已保存：…/files/Music/Its …」，落盘 `Its My Life Bon Jovi.flac` 29,648,735 B；重复下载自动 `_2.flac` |
| 设置页下载路径 | 行可见：`Music（应用私有目录）` + 「选择」 |
| OpenList 并发输入框 | 编辑器出现「请求并发数（可选）」空框；默认 4 时同步「扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 · 清理 1 条 · **30586 ms**」 |
| 歌单多选 | 选择 → 勾 2 首 → 底部栏亮起；**下载**两首云端全成功（`Love Paradise - 陈慧琳.flac` 22,658,255 B）；**移除** 3 首 → 2 首；**移动** 到 MoveTest（DB `BatchTest\|1 / MoveTest\|1`）；**加歌单** 到 MoveTest（`MoveTest\|2`） |
| 播放页爱心 | 点爱心 → `favorites` 写入 `local:e6250c3e…`，收藏歌曲详情变「1 首 · 冬眠」 |
| 播放页三点菜单 | 只剩「取消收藏」（重新加载歌词已删） |
| 横屏歌词 | 封面左移让位、正文左缘 40.5% → ≈42.5%，工具条浮右上、玻璃卡压封面列 |
| 通知栏 | 下拉见媒体卡：大封面、歌名「冬眠」+ 歌手「司南」、进度、上一首/播放/下一首，`channel=msop_playback`、`category=transport`、`actions=4` |
| 同名合并 | 曲库 283 → 278（去重 5 条），本地条优先 |
| 玻璃 | 迷你条/导航栏明显更实，不再透出后面列表文字 |

### 三、构建与测试

- `test`：**28 类 / 235 例 / 0 失败**（debug；release 变体 app 侧 19 例亦 0 失败）。
- `:app:assembleDebug` → **20,015,144 字节**，装机后冷启动无崩溃（含通知频道修复后的包）。
- `:app:assembleRelease` 本轮未重打。

### 四、坑与注意

- 自定义 `MediaNotification.Provider` 时频道不会由 Media3 代建：`NotificationChannel` 必须在 Provider 里自己建，否则 `NotificationService: No Channel found` 静默丢通知。
- `Notification.Action.Builder` 吃 `android.graphics.drawable.Icon`，Media3 的 `ActionFactory` 只提供 `createMediaActionPendingIntent(session, command)`；用 `createMediaAction` 再转换会多绕一层。
- `run-as … sqlite3` 传带括号的 SELECT 会报 `incomplete input`：SQL 先写本地文件再 `adb push` 进 `/data/local/tmp`。
- 曲库三点菜单项间距很窄，点之前必须 `uiautomator dump` 取真实 bounds 中心；本轮曾按截图坐标点 `777` 落在「移除曲库」上，误删一条云端曲（已通过一次 OpenList 同步恢复，`tracks` 283、Butter-Fly 1 条）。

### 五、遗留

1. 通知栏视觉只按「ShuYin 大封面 + 三键 + 关闭 + 进度」实现，未做像素级对照（ShuYin 仓库当次 fetch 失败）。
2. ~~模拟器无锁屏（keyguard 关闭），锁屏媒体卡未截图验证~~ **已补验（2026-11-16 15:42，用户手动打开锁屏后）**：亮屏停锁屏页，媒体卡按同一 `MediaStyle` 呈现——大封面铺满卡片、歌名「冬眠」+ 歌手「司南」、进度条、左下上一首/右下下一首、右侧播放键；在锁屏上点暂停 → `dumpsys media_session` `PAUSED(2)/position=63858ms`，按钮立即变播放态，进度与封面不变。截图 `%TEMP%\r12shots\42-lockmedia.png`、`43-lockpause.png`。
3. 「下载到 SAF 目录」只做了代码路径，未在设备上实际选目录验证（本轮验的是默认应用私有目录）。
4. WebDAV 编辑器并发框未逐个点到（OpenList 已验，两处同一组件）。
5. `assembleRelease` 未重打；用户要的话下轮补。
---

## 2026-11-16 追加（通知栏 / 锁屏播放器重做 + 三件遗留验完）

用户贴出两张目标图（`通知栏播放器.PNG` / `锁屏播放器.PNG`，指向 `cosmictaserdev-creator/Convx`）要求「照抄」，并追加「不要猜，检索 GitHub 解决」。结论与实现如下。

### 一、Convx 与系统媒体通知的真相

- `Convx` 的 `app/src/main/kotlin/com/convx/music/playback/MusicService.kt` 第 675 行只是 `setMediaNotificationProvider(DefaultMediaNotificationProvider(this, { NOTIFICATION_ID }, CHANNEL_ID, R.string.music_player).apply { setSmallIcon(R.drawable.convx_notification) })`，仓库里**没有**任何通知布局（`res/layout` 只有 widget）；`DefaultMediaNotificationProvider`（Media3）也只设 `MediaStyle` + `setShowActionsInCompactView` + `setLargeIcon`，不设 `setColor`/`setProgress`/colorized。
- 也就是说用户截图那种「紧凑卡」是**旧版系统 UI 的媒体卡模板**，不是某个项目的自定义布局。本机 Android 15 的 SystemUI 只画「整张大封面 + 波浪进度条 + 右上大圆键」。
- 实测走不通的六条路（全部在设备上验过，截图 `50/51/52/54/56/62/68-*.png`）：① 只改字段（去 progress / 去关闭 / 改 color）仍是系统大卡；② `DecoratedCustomViewStyle` + 大卡自定义视图 → 通知自动展开成大卡且右侧多 `setLargeIcon` 缩略图；③ 去掉大卡只留折叠自绘 + `setColorized(true)` → 通知栏出现自绘卡，但锁屏整条看不见（频道 IMPORTANCE_LOW 的静音通知默认不上锁屏）；④ `Notification.MediaStyle` + 自定义视图 → 系统接管回大卡；⑤ `Notification.DecoratedMediaCustomViewStyle` + 三颗 `Action` → 依旧被接管；⑥ 换新频道 `msop_playback_v2`（DEFAULT）后锁屏能显示，但系统把带色通知重新套白底，白字看不见。

### 二、最终实现（自绘紧凑卡）

- 样式：**普通通知 + `Notification.DecoratedCustomViewStyle()`**，内容视图 `notif_player_small.xml`：`FrameLayout`（`notif_bg` 底图 + 内容行）→ 48dp 圆角方形封面、歌名/歌手、上一首/播放暂停/下一首三颗内联 `ImageButton`（全部 `PendingIntent` 走 `MediaController` 命令，系统不参与）。
- 底图自绘：`cardBackground(tint)` 画 360×72dp、半径 18dp 的封面主色圆角矩形，塞进 `notif_bg`（`fitXY` 铺满）；**不再依赖 `setColorized`**——锁屏上系统会把 colorized 通知重新套白底。
- 配色：`tintFrom()`（16×16 下采样取 S≤0.34、V=0.92 的封面主色）→ 字色/图标色按 `Color.luminance(tint) > 0.42f` 取深（`0xFF241E20`）或浅（白）；无封面时给同色系占位方块 + 对比色音符（`placeholderArt`）。
- 频道：`msop_playback_v2` / `IMPORTANCE_DEFAULT` / `setSound(null, null)` / `enableVibration(false)` / `lockscreenVisibility=PUBLIC`；**必须换 id**——旧频道重要性改不动。
- `setColor(tint)` 仍开着（部分系统会用色做强调），`setOngoing(showPlaying)`、`setSound(null)`、`setOnlyAlertOnce(true)`、`setVisibility(PUBLIC)`、`CATEGORY_TRANSPORT`。

### 三、设备实测（模拟器 127.0.0.1:5555 / 900×1600 / Android 15）

- 通知栏（`74-shade14.png`）：卡片为封面主色的圆角块，左侧 48dp 方形封面，右侧「勇气 / 棉花」+ 三颗深色传输键；系统在卡外仍包一层白底（头部 `msop` 落在白底上）——这是 `DecoratedCustomViewStyle` 的固有结构，**做不到目标图那种「整张粉卡含头部」**（要那样就得放弃 style，连头部一起自绘，代价是丢掉系统提供的应用名行）。
- 锁屏（`75-lockcard7.png`）：同一条卡在锁屏通知区正常渲染，字色/封面/三键可读，点三键能控制播放（媒体会话 `PAUSED(2)` / `PLAYING(3)` 状态随动）。
- 结论：**媒体通知的卡片外观由 SystemUI 决定，应用只能二选一**——要么接受系统大卡（能上锁屏媒体区、有进度条），要么用普通通知自绘卡片（外观可控但与系统媒体区脱钩、外层多一圈白边）。

### 四、三件遗留验完

1. **SAF 自定义下载目录**：设置 → 播放 → 下载路径 →「选择」→ DocumentsUI 里新建 `msop-music` →「使用此文件夹」→ 允许。`msop_download.xml` 写入 `tree_uri=content://com.android.externalstorage.documents/tree/primary%3Amsop-music` / `tree_name=primary:msop-music`；回曲库对云端曲「Love Paradise - 陈慧琳」点「下载歌曲」，`ls /storage/emulated/0/msop-music` 得到 `Love Paradise - 陈慧琳.flac 22,658,255 B`；重扫后曲库出现本机条目「Love Paradise · msop-music」。注：DocumentsUI 不允许选根目录/Download/DCIM 顶层（提示「为保护您的隐私，请选择其他文件夹」），所以自建子目录。
2. **WebDAV 并发输入框**：实际点进 `lan-dav` 编辑器填 `8` 保存，发现**值没落盘**——`WebDavConfigStore.upsert()` 只写了 name/url/user/password/root/allowHttp，**漏了 `putInt(key(id, FIELD_CONCURRENCY), config.concurrency)`**（OpenList 的 `ServerConfigStore` 有），所以输入框形同摆设。补上后重装复验：`shared_prefs/msop_webdav.xml` 出现 `p_p1_concurrency = 8`。
3. **`:app:assembleRelease`**：本轮重打通过，`app-release.apk` **3,578,834 B**（自签名 + R8）。

### 五、本轮构建与测试

- `test`（debug + release 双变体）：**38 个 XML 结果文件、0 失败 0 错误**（28 类 / 235 例基线不变）。
- `:app:assembleDebug` → **20,016,296 B**，装机冷启动正常；`:app:assembleRelease` → **3,578,834 B**。

### 六、本轮改动的文件

- `feature/player/src/main/java/com/msop/player/feature/player/notification/ShuYinNotificationProvider.kt`（整文件重写为自绘卡：`cardBackground`/`placeholderArt`/`tintFrom`/`darken`/`tintedIcon`/`rounded`/`artFor`，频道 `msop_playback_v2`）
- `feature/player/src/main/res/layout/notif_player_small.xml`（`notif_bg` 底图 + 内容行）、`notif_player_big.xml`（锁屏大卡，当前未启用）、`drawable/bg_notif_play.xml`、`drawable/ic_stat_msop.xml`、`drawable/ic_notif_{prev,next,play,pause,close}.xml`
- `core/datastore/src/main/java/com/msop/player/core/datastore/WebDavConfigStore.kt`（补 `putInt(FIELD_CONCURRENCY)`）

---

## 2026-11-16 第十二轮（r12-b）· 12 条需求：移除曲库可恢复 / 偏移浮层 / 菜单语义收敛

### 一、需求逐条落地

| # | 用户需求 | 实现 |
|---|---|---|
| ① | 自定义下载目录增加目录说明 | 设置页「下载路径」行按钮改「选择目录 / 更换目录」，下方常驻 `SettingsHint`：选走 SAF 授权、不选走应用私有 Music、下载完需重扫才会进曲库 |
| ② | 曲目菜单「查看文件信息」改「歌曲信息」；「移除曲库」二次确认 | `TrackRow` 文案改名；`confirmRemoveLibrary` + `AlertDialog`（文件不删 / 重扫不回来 / 想恢复去曲库右上角菜单） |
| ③ | 移除曲库的歌重扫不恢复 | **Room v5 + 墓碑表 `hidden_tracks`**，四个展示查询加 `NOT IN`；扫描路径不过滤（详见第二节） |
| ④ | 曲库右上角菜单加「已移除歌曲」，可恢复 | `LibrarySecondary.RemovedTracks` + 新文件 `ui/library/RemovedTracksScreen.kt`（行内「恢复」） |
| ⑤ | 收藏歌曲三点菜单删「从歌单移除」 | 收藏虚拟歌单的 `onRemove` / `onRemoveFromPlaylist` 都置 null |
| ⑥ | 播放页三点菜单删收藏，改「歌曲信息」「下载歌曲」 | `PlayerTopBar` 参数 `isFavorite/onToggleFavorite` → `track/onDownload`；下载只在云端曲出现 |
| ⑦ | 歌词偏移窗口盖住歌词，按 web 浮层修 | `AlertDialog` → 页面内非模态玻璃卡 `LyricsOffsetPanel`（照 `music-web-cf-main` 的 `buildCalPop`：标题 + 当前值 + ✕ + 说明 + 滑杆 + −0.5/−0.1/归零/+0.1/+0.5） |
| ⑧ | 歌词横屏迷你控制条透明度对齐迷你条 | `LyricsGlassControls` 白 0.34→0.12 渐变 → 与 `MiniPlayer` 同款 `FAFAFA 0.72` / `121212 0.74` |
| ⑨ | 歌单删「· N 分钟」 | `PlaylistScreen` 标题副标题与信息卡只留「N 首」 |
| ⑩ | 请求并发数提示词写清楚 | `ConfigField` 新增 `note` 参数，两个 ConfigScreen 常驻三行说明（留空=4 / 填 1 最稳 / 内网 8~16 / 429、5xx 降到 2~3 / 范围 1~16）——placeholder 填了数就消失，解释必须常驻 |
| ⑪ | 顶部 msop 下方文案改「本机 + OpenList + WebDAV」 | `SettingsScreen` 应用卡副标题 |

### 二、为什么用独立墓碑表，不给 `tracks` 加列

- 曲目入库是 `TrackDao.upsertAll`（`@Upsert` 整行覆盖）。若在 `TrackEntity` 加 `removed: Boolean`，重扫时新构造的实体取默认 `false`，标记必被冲掉——「加列」这条路在扫描走 upsert 的前提下结构上做不到。
- 改为独立表 `hidden_tracks(id TEXT PK, removedAt INTEGER)`，与 `tracks` 无外键：重扫只碰 `tracks`，墓碑纹丝不动。
- `TrackDao` 的展示查询（`observeAll` / `observeAllByAddedDesc` / `observeBySource` / `observeBySourceAddedDesc`）统一加 `AND id NOT IN (SELECT id FROM hidden_tracks)`；`allIds()` 与 `upsertAll` 不过滤，所以文件还在库里、扫描不会把它当幽灵。
- **本轮踩坑记录**：给 `TrackEntity.kt` 加字段时误用整文件重写（凭摘要重建，丢了 `source: SourceType`、四个索引与 `lastModified`，还把另外三个实体重复定义进来）。已从上一轮交付 zip 解出原文件逐字节还原（1,150 B），新内容另写 `HiddenTrackRow.kt`。**教训：改任何既有文件前必须先 Read 原文，不要凭摘要整文件重写。**

### 三、设备实测（模拟器 127.0.0.1:5555 / 900×1600 / Android 15 / 浅色主题 / 曲库 279 首）

1. 曲目菜单（`r12-03-menu.png`）：歌曲信息 / 加入歌单 / 移除曲库 / 下载歌曲。
2. 二次确认（`r12-04-confirm.png`）→ 移除后 279→278（`r12-05-after-remove.png`）。
3. 曲库右上角菜单（`r12-06-topmenu.png`）：已移除歌曲 / 重新扫描曲库。
4. 重扫完成后仍 278（`r12-07-rescan.png`）——墓碑生效。
5. 已移除歌曲页（`r12-08-removed-page.png`）：1 首 Butter-Fly + 「恢复」→ 点恢复回 279 且回列表（`r12-09-restored.png`）。
6. 收藏曲菜单（`r12-12-fav-menu.png`）：歌曲信息 / 取消收藏 / 移除曲库（无「从歌单移除」）。
7. 歌单详情（`r12-11-fav-detail.png`）：副标题与信息卡只剩「1 首」。
8. 播放页菜单：本地曲只有「歌曲信息」（`r12-14-player-menu.png`）；云端曲「歌曲信息 + 下载歌曲」（`r12-26-cloud-menu.png`）。
9. 歌词偏移浮层（`r12-17-offset-panel.png`）：贴歌词区底部、滑杆 + 五钮 + ✕、上方歌词仍可见。
10. 横屏（`r12-18-land.png`）：玻璃卡变浅白实垫与迷你条一致，歌词列左缘 ≈42.5%。
11. 设置页（`r12-19-settings.png` 下载说明；`r12-20-settings-top.png` 顶部「本机 + OpenList + WebDAV」）。
12. OpenList 编辑器（`r12-23-editor.png`）：并发数空框 + 常驻三行说明。
13. 数据库 v4→v5 迁移在设备上实际升级成功，曲库 279 首一条未丢。

### 四、本轮构建

- `test`：**31 个结果文件 / 254 例 / 0 失败 0 错误**。
- `:app:assembleDebug` → **20,287,537 B**（装机实测）。
- `test :app:assembleRelease` → **BUILD SUCCESSFUL in 8m 17s**，`app-release.apk` **3,595,218 B**（R8 + 自签名）。

### 五、本轮改动的文件

- 新增 `core/database/.../entity/HiddenTrackEntity.kt`、`entity/HiddenTrackRow.kt`、`dao/HiddenTrackDao.kt`、`app/.../ui/library/RemovedTracksScreen.kt`
- `core/database/.../MsopDatabase.kt`（v5 + `MIGRATION_4_5`）、`di/DatabaseModule.kt`（迁移注册 + `provideHiddenTrackDao`）、`dao/TrackDao.kt`（四处 `NOT IN`）、`dao/PlaylistDao.kt`（`removeTrackFromAllPlaylists`）
- `app/.../ui/playlist/PlaylistViewModel.kt`（墓碑 / `removedTracks` / `restoreToLibrary`）、`PlaylistScreen.kt`（收藏菜单、删分钟）
- `app/.../ui/components/TrackRow.kt`（改名 / 确认弹窗 / `TrackInfoDialog` 公开）、`ui/library/LibraryScreen.kt`（已移除入口）
- `app/.../ui/player/PlayerSheet.kt`（顶栏菜单）、`ui/MsopApp.kt`（`onDownload` 接线）、`ui/player/LyricsScreen.kt`（`LyricsOffsetPanel` / `LyricsGlassControls`）
- `app/.../ui/settings/SettingsScreen.kt`、`OpenListConfigScreen.kt`、`WebDavConfigScreen.kt`（`ConfigField.note`）

## 2026-11-16 第十三轮（r13）

> 用户第十三批反馈 7 条。设备：安卓模拟器 `127.0.0.1:5555`（PGEM10 / Android 15 / 900×1600，浅色主题）。

### 一、七条需求逐条落地

1. **曲库重复（截图 `Love Paradise` 出现三次）**。两个成因叠在一起，都在 `app/.../ui/library/TrackMerger.kt`：
   - **同源重复**：同一个文件被两个本机目录都扫到（`Music` 与上一轮 SAF 下载测试用的 `msop-music`），时长与体积完全相同。旧实现只做「云端命中本地即丢云端」，同源重复一条都不动。
   - **键对不上**：OpenList 把远端文件名整串当标题（`Love Paradise - 陈慧琳`）且 `artist = null`，本机条是 `title = Love Paradise / artist = 陈慧琳`，旧键 `normalize(title)|normalize(artist)` 永远不相等。
   改法：①每个条目产出**候选键集合**（原样 `标题|歌手` + 「歌名 - 歌手」拆开后的 `歌名|歌手`），两边有交集即同曲；②同源去重按 `键|时长|体积` 只留第一条（同名不同版本、不同码率不合并）。实测曲库 **279 → 264 首**，截图里那三条 Love Paradise 收敛成一条。
   追加（自查发现）：列表上只有一行、库里却有同歌第二份时，只墓碑一条会被另一条顶回来。`PlaylistViewModel.removeFromLibrary` 改走 `TrackMerger.twinsOf`（同键 + 时长差 ≤1s 或体积相同）一起写墓碑，`restoreToLibrary` 也一起复原。新增 `TrackDao.all()` 支撑这条查询。
2. **封面显示成白色**。根因是 `coverUri` 指向 `files/covers/<sha1>.jpg`，release 验证时 `uninstall` 把缓存目录清空了，而 `warmCovers()` 只吃 `coverUri IS NULL` 的条目，「有 URI 没文件」永远修不好；Coil 加载不存在的 `file://` 直接失败，只剩 `Surface3` 底色（浅色主题就是白块）。两层修：数据层 `LocalMetadataEnricher.repairBrokenCovers()`（`CoverCache.existing()` 判存活 → `TrackDao.clearCover` → 立刻 `warmCovers`），首屏预热协程改成先修再补（实测日志 `封面自愈 失效=17/17` → `封面预热 候选=19 成功=17`）；UI 层 `TrackRow.usableCoverUri()` / `CoverThumb` / `CoverSquare` 对 `file://` 先查文件存在，不存在就当无封面走程序化封面。本地 17 首封面全部恢复（`preview` 见 `r13-15-music.png`）。云端封面的空白属于网络：`/@rest/getCoverArt.view` 拿不到图就只能是空块。
3. **点击下载没反馈、没进度**。原实现是同步阻塞 `downloader.download(track)`（连接 20s / 读 60s）后才弹一句 toast。新增 `core/network/DownloadCenter.kt`（`@Singleton`；`State{RUNNING,DONE,FAILED}`；`bytes/total/savedPath/error` + `progress` 计算属性；`tasks: StateFlow`；同一首在途去重；完成停 4s、失败停 6s 自动移除），`RemoteFileDownloader.download` 增加 `onProgress(written, total)`（`Content-Length` ≤0 → total=null；64KB 缓冲、每 256KB 回调一次、结束再报一次）。UI 是新增 `app/.../ui/components/DownloadCard.kt` 的 `DownloadTaskCard`（状态位 + 标题歌手 + `正在下载 198.5MB / 212.0MB · 93%` + 3dp 进度条 + 无涟漪 ✕），挂在 `MsopApp` 底部浮层迷你条上方；迷你条玻璃投影外扩 20dp，中间留 12dp 否则投影盖住进度条。`LibraryViewModel` / `PlaylistViewModel` 的单首下载都改走中心（`downloadTracks` 批量仍是串行 + 汇总 toast）。实测点下去立刻出卡、进度一路到 93%→完成，落盘 `Android/data/com.msop.player/files/Music/そばにいるね - 青山黛玛.flac`（222,306,561 B）。
4. **删掉下载路径提示词**：`SettingsScreen.kt` 里「下载路径」行下面那段常驻 `SettingsHint` 整块移除，只留一行注释记用户指令。实测设置页该行下方已无说明段（`r13-23-settings2.png`）。
5. **「已移除歌曲」页点空白会误触播放**。二级页 overlay 是根 `Box` 的兄弟节点，而页面根节点原先没有任何 pointer 处理，空白处的点击直接穿透到下面仍在的曲库列表。修法：`ui/components/Surfaces.kt` 的 `EnteringPage` 根 `Box` 上挂无涟漪 `clickable`（`indication = null`）吃掉空白点击，并注明穿透原理。实测：移除一首 → 进已移除页（1 首）→ 点空白，迷你条与 `PlaybackState` 都不变（`r13-09/r13-10`）。
6. **文件夹增加云端**。`folderOf(track)` 原来只取 `localPath`，云端 `localPath = null`，且 `FolderList` 还有 `filter { it.localPath != null }` 把云端整批排除。现在文件夹键 = `来源标签:目录`（`local:` / `openlist:` / `webdav:` + 目录；云端目录从 `openlist:p2:/yd/musicweb/x.flac` 的第三段起取），`folderLabel()` 拆出「名称 / 目录 / 来源」三元组，列表排序按来源 + 目录，行副标题 `"$sourceLabel · $dir"`，云端行图标 `Palette.BlueLight`；详情页标题与副标题也走同一套。实测「文件夹」Tab 同时列出本机 `Music`(17) / `msop-music`(1) 与 OpenList 各专辑目录（`r13-14-foldercheck.png`）。
7. **通知栏 / 锁屏**（等用户选型，未改代码）。检索证据：
   - `wangchenyan/ponymusic`：`MusicService` 用 `DefaultMediaNotificationProvider.Builder(...).setSmallIcon()`，布局完全交给系统。
   - `cwuom/NeriPlayer`：`Notification.MediaStyle().setMediaSession(...).setShowActionsInCompactView(0,1,3)`，同样是系统大卡。
   - `cosmictaserdev-creator/Convx`（用户提供截图的来源，`--depth 1` 克隆 2523 文件）：`MusicService` 里只有 `DefaultMediaNotificationProvider(this, NOTIFICATION_ID, CHANNEL_ID, R.string.music_player).setSmallIcon(...)`，**没有任何自绘通知代码**。也就是说那两张截图是系统（或厂商 skin）渲染的媒体卡，不是应用画出来的。
   - 结论：GitHub 上没有「直接能移植的自定义粉卡」，本项目现有的 `ShuYinNotificationProvider`（普通通知 + `DecoratedCustomViewStyle` + `RemoteViews` 自绘）是唯一能改布局的形态。已把三个可选方案交用户拍板（见交付说明）。

### 二、验证

- `:app:assembleDebug` **BUILD SUCCESSFUL**，装机 `127.0.0.1:5555` 逐条实测：曲库去重 279→264、本地封面恢复、下载卡实时进度、已移除页空白点击不误触、设置页提示词已删、文件夹含云端目录。
- 截图目录 `%TEMP%\r13shots\`：`r13-01…r13-23*.png`。

### 三、本轮改动文件

- 重写/新增：`app/.../ui/library/TrackMerger.kt`（候选键集合 + 同源去重 + `twinsOf`）、`core/network/.../DownloadCenter.kt`（新增）、`app/.../ui/components/DownloadCard.kt`（新增）、`app/src/test/.../TrackMergerTest.kt`（新增 5 例）
- `core/network/.../RemoteFileDownloader.kt`（`onProgress` + `copyWithProgress` + `PROGRESS_STEP_BYTES`）
- `core/database/.../dao/TrackDao.kt`（`withCover` / `clearCover` / `all()`）、`feature/local/.../MetadataStore.kt`、`RoomMetadataStore.kt`、`LocalMetadataEnricher.kt`（`repairBrokenCovers`）
- `app/.../ui/library/LibraryViewModel.kt`（先修复后预热；注入 DownloadCenter；`downloads`/`dismissDownload`）、`LibraryScreen.kt`（`folderOf`/`remotePathOf`/`folderLabel`、`FolderList`/`FolderDetailScreen`）、`ui/components/TrackRow.kt`（`usableCoverUri`）、`ui/components/Surfaces.kt`（`EnteringPage` 空白拦截）、`ui/MsopApp.kt`（下载卡挂载 + 12dp 间隙）、`ui/playlist/PlaylistViewModel.kt`（下载走中心、移除/恢复连坐同歌副本）、`ui/library/RemovedTracksScreen.kt`（恢复传 `track`）、`ui/settings/SettingsScreen.kt`（删提示词）
### 四、第七条的结论（用户拍板后落地）

检索结论：`ponymusic` / `NeriPlayer` / `Convx` 三个仓库的通知全部是 `DefaultMediaNotificationProvider` 或 `Notification.MediaStyle` 交系统渲染，**没有任何自绘通知布局**；`Convx` 就是用户那两张截图的来源，它自己也没自绘。所以「找一个能直接移植过来、长得像截图的 UI」这条路不存在——截图本来就是系统（或皮肤）画的媒体卡。

用户在四个方案里选了 **B：换回系统默认媒体大卡**。落地改动：

- `feature/player/.../PlaybackService.kt`：不再 `setMediaNotificationProvider(ShuYinNotificationProvider(...))`，改成 `DefaultMediaNotificationProvider.Builder(this).setChannelId("msop_playback_v3").setChannelName(R.string.notif_channel_playing).setNotificationId(1001).build()`；小图标用 `setSmallIcon(R.drawable.ic_stat_msop)`——**1.11 的 Builder 没有 `setSmallIcon`**，它是 provider 的实例方法（`javap` 核过 API 才没白改一轮）。
- 新增 `feature/player/src/main/res/values/strings.xml`（`notif_channel_playing = 正在播放`，给渠道命名，默认是英文 `Now playing`）。
- 删除 `feature/player/.../notification/ShuYinNotificationProvider.kt` 与 `feature/player/src/main/res/layout/notif_player_small.xml`（自绘卡退场；上一轮踩明的死路结论保留在 `AGENTS.md`）。

实测（`127.0.0.1:5555`，播放 Butter-Fly）：通知栏展开即系统大卡——大封面（封面即专辑图，带歌名/歌手叠加）、播放暂停、上一首/下一首、可拖的播放进度波浪条；锁屏同样一张大封面卡，三键可点。截图 `%TEMP%\r13shots\r13-27-notif.png`、`r13-28-lock.png`。`test` 仍 33 个结果文件 / 264 例 / 0 失败，`:app:assembleDebug` **21,143,938 B**。

## 2026-11-16 第十四轮（r14）

> 设备仍是安卓模拟器（`127.0.0.1:5555`，PGEM10 / Android 15 / 900×1600）。本轮 5 条用户反馈，全部改完、逐条实测。

### 一、五条需求逐条落地

1. **搜索 / 设置页返回后导航栏按钮还在原位**。复现：点「设置」→ 返回 → 内容回到音乐库，但玻璃滑块仍停在「设置」。根因在 `ui/components/ShuYinNavBar.kt` 的 `LiquidGlassNavBar`：`LaunchedEffect(Unit)` 里的 `snapshotFlow { selectedTabIndex() }` 捕获的是**首次组合那个 lambda 实例**，闭包里的 `currentDestination` 永远是旧值，`popBackStack` 改了路由也读不到；同时另一段 `LaunchedEffect(dampedDragAnimation)` 把「外部导航」当用户点击又 `onTabSelected` 一次，与 `popBackStack` 互相打架。改法：`rememberUpdatedState(selectedTabIndex)` / `rememberUpdatedState(onTabSelected)` 取最新 lambda；外部导航效应只做 `currentIndex = it` + `animateToValue`，**不再回调 onTabSelected**；用户点击入口显式 `currentIndex = index; currentOnTabSelected(index)`；`onDragStopped` 按「是否真拖动 / target 是否等于当前项」分流（相等走 reselected，不等才回调整）。
2. **三点菜单「下载歌曲」移到第一**。`ui/components/TrackRow.kt` 里原在末尾的下载块（`track.source != LOCAL` && `menu.onDownload != null`）整体上移到 `DropdownMenu {` 后第一位；云端曲顺序变成 下载歌曲 / 歌曲信息 / 加入歌单 / 移除曲库。
3. **文件夹里云端只按设置界面填写的路径聚合**。`LibraryViewModel` 构造注入 `ServerConfigStore` / `WebDavConfigStore`（SharedPreferences 实现、**没有可观察 Flow**，只能主动拉一次），新增 `cloudRoots: StateFlow<Map<String,String>>` + `refreshCloudRoots()`（值变了才写），键 `tag:profileId`、值 `rootPath`；`LibraryScreen` 顶部 `collectAsStateWithLifecycle` + `LaunchedEffect(Unit)` 刷新。`folderOf(track, cloudRoots)` 先 `remoteProfileId(uri)`（`scheme:profileId:/path` 第二段）查表，命中用 `normalizeCloudRoot(rootPath)`（空串或 `/` → `/`；否则补前导 `/`、去尾 `/`），查不到才回退旧的按 uri 子目录切分。`FolderList` 增参、`remember(tracks, cloudRoots)`。
4. **歌单「移动到其它歌单」删掉**（与「加歌单」重复）：`TrackMenuActions` 去掉 `currentPlaylistId` / `onMoveToPlaylist` 两个字段，菜单块整块删除，`TrackPickerState` 保留但弹窗标题固定「加入歌单」；`PlaylistScreen` 对应构造点同步删参数。批量操作条的「移动」保留（多选语义）。
5. **收藏歌曲三点菜单增加「加入歌单」**：`PlaylistScreen` 里收藏类目 `onAddToPlaylist = { target -> playlistVm.addTracksToPlaylist(target, listOf(track.id)) }`；普通歌单仍按 r11 指令不给该入口。

### 二、验证

- `:app:assembleDebug` **BUILD SUCCESSFUL in 3m 15s**，APK 21,143,938 B，装机 `127.0.0.1:5555` 逐条实测。
- 设置页返回 → 滑块回到「音乐库」（`r14-02-back-fixed.png`）；云端曲菜单首项「下载歌曲」（`r14-04-menu.png`）；文件夹 Tab 只剩 `OpenList /yd/music`(23)、`OpenList /yd/musicweb`(42)、`WebDAV /cd18/Music`(181) 三条云端目录 + 本机 Music(17)/msop-music(1)（`r14-03-folders.png`）；普通歌单曲目菜单无「移动」（`r14-12-pl-menu.png`）；收藏曲菜单含「加入歌单」（`r14-06-fav-menu.png`），选 BatchTest 后**连同 `msop.db-wal`** 拉库确认写入，测试条目已从 BatchTest 移除还原。
- 截图目录 `%TEMP%\r14shots\`（`r14-01…r14-16*.png`）。

### 三、本轮改动文件

- `app/src/main/java/com/msop/player/ui/components/ShuYinNavBar.kt`（`rememberUpdatedState` + 外部导航只动滑块 + 点击/拖动显式回调）
- `app/src/main/java/com/msop/player/ui/components/TrackRow.kt`（下载歌曲上移第一、删「移动到其它歌单」、`TrackMenuActions` 精简、弹窗标题固定）
- `app/src/main/java/com/msop/player/ui/playlist/PlaylistScreen.kt`（收藏类目 `onAddToPlaylist`、删移动相关参数）
- `app/src/main/java/com/msop/player/ui/library/LibraryViewModel.kt`（注入两个配置 Store、`cloudRoots` + `refreshCloudRoots` + `cloudRootsSnapshot`）
- `app/src/main/java/com/msop/player/ui/library/LibraryScreen.kt`（`cloudRoots` collect / 刷新、`folderOf(track, cloudRoots)`、`remoteProfileId` / `normalizeCloudRoot`、`FolderList` 增参）

### 四、补验：下载卡 DONE / FAILED 两态（本轮追加）

> r13 交付时这两态只有代码路径 + 落盘文件佐证，没有实机截图；本轮补上。

- **RUNNING**：云端曲「Butter-Fly」点「下载歌曲」立刻出卡，`正在下载 4.3MB / 33.3MB · 12%`（有 `Content-Length` 时走百分比 + 实心进度条；`そばにいるね - 青山黛玛` 首次回调 `正在下载 0B`、长度未到时不显示百分比，走不确定进度条）。截图 `%TEMP%\r13shots\r15-bf-04.png`。
- **DONE**：绿色 ✓ + 「已保存到 /storage/emulated/0/Android/data/com.msop.player/file…」，4 秒后自动消失。落盘核对 `Butter-Fly - 和田光司_2.flac` 34,933,398 B、`Its My Life Bon Jovi.flac` 29,648,735 B、`そばにいるね - 青山黛玛_4.flac` 222,306,561 B。截图 `r15-bf-08.png`、`r15-ml-12.png`。
- **FAILED**：把设备系统代理临时指到死端口（`settings put global http_proxy 127.0.0.1:1`）再点「下载歌曲」，卡片变红色 ✕ + 红字 `无法连接服务器：Failed to connect to /127.0.0.1:1`，无进度条，6 秒后自动消失；随后 `settings put global http_proxy :0` 还原（已复核 `settings get global http_proxy` = `:0`）。截图 `r15-fail-05.png`。
- 补验用的三个下载文件已从 `files/Music` 删除，设备恢复原状；`airplane_mode_on` 复核为 0。
- **踩坑**：想靠飞行模式制造失败会顺手把 adbd 打离线（MuMu 的 `cmd connectivity airplane-mode enable` 之后 `adb devices` 全是 offline，只能靠用户手动关掉飞行模式恢复）。制造下载失败请用上面那个死代理的办法。恢复后 MuMu 的 adb 端口变成 `emulator-5554` / `127.0.0.1:16384` / `127.0.0.1:7555`，原 `127.0.0.1:5555` 未再监听。

## 2026-11-16 第十五轮（r16/r17/r18）：A 组五项补验 + 通知栏耳机图标

> 用户指令：「做 A 组」+「通知栏 锁屏播放器 音符图标换成耳机 和软件图标耳机一样」+「C 1 不用改，保持当前」。
> 本轮没有新功能，唯一代码改动是 `feature/player/src/main/res/drawable/ic_stat_msop.xml`。五项补验里 3 项翻 `[x]`、1 项翻 `[~]`、1 项查明无法完成。

### 一、P3-04 SimpleCache 缓存命中（`[?]` → `[x]`）

设备读法：`adb -s emulator-5554 shell "run-as com.msop.player du -sk /data/data/com.msop.player/cache/media_cache"`。

1. **清理后归零**：设置页「远程播放缓存」显示 `106.8 MB · 2 首· 上限 2.0 GB` → 点行 → 「清理远程播放缓存?」确认弹窗写明「将删除 5 首（106.8 MB）」→ 清理后变 `0 B · 0 首`。截图 `r16-05-settings2.png` / `r16-06-cache-dlg.png` / `r16-07-cache-cleared.png`。
2. **同曲重播命中**：清空后播 `Rage Your Dream`（`openlist:p2:/yd/musicweb/Rage Your Dream - m.o.v.e.flac`），`du` 从 28 KB 涨到 `8240 KB`，日志一条 `远程缓存未命中，解析播放地址 uri=…`；换成小体积 `指尖缠绕 - ZIV`（2.38 MB / 3:14）播到结束，缓存 `29272 KB`；**再点同一行重播，12 秒 logcat 只有 `远程缓存策略 wifiOnly=true metered=false 写入=true`，零「未命中」**——头部区域走缓存不再回源。截图 `r16-17-ziv2.png` / `r16-18-replay.png`。
3. **计费网络占用不涨**：`cmd wifi add-network Linksys_6205d666c0b5 open -m` 把当前 Wi-Fi 标成 metered（`cmd netpolicy list wifi-networks` → `;true`，`dumpsys connectivity` 里 WIFI 的 `Capabilities` 不含 `NOT_METERED`）。清空缓存后播 30 MB 未缓存的 `Rage Your Dream`，日志 `远程缓存策略 wifiOnly=true metered=true 写入=false`，`du` 前后都是 `2364 KB`；已缓存的 ZIV 在 metered 下继续正常播放、占用不涨（`r18-07-metered.png` / `r18-08-cachedplay.png`）。验完 `cmd wifi add-network … open`（不带 `-m`）还原成 `;none` + `NOT_METERED`。
4. **伪故障排除**：WebDAV 曲 `/Nivis/慢慢（抖音热版） Nivis..mp3` 每 2–3 秒「未命中」最后 `Source error`——python `urllib` 对该 `/dav` 文件 GET + `Range: bytes=0-1023` 返回 404，是服务端 GET 不通（与既记「多数文件 GET 403/404、HEAD 全 404、只有 PROPFIND 正常」同源），不是缓存 bug。
5. 注意：整首只缓存约 1.3 MB 属 `SimpleCache` 环形分配的正常行为；但因此「二次播放整段零 miss」只在窗口内成立，跨界仍会回源——验收只要求「命中缓存、不再回源」，这点满足。

### 二、P7-04 备份与恢复（`[?]` → `[x]`）

- **导出**：设置页滚两次到「备份与恢复」组 → 「导出备份」→ 系统 SAF create-document 弹窗（默认落 `msop-music`、文件名预填 `msop-backup-20261116-2133.zip`）→ 「保存」→ App 弹「已导出 2 个歌单、1 首曲目；含全部设置与服务器凭据」。文件在 `/storage/emulated/0/msop-music/msop-backup-20261116-2133.zip`（888 B）。截图 `r17-01-settings.png` / `r17-02-export1.png` / `r17-03-exported.png`。
- **包内容（拉回本机解 zip 逐字核对）**：`manifest.json` = `format msop-backup-v1 / version 1 / createdAt 1794836021707 / playlistCount 2 / trackCount 1`；`playlists.json` = `BatchTest`(0 首) + `MoveTest`(1 首 `webdav:p1:df84986344bb9a7dde93655ac3b1328ed89b5504`)；`settings.json` = `wifiOnlyCache`、equalizer 平直、`server{baseUrl/username/allowPlainHttp/password}`、`webDav{baseUrl/rootPath/username/allowPlainHttp/password}`。
- **恢复闭环**：歌单页 `BatchTest` ⋮ → 「删除」→ 确认框「删除歌单?「BatchTest」会从歌单列表中移除，歌曲文件本身不受影响」→ 删后只剩 收藏歌曲 + MoveTest（`r17-04…r17-07`）→ 设置页「导入备份」→ SAF 选择器里能看到 zip → 选中 → 弹「已恢复 2 个歌单、1 首曲目；OpenList 配置已恢复，可直接使用；WebDAV 配置已恢复，可直接使用」（`r17-08…r17-09`）→ 歌单页 `BatchTest` 复活、三项齐全（`r17-10-restored.png`）。
- **坏 zip 用例**：本机造一个只含 `notes.txt` 的 zip，`adb push` 到 `/storage/emulated/0/msop-music/bad.zip`，导入 → 「导入失败：不是 msop 备份：缺少 manifest.json」，歌单与配置均未变化（`r17-11-badpick.png` / `r17-12-badimport.png`）。
- **口径不一致（按实现改写验收，不当缺陷）**：`TASKS.md` P7-04 原写「密码不导出」，实现是 `settings.json` 里明文 `password`，且设置页副标题与导出弹窗都明写「含服务器密码 / 含全部设置与服务器凭据」——设计如此。
- 恢复语义（实测）= 同名歌单合并 + 缺失歌单补回 + 服务器配置直接生效；曲库中不存在的曲目自动跳过。

### 三、P7-03 断网保护（`[?]` → `[~]`，三项实测、缓冲提示仍缺）

**手法**（关键，见 `AGENTS.md` D29）：不要从 PC 侧 `svc wifi disable`（会立刻掉 adb，任务卡在 waiting-for-device，本轮已两次踩到、靠用户手动恢复）。改成设备端脚本：

```sh
# /data/local/tmp/p7a.sh
logcat -c
svc wifi disable
sleep 5
screencap -p /sdcard/r18-off-a.png
sleep 5
logcat -d -s Msop:V > /sdcard/r18-off-a.log
screencap -p /sdcard/r18-off-b.png
svc wifi enable
```

`adb -s emulator-5554 shell "nohup sh /data/local/tmp/p7a.sh >/dev/null 2>&1 &"` —— 断网窗口里脚本自己截屏、自己 dump 日志，Wi-Fi 恢复后 adb 自动回连，再 `adb pull` 取证。

1. **云端曲断网**：`指尖缠绕 - ZIV`（标签「云端」）播到 1:10 跑脚本 → 截图显示顶栏橙点「网络已断开」+ 浮层胶囊「网络已断开，播放已暂停」、播放键变 ▶、进度停在 1:10；同期日志 `网络断开，远程曲目已自动暂停`（`r18-12-offline-a.png` / `r18-off-a.log`）。
2. **恢复网络不续播**：Wi-Fi 回来后顶栏变「已暂停」、提示消失、仍停在 1:10，日志 `网络已恢复，等待用户手动继续播放`（`r18-14-recovered.png`）。
3. **本地曲不受影响**：本地 FLAC `Love Paradise`（标签「本地」）播到 0:07 跑同一脚本 → 断网窗口截图仍在「正在播放」、进度走到 0:32；日志文件为空（无网络相关行）（`r18-17-offline-local.png` / `r18-off-local.log`）。
4. **仍缺**：「缓冲中…」（1.2s）与「网络较慢，仍在缓冲…」（15s）两条需要低速网络。本机没有「不掉 adb 又能限速」的手段——系统代理只能造连接失败（→ERROR，不是 BUFFERING），关网络又掉 adb。代码路径在 `PlayerSheet.PlaybackNotice`，未取得实测截图。

### 四、P4-02 搜索合并（`[?]` → `[x]`）

搜 `Music` → `搜索 结果 36 首`，同屏出现本机行「世界が終るまでは…／WANDS · Music（专辑）」与云端行（`musicweb` 歌手）；搜 `ZIV` → 1 首「指尖缠绕 - ZIV / musicweb」、搜 `Nivis` → 1 首「慢慢（抖音热版）Nivis.」——单一查询同时覆盖本地与云端。截图 `r18-18-merge.png` / `r16-11-search2.png` / `r16-15-ziv.png`。
实现：`LibraryViewModel.searchResults` = `combine(_searchQuery, tracks)` + `debounce(SEARCH_DEBOUNCE_MS)` + `SEARCH_LIMIT`，`tracks` 本身含 OPENLIST / WEBDAV 行。

### 五、P5-01 远程歌词（仍 `[~]`，查明「没实现」）

- `feature/lyrics/.../LyricsRepository.kt`：KDoc 明写「远程曲目内嵌歌词要先把文件拉到本地，等 P3 远程音源落地后再接」；`readEmbedded()` 里 `if (track.source != SourceType.LOCAL) return null`；旁挂 `.lrc` 只查 SAF 文档兄弟 / 授权树 / 裸路径，云端 uri 走不到。
- 素材也没有：OpenList `/yd/musicweb` 递归 60 条 **0 个 `.lrc`/`.ttml`**；WebDAV `/cd18/Music` PROPFIND depth 1 共 33 href **0 个歌词文件**。
- 结论：要落地 = 新写「远程旁挂 lrc 拉取 + 本地缓存」（可选再做「远程内嵌歌词」需先 Range 拉文件）+ 往服务器临时放一个测试 `.lrc`（改用户服务器，须先拍板）。

### 六、通知栏 / 锁屏小图标换耳机

- 只改 `feature/player/src/main/res/drawable/ic_stat_msop.xml`：整文件重写成纯白描边矢量，形态照桌面图标 `app/src/main/res/drawable-nodpi/msop_icon.png`——头梁弧 `M4.2,15.2 A7.8,7.8 0 0 1 19.8,15.2`（`strokeWidth=2.2`、round cap）、左右耳罩 `4.2,15→4.2,18.6` 与 `19.8,15→19.8,18.6`（3.4）、三根电平柱 x=9.6(16.6→19.2)/12(14.8→19.2)/14.4(16.2→19.2)（1.6），全部 `fillColor="#00000000"`。
- `PlaybackService.kt` 第 70 行 `notificationProvider.setSmallIcon(R.drawable.ic_stat_msop)` 保持原样（Media3 1.11 的 Builder 没有 `setSmallIcon`，小图标是实例方法），**Kotlin 未改**。
- 装机后实测：展开通知栏拿到系统媒体大卡左上角耳机图标，裁剪 6 倍放大确认「头梁 + 两耳罩 + 三柱」剪影（`r16-02-notif.png` / `r16-02-icon-zoom.png`）；锁屏同一张卡同图标（`r16-03-lock.png`）。
- 说明：设备当前是浅色主题，通知卡在深色下拉面板呈橄榄/棕系统配色、无大封面，属系统媒体卡渲染，非本次改动范围。

### 七、环境事实（本轮新增）

- `cmd netpolicy set metered-network <id> true/false` 对开放网络**不生效**（返回 True 但 `list wifi-networks` 仍 `none`）；要造计费网络只能 `cmd wifi add-network <SSID> open -m`（会短暂掉 adb 约 30s 自动回连），还原用 `cmd wifi add-network <SSID> open`。
- 关 Wi-Fi / 飞行模式 / 关移动数据都会掉 MuMu 的 adb（累计 4 次，其中 2 次靠用户手动恢复）；断网类验证只能用设备端 `nohup` 脚本自取证。
- `cmd wifi add-network` 之后 `logcat -d -s Msop:V` 曾出现空结果（logd 缓冲被刷），要看历史行就在脚本里 `logcat -d > /sdcard/x.log` 再拉。
- 备份/恢复相关坐标：设置页滚两次后 导出备份 y≈612、导入备份 y≈748；SAF 选择器里 zip 行 y≈658（`bad.zip` y≈528）；歌单行 ⋮ x≈700（MoveTest y≈600 / BatchTest y≈770），菜单「删除」y≈954，确认框「删除」(663,897)；导出/导入成功弹窗「知道了」(640,895) / (640,916)；缓存清理确认「清理」(657,912)。
