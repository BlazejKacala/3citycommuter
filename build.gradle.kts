@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // Quality plugins - apply to root project
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Store detekt version for use in subprojects
val detektVersion = libs.versions.detekt.get()

// Configure Spotless - simple DSL without excludes
// (Spotless automatically skips build directories)
spotless {
    // Format Kotlin files
    kotlin {
        target("**/*.kt")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_function_naming" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
                // Force LF line endings for cross-platform consistency (Windows/WSL)
                "end_of_line" to "lf",
            ),
        )
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_function_naming" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
                // Force LF line endings for cross-platform consistency (Windows/WSL)
                "end_of_line" to "lf",
            ),
        )
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
    // Format other files
    format("misc") {
        target("*.md", ".gitignore", "*.yml", "*.yaml")
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()
        // Force LF line endings for cross-platform consistency (Windows/WSL)
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
    }
}

// Apply and configure Spotless for all subprojects
subprojects {
    apply(plugin = "com.diffplug.spotless")

    the<com.diffplug.gradle.spotless.SpotlessExtension>().apply {
        kotlin {
            target("**/*.kt")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_function_naming" to "disabled",
                    "ktlint_standard_function-naming" to "disabled",
                    // Force LF line endings for cross-platform consistency (Windows/WSL)
                    "end_of_line" to "lf",
                ),
            )
            trimTrailingWhitespace()
            indentWithSpaces(4)
            endWithNewline()
        }
        kotlinGradle {
            target("**/*.kts")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_function_naming" to "disabled",
                    "ktlint_standard_function-naming" to "disabled",
                    // Force LF line endings for cross-platform consistency (Windows/WSL)
                    "end_of_line" to "lf",
                ),
            )
            trimTrailingWhitespace()
            indentWithSpaces(4)
            endWithNewline()
        }
        // Format other files
        format("misc") {
            target("*.md", ".gitignore", "*.yml", "*.yaml")
            trimTrailingWhitespace()
            indentWithSpaces(2)
            endWithNewline()
            // Force LF line endings for cross-platform consistency (Windows/WSL)
            lineEndings = com.diffplug.spotless.LineEnding.UNIX
        }
    }
}

// Configure Detekt
detekt {
    toolVersion = detektVersion
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    ignoreFailures = false
}

// Configure Detekt reports on the task
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}

// Apply and configure Detekt for all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    the<io.gitlab.arturbosch.detekt.extensions.DetektExtension>().apply {
        toolVersion = detektVersion
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        ignoreFailures = false
    }
}

// Configure Detekt reports for subprojects
subprojects {
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
        }
    }
}

// Common lint task that runs all quality checks
tasks.register("lint") {
    description = "Runs all code quality checks (Spotless and Detekt)"
    group = "verification"

    dependsOn(
        tasks.named("spotlessCheck"),
        tasks.named("detekt"),
    )

    // Collect all subproject tasks
    val spotlessTasks = subprojects.mapNotNull { subproject ->
        subproject.tasks.findByName("spotlessCheck")
    }
    val detektTasks = subprojects.mapNotNull { subproject ->
        subproject.tasks.findByName("detekt")
    }

    (spotlessTasks + detektTasks).forEach { task ->
        dependsOn(task)
    }
}

// Make check depend on lint for quality gate
tasks.named("check") {
    finalizedBy(tasks.named("lint"))
}
