# 03 — OpenList 对接方案

> **重要**：本文档区分「已从源码确认」与「待实测」两类信息。
> 标注 ⚠️ 的必须在 P3-01 中实测后回填，**不要凭文档假设直接写进代码**。

## 1. 三条可选通道

| 通道 | 状态 | 适用 |
|---|---|---|
| **Subsonic API** | ✅ 源码确认存在（`server/handles/subsonic.go`） | **首选**，面向音乐语义 |
| WebDAV | ✅ 源码确认存在（`server/webdav/`，含 litmus 测试） | 兜底，一定能列目录 |
| OpenList 原生 REST | ✅ 源码确认存在（`server/handles/fsread.go`） | 需要 raw_url / 缩略图时 |

### 1.1 公网部署的四个前提（本项目已确认按公网设计）

用户的实际部署是**公网域名 + HTTPS**，因此以下四条是硬前提：

1. **必须 HTTPS**。公网明文 HTTP 会让 Basic Auth / Subsonic token 直接暴露在链路上。
2. **base URL 可能带子路径**。反代常见形态：`https://music.example.com/openlist`。代码里 base 必须当作**可带 path prefix 的 HttpUrl** 处理，不能假设根路径。
3. **可能自签证书**。处理方式见 `02-architecture.md` §8.3。
4. **延迟显著高于局域网**。首屏要接受 300–800 ms，流播起播要预缓冲，超时值见 `02` §8.4。

> 调试阶段可以先在局域网用 `http://192.168.x.x:5244` 跑通，但**交付前必须用公网 HTTPS 地址复测一遍**——反代和证书是最容易在最后才炸的地方。


## 2. Subsonic API（首选）

### 2.1 基础约定

```
GET {baseUrl}/rest/{method}.view?u={user}&t={token}&s={salt}&v=1.16.1&c=Msop&f=json
```

`baseUrl` 示例：
- 局域网：`http://192.168.1.10:5244`
- 公网无反代：`https://music.example.com:5244`
- **公网带反代子路径**：`https://music.example.com/openlist` ← 最常见，也最容易写错

### 1.2 URL 规范化规则（必须在保存配置时执行）

用户输入什么都要能吃下，统一成规范形态再存：

| 用户可能输入 | 规范化结果 | 处理 |
|---|---|---|
| `music.example.com` | `https://music.example.com` | 缺协议，补 `https://` |
| `https://example.com/openlist/` | `https://example.com/openlist` | 去末尾斜杠 |
| `https://example.com/openlist//` | `https://example.com/openlist` | 去重复斜杠 |
| `http://192.168.1.10:5244` | 保持 | 局域网明文，放行但提示 |
| `https://example.com/openlist/index.html` | `https://example.com/openlist` | 用户误粘网页地址，剥掉尾部页面名 |

实现要点：

```kotlin
fun normalizeBaseUrl(raw: String): HttpUrl {
    var s = raw.trim()
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    val url = s.toHttpUrlOrNull() ?: throw IllegalArgumentException("地址格式不正确")
    // 剥掉误粘的页面路径，只保留 path prefix
    val cleaned = url.pathSegments
        .filter { it.isNotBlank() && it != "index.html" }
    return url.newBuilder().encodedPath("/" + cleaned.joinToString("/")).build()
}
```

**注意**：`addPathSegments("rest/ping.view")` 会正确处理 base 是否带尾斜杠，所以规范化后即使 base 末尾有斜杠也不会拼出 `//`。但为了日志可读，仍然统一去掉末尾斜杠。


- `token = md5(password + salt)`，`salt` 为任意随机串（建议 8–16 位）
- 响应用 `f=json` 返回 JSON；不加则返回 XML
- 统一外层：`{"subsonic-response": { "status": "ok" | "failed", ... }}`
- 失败时：`{"subsonic-response":{"status":"failed","error":{"code":40,"message":"..."}}}`

**⚠️ 待实测**：OpenList 的 Subsonic 挂载路径是否为 `/rest/`，是否需要额外开启开关。

### 2.2 需要用到的接口

| 方法 | 参数 | 用途 | 状态 |
|---|---|---|---|
| `ping` | — | 连通性测试 | ⚠️ 待实测 |
| `getArtists` | — | 艺术家索引 | ⚠️ 待实测 |
| `getArtist` | `id` | 某艺术家的专辑 | ⚠️ 待实测 |
| `getAlbumList2` | `type=newest\|alphabeticalByName\|random`, `size`, `offset` | 专辑分页 | ⚠️ 待实测 |
| `getAlbum` | `id` | 专辑详情 + 歌曲列表 | ⚠️ 待实测 |
| `getMusicDirectory` | `id` | 按目录浏览（兜底） | ⚠️ 待实测 |
| `stream` | `id`, `maxBitRate`（可选） | 音频流（需支持 Range） | ⚠️ 待实测 |
| `getCoverArt` | `id`, `size` | 封面 | ⚠️ 待实测 |
| `search3` | `query`, `songCount`, `albumCount`, `artistCount` | 搜索 | ⚠️ 待实测 |
| `getLyricsBySongId` | `id` | 歌词（OpenSubsonic 扩展） | ⚠️ 待实测，可能不支持 |

