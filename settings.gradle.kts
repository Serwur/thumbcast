// Thumbcast: ustawienia wielomodułowego buildu.
//
// Stan obecny (pre-alpha, przed forkiem): tylko nowe moduły Thumbcast.
// Po forku upstreamu moonlight-stream/moonlight-android (patrz docs/NEXT-STEPS.md)
// dochodzi moduł :app z upstreamu. Rekomendowany wariant to scalenie:
// repo Thumbcast powstaje jako fork upstreamu, a te moduły są dodane obok katalogu app/.
// Wtedy upstreamowy settings.gradle (Groovy) usuwamy i odkomentowujemy include(":app") poniżej.
// Wariant alternatywny (submoduł git z upstreamem w np. upstream/) wymaga
// project(":app").projectDir = file("upstream/app") i jest trudniejszy przy rebase.

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
    // PREFER_SETTINGS zamiast FAIL_ON_PROJECT_REPOS, bo upstream deklaruje repozytoria w allprojects {}
    // (m.in. jitpack.io). Po scaleniu przenieść je tutaj i rozważyć FAIL_ON_PROJECT_REPOS.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Upstream używa jitpack.io (build.gradle upstreamu, [Z:7]). Odkomentować po scaleniu z :app.
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "thumbcast"

// Czysty JVM: model profilu, schemat, walidacja, migracje (agent MODEL).
include(":control-model")
// Czysty JVM: silnik wejścia, ActionPolicy, testy właściwości (agent ENGINE).
include(":control-engine")
// Biblioteka Androida: ThumbcastOverlay i adaptery InputSink (agent ANDROID).
include(":control-android")

// Po forku: moduł aplikacji z upstreamu (Game.java z hookami z patches/).
// include(":app")

// Etap E2 (plan sekcja 5.1): edytor układu w Compose.
// include(":control-editor")
