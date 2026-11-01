plugins {
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}


dependencies {
    implementation(project(":samples:planetSample:shared"))
    implementation(compose.ui)
    implementation("androidx.activity:activity-compose:1.13.0")
}

android {
    namespace = "com.kanvas.fx.planetSample.android"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.kanvas.fx.planetSample.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
