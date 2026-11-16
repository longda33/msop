# TASKS.md — 分段任务表

> **这是进度的唯一真相。** 开工前找到第一个 `[ ]`，完成后改成 `[x]` 并更新 `AGENTS.md` 第 1 节。
> 标记：`[ ]` 未开始 · `[~]` 进行中 · `[?]` 已实现待验证 · `[x]` 已完成 · `[!]` 阻塞（必须在备注写原因）
>
> **`[?]` 的用法**：代码写完但尚未通过验收（例如本地没有构建环境）。只有真正跑通验收标准才能改成 `[x]`。
>
> **✅ P3 阻塞已解除**（2026-11-11）：用户提供真实 OpenList 地址与账号，完成实测并接通远程音源。
> 但实测发现这台服务器**未启用 Subsonic**，D3 决策据实测反证修订为「OpenList 原生 REST 主通道」，
> 见 `03-openlist-api.md` §6。P3-04 的 `SimpleCache` 边播边缓存与「仅 Wi-Fi 缓存」开关已实现（2026-11-12，
> 真机复验待做，见 `03-openlist-api.md` §8.4）；P3-05 的 WebDAV 已升级为独立第三音源，并入 P7。
>
> **P7（2026-11-12 起）**：ShuYin 功能融入，12 项，计划与验收见 `08-shuyin-integration.md`。
> 2026-11-15 起：项目开源，第三方代码可直接采用（GPL-3.0 限制已解除，见 `AGENTS.md` D26）；`AGENTS.md` §3 第 2 条已允许 WebDAV。

## 总览

| 阶段 | 目标 | 任务数 | 预估 | 状态 |
|---|---|---|---|---|
| **P0** 立项与骨架 | 工程能编译、能装、能跑空壳 | 4 | 2–3 天 | 4 完成 |
| **P1** 本地媒体库 | 列出本机所有歌并入库（MediaStore + SAF 双通道） | 9 | 7–9 天 | 6 完成 / 1 部分 / 2 待验证 |
| **P2** 播放内核 | 点歌能放，切后台不断，锁屏可控 | 5 | 5–7 天 | 5 完成 |
| **P3** OpenList 接入 | 浏览公网远程音乐库并流播（含 HTTPS 与证书） | 7 | 9–13 天 | 6 完成（含缓存，1 项真机待验）/ 1 未做（WebDAV） |
| **P4** 界面 | 四种浏览 + 搜索 + 队列 + 播放页 | 5 | 7–10 天 | 3 完成 / 1 部分 / 1 待验证（本轮播放页适配） |
| **P5** 歌词与打磨 | 歌词滚动、性能、保活 | 5 | 5–7 天 | 3 完成 / 1 部分 / 1 未做 |
| **P6** 验收与打包 | 全流程走查、出 APK | 3 | 2–3 天 | 2 完成（release 包 + 使用说明）/ 1 未做 |
| **P7** ShuYin 融入 | 播放恢复/断网保护/备份/TTML/音质/WebDAV/悬浮歌词/视觉动效/libFLAC | 12 | 12–18 天 | 8 完成 / 3 待验证 / 1 阻塞（视觉三连全部完成） |
| | | **50** | **约 12–16 周** | |

**关键路径**：P0-01 → P1-01 → P1-02 → P1-03 → P2-01 → P2-02 → P3-01 → P3-02 → P3-06 → P4-01 → P6-02
其余任务可与关键路径并行。

**最小可用切片（只有 3 天时）**：P0-01、P0-02、P1-01、P1-03、P2-01、P4-01 简化版。

---

## P0 — 立项与骨架

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P0-01 | 初始化 Gradle KTS 工程 + `gradle/libs.versions.toml` | `./gradlew tasks` 成功；版本全在 toml 中 | — | `04-reference-index.md` §1 |
| `[x]` P0-02 | 建 12 个模块骨架 + Hilt 装配 | `./gradlew :app:assembleDebug` 出 APK（12 MB）；真机启动待验证 | P0-01 | `02-architecture.md` §2 |
| `[x]` P0-03 | 接入 Compose + Material3 + Navigation，做出一个空 Tab 页 | 真机（OPPO PJB110 / Android 12）确认底部三 Tab 可见、可切换，无崩溃 | P0-02 | `04-reference-index.md` §2 |
| `[x]` P0-04 | 建 `Application` 类 + 日志封装 + 崩溃捕获 | 真机 logcat 确认 `Application onCreate` 与「崩溃捕获已安装」输出 | P0-02 | — |

## P1 — 本地媒体库

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P1-01 | 定义 `Track` / `SourceType` / `Album` / `Artist` 模型 | 字段与 `AGENTS.md` §7 一致；模型被数据层与全部 UI 消费，历轮真机功能验收已覆盖（2026-11-16 按实际状态把旧 `[?]` 收敛为 `[x]`） | P0-02 | `AGENTS.md` §7 |
| `[x]` P1-02 | Room 实体 + DAO + 数据库 + 索引 | KSP 编译期校验 DAO SQL 通过；schema 确认 4 个索引（source/album/artist/lastModified）；mapper 4 个单测全过 | P1-01 | `04-reference-index.md` §3 |
| `[x]` P1-03 | **MediaStore 主通道**：音频查询、分页 500/批、按 `date_modified` 增量 | 真机实测：1003 首全量 **376ms**、增量 **35ms**（外推 1 万首约 3.8s，远低于 30s）；`<unknown>` 标签已转 null | P1-02 | `02-architecture.md` §9.1 |
| `[~]` P1-04 | 权限：`READ_MEDIA_AUDIO`（13+）/ `READ_EXTERNAL_STORAGE`（≤12），拒绝时有引导页 | 门禁与授权放行已真机验证；**拒绝/重试流程无法验证**——该设备自动授予权限，系统弹窗不出现 | P1-03 | `04-reference-index.md` §4 |
| `[x]` P1-05 | **SAF 自定义目录**：`OpenDocumentTree` + 持久授权 + `ContentResolver.query(childrenUri)` 批量遍历 | 真机验证：添加 /Music 后重启仍有效；503 文件遍历 878ms（外推 1 万约 18s，低于 40s 目标） | P1-04 | `02-architecture.md` §9.2 |
| `[x]` P1-06 | **双通道去重**：MediaStore 与 SAF 结果归一化为绝对路径后取 sha1 | 真机：同一批文件两条通道各扫一遍 → **扫描 1006 / 写入 503 / 去重 503**，库内 503 条；跨页与跨来源的重复都计入 | P1-05 | `02-architecture.md` §9.3 |
| `[x]` P1-07 | 元数据与封面提取（`MediaMetadataRetriever` + 封面落盘缓存） | **数据侧已真机验证**：SAF-only 文件标签回填（title/artist/album/duration/trackNo/year）、封面落盘 300×300 PNG、`coverUri` 回写、重扫不丢、删曲目后孤立封面被回收。原备注的「待 P1-09」已随 P1-09 真机验收解除（列表页标签/封面显示与滚动均正常，2026-11-16 收敛为 `[x]`） | P1-06 | `04-reference-index.md` §5 |
| `[x]` P1-08 | WorkManager 后台扫描 + 进度通知（双通道合并为一个任务） | 真机验证：排入任务 → Worker 执行 → 通知显示「扫描完成：共 3 首，写入 3 首」；唯一任务不叠加。杀进程恢复由 WorkManager 保证，因扫描仅 376ms 未实测中断 | P1-07 | `04-reference-index.md` §6 |
| `[x]` P1-09 | 本地列表页：歌曲 / 专辑 / 艺术家 / 文件夹 四种 | **真机验收通过**（emulator-5554 / PJB110 / 450×800dp）：五种视图（另加「最近」）分组正确，专辑网格 → 专辑详情跳转正常，艺术家与文件夹按标签/目录归类正确 | P1-07 | `04-reference-index.md` §7 |

## P2 — 播放内核

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P2-01 | ExoPlayer 初始化 + `ResolvingDataSource`（预留远程重定向） | 真机：**MP3 / WAV / FLAC 均正常播放**，位置持续推进无 `ExoPlaybackException`；音频焦点、耳机拔出暂停、唤醒锁已配置 | P1-01 | `04-reference-index.md` §8 |
| `[x]` P2-02 | `MediaSessionService` + 通知栏 + 锁屏控制 | 真机：按 HOME 后仍 PLAYING、服务 `isForeground=true`；通知含上一项/暂停/下一项三个动作与封面大图；媒体键（等价锁屏/耳机键）暂停→PLAYING→下一首均生效。**未做锁屏截图**（避免设备锁死），走的是同一条 MediaSession 通路 | P2-01 | `04-reference-index.md` §8 |
| `[x]` P2-03 | 播放队列、顺序/单曲/随机、上一首下一首 | ✅ 真机：队列切歌、单曲循环（5 秒曲目 9 秒后仍在播且位置回到 0.9s）、随机（item 1→432→428→283 非连续）均正确；拖拽排序 UI 已接（长按手柄、`QueueReorder` 6 个单测、松手一次性 `moveInQueue`），真机手势未单独验证 | P2-02 | `04-reference-index.md` §9 |
| `[x]` P2-04 | 音频焦点、耳机拔出暂停、来电打断恢复 | 真机实测：① **拔耳机**——root 发系统保护广播 `android.media.AUDIO_BECOMING_NOISY` → 播放立即暂停（state 3→2）；② **来电打断**——申请瞬态焦点 → 暂停；③ **挂断恢复**——放弃焦点 → 自动续播（state 2→3）。`dumpsys audio` 可见 ExoPlayer 的 `AudioFocusManager` 持 GAIN 焦点、`notified=true` | P2-02 | `04-reference-index.md` §9 |
| `[x]` P2-05 | 播放页基础 UI：封面、进度条、播放控制 | **真机验收通过**：拖动进度条 seek 精确（拖到 66.7% → `position=100008ms`，曲长 150s），封面/歌词双态切换正常，五个控制键与底部三入口均可点 | P2-03 | `04-reference-index.md` §10 |

