// Top-level build file. Plugins are declared here once and applied per module.
plugins {
    alias(libs.plugins.android.application) apply false
    // Declared so AGP's built-in Kotlin uses this KGP version instead of its bundled one.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}
