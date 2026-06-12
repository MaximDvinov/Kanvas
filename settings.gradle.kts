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
include(":samples:planetSample:shared")
include(":samples:planetSample:desktop")
include(":samples:planetSample:android")
include(":samples:planetSample:web")
include(":samples:planetSample:ios")
include(":samples:flappySample:shared")
include(":samples:flappySample:desktop")
include(":samples:flappySample:android")
include(":samples:flappySample:web")
include(":samples:flappySample:ios")
