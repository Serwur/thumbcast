# Patch: preferencje (`PreferenceConfiguration`, `StreamSettings`, `res/xml/preferences.xml`, stringi)

Zweryfikowane na upstreamie v12.2 (commit `b48494cb96bff23d8886c4775cc4f39a1075495d`, 12.09.2026).
Opis zmian krok po kroku (nie `git diff`; patrz uwaga w `Game.java.patch.md`).

> Stan po 11.11.2026 różni się od kroków poniżej: wybór profilu to `Preference` otwierająca `ProfileLibraryActivity`
> (bez tablic `thumbcast_profile_*`), domyślny rozmiar 100%, a od 12.11.2026 domyślny profil `gamepad-playstation.thumbcast.json`
> (sekcja 7). Pewny stan: `0001-thumbcast.diff`.

Co sprawdzono:
- `res/xml/preferences.xml`: korzeń `<PreferenceScreen>` z `xmlns:android` i `xmlns:seekbar`. Kategorie w kolejności:
  `category_basic_settings`, (audio, bez klucza), `category_gamepad_settings`, `category_input_settings`,
  **`category_onscreen_controls`**, (host, bez klucza), `category_ui_settings`, `category_advanced_settings`.
  Dzieci `category_onscreen_controls`: `checkbox_show_onscreen_controls`, `checkbox_vibrate_osc`, `checkbox_only_show_L3R3`,
  `checkbox_show_guide_button`, `seekbar_osc_opacity` (wszystkie poza pierwszym mają `android:dependency="checkbox_show_onscreen_controls"`)
  oraz `ConfirmDeleteOscPreference` (`title_reset_osc`).
- `StreamSettings extends Activity`, zagnieżdżony `public static class SettingsFragment extends PreferenceFragment`
  z **`android.preference.PreferenceFragment`** (stare API, nie androidx). Fragment ma `public void onCreate(Bundle savedInstanceState)`
  z `addPreferencesFromResource(R.xml.preferences);` (nie ma `onCreatePreferences`). Kategorię OSC znajduje przez
  `findPreference("category_onscreen_controls")`; na urządzeniach bez ekranu dotykowego usuwa ją przez `screen.removePreference(category)`.
- `PreferenceConfiguration`: `public static PreferenceConfiguration readPreferences(Context context)`, lokalna zmienna
  `SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);`, pola `public int width, height, fps;`,
  `public boolean onscreenController; onlyL3R3; vibrateOsc;`, stała `ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls"`,
  `ONSCREEN_CONTROLLER_DEFAULT = false`. Metoda kończy się `return config;`.

## 1. `res/xml/preferences.xml`

Nowa kategoria wstawiona **bezpośrednio przed** `<PreferenceCategory android:key="category_onscreen_controls" ...>`
(żeby było widać, że legacy OSC to druga opcja). Nowy `ListPreference` nie ma `android:dependency` (patrz uwaga niżej):

```xml
+ <!-- THUMBCAST: -->
+ <PreferenceCategory
+     android:key="category_thumbcast"
+     android:title="@string/category_thumbcast">
+
+     <ListPreference
+         android:key="list_control_layer"
+         android:title="@string/title_control_layer"
+         android:summary="%s"
+         android:entries="@array/control_layer_names"
+         android:entryValues="@array/control_layer_values"
+         android:defaultValue="thumbcast" />
+
+     <ListPreference
+         android:key="list_thumbcast_profile"
+         android:title="@string/title_thumbcast_profile"
+         android:summary="%s"
+         android:entries="@array/thumbcast_profile_names"
+         android:entryValues="@array/thumbcast_profile_values"
+         android:defaultValue="mmo-pad-plus.thumbcast.json" />
+
+     <CheckBoxPreference
+         android:key="checkbox_thumbcast_debug"
+         android:title="@string/title_thumbcast_debug"
+         android:summary="@string/summary_thumbcast_debug"
+         android:defaultValue="false" />
+ </PreferenceCategory>
+ <!-- THUMBCAST END -->
```

