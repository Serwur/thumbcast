# Patch: ustawienia i biblioteka profili (pakiet P3)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Specyfikacja:
`docs/ui-audit.md` (U4, U5, U6, U14; D3, D4, D5, D7). Pewny stan po scaleniu wszystkich pakietów: nowy
`git diff v12.2 -- app/` (`0001-thumbcast.diff` odświeża orkiestrator).

## Zmiany w plikach upstreamu

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `preferences/StreamSettings.java` | importy | blok THUMBCAST: `ActivityNotFoundException`, `Insets`, widgety, `ThumbcastSettings`, `UiContract`, `OnboardingResult` |
| | `static DisplayCutout displayCutoutP;` | pole `thumbcastReopenAdvanced` (blok THUMBCAST) |
| | `SettingsFragment.onResume` (blok THUMBCAST z 11.11.) | w tym samym bloku: `onActivityResult` (wynik kreatora), `onActivityCreated` i `onDestroy` (ponowne otwarcie „Zaawansowane” po `reloadSettings()`), pomocnicze `thumbcastAddHeader`, `thumbcastDecorateNestedScreen`, `thumbcastOpenAdvanced`, `thumbcastStart` |
| | `SettingsFragment.onCreateView`, po `applyStatusBarPadding` | nagłówek listy: strzałka wstecz (`getActivity().onBackPressed()`, ta sama ścieżka co systemowy Back) i tytuł „Ustawienia” |
| | `addPreferencesFromResource(R.xml.preferences)` | `R.xml.thumbcast_preferences` |
| | blok THUMBCAST „ustawienia OSC legacy…” (11.11.) | rozszerzony, patrz niżej |

Upstreamowy `res/xml/preferences.xml` zostaje bez zmian (nadal używa go `PcView`:
`PreferenceManager.setDefaultValues(this, R.xml.preferences, false)`). **Nowa preferencja z wartością domyślną musi
trafić do obu plików**, inaczej domyślna wartość nie zostanie zapisana przy pierwszym uruchomieniu.

### Rozszerzony blok w `SettingsFragment.onCreate`

Upstreamowy kod przed blokiem usuwa pozycje z kategorii o konkretnych kluczach. W `thumbcast_preferences.xml` te pozycje
są nadal w kategoriach o tych samych kluczach (patrz niżej), z dwoma wyjątkami, które obsługuje blok:

- `category_onscreen_controls` nie jest już dzieckiem ekranu głównego, więc `screen.removePreference(category)` na
  urządzeniu bez ekranu dotykowego nic nie robi. Blok usuwa wtedy stare przyciski ekranowe na stałe.
- `checkbox_vibrate_osc` jest w „Sterowanie” (`category_thumbcast`), więc upstreamowe usunięcie z kategorii OSC przy
  braku wibracji nic nie robi. Blok usuwa go z `category_thumbcast`.

Stare przyciski ekranowe (pozycje `category_onscreen_controls` za `list_control_layer` i `checkbox_thumbcast_debug`:
`checkbox_show_onscreen_controls`, `checkbox_only_show_L3R3`, `checkbox_show_guide_button`, `seekbar_osc_opacity`,
reset układu) są widoczne tylko przy rodzaju sterowania `legacy`. Listener `list_control_layer` usuwa je i dodaje z
powrotem w kategorii (bez przeładowania fragmentu, lista w oknie „Zaawansowane” odświeża się sama). Kolejność ma
znaczenie: dodawanie od `checkbox_show_onscreen_controls`, usuwanie od końca, a zależność `checkbox_vibrate_osc` jest
ustawiana po dodaniu i zdejmowana przed usunięciem (inaczej `Preference.registerDependency` rzuca
`IllegalStateException`). Przy braku starych przycisków (urządzenie bez dotyku) `checkbox_vibrate_osc` nie dostaje
zależności także w trybie legacy.

