plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
        }
        androidMain.dependencies {
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.kanvas.fx.engine"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}
