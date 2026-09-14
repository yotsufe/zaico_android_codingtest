// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.androidx.navigation.safeargs) apply false
    // ルート直下の build.gradle.kts と settings.gradle.kts を検査対象にするため apply する
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
}
