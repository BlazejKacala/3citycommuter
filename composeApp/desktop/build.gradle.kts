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

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.androidx.navigation.compose)
    testImplementation(libs.kotlinx.datetime)
}

// Material's date picker and existing providers need both the new and the 0.6 time API at runtime.
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath", ignoreCase = true)) {
        resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.kotlinxDatetimeCompat.get()}")
    }
}

tasks.register<JavaExec>("runSchedulePreview") {
    group = "application"
    description = "Runs the mock schedule screens without the database or network."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("pl.bkacala.threecitycommuter.SchedulePreviewKt")
}

compose.desktop {
    application {
        mainClass = "pl.bkacala.threecitycommuter.MainKt"
    }
}
