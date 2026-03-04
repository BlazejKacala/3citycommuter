import java.io.FileInputStream
import java.util.Properties

apply("${project.rootDir}/spotless.gradle")
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.secrets)
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("com.diffplug.spotless") version "7.0.2"
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "2.1.10"

}

secrets {
    propertiesFileName = "secrets.properties"

    ignoreList.add("keyToIgnore")
    ignoreList.add("sdk.*")
}


android {
    namespace = "pl.bkacala.threecitycommuter"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.bkacala.threecitycommuter"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"

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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.android)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation(libs.maps.compose.widgets)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.permission.flow.android)
    implementation(libs.permission.flow.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)


    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.junit4)

    implementation(project(":data"))
    implementation(kotlin("reflect"))

}
