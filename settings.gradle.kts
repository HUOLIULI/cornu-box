pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MediaShell"

include(":app")
include(":core:common")
include(":core:ui")
include(":core:data")
include(":core:source")
include(":core:media")
include(":core:ai")
include(":feature:video")
include(":feature:reader")
include(":feature:music")
include(":feature:settings")
