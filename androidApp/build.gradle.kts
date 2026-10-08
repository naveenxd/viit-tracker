import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val keystorePropertiesFile = rootProject.file("androidApp/key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// CI supplies these via env (see .github/workflows/android-release.yml);
// local/dev builds fall back to the checked defaults.
val ciVersionCode = System.getenv("VERSION_CODE")?.trim()?.toIntOrNull()
val ciVersionName = System.getenv("VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    // Compose UI for the in-app update overlay (pill + update dialog)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Ktor Client
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines (Main dispatcher for widget toasts)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Encrypted credential storage (widget reads the shared credential store)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Lifecycle & ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
}

android {
    namespace = "com.vignan.tracker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vignan.tracker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }
    buildTypes {
        debug {
            // Side-by-side install: the debug package (com.vignan.tracker.debug)
            // coexists with release, so testing builds never overwrite the real app
            // (and vice versa — different package, no signature clash on install).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Per-ABI splits + universal APK, enabled only by CI release builds so local
    // dev builds stay single-APK (ENABLE_ABI_SPLITS=true ./gradlew assembleRelease).
    if (System.getenv("ENABLE_ABI_SPLITS") == "true") {
        splits {
            abi {
                isEnable = true
                reset() // drop AGP's default filter set (includes dead ABIs)
                include("armeabi-v7a", "arm64-v8a", "x86_64")
                isUniversalApk = true
            }
        }
    }
    buildFeatures {
        compose = true
    }
}
