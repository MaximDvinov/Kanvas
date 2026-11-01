@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    jvm()
    android {
        namespace = "com.kanvas.fx.planetSample.shared"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 24
    }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":engine"))
            implementation(project(":engineGravityBarnesHut"))
            implementation(project(":enginePhysics"))
            implementation(project(":engineWorldObjectsKit"))
        }
    }
}