## P3 — OpenList 接入

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P3-01 | **实测真实服务器**，把真实响应写进 `03-openlist-api.md` | ✅ 两轮实测：① Subsonic 未启用时三条通道对比（`03-openlist-api.md` §6）；② 用户启用媒体库后复测（§7）：Subsonic 只认 `apiKey`、封面返回真实 JPEG、`stream` 302 到 http 明文不可用、`path` 与 `/api/fs/get` 不通用 | P0-02 | `03-openlist-api.md` §6 §7 |
| `[x]` P3-02 | 远程客户端封装：原生 REST（JWT）+ Subsonic（`apiKey` 元数据） | ✅ 真机：登录/列目录/取直链全通，错误有中文提示；`ServerUrl` 11 个单测覆盖 URL 规范化与 `HttpUrl` 拼接；Subsonic 走 `@rest` + `apiKey`，只取 `search3` 与 `getCoverArt` | P3-01 | `03-openlist-api.md` §7 |
| `[x]` P3-03 | 远程 Track 映射入库（`source=OPENLIST`，`id=openlist:{sha1(path)}`，`uri=openlist:/路径`） | ✅ 真机：`扫描 23 项 · 写入 23 首 · 补全 23 首 · 8476ms`，DB `OPENLIST|23`（23 有封面/艺术家/albumId），与 508 首本地歌混排；安全阀不误删本地 | P3-02 | `03-openlist-api.md` §6 §7 |
| `[x]` P3-04 | 远程流播 + `SimpleCache` 边播边缓存 + 「仅 Wi-Fi 缓存」开关 | ✅ **2026-11-16 模拟器实测三条全过**：① 清理后归零（设置页 `106.8 MB · 5 首` → 确认弹窗 → `0 B · 0 首`）；② 同曲重播命中——首播一次 `远程缓存未命中`、重播 12s 内零未命中（`RemoteCacheDataSource` 用 `CacheDecision.stableKey` 剥掉带签名的 `?`/`#`）；③ 计费网络占用不涨——`cmd wifi add-network <SSID> open -m` 造出 metered Wi-Fi（不掉 adb），播 30 MB 未缓存 FLAC 时日志 `远程缓存策略 wifiOnly=true metered=true 写入=false`，缓存目录 `2364 KB` 前后不变；已缓存的 ZIV 在 metered 下继续正常播放、占用仍不涨 | P3-02 | `04-reference-index.md` §11 / `03-openlist-api.md` §8、`05-progress.md` 2026-11-16 第十五轮 |
| `[x]` P3-05 | WebDAV 兜底通道（PROPFIND 列目录） | ✅ 2026-11-16 并入 P7-07 并真机验收：内网 `http`（187 首）与公网 `https`（3 首）两台同时可用，`PROPFIND` 207 + Basic Auth，多台配置可保存/切换/删除 | P3-02 | `03-openlist-api.md` §4 §9、`08-shuyin-integration.md` §2 |
| `[x]` P3-06 | **网络安全配置**：`network_security_config.xml` 禁明文、允许 user CA、`HttpUrl` 拼路径 | ✅ 配置页填 `http://` 弹明文警告并要二次确认；manifest 未开 `usesCleartextTraffic`；13 个用例覆盖 | P3-02 | `02-architecture.md` §8 |
| `[x]` P3-07 | 服务器配置页：地址 / 账号 / 密码 + URL 规范化 + 连通性测试 | ✅ 真机：填域名自动补 `https://`，测试连接与「保存并同步」均成功，错误有中文提示 | P3-06 | `03-openlist-api.md` §1.2 |

## P4 — 界面

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P4-01 | 导航图 + 底部 Tab（库 / 搜索 / 设置）+ 播放条 | **真机验收通过**：三 Tab 切换正常，任意页面可见胶囊播放条，点击展开播放页，返回键只收起浮层 | P0-03, P2-05 | `04-reference-index.md` §13 |
| `[x]` P4-02 | 搜索：本地 + 远程合并，防抖 300ms | ✅ **2026-11-16 合并实测**：同一关键词 `Music` 返回 `36 首`，列表里本机行（`世界が終るまでは…` / WANDS · **Music** 专辑）与云端行（`musicweb` 歌手）同屏；`ZIV` / `Nivis` 只命中云端条（证明云端参与同一查询）。实现上 `_searchQuery` + `tracks` `combine` 后 `debounce`，`tracks` 本身含 OPENLIST / WEBDAV 行 | P4-01 | `04-reference-index.md` §14、`05-progress.md` 2026-11-16 第十五轮 |
| `[x]` P4-03 | 播放列表：新建/重命名/加歌/删歌，持久化 | **真机验收通过**：Room v1→v2 迁移保留 505 首；新建 `TestMix` → 加 2 首 → 移除 1 首 → 重命名 `Mix2`，强停重启后歌单与曲目仍在 | P1-02 | `04-reference-index.md` §15 |
| `[x]` P4-04 | 播放页完整版：模糊封面背景、下滑收起（**上滑开队列已于 2026-11-15 按用户指令移除**）、竖屏左右滑切封面/歌词、当前歌词居中，横屏左侧封面/曲目信息/控制、右侧仅歌词 | 手势与模糊背景真机验收；2026-11-15 改为「整页一起下滑、后面露出曲库」，顶部异色块根因（1.18 倍模糊封面溢出）加 `clipToBounds()` 修掉，深/浅主题 + 回弹 + 上滑无动作 + 队列按钮五项真机实测通过；此后第五～九轮歌词页视觉/浮层/横竖屏形态均在真机逐轮验收（2026-11-16 收敛为 `[x]`） | P2-05 | `04-reference-index.md` §16、`05-progress.md` 2026-11-15 条 |
| `[x]` P4-05 | 设置页：扫描目录、服务器、缓存大小与清理、关于 | ✅ **本地与远程部分全部真机验收**：目录管理（含新增的「移除目录」）、重扫、封面缓存清理、OpenList 配置/测试/同步、均衡器、电池优化白名单引导、版本与只读声明全部可点可用 | P3-07 | — |

## P5 — 歌词与打磨

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[~]` P5-01 | LRC 解析：内嵌（ID3 USLT / FLAC Vorbis）、同目录 `.lrc`、远程 | **本地两种来源已真机验收**：ID3v2.3 `USLT`（UTF-16）同步歌词高亮/点击跳转正确；无时间标签的 `USLT` 退化为静态全文并标注「歌词未同步」；同名 `.lrc` 优先于内嵌。**远程分支 2026-11-16 查明「完全没实现」**：`LyricsRepository.readEmbedded()` 对 `track.source != LOCAL` 直接 `return null`，旁挂 `.lrc` 只查 SAF 兄弟文件 / 授权树 / 裸路径，云端 uri 走不到；且两台服务器均无素材（OpenList `/yd/musicweb` 递归 60 条 0 个 `.lrc`/`.ttml`，WebDAV `/cd18/Music` PROPFIND 33 href 0 个歌词文件）。要落地需新写「远程旁挂 lrc 拉取 + 缓存」并往服务器放测试 `.lrc`（改用户服务器，待拍板） | P1-05 | `04-reference-index.md` §17、`05-progress.md` 2026-11-16 第十五轮 |
| `[x]` P5-02 | 歌词滚动与当前行高亮，手动滑动 5s 后回中 | **真机验收通过**：当前行蓝色高亮、自动滚动跟随；点击任意歌词行跳转到对应时间；手动拖动后暂停跟随，停手 5 秒恢复。静态歌词不参与高亮与跳转 | P5-01 | `04-reference-index.md` §17 |
| `[x]` P5-03 | 均衡器（`android.media.audiofx.Equalizer`，10 段曲线 + 设备实际段数映射） | ✅ 真机：识别到设备 5 段（60/230/910/3.6k/14k），点「摇滚」实时变为 +3.5/-1.0/+1.0/+3.5/+4.0 dB，日志与设置页状态同步；PlaybackService 在 `onAudioSessionIdChanged` attach；Media3 1.11 改用 `AudioTrackAudioOutputProvider` 关 offload | P2-01 | `04-reference-index.md` §17 |
| `[ ]` P5-04 | 性能：大库（5 万首）滚动、封面内存缓存、冷启动 | 列表滚动 ≥ 55fps；冷启动 < 2s | P4-04 | — |
| `[x]` P5-05 | 后台保活：前台服务 + 电池优化白名单引导 | ✅ 前台服务/`WAKE_LOCK`/`stopWithTask=false` 已就位；设置页显示豁免状态并可跳系统弹窗，回前台自动重判。**息屏 30 分钟未实测**（需真人设备） | P2-02 | `04-reference-index.md` §18 |

## P6 — 验收与打包

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[ ]` P6-01 | 真机全流程走查（按 `05-progress.md` 的清单） | 清单全部通过，问题记入 `05-progress.md`。本轮已覆盖大部分条目，但清单未逐条勾完 | P5-* | — |
| `[x]` P6-02 | 自签名 release APK（`minifyEnabled=true` + 混淆规则） | ✅ `assembleRelease` 成功，V2 签名校验通过，APK **19.3 MB → 3.0 MB**；release 包真机验证：权限门禁、本地扫描、OpenList 登录/同步（23 首）、远程 FLAC 播放全部正常，无 R8 误删 | P6-01 | `USAGE.md` §6 |
| `[x]` P6-03 | 写使用说明（怎么填 OpenList 地址、怎么加本地目录） | ✅ `USAGE.md` 已交付，含安装、权限、目录增删、服务器配置、播放/歌词/均衡器、构建与重新生成签名、已知限制 | P6-02 | `USAGE.md` |

