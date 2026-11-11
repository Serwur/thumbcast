# Patch: `applicationId`, nazwa, AndroidManifest, zależność od `:control-android`

Zweryfikowane na upstreamie v12.2 (commit `b48494cb96bff23d8886c4775cc4f39a1075495d`, 12.09.2026):
`app/build.gradle` (Groovy), `app/src/main/AndroidManifest.xml`, `settings.gradle`, `build.gradle`.
Wartości `applicationId` i nazwy aplikacji są **robocze** (otwarta decyzja w `docs/DECISIONS.md`, pkt h; nazwa w `docs/naming.md`).

## Fakty o upstreamie (v12.2)

- `settings.gradle`: jedna linia `include ':app'`. Główny `build.gradle`: `classpath 'com.android.tools.build:gradle:9.4.0'`,
  repozytoria Maven Central, Google i JitPack (`allprojects`).
- `app/build.gradle`: `apply plugin: 'com.android.application'`, `ndkVersion "29.0.14206865"`, `compileSdk 37`,
  `namespace 'com.limelight'`, `defaultConfig { minSdk 21; targetSdk 36; versionName "12.2"; versionCode = 315 }`,
  `flavorDimensions.add("root")`, `buildFeatures { buildConfig = true; resValues = true }`,
  Java 17 + `coreLibraryDesugaringEnabled true`.
- **Flavory** (wymiar `root`):
  - `root`: `maxSdk 25`, `applicationId "com.limelight.root"`, `buildConfigField "boolean", "ROOT_BUILD", "true"`;
  - `nonRoot`: `applicationId "com.limelight"`, `buildConfigField "boolean", "ROOT_BUILD", "false"`.
  Nazwy wariantów: `assembleNonRootDebug`, `assembleNonRootRelease`, `assembleRootDebug`, `assembleRootRelease`.
  **`applicationId` jest ustawiony w flavorach, a nie w `defaultConfig`**; ustawienie go w `defaultConfig` nic by nie dało.
- **Typy buildu**: `debug` ma `applicationIdSuffix ".debug"` i `resValue "string", "app_label", "Moonlight (Debug)"` /
  `"app_label_root", "Moonlight (Root Debug)"`; `release` ma `applicationIdSuffix ".unofficial"`,
  `resValue ... "app_label", "Moonlight"` / `"app_label_root", "Moonlight (Root)"`, `minifyEnabled true` i `proguard-rules.pro`.
  Nad `.unofficial` jest długi komentarz autora: sufiks ma uniemożliwić wydawanie APK pod oficjalnym id; prosi, by
  przy własnym projekcie zmienić `applicationId` (to robimy), po czym sufiks jest zbędny.
- Manifest: jedyny provider to `.PosterContentProvider` z `android:authorities="poster.${applicationId}"`, więc **już** zależy od
  `applicationId` (żadna zmiana authorities nie jest potrzebna; wcześniejsze obawy z `INSTALL_FAILED_CONFLICTING_PROVIDER`
  były nieuzasadnione). `res/xml` zawiera tylko `backup_rules.xml`, `backup_rules_s.xml`, `game_mode_config.xml`,
  `locales_config.xml`, `network_security_config.xml`, `preferences.xml`; **nie ma `shortcuts.xml`**, skróty są
  tworzone dynamicznie z klasami (`new Intent(this, PcView.class)`), bez literału pakietu.
- Manifest ma już `VIBRATE`; `Game`: `singleTask`, `noHistory`, `excludeFromRecents`, PiP, `enableOnBackInvokedCallback="false"`.
  Aktywność `Game` nie ustawia `screenOrientation` w manifeście (orientację robi kod, `setPreferredOrientationForCurrentDisplay()`).

## 1. `app/build.gradle` (Groovy)

