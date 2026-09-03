plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.roomcheck.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.roomcheck.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.6.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ROOMCHECK_KEYSTORE") ?: "keystore/roomcheck.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("ROOMCHECK_KEYSTORE_PASSWORD") ?: "roomcheck"
            keyAlias = System.getenv("ROOMCHECK_KEY_ALIAS") ?: "roomcheck"
            keyPassword = System.getenv("ROOMCHECK_KEY_PASSWORD") ?: "roomcheck"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }

    // Build the version into the filename, so a downloaded APK says which one it is without
    // having to be installed to find out - and two of them can sit in a folder together.
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "RoomCheck-$versionName.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
}
