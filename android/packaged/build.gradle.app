plugins { id 'com.android.application' }

android {
    namespace 'com.oracle.port'
    compileSdk 34

    defaultConfig {
        applicationId "com.oracle.port"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        ndk {
            // One architecture. Every Android handheld worth running this on
            // is arm64; adding the others multiplies a long build by four.
            abiFilters 'arm64-v8a'
        }
        externalNativeBuild {
            cmake { arguments '-DANDROID_STL=none' }
        }
    }

    externalNativeBuild {
        cmake {
            path 'src/main/cpp/CMakeLists.txt'
            version '3.22.1'
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            signingConfig signingConfigs.debug
        }
    }

    // The cartridge is read straight out of the package; leaving it
    // uncompressed means it can be mapped rather than unpacked at startup.
    androidResources { noCompress 'gbc', 'gb' }

    // Sixty translated banks take a few minutes to compile. Nothing in here
    // is worth failing a build over a lint opinion.
    lint { checkReleaseBuilds false; abortOnError false }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