Uwaga: `android:dependency` działa tylko dla nadrzędnej preferencji typu boolean, więc dla `ListPreference`
go nie używamy; włączanie/wyłączanie profilu robi kod w `StreamSettings` (pkt 5).

## 2. `res/values/arrays.xml` (albo nowy plik `res/values/thumbcast_arrays.xml`, mniej konfliktów)

```xml
+ <string-array name="control_layer_names">
+     <item>@string/control_layer_thumbcast</item>
+     <item>@string/control_layer_legacy</item>
+ </string-array>
+ <string-array name="control_layer_values" translatable="false">
+     <item>thumbcast</item>
+     <item>legacy</item>
+ </string-array>
+ <string-array name="thumbcast_profile_names">
+     <item>MMO – Pad+</item>
+     <item>MMO – Klawiatura</item>
+ </string-array>
+ <string-array name="thumbcast_profile_values" translatable="false">
+     <item>mmo-pad-plus.thumbcast.json</item>
+     <item>mmo-keyboard.thumbcast.json</item>
+ </string-array>
```

## 3. `res/values/strings.xml` (i `values-pl/strings.xml`), najlepiej w nowym pliku `thumbcast_strings.xml`

| Klucz | EN | PL |
|---|---|---|
| `category_thumbcast` | Touch controls | Sterowanie dotykowe |
| `title_control_layer` | Control layer | Warstwa sterowania |
| `control_layer_thumbcast` | Thumbcast (multitouch, profiles) | Thumbcast (multitouch, profile) |
| `control_layer_legacy` | Classic on-screen controller | Klasyczny kontroler ekranowy |
| `title_thumbcast_profile` | Control profile | Profil sterowania |
| `title_thumbcast_debug` | Input debug overlay | Nakładka debug wejścia |
| `summary_thumbcast_debug` | Shows active pointers, sent pad flags and touch-to-send latency | Pokazuje aktywne wskaźniki, wysłane flagi pada i opóźnienie dotyk→wysyłka |
| `thumbcast_profile_error` | Control profile could not be loaded, using classic controller: %1$s | Nie udało się wczytać profilu, używam klasycznego kontrolera: %1$s |

## 4. `preferences/PreferenceConfiguration.java` (pola, odczyt, **wymuszenie `onscreenController`**)

```java
+ // THUMBCAST:
+ public enum ControlLayer { LEGACY, THUMBCAST }
+ private static final String CONTROL_LAYER_PREF_STRING = "list_control_layer";
+ private static final String THUMBCAST_PROFILE_PREF_STRING = "list_thumbcast_profile";
+ private static final String THUMBCAST_DEBUG_PREF_STRING = "checkbox_thumbcast_debug";
+ private static final String DEFAULT_CONTROL_LAYER = "thumbcast";
+ private static final String DEFAULT_THUMBCAST_PROFILE = "mmo-pad-plus.thumbcast.json";
+
+ public ControlLayer controlLayer;
+ public String thumbcastProfileFile;
+ public boolean thumbcastDebugOverlay;
+ // THUMBCAST END
```

W `readPreferences(Context context)` **zaraz po** (dosłownie z v12.2):

```java
config.onscreenController = prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, ONSCREEN_CONTROLLER_DEFAULT);
```

dodaj:

