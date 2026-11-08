// Moduł :control-model: czysty JVM, model profilu Thumbcast, kodek JSON, walidacja, migracje.
// Wersje pluginów i bibliotek pochodzą wyłącznie z gradle/libs.versions.toml (agent SCAFFOLD).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.property)
    // Gradle 9 nie dokłada launchera JUnit Platform automatycznie.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform() // ustawiane też globalnie w głównym build.gradle.kts; tu dla jawności
    // Testy round-trip czytają presety z katalogu głównego projektu.
    systemProperty("thumbcast.presetsDir", rootProject.file("presets").absolutePath)
}
