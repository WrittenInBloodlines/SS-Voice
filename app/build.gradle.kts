plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ss.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ss.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
    }

    androidResources {
        noCompress += "onnx"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.7.aar"))
    implementation("org.apache.commons:commons-compress:1.28.0")
}
