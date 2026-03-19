plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.flamease"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.flamease"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "EMAIL_SENDER", "\"flamease.config@gmail.com\"")
            buildConfigField("String", "EMAIL_PASSWORD", "\"ffgj gntp fuzi hakd\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "EMAIL_SENDER", "\"flamease.config@gmail.com\"")
            buildConfigField("String", "EMAIL_PASSWORD", "\"ffgj gntp fuzi hakd\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}
android {
    // ... other config

    buildTypes {
        release {
            // ...
        }
        debug {
            // ...
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

// Strip quotes if present
val geminiApiKey = properties["GEMINI_API_KEY"]?.toString()?.trim('"') ?: ""

android {
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }
}

dependencies {

    implementation("com.sun.mail:android-mail:1.6.2")
    implementation("com.sun.mail:android-activation:1.6.2")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.android.volley:volley:1.2.1")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-firestore")

    // ADD THIS
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Coroutines (already have this)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
// Lifecycle scope for coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

}