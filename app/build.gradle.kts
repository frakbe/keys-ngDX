plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.keysng.dx"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.keysng.dx"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-beta.1"
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
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.github.open-keychain.open-keychain:openpgp-api:v5.7.1")
}
