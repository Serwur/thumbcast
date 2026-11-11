# Następne kroki: fork upstreamu, wpięcie modułów, pierwszy test T-03

Ten dokument jest dla pierwszej sesji na maszynie z internetem i Android Studio. Repo Thumbcast powstało offline: bez sieci, bez kompilatora Kotlina, bez uruchomienia Gradle. **Nic tu nie było kompilowane ani testowane.**

## 0. Wymagania

- Android Studio (stabilne, zgodne z AGP 9.4/9.5), Android SDK: platforma 37 (compileSdk), build-tools, platform-tools.
- NDK **29.0.14206865** i CMake (SDK Manager → SDK Tools).
- JDK 17 (Android Studio ma wbudowany JBR; w CI Temurin 17).
- Git z obsługą submodułów, konto GitHub.
- PC z Sunshine v2026.914+ i grą do testu ([host-setup.md](host-setup.md)).

## 1. Fork upstreamu i scalenie z Thumbcast

Rekomendowany wariant: **repo Thumbcast = fork upstreamu + nasze moduły obok `app/`** (scalenie). Ułatwia to rebase (plan, ryzyko R5).

```bash
# 1. Na GitHub: Fork moonlight-stream/moonlight-android → <login>/thumbcast (publiczne, GPLv3).
git clone --recurse-submodules git@github.com:<login>/thumbcast.git
cd thumbcast
git remote add upstream https://github.com/moonlight-stream/moonlight-android.git
git fetch upstream --tags
git checkout -b thumbcast/main v12.2     # gałąź z naszymi zmianami; master śledzi upstream (v12.2 = commit b48494cb96bf, sprawdzony 10.11.2026)
git submodule update --init --recursive  # jedyny submoduł: moonlight-common-c w app/src/main/jni/moonlight-core/

# 2. Sprawdź, że czysty upstream się buduje (zanim cokolwiek zmienisz):
./gradlew assembleNonRootDebug           # nazwa wariantu zgodna z build.gradle upstreamu v12.2 (flavory root / nonRoot, typy debug / release); sam build niesprawdzony
```

Zainstaluj APK, sparuj z Sunshine w LAN i sprawdź stream pulpitu (plan, sekcja 13, krok 2).

```bash
# 3. Skopiuj pliki z repo offline (katalog /home/claude/thumbcast) do korzenia forka:
#    control-model/ control-engine/ control-android/ presets/ patches/
#    docs/ fastlane/ .github/ (scal z istniejącym .github upstreamu, jeśli jest)
#    settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml
#    README.md FAIR_PLAY.md CONTRIBUTING.md SECURITY.md NOTICE CONTRACT.md CONTRACT-NOTES.md
#    .gitignore .editorconfig
#    NIE nadpisuj: gradle/wrapper/gradle-wrapper.jar, gradlew, gradlew.bat (przychodzą z upstreamu).
```

Konflikty z plikami upstreamu:

| Plik upstreamu | Co zrobić |
|---|---|
| `settings.gradle` (Groovy) | Usuń. Zostaje nasz `settings.gradle.kts`; odkomentuj w nim `include(":app")`. Gradle nie pozwala mieć obu. |
| `build.gradle` (główny, Groovy, `buildscript { classpath AGP 9.4.0 }`, `allprojects { repositories ... jitpack }`) | Usuń. Zostaje nasz `build.gradle.kts` (pluginy przez version catalog). W `settings.gradle.kts` odkomentuj `maven { url = uri("https://jitpack.io") }`. |
| `gradle.properties` | Scal ręcznie: zachowaj ustawienia upstreamu i dodaj nasze (`thumbcast.policy.iterations`, `kotlin.code.style`). |
| `gradle/wrapper/gradle-wrapper.properties` | Zachowaj wersję upstreamu (9.7.1), dodaj `distributionSha256Sum` z https://gradle.org/release-checksums/. |
| `LICENSE.txt` upstreamu | Zachowaj. Nasz `LICENSE` zawiera już pełny tekst GPLv3 (10.11.2026); zostaw jeden z dwóch plików, np. `LICENSE.txt` upstreamu. |
| `fastlane/metadata/android/` upstreamu | Zastąp naszymi metadanymi (inna nazwa, opis bez znaków towarowych). Usuń zrzuty i grafiki upstreamu, jeśli pokazują markę Moonlight. |
| `README.md` upstreamu | Zastąp naszym; dopisz link do upstreamu (już jest). |