### 2.3 实测命令模板

```bash
# 公网带子路径的典型写法；局域网把 BASE 换成 http://192.168.1.10:5244
BASE="https://music.example.com/openlist"
U="admin"; P="你的密码"; S="abcdefgh"
T=$(printf "%s%s" "$P" "$S" | md5sum | cut -d' ' -f1)
Q="u=$U&t=$T&s=$S&v=1.16.1&c=Msop&f=json"

# 0) 先确认证书是否可信（自签会报 SSL 错误，此时才用 -k，仅用于诊断）
curl -sI "$BASE/rest/ping.view?$Q" | head -1
curl -skI "$BASE/rest/ping.view?$Q" | head -1

# 1) 连通性
curl -s "$BASE/rest/ping.view?$Q" | jq

# 2) 专辑列表
curl -s "$BASE/rest/getAlbumList2.view?$Q&type=alphabeticalByName&size=10" | jq

# 3) 某专辑详情（把上一步的 id 填进来）
curl -s "$BASE/rest/getAlbum.view?$Q&id=ALBUM_ID" | jq

# 4) 流播是否支持 Range（关键！）
curl -sI "$BASE/rest/stream.view?$Q&id=SONG_ID" -H "Range: bytes=0-1023" \
  | grep -iE "HTTP/|accept-ranges|content-range|content-type|location"

# 5) 封面
curl -sI "$BASE/rest/getCoverArt.view?$Q&id=ALBUM_ID&size=512" | grep -iE "HTTP/|content-type"
```

**第 4 条最关键**：若返回 `302` + `Location`，说明走临时直链，必须在 `ResolvingDataSource` 里处理；若返回 `206 Partial Content` 则直接可播。

### 2.4 客户端封装要点

```kotlin
class SubsonicApi(private val okHttp: OkHttpClient, private val config: ServerConfig) {

    private val authQuery: String
        get() {
            val salt = Random.nextBytes(8).toHex()
            val token = (config.password + salt).md5()
            return "u=${config.user}&t=$token&s=$salt&v=1.16.1&c=Msop&f=json"
        }

    suspend fun ping(): Result<Unit> = request("ping")

    suspend fun getAlbumList(type: String, size: Int, offset: Int): Result<List<AlbumDto>> =
        request("getAlbumList2", "type=$type&size=$size&offset=$offset")
            .map { it.albumList2.album }
}
```

**必须处理**：
- 错误码 → 中文提示映射（40 = 账号密码错误，50 = 无权限，70 = 未找到）
- 网络超时 15s，流播单独放宽到 30s
- 密码用 `EncryptedSharedPreferences` 存，不要明文

## 3. OpenList 原生 REST（备选，用于取 raw_url）

源码确认存在的接口（`server/handles/fsread.go`）：

```
POST /api/auth/login   { username, password }        → { code, data: { token } }
POST /api/fs/list      { path, password, page, per_page, refresh }
POST /api/fs/get       { path, password }            → { data: { raw_url, sign, size, ... } }
GET  /d/{path}?sign=...                              → 实际文件（支持 Range）
Header: Authorization: {token}
```

用途：当 Subsonic 的 `stream` 不好用，但需要精确控制直链时，用 `fs/get` 拿 `raw_url` 再交给 ExoPlayer。

**⚠️ 待实测**：字段名以官方文档 `doc.oplist.org` 与实际响应为准。

## 4. WebDAV 兜底

### 4.1 地址与鉴权

```
http://{host}:5244/dav/{存储名}/{子路径}
Basic Auth: OpenList 的账号密码
```

已验证的社区实践：`http://127.0.0.1:5244/dav/aliyundrive` 这类路径可直接被第三方工具挂载。

### 4.2 列目录

```http
PROPFIND /dav/音乐/周杰伦 HTTP/1.1
Host: 192.168.1.10:5244
Authorization: Basic {base64(user:pass)}
Depth: 1
Content-Type: application/xml

<?xml version="1.0"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/><d:getcontentlength/><d:getcontenttype/>
    <d:getlastmodified/><d:resourcetype/>
  </d:prop>
</d:propfind>
```

解析要点：命名空间是 `DAV:`，`<d:collection/>` 表示目录，`getcontentlength` 可能是字符串。

### 4.3 实现选择

| 方案 | 评价 |
|---|---|
| **dav4jvm**（`bitfireAT/dav4jvm`，MPL-2.0，104★，活跃） | 推荐。DAVx5 团队维护，PROPFIND 解析成熟 |
| 自写 OkHttp + XmlPullParser | 可控但重复劳动，除非 dav4jvm 依赖冲突 |

