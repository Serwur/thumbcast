# Patch: fundament przebudowy UI (pakiet P0)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Specyfikacja: `docs/ui-audit.md`
(D1, D2, D6, D8, U7, U12). Pewny stan po scaleniu wszystkich pakietów: nowy `git diff v12.2 -- app/` (`0001-thumbcast.diff`
odświeża orkiestrator).

## Zmiany w plikach upstreamu

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | `<application ... android:theme="@style/AppTheme">` | `android:theme="@style/Theme.Thumbcast"`; blok `<!-- THUMBCAST -->` przed znacznikiem `<application>`, `<!-- THUMBCAST END -->` zaraz po nim (komentarz nie może stać wewnątrz znacznika). `Game` ma własny `android:theme="@style/StreamTheme"` i zostaje bez zmian. |
| | `android:name=".preferences.StreamSettings"` | `android:label="Streaming Settings"` → `@string/thumbcast_label_settings` (blok THUMBCAST wokół całej aktywności) |
| | `android:name=".preferences.AddComputerManually"` | `android:label="Add Computer Manually"` → `@string/thumbcast_label_add_pc` (blok THUMBCAST wokół całej aktywności) |
| `app/build.gradle` | `implementation project(':control-editor')` | pod nim `implementation project(':control-ui')` z komentarzem `THUMBCAST` |

Motyw na poziomie `<application>` zamiast na pięciu aktywnościach: obejmuje `PcView`, `AppView`, `StreamSettings`,
`AddComputerManually`, `ProfileLibraryActivity`, a także `ShortcutTrampoline` i `HelpActivity` (też ekrany poza strumieniem)
i każdą nową aktywność `:app`. `Theme.Thumbcast` dziedziczy po `AppTheme`, więc zostaje `Theme.Material` bez paska akcji,
a na API 29+ `forceDarkAllowed=false` (`values-v29/styles.xml`).

Etykiety usług (`DiscoveryService`, `ComputerManagerService`, `UsbDriverService`) zostają po angielsku: użytkownik ich
nie widzi poza systemową listą działających usług.

## Nowe pliki w `app/` (bez zmian upstreamu)

- `res/values/thumbcast_colors.xml`: paleta D1 (`thumbcast_bg`, `thumbcast_surface`, `thumbcast_surface_raised`, `thumbcast_outline`,
  `thumbcast_outline_strong`, `thumbcast_text`, `thumbcast_text_secondary`, `thumbcast_accent`, `thumbcast_on_accent`,
  `thumbcast_success`, `thumbcast_warning`, `thumbcast_error`, `thumbcast_error_container`, `thumbcast_*_dim`,
  `thumbcast_scrim`, `thumbcast_menu_surface`, `thumbcast_divider`).
- `res/color/thumbcast_*`: listy stanów (tekst, przyciski) z wyszarzeniem dla `state_enabled=false`.
- `res/values/thumbcast_dimens.xml`: odstępy, obszar dotyku, rogi, rozmiary tekstu.
- `res/values/thumbcast_styles.xml`: `Theme.Thumbcast`, `Theme.Thumbcast.Dialog`, style `ThumbcastText.*`, `ThumbcastButton.*`,
  `ThumbcastIconButton`, `ThumbcastListItem`, `ThumbcastCard`, `ThumbcastDivider`.
- `res/drawable/thumbcast_bg_*`: karta, przyciski, plakietki.
- `res/drawable/thumbcast_ic_*`: wspólne ikony D6 (Material Symbols Rounded, Apache-2.0, wpis w `NOTICE`).
- `res/values/thumbcast_strings_common.xml`, `res/values-pl/thumbcast_strings_common.xml`: wspólne słowa i etykiety z manifestu.

## Sprawdzenie

`./gradlew :control-ui:testDebugUnitTest :control-editor:testDebugUnitTest :control-android:testDebugUnitTest
:app:assembleNonRootDebug :app:lintNonRootDebug :control-ui:lintDebug :control-editor:lintDebug`.
Na urządzeniu jeszcze nie sprawdzone.