```java
+ // THUMBCAST:
+ String layer = prefs.getString(CONTROL_LAYER_PREF_STRING, DEFAULT_CONTROL_LAYER);
+ config.controlLayer = "legacy".equals(layer) ? ControlLayer.LEGACY : ControlLayer.THUMBCAST;
+ config.thumbcastProfileFile = prefs.getString(THUMBCAST_PROFILE_PREF_STRING, DEFAULT_THUMBCAST_PROFILE);
+ config.thumbcastDebugOverlay = prefs.getBoolean(THUMBCAST_DEBUG_PREF_STRING, false);
+ if (config.controlLayer == ControlLayer.THUMBCAST) {
+     // KRYTYCZNE: upstream ustawia bit 0 maski pada (kontroler nr 0, do którego pisze reportOscState)
+     // WYŁĄCZNIE przy onscreenController: ControllerHandler.getAttachedControllerMask() (czyta preferencje
+     // przez readPreferences, nie pole prefConfig), getActiveControllerMask() (multiController) i Game.onCreate
+     // (gamepadMask |= 1). Poza tym ta flaga włącza landscape w setPreferredOrientationForCurrentDisplay()
+     // oraz rumble telefonem w ControllerHandler.handleRumble(). Wymuszamy ją więc w jedynym miejscu.
+     config.onscreenController = true;
+ }
+ // THUMBCAST END
```

Skutek uboczny: gdy ładowanie profilu się nie uda, `Game` tworzy upstreamowy `VirtualController`
(`prefConfig.onscreenController` jest `true`), czyli fallback do legacy działa bez dodatkowego kodu.
Ścieżki `Game`, które używają `prefConfig.onscreenController`, to (v12.2): maska pada w `onCreate`, tworzenie
`VirtualController`, `setPreferredOrientationForCurrentDisplay()`; w pliku może być więcej wystąpień poza
fragmentem, który przeczytałem (odczyt był obcięty przy ok. 100 tys. znaków z 120 tys.): zrób `grep -n onscreenController`.

Domyślna wartość `thumbcast` oznacza, że nowa instalacja startuje z naszą warstwą (inne `applicationId` niż upstream,
więc preferencje oryginału nie są dziedziczone).

## 5. `preferences/StreamSettings.java`

Stare API (`android.preference.*`), więc `findPreference(CharSequence)` zwraca `Preference`. W `SettingsFragment.onCreate`,
**po** `addPreferencesFromResource(R.xml.preferences);` (i najlepiej po istniejących usunięciach preferencji dla urządzeń bez
dotyku/wibracji, żeby `findPreference` nie zwracało `null` dla usuniętych):

```java
+ // THUMBCAST: ustawienia OSC legacy mają sens tylko w trybie legacy, profil tylko w trybie thumbcast.
+ final ListPreference layer = (ListPreference) findPreference("list_control_layer");
+ final Preference profile = findPreference("list_thumbcast_profile");
+ final Preference osc = findPreference("checkbox_show_onscreen_controls");
+ final Preference vibrate = findPreference("checkbox_vibrate_osc");
+ if (layer != null) {
+     Preference.OnPreferenceChangeListener l = (pref, value) -> {
+         boolean tc = "thumbcast".equals(value);
+         if (profile != null) profile.setEnabled(tc);
+         if (osc != null) osc.setEnabled(!tc);
+         // Wibracja telefonu przy rumble (vibrateOsc) działa też w trybie Thumbcast, więc nie może zależeć od
+         // checkbox_show_onscreen_controls (który w tym trybie jest wyszarzony i ma wartość false).
+         if (vibrate != null) vibrate.setDependency(tc ? null : "checkbox_show_onscreen_controls");
+         return true;
+     };
+     l.onPreferenceChange(layer, layer.getValue());
+     layer.setOnPreferenceChangeListener(l);
+ }
+ // THUMBCAST END
```

Uwaga: `checkbox_only_show_L3R3`, `checkbox_show_guide_button` i `seekbar_osc_opacity` zależą od `checkbox_show_onscreen_controls`
i w trybie Thumbcast zostają wyszarzone (przezroczystość bierze overlay z profilu, `GlobalSettings.opacity`).
Jeśli `ListPreference` w XML nie ma domyślnej wartości zapisanej jeszcze w `SharedPreferences`, `layer.getValue()` zwraca
`android:defaultValue` po zainicjalizowaniu przez `addPreferencesFromResource`; sprawdź na urządzeniu.

## 6. Assety z presetami

Presety z katalogu `presets/` repo kopiujemy do `app/src/main/assets/presets/` zadaniem Gradle
(nie ręcznie, żeby nie rozjechały się z walidowanymi plikami):

