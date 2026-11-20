import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp) // runs the Room code generator
}

// Read the TMDB token from local.properties. That file is git-ignored, so the secret
// never ends up in a tracked file. If the file or the key is missing we use "" and the
// app still builds and starts (network calls will simply fail with an "unauthorized" error).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val tmdbReadToken: String = localProperties.getProperty("TMDB_READ_TOKEN", "").trim()

android {
    namespace = "com.example.cinefiles"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cinefiles"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Available in code as BuildConfig.TMDB_READ_TOKEN
        val escapedToken = tmdbReadToken.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "TMDB_READ_TOKEN", "\"$escapedToken\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)

    // Screen state: ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Navigation Component (single activity, many fragments)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Background work
    implementation(libs.kotlinx.coroutines.android)

    // Local storage (Room). "ksp" is the code generator.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network (TMDB)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson) // brings in Gson
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging.interceptor) // debug builds only

    // Images (no annotation processor on purpose)
    implementation(libs.glide)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
