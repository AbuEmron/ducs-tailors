plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Deliberately thin. Everything here speaks HTTP and JSON to Supabase, and the
// only way to keep that testable without a network — and buildable inside a
// restricted environment — is to depend on nothing but the standard library,
// coroutines, and the shared model types.
dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
