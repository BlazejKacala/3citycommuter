plugins {
    id("threecitycommuter.kmp.library")
    id("threecitycommuter.kmp.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.bkacala.threecitycommuter.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:data"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // MapLibre Compose - open-source fork of Mapbox GL
            implementation(libs.maplibre.compose)
            // MapLibre Native Android SDK (required by MapLibre Compose)
            implementation(libs.maplibre.native)
            // Kotlin Multiplatform Compose Animation for animateFloatAsState
            implementation("org.jetbrains.compose.animation:animation:1.7.3")
        }
        jvmMain.dependencies {
            // MapLibre Compose works on JVM (Desktop) too
            implementation(libs.maplibre.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
