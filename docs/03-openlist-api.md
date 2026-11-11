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
GET {baseUrl}/rest/{method}.view?u={user}&t={token}&s={salt}&v=1.16.1&c=PrimuseLite&f=json
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
Q="u=$U&t=$T&s=$S&v=1.16.1&c=PrimuseLite&f=json"

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
            return "u=${config.user}&t=$token&s=$salt&v=1.16.1&c=PrimuseLite&f=json"
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
| D3 | 主通道用 Subsonic API | OpenList 源码存在 `subsonic.go`，音乐语义天然匹配，省掉自解析 ID3 |
| D4 | 保留 WebDAV 兜底 | Subsonic 实现完整度未知，WebDAV 一定能列目录 |
| D7 | 按公网 HTTPS 设计，base URL 支持 path prefix | 用户实际部署在公网反代后，非根路径 |
| D11 | 不缓存远程元数据到本地全量 | 只在首次浏览时拉取并入库，避免大库同步耗时 |
