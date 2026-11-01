plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "com.kanvas.fx.compose.dsl"
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
            api(project(":engine"))
            api(compose.runtime)
            api(compose.ui)
            api(compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
