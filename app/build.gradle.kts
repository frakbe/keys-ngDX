plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseSigningAvailable = !releaseKeystorePath.isNullOrBlank() && !releaseKeystorePassword.isNullOrBlank()

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Checks that the persistent release keystore is configured."
    doLast {
        check(releaseSigningAvailable) {
            "Release signing is not configured. Set ANDROID_KEYSTORE_PATH and ANDROID_KEYSTORE_PASSWORD."
        }
        check(file(releaseKeystorePath!!).isFile) {
            "Release keystore not found at ANDROID_KEYSTORE_PATH."
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") {
        dependsOn(verifyReleaseSigning)
    }
}

android {
    namespace = "it.keysng.dx"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.keysng.dx"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.0-beta.3"
    }

    if (releaseSigningAvailable) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystorePath!!)
            storePassword = releaseKeystorePassword
            keyAlias = "keys-ngdx"
            keyPassword = releaseKeystorePassword
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
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

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.github.open-keychain.open-keychain:openpgp-api:v5.7.1")
}
