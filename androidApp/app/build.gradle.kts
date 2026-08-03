plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.fisabilillah.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.fisabilillah.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where this build signs in.
        //
        // Both values are safe to ship: the URL is public and the publishable key is the
        // one Supabase intends to go in clients -- it grants nothing on its own, because
        // every table is behind row-level security keyed on auth.uid(). The service-role
        // key is NOT here and must never be; SupabaseConfig refuses one at runtime.
        //
        // Override for a different project with -PsupabaseUrl=... -PsupabaseKey=... or the
        // matching environment variables, so a fork does not have to edit this file.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"" + (providers.gradleProperty("supabaseUrl").orNull
                ?: System.getenv("SUPABASE_URL")
                ?: "https://mqrooikosbhwjcdssatf.supabase.co") + "\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"" + (providers.gradleProperty("supabaseKey").orNull
                ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
                ?: "sb_publishable_CcxLXpGAO0a5-clWVaQLUQ_0OEKSU1i") + "\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Substituted for the sibling composite build's projects — see settings.gradle.kts.
    implementation("org.fisabilillah:core-model:0.1.0")
    implementation("org.fisabilillah:core-policy:0.1.0")
    implementation("org.fisabilillah:core-domain:0.1.0")
    implementation("org.fisabilillah:core-data:0.1.0")
    implementation("org.fisabilillah:core-auth:0.1.0")
    implementation("org.fisabilillah:core-payments:0.1.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
