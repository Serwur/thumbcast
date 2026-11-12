// Moduł :control-ui – wspólny motyw Compose i nowe ekrany Thumbcast (docs/ui-audit.md, D2 i D8).
//
// Zawartość: ThumbcastTheme (paleta D1 w Material 3), kontrakt app.thumbcast.ui.UiContract (wołany także z Javy w :app),
// aktywności OnboardingActivity (kreator pierwszego uruchomienia) i SetupGuideActivity (poradnik „Jak podłączyć PC”).
// Używają go :app i :control-editor. Szczegóły: docs/ui.md.
//
// Kotlin: „built-in Kotlin” z AGP 9 (jak :control-editor); kompilator Compose przez plugin kotlin-compose.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.thumbcast.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Testy JVM obejmują tylko czystą logikę (UiContract, kroki kreatora, model poradnika), bez API Androida.
        unitTests.isReturnDefaultValues = true
        // UiContractTest sprawdza, że presety kreatora istnieją w katalogu presets/.
        unitTests.all { it.systemProperty("thumbcast.presetsDir", rootProject.file("presets").absolutePath) }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)

    // Testy JVM (JUnit 4, jak w :control-editor i :control-android).
    testImplementation(libs.junit4)
}
