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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KideApp"
include(":app")
include(":kide")
include(":kide-test")
include(":kide-navigation")
include(":kide-clean-architecture")
include(":kide-clean-architecture-test")
include(":kide-koin")
include(":kide-decompose")
include(":kide-voyager")
include(":kide-devtools")
