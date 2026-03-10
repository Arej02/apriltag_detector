plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.arej00"
    compileSdk = 34

    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
        excludes += setOf(
            "**/x86/**",
            "**/x86_64/**",
            "**/mips/**",
            "**/mips64/**",
            "**/armeabi/**"
        )
    }

    defaultConfig {
        applicationId = "com.example.arej00"
        minSdk = 24
        targetSdk = 30
        versionCode = 2
        versionName = "1.0-CK65"

        ndk {
            abiFilters.clear()
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation (project(":opencv"))
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}