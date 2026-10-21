plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// CI passes the run number so every release APK upgrades the previous one (Obtainium).
val ciVersionCode = providers.environmentVariable("CONTRAIL_VERSION_CODE").orNull?.toIntOrNull()
val releaseKeystore = providers.environmentVariable("CONTRAIL_KEYSTORE_PATH").orNull

android {
    namespace = "com.contrail.contrail"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.contrail.contrail"
        minSdk = 34
        targetSdk = 37
        versionCode = ciVersionCode ?: 1
        versionName = "0.1.${ciVersionCode ?: 0}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The keystore never lives in the repo. CI decodes it from GitHub Secrets.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("CONTRAIL_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CONTRAIL_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CONTRAIL_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // Whole-app R8. A package-scoped pass broke OkHttp's access to rewritten kotlin.* classes.
            optimization {
                enable = true
            }
            // Phones are arm64; x86_64 keeps the release testable on the emulator.
            ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "3g"
                // Robolectric reaches into JDK internals that newer JDKs no longer open by default.
                test.jvmArgs(
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                )
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            }
        }
    }
    lint {
        // Lint runs in CI and locally; real issues fail the build, dependency nags do not.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "LogNotTimber")
        abortOnError = true
        checkDependencies = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.maplibre.android)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.coil.test)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
