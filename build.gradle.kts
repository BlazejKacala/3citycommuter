// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false

}
//    id("com.android.application") version "8.2.0" apply false
//    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
//    id("com.android.library") version "8.2.0" apply false
//    id("org.jetbrains.kotlin.jvm") version "1.9.0" apply false
//    id("com.google.dagger.hilt.android") version "2.50" apply false
//}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.secrets.gradle.plugin)
    }
}