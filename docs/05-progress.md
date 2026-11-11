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
