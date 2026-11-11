# 02 — 架构设计

## 1. 分层

```
┌──────────────────────────────────────────────┐
│  :app   单 Activity · Navigation · Hilt 装配  │
└───────────────┬──────────────────────────────┘
                │
┌───────────────▼──────────────────────────────┐
│  feature 层（互不依赖，只依赖 core）           │
│  local · remote · player · library ·          │
│  lyrics · settings                            │
└───────────────┬──────────────────────────────┘
                │
┌───────────────▼──────────────────────────────┐
│  core 层（无 UI 依赖，可单测）                 │
│  model · database · network · datastore ·     │
│  common                                       │
└──────────────────────────────────────────────┘
```

**铁律**：`feature` 之间禁止互相 import。需要共享就下沉到 `core`。

## 2. 模块职责

| 模块 | 职责 | 不做什么 |
|---|---|---|
| `:app` | 单 Activity、导航图、Hilt 模块装配 | 不写业务逻辑 |
| `:core:model` | `Track` / `Album` / `Artist` / `SourceType` / `PlaybackMode` | 无 Android 依赖（纯 Kotlin） |
| `:core:database` | Room 实体、DAO、TypeConverter、迁移 | 不写查询之外的逻辑 |
| `:core:network` | Subsonic 客户端、WebDAV 兜底、鉴权拦截器 | 不碰数据库 |
| `:core:datastore` | DataStore 偏好：服务器配置、扫描目录、播放模式 | 不存大对象 |
| `:core:common` | 扩展函数、时间格式化、`Result` 封装、日志 | 不做业务 |
| `:feature:local` | MediaStore/SAF 扫描、元数据提取、写库 | 不画 UI |
| `:feature:remote` | OpenList 拉取、映射为 `Track`、写库 | 不画 UI |
| `:feature:player` | `MediaSessionService`、ExoPlayer、队列、缓存 | 不做列表 UI |
| `:feature:library` | 歌曲/专辑/艺术家/文件夹四种列表 | 不直接操作 ExoPlayer |
| `:feature:lyrics` | LRC 解析、滚动状态 | 不做播放控制 |
| `:feature:settings` | 设置界面 | — |

## 3. 数据流

### 3.1 本地扫描

```
MediaStore.Query / SAF Tree
      ↓ (分页 500，按 date_modified 增量)
TrackMapper → List<Track>
      ↓
TrackDao.upsertAll()
      ↓
Flow<List<Track>> → UI
```

封面单独走一条路：`MediaMetadataRetriever.embeddedPicture` → 写 `cacheDir/covers/{hash}.jpg` → `Track.coverUri` 存路径。**不要在查询时同步解码封面。**

### 3.2 远程（OpenList / Subsonic）

```
SubsonicApi.getAlbumList2() → List<AlbumDto>
      ↓
SubsonicApi.getAlbum(id) → List<SongDto>
      ↓
SongMapper → Track(source=OPENLIST, uri=streamUrl(songId))
      ↓
TrackDao.upsertAll()
```

播放时：`uri` 直接交给 ExoPlayer，由 `OkHttpDataSource` 带鉴权参数请求。

### 3.3 播放

```
UI 点击 Track
      ↓
PlaybackController.setQueue(List<Track>, startIndex)
      ↓
MediaSessionService ← MediaController
      ↓
ExoPlayer(DefaultMediaSourceFactory(ResolvingDataSource(OkHttpDataSource)))
      ↓
SimpleCache（远程边播边缓存）
```

`ResolvingDataSource` 的作用：远程 URL 若返回 302 到临时直链，在这里解析后再请求，避免鉴权头被带到第三方域名。

## 4. 数据模型

