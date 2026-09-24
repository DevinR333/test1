plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blacklab.buddybounce"
    // Google Play requires apps to target API 36 (Android 16). compileSdk has to be at least
    // targetSdk, so both move together.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blacklab.buddybounce"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The whole game is drawn on a Canvas, so none of these are needed.
    buildFeatures {
        buildConfig = false
        resValues = false
    }
}

dependencies {
    // Intentionally empty: the game uses only the Android framework + Kotlin stdlib,
    // so the APK stays tiny and the build has no third-party resolution to do.
}
