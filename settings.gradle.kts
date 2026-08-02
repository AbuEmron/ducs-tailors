// Fi Sabilillah — shared platform core.
//
// This build contains only pure Kotlin/JVM modules. It is deliberately free of
// any Android or Google Maven dependency so that the safety-critical logic of
// the platform can be compiled and tested on any machine, in CI, and inside
// restricted build environments — and so the same core can later be consumed by
// an iOS/Kotlin Multiplatform client or a server-side tool.
//
// The Android application lives in `androidApp/`, which is a separate Gradle
// build that includes this one as a composite build. See androidApp/settings.gradle.kts.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "fi-sabilillah-core"

include(":core:model")
include(":core:policy")
include(":core:auth")
include(":core:domain")
include(":core:data")
