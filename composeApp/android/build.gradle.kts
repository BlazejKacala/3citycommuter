import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "pl.bkacala.threecitycommuter"
    compileSdk = 37
    val repoParentDir = rootProject.rootDir.parentFile

    val ciVersionCode = providers.environmentVariable("ANDROID_VERSION_CODE").orNull?.toIntOrNull()
    val ciVersionName = providers.environmentVariable("ANDROID_VERSION_NAME").orNull

    defaultConfig {
        applicationId = "pl.bkacala.threecitycommuter"
        minSdk = 29
        targetSdk = 37
        versionCode = ciVersionCode ?: 6
        versionName = ciVersionName ?: "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val secretProperties = Properties()
    val secretPropertiesFile = repoParentDir.resolve("secrets.properties")
    if (secretPropertiesFile.exists()) {
        secretProperties.load(FileInputStream(secretPropertiesFile))
    }

    val signingStoreFilePath =
        providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
            ?: repoParentDir.resolve("signing/key.jks").takeIf { it.exists() }?.absolutePath
    val signingStorePassword =
        providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
            ?: secretProperties.getProperty("PASS")
    val signingKeyAlias =
        providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
            ?: secretProperties.getProperty("ALIAS")
    val signingKeyPassword =
        providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
            ?: secretProperties.getProperty("ALIAS_PASS")

    if (
        !signingStoreFilePath.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFilePath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:core"))
    implementation(project(":shared:data"))
    implementation(project(":shared:network"))
    implementation(project(":shared:database"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.serialization.json)
}
