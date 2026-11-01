plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "com.kanvas.fx.engine"
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
            api(compose.runtime)
            api(compose.ui)
            api(compose.foundation)
        }
        androidMain.dependencies {
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
