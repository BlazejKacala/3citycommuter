plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:core"))
    implementation(project(":shared:data"))
    implementation(project(":shared:network"))
    implementation(project(":shared:database"))

    implementation(libs.compose.desktop.current.os)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "pl.bkacala.threecitycommuter.MainKt"
    }
}
