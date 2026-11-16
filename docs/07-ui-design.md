# 07 — 当前 UI 设计说明 v2.0（Aurora Glass）

> **状态：当前实现说明，不是提案** · 2026-11-12 重写
> 唯一事实来源是 `app/src/main/java/com/msop/player/ui/`，令牌以 `ui/theme/Theme.kt` 为准。
> 视觉基线：`preview/baseline/`（库页 / 播放中 / 播放页三张真机截图）。
> 历史设计稿（v1.0 原型 `docs/design/prototype.html`、`preview/index.html` 及各版 redesign HTML）已于 2026-11-16 **移出仓库**，归档在本地 `archive/design-proposals/`，仅用于溯源。
>
> **v1.0 已废弃**。重构前的「定稿提案」共 484 行，其中色彩、字号、圆角、组件路径、
> 横屏规格与当前实现全部不符，§9.2 的 9 条组件路径无一存在。本次删除了这些章节及配套
> 原型，只保留仍然有效的结构决策，并补上实测状态与优化待办。

---

## 1. 设计定位

一句话：**一个安静的私人唱片架，点开就是聆听室。**

不推荐、不社交、不卖歌。用户只有两个动作：找歌、听歌。

| # | 原则 | 落地方式 |
|---|---|---|
| 1 | 一屏之内可完成 | 播放页 / 队列 / 歌词都在同一 overlay 层，不进返回栈 |
| 2 | 手比眼快 | 高频动作集中在屏幕下 1/3；底部三 Tab + 胶囊导航 |
| 3 | 层次靠光，不靠框 | 深空墨底 + 两团低透明极光 + 玻璃卡片 1dp 高光描边；颜色只表达状态 |

> v1.0 第 3 条是「无渐变装饰、无动画炫技」，与 Aurora Glass 直接冲突，已删除。
> 现在的主张是：**允许渐变与动效，但只用于建立空间层次和表达状态，不做装饰噪音**。

三处有主张的实现，改视觉时不要轻易推翻：

1. `AmbientBackground` 用两团低透明径向极光代替纯黑，避免大块死黑显廉价。
2. `glassSurface` 用「深色渐变 + 1dp 白色高光描边」模拟玻璃，**不开 `RenderEffect`**——
   实时模糊在 API 31 以下无解，且滚动/切歌会掉帧。**P7-09 例外**：只有顶部标题栏
   （`HazeTitleScaffold`，面积小且只有一层）在 API 31+ 用 Haze 2.x 做实时背景模糊，
   API 30 及以下退化为纯色；卡片、封面、导航依旧不开模糊，避免整屏掉帧。
   - **源容器必须统一**：`hazeSource` 挂在 `HazeTitleScaffold` 内的稳定 `Box` 上，
     不要交给页面拼到 `AnimatedContent` 根节点。2026-11-14 真机实测：曲库页
     （`AnimatedContent` + `LazyColumn`）这样挂时滚动后只剩半透明 scrim，
     列表文字清晰透出并与标题重叠；设置页（`Column` + `verticalScroll`）却正常。
   - **可读性兜底**：模糊层给 `backgroundColor = Surface 92%` 垫色并加 55% 压暗 tint，
     即使源捕获失败或模糊降级也不透字；滚动内容首项必须用 `contentPadding`／`Spacer`
     按 `LocalHazeTopInset` 让开固定头，不能用 `Modifier.padding(top = ...)` 裁视口。
3. `GeneratedArtwork` 按专辑名 / 歌手名哈希出固定的极光渐变几何图，同一张专辑永远同一张，
   离线、零网络、零磁盘，替代早期那个灰底唱片图标。

---

## 2. 结构决策（沿用 v1.0，仍有效）

| 决策 | 结论 | 当前实现 |
|---|---|---|
| Q1 底部导航 | 音乐库 / 搜索 / 设置三个目的地 | `MsopApp.kt` 的 `TABS` + `BottomNav`，悬浮胶囊、高 62dp |
| Q2 列表封面 | 显示封面 + 无封面兜底 | `TrackRow` 用 48dp 封面，兜底走 `GeneratedArtwork`；紧凑模式**未实现** |
| Q3 播放页形态 | 全屏 overlay，不入返回栈 | `AnimatedVisibility` 包 `PlayerSheet`，返回键只收起浮层 |
| Q4 组件路线 | Material 3 + 自有视觉语言 | `MsopTheme` 恒定深色 `darkColorScheme`，不跟随系统 |

**歌单入口**不占底部导航，在音乐库页右上角。**搜索**独立成 Tab，因为本地 + OpenList 合并检索
是这个 App 的核心能力。这两点实现一致。

---

## 3. 实际视觉令牌

以下全部取自 `ui/theme/Theme.kt`，改令牌请改那里，不要在各页面写死。

### 3.1 色板 `Palette`

