pluginManagement {
    includeBuild("build-logic/convention")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://api.mapbox.com/downloads/v2/releases/maven") {
            credentials.username = "mapbox"
            credentials.password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").getOrElse("")
        }
    }
}

rootProject.name = "threecitycommuter"

include(":shared:core")
include(":shared:network")
include(":shared:database")
include(":shared:data")
include(":shared:ui")
include(":composeApp:android")
include(":composeApp:desktop")
