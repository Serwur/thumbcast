// Moduł :control-android – warstwa Androida dla Thumbcast (overlay dotykowy + adaptery InputSink).
//
// Oczekiwane aliasy w gradle/libs.versions.toml (tworzy agent SCAFFOLD):
//   [plugins]   android-library            = com.android.library (AGP 9.5.1 od 12.11.2026, wcześniej 9.4.0 jak upstream)
//   [libraries] junit4                     = junit:junit (4.13.2)                      [istnieje]
//               androidx-test-runner       = androidx.test:runner                     [BRAK, prośba w CONTRACT-NOTES]
//               androidx-test-ext-junit    = androidx.test.ext:junit                  [BRAK, prośba w CONTRACT-NOTES]
//
// Kotlin: zakładamy „built-in Kotlin” z AGP 9 (bez osobnego pluginu org.jetbrains.kotlin.android).
// Jeśli SCAFFOLD wyłączy built-in Kotlin, trzeba dopisać `alias(libs.plugins.kotlin.android)`.

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.thumbcast.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Testy JVM nie dotykają klas Androida w ścieżce wykonania, ale na wszelki wypadek
        // stuby android.jar zwracają wartości domyślne zamiast rzucać wyjątek.
        unitTests.isReturnDefaultValues = true
        // ProfileRepositoryTest sprawdza prawdziwe presety z katalogu głównego projektu.
        unitTests.all { it.systemProperty("thumbcast.presetsDir", rootProject.file("presets").absolutePath) }
    }
}

dependencies {
    // `api`, bo publiczne API modułu (fabryka, overlay) używa typów Profile i InputSink.
    api(project(":control-model"))
    api(project(":control-engine"))
    // Ustawienia aplikacji hosta (apps/AppStreamSettings): tylko API JsonElement z biblioteki, bez pluginu
    // kotlin-serialization. Ta sama biblioteka jest już w APK przez :control-model.
    implementation(libs.kotlinx.serialization.json)

    // Testy JVM (JUnit 4 – domyślny runner testów jednostkowych AGP). Bez Robolectric: logika
    // testowana w testach nie dotyka API Androida (patrz docs/android.md, „Testy”).
    testImplementation(libs.junit4)

    // Test instrumentacyjny (src/androidTest). Aliasy jeszcze nie istnieją w version catalog;
    // odkomentować po dodaniu ich przez SCAFFOLD (nieznany alias psuje konfigurację całego buildu).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // androidTestImplementation(libs.junit4)
}
