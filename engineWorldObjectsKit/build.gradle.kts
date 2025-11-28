plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.ui)
            implementation(project(":engine"))
            implementation(project(":enginePhysics"))
        }
    }
}

android {
    namespace = "com.kanvas.fx.worldkit"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}
