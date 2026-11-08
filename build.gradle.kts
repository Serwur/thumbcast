// Główny plik buildu Thumbcast.
// Pluginy deklarujemy tu z "apply false", żeby wszystkie moduły dzieliły jeden classloader
// (AGP i Kotlin Gradle Plugin w tej samej wersji w całym buildzie).
//
// Po scaleniu z upstreamem: upstreamowy build.gradle (Groovy, buildscript { classpath AGP })
// zastępujemy tym plikiem. :app może dalej używać "apply plugin: 'com.android.application'",
// bo plugin jest na classpath dzięki deklaracji poniżej.

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}

// Liczba iteracji testów właściwości polityki (1 dotyk = 1 wejście).
// Lokalnie domyślnie z gradle.properties; w CI job "policy-tests" podaje
// -Pthumbcast.policy.iterations=100000 (kryterium E1: 0 naruszeń w 10^5 sekwencji, plan sekcja 8).
// Testy czytają ją przez System.getProperty("thumbcast.policy.iterations").
val policyIterations: Provider<String> =
    providers.gradleProperty("thumbcast.policy.iterations").orElse("1000")

subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("thumbcast.policy.iterations", policyIterations.get())
        testLogging {
            events("failed", "skipped")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
        reports.junitXml.required.set(true)
        reports.html.required.set(true)
    }

    // Moduły czysto JVM uruchamiają testy na JUnit Platform (kotlin.test + JUnit Jupiter + Kotest property).
    // Testy jednostkowe w :control-android (Robolectric) konfiguruje właściciel modułu.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
