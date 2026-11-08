# Patch: `applicationId`, nazwa, AndroidManifest, zależność od `:control-android`

Opis zmian w module `:app` upstreamu. Wartości `applicationId` i nazwy aplikacji są **robocze**
(plan 0 pkt 5 i sekcja 11; ostateczna nazwa w `docs/naming.md`, decyduje SCAFFOLD/autor).

## 1. `app/build.gradle` (Groovy, upstream)

Upstream: flavory `root` i `nonRoot`, `applicationId "com.limelight"` (plan 1.3 [Z:6][Z:7]) [?: dokładne
miejsce, czy suffix ustawiają flavory].

```groovy
  android {
      defaultConfig {
~         applicationId "app.thumbcast"           // było: com.limelight
~         minSdkVersion 26                        // było: 21 (plan 3: minSdk 26)
          // targetSdk 36, compileSdk 37 bez zmian
      }
      productFlavors {
          root {
~             applicationIdSuffix ".root"         // [?] jeśli upstream ma inny suffix, zachować schemat, zmienić tylko bazę
          }
          nonRoot { /* bez zmian */ }
      }
+     // THUMBCAST: namespace zostaje "com.limelight" – to pakiet klas R/BuildConfig, nie id aplikacji.
+     // Zmiana namespace wymusiłaby zmianę importów R w całym upstreamie (koszmar przy rebase).
  }

  dependencies {
+     // THUMBCAST:
+     implementation project(':control-android')
+     // THUMBCAST END
  }
```

Ważne: **nie zmieniamy pakietów Javy** `com.limelight.*` (rebase). Zmieniamy tylko `applicationId`,
dzięki czemu APK instaluje się obok oryginalnego Moonlight (kryterium E0).

## 2. `app/src/main/AndroidManifest.xml`

- Uprawnienie `VIBRATE`: biblioteka `:control-android` deklaruje je sama (scalanie manifestów);
  upstream najpewniej już je ma (`vibrateOsc`).
- Brak nowych aktywności w MVP.
- Authorities `ContentProvider`/`FileProvider`, jeśli upstream je deklaruje z literałem `com.limelight`
  [?], zmienić na `${applicationId}.…`, inaczej instalacja obok oryginału się nie powiedzie
  (`INSTALL_FAILED_CONFLICTING_PROVIDER`).
- Skróty (`shortcuts.xml`) z `android:targetPackage="com.limelight"` [?]: zmienić na nowy `applicationId`.
- `Game` ma w upstreamie własną orientację/konfigurację [?]; nie zmieniamy.

## 3. Nazwa i ikona

- `res/values/strings.xml`: `app_label` (albo `app_name` [?]) = „Thumbcast” (robocza). Bez „Moonlight”
  i bez „WoW” w nazwie aplikacji (CONTRACT, reguły).
- Ikona: własny zestaw `mipmap-*/ic_launcher*` (adaptacyjna, API 26). Stare ikony usunąć z forka,
  żeby nie używać brandingu upstreamu.
- Teksty „Moonlight” w UI upstreamu (np. tytuły ekranów) zostawić w MVP, jeśli są atrybucją,
  a zmienić te, które sugerują, że to oficjalna aplikacja Moonlight. Atrybucja w NOTICE (SCAFFOLD).

## 4. ProGuard/R8

`:control-android` dostarcza `consumer-rules.pro` (keep fabryki i fasad). Upstream nie musi nic dodawać.
