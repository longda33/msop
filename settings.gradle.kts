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
    }
}

rootProject.name = "PrimuseLite"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")

include(":feature:local")
include(":feature:remote")
include(":feature:player")
include(":feature:library")
include(":feature:lyrics")
include(":feature:settings")
