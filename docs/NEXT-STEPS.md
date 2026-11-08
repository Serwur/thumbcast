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
git checkout -b thumbcast/main            # gałąź z naszymi zmianami; master śledzi upstream
git submodule update --init --recursive  # moonlight-common-c i inne

# 2. Sprawdź, że czysty upstream się buduje (zanim cokolwiek zmienisz):
./gradlew assembleNonRootDebug           # DO ZWERYFIKOWANIA: nazwy wariantów (flavory root / nonRoot)
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
| `LICENSE.txt` upstreamu | Zachowaj. Nasz `LICENSE` (tylko nagłówek) zastąp pełnym tekstem albo usuń i zostaw `LICENSE.txt`. |
| `fastlane/metadata/android/` upstreamu | Zastąp naszymi metadanymi (inna nazwa, opis bez znaków towarowych). Usuń zrzuty i grafiki upstreamu, jeśli pokazują markę Moonlight. |
| `README.md` upstreamu | Zastąp naszym; dopisz link do upstreamu (już jest). |

`app/build.gradle` (Groovy) upstreamu może zostać w Groovy: plugin `com.android.application` jest na classpath przez nasz główny `build.gradle.kts`. DO ZWERYFIKOWANIA: czy `apply plugin:` / `plugins { id 'com.android.application' }` w `app/build.gradle` nie wymaga podania wersji po usunięciu `buildscript`.

## 2. Zmiany w `:app` (rename, flavor, zależność od modułów)

W `app/build.gradle` (plan, sekcja 13, krok 6):

1. Nowe `applicationId` (np. `io.github.<login>.thumbcast`), nazwa aplikacji „Thumbcast”, własna ikona. Sprawdź, że APK instaluje się **obok** oryginalnego Moonlight: zmień też `android:authorities` providerów w manifeście, jeśli są.
2. `minSdk 26` (plan, sekcja 3). DO ZWERYFIKOWANIA: czy upstreamowy kod z minSdk 21 nie ma ścieżek, które warto uprościć.
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

5. Ustaw w `release.yml` właściwe `APP_ASSEMBLE_TASK` i `APK_GLOB` (obecnie `:app:assembleNonRootRelease`, do potwierdzenia).

## 3. Zastosowanie `patches/`

Katalog `patches/` przygotowuje agent ANDROID: hooki w `Game.java` (preferencja `control_layer = legacy|thumbcast`, utworzenie `ThumbcastOverlay` nad `StreamView`, przekierowanie palców `TOOL_TYPE_FINGER` do overlaya, `releaseAll()` przy utracie fokusu) oraz implementacje fasad `HostConnection` / `HostPadReporter` przeciw `NvConnection` i `ControllerHandler`. **Szczegóły i kolejność stosowania są w opisie w `patches/` (README lub komentarze w plikach diff); ten dokument ich nie powtarza, bo w chwili pisania katalog był jeszcze pusty.**

Ogólna procedura:

```bash
git checkout -b thumbcast/hooks
# Najpierw sucha próba (diff był pisany offline, bez dostępu do aktualnego Game.java):
git apply --check --verbose patches/app/*.patch
# Jeśli przechodzi:
git apply --index patches/app/*.patch
# Jeśli nie: zastosuj ręcznie według opisu w patches/ i zapisz nowy, poprawny diff:
git diff upstream/master -- app/ > patches/app/0001-thumbcast-hooks.patch
```

Po zastosowaniu: zmiany w `Game.java` powinny mieścić się w ok. 50 liniach (plan, ryzyko R5).

## 4. Pierwszy build i testy

```bash
./gradlew :control-model:test :control-engine:test          # testy JVM
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
./gradlew :control-android:lint
./gradlew :app:assembleNonRootDebug                          # DO ZWERYFIKOWANIA nazwa wariantu
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

Upstream:
- Aktualna postać `Game.java`, `ControllerHandler.reportOscState` i `NvConnection` (patche pisane wg planu, nie wg aktualnego kodu).
- Nazwy wariantów builda (flavory `root`/`nonRoot`) i ścieżki APK w `release.yml`.
- Czy upstream ma własny katalog `.github/workflows` do scalenia.

CI:
- Workflowy przeszły tylko walidację składni YAML (PyYAML); nie były uruchomione na GitHub Actions.
- Wersje akcji sprawdzone na stronach wydań 7.11.2026 (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7, android-emulator-runner v2.38.0), z wyjątkiem `actions/cache@v4` (nie sprawdzono).
- Dostępność obrazów emulatora API 26/34/36 z `target: default`.

Host i gra:
- Wszystkie [?] z planu: T-03, rozpoznawanie DS4 przez grę (Q1), Raw Input w KBM bez licencji (Q2), kontekst 0 vs osobny pad (Q3), traktowanie `100.64/10` przez Sunshine, MTU w Tailscale.

Dystrybucja i prawo:
- Kontrola znaków towarowych nazwy „Thumbcast” ([naming.md](naming.md)).
- Jak Android Developer Verification obejmie buildy F-Droid.
- Pełny tekst GPLv3 w `LICENSE` (obecnie tylko nagłówek).
