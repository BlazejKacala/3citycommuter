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

    defaultConfig {
        applicationId = "pl.bkacala.threecitycommuter"
        minSdk = 29
        targetSdk = 37
        versionCode = 5
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val secretPropertiesFile = rootProject.file("secrets.properties")
    if (secretPropertiesFile.exists()) {
        val secretProperties = Properties()
        secretProperties.load(FileInputStream(secretPropertiesFile))

        signingConfigs {
            create("release") {
                storeFile = rootProject.file("signing/key.jks")
                storePassword = secretProperties.getProperty("PASS") ?: ""
                keyAlias = secretProperties.getProperty("ALIAS") ?: ""
                keyPassword = secretProperties.getProperty("ALIAS_PASS") ?: ""
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