| 用途 | 令牌 | 值 |
|---|---|---|
| 页面底色 | `Ink` | `#07080D` |
| 播放页最底 / 状态栏渐隐 | `Void` | `#040509` |
| 卡片 / 导航 / 面板 | `Surface` | `#11131C` |
| 输入框 / 图标底板 | `Surface2` / `Surface3` | `#181B27` / `#222636` |
| 分割线 | `Line` | `#2A3042` |
| 主强调（极光紫） | `Blue` / `BlueLight` | `#6D5DFB` / `#9A8CFF` |
| 选中态蒙层 | `BlueWash` | `#2E7C6CFF` |
| 第二强调（薄荷青） | `Teal` | `#4FD1C5` |
| 语义色 | `Red` / `Green` / `Gold` | `#FF6B8A` / `#4EE0B0` / `#FFC46B` |
| 文字三级 | `TextPrimary` / `TextSecondary` / `TextTertiary` | `#F6F7FC` / `#A7ADC2` / `#6A7189` |

`MsopColorScheme` 把上述值映射到 M3 `darkColorScheme`；**全 App 恒定深色，不做浅色主题**。

### 3.2 字体 `MsopTypography`

系统默认字体，不打包第三方。层级靠字号 + 字重 + 负字距建立。

| 令牌 | 字号 / 行高 | 字重 | 字距 |
|---|---|---|---|
| `displaySmall` | 32 / 38 | Bold | -1.1 |
| `headlineLarge` | 28 / 34 | Bold | -0.9 |
| `headlineMedium` | 23 / 29 | Bold | -0.6 |
| `headlineSmall` | 20 / 26 | SemiBold | -0.3 |
| `titleLarge` | 18 / 24 | SemiBold | -0.2 |
| `titleMedium` / `titleSmall` | 15 / 20 · 13.5 / 18 | Medium | — |
| `bodyMedium` / `bodySmall` | 13.5 / 20 · 11.5 / 16 | Regular | — |
| `labelMedium` / `labelSmall` | 12 / 16 · 10.5 / 14 | Medium | 0.1 / 0.25 |

### 3.3 尺寸 `Dim` 与形状 `Shapes`

| `Dim` | 值 | | `Shapes` | 圆角 |
|---|---|---|---|---|
| `screen` | 20dp | | `coverSmall` | 11dp |
| `rowHeight` | 68dp | | `coverGrid` / `coverStage` | 18 / 30dp |
| `coverSmall` | 48dp | | `card` / `cardSmall` | 22 / 15dp |
| `miniHeight` / `navHeight` | 64 / 62dp | | `mini` / `sheetTop` | 20 / 30dp |
| `touch` | 44dp | | `pill` | 999dp |

`Gradients` 只保留实际在用的两条：`accent`（主按钮）、`accentSoft`（搜索选中背景）。
`teal` / `ember` / `coverScrim` 三条零引用渐变已于本次评审移除。

---

## 4. 组件与文件

UI 全部集中在 `app` 模块，**不按 feature 拆分**（`AGENTS.md` 已定决策，不要改模块边界）。
共 24 个文件 / 约 7,517 行。

| 文件 | 职责 |
|---|---|
| `MsopApp.kt` | 三 Tab 外壳、极光背景、迷你播放条、播放页 / 队列浮层 |
| `theme/Theme.kt` | 色板 / 字体 / 尺寸 / 形状 / 渐变令牌 |
| `components/Surfaces.kt` | `AmbientBackground`、`glassSurface`、`SectionHeader`、`PrimaryPill`、`GlassPill`、`CircleGlassButton`、`IconTile`、`InsetDivider` |
| `components/HazeTitle.kt` | P7-09 毛玻璃标题栏外壳：`HazeTitleScaffold`、`HazeTitleHeight`、`LocalHazeTopInset`；曲库 / 搜索 / 设置三页统一 |
| `components/TrackRow.kt` | 歌曲行、封面、空态、标题、文件信息弹窗、`artSeed()` |
| `components/MiniPlayer.kt` | 胶囊播放条 + 底部进度线 |
| `icons/Artwork.kt` | `GeneratedArtwork`、`ArtistAvatar` |
| `icons/PlayerIcons.kt` | 12 个自绘播放图标（不引 `material-icons-extended`） |
| `library/LibraryScreen.kt` | 五种视图：歌曲 / 最近 / 专辑 / 艺术家 / 文件夹 |
| `library/AlbumDetail.kt` | 专辑详情 |
| `player/PlayerSheet.kt` | 全屏播放页（封面 / 歌词两态、手势、模糊背景） |
| `player/QueueSheet.kt` | 队列面板与拖拽排序 |
| `playlist/PlaylistScreen.kt` | 歌单列表 / 详情 / 选歌 / 重命名 |
| `search/SearchScreen.kt` | 搜索与发现页 |
| `settings/SettingsScreen.kt` | 设置（含远程缓存占用与「仅 Wi-Fi 缓存」开关） |
| `settings/EqualizerScreen.kt` | 均衡器 |
| `settings/OpenListConfigScreen.kt` | OpenList 服务器配置与同步 |
| `permission/AudioPermissionGate.kt` | 权限门禁 |

> v1.0 §9.2 的「组件 → 文件」表指向 `feature/library/ui/`、`core/common/ui/UiTokens.kt` 等
> 9 个**不存在的路径**，已整节删除。上表是真实位置。

---

## 5. 实现状态与已知缺口

### 已落地