`app/build.gradle` (Groovy) upstreamu zostaje w Groovy: plugin `com.android.application` jest na classpath przez nasz główny `build.gradle.kts`. Sprawdzone 11.11.2026: po usunięciu `buildscript` `apply plugin: 'com.android.application'` rozwiązuje się poprawnie.

## 2. Zmiany w `:app` (rename, flavor, zależność od modułów)

W `app/build.gradle` (Groovy; plan, sekcja 13, krok 6). Szczegółowy opis ze stanem v12.2: `patches/manifest-and-appid.patch.md`.

1. Nowe `applicationId` (np. `app.thumbcast` albo `io.github.<login>.thumbcast`), nazwa aplikacji „Thumbcast”, własna ikona.
   W v12.2 `applicationId` jest ustawiony **we flavorach** (`nonRoot`: `com.limelight`, `root`: `com.limelight.root`), nie w `defaultConfig`,
   więc zmieniamy go we flavorze `nonRoot`. Typ `release` dodaje sufiks `.unofficial` (komentarz autora każe go usunąć po zmianie bazowego id),
   `debug` dodaje `.debug`. Etykieta aplikacji to `resValue "string", "app_label"` w `buildTypes` (obecnie „Moonlight”): zmienić na „Thumbcast”.
   Provider `PosterContentProvider` ma authority `poster.${applicationId}`, więc nic nie trzeba zmieniać; `shortcuts.xml` nie istnieje.
2. `minSdk 26` (plan, sekcja 3; w upstreamie `minSdk 21`). Flavor `root` ma `maxSdk 25`, więc po podniesieniu minSdk jest pusty:
   **usuń flavor `root`** (zostaje `nonRoot`, nazwy zadań bez zmian: `assembleNonRootDebug`, `assembleNonRootRelease`).
3. Zależność: `implementation project(':control-android')` (ten moduł dociąga `:control-engine` i `:control-model`).
4. Podpis release z właściwości Gradle, których używa `release.yml`:

```groovy
android {
    signingConfigs {
        release {
            if (project.hasProperty('thumbcastKeystoreFile')) {
                storeFile file(project.property('thumbcastKeystoreFile'))
                storePassword project.property('thumbcastKeystorePassword')
                keyAlias project.property('thumbcastKeyAlias')
                keyPassword project.property('thumbcastKeyPassword')
            }
        }
    }
    buildTypes { release { signingConfig signingConfigs.release } }
}
```

5. `release.yml`: `APP_ASSEMBLE_TASK` = `:app:assembleNonRootRelease` pasuje do flavoru `nonRoot` z v12.2. `APK_GLOB` (`app/build/outputs/apk/**/release/*.apk`)
   pasuje do konwencji AGP (`.../apk/nonRoot/release/`). Sprawdzone 11.11.2026 lokalnie z testowym keystore: powstaje
   `app-nonRoot-release.apk`, `apksigner verify` OK.

## 3. Zastosowanie `patches/`

Katalog `patches/` zawiera hooki w `Game.java` (preferencja `control_layer = legacy|thumbcast`, utworzenie `ThumbcastOverlay` nad `StreamView`, pominięcie `touchContextMap` dla palców, `releaseAll()` przy zatrzymaniu połączenia) oraz implementacje fasad `HostConnection` / `HostPadReporter` / `KeyTranslator` przeciw `NvConnection`, `ControllerHandler` i `KeyboardTranslator`. **Szczegóły i kolejność stosowania: `patches/APPLY.md` oraz `patches/*.patch.md`.** Opisy zostały porównane z kodem upstreamu v12.2 (commit `b48494cb96bf`, sprawdzone 10.11.2026), ale to opisy ręczne, **nie gotowe diffy** (patrz niżej). Jedna rzecz jest krytyczna: w trybie Thumbcast trzeba wymusić `onscreenController = true` w `PreferenceConfiguration.readPreferences`, inaczej bit 0 maski pada nie jest ustawiany (`patches/preferences.patch.md`, pkt 4).

Ogólna procedura:

