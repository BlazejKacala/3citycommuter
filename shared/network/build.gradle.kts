import java.util.Properties

plugins {
    id("threecitycommuter.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

val generatePlkApiConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/plk-config/src/commonMain/kotlin")
    outputs.dir(outputDir)

    doLast {
        val secrets = Properties()
        val candidateFiles = listOf(
            rootProject.file("secrets.properties"),
            rootProject.rootDir.parentFile.resolve("secrets.properties"),
        )
        candidateFiles.firstOrNull { it.exists() }?.inputStream()?.use { stream ->
            secrets.load(stream)
        }

        val plkKey = providers.environmentVariable("PLK_KEY").orNull
            ?: secrets.getProperty("PLK_KEY")
            ?: ""
        val packagePath = outputDir.get().file("pl/bkacala/threecitycommuter/client/PlkApiConfig.kt").asFile
        packagePath.parentFile.mkdirs()
        packagePath.writeText(
            """
            package pl.bkacala.threecitycommuter.client

            internal object PlkApiConfig {
                const val baseUrl = "https://pdp-api.plk-sa.pl"
                const val apiKey = "${plkKey.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val skmTricityCarrierCode = "SKMT"
            }
            """.trimIndent(),
        )
    }
}

kotlin {
    android {
        namespace = "pl.bkacala.threecitycommuter.network"
    }
    jvm("jvm")
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets.named("commonMain") {
        kotlin.srcDir(generatePlkApiConfig)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.java)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generatePlkApiConfig)
}