**注意**：dav4jvm 是 MPL-2.0，可安全链接进闭源或 GPL 项目，只需保留其文件级许可证声明。

## 5. 决策记录

| # | 决策 | 依据 |
|---|---|---|
| D3 | ~~主通道用 Subsonic API~~ **2026-11-11 修订为 OpenList 原生 REST** | 见 §6 实测：用户服务器未启用 Subsonic 且账号非管理员，无法开启 |
| D4 | 保留 WebDAV 兜底（本轮未实现） | WebDAV 实测可用，作为将来原生 API 变更时的备选 |
| D7 | 按公网 HTTPS 设计，base URL 支持 path prefix | 用户实际部署在公网反代后，非根路径 |
| D11 | 不缓存远程元数据到本地全量 | 只在首次浏览时拉取并入库，避免大库同步耗时 |

---

## 6. 2026-11-11 真实服务器实测（P3-01 验收）

用户提供了公网 OpenList 部署与账号（**凭据只用于本机实测，未写入任何仓库文件**）。
服务器 `OpenList v4.2.9 (Commit 0497ee8)`，根目录 23 个 FLAC 文件、无子目录、无封面。

本机默认代理对 `*.koyeb.app` 返回 `CONNECT tunnel failed, 502`，直连超时；
**唯一可用的探测通道是本地代理 `http://127.0.0.1:10808`**，所有 curl 都带
`--proxy http://127.0.0.1:10808`。真机验证时给模拟器设了
`settings put global http_proxy 10.0.2.2:10808`（`10.0.2.2` 是模拟器访问主机的地址）。

### 6.1 三条通道结论

| 通道 | 实测结果 | 结论 |
|---|---|---|
| **Subsonic** | `/rest/*`、`/api/subsonic/rest/*`、`/subsonic/rest/*` 全部返回 8506 字节 SPA HTML；v4.2.9 `server/router.go` 显示真实路由是 `/@rest/*`，实测该路径 **404** | **不可用**。`server/handles/subsonic.go` 在 `MediaSubsonicEnabled()==false` 时直接 404；`/api/public/settings` 里 `media_enabled=false`，`/api/me` 显示账号 `role=0`（非 admin），`/api/admin/setting/list` 返回 403，无法自行开启 |
| **WebDAV** | `PROPFIND /dav/` + Basic → **207 Multi-Status**，XML 含 `displayname / getcontentlength / getcontenttype`；`GET /dav/<file>` → 302 跳 SharePoint 直链 | **可用但没选**。要解析 XML、跟 302；改 JWT Bearer 会 401 |
| **OpenList 原生 REST** | 登录 / 列目录 / 取直链 / Range 全部实测通过 | **选为主通道**（P3-02 落地） |

### 6.2 原生 REST 真实响应（截断）

```bash
# 登录：请求头是 Authorization: <jwt>，没有 Bearer 前缀
curl --proxy http://127.0.0.1:10808 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"<user>","password":"<pwd>"}'
# → {"code":200,"message":"success","data":{"token":"eyJhbGciOiJIUzI1NiIs..."}}

# 列目录
curl --proxy ... -X POST "$BASE/api/fs/list" -H "Authorization: $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"path":"/","page":1,"per_page":100,"refresh":false}'
# → {"code":200,"data":{"content":[{"name":"Butter-Fly - 和田光司.flac",
#     "size":34929358,"is_dir":false,"modified":"2026-11-02T08:36:47Z",
#     "sign":"","thumb":"","type":3}],"total":23,"provider":"unknown"}}

# 取直链
curl --proxy ... -X POST "$BASE/api/fs/get" -H "Authorization: $JWT" \
  -d '{"path":"/Butter-Fly - 和田光司.flac"}'
# → {"code":200,"data":{"name":"...","size":34929358,"sign":"",
#     "raw_url":"https://<sharepoint>/.../download.aspx?...&tempauth=v1.eyJ...",
#     "provider":"Onedrive Sharelink"}}

# 直链 GET 支持 Range（关键）
curl --proxy ... -H 'Range: bytes=0-1023' -o /dev/null -w '%{http_code} %{size_download}' "$RAW_URL"
# → 206 1024     注意：这个请求**不能**带 Authorization，实测加了会 401
```

其它实测：`POST /api/fs/other` → 业务码 500 `not implement`；`GET /d/<file>` → 500（需要 sign，而 list 的 `sign` 为空）；`thumb` 全空、同目录无图片，**服务端不产出音频封面**。

### 6.3 由此定下的实现约束（写进代码了）

1. **绝不把 `raw_url` 存进数据库**——它带有时效 `tempauth`，过期后整个曲库都播不了。
   库里存稳定内部 URI `openlist:/路径`，每次起播（含 seek 重新打开 DataSpec）由
   `ResolvingDataSource` 调一次 `/api/fs/get` 换新鲜直链。
