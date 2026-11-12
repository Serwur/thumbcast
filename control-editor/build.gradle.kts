// Moduł :control-editor – edytor układu kontrolek (Compose, etap E2, plan sekcja 7 „Edytor układu”).
//
// Kontrakt dla :app: aktywność app.thumbcast.editor.LayoutEditorActivity, extra
// app.thumbcast.editor.extra.PROFILE_JSON (wejście i wynik). Szczegóły: docs/editor.md.
//
// Kotlin: „built-in Kotlin” z AGP 9 (jak :control-android); kompilator Compose przez plugin kotlin-compose.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.thumbcast.editor"
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
        // Testy JVM obejmują tylko czystą logikę (pakiet app.thumbcast.editor.state), bez API Androida.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Rysowanie kontrolek jak w grze (ControlLayout, OverlaySnapshotBuilder, OverlayRenderer);
    // :control-android eksportuje przez `api` także :control-model i :control-engine.
    implementation(project(":control-android"))
    // Wspólny motyw Compose (ThumbcastTheme, paleta D1 z docs/ui-audit.md).
    implementation(project(":control-ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)

    // Testy JVM czystej logiki edytora (JUnit 4, jak w :control-android).
    testImplementation(libs.junit4)
    // Test FAIR_PLAY parsuje zapisany JSON (ProfileValidator.validateRaw przyjmuje JsonElement).
    testImplementation(libs.kotlinx.serialization.json)
}