```bash
git checkout -b thumbcast/hooks
# W patches/ nie ma plików .patch/.diff przeznaczonych dla `git apply` (są opisy *.patch.md z dosłownymi kotwicami z v12.2).
# Zastosuj zmiany ręcznie według patches/APPLY.md, a potem zapisz własny, pewny diff:
git diff v12.2 -- app/ > patches/0001-thumbcast-hooks.diff
# Nowe pliki Java: skopiuj patches/app/src/main/java/com/limelight/thumbcast/ do app/src/main/java/com/limelight/thumbcast/
```

Po zastosowaniu: zmiany w `Game.java` powinny mieścić się w ok. 50 liniach (plan, ryzyko R5).

## 4. Pierwszy build i testy

```bash
./gradlew :control-model:test :control-engine:test          # testy JVM
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
./gradlew :control-android:lint
./gradlew :app:assembleNonRootDebug                          # nazwa wariantu zgodna z v12.2; build niesprawdzony
```

Oczekuj poprawek: kod modułów był pisany bez kompilatora. Typowe miejsca: importy, wersje w `libs.versions.toml` (sekcja 6), konfiguracja built-in Kotlin w AGP 9 dla `:control-android`, plugin serializacji.

Potem w GitHub:
1. Wypchnij gałąź, sprawdź, czy workflow `ci` jest zielony.
2. Settings → Branches → reguła dla `main`: wymagane checki `jvm-tests` i `policy-tests`.
3. Settings → Secrets: `THUMBCAST_KEYSTORE_BASE64`, `THUMBCAST_KEYSTORE_PASSWORD`, `THUMBCAST_KEY_ALIAS`, `THUMBCAST_KEY_PASSWORD`.
4. Settings → Code security: włącz Private Vulnerability Reporting (SECURITY.md).

## 5. Pierwszy test T-03

