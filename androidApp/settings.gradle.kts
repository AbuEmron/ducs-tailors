// Fi Sabilillah — Android client.
//
// This is a separate Gradle build that includes the shared core build (the repository
// root) as a composite build. The split is deliberate:
//
//  * The shared core is pure Kotlin/JVM. It compiles and its full test suite runs on any
//    machine with a JDK — no Android SDK, no Google Maven, no emulator. That is what makes
//    the safety-critical logic cheap to verify in CI and reusable by a future iOS client.
//  * This build owns everything Android: the Compose UI, navigation, and platform plumbing.
//
// Open this directory in Android Studio. Gradle substitutes the `org.fisabilillah:core-*`
// coordinates below for the sibling build's projects, so a change in the core is picked up
// immediately without publishing anything.

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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "fi-sabilillah-android"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("org.fisabilillah:core-model")).using(project(":core:model"))
        substitute(module("org.fisabilillah:core-policy")).using(project(":core:policy"))
        substitute(module("org.fisabilillah:core-auth")).using(project(":core:auth"))
        substitute(module("org.fisabilillah:core-domain")).using(project(":core:domain"))
        substitute(module("org.fisabilillah:core-data")).using(project(":core:data"))
        substitute(module("org.fisabilillah:core-payments")).using(project(":core:payments"))
    }
}

include(":app")