```groovy
// app/build.gradle (upstream jest w Groovy; zweryfikowane: `apply plugin: 'com.android.application'`)
android {
    sourceSets {
        main {
            assets.srcDir layout.buildDirectory.dir("generated/thumbcastAssets")
        }
    }
}
def copyPresets = tasks.register('copyThumbcastPresets', Copy) {
    from(rootProject.file('presets')) { include '*.thumbcast.json' }
    into layout.buildDirectory.dir('generated/thumbcastAssets/presets')
}
tasks.named('preBuild') { dependsOn copyPresets }
```
(Składnia Groovy z `layout.buildDirectory` w `srcDir` jest nieprzetestowana; w razie problemu użyć
`"$buildDir/generated/thumbcastAssets"`. Task `preBuild` istnieje w module aplikacji AGP.)
`ThumbcastProfiles.load(context, name)` czyta najpierw `filesDir/profiles/<name>`, potem `assets/presets/<name>`.

## 7. Profil domyślny „Pad – PlayStation” i gesty myszy (12.11.2026)

Zmiany w plikach upstreamu (tylko bloki `THUMBCAST`), kotwice z v12.2:

- `PreferenceConfiguration.java`, blok `// THUMBCAST:` po `public boolean onscreenController;`:
  `DEFAULT_THUMBCAST_PROFILE = app.thumbcast.android.ThumbcastProfiles.DEFAULT_PRESET` (jedno źródło; wartość
  `gamepad-playstation.thumbcast.json`). `readPreferences` czyta `list_thumbcast_profile` z tym domyślnym jak dotąd.
- `StreamSettings.java`, blok `// THUMBCAST: podsumowanie ...` w `SettingsFragment.onResume`: podsumowanie preferencji
  `list_thumbcast_profile` = `thumbcast_profile_summary` („Domyślny: <nazwa>”) z `ProfileLibraryActivity.defaultProfileName`.
- `res/xml/preferences.xml` bez zmian: `list_thumbcast_profile` to `Preference` bez `android:defaultValue` i z
  `android:persistent="false"`, więc `PreferenceManager.setDefaultValues` (`PcView.onCreate`) niczego nie zapisuje.
  Kto nie wybrał profilu, dostaje `ThumbcastProfiles.DEFAULT_PRESET`, także po zmianie domyślnego w nowej wersji.
  Wyjątek: wersje z 11.11.2026 sprzed biblioteki profili (`ListPreference` z `android:defaultValue`, commity
  `a8330da2`..`4cd7c9bd`) mogły przy pierwszym uruchomieniu zapisać `mmo-pad-plus` albo `gamepad-xbox`; takiego wpisu
  nie da się odróżnić od wyboru użytkownika, więc nie jest migrowany (w bibliotece wystarczy „Ustaw jako domyślny”).

Reszta zmian jest w plikach Thumbcast (`com.limelight.thumbcast`, `res/*/thumbcast_*.xml`,
`layout/activity_thumbcast_profile_library.xml`), bez kotwic w upstreamie:
`ProfileLibraryActivity` (akcja „Ustaw jako domyślny”, znacznik „★ Domyślny”, podpowiedź pod tytułem, usunięcie profilu
domyślnego usuwa preferencję i pokazuje nazwę presetu dostarczanego z aplikacją, przełącznik „Gesty myszy” w opcjach
profilu), `ThumbcastSettings` (klucze `thumbcast_mouse_gestures:<plik profilu>`), `ThumbcastSession` (profil efektywny
`MouseGesturesTransform` → `ScaleTransform`, wiersz menu, zapasowy profil domyślny przez
`AppStreamOverrides.selectProfile(..., ThumbcastProfiles.DEFAULT_PRESET)`), `ThumbcastMenuModel` (wiersz
„Gesty myszy: wł./wył.”, ★ przy profilu domyślnym na stronie profili), `ThumbcastAppSettingsDialog`
(`ProfileLibraryActivity.defaultProfileFile`).
