// Thumbcast: ustawienia wielomodułowego buildu.
//
// Repo = historia upstreamu moonlight-stream/moonlight-android (scalona od v12.2, 11.11.2026)
// + moduły Thumbcast obok katalogu app/. Upstreamowe settings.gradle i build.gradle (Groovy)
// są usunięte; repozytoria z ich allprojects {} (w tym jitpack.io) są tutaj.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Upstreamowy allprojects {} jest usunięty, żaden moduł nie deklaruje własnych repozytoriów.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // :app upstreamu: com.github.cgutman:ShieldControllerExtensions jest tylko na jitpack.io.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.cgutman") }
        }
    }
}

rootProject.name = "thumbcast"

// Czysty JVM: model profilu, schemat, walidacja, migracje (agent MODEL).
include(":control-model")
// Czysty JVM: silnik wejścia, ActionPolicy, testy właściwości (agent ENGINE).
include(":control-engine")
// Biblioteka Androida: ThumbcastOverlay i adaptery InputSink (agent ANDROID).
include(":control-android")

// Moduł aplikacji z upstreamu (Game.java z hookami z patches/).
include(":app")

// Etap E2 (plan sekcja 5.1): edytor układu w Compose (LayoutEditorActivity).
include(":control-editor")
