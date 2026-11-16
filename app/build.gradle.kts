import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // P7-04：备份清单/歌单/设置用 JSON
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// P6-02：自签名 release。keystore.properties 与 .jks 都不入库（见 .gitignore）。
// 没有密钥的机器上仍能打 release 包（退回 debug 签名），不会让 CI 直接失败。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val storeFilePath = keystoreProperties.getProperty("storeFile", "")
val hasReleaseKeystore = storeFilePath.isNotBlank() && rootProject.file(storeFilePath).exists()

android {
    namespace = "com.msop.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.msop.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // AGP 9 的 Compose Preview 任务需要编译后的 Android 资源，
            // 否则 `./gradlew tasks` 会在 :app:generateDebugComposePreviewRunfiles 阶段失败。
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9 会为每个变体生成 Compose Preview 资源，release 变体默认关闭单测，
// 导致 `./gradlew tasks` 在 :app:generateReleaseComposePreviewRunfiles 失败。
// enableUnitTest 声明在 HasUnitTestBuilder 上，beforeVariants 的静态类型看不到，需要强转。
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        (variantBuilder as? com.android.build.api.variant.HasUnitTestBuilder)
            ?.enableUnitTest = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.coil.compose)
    // Coil 3 的网络 fetcher 是独立模块：不加这个，远程封面（https）永远加载不出来，
    // 而本地 file:// 封面照常显示，很容易误判成服务端问题。
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // P7-09：标题栏毛玻璃。Haze 2.x 拆成 core（hazeSource 捕获）与 blur（hazeBlur 效果）两个模块。
    implementation(libs.haze.core)
    implementation(libs.haze.blur)

    // 2026-11-14：底部液态玻璃（Kyant0 Backdrop，Apache-2.0）。折射透镜/色差在
    // API 33+，磨砂+饱和度在 API 31+，更低版本由 onDrawSurface 不透明底兜底。
    implementation(libs.kyant.backdrop)

    // P3-04：设置页要读 MediaCache（其内部持有 SimpleCache），
    // 显式声明编译期可见，避免依赖 feature:player 的 implementation 传递。
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)

    testImplementation(libs.junit)

    // WorkManager 的 Hilt 集成：Application 要实现 Configuration.Provider 并提供 HiltWorkerFactory
    implementation(libs.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:local"))
    implementation(project(":feature:remote"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:lyrics"))
    implementation(project(":feature:settings"))
}
