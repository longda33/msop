# INDEX.md — 目录导航（接手先读这一页）

> **目的**：任何模型接手本项目，读这一页就知道「读什么、跳过什么、改哪里、怎么跑」。不需要通读 `AGENTS.md`（约 110 KB）、`05-progress.md`（约 232 KB / 2200 行）。
> 本页只做导航与状态快照，**唯一真相仍是 `TASKS.md`**；本页与 `TASKS.md` 冲突时以 `TASKS.md` 为准。

## 0. 三句话

1. **是什么**：安卓音乐播放器，Kotlin + Compose + Media3，包名 `com.msop.player`。只读本机音频 + 对接自己的 OpenList / WebDAV / Subsonic 曲库，自用不上架。
2. **做到哪了**：核心功能全部打通；P0–P4 全部完成，P6 只剩 P6-01 走查，50 项任务已完成 44 项，剩 6 项未完结（P1-04、P5-01、P5-04、P6-01、P7-03、P7-12，见 §4）。设备是**安卓模拟器**（`adb -s emulator-5554`，Android 15 / 900×1600），不是真机。
3. **下一步能做什么**：P5-04 大库性能、P6-01 走查勾单可立即做；P1-04 权限拒绝流程、P5-01 云端歌词素材、P7-03 缓冲提示需要外部条件；P7-12 libFLAC 被 NDK 阻塞（详见 §4 备注）。

## 1. 推荐读取顺序（省时间）

| # | 读什么 | 读多少 | 为什么 |
|---|---|---|---|
| 1 | 本页 `INDEX.md` | 全页（约 2 分钟） | 建地图，避免瞎翻 |
| 2 | `AGENTS.md` 的 **§0 第 1–7 条** | 只读这七条（约 60 行） | 硬约束与「最容易踩的坑」清单，写代码前必读 |
| 3 | `TASKS.md` 的 **总览表 + 第一个未完成行** | 约 30 行 | 分清已完成/未完成，别做重复工 |
| 4 | 按任务领域选读：远程/缓存/Subsonic → `03-openlist-api.md` §6–§8；UI 视觉 → `07-ui-design.md`；模块边界 → `02-architecture.md`；找参考实现 → `04-reference-index.md` | 按需 `grep` 章节 | 全是按主题分好的，不要整篇读 |
| 5 | 只有要查历史决策时才碰 `05-progress.md` | **从文件尾往前搜日期**，别从头读 | 2200 行时间序日志，旧的已过时 |

**明确不要通读的**：`AGENTS.md` 第 2 节「最后更新」单元格（几百 KB 的历史流水）、`05-progress.md` 全文、`08-shuyin-integration.md`（ShuYin 融合计划，577 行，只查具体条目）。这三处只在需要确证某个历史细节时用 `Grep` 定位。

## 2. 文件地图

| 路径 | 是什么 | 什么时候读 |
|---|---|---|
| `README.md` | 对外简介：功能表、模块清单、构建命令 | 第一次了解项目 |
| `AGENTS.md` | **交接契约**：§0 五分钟接手（含踩坑清单）、D1–D29 决策表、禁止事项 | 动代码前读 §0 + 决策表相关行 |
| `TASKS.md` | **唯一进度真相**：P0–P7 任务表、验收标准、变更记录 | 开工找任务、收工翻勾 |
| `USAGE.md` | 使用说明：权限、目录、音源配置、播放、构建、重装换机 | 部署/装机/换机器 |
| `BUILD-NOTES.md` | 构建环境备忘（JDK、SDK、Gradle 版本、R8） | 构建报错时 |
| `02-architecture.md` | 模块边界、数据流、双通道扫描、公网安全 | 改数据层/加模块 |
| `03-openlist-api.md` | **音源接口实测真相**：OpenList 原生 REST、OpenSubsonic、WebDAV、缓存链 | 碰任何远程音源前必读 §6–§8 |
| `04-reference-index.md` | 参考实现索引：去哪个 GitHub 仓库看哪个文件 | 不知道怎么写时先查这里 |
| `05-progress.md` | 逐轮进度日志与踩坑（**最新一轮在文件末尾**） | 查「某轮为什么这么改」 |
| `07-ui-design.md` | 当前 UI 设计说明：Aurora Glass 令牌、组件表、已知缺口 | 改视觉/加组件 |
| `08-shuyin-integration.md` | ShuYin 功能融合计划与验收记录（P7 来源） | 查 P7 某项的原始意图 |
| `../preview/baseline/` | 当前视觉基线 3 张截图（库页 / 播放中 / 播放页），**仓库里保留** | 改视觉后对照更新 |
| `../scripts/check.sh` | 工程完整性自检（不需要 JDK） | 提交前跑一遍 |
| `../archive/` | **非项目文件归档区**（见 §6），不入库、不参与构建 | 找历史设计稿/验收截图/旧交付包时 |