Pomoc: „Jak podłączyć PC” → `UiContract.setupGuideIntent(ctx, null)`, „Coś nie działa?” →
`setupGuideIntent(ctx, SECTION_TROUBLESHOOT)`, „Uruchom konfigurację od nowa” → `startActivityForResult(
UiContract.onboardingIntent(ctx))`. Brak aktywności (`ActivityNotFoundException`) = komunikat.

**Decyzja: kreator z Ustawień zapisuje wynik sam** (zamiast zerować `thumbcast_onboarding_version` i czekać na powrót
do listy PC). `SettingsFragment.onActivityResult` robi te same zapisy co hak P2 w `PcView`: zawsze
`thumbcast_onboarding_version` = `CURRENT_ONBOARDING_VERSION`; przy `RESULT_OK` `list_thumbcast_profile` = plik presetu
i `ThumbcastSettings.setHideOnGamepad(...)` (+ odświeżenie pola na ekranie) i komunikat „Zapisano. Profil sterowania:
<nazwa>”. Powód: zmiana jest widoczna od razu, nie zależy od tego, czy lista PC jest pusta (kreator przy niepustej
liście w ogóle się nie pokazuje), i nie trzeba wracać do listy PC. Przy zmianie kontraktu wyniku (`UiContract`) poprawić
oba miejsca.

„Zaawansowane” to zagnieżdżony `PreferenceScreen` (platforma pokazuje go w pełnoekranowym `Dialog` z motywem
aktywności). Listener kliknięcia dokłada nagłówek (strzałka zamyka okno, tytuł ekranu) i odstępy od pasków systemowych
(od Androida 15 okna rysują się pod nimi). `reloadSettings()` (zmiana „Pokaż wszystkie wartości FPS”, zmiana ekranu na
składanym telefonie) podmienia fragment i zamyka okno; `onDestroy` zapamiętuje, że było otwarte, a nowy fragment
otwiera je ponownie (`PreferenceScreen.onItemClick` z pozycją z `getRootAdapter()`). Nagłówka i odstępów nie ma, gdy
system sam odtworzy okno po odtworzeniu aktywności (rzadkie; aktywność obsługuje zmiany konfiguracji sama).

## Nowe pliki w `app/`

