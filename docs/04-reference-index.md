# 04 — 参考实现索引

> 用法：做到某个任务卡住时，先来这里查「去哪个仓库、看哪个文件、搜什么关键词」。
> **带 ✅ 的是已用 GitHub API 核实存在的路径；带 🔍 的是给你检索关键词，路径以仓库实际为准，不要照抄本文。**

## 0. 先看这三份文档（省你两天）

| 文档 | 内容 | 为什么值得读 |
|---|---|---|
| ✅ NeriPlayer `docs/implementation.md`（24.7 KB） | 整体实现说明 | 一个成熟安卓音乐 App 的完整设计复盘 |
| ✅ NeriPlayer `docs/music-server-api.md`（31.8 KB） | 音乐服务器 API 对接 | 与 Subsonic/OpenList 对接高度重合 |
| ✅ NeriPlayer `modules/local/README.md`（15.8 KB） | 本地库模块设计 | 直接对应我们的 `:feature:local` |

---

## 1. Gradle 工程骨架（P0-01）

| 抄什么 | 去哪看 |
|---|---|
| 多模块 + Version Catalog + convention plugin | ✅ `cwuom/NeriPlayer` → `build-logic/convention/`、`gradle/libs.versions.toml` |
| 模块 `build.gradle.kts` 精简写法 | ✅ NeriPlayer → 任一 `modules/*/build.gradle.kts`（多数仅 0.6–2 KB） |
| Compose 编译参数 | ✅ NeriPlayer → `app/build.gradle.kts`（21.6 KB，最全） |

## 2. Compose + Navigation 骨架（P0-03）

| 抄什么 | 去哪看 |
|---|---|
| 单 Activity + 底部 Tab | 🔍 `OxygenCobalt/Auxio` 搜 `MainActivity`、`Navigation` |
| 底部播放条 + 上滑播放页 | 🔍 `OuterTune/OuterTune` 搜 `MiniPlayer`、`BottomSheet` |
| Material3 主题与动态取色 | 🔍 `RetroMusicPlayer` 搜 `MaterialTheme`、`dynamicColor` |

## 3. Room 数据层（P1-02）

| 抄什么 | 去哪看 |
|---|---|
| 音乐库表结构与索引设计 | ✅ NeriPlayer → `modules/database/README.md`（1.9 KB，讲清了设计取舍） |
| DAO 分页 + Flow | 🔍 `Auxio` 搜 `@Query`、`PagingSource` |
| 多来源统一 Track 模型 | ✅ NeriPlayer → `modules/model/src/main/java/.../data/model/`（252 文件） |

## 4. MediaStore 扫描与权限（P1-03 / P1-04）

| 抄什么 | 去哪看 |
|---|---|
| MediaStore 音频查询 + 分页 | 🔍 `Auxio` 搜 `MediaStore.Audio`、`ContentResolver.query` |
| SAF 目录树持久授权 | 🔍 `Auxio` / `RetroMusicPlayer` 搜 `OPEN_DOCUMENT_TREE`、`takePersistableUriPermission` |
| Android 13 `READ_MEDIA_AUDIO` 分支 | 🔍 `OuterTune` 搜 `READ_MEDIA_AUDIO` |
| 增量扫描（按 date_modified） | ✅ NeriPlayer → `modules/local/src/`（630 文件，最完整） |

## 5. 元数据与封面（P1-05）

| 抄什么 | 去哪看 |
|---|---|
| `MediaMetadataRetriever` 批量提取 + 缓存 | 🔍 `Auxio` 搜 `MediaMetadataRetriever`、`embeddedPicture` |
| Coil 自定义 Fetcher 加载封面 | 🔍 `OuterTune` 搜 `Fetcher`、`ImageLoader` |
| 封面落盘与 LRU 清理 | 🔍 `RetroMusicPlayer` 搜 `Glide`/`Cache`（它用 Glide，思路可迁移到 Coil） |

## 6. WorkManager 扫描任务（P1-06）

| 抄什么 | 去哪看 |
|---|---|
| 唯一任务 + 进度上报 + 中断恢复 | ✅ NeriPlayer → `modules/local/.../data/sync/work/` |
| 前台进度通知 | 🔍 `Auxio` 搜 `ForegroundInfo`、`setForeground` |

## 7. 列表页四种视图（P1-07）

| 抄什么 | 去哪看 |
|---|---|
| 歌曲/专辑/艺术家三 Tab 结构 | 🔍 `RetroMusicPlayer` 搜 `HomeFragment`、`LibraryFragment` |
| 文件夹树浏览 | 🔍 `Auxio` 搜 `Directory`、`TreeView` |
| 列表项复用与点击反馈 | 🔍 `OuterTune` 搜 `LazyColumn`、`ListItem` |

## 8. ExoPlayer + MediaSessionService（P2-01 / P2-02）

| 抄什么 | 去哪看 |
|---|---|
| `MediaSessionService` 标准实现 | ✅ NeriPlayer → `modules/playback/runtime/`（560 文件） |
| 通知栏与锁屏控制 | 🔍 `Auxio` 搜 `MediaSessionService`、`PlayerNotification` |
| `ResolvingDataSource` 处理重定向 | 🔍 `OuterTune` 搜 `ResolvingDataSource`、`DataSource.Factory` |
| OkHttpDataSource 加鉴权头 | 🔍 `OuterTune` 搜 `OkHttpDataSource`、`RequestProperties` |

## 9. 队列与播放模式（P2-03 / P2-04）