## 3. 代码入口（想改什么 → 改哪）

| 想改什么 | 文件 |
|---|---|
| 应用装配、导航、dock、迷你条挂载 | `app/src/main/java/com/msop/player/ui/MsopApp.kt` |
| 曲库列表 / 歌手 / 专辑 / 文件夹 Tab | `ui/library/LibraryScreen.kt`、`ui/library/LibraryViewModel.kt`、`ui/library/TrackMerger.kt` |
| 单行曲目与三点菜单 | `ui/components/TrackRow.kt` |
| 播放页、进度条、手势、扇形菜单 | `ui/player/PlayerSheet.kt` |
| 歌词页（竖屏/横屏/逐字/偏移浮层） | `ui/player/LyricsScreen.kt` |
| 歌单 Tab 与歌单详情 | `ui/playlist/PlaylistScreen.kt` |
| 搜索页 | `ui/search/SearchScreen.kt` |
| 设置页（缓存/备份/下载路径/音源入口） | `ui/settings/SettingsScreen.kt`、`ui/settings/SettingsViewModel.kt` |
| 迷你播放条 / 液态玻璃导航 | `ui/components/MiniPlayer.kt`、`ui/components/ShuYinNavBar.kt` |
| 后台播放、通知栏、缓存数据源 | `feature/player/src/main/java/com/msop/player/feature/player/PlaybackService.kt` |
| 远程同步、时长探测、封面 | `feature/remote/`、`core/network/` |
| 数据库与迁移 | `core/database/src/main/java/com/msop/player/core/database/MsopDatabase.kt` |
| 歌词解析（本机内嵌/旁挂 + 云端内嵌；云端旁挂 `.lrc` 未实现） | `feature/lyrics/`、`core/network/RemoteLyricsProbe.kt` |
| 代码审查发现与已修问题 | `CODE-REVIEW-2026-11-16.md`（基线 21 项）、`CODE-FIXES-2026-11-17.md`（本轮修复 + 未验证项） |

> 规则：`ui/` 全在 `app` 模块，`feature/*/ui/` 保持空着（原因见 `AGENTS.md` §5）。**不要**把 UI 拆进 feature 模块。

## 4. 状态快照（2026-11-17 更新）

`TASKS.md` 表格标记：`[x]` 44 行 · 未完结 6 行（`[~]` 3 · `[ ]` 2 · `[!]` 1）。

