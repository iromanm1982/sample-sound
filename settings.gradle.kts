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

rootProject.name = "samples-button"
include(":app")
include(":core:model")
include(":core:database")
include(":core:audio")
include(":core:data")
include(":core:ui")
include(":core:designsystem")
include(":feature:soundboard:api")
include(":feature:soundboard:impl")
include(":feature:browser:api")
include(":feature:browser:impl")
