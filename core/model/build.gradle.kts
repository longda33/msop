plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.primuse.lite.core.model"
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

}
