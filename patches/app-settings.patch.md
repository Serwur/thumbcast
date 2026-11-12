# Patch: ustawienia Thumbcast dla aplikacji hosta (profil, rozdzielczość, FPS)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Pewny stan: `0001-thumbcast.diff`.
Logika bez Androida: `control-android/src/main/kotlin/app/thumbcast/android/apps/` (`AppStreamSettings.kt`: dane, dopasowanie,
JSON; `AppStreamOverrides.kt`: nadpisanie strumienia i wybór profilu z fallbackiem), testy `AppStreamSettingsTest`
(`:control-android:testDebugUnitTest`). Opis dla użytkownika i konfiguracja hosta: `docs/host-apps.md`.

## Co upstream daje (v12.2)

- `AppView` zna `computer.uuid` (`uuidString`, extra `UUID`) i `NvApp` (`getAppId()`, `getAppName()`). Ukryte aplikacje upstream
  trzyma w SharedPreferences `HiddenApps`: klucz = uuid PC, wartość = zbiór id aplikacji. Robimy tak samo (osobny plik).
- Każda droga startu strumienia (`AppView`, `PcView` „Wznowienie sesji”, `ShortcutTrampoline`) idzie przez
  `ServerHelper.createStartIntent`, który podaje `Game` extra `AppId` (int), `AppName` i `UUID` (uuid PC). Wznowienie z `PcView`
  podaje nazwę zastępczą `"app"`, `ShortcutTrampoline` przy wznowieniu `null`, stąd dopasowanie także po samym id.
- `Game.onCreate` czyta `prefConfig = PreferenceConfiguration.readPreferences(this)` i dalej używa `prefConfig.width/height/fps`
  (orientacja, wcięcia, tryb ekranu, `StreamConfiguration.setResolution/setLaunchRefreshRate/setRefreshRate`; do hosta idą w `/launch`
  jako `mode=<w>x<h>x<fps>`, `NvHTTP.launchApp`). Nadpisanie pól tego obiektu zaraz po odczycie zmienia tylko ten strumień.
- Id aplikacji w Sunshine: CRC32 z nazwy i SHA-256 obrazka (`src/process.cpp`, `calculate_app_id`), więc stałe, dopóki nie zmienisz
  nazwy ani obrazka.

## Zmiany

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `AppView.java` | `import com.limelight.preferences.PreferenceConfiguration;` | import `ThumbcastAppSettingsDialog` |
| | `private final static int HIDE_APP_ID = 7;` | stała `THUMBCAST_APP_SETTINGS_ID = 8` |
| | `menu.add(Menu.NONE, VIEW_DETAILS_ID, 4, ...applist_menu_details));` | pozycja „Ustawienia Thumbcast dla aplikacji” |
| | `case CREATE_SHORTCUT_ID:` (w `onContextItemSelected`) | przed nim `case THUMBCAST_APP_SETTINGS_ID` → `ThumbcastAppSettingsDialog.show(...)` |
| `Game.java` | `import com.limelight.thumbcast.ThumbcastSession;` (blok THUMBCAST) | import `ThumbcastAppSettings` |
| | `prefConfig = PreferenceConfiguration.readPreferences(this);` | zaraz po nim `ThumbcastAppSettings.applyToStream(this, prefConfig, getIntent());` |
| `preferences/PreferenceConfiguration.java` | blok THUMBCAST z `public String thumbcastProfileFile;` | pole `thumbcastAppProfileFile` (nie z preferencji; ustawia `applyToStream`) |
| `res/values{,-pl}/thumbcast_strings.xml` | koniec pliku | teksty `thumbcast_app_settings_*`, `thumbcast_app_profile_missing`, `thumbcast_menu_profile_session_only` |

Nowe pliki w `com.limelight.thumbcast`: `ThumbcastAppSettings` (zapis w SharedPreferences `ThumbcastAppSettings`, klucz = uuid PC
małymi literami, wartość = JSON `{"version":1,"apps":[{"appId","appName","profile","width","height","fps"}]}`; `applyToStream`)
i `ThumbcastAppSettingsDialog` (dialog ze spinnerami: profil z `ProfileRepository.listAll()` albo domyślny, `resolution_values`
i `fps_values` upstreamu albo ustawienie globalne). `ThumbcastSession.createOrNull` wybiera profil przez
`AppStreamOverrides.selectProfile` (profil aplikacji, a gdy go brak lub jest niepoprawny: globalny + Toast), a zmiana profilu
w menu w grze przy profilu aplikacji nie zapisuje `list_thumbcast_profile`.

`:control-android` dostał `implementation(libs.kotlinx.serialization.json)` (tylko API `JsonElement`, bez pluginu).

## Przy konflikcie z nowym upstreamem

Bloki są krótkie i niezależne. W `Game.java` hak musi stać po `readPreferences`, a przed pierwszym użyciem `prefConfig.width`
(`shouldIgnoreInsetsForResolution`, `setPreferredOrientationForCurrentDisplay`). W `AppView` id pozycji menu (8) nie może się
powtórzyć z id upstreamu.
