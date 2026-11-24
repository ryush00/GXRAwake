import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gxrawake.app"
    compileSdk = 36

    val propVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull()
    val propVersionName = providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() }

    val releaseStoreFilePath = providers.gradleProperty("releaseStoreFile")
        .orElse(providers.environmentVariable("ANDROID_KEYSTORE_PATH"))
        .orNull
    val releaseStorePassword = providers.gradleProperty("releaseStorePassword")
        .orElse(providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD"))
        .orNull
    val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias")
        .orElse(providers.environmentVariable("ANDROID_KEY_ALIAS"))
        .orNull
    val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword")
        .orElse(providers.environmentVariable("ANDROID_KEY_PASSWORD"))
        .orNull

    val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    defaultConfig {
        applicationId = "com.gxrawake.app"
        minSdk = 29
        targetSdk = 35
        versionCode = propVersionCode ?: 1
        versionName = propVersionName ?: "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    testImplementation("junit:junit:4.13.2")
}
