plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.primuse.lite.feature.local"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // 同步逻辑是纯 Kotlin，但会调 AppLog -> android.util.Log，
            // JVM 单测里该方法是 stub，不置 true 会抛 "not mocked"。
            // 若将来需要真实 Android 行为，改用 Robolectric 并去掉此项。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
