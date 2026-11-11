plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.primuse.lite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.primuse.lite"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // P6-02 再打开混淆；现在关掉以免 R8 规则干扰前期开发。
            isMinifyEnabled = false
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
