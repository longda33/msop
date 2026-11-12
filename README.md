# msop

一个只做两件事的安卓音乐播放器：**读本机音频** + **对接自己的 OpenList 音乐库**。
自用、不上架；只读文件，不修改、不上传任何音频。

## 功能

| 领域 | 已实现 |
|---|---|
| 本机曲库 | MediaStore 标准目录 + SAF 自定义目录（可增可删）；双通道按路径哈希去重；增量扫描、幽灵清理、标签与封面补齐 |
| 远程曲库 | OpenList 原生 REST 同步与流播（HTTPS + Range，可拖动进度条）；Subsonic 应用密钥补艺术家 / 专辑 / 真实封面 |
| 播放 | ExoPlayer + MediaSessionService：后台播放、通知栏 / 锁屏 / 耳机键、音频焦点、拔耳机暂停、来电打断续播 |
| 队列 | 顺序 / 单曲 / 随机；长按手柄拖拽排序 |
| 歌词 | 同名 `.lrc` 优先，内嵌 USLT / Vorbis 兜底；当前行高亮、点击跳转、手动滑动 5 秒后回中 |
| 均衡器 | 标准 10 段曲线，按设备实际 band 数映射，7 个预设 + 自定义 |
| 其他 | 歌单持久化、搜索、封面缓存管理、电池优化白名单引导、release 自签名 + R8 |

支持 MP3 / AAC / M4A / FLAC / WAV / OGG / Opus，最低 Android 8.0（API 26）。

## 技术栈

Kotlin 2.4 · Jetpack Compose + Material 3 · Media3 1.11 · Room · Hilt · Coil 3 ·
OkHttp + kotlinx.serialization · WorkManager

构建：AGP 9.5 + Gradle 9.6 + Version Catalog（AGP 9 内置 Kotlin，不要再加 `kotlin.android` 插件）

## 模块

```
:app              单 Activity + 全部 Compose UI + 导航 + DI 装配
:core:model       Track / Album / Artist / SourceType
:core:common      日志、崩溃捕获、SAF 根目录
:core:database    Room 实体 / DAO / 迁移
:core:network     OpenList 原生 REST + Subsonic 元数据客户端
:core:datastore   服务器配置 + JWT 缓存
:feature:local    MediaStore + SAF 扫描入库
:feature:remote   远程曲库同步与元数据补全
:feature:player   ExoPlayer + MediaSessionService + 均衡器
:feature:lyrics   LRC 与内嵌歌词解析
:feature:library / :feature:settings   预留（UI 目前集中在 app 模块）
```

依赖方向 `feature:* → core:*`，**feature 之间不互相依赖**，共享逻辑下沉到 core。

## 构建

```bash
sh scripts/check.sh                   # 完整性自检（不需要 JDK）
./gradlew.bat test                     # JVM 单测
./gradlew.bat :app:assembleDebug       # 调试包
./gradlew.bat :app:assembleRelease     # R8 + 自签名
```

环境：JDK 17+、Android SDK（compileSdk 37 / build-tools 37.0.0）、`local.properties` 填 `sdk.dir`。

release 签名读根目录的 `keystore.properties`（与 `app/release.jks` 一起被 `.gitignore` 排除）。
换机器按 [`USAGE.md`](USAGE.md) §6 重新生成即可；没有密钥时构建会退回 debug 签名，不会失败。

## 文档

| 文件 | 内容 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | 交接契约：当前状态、硬约束、下一步、踩过的坑 |
| [`TASKS.md`](TASKS.md) | 分阶段任务与验收标准 |
| [`USAGE.md`](USAGE.md) | 使用说明：权限、目录、OpenList 配置、播放、构建 |
| [`docs/02-architecture.md`](docs/02-architecture.md) | 模块边界、数据流、公网安全、双通道扫描 |
| [`docs/03-openlist-api.md`](docs/03-openlist-api.md) | OpenList 实测：原生 REST、Subsonic、WebDAV 与决策依据 |
| [`docs/04-reference-index.md`](docs/04-reference-index.md) | 实现参考索引 |
| [`docs/05-progress.md`](docs/05-progress.md) | 进度日志与踩坑记录 |
| [`docs/07-ui-design.md`](docs/07-ui-design.md) | UI 设计与 Compose 落地映射 |
| [`docs/design/prototype.html`](docs/design/prototype.html) | 可交互 UI 原型（浏览器打开） |

## 说明

自用项目，不提供分发，不含任何音乐内容。音频只在本机与自己的 OpenList 之间流转。
