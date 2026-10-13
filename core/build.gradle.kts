import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The scraper core is plain Kotlin on purpose: no Android, no third party
// libraries. That keeps every parsing rule unit-testable on a bare JVM.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}

val javaExt = extensions.getByType<JavaPluginExtension>()

/**
 * Runs the scraper test suite (no JUnit, no network):
 *   ./gradlew :core:parserSelfTest
 */
tasks.register<JavaExec>("parserSelfTest") {
    group = "verification"
    description = "Runs the dependency-free scraper parser tests (no network needed)."
    classpath = javaExt.sourceSets["test"].runtimeClasspath
    mainClass.set("com.fap.core.ParserTestMainKt")
}