```kotlin
enum class SourceType { LOCAL, OPENLIST }

@Entity(tableName = "tracks", indices = [Index("source"), Index("album"), Index("artist")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: SourceType,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val durationMs: Long,
    val trackNo: Int?,
    val discNo: Int?,
    val year: Int?,
    val coverUri: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val localPath: String?,      // 仅 LOCAL
    val lastModified: Long,      // 增量扫描依据
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey val id: Long, val name: String, val createdAt: Long)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(val playlistId: Long, val trackId: String, val position: Int)
```

**ID 规则**：
- 本地：`"local:" + sha1(absolutePath)`（路径变则视为新文件，可接受）
- 远程：`"subsonic:" + songId`

## 5. 依赖清单

写入 `gradle/libs.versions.toml`，**版本号以创建时的最新稳定版为准，不要照抄此处**：

| 用途 | 坐标 |
|---|---|
| Compose BOM | `androidx.compose:compose-bom` |
| 导航 | `androidx.navigation:navigation-compose` |
| Media3 | `androidx.media3:media3-exoplayer` / `media3-session` / `media3-datasource-okhttp` / `media3-ui-compose` |
| Room | `androidx.room:room-runtime` / `room-ktx` + KSP `room-compiler` |
| Hilt | `com.google.dagger:hilt-android` + KSP `hilt-compiler` |
| Coil | `io.coil-kt.coil3:coil-compose` |
| 网络 | `com.squareup.okhttp3:okhttp` |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| WorkManager | `androidx.work:work-runtime-ktx` |
| DataStore | `androidx.datastore:datastore-preferences` |
| 安全存储 | `androidx.security:security-crypto`（存 OpenList 密码） |

**不引入**：FFmpeg、ExoPlayer 旧版、RxJava、Koin、Glide、Retrofit（Subsonic 请求简单，OkHttp 直写更可控）。

## 6. 关键实现约定

| 场景 | 约定 |
|---|---|
| 线程 | 所有 IO 走 `Dispatchers.IO`；DB 走 Room 自带调度；UI 只用 `StateFlow` |
| 错误 | 统一 `Result<T>`（自定义 sealed class），网络错误映射为中文提示 |
| 扫描 | 每批 500 条，批间 `yield()`；用 `WorkManager` + 唯一任务名防重入 |
| 缓存 | `SimpleCache` 上限 2 GB，LRU；设置页可清理 |
| 封面 | 统一 512×512，JPEG 质量 85，存 `cacheDir/covers/` |
| 播放器 | 全应用只有一个 `ExoPlayer` 实例，生命周期跟随 Service |

## 7. 已知风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| OpenList Subsonic 实现不全 | 部分接口 404 或字段缺失 | P3-01 先全量实测；缺失则降级到 WebDAV（P3-05） |
| 远程 302 到临时直链 | 鉴权头泄漏或 403 | `ResolvingDataSource` 拦截并剥离敏感头 |
| 大库扫描卡顿 | 首扫体验差 | 分页 + WorkManager + 进度通知 |
| 国产 ROM 杀后台 | 息屏断播 | 前台服务 + 电池优化白名单引导 |
| Media3 均衡器在 offload 下失效 | EQ 无效果 | 关闭 audio offload（`setEnableAudioOffload(false)`） |
| 反向代理挂子路径（如 `/openlist/`） | URL 拼接错，全部 404 | 用 `HttpUrl.newBuilder().addPathSegments()`，禁止字符串拼 |
| 自签证书握手失败 | 连接不上，用户以为地址填错 | `network_security_config.xml` 指定信任域 + 引导导入 CA |
| SAF 遍历几万文件极慢 | 扫描卡死几分钟 | 用 `ContentResolver.query(childrenUri)` 批量查，禁用 `DocumentFile.listFiles()` |

## 8. 公网访问与网络安全

用户部署在公网，以下三条是必须实现的，不是可选项。

### 8.1 强制 HTTPS

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">你的域名</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/openlist_ca" />  <!-- 自签时才有 -->
        </trust-anchors>
    </domain-config>
