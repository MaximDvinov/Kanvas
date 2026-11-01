pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("../../build/local-maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "publication-consumer"
