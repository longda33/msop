# BUILD-NOTES — 首次构建必读

## ⚠️ 先说最重要的一句

**本工程尚未经过一次真实编译。** 生成环境里没有 JDK、没有 Gradle、没有 Android SDK，所以代码是按官方文档写的，但没有跑通过 `assembleDebug`。

第一次打开时如果报错，**先看本文第 4 节的降级方案**，不要急着改架构。文档里给了每个风险的 2 分钟回退路径。

## 1. 环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | **17 或以上** | AGP 9.5 最低 JDK 17；Android Studio 自带 JBR 21 可直接用 |
| Android Studio | 支持 AGP 9.5 的版本（2026 年 10 月之后） | 旧版本会提示 AGP 版本过高 |
| Gradle | **9.6.0** | 已由 wrapper 固定，首次同步会自动下载 |
| compileSdk | **37** | AGP 9.5 支持的最高 API |
| Build Tools | **36.0.0** | AGP 9.5 默认值 |
| minSdk | 26 | Android 8.0 |

## 2. 版本清单（2026-11-10 实采，非猜测）

| 组件 | 版本 | 来源 |
|---|---|---|
| AGP | 9.5.1 | Google Maven |
| Gradle | 9.6.0 | AGP 9.5 官方要求的最低版本 |
| Kotlin | 2.4.21 | Maven Central |
| KSP | 2.3.13 | Maven Central（已改为独立版本号，不再跟 Kotlin 前缀） |
| Compose BOM | 2026.09.00 | Google Maven |
| Media3 | 1.11.1 | Google Maven |
| Room | 2.8.5 | Google Maven |
| Hilt | 2.60.1 | Maven Central |
| Coil | 3.6.3 | Maven Central |
| OkHttp | 5.5.1 | Maven Central |

全部集中在 `gradle/libs.versions.toml`，升级只改那一个文件。

## 3. 首次构建步骤

```bash
# 1) 指定 SDK 路径（二选一）
cp local.properties.example local.properties
# 然后编辑 local.properties 填 sdk.dir

# 或直接设环境变量
export ANDROID_HOME=/path/to/Android/Sdk

# 2) 构建
./gradlew :app:assembleDebug

# 3) 装到设备
./gradlew :app:installDebug
```

Windows 下用 `gradlew.bat`。也可以直接用 Android Studio 打开根目录，让它自动同步。

**预期结果**：APK 装到手机后，看到底部三个 Tab（音乐库 / 搜索 / 设置），点击可切换，三个页面都是占位文案。

## 4. 已知风险与降级方案

### 4.1 风险 A：Hilt / KSP 与 AGP 9 内置 Kotlin 冲突

**症状**：同步时报 `Cannot add extension with name 'kotlin'`、KSP 任务找不到，或 Hilt 注解处理器不执行。

**为什么会有**：AGP 9 默认启用内置 Kotlin，KSP 与 Hilt 插件对新机制的适配是近期才完成的，版本组合存在不确定性。

**2 分钟回退**（先让工程能跑，P1 再补 DI）：

1. `app/build.gradle.kts` 的 `plugins {}` 里删掉两行：
   ```kotlin
   alias(libs.plugins.ksp)
   alias(libs.plugins.hilt)
   ```
2. 同文件 `dependencies {}` 里删掉三行：
   ```kotlin
   implementation(libs.hilt.android)
   ksp(libs.hilt.compiler)
   implementation(libs.hilt.navigation.compose)
   ```
3. `PrimuseApplication.kt` 删掉 `@HiltAndroidApp` 注解和 import
4. `MainActivity.kt` 删掉 `@AndroidEntryPoint` 注解和 import

### 4.2 风险 B：内置 Kotlin 本身有问题

**症状**：Kotlin 源码不参与编译、`kotlin {}` 扩展找不到、或编译产物缺类。

**回退到旧机制**：在 `gradle.properties` 追加两行

```properties
android.builtInKotlin=false
android.newDsl=false
```

然后在根 `build.gradle.kts` 和各模块 `plugins {}` 里加回 `org.jetbrains.kotlin.android` 插件（版本用 2.4.21），并在 `libs.versions.toml` 的 `[plugins]` 里补上定义。

> 注意：AGP 10 起将无法关闭内置 Kotlin，所以这只是临时手段，最终还是要在 AGP 9 上跑通。

### 4.3 风险 C：compileSdk 37 不存在或未安装

**症状**：`Failed to find target with hash string 'android-37'`

**处理**：SDK Manager 里装 Platform 37；若确实没有，把根目录所有 `compileSdk = 37` 降到 `36`，`targetSdk` 同步改 36。

### 4.4 风险 D：gradlew 脚本不是官方版本

本工程的 `gradlew` / `gradlew.bat` 是精简实现（只做「找到 java → 执行 wrapper jar」），`gradle-wrapper.jar` 取自 `gradle/gradle` 仓库 v9.6.0 标签，48 KB，与发行版一致。

如需官方完整脚本，在装了 Gradle 的机器上执行：

```bash
gradle wrapper --gradle-version 9.6.0
```

## 5. 工程结构

```
primuse-android/
├── settings.gradle.kts          12 个模块的 include
├── build.gradle.kts             根插件声明（全部 apply false）
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       唯一版本来源
│   └── wrapper/
├── app/                         单 Activity + Compose + Hilt
├── core/
│   ├── model/                   纯数据类（无 Android 依赖）
│   ├── common/                  工具、Result、日志
│   ├── database/                Room（P1-02 填充）
│   ├── network/                 Subsonic / WebDAV（P3 填充）
│   └── datastore/               设置项
└── feature/
    ├── local/                   MediaStore + SAF 扫描（P1 填充）
    ├── remote/                  OpenList 音源（P3 填充）
    ├── player/                  Media3 播放（P2 填充）
    ├── library/                 四种列表页（P1/P4 填充）
    ├── lyrics/                  LRC（P5 填充）
    └── settings/                设置页（P4 填充）
```

每个模块目前只有一个 `ModuleMarker.kt` 占位对象，用于验证模块被正确编译并接入依赖图。有真实实现后删掉即可。

## 6. 验证通过后要做的事

回到 `TASKS.md`，把 P0-01 / P0-02 / P0-03 的 `[?]` 改成 `[x]`，并在 `docs/05-progress.md` 追加一条记录，写清实际构建结果和遇到的问题。
