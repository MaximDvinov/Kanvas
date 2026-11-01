import org.gradle.api.publish.PublishingExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.maven.publish) apply false
}

val kanvasLibraryModules = setOf(
    ":engine",
    ":engineComposeDsl",
    ":enginePhysics",
    ":engineGravityBarnesHut",
    ":engineWorldObjectsKit",
)

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    if (path !in kanvasLibraryModules) return@subprojects

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        pluginManager.apply("com.vanniktech.maven.publish")

        val moduleDescription = when (path) {
            ":engine" -> "Core Kotlin Multiplatform 2D runtime for Compose applications."
            ":engineComposeDsl" -> "Compose-first declarative game DSL and host integration for Kanvas."
            ":enginePhysics" -> "Optional 2D physics systems and DSL extensions for Kanvas."
            ":engineGravityBarnesHut" -> "Barnes-Hut n-body gravity simulation extension for Kanvas."
            ":engineWorldObjectsKit" -> "Ready-to-use 2D world object templates for Kanvas scenes."
            else -> "Kanvas Kotlin Multiplatform 2D runtime library module."
        }

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "GitHubPackages"
                    val repository = providers.environmentVariable("GITHUB_REPOSITORY")
                        .orElse("MaximDvinov/Kanvas")
                    url = uri("https://maven.pkg.github.com/${repository.get()}")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
                maven {
                    name = "localBuild"
                    url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI()
                }
            }

        }

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral()
            signAllPublications()
            pom {
                name.set("Kanvas ${project.name}")
                description.set(moduleDescription)
                url.set("https://github.com/MaximDvinov/Kanvas")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit/")
                    }
                }
                developers {
                    developer {
                        id.set("MaximDvinov")
                        name.set("Maxim Dvinov")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/MaximDvinov/Kanvas.git")
                    developerConnection.set("scm:git:ssh://git@github.com/MaximDvinov/Kanvas.git")
                    url.set("https://github.com/MaximDvinov/Kanvas")
                }
            }
        }

        extensions.configure<SigningExtension>("signing") {
            setRequired {
                gradle.taskGraph.allTasks.any {
                    it.name.contains("MavenCentral") || it.name.contains("GitHubPackages")
                }
            }
        }
    }
}
