// Moduł :control-engine — silnik wejścia dotykowego (czysty JVM, bez Androida).
// Aliasy pochodzą z gradle/libs.versions.toml tworzonego przez agenta SCAFFOLD:
//   [plugins]   kotlin-jvm
//   [libraries] kotlin-test, kotlin-test-junit5, kotest-property, junit-jupiter, junit-platform-launcher
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // Publiczne API silnika używa typów modelu (Profile, MouseBtn), więc "api", nie "implementation".
    api(project(":control-model"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.junit.jupiter)
    // Gradle 9 nie dokłada launchera JUnit Platform automatycznie.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform() // ustawiane też globalnie w głównym build.gradle.kts
    // Liczbę iteracji testów właściwości (thumbcast.policy.iterations) przekazuje główny build.gradle.kts.
}