2. **直链 GET 不带任何 Authorization 头**，完全交给 Media3 的 DefaultHttpDataSource。
3. **Track.id 用路径 SHA-1**（`openlist:` 前缀），不是 URL、不是文件名。
4. **业务码与 HTTP 码都要判**：HTTP 200 也可能返回 `code=401 Guest user is disabled`；
   401/403 时清 token 重登一次。
5. `durationMs` 入库时是 0（服务端不给时长），播放页由 Media3 解析，列表里显示 `--:--`。
6. 无远程封面，`coverUri` 留 null，列表与播放页用默认占位，不造假数据。

### 6.4 真机验收（emulator-5554 / Android 12）

| 项 | 结果 |
|---|---|
| 配置页读入地址/账号/密码 | ✅ 显示「已配置」 |
| 保存并同步 | ✅ `扫描 23 项 · 写入 23 首 · 清理 0 条 · 454 ms`，DB `OPENLIST|23` |
| 远程歌与本地歌混排 | ✅ 搜索「Butter-Fly」命中 1 首，副标题 OpenList |
| 远程 FLAC 播放 | ✅ `state=3`，`CCodecConfig: audio/flac`，position 持续推进，无 ExoPlaybackException |
| 拖动进度条 seek | ✅ position `68129 → 208546` ms，仍 `state=3`，无 DataSource 异常（Range 与直链刷新都生效） |

---

## 7. 2026-11-11 晚 · Subsonic 启用后的复测（元数据增强）

用户随后在 OpenList 后台启用了媒体库并提供了**媒体应用密钥**（`olmp_` 前缀，凭据不入库）。
本节的结论直接决定了最终架构：**Subsonic 只补元数据与封面，流播仍走原生 REST**。

### 7.1 鉴权：只认 `apiKey`

`/api/public/settings` 的 `media_enabled` 从 `false` 变成 `true`，`/@rest/ping.view` 不再 404。
但鉴权方式和标准 Subsonic 不同（源码 `server/handles/subsonic.go` 的 `subAuthInner` 印证）：

| 方式 | 实测结果 |
|---|---|
| `apiKey=<应用密钥>` | ✅ `{"status":"ok",...}` |
| `u=app&t=md5(密钥+salt)&s=salt` | ❌ `code=40 Wrong username or password; use an app key as the password or enable password auth` |
| `u=app&p=<密钥>` | ❌ 同上（`p` 分支要求 `u` 对应的用户与该密钥属于同一账号，或管理员开启密码认证） |
| `u=&p=<密钥>` | ✅ 也通（源码：用户名为空时按应用密钥查用户） |

**客户端用 `apiKey` 参数**，不需要用户名、密码或 salt。

### 7.2 接口实测（`/@rest/*.view`）

| 接口 | 结果 | 关键字段 |
|---|---|---|
| `ping` | ✅ | `status=ok`，`type=OpenList`，`openSubsonic=true` |
| `getMusicFolders` | ✅ | `[{"id":"1","name":"Music"}]` |
| `getArtists` | ✅ | `index[].artist[]` 含 `id/name/albumCount/coverArt/album[]` |
| `getAlbumList2` | ✅ | 22 张专辑、23 首歌；`duration` 恒为 0 |
| `getAlbum` / `getSong` | ✅ | song 含 `title/album/artist/coverArt/size/contentType/suffix/path/albumId/artistId`；**没有 duration 字段** |
| `search3` | ✅ | **空 `query` + `songCount=500` 一次返回全部 23 首**（用它代替 22 次 `getAlbum`） |
| `getCoverArt` | ✅ | `HTTP 200 image/jpeg 129708 B`，真实专辑封面 |
| `getLyricsBySongId` | ⚠️ | `code=70 lyrics not found`（OpenSubsonic 扩展，服务器没歌词） |
| `stream` | ⚠️ | `302` 跳 `http://.../d/...?sign=...`，**明文** |

`stream` 不可用的原因很具体：跳转目标是 `http://`，而 App 的
`network_security_config.xml` 全局禁明文，Media3 跟随后会被系统拒绝；把 scheme
改成 `https://` 也不稳定（签名 URL 带未编码空格，且签名有时效）。所以**流播继续用
原生 REST 的 `/api/fs/get` 拿 HTTPS raw_url**。

另一个坑：Subsonic 的 `path` 是服务器绝对路径
（`/od-20200439/musicweb/xxx.flac`），直接喂给 `/api/fs/get` 返回
`{"code":500,"message":"object not found"}`；原生 REST 要的是相对用户根目录的
`/xxx.flac`。两边路径**不通用**，因此元数据匹配用**文件名**而不是路径。

### 7.3 最终架构