## P7 — ShuYin 功能融入（独立重写）

> 完整计划、技术预研与逐项验收见 `08-shuyin-integration.md`。
> 原则（历史）：原为只借鉴功能、不复制 GPL-3.0 代码/资源；该限制已于 2026-11-15 解除（D26）。UI 仍在 `app/ui`。

| # | 任务 | 验收标准 | 依赖 | 参考 |
|---|---|---|---|---|
| `[x]` P7-01 | 播放页进度条回归修复 | ✅ 真机（PGEM10 / Android 15）：120s 曲目点 50% → `position=60003ms`（误差 3ms）；拖到 20% → `23634ms`；拖动跟手、不与上下滑冲突 | — | `08-shuyin-integration.md` §2 |
| `[x]` P7-02 | 播放状态恢复 | ✅ 真机：播放至 43.3s → 强停 → 重启 `PAUSED(2)/position=43340`、队列 index=501、迷你条不出声；点播放从恢复点继续。快照 7 天过期与清数据由代码+单测保证（8 用例） | P7-01 | `08-shuyin-integration.md` §2 |
| `[~]` P7-03 | 断网保护 + 缓冲提示 | **2026-11-16 三项已实测，缓冲提示一项仍缺**。手法：`svc wifi disable; sleep …; svc wifi enable` 写成设备端 `nohup` 脚本先跑，断网窗口里由脚本自己 `screencap` + `logcat -d`，Wi-Fi 恢复后 adb 自动回来再拉取证（这样不必人手救 adb）。结果：① 播云端曲（`指尖缠绕 - ZIV`，1:10 处断网）→ 播放页顶栏变橙点「网络已断开」+ 浮层「网络已断开，播放已暂停」，播放键转 ▶，日志 `网络断开，远程曲目已自动暂停`；② Wi-Fi 恢复后顶栏变「已暂停」、提示消失、停在 1:10 **不自动续播**，日志 `网络已恢复，等待用户手动继续播放`；③ 播本地 FLAC（`Love Paradise`）同时断网 → 继续播（0:32 仍在走）且日志无网络行。**「缓冲中…」/「网络较慢，仍在缓冲…」未验**：本机没法在不掉 adb 的前提下造低速网络（代理只能造连接失败 → ERROR，不是 BUFFERING） | P7-02 | `08-shuyin-integration.md` §2、`05-progress.md` 2026-11-16 第十五轮 |
| `[x]` P7-04 | 备份与恢复 | ✅ **2026-11-16 SAF 全流程实测**：导出（SAF create-document，落 `/storage/emulated/0/msop-music/msop-backup-20261116-2133.zip`，888 B，zip 内 `manifest.json` + `playlists.json` + `settings.json`，弹窗「已导出 2 个歌单、1 首曲目；含全部设置与服务器凭据」）→ 删掉 `BatchTest` 歌单 → 导入同一 zip → 弹出「已恢复 2 个歌单、1 首曲目；OpenList / WebDAV 配置已恢复」→ 歌单页 `BatchTest` 复活、三项齐全。坏 zip 也测了：推一个只含 `notes.txt` 的 zip 进去导入，报「导入失败：不是 msop 备份：缺少 manifest.json」，原数据无变化。**口径修正**：本条原先写的「密码不导出」与实现相反——备份 `settings.json` 里 `password` 是明文、设置页副标题与导出弹窗都写明「含服务器密码 / 含全部设置与服务器凭据」，即设计如此，不是缺陷 | P7-02 | `08-shuyin-integration.md` §2、`05-progress.md` 2026-11-16 第十五轮 |
| `[x]` P7-05 | TTML 逐字歌词 | ✅ 真机 `.ttml`：4 行、4.8s 时前两段 span 高亮、中文不插空格、暂停态点第三行 → `45000ms`；13 个单测覆盖命名空间/嵌套 span/帧与 tick/无 end 回退 | P7-01 | `08-shuyin-integration.md` §2 |
| `[x]` P7-06 | 音质信息（码率/采样率/位深） | ✅ 真机：`user_version=3`、509/509 码率与采样率、505 位深；弹窗 `16bit / 44.1kHz / 129kbps`；v2→v3 为三条 `ALTER TABLE`。**远程 23 首音质未验**（设备无 OpenList 配置） | P7-02 | `08-shuyin-integration.md` §2 |
| `[x]` P7-07 | WebDAV 独立第三音源 | ✅ 2026-11-16 真机（PGEM10）三台：内网 `http://172.207.160.128:5244/dav` + `/cd18/Music` 同步 **187 首**、`https://dav.2dland.cn` 根同步 **3 首**、koyeb `https` 在本设备解析到 `[::1]` 不可达（`FAIL` 状态如实显示）；多台保存 + 单选启用 + 切换清空本来源 + 删除非启用项不动曲库 + http 明文明文二次确认全部验收 | P7-06 | `08-shuyin-integration.md` §2、`03-openlist-api.md` §4 §9 |
| `[x]` P7-08 | 桌面悬浮歌词（完整版） | ✅ 真机：授权后可见、逐行更新、拖动吸附（x 0→660）、锁定 `NOT_TOUCHABLE`、字号 17→30sp/颜色/背景 30→80% 实时生效、单行、关闭立即消失；杀进程不重启，重启 App 状态保持且打开设置页按需恢复 | P7-03 | `08-shuyin-integration.md` §2 |
| `[x]` P7-09 | Haze 标题栏毛玻璃 | 真机：设置/曲库/专辑详情滚动中标题栏后方内容呈模糊轮廓、文字清晰；专辑详情/歌单列表/歌单详情/选歌页接入统一 HazeTitleScaffold（含返回键）。性能已按 60Hz 云真机实测并优化（20dp+noise→ 12dp）：无模糊基线 p50=26ms，模糊后 27–32ms，系统设置应用基线 21ms（≈47fps 设备上限）；模糊边际开销约 5–6ms。API<12 降级纯色仅代码路径验证（本机无 API 30 设备/模拟器） | P7-08 | `08-shuyin-integration.md` §2 |
| `[x]` P7-10 | 液态玻璃导航 | 真机（debug + release 双包）：内容从底部胶囊/迷你条后方经过呈磨砂；按压跟随触点的径向光斑 + 果冻挤压（motionevent DOWN 抓帧验证）；Tab 切换/迷你条/播放页/搜索均正常；热区 48dp；R8 后磨砂仍存活（3.3MB）。性能如实记录：60Hz 云真机中速滑 p50 32–34ms（设备本身上限 ≈47fps）。API<31 降级仅代码路径验证 | P7-09 | `08-shuyin-integration.md` §2 |
| `[x]` P7-11 | iOS 式二级页转场 | 真机：专辑/歌单列表/歌单详情/选歌/设置四子页右滑入+0.94 缩放+24dp 圆角收拢，背景压缩渐暗；返回反向；连点 ×4 不崩；返回键行为不变（单步验证四条链路）。修复两个真机暴露的 bug：圆角负值崩溃（临界阻尼）、透明页鬼影（内置不透明底）；test + assembleRelease 全绿 | P7-10 | `08-shuyin-integration.md` §2 |
| `[!]` P7-12 | libFLAC Native 扩展 | APK 含 `libflacJNI.so`；真机 FLAC 走扩展解码器；其他格式不回退失败。**阻塞：本机无 NDK/CMake，Media3 未发布 `media3-decoder-flac` AAR** | P7-01 | `08-shuyin-integration.md` §3.3 |

---

