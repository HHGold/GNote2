import java.util.Properties
import java.io.ByteArrayOutputStream



plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.chinhsiang.premiumnotes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chinhsiang.premiumnotes"
        minSdk = 24
        targetSdk = 34
        
        // 取得最新的 Git Tag (例如 v1.0.8 -> 1.0.8)
        val gitTag = try {
            val stdout = ByteArrayOutputStream()
            rootProject.exec {
                commandLine("git", "describe", "--tags", "--abbrev=0")
                standardOutput = stdout
                isIgnoreExitValue = true
            }
            stdout.toString().trim().removePrefix("v").takeIf { it.isNotBlank() } ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

        // 格式化 Version Code (例如 1.0.8 -> 10008)
        fun calculateVersionCode(v: String): Int {
            return try {
                val parts = v.split(".")
                val major = parts.getOrNull(0)?.toInt() ?: 1
                val minor = parts.getOrNull(1)?.toInt() ?: 0
                val patch = parts.getOrNull(2)?.toInt() ?: 0
                major * 10000 + minor * 100 + patch
            } catch (e: Exception) {
                10000
            }
        }

        // 從環境變數讀取版本資訊 (用於 GitHub Actions)，否則使用 Git Tag
        versionCode = System.getenv("APP_VERSION_CODE")?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: calculateVersionCode(gitTag)
        versionName = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: gitTag
    }


    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.p12")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Gson for simple JSON serialization with SharedPreferences
    implementation("com.google.code.gson:gson:2.10.1")

    // Navigation and Extended Icons
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Sign-In & Coroutines
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.credentials:credentials:1.3.0-alpha04")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0-alpha04")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Biometric Auth
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
}