```groovy
  android {
      defaultConfig {
~         minSdk 26                                  // było: 21 (moduł :control-android ma minSdk 26; niższy minSdk aplikacji da błąd scalania manifestu)
          // targetSdk 36, compileSdk 37 bez zmian
      }
      productFlavors {
-         root { ... }                               // USUŃ cały flavor `root` (patrz niżej)
          nonRoot {
~             applicationId "app.thumbcast"          // było: "com.limelight"  (robocze; decyzja w DECISIONS.md)
              // reszta bez zmian (ndkBuild PRODUCT_FLAVOR=nonRoot, dimension "root", ROOT_BUILD=false)
          }
      }
      buildTypes {
          debug {
              applicationIdSuffix ".debug"                                // bez zmian
~             resValue "string", "app_label", "Thumbcast (Debug)"          // było: "Moonlight (Debug)"
~             resValue "string", "app_label_root", "Thumbcast (Debug)"     // było: "Moonlight (Root Debug)"
          }
          release {
-             applicationIdSuffix ".unofficial"                           // USUŃ po zmianie bazowego applicationId (komentarz autora upstreamu)
~             resValue "string", "app_label", "Thumbcast"                  // było: "Moonlight"
~             resValue "string", "app_label_root", "Thumbcast"             // było: "Moonlight (Root)"
              // minifyEnabled true i proguardFiles bez zmian
          }
      }
  }

  dependencies {
+     // THUMBCAST:
+     implementation project(':control-android')
+     // THUMBCAST END
  }
```

Dlaczego usuwamy flavor `root`: ma `maxSdk 25`, a nasz `minSdk` wynosi 26, więc wariant byłby pusty/niepoprawny. Wariant root służył
tylko Androidowi 5–7 (przechwytywanie myszy przez root); Android 8+ ma natywne przechwytywanie. Zostaje `nonRoot` (z `ROOT_BUILD=false`,
więc kod używający `BuildConfig.ROOT_BUILD` działa bez zmian). Nazwy zadań po zmianie: `:app:assembleNonRootDebug`,
`:app:assembleNonRootRelease` (flavor `nonRoot` zostaje, więc `release.yml` nie wymaga zmiany nazwy zadania).
Ścieżka APK (AGP, flavor `nonRoot`, typ `release`): `app/build/outputs/apk/nonRoot/release/app-nonRoot-release*.apk`
(zgodnie z konwencją AGP; **niesprawdzone** bez builda).

Podpis release z właściwości Gradle jest w `docs/NEXT-STEPS.md`, pkt 2. Upstream nie ma `signingConfigs`.

`namespace 'com.limelight'` zostaje: to pakiet klas `R`/`BuildConfig`, nie `applicationId`. Zmiana wymusiłaby zmianę importów `R` w całym
upstreamie (kłopot przy rebase). Pakietów Javy `com.limelight.*` nie zmieniamy. Nasze nowe klasy w `com.limelight.thumbcast`
mają w nazwie pakietu `limelight` (upstream), nie „Moonlight”, więc zasada nazewnictwa jest zachowana.

## 2. `app/src/main/AndroidManifest.xml`

- Uprawnienie `VIBRATE`: upstream ma je już (`:control-android` deklaruje je też sam; manifesty się scalą).
- Brak nowych aktywności w MVP.
- Provider `.PosterContentProvider`: authority to `poster.${applicationId}`, **bez zmian**.
- Skróty: brak statycznego `shortcuts.xml`, nic do zmiany.
- `Game` nie zmieniamy.

## 3. Nazwa i ikona

- Etykieta aplikacji pochodzi z `resValue "app_label"` w `buildTypes` (pkt 1), a nie z `strings.xml`.
  Manifest odwołuje się do `@string/app_label` (i `app_label_root` dla flavoru root) [odwołań w manifeście nie cytuję, bo odczyt był skrócony].
  Po usunięciu flavoru `root` `app_label_root` może zostać nieużywany (zostaw `resValue`, żeby manifest się budował).
- Ikona: własny zestaw `mipmap-*/ic_launcher*` (adaptacyjna). Stare ikony usunąć z forka, żeby nie używać brandingu upstreamu.
  Manifest ma też baner TV (`android:banner`): podmienić lub usunąć.
- Teksty „Moonlight” w UI upstreamu (np. tytuły ekranów, `HelpActivity`) zostawić w MVP, jeśli są atrybucją,
  a zmienić te, które sugerują, że to oficjalna aplikacja Moonlight. Atrybucja w NOTICE (SCAFFOLD).
- **Stan (11.11.2026):** zrobione inaczej niż wyżej: zamiast usuwać ikony i edytować `strings.xml` upstreamu
  nadpisujemy je zasobami flavoru w `app/src/nonRoot/res/` (mniej konfliktów przy rebase). Ikona i baner TV to
  placeholdery. Audyt, decyzje i weryfikacja: `docs/branding.md`.

## 4. ProGuard/R8

Upstream ma `minifyEnabled true` w `release`. `:control-android` dostarcza `consumer-rules.pro` (keep fabryki i fasad). Klasy
`com.limelight.thumbcast.*` są wołane wprost z `Game`, więc R8 ich nie usunie.
