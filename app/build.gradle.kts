
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.samplers.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samplers.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            arguments += listOf("-DANDROID_STL=c++_shared")
        }
    }
}

dependencies {
    implementation("net.volcanomobile.fluidsynth-android:fluidsynth-android:2.4.6")
}