```
Subsonic（apiKey）  ──→  艺术家 / 专辑 / 专辑ID / contentType / 真实封面
                              ↓ 按文件名匹配
原生 REST（JWT）    ──→  目录扫描（稳定路径、幽灵清理）+ /api/fs/get 流播（HTTPS + Range）
```

- 未填应用密钥：行为和 §6 完全一致（无封面，专辑取目录名）。
- 填了应用密钥：同步后额外一次 `search3`，把 23 首的元数据补齐；失败只降级不阻断。
- 数据库里 `coverUri` 存的是**带 apiKey 的 getCoverArt URL**，交给 Coil 加载。

### 7.4 真机验收（emulator-5554 / Android 12）

| 项 | 结果 |
|---|---|
| 配置页新增「Subsonic 应用密钥（可选）」 | ✅ 45 位密钥完整输入，状态卡片显示「Subsonic 元数据已启用」 |
| 同步 | ✅ `扫描 23 项 · 写入 23 首 · 补全 23 首 · 清理 0 条 · 8476 ms` |
| 数据库 | ✅ `23 首 / 23 有封面 / 23 有艺术家 / 23 有 albumId` |
| 元数据正确性 | ✅ `Rage Your Dream · m.o.v.e · 頭文字D Final Best Collection`（原生 REST 只能给「OpenList」） |
| 封面渲染 | ✅ 真实 JPEG（129 KB），Coil 磁盘缓存写入 180 KB 文件 |
| 播放 | ✅ `state=3`，metadata `Butter-Fly, 和田光司, Butter-Fly`，无 `ExoPlaybackException` |

**封面渲染踩了两个坑（都表现为「静默不显示」）**：

1. Coil 3 的网络加载器是独立模块 `coil-network-okhttp`。只依赖 `coil-compose` 时，
   本地 `file://` 封面照常显示、远程 `https://` 封面静默失败，很容易误判成服务端问题。
   已在 `app/build.gradle.kts` 补上该依赖。
2. 补完依赖后 debug 正常，但 **release 包封面又变回占位图**：R8 把 Coil 通过
   `META-INF/services/coil3.util.FetcherServiceLoaderTarget` 注册的实现类删掉了，
   `shrinkResources` 连注册文件一起删。已改为在 `MsopApplication` 显式实现
   `SingletonImageLoader.Factory` 并 `add(OkHttpNetworkFetcherFactory())`（R8 能看到
   静态引用），`proguard-rules.pro` 里再 keep 一层兜底。

---

## 8. 2026-11-12 · P3-04 远程边播边缓存（SimpleCache）

§6 定下的「raw_url 不入库、每次起播现换」在加了缓存之后要补一条：
**缓存 key 只能用稳定的 `openlist:/路径`，绝不能用换出来的 raw_url**。
后者带 tempauth，`/api/fs/get` 每次返回都不同，拿它当 key 的话同一首歌
seek 一次就多一份缓存，永远命不中。

### 8.1 DataSource 链的顺序（本任务最关键的一处）

```
DefaultMediaSourceFactory
  └─ RoutingDataSource（按 scheme 分流）
       ├─ 本地 content:// / file:// → DefaultDataSource（不缓存）
       └─ 远程 openlist:/路径      → CacheDataSource
                                      ├─ 读：FileDataSource
                                      └─ 未命中：ResolvingDataSource
                                           └─ DefaultDataSource(https raw_url)
```

`CacheDataSource` 必须**包在** `ResolvingDataSource` 外面：

1. 它看到的是稳定的内部 URI，缓存 key 天然固定；
2. 缓存全命中时根本不会 open upstream，连 `/api/fs/get` 都不用调，
   二次播放是真正的「秒开 + 零 API 请求」；
3. seek 到未缓存区间时，upstream 才去换一条新鲜直链，Range 请求照常生效。

反过来把 `CacheDataSource` 放在里面（用 `DataSpec.key` 补救也行但更绕），
就得对每个请求解析一次直链，缓存命中也要先付一次网络往返。

### 8.2 「仅 Wi-Fi 缓存」的语义

开关只限制**写**，不限制**读**：蜂窝下已缓存的内容照常从磁盘读（反而省流量），
只是不再往磁盘写新内容。实现上是构造 `CacheDataSource` 时把写侧 `DataSink`
传 `null`，Media3 会走只读路径（未命中时 `releaseHoleSpan` 后直接走网络）。

判定用 `TRANSPORT_CELLULAR` 或「没有 `NET_CAPABILITY_NOT_METERED`」。
**注意 Android SDK 里没有 `NET_CAPABILITY_METERED` 这个常量**（只有正向能力
`NET_CAPABILITY_NOT_METERED`），第一版按记忆写了它，编译直接报 unresolved reference。

查不到网络能力时放行：边播边缓存只是把正在下载的字节顺手落盘，不会比不缓存
多耗一份流量；真正会烧流量的是主动预下载，本版本没做。

### 8.3 其它约束

