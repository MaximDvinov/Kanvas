plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project(":engine"))
            implementation(project(":samples:flappySample:shared"))
            implementation(project(":samples:planetSample:shared"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kanvas.fx.effectsShowcase.desktop.MainKt"
    }
}
