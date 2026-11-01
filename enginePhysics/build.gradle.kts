plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "com.kanvas.fx.physics"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 24
        withHostTest {}
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
