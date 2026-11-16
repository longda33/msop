plugins {
    alias(libs.plugins.android.library)
    // P7-02：播放快照用 JSON 落盘
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.msop.player.feature.player"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    // P3-04：「仅 Wi-Fi 缓存」开关偏好与网络计费状态判定
    implementation(project(":core:datastore"))
    // P7-02：按快照里的 id 回库重建播放队列
    implementation(project(":core:database"))

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    // SimpleCache / CacheDataSource 在 datasource，索引数据库在 database；
    // 显式声明，不靠 exoplayer 的传递依赖。
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