- `res/xml/thumbcast_preferences.xml` (D3). Klucze, typy, wartości domyślne, `entries` i zależności jak w
  `preferences.xml`; zmienione tylko tytuły, podpisy, ikony i miejsce. Drzewo:

  | Sekcja (klucz kategorii) | Pozycje |
  |---|---|
  | Sterowanie (`category_thumbcast`) | Profil sterowania (`list_thumbcast_profile`, biblioteka), Chowaj przyciski, gdy gram padem, Rozmiar przycisków, Wibracje telefonu (`checkbox_vibrate_osc`) |
  | Obraz (`category_basic_settings`) | Rozdzielczość, Płynność (FPS), Jakość obrazu (bitrate), Rozciągnij obraz, Dopasuj grę na PC do telefonu (`checkbox_enable_sops`, podpis: bez tego PC nie zmieni rozdzielczości), Statystyki (`checkbox_enable_perf_overlay`), Szczegółowe statystyki (`checkbox_thumbcast_perf_advanced`, zależy od Statystyk) |
  | Dźwięk (`category_thumbcast_sound`) | Kanały dźwięku, Graj dźwięk także na PC, Korektor dźwięku |
  | Aplikacja (`category_ui_settings`) | Język, Małe kafelki gier, Obraz w obrazie (`checkbox_enable_pip`) |
  | Pomoc (`category_thumbcast_help`) | Jak podłączyć PC, Coś nie działa?, Uruchom konfigurację od nowa |
  | Więcej (`category_thumbcast_more`) | Zaawansowane (`screen_thumbcast_advanced`, zagnieżdżony ekran) |

  „Zaawansowane”:

  | Sekcja (klucz kategorii) | Pozycje |
  |---|---|
  | Sterowanie (`category_onscreen_controls`) | Rodzaj sterowania (`list_control_layer`), Diagnostyka dotyku (`checkbox_thumbcast_debug`), stare przyciski ekranowe (tylko `legacy`) |
  | Obraz i dekodowanie (`category_advanced_settings`) | `frame_pacing`, `video_format`, `checkbox_enable_hdr`, `checkbox_full_range`, `checkbox_unlock_fps`, `checkbox_reduce_refresh_rate`, `checkbox_disable_warnings`, `checkbox_enable_post_stream_toast` |
  | Pad podłączony do telefonu (`category_gamepad_settings`) | martwa strefa, wiele padów, sterownik USB (+ wszystkie pady), emulacja myszy padem (+ przewijanie gałką), wibracje telefonu zamiast pada (+ siła), zamiana A/B/X/Y, touchpad pada jako mysz, czujniki ruchu (+ emulacja) |
  | Mysz i ekran dotykowy (`category_input_settings`) | `checkbox_touchscreen_trackpad`, `checkbox_mouse_nav_buttons`, `checkbox_absolute_mouse_mode` |

  Ikony (`android:icon`, wspólne `thumbcast_ic_*` z P0): profil, pad, rozmiar, statystyki, pomoc, zaawansowane.
  Od 12.11.2026 (prośba właściciela) ikonę ma każda pozycja ekranu głównego: wibracje, rozdzielczość, FPS, jakość,
  rozciąganie, optymalizacja gry, szczegóły statystyk, kanały dźwięku, dźwięk na PC, efekty dźwięku, język, małe
  ikony, obraz w obrazie (14 nowych `thumbcast_ic_*`, „Konfiguracja od nowa” ma `restart_alt`). Ekran Zaawansowane
  zostaje bez ikon.
- `res/layout/thumbcast_settings_header.xml`: nagłówek listy ustawień i okna „Zaawansowane”.
- `res/values/thumbcast_strings_settings.xml`, `res/values-pl/thumbcast_strings_settings.xml` (D5, słownik D7).
  Część tytułów i podpisów z upstreamu zostaje (np. HDR, opcje pada, myszy, podpisy techniczne w „Zaawansowane”).
- Biblioteka profili (U14, opis w `docs/profiles.md`): nowe `ProfileLibraryActivity` (wiersz z ikoną rodzaju, plakietką
  „Domyślny”, akcjami „Ustaw jako domyślny” i „Edytuj układ”, reszta pod ⋮ w `PopupMenu`, „Usuń” na końcu w kolorze
  błędu, jedna akcja „Dodaj profil”), layouty `activity_thumbcast_profile_library.xml`, `thumbcast_profile_item.xml`,
  `thumbcast_profile_header.xml`, nowy `thumbcast_profile_note.xml`, teksty `thumbcast_strings_library.xml` (EN, PL).
  `:control-android`: `library/ProfileKind.kt` i pole `ProfileEntry.kind` (testy w `ProfileRepositoryTest`).

## Teksty upstreamu z marką (U6, D4)

Flavor `nonRoot` ma pierwszeństwo przed `main`; pliki upstreamu zostają bez zmian. Jeden klucz nie może być w dwóch
plikach tego samego katalogu flavoru (błąd „Duplicate resources”).

- `app/src/nonRoot/res/values/thumbcast_overrides.xml` i `values-pl/thumbcast_overrides.xml` (nowe): teksty z
  „GFE”, „GeForce Experience”, „SHIELD”: `searching_pc`, `error_404`, `summary_checkbox_enable_sops`; w `values-pl`
  dodatkowo `local_network_rationale` (upstream nie ma polskiego tłumaczenia).
