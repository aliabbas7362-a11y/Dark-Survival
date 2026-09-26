plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blindtechabbas.darksurvival"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.blindtechabbas.darksurvival"
        minSdk = 24
        targetSdk = 34
        versionCode = 21
        versionName = "3.0"
    }

    // FIXED SIGNING (v3.0): every APK from this machine is signed with the
    // SAME keystore, so version upgrades install directly over old versions
    // and a package-signature conflict can never happen again.
    // The keystore file is NOT committed to git (.gitignore) — if it is
    // missing (e.g. GitHub Actions), builds fall back to default signing.
    val ksFile = file("darksurvival-release.keystore")
    if (ksFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = ksFile
                storePassword = "DarkSurvival@2026"
                keyAlias = "darksurvival"
                keyPassword = "DarkSurvival@2026"
            }
        }
        buildTypes {
            debug {
                signingConfig = signingConfigs.getByName("release")
            }
            release {
                isMinifyEnabled = false
                signingConfig = signingConfigs.getByName("release")
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    } else {
        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