## 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-11-10 | 创建任务表，35 个任务分 7 阶段 | 立项 |
| 2026-11-10 | P1 拆为 MediaStore / SAF / 去重三项（+2）；P3 增加网络安全配置（+1）；总任务 35 → 38，工期 8–10 周 → 9–11 周 | 用户确认：本机默认标准目录 + 支持自定义目录；OpenList 为公网部署 |
| 2026-11-11 | P1-06 完成、P1-07 数据侧完成（`[?]` 等 UI）；P1 由「4 完成 / 4 未开工」变为「5 完成 / 1 部分 / 2 待验证 / 1 未开工」 | 双通道去重与元数据/封面提取已真机验收 |
| 2026-11-11 | 转入 P2：P2-01/02 完成，P2-03/04 待验证；P2 由「未开始」变为「2 完成 / 2 待验证 / 1 挂起」 | 播放内核已真机跑通，队列拖拽与音频焦点场景需手动验证 |
| 2026-11-11 | UI 设计定稿提案 v1.0 交付（`07-ui-design.md` + 9 屏可交互原型），Q1–Q4 已答；UI 任务由「等 Q1–Q4」变为「等用户确认设计稿」 | 设计瓶颈解除，用户确认后 P1-09 / P2-05 / P4-* / P5-02 可开工 |
| 2026-11-11 | P2-04 完成；P2 变为「3 完成 / 1 待验证 / 1 挂起」 | 耳机拔出用 root 系统广播、来电用焦点探针，三个场景均实测通过 |
| 2026-11-11 | **用户提供 OpenList 地址与账号，P3 阻塞解除**；D3 据实测反证修订 | 实测 v4.2.9 未启用 Subsonic（`/@rest/*` 404、`media_enabled=false`、账号非 admin），WebDAV 可用但选原生 REST 为主通道，见 `03-openlist-api.md` §6 |
| 2026-11-11 | P3-01/02/03/06/07、P5-03、P5-05、P6-02、P6-03 完成；P2-03 拖拽补齐；P4-05 全部入口补齐 | 远程音源端到端真机跑通（同步 23 首 + FLAC 播放 + seek）；均衡器真机生效；release 包 3.0 MB 且真机验证通过；`USAGE.md` 交付 |
| 2026-11-11 | 新增「移除 SAF 目录」「队列拖拽」两个已知缺口的修复 | 目录管理弹窗可列出并移除目录（同时释放持久授权 + 全量重扫）；队列长按手柄拖动，纯函数 `QueueReorder` 带 6 个单测 |
| 2026-11-11 | 剩余：P3-04 `SimpleCache`、P3-05 WebDAV 兜底、P5-04 大库性能、P6-01 清单逐条走查 | 这四项都不阻塞「本机 + OpenList 天天用」 |
| 2026-11-11 | **Subsonic 启用后改为混合通道**：原生 REST 管同步/流播，Subsonic（`apiKey`）管艺术家/专辑/封面 | 复测发现 Subsonic 只认 `apiKey`、`stream` 302 到 http 明文、`path` 与 `/api/fs/get` 不通用；但封面是原生 REST 给不了的。真机 23/23 补全，见 `03-openlist-api.md` §7 |
| 2026-11-11 | 修复 Coil 3 网络封面不加载 | 只依赖 `coil-compose` 时本地封面正常、远程静默失败；补 `coil-network-okhttp` 后真实 JPEG 正常渲染并落缓存 |
| 2026-11-12 | 更新播放器布局并采用用户提供的 mslogo 启动图标；P4-04 标为 `[?]` 等待构建和设备复验 | 竖屏当前歌词居中；横屏左侧封面/曲目信息/控制，右侧仅歌词；项目名与 app 名统一为 `msop` |
| 2026-11-12 | 全 UI 视觉重构（Aurora Glass），无任务勾选变化 | 用户要求「UI 太丑 进行重构」；功能路径不变，debug/test/release 通过，纯视觉待设备复验 |
| 2026-11-12 | **P3-04 实现完成，标 `[?]`** | 用户「开始 P3-04」：`SimpleCache` 边播边缓存 +「仅 Wi-Fi 缓存」开关落地；新增 4 个 main 类 + 1 个测试类（5 用例）。缓存 key 用稳定 `openlist:/路径`、`CacheDataSource` 包在 `ResolvingDataSource` 外层、蜂窝下只读不写。20 类 173 用例 0 失败，debug 18 MB / release 3.2 MB 构建通过。本机无设备，真机缓存命中按 `03-openlist-api.md` §8.4 复验后改 `[x]` |
| 2026-11-12 | **UI 评审与设计资产清理**，无任务勾选变化 | 用户「评审现有 UI 设计并提出优化方案 / 删除无用部分」：删 43 个失效过程截图（20.3 MB）+ v1.0 历史原型；`07-ui-design.md` 从 484 行旧提案重写为「当前实现说明 v2.0」；归档 `../preview/baseline/` 3 张当前视觉基线；移除 3 个零引用渐变、`Dim.navHeight` 落地；同步 `AGENTS.md` / `README.md` / `../scripts/check.sh` 索引。实测缺口（横屏未适配、466 处硬编码 dp、触摸目标 <48dp 等）与 P1/P2 优化待办记在 `07-ui-design.md` §5，**本次不改视觉** |
| 2026-11-12 | 新增 P7「ShuYin 功能融入」12 项；P3-05 WebDAV 升级为独立第三音源；总任务 38 → 50 | 用户逐项确认：播放恢复、悬浮歌词、断网保护、备份、TTML/音质、Haze/液态玻璃/iOS 转场、WebDAV 第三音源、libFLAC；其余默认跳过。计划见 `08-shuyin-integration.md` |
| 2026-11-14 | **P7-08 完成并真机验收；P7-01/02/05/06 真机复验转 `[x]`** | 设备 PGEM10 / Android 15 可用，APK 与本地 debug 包 md5 一致。`compileDebugKotlin` + `test` + `assembleDebug` 全绿（27 类 224 用例 0 失败）；悬浮窗服务/拖动/锁定/样式/生命周期全部实测；进度条、播放恢复、TTML、音质在真机上拿到可复现数据。P7-03/04/07 与 P3-04 因缺远程音源/服务器/SAF 流程仍为 `[?]`。详见 `08-shuyin-integration.md` §5 与 `05-progress.md` |
| 2026-11-14 | **P7-09 完成并真机验收**：Haze 2.1.0 接入；`HazeTitleScaffold` 统一五页（曲库/搜索/设置/专辑详情/歌单三屏）；二级页标题栏新增返回键（ScreenTitle leading 槽）；性能调参 12dp 无 noise；AlbumDetail/PlaylistScreen 重构为「全屏滚动 + 固定标题栏」iOS 大标题形态；删 PlaylistHeader 与 HazeLogger 调试开关。全量单测 27 类 224 用例 0 失败；真机视觉/交互/性能实测记录见 `08-shuyin-integration.md` §5 | 接手时 P7-09 代码已在调试中（`08-shuyin-integration.md` 看板还是 `[ ]`）；本轮补齐专辑/歌单接入、完成参数调优与验收闭环 |
| 2026-11-14 | **P7-10 完成并真机验收（含 release）**：去 Scaffold 底栏改为全屏内容 + 底部悬浮液态玻璃层；共享 HazeState（唯一源）+ `LocalHazeBottomInset`；新增 `Modifier.liquidGlass`（磨砂/折射高光/双层渐变/1dp 内描边，低版本降级）；`LiquidTab` 按压光斑 + 果冻挤压（48dp 热区）；MiniPlayer 升液态玻璃底；各屏列表 bottom padding 接入底部 inset（9 处）；底部磨砂 12→8dp 后中速滑 p50 32–34ms；R8 后磨砂存活（release 3.3MB 真机实测）；设备验证后恢复 debug 包 | P7-09 的 Haze 依赖被复用（磨砂采样）；验收含 debug + release 双包 |
| 2026-11-14 | **P7-11 完成并真机验收：视觉三连收官**。`iosStackSpec`+`EnteringPage` 基础设施；曲库两分支/歌单两层/设置四子页接入「常驻主内容压缩渐暗 + overlay 右滑入」模式。真机修复两个 bug：圆角弹簧欠阻尼过冲负值崩溃（改临界阻尼+钳位）、二级页透明背景叠加鬼影（EnteringPage 内置 AmbientBackground 不透明底）。连点 ×4 存活；返回键行为不变（单步验证四条链路）；test + assembleRelease 全绿。P7 剩余：P7-03/04/07 待远程资源、P7-12 NDK 阻塞 | 视觉三连（09/10/11）全部完成 |
| 2026-11-14 | **修复「曲库扫描没用」：全量扫描前请求系统重扫标准目录**。新增 `SystemMediaIndexer`（MediaScannerConnection 公开 API，Music/Download 目录级重扫，15s 超时兜底）；`LocalScanWorker` 仅 full=true 时触发，增量扫描不受影响。真机实证：未索引探针文件 + 点重扫 → 系统回调返回有效 URI、`扫描=19 写入=19`；删探针再重扫 → `清理幽灵条目 1 条`、回落 18。根因：adb push 落盘的文件不进 MediaStore，主通道查不到（`AGENTS.md` §8 已知环境特性） | 用户反馈「曲库扫描没用」；设备 18 首新歌入库验证 |列表 bottom padding 接入底部 inset（9 处）。性能：底部磨砂 12→8dp 后中速滑 p50 32–34ms。R8 后磨砂存活（release 3.3MB 真机实测）；设备已恢复 debug 包 | P7-09 的 Haze 依赖被 P7-10 复用（磨砂采样）；验收含 debug + release 双包 |
| 2026-11-14 | **用户截图需求批次 UI 改造收官**（无任务勾选变化）：主题两态化（深/浅两档、默认深色、无跟随系统，推翻 `08-shuyin-integration.md` §1 旧边界，见 D23）；`liquidGlass` 迁移 Kyant0 Backdrop 2.0.1（Apache-2.0，D24）；迷你条按用户参考图定稿两键版；播放页喜欢入顶栏/队列移底部 dock/删「点封面看歌词」提示；SleepDialog 预设+自定义步进；歌词页 ShuYin 化；搜索页删统计块改「共 N 首」；曲库删随机与艺术家 Tab、增横向歌手轮播；无封面统一程序化封面。**接手断点恢复**：上一会话在收尾阶段挂死，本轮补齐全量验证 + 真机验收（含 release R8 包磨砂存活）+ 五份文档记录 | 用户 2026-11-14 上午批量 UI 需求（截图 + 条目清单）；详细验收数据见 `05-progress.md` 与 `08-shuyin-integration.md` §5 同日条 |
| 2026-11-14 | **HOTFIX 启动闪退 + 标题栏磨砂回归**（无任务勾选变化）：根因是 `HazeTitleScaffold` 在外壳 `layerBackdrop()` 捕获的内容子树内 `drawBackdrop(同一 backdrop)`，RenderThread `prepareTreeImpl` 递归 512 帧爆栈 SIGSEGV；标题栏改回 Haze 并在组件内自建 `hazeSource`。全量 `test`（27 类 224 用例 0 失败）+ `assembleDebug` 全绿；真机 3 次冷启动 / Tab 切换 / 滚动均无崩溃，滚动后磨砂生效（截图 `../preview/verification/2026-11-14-crash-fix*.png`） | 用户反馈「app 闪退」；21:50 装包后 21:51 连续三次启动崩溃，详见 `05-progress.md` 同日 HOTFIX 条 |
| 2026-11-15 | **播放页手势收敛（真机验收通过）**：① 删掉「上滑打开队列」——竖直手势不再累计上滑分量，上滑钳在 0、不触发任何行为，队列只从底部 dock 进入；② 下滑改回**整页一起下滑、后面正常露出曲库**（`offset` 整层搬移），并定位「顶部异常色块」真正根因：`PlayerBackdrop` 的模糊封面 `scale 1.18` 溢出节点上下各约 144px，遮罩只铺面板范围，导致面板顶边之上露出约 100px 未压暗的亮带 → 根 Box 加 `clipToBounds()` 裁掉；③ 删除 `MsopApp` 里 11-15 早间临时加的 `Void @ 0.94` 实底。真机（PGEM10 / Android 15）五项实测：下滑露曲库无横带（像素采样：面板顶边 (41,45,62) = 静止页首行 (41,45,62)）、深/浅两主题均正常、超阈值收起、小幅回弹、上滑无动作、dock 队列正常 | 用户 07:19 截图反馈「播放界面向下滑动 顶部有异常色块，删掉向上滑动出现队列」；第二版方案被用户否决后改为「整页下滑露曲库」，详见 `05-progress.md` 2026-11-15 条 |