Pełna procedura i tabela wyników: [test-plan.md, sekcja 4](test-plan.md#4-procedura-t-03-czy-mysz-przełącza-hud-gry-z-trybu-pada).

1. **Faza A od razu, jeszcze przed Thumbcast** (kroki A0–A7): oryginalny OSC upstreamu (już zbudowany w kroku 1) + tryb trackpad. Na PC: `gamepad_driver = vigembus` (lub licencja Virtual HID), `gamepad = x360`; w grze Enable Gamepad UI (Alpha) i Show Input Prompts.
2. Wypełnij tabelę 4.4, zapisz w `docs/tests/RRRR-MM-DD-T-03.md`, otwórz issue z szablonu „Wynik testu w grze”.
3. Podejmij decyzję z tabeli 4.5: Pad+ czy Pad-czysty. Zaktualizuj presety i plan.
4. **Faza B** (kroki B1–B7) po wpięciu overlaya z `patches/` i profilu „MMO – Pad+”.

## 6. Czego NIE dało się zweryfikować offline

**Aktualizacja 11.11.2026 (krok 1 z HANDOFF.md, Windows + Android Studio):** sekcje 1 i 2 wykonane wariantem „scalenie historii”.
Sprawdzone buildem: konfiguracja Gradle działa; Kotlin 2.4.21 (KGP `kotlin-jvm`) i built-in Kotlin z AGP 9.4.0 współistnieją bez konfliktu;
kod główny trzech modułów `control-*` się kompiluje; kotlinx-serialization 1.12.0 działa z Kotlin 2.4.21 (kompilacja `:control-model`);
`distributionSha256Sum` dodany; APK release to `app/build/outputs/apk/nonRoot/release/app-nonRoot-release-unsigned.apk` (bez podpisu),
więc `APK_GLOB` z `release.yml` pasuje. Upstream v12.2 nie ma `.github/workflows` (tylko szablony zgłoszeń i konfiguracje ProBot, te usunięte).
Nadal niesprawdzone: testy (Kotest, JUnit 6, Robolectric), lint, CI na GitHub Actions, patche.

Build i kod:
- Żaden plik Gradle nie był uruchomiony; nie wiadomo, czy build się konfiguruje.
- Kod Kotlin w modułach nie był kompilowany; testy nie były uruchamiane.
- Zgodność wersji w `gradle/libs.versions.toml` (wpisy „DO ZWERYFIKOWANIA”):
  - kotlinx-serialization 1.12.0 z Kotlin 2.4.21 (1.12.0 zbudowano z Kotlin 2.4.10);
  - Kotest 6.2.5 z Kotlin 2.4.21;
  - JUnit 6.1.3 (plan mówił „JUnit 5”) z kotlin-test-junit5 i Gradle 9.7.1;
  - JUnit 4.13.2 jako runner Robolectric (czy nie ma nowszego);
  - Robolectric 4.17 z compileSdk 37 i AGP 9.4.
- Działanie built-in Kotlin z AGP 9.4 w `:control-android` razem z pluginem `kotlin-serialization` (jeśli moduł go używa).
- Czy `kotlin-jvm` (KGP) i AGP 9.4 z built-in Kotlin mogą współistnieć w jednym buildzie bez konfliktu wersji KGP. DO ZWERYFIKOWANIA: AGP 9 może narzucać własną wersję KGP; wtedy wyrównać `kotlin` w katalogu.
- Suma SHA-256 dystrybucji Gradle 9.7.1 (brak `distributionSha256Sum`).
- Brak `gradlew` i `gradle-wrapper.jar` (celowo, przychodzą z upstreamem).

Upstream (stan po porównaniu z v12.2, commit `b48494cb96bf`, 10.11.2026):
- Zweryfikowane na kodzie: sygnatury `NvConnection.send*`, `ControllerHandler.reportOscState`, `KeyboardTranslator.translate(int, int)`, stałe
  `KeyboardPacket`/`MouseButtonPacket`/`ControllerPacket`/`Limelight.h`, flavory `root`/`nonRoot` i `applicationId`, `minSdk 21`, provider
  `poster.${applicationId}`, kolejność w `Game.onCreate()`, API preferencji (`android.preference`), `activity_game.xml` (`<merge>`).
- Nadal niesprawdzone: żaden z patchy nie był stosowany ani kompilowany; pełne ciała `Game.stopConnection()` i `onGenericMotion`,
  wszystkie wystąpienia `onscreenController` w `Game`, zawartość `ShortcutHelper`, ścieżka APK (konwencja AGP), czy upstream ma własny
  katalog `.github/workflows` do scalenia.

CI:
- **Aktualizacja 11.11.2026.** `ci.yml` (jobs `jvm-tests`, `policy-tests`, `lint`) jest zielony na GitHub Actions. Wszystkie workflowy
  używają `./gradlew` (bez fallbacku na `gradle`, bez `gradle-version` w setup-gradle). Nowy job `app-build` (niewymagany):
  NDK z `ndkVersion` w `app/build.gradle` przez `sdkmanager`, `:app:assembleNonRootDebug`, APK debug jako artefakt
  `thumbcast-debug-apk`, potem `:app:lintNonRootDebug` (lokalnie 0 błędów, ok. 260 ostrzeżeń, więc blokuje tylko nowe błędy).
  Job `app-build` nie był jeszcze uruchomiony na GitHub Actions.
- Lokalnie (Windows, 11.11.2026) przechodzą wszystkie polecenia Gradle z workflowów, w tym podpisany `:app:assembleNonRootRelease`
  z testowym keystore i `:control-android:assembleDebugAndroidTest`. Nieuruchomione: `release.yml`, `emulator-tests.yml`,
  `connectedDebugAndroidTest` (brak urządzenia), krok `sdkmanager` (lokalnie brak `cmdline-tools`).
- Składnia: PyYAML i actionlint 1.7.12 (bez shellcheck) bez uwag.
- Wersje akcji sprawdzone na stronach wydań 7.11.2026 (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7,
  android-emulator-runner v2.38.0) i 11.11.2026 (`actions/cache` v6.1.0, w `emulator-tests.yml` zmienione z v4).
- Obrazy emulatora `system-images;android-{26,34,36};default;x86_64` istnieją w repozytorium SDK Google (sprawdzone 11.11.2026).

Host i gra:
- Wszystkie [?] z planu: T-03, rozpoznawanie DS4 przez grę (Q1), Raw Input w KBM bez licencji (Q2), kontekst 0 vs osobny pad (Q3), traktowanie `100.64/10` przez Sunshine, MTU w Tailscale.

Dystrybucja i prawo:
- Kontrola znaków towarowych nazwy „Thumbcast” ([naming.md](naming.md)).
- Jak Android Developer Verification obejmie buildy F-Droid.
- Pełny tekst GPLv3 w `LICENSE` jest (kopia z Debiana, SHA-256 zgodny z oficjalnym `gpl-3.0.txt` z pamięci; porównać z gnu.org).
