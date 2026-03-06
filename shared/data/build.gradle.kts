plugins {
    id("threecitycommuter.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.bkacala.threecitycommuter.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:network"))
            implementation(project(":shared:database"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.gms.play.services.location)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