| 任务 | 标记 | 卡在哪 |
|---|---|---|
| P1-04 权限拒绝流程 | `[~]` | 模拟器自动授予权限，拒绝/重试弹窗拍不到 |
| P5-01 LRC 远程歌词 | `[~]` | 云端**内嵌**歌词 2026-11-17 傍晚已实现（`StreamUrlResolver` + `RemoteLyricsProbe`，ID3 USLT / FLAC LYRICS / MP4 ©lyr），但抽样 25 首**0 个歌词标签**、无素材可验；云端**旁挂 `.lrc`** 仍未实现，两台服务器也无 `.lrc` |
| P5-04 大库性能（5 万首） | `[ ]` | 无大库样本，可造数据后立刻做 |
| P6-01 全流程走查 | `[ ]` | 清单未逐条勾完，可立即做 |
| P7-03 断网保护 | `[~]` | 断网暂停/恢复不续播/本地曲不受影响已验；迷你条已加「正在连接云端，缓冲中…」，但播放页「缓冲中…」/「网络较慢，仍在缓冲…」仍缺低速网络实测 |
| P7-12 libFLAC Native | `[!]` | 本机无 NDK/CMake，且 Media3 未发布 `media3-decoder-flac` AAR |

2026-11-17 补修：OpenList `/dav` 当 WebDAV 音源时「能同步、不能播放」——播放改走原生 REST 换直链，模拟器已完整播完一首，见 `AGENTS.md` §0.6、`03-openlist-api.md` §10、`05-progress.md` 末节。
2026-11-17 下午补：云端内嵌封面（`RemoteCoverProbe` + `RemoteCoverExtractor`，并发 4）、玻璃垫色提到 0.90/0.92、「从歌单移除」改「移除歌单」，见 `AGENTS.md` §0.7、`03-openlist-api.md` §11。
**2026-11-17 晚回滚**：上面的 0.90/0.92 把液态玻璃糊成实板（用户报「效果丢失」），三处垫色回到上游 ShuYin 的 0.55 / 0.60；随后按 `ShuYin.PNG` / `msop.PNG` 对比又发现 `AmbientBackground()` 画在 `.layerBackdrop()` 外面（采到的是透明图，模糊等于没做），已把它挪进录制子树。高光曾加到 2dp / `White 0.62`，用户实物后否掉（描边感），已退回 1dp 原参数。见 `AGENTS.md` §0.11。
2026-11-17 傍晚再补：歌单播放/随机按钮状态、收藏条固定置顶、云端内嵌歌词（ID3/FLAC/MP4）、云端缓冲迷你条提示、白封面魔数校验与自愈，见 `AGENTS.md` §0.8 与 `03-openlist-api.md` §12。
同日文档状态同步：总览表计数与 P5-01 / P7-03 备注按上表对齐（只改文档、勾选不变）。
**2026-11-17 深夜五项缺陷修复**：① 筛选在歌手/专辑/文件夹上本来生效，加 `SourceFilterBanner` 让状态可见（歌单 Tab 只提示不过滤）；② 文件夹收敛——云端 rootPath 留空不再回退子目录、本机按「设置里添加的音乐目录」聚合（实测 4 条）；③ 队列/歌单拖动改「按下即拖」+ 主题感知手柄色（浅色可见、adb 实测能换位落库）；④ 播放页定时改 `mm:ss` 每秒倒计时；⑤ 设置「下载路径」显示真实路径。见 `AGENTS.md` §0.12。
**2026-11-17 深夜五项收尾修正**（对上一批的验收反馈）：① 状态条瘦成只剩「本机 / 云端」二字（删首数与「点一下恢复全部来源」）；② 文件夹本机项副标题显示真实路径（`本机 · /storage/emulated/0`），OpenList / WebDAV 标题改用设置里填的名称（`lan` / `lan-dav` / 主机串），`cloudNames` 键改为 `tag + normalizeCloudRootPath()` 才命中；③ 拖动回滚成「长按抓取才能拖」，另一根手指可照常滑列表（颜色修复保留退回原尺寸不上调）；④ 歌词页信息行最右加悬浮歌词开关（`Icons.Filled.Lyrics` 裸图标，位置同 ShuYin，**横屏不加**），复用 `DesktopLyricsService`，无权限时跳 `ACTION_MANAGE_OVERLAY_PERMISSION`；⑤ 设置页 OpenList / WebDAV 副标题删「· 点此管理」。见 `AGENTS.md` §0.13。
**2026-11-17 深夜三项再补**：① 悬浮歌词钮的图标换成 `Icons.Filled.Lyrics` 原 pathData（手抄进 `PlayerIcons.Lyrics`，EvenOdd 挖出三行歌词，不引 8MB 的 extended 包）；② 播放页三点菜单加「加入歌单」（歌曲信息 → 加入歌单 → 下载歌曲，复用 `PlaylistPickerDialog`，已加过显示「已加歌单」）。见 `AGENTS.md` §0.14。
**2026-11-17 夜四项再改**：① 歌词图标拆两套——播放页 dock 与设置页「播放 → 歌词」改回**原始四条横线**，只有歌词页悬浮钮**和**设置页「桌面悬浮歌词」行保留 Material 气泡+音符（用户中途纠正过一次「桌面悬浮歌词不要改」）；② 顶栏「已暂停/正在播放」文字色 `TextSecondary` → `TextPrimary`；③ 删掉播放页顶部胶囊状态弹窗（`PlaybackNotice` 函数与调用全删，缓冲/断网只剩顶栏文字）；④ OpenList / WebDAV「路径」支持多行多目录（`splitRootPaths`，WebDAV 多目录时库内路径改成相对服务器根的绝对路径 + `effectiveRootPath`，文件夹 Tab 按最长命中目录归桶、名称逐 root 一条）。见 `AGENTS.md` §0.15。
**2026-11-17 深夜返回键/关闭收尾**（用户报「无法使用返回关闭 app」，逐项确认后改）：① 返回键逐级返回——非「歌曲」类目先退回歌曲、二级页退二级页；歌曲类目按返回弹 `AlertDialog`，文案只有 `是否关闭软件` + `取消/确认`（无正文）；② 确认 = **完全关闭**：`PlaybackService.ACTION_SHUTDOWN`（停播 + 清队列 + 摘前台通知 + `stopSelf`，`START_NOT_STICKY`）+ `DesktopLyricsService.stop` + `finishAndRemoveTask()`；实测确认后回桌面、通知消失、`playbackState=NONE`、无音频焦点、进程降为缓存进程（`stopService` 会被 Media3 拉回，**不要退回**）；③ 多行路径框键盘由 `KeyboardType.Uri` 改 `Text`，能打回车（实测 `/yd/a`↵`/yd/b` 两行）；④ OpenList 保存后回填归一化路径；⑤ 删 `RemoteLibrarySyncer.normalizeRoot` 死代码，加 `RootPathsTest`（8 断言）。见 `AGENTS.md` §0.16。
最近几轮（从旧到新）：2026-11-16 第十五轮 r16/r17/r18 补验 → 2026-11-17 `/dav` 播放修复 → 同日下午三项 → 同日傍晚六项 → 同日文档状态同步 → 同日代码审查修复 → 同日晚液态玻璃回滚 → 同日深夜五项缺陷修复 → 同日深夜五项收尾修正 → 同日深夜图标/加歌单 → 同日夜四项再改 → 同日深夜返回键/完全关闭，证据依次见 `AGENTS.md` §0.5 / §0.6 / §0.7 / §0.8 / §0.9 / §0.10 / §0.11 / §0.12 / §0.13 / §0.14 / §0.15 / §0.16 与 `05-progress.md` 末十节。

