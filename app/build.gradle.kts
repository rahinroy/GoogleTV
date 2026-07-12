plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nihar.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nihar.tvlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Optional: pin a debug keystore at ../signing/debug.keystore so every build
        // (across machines) signs identically — `adb install -r` then updates the
        // installed app in place instead of failing on a signature mismatch. The
        // keystore is NOT committed; if it's absent, Gradle falls back to its
        // auto-generated debug key (standard behavior). To create your own pinned key:
        //   keytool -genkey -v -keystore signing/debug.keystore -storepass android \
        //     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
        //     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
        getByName("debug") {
            val pinned = file("../signing/debug.keystore")
            if (pinned.exists()) {
                storeFile = pinned
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Keep it debuggable-simple for now; no minification while iterating.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // The entire TV Material3 API surface is still marked experimental.
        freeCompilerArgs += "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
    }
    buildFeatures {
        compose = true
    }
    // Large source JPEGs are already compressed; don't let AAPT re-compress them.
    androidResources {
        noCompress += "jpg"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
    implementation(libs.androidx.exifinterface)

    debugImplementation(libs.androidx.ui.tooling)
}
