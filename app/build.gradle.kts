plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.autonet.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autonet.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "7.0-Small"
        
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
    }
    aaptOptions {
        noCompress("cact", "onnx", "bin", "txt")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("ca.solo-studios:kt-fuzzy:0.1.0")

    // The Brains: Cactus (STT) + Sherpa (TTS)
    implementation("com.cactuscompute:cactus:1.4.1-beta")
    
    // ✅ THE FIX: Correct Sherpa-ONNX dependency
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5")
}