- 本机曲目不进缓存：几百首本地文件会把 2 GB 配额挤空，读本地也没有流量问题。
- `SimpleCache` 必须是**进程内单例**，Media3 用目录锁防止两个实例同时打开同一目录
  把索引写坏；且不能随 `PlaybackService.onDestroy` release（服务会反复重建）。
- 清理缓存用 `Cache.removeResource(key)` 而不是直接删文件，否则 SQLite 索引会
  留下「说有、其实没了」的幽灵条目。
- `docs/05` 走查清单里的「二次播放 < 1 秒」「切到蜂窝不自动缓存」需要在有设备的
  环境里复验；本轮只做了编译、单测与 release 构建。

### 8.4 真机复验步骤（有设备时照跑）

```bash
export MSYS_NO_PATHCONV=1
DEV=127.0.0.1:5555

# ① 播一首远程 FLAC 约 10 秒，看缓存目录确实长出文件
adb -s $DEV shell run-as com.msop.player ls -l cache/media_cache/

# ② 第一次播放应能看到「远程缓存未命中，换取新鲜直链」；
#    退出重进同一首歌（命中缓存）时这条日志不应再出现
adb -s $DEV logcat -s Msop:V | grep -E "远程缓存|缓存策略"

# ③ 设置 → 存储，比较播放前后「远程播放缓存」的曲目数与占用字节

# ④ 关掉 Wi-Fi 只留蜂窝，播一首没听过的远程歌：
#    「远程缓存策略 ... 写入=false」，设置页占用数字不增长；
#    再播一首已经缓存过的歌，仍能正常播放且不涨流量
```

缓存 key 的稳定性由 `MediaCachePolicyTest` 的 5 个 JVM 用例覆盖（同一路径 +
不同 tempauth 必须得到同一个 key），这部分不需要设备即可回归。

---

## 9. 2026-11-16 · 多实例配置 + HTTP 明文 + 音乐目录（B 类任务）

### 9.1 「多台」的语义：可保存多条，单选启用

每类音源（OpenList / WebDAV）都能保存任意多条配置，但**同一时刻只有一条「启用中」**，
`load()` 仍只返回 `activeConfig()`。因此：

- 曲目 id 方案不变（`openlist:{sha1(path)}` / `webdav:{sha1(path)}`），不需要为每条
  profile 建独立 id 空间，Room 迁移与播放解析都不用改；
- 若改成「多台同时启用、曲库合并」，两台服务器上的同名路径会撞 id，必须先把 profile
  前缀写进 id，属于另一规模的改造（未做，见 `AGENTS.md` 待办）；
- 切换启用项或删除启用项时先 `syncer.clearRemote()` 再同步，否则旧来源的曲目会留在
  曲库里（WebDAV 实测：187 首 → 3 首，切回再同步回到 187 首）。

存储布局（`msop_openlist` / `msop_webdav` 两套 SharedPreferences，同构）：

```
profile_ids = "p1,p2,p3"        有序列表，决定列表顺序
active_id   = "p1"              当前启用的那条
p_{id}_name / _url / _user / _pass / _root / _appkey
p_{id}_status / _status_msg / _status_at   OK / FAIL / UNTESTED + 人话文案 + 时间
p_{id}_sync_count / _sync_at               每条 profile 各自的同步结果
p_{id}_allow_http                          该条是否被用户明文确认过
token_{id} / _token_profile                 JWT 缓存按 profile 分开，切换不串号
```

旧单配置键在 `init` 里一次性迁移成 `p1` 并写 `profiles_migrated=true`，旧键保留对照。

### 9.2 音乐目录（`rootPath`）与坏目录跳过

内网 OpenList 从 `/` 做全盘 BFS 时，`od-20200305` / `od-20200439` / `od-20200441`
三个目录服务端直接返回 **HTTP 500**，整次同步会失败、曲库清零。两手修：

1. 配置里加「音乐目录（可选）」，`RemoteLibrarySyncer.sync()` 从该目录起 BFS
   （`normalizeRoot()` 规整前导/尾斜杠，空则根 `/`）。列表行会显示
   `地址 · 音乐目录`，一眼看出这台指向哪个目录。
2. `client.list` 单目录 try/catch：`Auth` / `NotConfigured` 原样抛出，其它异常只记
   `AppLog.w` 并跳过该目录，`RemoteSyncStats.skippedDirs` 汇总进结果文案
   （「跳过 N 个异常目录」）。

### 9.3 HTTP 明文：系统层放开、拦在应用层（决策 D27）

`network_security_config.xml` 的 base-config 从 `cleartextTrafficPermitted="false"` 改为
`"true"`——network security config 是**静态的**，无法按用户运行时输入的域名动态放行，
而内网自建服务器（`http://172.207.160.128:5244`）必须支持。

