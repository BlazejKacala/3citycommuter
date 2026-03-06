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
            // Mapbox requires MAPBOX_DOWNLOADS_TOKEN in gradle.properties to resolve
            // implementation(libs.mapbox.maps.android)
            // implementation(libs.mapbox.maps.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