| 2026-11-15 | **播放页控制条重排 + 模式扇形菜单 + 队列拖动跟手 + 定时对话框**（用户六条指令）：① 删右下循环、喜欢移到该位置且去掉圆圈底（顶栏喜欢一并移除）；② 左下改「顺序」按钮（默认），点开扇形选其余模式；③ 当前模式不出现在扇形里（恒三项）；④ 扇形布局与动效照抄 JellyFab（Apache-2.0，见 `PlayModeFan.kt` 署名）；`PlayMode` 新增 `REPEAT_ALL`；⑤ 顶栏标题随播放状态变化（未在播放/断网/缓冲中/正在播放/已暂停，圆点同色）；⑥ 队列拖动手柄换 6 点图标、拖动时整行跟手（不透明底 + 投影），单行占位修正 58dp；⑦ 定时改 5/15/30 预设 + 手动输入。真机逐条验收通过（截图 `../preview/verification/2026-11-15-mode-fan*.png` 等） | 用户本轮指令；踩坑：扇形不能用 `Popup`（窗口被系统钳制），改页面内覆盖层 + `boundsInRoot` 锚点，详见 `05-progress.md` 同日条 |

| 2026-11-15 | **播放页三处 BUG 修复**（用户报）：① 拖动进度条后切下一首，进度条仍停在上一首位置——双层根因：`dragValue` 抬手未清（旧 SeekBar 只在值变化时 onCommit）+ `_positionMs` 只在播放中轮询（暂停切歌不再刷新）；② 播放模式扇形菜单离按钮太远——**保持等半径圆弧扇形**，R=130→86dp、张角 55°→80°（第一次误改成链式累加，用户回退后修复）；③ 封面滑到歌词没有动画——`detectHorizontalDragGestures` 硬切改 `HorizontalPager` 跟手翻页 + 弹簧吸附 + 4% 视差。真机数值验证：拖 2:28 → 切歌 → 0:00；播放中拖 0:59 → 3 秒后 1:09 → 再 3 秒 1:20；扇形三项半径 94/93/88dp、角度 90°/53°/16°；左右滑与 dock 按钮双向翻页正常，下滑收起与歌词滚动无回归 | 用户 11:51 反馈；本轮无 ViewImage，改用 `uiautomator dump` 解析 bounds 验证，详见 `05-progress.md` 2026-11-15 第三条 |

| 2026-11-15 | **D26：删除 GPL-3.0 复制约束**（用户「项目是开源的」）：`AGENTS.md` §0.7 / §3.4 / D22·D24·D25、`TASKS.md` 两处原则、`08-shuyin-integration.md` §0 边界全部改为「标注来源与许可证即可直接采用」，新增 D26；历史进度记录不追溯改写 | 待办：仓库无 LICENSE 文件，若引入 GPL 代码需补 GPL-3.0 LICENSE；BetterLyrics 是 C#/WinUI3，跨语言只能照抄视觉形态 |

| 2026-11-15 | **六项需求批次**：4 项落地（音乐库删「最近」Tab；文件夹条目由「点了就播」改为打开 `FolderDetailScreen`；迷你条进度条内缩 20dp 修首尾裁切 + 颜色统一 #9A8CFF；设置关于文案与导出/导入含明文密码）；2 项按用户要求**只出预览不写代码**：`../preview/icons-redesign.html`（15 旧 vs 15 新 + 1 新增 Library 图标对照）、`../preview/lyrics-redesign.html`（BetterLyrics 风格纯歌词，竖/横屏 + 自动隐藏迷你控制条 + 右上横屏键） | 真机验证：Tab 4 个、文件夹详情出现「播放全部」且不自动播放、设置页新旧文案核对；`test` 全绿。图标与歌词 Compose 代码等用户确认预览后再动 |

| 2026-11-15 | **图标替换落地 + 歌词页移植**：`PlayerIcons.kt` 换 11 个（Lyrics/Order/Cloud/DragHandle 保持原样、Library 新图标被否决）；`LyricsScreen.kt` 整文件重写，直接移植用户自有项目 `music-web-cf-main` 的全屏歌词（`.m2l-fs`）：纯渐变背景不用 blur、逐行 scale/30%-72%-100% 白、当前行发光、DstIn 边缘渐隐、3 秒工具条与控制条一起淡出；**纯净竖屏**删顶部封面信息行并隐藏 PlayerTopBar/PlaybackNotice/PlayerTransport；迷你条只留三按钮 + 进度条；横屏左封面+下方歌名歌手、右歌词、迷你条 zIndex 高于文字 | 真机：歌词页无顶栏/曲库标题、16 行歌词 + 横屏键 + 三按钮；3.5 秒后工具条与按钮一起消失而歌词仍在；`test` 全绿。译文行与封面取色因 Android 数据模型/接口未移植 |

| 2026-11-15 | **文件夹详情雾感修复 + 歌词页照抄 Primuse**：① `LibraryScreen` 的二级页 overlay 从 `HazeTitleScaffold` 内容层移到根 `Box` 兄弟层（与设置页/歌单页一致），主内容单独吃 alpha 0.55 + scale 0.96，详情页不再被压成半透明、也不残留「音乐库」标题栏；② `LyricsScreen.kt` 按用户「照抄 https://github.com/chenqi92/primuse」重写——26sp Semibold 左对齐、当前句 1.08 倍、已唱 0.32/0.40 与未来 0.46/0.56（深/浅）、按行距 +1.25dp 模糊（上限 5dp）、36% 锚点、540ms 换句、上下 12% 渐隐、250ms 提前切句、手动滚动 3s 宽限；背景去掉粉色渐变改模糊封面 + 主题感知压暗；顺带修 `LazyListItemInfo.offset` 不含 `contentPadding` 导致的锚点漂移（当前句停在 ~60%）。`test` 28 类 231 例 0 失败、`assembleDebug` + `assembleRelease`（3.48 MB）通过，真机深/浅两主题验收 | 用户 2026-11-15 两条反馈；Primuse 为 AGPL-3.0-only（D26 允许），来源与常量写进 `LyricsScreen.kt` 文件头 |