三 Tab、扫描入库后的五种列表视图、全屏播放页（封面 / 歌词两态 + 手势 + 逐字歌词）、
队列拖拽、歌单 CRUD、搜索与发现、设置、均衡器、OpenList 配置、程序化封面兜底、
远程播放缓存开关、睡眠定时、收藏。功能行为在 Aurora Glass 重构中保持不变。

### 已知缺口（2026-11-12 评审实测）

| # | 问题 | 依据 | 优先级 |
|---|---|---|---|
| 1 | **横屏完全未适配**：只有竖屏 `NavigationBar`，无 `NavigationRail` / 双栏 | v1.0 §7 写「真机是 1280×720，这节不是可选项」，实现未做 | P1 |
| 2 | 来源信息冗余：库页顶部「509 首 · 本机 509 · 云端 0」，两数重复、一数恒 0 | `baseline/library.png`；v1.0 §3.2 本就要求仅在混合列表显示 | P1 |
| 3 | 播放页底部功能行版本不一致：旧版 4 项（含队列），基线只剩 3 项 | `baseline/player.png` | P1 |
| 4 | 触摸目标低于 48dp：`CircleGlassButton` 42、`IconTile` 38、`PrimaryPill` 高 42 | `Surfaces.kt` / `MiniPlayer.kt` | P2 |
| 5 | 无障碍不全：47 处 `contentDescription` 中 18 处为 `null`，19 个 clickable 部分无语义标注 | 全量 UI 源码统计 | P2 |
| 6 | 尺寸令牌半失效：**466 处硬编码 `X.dp`** vs 48 次 `Dim.*`（9.7:1），另有 77 处硬编码 `Color(0x...)` | 全量 UI 源码统计 | P2 |
| 7 | 单文件偏大：`PlayerSheet.kt` 49KB、`PlaylistScreen.kt` 28KB、`LibraryScreen.kt` 26KB、`SettingsScreen.kt` 26KB | 文件大小 | P2 |

**令牌健康度是分化的**：`Palette` 被引用 351 次、`Shapes` 65 次，颜色和形状令牌是真正落地的；
失效的只有尺寸。所以方向不是「重建令牌体系」，而是把高频重复的 `X.dp` 逐步收回 `Dim`。

### 优化方案

- **P1（需真机定稿，本次未做）**：横屏三档断点（`NavigationRail` + ≥840dp 双栏）；
  云端为 0 时来源行收敛成「N 首」；播放页功能行定稿为 4 项并统一。
- **P2（打磨，本次未做）**：视觉尺寸不变、热区补到 48dp；给可点 `Row` 补语义标注；
  收拢高频 `X.dp`；把 4 个超大文件按私有 composable 内拆（**不动模块边界**）。
- **本次已完成**：删除 43 个失效过程截图（20.3 MB）与 58KB 历史原型、移除 3 个零引用渐变、
  `navHeight` 令牌落地、把本文重写为当前实现说明、归档 3 张视觉基线。

---

## 6. 视觉资产

| 路径 | 用途 |
|---|---|
| `preview/baseline/library.png` | 库页 · 空闲态（当前视觉基线） |
| `preview/baseline/library_playing.png` | 库页 · 播放中（当前行高亮 + 迷你条） |
| `preview/baseline/player.png` | 播放页 · 程序化封面 + 玻璃控件 |

历史设计稿（`index.html` / `library-redesign.html` / `icons-redesign.html` / `lyrics-redesign.html` 与 v1.0 原型）已移出仓库，本地归档在 `archive/design-proposals/`。

改视觉后请重截前三张覆盖，避免再出现「截图里新旧两版混放」。
`docs/05-progress.md` 里的历史记录描述的是当时发生的事，不随本文修订。

---

## 7. 验收建议（替代 v1.0 那份全空清单）

v1.0 §10 的 8 条清单从未勾选，且多条针对已废弃的浅色 / 双栏方案，已删除。
当前建议按以下顺序在真机核对：

- [ ] 浅色系统主题下 App 仍为深色，播放页返回键只收起浮层
- [ ] 1000 首列表快速甩动无白块，程序化封面不闪烁
- [ ] 字体放大 200% 后列表标题不被截断
- [ ] 横屏 1280×720 下布局（**当前未实现，见 §5 缺口 1**）
- [ ] TalkBack 能走完「选歌 → 播放 → 暂停 → 下一首」（**当前存疑，见 §5 缺口 5**）
- [ ] 改视觉后 `preview/baseline/` 三张截图同步更新

---

## 8. 版本记录

| 版本 | 日期 | 变化 |
|---|---|---|
| v1.0 | 2026-11-11 | 重构前定稿提案：Q1–Q4、浅/深双主题、靛蓝种子色、9 屏规格、横屏三档、Compose 落地映射。配套 `docs/design/prototype.html` |
| — | 2026-11-12 | Aurora Glass 视觉重构落地，v1.0 规格整体失效，仅在文件头加了免责说明 |
| v2.0 | 2026-11-12 | UI 评审后重写：删除 6 个失效章节与历史原型，令牌/组件表改为真实值，补实测缺口与优化待办，归档视觉基线 |
