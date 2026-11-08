plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kidlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kidlock.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed key so future versions install over the old one without uninstalling.
    signingConfigs {
        create("kidlock") {
            storeFile = file("kidlock-release.jks")
            storePassword = "kidlock123"
            keyAlias = "kidlock"
            keyPassword = "kidlock123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("kidlock")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