| 2026-11-15 | **第五轮四条需求落地**（无任务勾选变化，均为 UI 层）：① 迷你播放条 34dp → **16dp 圆角矩形**，底部进度线内缩 20 → 16dp 对齐新圆角；② 音乐库「歌手」「专辑」**位置互换**（歌曲/歌手/专辑/文件夹）；③ 歌手 Tab 删 2 列网格改 **Primuse 式列表 + 顶部搜索框**（44dp 圆形头像、`N 张专辑 · N 首`、行高 64dp、名字即时过滤、搜索词提到页面级 `rememberSaveable`、未知艺术家沉底 + 字母序、点行仍直接播放该歌手全部歌）；④ `LyricsScreen.kt` 整文件重写 **恢复 msop 原始歌词界面并居中**（42dp 封面信息行、当前句 28sp 加粗居中 + 柔光、邻行空间透视淡出、视口正中锚点、上下 12% `DstIn` 渐隐、逐字卡拉 OK / 点行 seek / 手动 5s 宽限；`PlayerSheet` 顶栏与进度控制条两页常驻） | 用户 2026-11-15 第五轮四条指令（歌手列表形态与搜索框参照 `chenqi92/primuse` `ArtistListView.swift`，AGPL-3.0-only，D26；歌词恢复目标为 <https://github.com/longda33/msop> HEAD 的 `LyricsPane`/`LyricLines`）。真机 `127.0.0.1:5555` 深/浅两主题验收；`test` + `assembleDebug` 全绿；详见 `05-progress.md` 同日第五条 |

| 2026-11-15 | **第六轮五条需求落地**（均为 UI 层，无任务勾选变化）：① 歌词页右上角「喜欢」删掉改**三个点**，`DropdownMenu` 与三点同居一个 `Box` → 弹窗贴着三点弹出（菜单：收藏/取消收藏、切换为横屏、重新加载歌词）；② 歌词区新增**横竖屏切换按钮**（`PlayerIcons.Rotate`，图标照 `music-web-cf-main` 的 `ICON.land` SVG；竖屏→`SENSOR_LANDSCAPE`、横屏页→`PORTRAIT`）；③ 歌词显示效果换成 `music-web-cf-main` 全屏歌词规格（字号 `clamp(24,3.2vmin+15,46)` + `h/4.6`、1.32 行高/.42em 行距/700 字重、`.82`/`.75`→`1.0` 缩放 300ms 缓动、白色三档 30/72/100% + 22px 光晕、逐行 `(1-d)/0.25` 距离淡出、竖屏居中/横屏左对齐、逐字「已唱纯白/未唱 38%」），**背景色不用它的**；横屏同源复用；④ 音乐库「全部歌曲」标题随右上角筛选联动（全部歌曲/本机歌曲/云端歌曲 + 副标题「共 N 首」）；⑤ `LyricsViewModel.reload()` 供菜单「重新加载歌词」使用（`load()` 同曲幂等早退） | 用户 2026-11-15 本轮五条指令（附件 `music-web-cf-main.zip` 为用户自有项目，D26 可直接采用，来源行号写进 `LyricsScreen.kt` 文件头）。真机 `127.0.0.1:5555` 验收：筛选三档标题齐全、三点菜单贴三点、旋转 竖→横→竖（`SurfaceOrientation` 1/0）、点行 seek `position=237660ms`、深浅两主题；`test` 28 类 231 例 0 失败、`assembleDebug` 通过；详见 `05-progress.md` 同日第六条 |

