plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "com.kanvas.fx.gravity"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 24
    }
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
            api(project(":engine"))
        }
    }
}
