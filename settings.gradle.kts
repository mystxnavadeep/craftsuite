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

rootProject.name = "CraftSuite"

include(":app")
include(":core:model")
include(":core:fileaccess")
include(":core:safety")
include(":core:tools")
include(":core:enginebridge")
include(":core:ai")
include(":feature-wordcraft")
include(":feature-gridcraft")
include(":feature-deckcraft")