## 5. 常用命令（Windows PowerShell）

```powershell
$root = "C:\Users\Win\WorkBuddy AI\2026-11-08-09-37-42\primuse-android"
$ADB  = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"   # 直接写字符串偶被截断

# 构建：gradlew.bat 在 PowerShell 下静默失败，必须用 java 起 wrapper
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
  -ArgumentList '-Xmx4g','-classpath','gradle\wrapper\gradle-wrapper.jar',
                'org.gradle.wrapper.GradleWrapperMain',':app:assembleDebug','--console=plain' `
  -WorkingDirectory $root -WindowStyle Hidden `
  -RedirectStandardOutput "$env:TEMP\build.log" -RedirectStandardError "$env:TEMP\build.err"

# 单测 / 自检
& ".\gradlew.bat" test            # 或同样用 java 起 wrapper
sh ../scripts/check.sh              # 完整性自检，不需要 JDK

# 装机 + 看日志
& $ADB -s emulator-5554 install -r -t "$root\app\build\outputs\apk\debug\app-debug.apk"
& $ADB -s emulator-5554 logcat -d -s Msop:V        # 偶发空 → 设备端 logcat -d > /sdcard/x.log 再 pull

# 远程缓存占用
& $ADB -s emulator-5554 shell "run-as com.msop.player du -sk /data/data/com.msop.player/cache/media_cache"
```

