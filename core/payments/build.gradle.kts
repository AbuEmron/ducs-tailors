plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// The adapter between the `PaymentGateway` port in :core:domain and the edge
// function that holds the Stripe key. It reuses the HTTP transport from
// :core:auth rather than introducing a second one, which keeps the number of
// places that can be pointed at a wrong host down to one.
dependencies {
    api(project(":core:domain"))
    api(project(":core:auth"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