| 2026-11-15 | **第七轮八条反馈落地**（UI 层 + 解析层，无任务勾选变化）：① **歌词「重复行」根治**——数据层定位：设备上 `从前说 - 小阿七.flac` 内嵌 `lyrics=` 是方括号逐字 LRC（`[00:44.23]哭[00:44.55]着…`），旧 `LrcParser` 把每个方括号当一行、文本取整句，导致同句按字级时间点复制 20 次；改为「标签间有文字且 ≥2 截 → 合成一行逐字时间轴（`wordJoiner=""`）、标签间无文字 → 保留多时间点多行」，同一条修复即落地逐字歌词，新增 4 单测；② 横竖屏按钮挪进**独占一行**的工具条（`LyricsToolsRow` = 逐字胶囊 + target/swap/collapse/land 四圆钮，照 web `m2l-tools`），三点菜单删「切换为横屏」（横屏页删「切回竖屏」）；③ 删掉整个 chrome 3 秒淡出，**迷你控制常驻**；④ 控制卡改**白色半透明液态玻璃**（`White 0.34→0.12` + 白描边），`PlayerSheet` 遮罩大幅减淡并删 `LyricsScrim()`，背景透出彩色模糊封面；⑤ 曲库标题下方副标题删除（`subtitle = null`）；⑥ `TrackRow` 三点菜单挪进三点自己的 `Box` → 弹窗贴三点；⑦ 横屏按 web 5 列栅格重排（歌词列从 37.4% 屏宽起），三点并进工具条同一排；⑧ 逐字/普通胶囊 + 偏移微调弹窗（±0.1s/±0.5s/归零）落地。真机 `127.0.0.1:5555` 深浅两主题、横竖屏、逐字开关、两处三点菜单全部截图验收；`:feature:lyrics:testDebugUnitTest` 48 例全绿、`:app:assembleDebug` 通过；截图 `../preview/verification/2026-11-15-r7-*.png` | 用户 2026-11-15 第七轮八条指令（附件两张 Edge 截图 + `music-web-cf-main.zip`，用户自有项目 D26 可直接采用）；详见 `05-progress.md` 同日第七条 |
| 2026-11-15 | **第八轮四条反馈落地**（UI 层，无任务勾选变化）：① 歌词工具条删掉「全屏」钮（`PlayerIcons.Collapse` 那颗），`onExitLyrics` 参数随之从 `LyricsPage` / `LandscapeLyricsPane` / `PlayerSheet` 三处移除；② 横屏隐藏系统状态栏（新 `HideStatusBar`：`WindowCompat` + `statusBars`，`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，离场恢复，留 navigationBars 保返回手势）；③ 工具条与迷你玻璃卡改**悬浮**——横屏结构变「底层 Row + 上层两 overlay」（`TopEnd` / `BottomStart`），歌词列只让 46dp 顶距，不再被整行挤压；④ 竖屏逐字胶囊靠左（`LyricsToolsRow.spreadChip` → `Arrangement.SpaceBetween`）。真机验：竖屏胶囊 x≈90 / 右侧三钮，横屏状态栏消失 + 浮层就位 + 回竖屏状态栏恢复，深色复验后切回浅色；`test` 28 类 235 例 0 失败、`assembleDebug` 通过；截图 `../preview/verification/2026-11-15-r8-*.png` | 用户第八轮四条指令；详见 `05-progress.md` 同日第八条 |
| 2026-11-15 | **第九轮四条反馈落地**（UI 层 + 设置页状态，无任务勾选变化）：① **歌词浮层自动隐藏**——新增 `CHROME_IDLE_MS=3000` / `CHROME_FADE_MS=380` / `CHROME_TICK_MS=120`、`ChromeVisibility` + `rememberChromeVisibility(paused)`（`lastTouch` 120ms 轮询，暂停/浮层打开恒显）、`Modifier.wakeChrome()`（Initial pass 只观察 pointer 事件不消费）、`ChromeOverlay`（380ms 淡出 + 隐藏时吞点击）；竖屏工具条由「独占一行」改浮层（`padding top 52dp`），横屏工具条与玻璃卡各包一层 overlay，3 秒后一起淡出；② **横屏按 web 矮窗栅格重写 `LandscapeLyricsPane`**——13.4 份栅格（7.5% / 封面 25.4% / 4.5% / 歌词 52.2% / 右 10.4%）、封面 `min(列宽, H*0.56)`、左列 `Center` 分组、歌词 `lineMaxWidthEm=13` 用 `padding` 等价 `margin:auto`、工具条浮 `TopEnd`、玻璃卡浮 `BottomStart` 且宽＝封面宽（盖住歌名）；③ **「播放列表」改名「歌单」并挪到「文件夹」前**——曲库 5 Tab，`PlaylistTab` + `PlaylistDetailPage` 替换原全页 `PlaylistScreen`，`LibrarySecondary.PlaylistDetail(id)` + `detailPlaylistId`，文案全改「歌单」，右上角菜单只留「重新扫描曲库」；④ **重扫反馈**——`SettingsViewModel` 注入 `LocalScanScheduler` 暴露 `ScanState`，设置页行内显示「正在扫描… 已扫到 N 首（写入 M 条）」/「扫描完成 · 共 N 首 · 用时 X.X 秒」（8 秒回落）/失败重试，点击弹 toast。真机验：竖屏 3 秒淡出 + 触碰唤醒、横屏（`SurfaceOrientation: 1`）新栅格与双浮层 4 秒一起淡出、状态栏隐藏、曲库 Tab/菜单、歌单 Tab→详情→添加→移除→删除全链路、设置页 toast + 完成文案 + 8 秒回落；`test` 28 类 235 例 0 失败、`assembleDebug` 通过；截图 `../preview/verification/2026-11-15-r9-*.png` | 用户第九轮四条指令（规格逐条回查用户自有 `music-web-cf-main/index.html`：隐藏类 2732/3091、栅格 3265、13em 3270–3282、工具条 3169–3174、迷你条 3296–3300、JS 测宽 15324–15331）；详见 `05-progress.md` 同日第九条 |
| 2026-11-16 | **B 类任务落地：音源配置多实例化 + HTTP 明文 + 状态 + 手动删除**（P3-05 / P7-07 由 `[?]` 改 `[x]`）：① 数据层 `ServerConfigStore` / `WebDavConfigStore` 整文件重写为「`profile_ids` 有序列表 + `active_id` + `p_{id}_*` 前缀 + 按 profile 的 JWT/同步计数」，旧单配置一次性迁移成 `p1`；② `ServerConfig` / `WebDavConfig` 加 `id`/`name`/`rootPath`，新增 `ServerStatus`；③ `network_security_config` 放开明文，真正门禁挪到应用层 `allowPlainHttp` + `PlainHttpNotConfirmed`（D27）；④ 同步从「音乐目录」起 BFS + 单目录失败跳过（内网盘三个坏目录返回 500）；⑤ 配置页改「列表 + 编辑器」两层：行内 ⋮（编辑/测试连接/启用并同步/删除）、启用中徽章、状态行、曲库计数、明文二次确认弹窗；⑥ 切换/删除启用项先 `clearRemote()` 再同步 | 用户「开始B，支持HTTP 支持添加多个，状态 手动删除这些 你搞定」+ 5 组真实凭据。真机 PGEM10 验收：内网 OpenList http 60 首（远程流播 `PLAYING(3)`）、内网 WebDAV http 187 首、`dav.2dland.cn` https 3 首、koyeb 两台在本设备 `[::1]` 不可达显示 FAIL、多台列表/切换/删除/计数全部实测；详见 `03-openlist-api.md` §9 与 `05-progress.md` 同日条 |
| 2026-11-16 | **任务表状态收敛**：P1-01 / P1-07 / P4-04 由 `[?]` 改 `[x]`（三项的验收条件在历轮真机验收中已全部满足，只是从未翻勾），未完成项 14 → **11**（可做 3 / 等外部资源 7 / 阻塞 1） | 用户「A 更新」：只改状态与备注，不动代码；各自新备注里写清收敛依据 |
| 2026-11-16 | **第十轮（r11）落地：多台音源同时启用 + 云端时长/封面 + 歌单体系补全 + 歌词/设置收尾**（无新勾选变化，P3-05 / P7-07 语义扩展）：① 两台（OpenList 三台/WebDAV 三台）可**同时启用**，配置行菜单补「取消启用」，同步逐台跑并各自计数（LAN 60 + koyeb 23，汇总「扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 · 清理 0 条 · 26800 ms」）；② `RemoteDeduper` 跨服务器按规范化标题去重（保留体积更大者）、复同步幂等；③ OpenSubsonic 无 `duration` → 新增 `RemoteDurationProbe`（`Range` 取头 64KB 解析 FLAC STREAMINFO / WAV / MP3 / M4A，末尾 moov 二次取尾），两个 Syncer upsert 前合并旧 `durationMs>0` 防复同步清零，OPENLIST 实测 **83/83**；④ koyeb 改 `OpenSubsonic apiKey`（`/@rest`）补艺术家/专辑/封面（covers 11）；⑤ **Room v4**（`MIGRATION_3_4` 非 destructive）：`playlists.sortOrder` + 新表 `favorites(trackId PK, addedAt)`（CASCADE+索引）+ `FavoriteDao`；收藏虚拟歌单 `FAVORITES_ID=-1` 置顶、歌单列表与曲目长按手柄拖动排序（`ui/components/Reorder.kt`）、三点菜单全项带图标（查看文件信息/收藏/加入歌单→已加歌单/移动到其它歌单/从歌单移除/移除曲库）+ `PlaylistPickerDialog`；⑥ 歌手条目改「打开详情」（`ArtistDetailScreen`：头像 + 播放全部 + 曲目 + 同一套菜单）；⑦ 歌词页三点并入播放页顶栏（共用，删「切换为横屏」）、点歌词不再 seek、横屏 `FLAG_KEEP_SCREEN_ON`、工具条与玻璃迷你卡 3 秒自动淡出且悬浮、横屏布局照 web 矮窗栅格；⑧ 设置页 `SettingRow` / `SettingSwitchRow` 改 `heightIn(min=66dp)` + 副标题 `maxLines=3`（窄屏长提示词不再省略） | 用户本轮批量需求与 bug 清单（代理只写模拟器不进项目；设备口径更正为**安卓模拟器**）。验证：`test` debug+release **28 类 / 254 例 / 0 失败**、`assembleDebug` 20,287,668 字节装机冷启动无崩溃；模拟器 900×1600 逐项截图（截图在 `%TEMP%\r11shots\`）。遗留：WEBDAV 时长 53/182（该 `/dav` 服务端 GET 返回 403）、多台启用后的 track id 迁移未决、收藏虚拟歌单不支持「移动到其它歌单」。详见 `05-progress.md` 2026-11-16 第十轮条 |
| 2026-11-16 | **第十一轮（r12）**：旧歌单清空；三点菜单删收藏（收藏类目除外）/删「重新加载歌词」/加歌单换圆圈加号/歌单内删加入歌单；云端曲目「下载歌曲」+ 设置页自定义下载路径；OpenList/WebDAV「请求并发数」（0=默认 4，钳 1..16，BFS 与时长探测并行）；歌单多选批量（移除/移动/加歌单/下载）；播放页爱心改走 Room `favorites`（修不进收藏列表）；本地与云端同名合并本地优先（283→278）；迷你条/导航栏玻璃 0.72/0.74；横屏歌词补 4% 内边距 + 间距 7.5%（正文 40.5%→42.5%）；通知栏按 ShuYin 重做（自建 channel + MediaStyle 大封面三键关闭进度）。设备：`test` 28 类 235 例 0 失败、`assembleDebug` 20,015,144 B、模拟器逐项实测通过（详见 `05-progress.md` 当日第十一轮条） | 用户第十一批反馈 |

| 2026-11-16 | **第十一轮追加（通知栏/锁屏重做 + 三件遗留补验）**：① 通知卡改**自绘紧凑卡**——普通通知 + `DecoratedCustomViewStyle` + `cardBackground(tint)` 圆角底图（封面主色）+ 48dp 方形封面 + 歌名/歌手 + 三颗内联传输键，字色按 `Color.luminance` 自动深/浅；频道换 `msop_playback_v2`（DEFAULT、静音、锁屏可见）。已实测踩明 `MediaStyle` / `DecoratedMediaCustomViewStyle` 被 SystemUI 接管这条死路（`setCustomContentView` 无效），也确认 Convx 用的就是 Media3 默认 provider、仓库无自定义通知布局；② **SAF 自定义下载目录实选验证**：DocumentsUI 新建 `msop-music` → 授权 → 下载云端曲落盘 22,658,255 B，重扫后曲库出现本机条目；③ **WebDAV 并发框实测**发现 `WebDavConfigStore.upsert` 漏写 `FIELD_CONCURRENCY`（输入框形同摆设），补齐后 `p_p1_concurrency=8` 落盘；④ `:app:assembleRelease` 重打 **3,578,834 B**。全量 `test`（debug+release）0 失败，`assembleDebug` 20,016,296 B | 用户「锁屏 通知栏播放器改成截图中效果」+「继续完成还没验的三件事」+「截图来自 Convx 照抄 / 不要猜，检索 github」。详见 `05-progress.md` 2026-11-16 追加条与 `03-openlist-api.md` §9.8 |

| 2026-11-16 | **第十二轮（r12-b）· 12 条需求**：① 设置页下载路径行加常驻目录说明（按钮改「选择目录 / 更换目录」）；② 曲目菜单「查看文件信息」→「歌曲信息」，`TrackInfoDialog` 放开给播放页复用；「移除曲库」加二次确认弹窗；③ **Room v5 + 墓碑表 `hidden_tracks`**——曲目入库走 `@Upsert` 整行覆盖，加列必被重扫冲掉，所以独立表 + `TrackDao` 四个展示查询加 `AND id NOT IN (SELECT id FROM hidden_tracks)`，扫描路径不过滤；`removeFromLibrary` 改为「清歌单关联 + 清收藏 + 写墓碑」；④ 曲库右上角菜单新增「已移除歌曲」二级页（`RemovedTracksScreen`，行内「恢复」）；⑤ 收藏虚拟歌单曲目菜单删「从歌单移除」（`onRemove`/`onRemoveFromPlaylist` 置 null）；⑥ 播放页顶栏菜单删「收藏」→「歌曲信息」+「下载歌曲」（云端曲才出现）；⑦ 歌词偏移 `AlertDialog` 改**页面内非模态玻璃浮层** `LyricsOffsetPanel`（照 `music-web-cf-main` `buildCalPop`，滑杆 + 五步进钮 + ✕，不遮歌词）；⑧ 歌词横屏迷你卡垫色对齐迷你条（`FAFAFA 0.72` / `121212 0.74`）；⑨ 歌单副标题与信息卡删「· N 分钟」；⑩ 两个音源编辑器并发数加常驻 `note` 说明（`ConfigField.note`：留空=4 / 填 1 最稳 / 内网 8~16 / 429·5xx 降到 2~3 / 范围 1~16）；⑪ 设置页顶部文案改「本机 + OpenList + WebDAV」 | 用户第十二批反馈。模拟器 `127.0.0.1:5555` 逐条截图验收：移除 279→278 → 重扫仍 278 → 已移除页恢复回 279；收藏菜单 / 播放页菜单（本地·云端）/ 偏移浮层 / 横屏玻璃卡 / 设置页文案与说明全部实测；`test` 254 例 0 失败、`assembleDebug` 20,287,537 B、`assembleRelease` 3,595,218 B（8m17s）。详见 `05-progress.md` 第十二轮条 |

| 2026-11-16 | **第十三轮（r13）· 7 条反馈**：① 曲库重复（`Love Paradise` 三次）——`TrackMerger` 改候选键集合（原样 `标题|歌手` + 把「歌名 - 歌手」拆开后再算一次，两边有交集即同曲），并新增同源去重 `键|时长|体积`；移除曲库改 `twinsOf` 连坐同歌全部副本、恢复一起复原（新增 `TrackDao.all()`）；实测 279→264。② 封面白块——`LocalMetadataEnricher.repairBrokenCovers()`（`CoverCache.existing` 判存活 → `clearCover` → 立刻 `warmCovers`）+ 首屏先修后补；UI `TrackRow.usableCoverUri()` 对 `file://` 查文件存在；实测 17 张本地封面回血。③ 下载无反馈——新增 `DownloadCenter`（StateFlow 任务 + 字节进度 + 完成停 4s/失败停 6s + 在途去重），`RemoteFileDownloader.download(onProgress)` 每 256KB 回调，新增 `DownloadTaskCard` 挂迷你条上方（投影 20dp 故留 22dp 间隙）；实测 `198.5MB/212.0MB · 93%` → 落盘 222,306,561 B。④ 删掉设置页「下载路径」常驻提示词。⑤ 已移除页空白点击误触——`EnteringPage` 根 Box 挂无涟漪 `clickable` 吃空白点击。⑥ 文件夹加云端——`folderOf` 改 `来源标签:目录` 键（云端目录取 `scheme:profileId:/path` 第三段），`FolderList` 去掉 `localPath != null` 过滤，行内显示来源 + 目录。⑦ 通知栏/锁屏——克隆 `ponymusic` / `NeriPlayer` / `Convx` 逐个确认都只用 `DefaultMediaNotificationProvider` 或 `Notification.MediaStyle`，无人自绘，故截图那两张是系统 skin；用户选 **B 系统默认媒体大卡**，PlaybackService 改回 DefaultMediaNotificationProvider（渠道 msop_playback_v3，setSmallIcon 是实例方法、Builder 没有），删自绘 provider 与 nototif_player_small.xml，通知栏/锁屏实测为系统大封面卡 | 用户第十三批反馈。模拟器 `127.0.0.1:5555` 逐条实测（曲库 264、封面恢复、下载卡进度、空白点击不误触、文件夹含 OpenList 目录、设置页提示词已删）；`test` 33 个结果文件 / 264 例 / 0 失败。详见 `05-progress.md` 第十三轮条 |
| 2026-11-16 | **第十四轮（r14）· 5 条反馈**：① 搜索/设置页返回后导航栏滑块不跟随——`LiquidGlassNavBar` 的 `snapshotFlow{selectedTabIndex()}` 捕获了首次组合的 lambda 实例（闭包里路由永远旧值），另有一段效应把外部导航误当用户点击又 `onTabSelected` 与 `popBackStack` 打架；改 `rememberUpdatedState` 取最新 lambda、外部导航只 `animateToValue` 不动 navigate、点击/拖动入口显式回调、`onDragStopped` 分流 reselect。② 云端曲三点菜单「下载歌曲」整块上移到第一位（下载歌曲/歌曲信息/加入歌单/移除曲库）。③ 文件夹云端改按「设置里填写的音乐目录」聚合——`LibraryViewModel` 注入 `ServerConfigStore`/`WebDavConfigStore`（SharedPreferences 无 Flow，只能主动拉），`cloudRoots: StateFlow<Map<tag:profileId,rootPath>>` + `refreshCloudRoots()`；`folderOf(track, cloudRoots)` 先按 `remoteProfileId` 查表用 `normalizeCloudRoot(rootPath)`，查不到才回退 uri 子目录；实测定格为 `OpenList /yd/music`(23) + `/yd/musicweb`(42) + `WebDAV /cd18/Music`(181)。④ 歌单「移动到其它歌单」删掉（与加歌单重复）——`TrackMenuActions` 去 `currentPlaylistId`/`onMoveToPlaylist`，弹窗标题固定「加入歌单」，批量条的「移动」保留。⑤ 收藏歌曲三点菜单加「加入歌单」（走 `PlaylistViewModel.addTracksToPlaylist`），普通歌单仍不给 | 用户第十四批反馈。`:app:assembleDebug` 21,143,938 B 装机逐条实测：返回滑块跟随、云端只 3 条目录、云端曲首项下载歌曲、普通歌单无移动、收藏曲含加入歌单并落库 BatchTest（**拉库必须连 `msop.db-wal` 一起**，只读主库会误判没写入，测试条目已还原）；截图 `%TEMP%\r14shots\r14-01…r14-16*.png`，`test` 0 失败；并补验 r13 欠的下载卡两态（DONE 绿 ✓「已保存到 …」、FAILED 红 ✕ 红字，截图 `%TEMP%\r13shots\r15-bf-08.png` / `r15-fail-05.png`）。详见 `05-progress.md` 第十四轮条 |
| 2026-11-16 | **第十五轮（r16/r17）· A 组五项补验 + 通知栏耳机图标**：① P3-04 由 `[?]` → `[x]`（清理归零 / 同曲重播零 miss / 计费网络占用不涨 三条都拿到设备证据）；② P7-04 由 `[?]` → `[x]`（SAF 导出 → 删歌单 → 导入复活 + 坏 zip 拒绝且不伤数据，并把「密码不导出」这条错口径改成「按实现导出明文密码」）；③ P7-03 由 `[?]` → `[~]`（断网暂停 + 提示、恢复不续播、本地曲不受影响三项实测；「缓冲中…/网络较慢」仍缺低速网络条件）；④ P4-02 由 `[?]` → `[x]`（`Music` 一次查询同屏出本机行与 `musicweb` 云端行，36 首）；⑤ P5-01 保持 `[~]`，查明远程分支**完全未实现**且服务器无 `.lrc` 素材；⑥ 通知栏/锁屏小图标 `ic_stat_msop.xml` 由音符改为纯白耳机矢量（头梁弧 + 双耳罩 + 三电平柱，与桌面图标同形），仅改资源、Kotlin 未动，装机后通知栏与锁屏均确认生效 | 用户「做 A 组」+「通知栏 锁屏播放器 音符图标换成耳机」+「C 1 不用改，保持当前」。新增两条设备侧手法写进 `AGENTS.md` D29：`cmd wifi add-network <SSID> open -m` 造计费网络（不掉 adb）；断网场景用设备端 `nohup` 脚本自截屏自 dump 日志，Wi-Fi 恢复后 adb 自动回连再取证。截图 `%TEMP%\r13shots\r16-*`、`r17-*`、`r18-*`；代码改动仅 1 个 XML，APK 21,143,938 B 已装机。详见 `05-progress.md` 第十五轮条 |
| 2026-11-16 | **项目区整理（无功能改动）**：新增根目录 `INDEX.md`（一页目录导航：读文件顺序 / 文件地图 / 代码入口 / 状态快照 / 常用命令 / 归档约定），`AGENTS.md` 首段与 `README.md` 文档表均已指向它，接手模型不必通读 100 KB 的 AGENTS 与 1861 行 05-progress.md；根目录 10 张调试截图 + 4 份 uiautomator dump 移入 `../archive/2026-11-15-drag-debug/`，ShuYin 参考图移入 `../archive/reference-shuyin/`，改动包移入 `../archive/deliverables/`；删除 `.temp/`（491 文件 / 151.7 MB 反编译解包、raw 录屏、logcat dump、过程截图）；`.gitignore` 增 `.temp/`、`../archive/`、`.perch/`、`../preview/verification/` | 用户指令「新建目录MD文件，其它模型接手不用再全部都读取一遍；整理项目区文件，除主项目文件其它文件新建文件夹放入，删除没用的文件」。仓库总占用 627.6 MB → 475.8 MB，根目录文件数由 30 降到 18（只剩源码模块、构建文件与 7 份文档） |

> AI生成