真正的开关在应用层：每条配置有 `allowPlainHttp`，只有用户在保存 `http://` 时看到
二次确认弹窗（`确认使用明文 HTTP？`，正文强调账号密码会裸奔）并点「仍然继续」才置
`true`；`OpenListClient.requireConfig()` 与 `WebDavClient.ensureAllowed()` 见到 `http://`
且标记为 false 直接抛 `PlainHttpNotConfirmed`，请求根本发不出去。地址不带协议时默认补
`https://`，即默认仍优先加密。

这是对 `AGENTS.md` 硬约束第 7 条的偏差，已记为 D27；`usesCleartextTraffic` 仍未开。

### 9.4 真机验收（PGEM10 / Android 15 / 900×1600，2026-11-16）

> **设备口径更正（2026-11-16 晚）**：这台「PGEM10」其实是**安卓模拟器**——
> `ro.product.model=PGEM10` / `ro.hardware=OPPO` 是伪装值，`ro.kernel.qemu` /
> `ro.boot.qemu` 被清空；但 `ping 10.0.2.2` 通（QEMU NAT 网关，`ttl=255`）可判定为模拟器。
> 下文所有「真机」按模拟器理解。模拟器出网走宿主机代理 `10.0.2.2:10808`
> （`adb shell settings put global http_proxy 10.0.2.2:10808`，**仅设备侧设置，不进项目代码**）。

| # | 服务器 | 结果 |
|---|---|---|
| ① | OpenList koyeb `https://hon-willette-longke233-…koyeb.app` `/yd/music` | 配置已保存，`FAIL`：本设备解析到 `[::1]:443`（域名 `ping` 100% 丢包，非代码问题） |
| ② | OpenList 内网 `http://172.207.160.128:5244` `/yd/musicweb` | `OK`，明文二次确认 → 同步 **60 首**，远程流播 `state=PLAYING(3)` |
| ③ | WebDAV koyeb `/dav` `/yd` | 配置已保存，`FAIL`（同上，`[::1]:443`） |
| ④ | WebDAV 内网 `http://172.207.160.128:5244/dav` `/cd18/Music` | `OK`，同步 **187 首**（扫描 219 项 / 8069 ms） |
| ⑤ | WebDAV `https://dav.2dland.cn` `/` | `OK`，`连接成功，根路径下 3 项`，同步 **3 首**（275 ms） |

列表 / 菜单 / 状态 / 删除都已跑过：行内 ⋮（编辑·测试连接·启用并同步·删除，启用项则是
立即同步）、启用中徽章、`连接正常 · 已同步 N 首` + `曲库 N 首 · 上次同步 …`、删除非启用项
不动曲库（OpenList 仍 60 首）、`清除全部`。PC 端另用
`HttpClient` + `new HttpMethod('PROPFIND')` 复验了 5 台全部 200/207
（`Invoke-WebRequest` 不支持 PROPFIND）。

## 9.5 多台同时启用 + 时长/封面补全（模拟器 / 900×1600，2026-11-16 晚）

| 项 | 结果 |
|---|---|
| 两台 OpenList 同时启用 | `profile_ids=p2,p3`，两台都「启用中」；同步汇总「扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 · 清理 60 条 · 32907 ms」，各台计数独立（lan 60 首 / koyeb 23 首） |
| 复同步 | 「清理 0 条 · 去重 5 首 · 26800 ms」，不清零计数 |
| 跨服务器去重 | `RemoteDeduper` 按规范化标题合并（保留体积更大者）：OPENLIST `发如雪 (史诗版)` flac+m4a → 1 条；WEBDAV `你那么爱她` / `单车 李克勤` / `有你在 梁静茹` / `醉拳 (国语/粤语) 成龙` 各 2 → 1 |
| 时长 | OpenSubsonic 完全不返回 `duration`（`search3` / `getSong` / `getAlbum` 的 song 无该字段、`album.duration` 恒 0）→ `RemoteDurationProbe` 用 `Range: bytes=0-65535` 读头字节：FLAC STREAMINFO / WAV fmt / MP3 Xing 或首帧 / M4A mvhd（moov 在尾部再取尾），OPENLIST 达 **83/83**；复同步靠 `TrackDao.bySource()` 合并旧 `durationMs>0`，不被 0 覆盖 |
| 封面 | koyeb 机配 `OpenSubsonic apiKey`（`/@rest` 前缀）后补到 `covers|11`（该机索引只收 `/od-20200439/musicweb` 的 23 首，覆盖不全属服务端索引） |
| WebDAV 时长 | 内网 WebDAV 182 首里只有 **53 首**拿到时长：宿主机直连该 `/dav` 对文件 `GET` 一律 **403**（`PROPFIND` 207、匿名 401），探测拿不到字节 → 疑为服务端限制（`dav.2dland.cn` 未复验） |
| koyeb WebDAV | 仍 `FAIL`（设备内解析到 `[::1]:443`）；模拟器代理下未复验 |

