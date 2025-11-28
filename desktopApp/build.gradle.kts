plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(project(":engine"))
            implementation(project(":enginePhysics"))
            implementation(project(":engineGravityBarnesHut"))
            implementation(project(":engineWorldObjectsKit"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kanvas.fx.desktop.MainKt"
    }
}

tasks.register<JavaExec>("runCollisionDemo") {
    group = "application"
    description = "Run collision and basic physics demo"
    mainClass.set("com.kanvas.fx.desktop.CollisionPhysicsLauncher")
    val mainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (mainCompilation.runtimeDependencyFiles ?: files()) + files(mainCompilation.output.allOutputs)
}

tasks.register<JavaExec>("runPlatformerDemo") {
    group = "application"
    description = "Run platformer-style world demo with controllable player"
    mainClass.set("com.kanvas.fx.desktop.PlatformerDemoLauncher")
    val mainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (mainCompilation.runtimeDependencyFiles ?: files()) + files(mainCompilation.output.allOutputs)
}
