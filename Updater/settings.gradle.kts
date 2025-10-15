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
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = "MrDemon-LICH"
                password = "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"
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = "MrDemon-LICH"
                password = "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"
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
            credentials {
                username = "MrDemon-LICH"
                password = "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"
            }
        }
    }
}

rootProject.name = "Updater"
include(":app")
