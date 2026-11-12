# Primuse Lite 使用说明

一个自用、不上架的安卓音乐播放器。只读本机音频 + 对接自己的 OpenList，**不修改、不上传任何音频文件**。
支持 MP3 / AAC / M4A / FLAC / WAV / OGG / Opus，最低 Android 8.0（API 26）。

---

## 1. 安装

```bash
# 开发包
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 正式包（自签名 + R8 混淆）
./gradlew.bat :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

**debug 与 release 签名不同**：互相覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
先 `adb uninstall com.primuse.lite` 再装。卸载会清掉曲库、歌单和服务器配置，
重装后需要重新添加目录 / 填一次服务器（音频文件本身不会被删）。

第一次启动会申请音频读取权限（Android 13+ 是 `READ_MEDIA_AUDIO`，12 及以下
是 `READ_EXTERNAL_STORAGE`），同时申请通知权限用于扫描进度。拒绝时显示引导页；
在系统设置里手动授权后回到 App 会自动放行。

---

## 2. 本机音乐

默认扫描 MediaStore 标准目录（Music 等），无需任何配置。授权后启动时自动跑一次增量扫描。

**添加自定义目录**（比如外接存储、非标准目录）：设置 → 音乐来源 → **本机目录 → 管理 → 添加目录**，
在系统文件选择器里选中目录并允许授权。授权会持久化，重启后仍有效。

**移除目录**：同一对话框里点目录右侧的删除图标。会同时释放 SAF 持久授权并触发一次全量重扫，
被移除目录里、其它来源又没有的曲目会从曲库消失。

**重新扫描曲库**：设置 → 重新扫描曲库。全量重扫会重新读标签与封面，并清掉文件已删除的幽灵条目。
增量扫描不会清理（看不到未变更文件，据此删除会误清整个曲库）。

> 同一个文件同时被 MediaStore 和自定义目录扫到时只入库一条（按绝对路径 SHA-1 去重）。

---

## 3. OpenList 服务器

设置 → 音乐来源 → **OpenList 服务器**，填三样：

| 字段 | 说明 |
|---|---|
| 服务器地址 | 直接填 `music.example.com` 也行，会自动补 `https://`；反代子路径写 `https://example.com/openlist`；保存时自动去尾斜杠、剥掉误粘的 `index.html?xxx#yyy` |
| 账号 | OpenList 用户名 |
| 密码 | OpenList 密码，存在应用私有目录，不上传 |
| Subsonic 应用密钥（可选） | `olmp_` 开头，在 OpenList 后台「媒体库 → 应用密钥」生成。**填了才有真实封面、艺术家、专辑**；不填只能拿到文件名，专辑取目录名 |

点 **测试连接** 只验证登录并列一次根目录；点 **保存并同步** 会递归扫描所有目录、
把音频写进曲库，与本机歌曲混排。同步结果显示「扫描 N 项 · 写入 M 首 · 清理 K 条 · 耗时」。
想换服务器或清空远程曲目，用底部 **清除配置与远程曲目**（本机歌曲不受影响）。

### 通道说明（重要）

实测这台服务器是 **OpenList v4.2.9**，客户端用两条通道配合：

| 用途 | 通道 | 说明 |
|---|---|---|
| 列目录、入库、流播 | OpenList **原生 REST**（`/api/auth/login` → `/api/fs/list` → `/api/fs/get`） | 直链是 HTTPS 且支持 Range，拖动进度条可用 |
| 艺术家 / 专辑 / 封面 | **Subsonic**（`/@rest/search3`、`/@rest/getCoverArt`，`apiKey` 鉴权） | 填了应用密钥才启用；失败只降级、不影响播放 |

Subsonic 的 `stream` 接口会 302 跳到 `http://` 明文直链，被 App 的禁明文策略拦掉，
所以**流播不用它**。完整实测记录见 `docs/03-openlist-api.md` §6（原生 REST）与 §7（Subsonic）。

- 数据库里存的是稳定的 `openlist:/路径`，**不存播放直链**——直链带有时效签名，
  每次起播（包括拖动进度条后重新打开数据源）都会重新取一条新的。
- 公网必须 HTTPS。`http://` 会先弹明文警告要求二次确认；即便确认，Android 的
  `network_security_config.xml` 默认仍禁止明文连接，请改用 HTTPS。
- 自签证书：把 CA 导入系统「用户凭据」后即可信任（配置里允许 user CA），**没有**做信任所有证书。
- 部分网络环境直连公网会被拦。开发机上验证时给模拟器设过
  `adb shell settings put global http_proxy 10.0.2.2:10808` 借道主机代理；
  真机请按自己的网络环境处理。