设备 `tracks` 分布（2026-11-16 12:0x）：**283 首 = LOCAL 18（18 有时长）/ OPENLIST 83（83）/ WEBDAV 182（53）**。


## 9.6 并发数配置（2026-11-16 第十一轮）

- 数据层：ServerConfig / WebDavConfig 新增 concurrency: Int = 0，语义「0 = 用默认」；
  ffectiveConcurrency 统一钳到 1..16，默认 DEFAULT_CONCURRENCY = 4
  （常量在 core/datastore 的 ServerConfig.kt 顶部）。ServerConfigStore /
  WebDavConfigStore 加 FIELD_CONCURRENCY = "concurrency"，写进 p{id}_concurrency，
  删除 profile 时一并清理。
- 界面：两个 ConfigScreen 在「音乐目录（可选）」下方加
  ConfigField("请求并发数（可选）", hint="不填用默认 4；内网可调 8~16，公网建议 2~3", KeyboardType.Number)；
  ViewModel 的 concurrency 以 String 保存（空串 = 默认），onConcurrencyChange
  只留数字、最多两位，落库 	oIntOrNull() ?: 0。
- 同步侧：RemoteLibrarySyncer / WebDavLibrarySyncer 的目录 BFS 改成**按层并行**
  （coroutineScope { async { … }.awaitAll() } + Semaphore(effectiveConcurrency)），
  单目录内部仍逐页顺序请求；localDirs / 计数改 AtomicInteger，
  WebDAV 的 canClean 改 ailedDirectories.get() == 0；
  illMissingDurations（云端时长探测）同样按并发数并行。
- 实测（模拟器，两台 OpenList 同时启用，两处并发都留空 → 默认 4）：
  「同步完成：扫描 103 项 · 写入 83 首 · 2 台服务器 · 补全 11 首 · 去重 5 首 ·
  清理 1 条 · 30586 ms」（上一轮串行同口径 32907 ms / 26800 ms 波动）。

## 9.7 云端曲目下载（2026-11-16 第十一轮）

- core/network/RemoteFileDownloader：先经 StreamUrlResolver 拿直链，再用 OkHttp
  直连下载（带音源鉴权头已由 resolver 拼好）。
- 目标目录：DownloadConfigStore（SharedPreferences msop_download）存
  	ree_uri / 	ree_name；有 SAF 树就 DocumentsContract.createDocument 建文件
  （重名自动加 _2、_3），否则写 getExternalFilesDir(DIRECTORY_MUSIC)。
  失败统一抛 DownloadException，调用方 toast。
- 入口：曲库/歌单曲目三点菜单「下载歌曲」（仅 source != LOCAL 显示）+
  歌单多选底部栏「下载」（选了云端曲才亮）。
- 实测：单曲落盘 iles/Music/Its My Life Bon Jovi.flac 29,648,735 B；
  重复下载生成 …_2.flac；批量两首云端曲（29.6 MB + 22.6 MB）都成功，
  toast「下载完成 2 首」。
## 9.8 通知栏 / 锁屏播放器（2026-11-16 追加）

- 实现类：`feature/player/…/notification/ShuYinNotificationProvider.kt`（Media3
  `MediaNotification.Provider`，由 `PlaybackService.setMediaNotificationProvider` 装入）。
- 形态：**普通通知 + `Notification.DecoratedCustomViewStyle()`** + 自绘 `RemoteViews`
  （`res/layout/notif_player_small.xml`：`notif_bg` 底图 + 48dp 方形封面 + 歌名/歌手 +
  上一首/播放暂停/下一首）。底图是 `cardBackground(tint)` 画的圆角矩形位图
  （封面主色，360×72dp / 半径 18dp），字色按 `Color.luminance(tint) > 0.42f` 取深/浅。
- 频道：`msop_playback_v2`，`IMPORTANCE_DEFAULT` + `setSound(null)` + `enableVibration(false)`
  + `lockscreenVisibility=PUBLIC`。**换频道 id 才能改重要性**；LOW 频道的静音通知不上锁屏。
- 死路（勿再试）：`MediaStyle` / `DecoratedMediaCustomViewStyle` 一律被 SystemUI 接管成
  「大封面 + 波浪进度条」系统大卡，`setCustomContentView` 被忽略；`setColorized(true)`
  在锁屏又被重新套白底。所以卡片外观只有两种选择：系统大卡（能进锁屏媒体区、有进度）
  或自绘卡（外观可控、外层多一圈系统白底、传输键要靠视图自己的 PendingIntent）。
- 参考：`cosmictaserdev-creator/Convx` 的通知就是 Media3 的 `DefaultMediaNotificationProvider`
  （无自定义布局），用户截图里的紧凑卡是旧版 SystemUI 模板，不是该项目写的。
- 入口 Intent 全部走 `MediaController`（`Media3Session` 命令），因此卡片在锁屏上也能控制播放。