- `strings_branding.xml` (z 11.11., `docs/branding.md`) już nadpisywał teksty z „Moonlight” w znaczeniu „ta
  aplikacja”. Zmienione tylko `nettest_text_success` w `values` i `values-pl`: zamiast „Moonlight Internet Hosting Tool”
  (narzędzie dla GeForce Experience i przekierowania portów, `docs/branding.md` c3) rada o Tailscale. Do potwierdzenia
  przez właściciela; inne języki nadal mają nazwę narzędzia (generator `tools/branding/gen_locale_overrides.py`).
- Zostają celowo (kategoria b w `docs/branding.md`): `nettest_text_inconclusive` („serwery projektu Moonlight”, test
  naprawdę z nich korzysta, c4), `summary_privacy_policy` (nieużywany), `pcview_menu_eol` (nazwa usługi NVIDIA; pozycję
  menu ukrywa P2, U11).
- Inne języki (`bg`, `cs`, `de`, `el`, `es`, `fr`, `hu`, `in`, `it`, `iw`, `ja`, `ko`, `nb-rNO`, `nl`, `pt`, `pt-rBR`,
  `ro`, `ru`, `sv`, `ta`, `tr`, `uk`, `vi`, `zh-rCN`, `zh-rTW`) nadal mają „GFE”/„GeForce Experience SHIELD” w
  `searching_pc`, `error_404`, `summary_checkbox_enable_sops` i atrybucję „Moonlight” (D4: decyzja właściciela później).

Sprawdzenie, że nadpisania działają: `aapt2 dump resources app-nonRoot-debug.apk` pokazuje dla `searching_pc`,
`error_404`, `summary_checkbox_enable_sops` w konfiguracjach `()` i `(pl)` teksty z `thumbcast_overrides.xml`,
dla `local_network_rationale` wpis `(pl)`, a dla `nettest_text_success` nowe zdanie o Tailscale (12.11.2026).

## Klucze tekstów do sprzątnięcia (D5, orkiestrator)

Po P3 nieużywane w kodzie (`values*/thumbcast_strings.xml`): `category_thumbcast`, `title_control_layer`,
`title_thumbcast_profile`, `title_thumbcast_debug`, `title_thumbcast_control_scale`, `title_thumbcast_hide_on_gamepad`,
`summary_thumbcast_hide_on_gamepad`, `title_thumbcast_perf_advanced`, `summary_thumbcast_perf_advanced`,
`thumbcast_library_import_file`, `thumbcast_library_import_clipboard`, `thumbcast_library_section_builtin`,
`thumbcast_library_section_user`, `thumbcast_library_empty_user`, `thumbcast_library_active_description`,
`thumbcast_library_action_select`, `thumbcast_library_action_edit`, `thumbcast_library_default_marker`,
`thumbcast_library_default_hint`, `thumbcast_library_action_mouse_gestures_on/off`, plural `thumbcast_library_controls`
(sprawdzić, czy nie używa ich inny pakiet; tytuły preferencji używa jeszcze upstreamowy `preferences.xml`).

## Sprawdzenie

`./gradlew :app:assembleNonRootDebug :app:assembleNonRootRelease :app:lintNonRootDebug :control-android:testDebugUnitTest`.
Na urządzeniu jeszcze nie sprawdzone (lista w raporcie P3 i niżej):

- ekran główny ustawień: nagłówek, ikony i wyrównanie tytułów, podsumowania `%s`;
- „Zaawansowane”: okno, nagłówek, strzałka, odstępy od pasków (Android 15+ i starszy), ponowne otwarcie po zmianie
  „Pokaż wszystkie wartości FPS”;
- rodzaj sterowania `thumbcast` ↔ `legacy`: pojawianie się i znikanie starych przycisków, „Wibracje telefonu”
  (wyszarzone w legacy, gdy klasyczne przyciski wyłączone; aktywne w thumbcast);
- Pomoc: poradnik, sekcja „Coś nie działa?”, kreator i zapis wyniku;
- biblioteka: wiersze, ⋮, „Dodaj profil”, usuwanie, pad/klawiatura (fokus na przyciskach w wierszach).
