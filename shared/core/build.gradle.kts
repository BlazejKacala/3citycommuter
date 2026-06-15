plugins {
    id("threecitycommuter.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.bkacala.threecitycommuter.core"
}

kotlin {
    androidTarget()
    jvm("jvm")
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
