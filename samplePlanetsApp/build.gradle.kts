plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":engine"))
            implementation(project(":engineGravityBarnesHut"))
            implementation(project(":enginePhysics"))
            implementation(project(":engineWorldObjectsKit"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation("androidx.activity:activity-compose:1.10.1")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kanvas.fx.sample.MainKt"
    }
}

android {
    namespace = "com.kanvas.fx.sample.planets"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kanvas.fx.sample.planets"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
