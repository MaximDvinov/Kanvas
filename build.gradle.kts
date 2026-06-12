import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

val kanvasLibraryModules = setOf(
    "engine",
    "enginePhysics",
    "engineGravityBarnesHut",
    "engineWorldObjectsKit",
)

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    if (name !in kanvasLibraryModules) return@subprojects

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        pluginManager.apply("maven-publish")
        pluginManager.apply("signing")

        val moduleDescription = when (name) {
            "engine" -> "Core Kotlin Multiplatform 2D runtime for Compose applications."
            "enginePhysics" -> "Optional 2D physics systems and DSL extensions for Kanvas."
            "engineGravityBarnesHut" -> "Barnes-Hut n-body gravity simulation extension for Kanvas."
            "engineWorldObjectsKit" -> "Ready-to-use 2D world object templates for Kanvas scenes."
            else -> "Kanvas Kotlin Multiplatform 2D runtime library module."
        }

        val javadocJar = tasks.register<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
            from(rootProject.file("README.md")) {
                into("META-INF")
            }
        }

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "MavenCentralStaging"
                    url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                    credentials {
                        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull
                        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull
                    }
                }
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

            publications.withType<MavenPublication>().configureEach {
                artifact(javadocJar)
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
        }

        extensions.configure<SigningExtension>("signing") {
            val signingKeyBase64 = providers.environmentVariable("SIGNING_KEY_BASE64").orNull
            val signingKey = if (!signingKeyBase64.isNullOrBlank()) {
                String(Base64.getDecoder().decode(signingKeyBase64))
            } else {
                providers.environmentVariable("SIGNING_KEY")
                    .orElse(providers.gradleProperty("signingInMemoryKey"))
                    .orNull
            }
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
                .orElse(providers.gradleProperty("signingInMemoryKeyPassword"))
                .orNull

            setRequired {
                gradle.taskGraph.allTasks.any {
                    it.name.contains("MavenCentralStaging")
                }
            }
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
            sign(extensions.getByType(PublishingExtension::class.java).publications)
        }
    }
}
