plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val versionProps = java.util.Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersion = run {
    val patch = (runNumber - versionProps.getProperty("patchBase").toInt()).coerceAtLeast(0)
    "${versionProps.getProperty("major")}.${versionProps.getProperty("minor")}.$patch"
}
val keystorePath: String? = System.getenv("SIMPLE_KEYSTORE_PATH")

android {
    namespace = "com.andresterrazas.simple"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.andresterrazas.simple"
        minSdk = 25
        targetSdk = 34
        versionCode = runNumber
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIMPLE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIMPLE_KEY_ALIAS")
                keyPassword = System.getenv("SIMPLE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    // Instala los perfiles de compilación anticipada de Compose: animaciones fluidas desde el primer uso.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
