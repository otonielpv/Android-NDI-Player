plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ndiplayer.oto"
    compileSdk = 34

    defaultConfig {
        applicationId = "ndiplayer.oto"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-DEBUG"
            // Performance optimizations for low-end devices
            buildConfigField("boolean", "ENABLE_PERFORMANCE_LOGGING", "true")
            buildConfigField("int", "TARGET_FPS", "30")
            buildConfigField("boolean", "USE_HARDWARE_ACCELERATION", "true")
            buildConfigField("boolean", "USE_OPTIMIZED_SERVICES", "true")
            buildConfigField("int", "MAX_FRAME_CACHE_SIZE", "3")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release optimizations
            buildConfigField("boolean", "ENABLE_PERFORMANCE_LOGGING", "false")
            buildConfigField("int", "TARGET_FPS", "30")
            buildConfigField("boolean", "USE_HARDWARE_ACCELERATION", "true")
            buildConfigField("boolean", "USE_OPTIMIZED_SERVICES", "true")
            buildConfigField("int", "MAX_FRAME_CACHE_SIZE", "3")
        }
        create("lowend") {
            initWith(getByName("debug"))  // Cambiar de release a debug para auto-firmar
            versionNameSuffix = "-LOWEND"
            buildConfigField("boolean", "ENABLE_PERFORMANCE_LOGGING", "true")
            buildConfigField("int", "TARGET_FPS", "20") // Lower FPS for low-end devices
            buildConfigField("boolean", "USE_HARDWARE_ACCELERATION", "true")
            buildConfigField("boolean", "USE_OPTIMIZED_SERVICES", "true")
            buildConfigField("int", "MAX_FRAME_CACHE_SIZE", "2") // Smaller cache
            buildConfigField("int", "MAX_RESOLUTION_WIDTH", "854")
            buildConfigField("int", "MAX_RESOLUTION_HEIGHT", "480")
        }
        create("staging") {
            initWith(getByName("debug"))
            isDebuggable = true
            versionNameSuffix = "-STAGING"
            buildConfigField("boolean", "ENABLE_EXTENSIVE_LOGGING", "true")
            buildConfigField("boolean", "USE_MOCK_NDI_SOURCES", "true")
            buildConfigField("boolean", "ENABLE_PERFORMANCE_LOGGING", "true")
            buildConfigField("int", "TARGET_FPS", "25") // Lower FPS for staging testing
            buildConfigField("boolean", "USE_OPTIMIZED_SERVICES", "true")
            buildConfigField("int", "MAX_FRAME_CACHE_SIZE", "4")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "25.1.8937393"

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
            pickFirsts += "**/libndi.so"
        }
    }
}

dependencies {
    // LEANBACK DISABLED FOR ULTRA MINIMAL TESTING
    // implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    // MEDIA DISABLED FOR ULTRA MINIMAL TESTING
    // implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    // implementation("androidx.media3:media3-exoplayer:1.2.1")
    // implementation("androidx.media3:media3-ui:1.2.1")
    // implementation("androidx.media3:media3-common:1.2.1")
}