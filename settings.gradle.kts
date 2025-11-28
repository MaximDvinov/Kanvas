rootProject.name = "Kanvas"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

include(":engine")
include(":enginePhysics")
include(":engineGravityBarnesHut")
include(":engineWorldObjectsKit")
include(":desktopApp")
include(":planetSample:shared")
include(":planetSample:desktop")
include(":planetSample:android")
include(":planetSample:web")
include(":planetSample:ios")
include(":flappySample:shared")
include(":flappySample:desktop")
include(":flappySample:android")
include(":flappySample:web")
include(":flappySample:ios")