| 抄什么 | 去哪看 |
|---|---|
| 队列管理、随机/顺序/单曲 | ✅ NeriPlayer → `modules/playback/logic/`（119 文件） |
| 音频焦点与来电打断 | 🔍 `Auxio` 搜 `AudioFocusRequest`、`AudioManager` |
| 耳机拔出暂停 | 🔍 `RetroMusicPlayer` 搜 `ACTION_AUDIO_BECOMING_NOISY` |

## 10. 播放页 UI（P2-05 / P4-04）

| 抄什么 | 去哪看 |
|---|---|
| 播放页布局、模糊背景 | 🔍 `RetroMusicPlayer` 搜 `NowPlaying`、`blur` |
| 上滑展开队列 | 🔍 `OuterTune` 搜 `PlayerSheet`、`BottomSheetScaffold` |
| 进度条拖拽 seek | 🔍 `Auxio` 搜 `Slider`、`seekTo` |

## 11. 远程流播与缓存（P3-04）

| 抄什么 | 去哪看 |
|---|---|
| `SimpleCache` + `CacheDataSource` | 🔍 `OuterTune` 搜 `SimpleCache`、`CacheDataSource` |
| 缓存大小限制与清理 | 🔍 `OuterTune` 搜 `LeastRecentlyUsedCacheEvictor` |
| 远程源元数据映射 | ✅ NeriPlayer → `docs/music-server-api.md` |

## 12. 服务器配置与连通性测试（P3-06）

| 抄什么 | 去哪看 |
|---|---|
| 地址/账号/密码表单 + 测试按钮 | ✅ NeriPlayer → `app/src/main/java/.../ui/screen/tab/settings/dialog/SettingsWebDavDialogs.kt`（11.7 KB） |
| 凭据加密存储 | ✅ NeriPlayer → `WebDavSyncCredentialRecoveryTest.kt` 同目录的实现；另有 `WebDavTransportSecurity.kt` |
| HTTPS 与自签证书处理 | ✅ NeriPlayer → `app/src/main/java/.../ui/screen/tab/settings/dialog/WebDavTransportSecurity.kt`（2.1 KB） |

## 13. 导航与全局播放条（P4-01）

| 抄什么 | 去哪看 |
|---|---|
| Scaffold 内嵌 MiniPlayer | 🔍 `OuterTune` 搜 `Scaffold` + `MiniPlayer` |
| 跨页面播放状态共享 | 🔍 `Auxio` 搜 `MediaController`、`HiltViewModel` |

## 14. 搜索（P4-02）

| 抄什么 | 去哪看 |
|---|---|
| 本地 + 远程合并搜索 | 🔍 `OuterTune` 搜 `SearchViewModel`、`combine` |
| 防抖 | 🔍 任一仓库搜 `debounce(` |

## 15. 播放列表（P4-03）

| 抄什么 | 去哪看 |
|---|---|
| 新建/重命名/排序 | 🔍 `RetroMusicPlayer` 搜 `Playlist` |
| 拖拽排序 | 🔍 `Auxio` 搜 `reorder`、`Reorderable` |

## 16. 歌词（P5-01 / P5-02）

| 抄什么 | 去哪看 |
|---|---|
| LRC 解析（含时间轴、多时间标签） | ✅ NeriPlayer → `modules/lyrics/src/`（37 文件） |
| 滚动高亮与自动回中 | 🔍 `OuterTune` 搜 `Lyrics`、`LazyListState.animateScrollToItem` |
| 内嵌歌词读取（ID3 USLT / Vorbis） | 🔍 `Auxio` 搜 `USLT`、`LYRICS` |

## 17. 均衡器（P5-03）

| 抄什么 | 去哪看 |
|---|---|
| Media3 `AudioEffect` + 10 段 EQ | 🔍 `RetroMusicPlayer` 搜 `Equalizer`、`android.media.audiofx` |
| 关闭 audio offload 使 EQ 生效 | 🔍 搜 `setEnableAudioOffload(false)` |

## 18. 后台保活（P5-05）

| 抄什么 | 去哪看 |
|---|---|
| 前台服务常驻 | 🔍 `Auxio` 搜 `startForeground`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| 电池优化白名单引导 | 🔍 搜 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

## 19. 绝对不要抄的东西

| 内容 | 原因 |
|---|---|
| NeriPlayer 的 `modules/download`、`modules/listentogether` | 我们不需要下载与一起听，抄了只增负担 |
| NeriPlayer 的 WebDAV 同步实现（40+ 文件） | 那是同步用途，与音源完全不同，会误导架构 |
| 任何仓库的 `GLSL` / 着色器代码 | 我们是普通 UI，不需要 GPU 特效 |
| Primuse 的任何 Swift 代码 | AGPL-3.0 + 平台不兼容，双重不可用 |
| ease-music-player 的 Rust 部分 | 试验性技术栈，维护成本高 |

## 20. 检索命令

```bash
# 克隆参考仓库（只读，别提交进本项目）
git clone --depth 1 https://github.com/cwuom/NeriPlayer /tmp/ref-neri
git clone --depth 1 https://github.com/OxygenCobalt/Auxio /tmp/ref-auxio
git clone --depth 1 https://github.com/OuterTune/OuterTune /tmp/ref-outer

# 找实现
rg -n "MediaSessionService" /tmp/ref-auxio --type kotlin
rg -n "ResolvingDataSource" /tmp/ref-outer --type kotlin
rg -n "SimpleCache" /tmp/ref-outer --type kotlin
rg -n "READ_MEDIA_AUDIO" /tmp/ref-outer --type kotlin
```
