import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.21"
}

val releaseProperties = Properties().apply {
    rootProject.file("../../gradle.properties").inputStream().use(::load)
}

dependencies {
    implementation("io.github.maximdvinov:engineComposeDsl:${releaseProperties.getProperty("VERSION_NAME")}")
}
