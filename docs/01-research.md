# 01 — 调研报告（GitHub 实测数据）

> 所有数据通过 GitHub API 于 **2026-11-10** 实采，非记忆、非推测。
> 复查命令见文末。

## 一、源项目 Primuse

| 项 | 实测值 |
|---|---|
| 仓库 | `chenqi92/primuse` |
| Stars / Forks | 253 / 41 |
| 语言 | Swift（43.6 MB）+ C（5.9 MB） |
| Swift 文件数 | **1954 个**，41.6 MB，约 90–100 万行 |
| 总提交 | **3126 次** |
| 开发周期 | 2026-03-28 → 2026-11-07（约 7.5 个月） |
| 许可证 | **AGPL-3.0-only**（2026-11-03 从 MIT 切换） |
| 最大单文件 | `Primuse/Services/Library/MusicLibrary.swift` **18,558 行** |

**结论**：纯 Apple 生态（SwiftUI / AVFoundation / MusicKit / CloudKit / CarPlay / WidgetKit），Android 零复用。AGPL-3.0 意味着即使能抄也不能抄。**只能当产品蓝图看。**

## 二、安卓端候选项目（按相关度排序）

| 仓库 | Stars | 语言 | 许可证 | 最后提交 | 与本项目的关系 |
|---|---|---|---|---|---|
| **cwuom/NeriPlayer** | **4148** | Kotlin | GPL-3.0 | 2026-11-09 | ⭐ 最接近。Compose + Media3 + 本地库 + 歌词，模块化极好。**但 WebDAV 只用于同步** |
| OuterTune/OuterTune | 5448 | Kotlin | GPL-3.0 | 2026-10-12 | 本地文件 + 在线源双通道架构，UI 参考价值高 |
| RetroMusicPlayer | 5395 | Kotlin | GPL-3.0 | 2026-11-06 | 老牌本地播放器，列表/播放页交互成熟 |
| OxygenCobalt/Auxio | 4441 | Kotlin | GPL-3.0 | 2026-11-02 | 最干净的本地库 + Media3 实现，代码质量高 |
| hpp2334/ease-music-player | 64 | Kotlin/Rust | GPL-3.0 | 2026-10-27 | 唯一把 **WebDAV 当音源**的中等项目，但是 Rust 试验项目，不够成熟 |
| bitfireAT/dav4jvm | 104 | Kotlin | **MPL-2.0** | 2026-11-09 | WebDAV 客户端库，DAVx5 团队出品，可直接依赖 |
| ultrasonic/ultrasonic | 330 | Java | GPL-3.0 | 2022-07-08 | Subsonic 客户端，**已归档**，仅作协议参考 |

### 2.1 NeriPlayer 深入核查

```
4750 个 .kt 文件，39.6 MB Kotlin 源码
模块：modules/local(630) modules/playback(560) modules/download(567)
     modules/platform(358) modules/sync(310) modules/model(252)
     modules/database(87) modules/lyrics(37) modules/network(22)
topics: media3, jetpack-compose, exoplayer, webdav, equalizer,
        usb-exclusive, karaoke-lyrics, listen-together
```

**关键判断**：所有 WebDAV 代码（`WebDavApiClient.kt`、`WebDavSyncBackend.kt`、`WebDavSyncWorker.kt` 等 40+ 文件）位于 `modules/sync` 与 `data/sync`，用途是**同步设置与播放列表**，不是浏览/流播音源。

→ 所以：**不能 fork 它来满足「OpenList 音源」需求**。但它的 `modules/local`（本地库）与 `modules/playback`（播放内核）是可直接照搬的架构范本。

### 2.2 为什么不用 ease-music-player

它确实支持 WebDAV 作为音源，但：
- 64 stars，README 自述是「探索 Rust 应用开发」的试验项目
- Roadmap 还在 v0.4 插件支持、v0.6 UI 改进
- Rust + Compose 混合栈，学习成本高于收益

→ 只抄它的 WebDAV 音源交互设计，不抄实现。

## 三、OpenList 接口能力（源码实测）

`OpenListTeam/OpenList`（25.6k★，AGPL-3.0，Go）仓库中实际存在的服务端实现：

| 能力 | 源文件 | 对本项目 |
|---|---|---|
| **WebDAV 服务端** | `server/webdav.go` + `server/webdav/`（含 `litmus_test_server.go`） | ✅ 可用作兜底音源 |
| **Subsonic 兼容 API** | `server/handles/subsonic.go`、`subsonic_handlers.go` | ✅ **首选方案** |
| Emby 兼容 API | `server/handles/emby*.go` | 不需要 |
| S3 兼容 | `server/s3/`（含 `range.go`） | 不需要 |
| FTP / SFTP | `server/ftp.go`、`server/sftp.go` | 不需要 |
| MCP 服务 | `server/mcp/`（`fs_list` / `fs_get` / `fs_link`） | 不需要 |

**决定性发现**：OpenList 内置 Subsonic 兼容 API。Subsonic 是音乐服务的事实标准协议，语义天然面向「艺术家/专辑/歌曲/封面/流播」，比裸 WebDAV 列目录 + 自己解析 ID3 高效得多。

→ 决策 **D3**：OpenList 走 Subsonic API。详见 `03-openlist-api.md`。

## 四、被否掉的方案

| 方案 | 否掉原因 |
|---|---|
| Flutter 跨平台 | 音频后台播放与 MediaSession 在 Flutter 侧仍是插件转接，坑多；且参考项目全是原生 |
| 引入 FFmpeg 支持 APE/DSD | 范围已砍掉这些格式；引入需 +2 个月且 NDK 打包复杂 |
| 自己写 WebDAV 客户端 | 已有 dav4jvm 与 Subsonic 协议，重复造轮子 |
| 直接 fork NeriPlayer | 4750 个文件、39.6 MB，理解成本远超自建；且它的 WebDAV 不是音源 |
| 直接用 ease-music-player | 试验项目，成熟度不足 |
| KMP 共享 Primuse 逻辑 | Primuse 是 Swift，不是 Kotlin，无共享可能 |

## 五、复查命令

```bash
# 项目基础数据
curl -s https://api.github.com/repos/cwuom/NeriPlayer | jq '{stars:.stargazers_count,lang:.language,pushed:.pushed_at}'

# 确认 NeriPlayer 的 WebDAV 用途
curl -s "https://api.github.com/repos/cwuom/NeriPlayer/git/trees/master?recursive=1" \
  | jq -r '.tree[].path' | grep -i webdav

# 确认 OpenList 有 Subsonic 实现
curl -s "https://api.github.com/repos/OpenListTeam/OpenList/git/trees/main?recursive=1" \
  | jq -r '.tree[].path' | grep -iE 'subsonic|server/webdav'
```
