# Primuse Lite for Android

一个只做两件事的安卓音乐播放器：**读本机音频文件** + **对接 OpenList 音乐库**。

不上架、不分发、单机自用。目标是「自己天天用」，不是「做一个产品」。

> **当前进度（2026-11-10）**：Gradle 多模块骨架已搭好（12 模块），**但从未成功编译过一次**；UI 方案尚未确认，界面代码一行未写。
> 接手开发请从 [`AGENTS.md`](AGENTS.md) 读起。

---

## 一、范围（硬边界）

| 做 | 不做 |
|---|---|
| 本机音频：**默认扫媒体库标准目录**，可追加 SAF 自定义目录 | 应用商店上架、软著、备案、隐私政策 |
| **公网 OpenList** 音乐库（Subsonic API 优先，WebDAV 兜底） | 各家网盘私有 SDK（115 / 百度 / 阿里云盘） |
| MP3 / AAC / M4A / FLAC / WAV / OGG / Opus | APE / WavPack / TTA / TAK / DSD / DTS-HD |
| 内嵌与外挂 LRC 歌词 | 逐字歌词、AI 翻译、海报生成 |
| 专辑 / 艺术家 / 文件夹三种浏览 | Android Auto、桌面小组件、跨设备同步后端 |
| 播放队列、播放列表、搜索 | 音乐视频、投屏、DLNA |

超出上表的需求，先改本文档再动代码。

## 二、为什么是「重写」而不是「移植 Primuse」

Primuse（[chenqi92/primuse](https://github.com/chenqi92/primuse)，253★）是纯 Apple 生态：

- 1954 个 Swift 文件、41.6 MB 源码、3126 次提交、2026-03-28 → 2026-11-07
- 依赖 SwiftUI / AVFoundation / MusicKit / CloudKit / CarPlay / WidgetKit，**Android 上一个都没有**
- 2026-11-03 起许可证从 MIT 改为 **AGPL-3.0-only**

结论：代码复用率为 0。只借鉴产品形态、数据模型与踩坑清单，**不抄任何代码和资源**。

## 三、为什么是「照搬成熟方案」

已实测验证（详见 [docs/01-research.md](docs/01-research.md)）：

- **播放与本地库** → 参考 NeriPlayer / Auxio / OuterTune 的模块划分与 Media3 用法
- **OpenList 对接** → 走 Subsonic 协议，不自己造 WebDAV 客户端
- **WebDAV 兜底** → dav4jvm（MPL-2.0，DAVx5 团队出品）

## 四、目录

> **如果这是你第一次接触本项目，并且要以「接手开发」的身份继续——直接去读 `AGENTS.md`，不要从 README 读起。**

```
primuse-android/
├── AGENTS.md               ← ★ 交接契约：状态、约束、下一步、禁止事项
├── BUILD-NOTES.md          ← ★ 动手构建前必读：版本表、环境要求、四条降级方案
├── TASKS.md                ← ★ 38 个任务，唯一进度真相
├── README.md               ← 你在这里，范围与背景
├── settings.gradle.kts     ← 12 个模块
├── build.gradle.kts
├── gradle/libs.versions.toml ← 唯一版本来源
├── app/                    ← 单 Activity + 三 Tab 骨架
├── core/                   ← model（已实现）/ common / database / network / datastore
├── feature/                ← local / remote / player / library / lyrics / settings（均占位）
├── scripts/check.sh        ← 完整性自检，无 JDK 也能跑
└── docs/
    ├── 01-research.md      ← GitHub 实测数据与选型依据
    ├── 02-architecture.md  ← 模块划分、数据流、公网安全、双通道扫描
    ├── 03-openlist-api.md  ← Subsonic 接口、URL 规范化、WebDAV 兜底
    ├── 04-reference-index.md ← 抄哪个仓库的哪个文件
    ├── 05-progress.md      ← 做过什么、踩过什么坑
    ├── 06-ui-spec.md       ← UI 草案（未定稿）+ 还原度评估
    └── reference/primuse-ios/ ← Primuse 官方截图 4 张
```

## 五、许可证

| 项目 | 许可证 | 对本项目的影响 |
|---|---|---|
| Primuse | AGPL-3.0 | 不复制代码，仅参考形态 |
| NeriPlayer / Auxio / OuterTune / RetroMusic | GPL-3.0 | 抄架构思路可以；**直接复制代码则本项目必须 GPL-3.0** |
| dav4jvm | MPL-2.0 | 可链接使用，文件级开源 |
| OpenList | AGPL-3.0 | 仅通过 HTTP 调用，不构成衍生作品 |

**当前策略：自用、不分发**，因此即使参考 GPL 项目也不产生分发义务。若日后要发布，先回来重读这一节。
