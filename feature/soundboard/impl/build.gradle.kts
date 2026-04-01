plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.role.samples_button.feature.soundboard.impl"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
