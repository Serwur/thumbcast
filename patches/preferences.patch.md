# Patch: preferencje (`PreferenceConfiguration`, `StreamSettings`, `res/xml/preferences.xml`, stringi)

Opis zmian bez kodu upstreamu. Nazwy istniejących kluczy (`checkbox_show_onscreen_controls`,
`seekbar_osc_opacity`, `checkbox_vibrate_osc`, …) pochodzą z planu 2.1 [Z:6e]; strukturę plików
(PreferenceCategory, `ListPreference`) zakładam jako typową dla upstreamu [?].

## 1. `res/xml/preferences.xml`

Nowa kategoria **przed** kategorią z ustawieniami OSC (żeby było widać, że legacy OSC to druga opcja):

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
+         android:defaultValue="mmo-pad-plus.thumbcast.json"
+         android:dependency="list_control_layer" />
+
+     <CheckBoxPreference
+         android:key="checkbox_thumbcast_debug"
+         android:title="@string/title_thumbcast_debug"
+         android:summary="@string/summary_thumbcast_debug"
+         android:defaultValue="false" />
+ </PreferenceCategory>
+ <!-- THUMBCAST END -->
```

Uwaga: `android:dependency` działa tylko dla preferencji typu boolean (wyłącza zależną, gdy
nadrzędna jest „false”). Dla `ListPreference` to nie zadziała; usunąć atrybut i ukrywać profil
w `StreamSettings` (pkt 3). Zostawione w opisie celowo, żeby nie zostało dodane przy kopiowaniu.

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

## 4. `preferences/PreferenceConfiguration.java`

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

W statycznej metodzie odczytu (`readPreferences(Context)` [?: nazwa w upstreamie]) obok
odczytu `onscreenController`:

```java
+ // THUMBCAST:
+ String layer = prefs.getString(CONTROL_LAYER_PREF_STRING, DEFAULT_CONTROL_LAYER);
+ config.controlLayer = "legacy".equals(layer) ? ControlLayer.LEGACY : ControlLayer.THUMBCAST;
+ config.thumbcastProfileFile = prefs.getString(THUMBCAST_PROFILE_PREF_STRING, DEFAULT_THUMBCAST_PROFILE);
+ config.thumbcastDebugOverlay = prefs.getBoolean(THUMBCAST_DEBUG_PREF_STRING, false);
+ // THUMBCAST END
```

Decyzja do potwierdzenia: domyślna wartość `thumbcast` oznacza, że nowa instalacja startuje
z naszą warstwą. Przy aktualizacji z czystego upstreamu (inne `applicationId`, więc w praktyce nie
występuje) preferencji nie ma, więc też `thumbcast`.

## 5. `preferences/StreamSettings.java`

W fragmencie ustawień (`SettingsFragment.onCreatePreferences`/`onCreate` [?]):

```java
+ // THUMBCAST: ustawienia OSC legacy mają sens tylko w trybie legacy, profil tylko w trybie thumbcast.
+ ListPreference layer = (ListPreference) findPreference("list_control_layer");
+ Preference profile = findPreference("list_thumbcast_profile");
+ Preference osc = findPreference("checkbox_show_onscreen_controls");
+ Preference.OnPreferenceChangeListener l = (pref, value) -> {
+     boolean tc = "thumbcast".equals(value);
+     profile.setEnabled(tc);
+     if (osc != null) osc.setEnabled(!tc);
+     return true;
+ };
+ l.onPreferenceChange(layer, layer.getValue());
+ layer.setOnPreferenceChangeListener(l);
+ // THUMBCAST END
```

Jeśli upstream używa `android.preference.*` (stare API) zamiast `androidx.preference.*` [?],
rzutowania i nazwy metod są te same; zmienia się tylko import.

## 6. Assety z presetami

Presety z katalogu `presets/` repo kopiujemy do `app/src/main/assets/presets/` zadaniem Gradle
(nie ręcznie, żeby nie rozjechały się z walidowanymi plikami):

```kotlin
// app/build.gradle(.kts) [?: upstream ma Groovy]
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/thumbcastAssets"))
val copyPresets = tasks.register<Copy>("copyThumbcastPresets") {
    from(rootProject.file("presets")) { include("*.thumbcast.json") }
    into(layout.buildDirectory.dir("generated/thumbcastAssets/presets"))
}
tasks.named("preBuild") { dependsOn(copyPresets) }
```
`ThumbcastProfiles.load(context, name)` czyta najpierw `filesDir/profiles/<name>`, potem `assets/presets/<name>`.