</network-security-config>
```

- `AndroidManifest` 的 `application` 上引用它，**不要**写 `usesCleartextTraffic="true"`
- 用户填 `http://` 时：弹窗警告「公网明文传输会导致密码泄露」+ 二次确认，记入设置项 `allowCleartext`

### 8.2 反向代理子路径

公网部署常见形态是 `https://music.example.com/openlist/rest/...`。所有 URL 必须走 `HttpUrl` 构建：

```kotlin
val base = "https://music.example.com/openlist".toHttpUrl()
val url = base.newBuilder()
    .addPathSegments("rest/ping.view")
    .addQueryParameter("u", user)
    .build()
```

**禁止** `"$base/rest/ping.view"` 这种拼接——`base` 带不带尾斜杠会出两种结果。

### 8.3 自签证书

两条路径，按用户实际情况选：

| 情况 | 做法 |
|---|---|
| 用户有域名 + 正规证书（Let's Encrypt 等） | 什么都不用做，系统信任链即可 |
| 用户自签 | 引导用户把 CA 证书（`.crt`/`.pem`）放到手机，App 内提供「导入证书」入口，写入 `res/raw` 不可行（运行时），改用运行时加载 `KeyStore` + 自定义 `X509TrustManager` |

**绝对不要**实现「信任所有证书」的 `TrustManager`——那等于关掉 HTTPS。

### 8.4 超时与流量

| 参数 | 值 | 理由 |
|---|---|---|
| 连接超时 | 15s | 公网 + 反代，比局域网宽 |
| 读超时（API） | 20s | 列目录可能慢 |
| 读超时（流播） | 30s | 起播缓冲 |
| 缓存上限 | 2 GB | `SimpleCache` + `LeastRecentlyUsedCacheEvictor` |
| 预加载 | 默认仅 Wi-Fi | 设置项可改「始终」，蜂窝下会烧流量 |

## 9. 本机扫描双通道

### 9.1 默认：MediaStore

标准目录（`Music/`、`Download/`、`Ringtones/`、`Podcasts/` 等）以及被媒体扫描器索引过的文件，走 MediaStore 最快：

```kotlin
resolver.query(
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
    null,
    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
)
```

- 支持 `LIMIT/OFFSET` 分页（API 30+ 原生支持，低版本用 `sortOrder + " LIMIT 500 OFFSET n"` 的 hack）
- 增量依据：`DATE_MODIFIED` 大于上次扫描时间戳
- 权限：Android 13+ `READ_MEDIA_AUDIO`；≤12 `READ_EXTERNAL_STORAGE`

### 9.2 补充：SAF 自定义目录

用户手动添加媒体库之外的目录（如 `/storage/emulated/0/我的音乐`）：

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
) { uri ->
    uri?.let {
        context.contentResolver.takePersistableUriPermission(
            it, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        saveTreeUri(it)  // 存 DataStore，重启后仍有效
    }
}
```

**性能关键**：`DocumentFile.fromTreeUri(...).listFiles()` 是递归 IPC，几万文件要几分钟。正确做法是对 children URI 直接批量查：

```kotlin
val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
resolver.query(childrenUri, arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_SIZE
), null, null, null)
```

一次 query 拿一层全部子项，比 `listFiles()` 快一个数量级。

### 9.3 去重（必须实现）

同一文件可能既在 MediaStore 又在 SAF 目录里。归一化规则：

| 来源 | 归一化 key |
|---|---|
| MediaStore | `DATA` 列（绝对路径），API 29+ 用 `RELATIVE_PATH + DISPLAY_NAME` 拼 |
| SAF | 通过 `DocumentsContract.getDocumentId()` 拿到的 `primary:Music/xxx.flac`，转成绝对路径 |

两者都归一化为**绝对路径**后取 `sha1`，作为 `Track.id`。扫描时先查库中是否存在该 id，存在则跳过。

> 无法归一化的情况（如第三方文件管理器的 provider）：退化为「按文件名 + 大小 + 时长」三元组判重，并在日志里标记。