### 远程曲目的已知限制

1. **封面与艺术家/专辑需要填应用密钥**。不填时列表显示默认占位图、专辑取目录名；
   填了之后同步会多花几秒（本项目 23 首约 8.5s），换回真实封面与元数据。
2. 拿不到时长，列表里显示 `--:--`；开始播放后由 Media3 解析，播放页会显示正确总时长。
3. 远程歌词暂不支持（服务器的 `getLyricsBySongId` 返回 `lyrics not found`）。

---

## 4. 播放

- 点任意歌曲会把**当前整个列表**入队（国内播放器惯例），底部出现迷你播放条。
- 点迷你条展开播放页：拖动进度条 seek、封面/歌词双态切换、上滑看队列、下滑收起。
- 队列里**长按右侧手柄拖动**可改顺序，松手才真正重排（拖动中只高亮目标行）。
- 播放模式：顺序 → 单曲循环 → 随机，点播放页的模式图标轮转。
- 后台播放由 `MediaSessionService` 前台服务承担，通知栏/锁屏/耳机媒体键都能控制；
  拔耳机自动暂停、来电打断后挂断自动续播。
- 国产 ROM 可能冻结后台进程：设置 → 后台播放 → **电池优化白名单** 引导加入豁免；
  自启动等厂商开关在 **系统应用设置** 里手动打开。

### 歌词

同名 `.lrc` 优先，内嵌歌词（MP3 ID3 `USLT` / FLAC Vorbis `LYRICS`）兜底。
同步歌词会高亮当前行、自动滚动、点击跳到对应时间；手动滑动后暂停跟随 5 秒再回中。
没有时间标签的内嵌歌词退化为「歌词未同步 · 仅展示全文」。

### 均衡器

设置 → 播放 → **均衡器**。开关、7 个预设（平直/流行/摇滚/爵士/古典/低音/人声）、
逐段滑杆，改动实时生效并持久化。段数按设备实际报告的 band 数绘制（有些 ROM 只有 5 段），
保存用的是标准 10 段曲线，换设备也不会丢调音。

> 实现上在构造 ExoPlayer 时关闭了 audio offload，否则音频绕过应用进程直达 DSP，
> 均衡器会「开着却完全没效果」。Media3 1.11 用 `AudioTrackAudioOutputProvider`
> 上报不支持 offload 来实现（旧的 `setEnableAudioOffload` 已被删除）。

---

## 5. 存储与设置

设置 → 存储 → **封面缓存** 显示「N 张 · X MB」，点击可清理。封面按内容哈希落盘，
同一张专辑封面只存一份；清理后播放时自动重建，不影响音频文件。

---

## 6. 自己构建

环境：JDK 17+（本机 Microsoft OpenJDK 21）、Android SDK（compileSdk 37 /
build-tools 37.0.0）、`local.properties` 里填 `sdk.dir`。

```bash
sh scripts/check.sh                 # 不依赖 JDK 的完整性自检
./gradlew.bat test                   # 全部 JVM 单测
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleRelease   # R8 + 自签名
```

**release 签名**读项目根目录的 `keystore.properties`（与 `app/release.jks` 一起被
`.gitignore` 排除，不会入库）。换机器时重新生成一次：

```bash
keytool -genkeypair -v -keystore app/release.jks -alias primuse \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "<你的密码>" -keypass "<你的密码>" \
  -dname "CN=Primuse Lite, OU=Personal, O=Primuse, C=CN"

# 然后新建 keystore.properties：
# storeFile=app/release.jks
# storePassword=<你的密码>
# keyAlias=primuse
# keyPassword=<你的密码>
```

没有密钥文件的机器也能打出 release 包（会退回 debug 签名并照常跑 R8），
不会让构建直接失败。

---

## 7. 还没做的

- OpenList 的 WebDAV 兜底通道、远程流播的 `SimpleCache` 边播边缓存、仅 Wi-Fi 预加载开关。
- 远程封面与远程歌词（服务端当前也不提供）。
- 横屏专门适配、权限「拒绝」路径的真机验证（测试设备会自动授予权限）。
- 1 万首以上大库的滚动性能基准（当前 500 首量级已真机验证）。
- 删除音频文件内标签后同步不过来（为了不让重扫抹掉补出来的元数据，合并不覆盖旧值）。

任务清单与验收标准见 `TASKS.md`，历史踩坑见 `docs/05-progress.md`。