环境：`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot\`；SDK compileSdk 37 / build-tools 37.0.0。

## 6. 归档区 `../archive/` 约定

| 子目录 | 放什么 |
|---|---|
| `../archive/2026-11-15-drag-debug/` | 那一轮手势/异色块排查的根目录截图与 uiautomator dump |
| `../archive/reference-shuyin/` | ShuYin 界面参考图（4 张） |
| `../archive/reference-device-verification/` | 旧版设备验收截图（原 `docs/reference/device-verification`，2026-11-16 移出仓库） |
| `../archive/design-proposals/` | 历史设计稿 HTML（`index.html`、`library/icons/lyrics-redesign.html`）与 v1.0 原型，2026-11-16 移出仓库 |
| `../archive/deliverables/` | 历史「仅改动文件」zip 归档。**2026-11-17 起用户要求改完不要打包交付、直接改项目文件**，该目录已清空且不再新增 |

**目录纪律**（后续接手请遵守）：

- 仓库根只放：源码模块（`app` `core` `feature`）、构建文件（`gradle*` `settings.gradle.kts` `build.gradle.kts` `*.properties`），以及 **`README.md` 这唯一一份根级 Markdown**。
- **其余 Markdown 全部在 `docs/`**（本页同目录）：`AGENTS.md` `TASKS.md` `USAGE.md` `BUILD-NOTES.md` `02`–`08`。新增文档也放这里，不要再落回仓库根。
- **进 GitHub 的只有**：源码 + Markdown 文档 + `../preview/baseline/` 三张基线图 + `gradlew*`/`../scripts/`。历史设计稿、旧验收截图、过程截图一律在 `../archive/`（已被 `.gitignore` 排除）。
- 临时产物（设备截图、logcat dump、uiautomator xml、反编译解包、`.raw` 录屏）**一律放 `%TEMP%\<轮次>shots\`**，不要落进项目根或被 git 跟踪。
- 确实值得留档的非项目文件 → `../archive/<主题>/`，并在提交说明里写清来源。

## 7. 一页避坑（详见 `AGENTS.md` §0 第 6 条）

- AGP 9 内置 Kotlin，**不要**加 `org.jetbrains.kotlin.android` 插件。
- URL 一律用 `HttpUrl.newBuilder()` 拼，禁止字符串拼接。
- 缓存 key 不能用带签名参数的 raw url（用 `CacheDecision.stableKey()`）。
- 标题栏禁止 `drawBackdrop(LocalLayerBackdrop.current)`（RenderNode 自引用 → RenderThread 栈溢出闪退）。
- 二级页 overlay 必须是根 `Box` 的兄弟节点，不能塞进 `HazeTitleScaffold` 的 content。
- `PlayMode` 4 档，任何 `when(PlayMode)` 都要补分支。
- 模拟器造计费网络 / 断网的正规手法见 `AGENTS.md` D29；**别从 PC 侧关 Wi-Fi 或开飞行模式**，会把 adb 打掉。
- 改结论时同步三处：`AGENTS.md`（对应轮次段）、`05-progress.md`（末节）、`TASKS.md`（状态行 + 变更记录）。
