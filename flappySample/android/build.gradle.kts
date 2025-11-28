plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":flappySample:shared"))
        }
        androidMain.dependencies {
            implementation(compose.ui)
            implementation("androidx.activity:activity-compose:1.10.1")
        }
    }
}

android {
    namespace = "com.kanvas.fx.flappySample.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kanvas.fx.flappySample.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
