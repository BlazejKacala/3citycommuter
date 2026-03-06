plugins {
    id("threecitycommuter.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "pl.bkacala.threecitycommuter.database"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(libs.androidx.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
