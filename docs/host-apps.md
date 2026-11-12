# Aplikacje na hoście i ustawienia aplikacji w Thumbcast

Stan: 12.11.2026. Nazwy opcji i zachowanie Sunshine sprawdzone w kodzie źródłowym: gałąź `master` (commit `da93e9de`, 12.11.2026) i wydanie stabilne `v2026.914.233613` (te same klucze `dd_*` i te same wartości domyślne). Oznaczenie **[?]** to rzecz niepotwierdzona, do sprawdzenia testem. Ogólna konfiguracja hosta (instalacja, pad, sieć): [host-setup.md](host-setup.md).

## 1. Jak to działa razem

1. W Sunshine każda pozycja z zakładki **Applications** to „aplikacja” na liście w Thumbcast (pulpit, Steam Big Picture, gra z Battle.net).
2. W Thumbcast przytrzymaj aplikację → **Ustawienia Thumbcast dla aplikacji**: profil sterowania, rozdzielczość i FPS tylko dla tej aplikacji (sekcja 4).
3. Przy starcie Thumbcast wysyła do hosta żądanie `/launch` z `mode=<szer.>x<wys.>x<FPS>` i `sops=1` („Optymalizacja ustawień gry”). Sunshine zapisuje te liczby w sesji (`src/nvhttp.cpp`, `make_launch_session`).
4. **Sama rozdzielczość strumienia zmienia tylko wideo.** Bez konfiguracji hosta PC dalej renderuje w swojej rozdzielczości, a enkoder skaluje obraz w dół. To odciąża sieć i dekoder telefonu, ale nie kartę graficzną PC.
5. Żeby PC naprawdę przełączył ekran, Sunshine musi mieć włączoną konfigurację ekranu (sekcja 3). Wtedy przed startem aplikacji ustawia rozdzielczość i odświeżanie z żądania klienta, a po zakończeniu przywraca poprzednie.

Opcje `dd_*` w Sunshine są globalne (jedne dla wszystkich aplikacji). Różne rozdzielczości dla różnych aplikacji biorą się stąd, że telefon wysyła inną rozdzielczość dla każdej aplikacji.

## 2. Dodawanie aplikacji w Sunshine

Web UI Sunshine (`https://localhost:47990`) → **Applications** → **Add New**. Pola wg `docs/app_examples.md` Sunshine.

| Aplikacja | Application Name | Detached Commands | Command Preparations | Image |
|---|---|---|---|---|
| Pulpit | `Desktop` | (puste) | (puste) | `desktop.png` |
| Steam Big Picture | `Steam Big Picture` | `steam://open/bigpicture` | Undo: `steam://close/bigpicture` | `steam.png` |
| Gra ze Steam | nazwa gry | `steam://rungameid/<id gry>` | (puste) | dowolny |
| WoW: Forever przez Battle.net | np. `WoW Forever` | `"C:\Program Files (x86)\Battle.net\Battle.net.exe" --exec="launch WoWF"` | (puste) | dowolny |
| WoW (retail) przez Battle.net | np. `World of Warcraft` | `"C:\Program Files (x86)\Battle.net\Battle.net.exe" --exec="launch WoW"` | (puste) | dowolny |

Uwagi:
- **Detached Commands** (a nie Command), bo Steam i Battle.net uruchamiają grę w innym procesie i same zostają w tle. Sunshine traktuje wtedy aplikację jako uruchomioną, dopóki nie wybierzesz w Thumbcast **Zakończenie sesji** (przytrzymanie aplikacji albo PC). Rozłączenie (☰ → Rozłącz) zostawia grę uruchomioną; ponowne wejście to wznowienie.
- Kody produktów Battle.net (`WoW`, `WoWF`) podał właściciel; `WoWF` dla WoW: Forever **[?]**. Sprawdź: skrót gry utworzony przez Battle.net (Właściwości → Cel) albo uruchom polecenie ręcznie w `cmd`. Ścieżka do `Battle.net.exe` może być inna. Battle.net musi być zalogowany; przy pierwszym uruchomieniu może pokazać okno launchera przed grą.
- Inne launchery: Epic (w Command URI `com.epicgames.launcher://apps/<id>?action=launch&silent=true` albo ścieżka do `.exe` gry w Command z Working Directory), gra bez launchera: ścieżka do `.exe` w **Command**. Zasada jest ta sama: launcher w Detached Commands, zwykły program w Command.
- **Nie zmieniaj nazwy ani obrazka aplikacji bez potrzeby.** Sunshine liczy id aplikacji jako CRC32 z nazwy i skrótu SHA-256 obrazka (`src/process.cpp`, `calculate_app_id`; przy kolizji dokłada indeks). Zmiana obrazka zmienia id: Thumbcast znajdzie ustawienia po nazwie. Zmiana nazwy zmienia i id, i nazwę: ustawienia trzeba przypisać ponownie.

## 3. Zmiana rozdzielczości ekranu PC (Windows)

### 3a. Wbudowana konfiguracja ekranu (zalecane)

Web UI → **Configuration** → **Audio/Video** → **Advanced display device options**. Klucze w `sunshine.conf` i wartości sprawdzone w `src/config.cpp` (`dd::*_from_view`) i `src/display_device.cpp`:

| Klucz | Etykieta w web UI | Ustaw | Domyślnie | Działanie |
|---|---|---|---|---|
| `dd_configuration_option` | Device configuration | `verify_only` (jeden monitor) albo `ensure_only_display` (kilka monitorów, streamuj tylko jeden) | `disabled` | `disabled` wyłącza **wszystkie** opcje `dd_*`. Inne wartości: `ensure_active`, `ensure_primary`. |
| `dd_resolution_option` | Resolution | `auto` | `auto` | rozdzielczość z żądania klienta (`mode`). `manual` = `dd_manual_resolution`, `disabled` = bez zmian. |
| `dd_refresh_rate_option` | Refresh rate | `auto` | `auto` | odświeżanie = FPS z żądania klienta. `manual` = `dd_manual_refresh_rate`. |
| `dd_hdr_option` | HDR | `auto` | `auto` | HDR wg klienta. |
| `dd_config_revert_delay` | Config revert delay | 3000 | 3000 ms | opóźnienie przywrócenia po zamknięciu aplikacji lub końcu ostatniej sesji. |
| `dd_config_revert_on_disconnect` | Config revert on disconnect | wg uznania | `disabled` | `enabled`: przywróć ekran już po rozłączeniu wszystkich klientów (gra dalej działa). |
| `dd_mode_remapping` | Display mode remapping | opcjonalnie | puste listy | zamiana żądanego trybu na inny, np. `1280x720` z telefonu → `1920x1080` na PC. |

Warunki (z kodu `src/display_device.cpp`, `parse_resolution_option`):
- **„Optymalizacja ustawień gry” w Thumbcast musi być włączona** (Ustawienia → `checkbox_enable_sops`, domyślnie włączone). Bez niej Sunshine loguje ostrzeżenie i nie zmienia rozdzielczości (odświeżanie zmienia).
- Tylko Windows. Tryb musi być obsługiwany przez monitor i kartę (np. 1280×720 przy 60 Hz). Jeśli nie jest, konfiguracja się nie uda **[?]** (zachowanie libdisplaydevice przy nieobsługiwanym trybie niesprawdzone; podgląd w logach Sunshine).
- Kiedy Sunshine zmienia ekran: w `/launch` i w `/resume`, jeśli nie ma innej aktywnej sesji (`src/nvhttp.cpp`). Kiedy przywraca: po zakończeniu aplikacji (Zakończenie sesji w Thumbcast albo wyjście z programu z Command) i przy błędzie startu, z opóźnieniem `dd_config_revert_delay`; przy `dd_config_revert_on_disconnect = enabled` także po rozłączeniu ostatniego klienta.
- W grze ustaw tryb **okno pełnoekranowe (bez ramki)**, żeby gra brała rozdzielczość pulpitu. Tryb wyłączny pełnoekranowy używa rozdzielczości z ustawień gry.

Przykład `sunshine.conf` (to samo co w web UI):

```
dd_configuration_option = verify_only
dd_resolution_option = auto
dd_refresh_rate_option = auto
```

### 3b. Alternatywa: polecenia przygotowania z rozdzielczością klienta

Sunshine ustawia dla poleceń aplikacji (prep-cmd, Command, Detached) zmienne środowiskowe (`src/process.cpp`, `proc_t::execute`): `SUNSHINE_APP_ID`, `SUNSHINE_APP_NAME`, `SUNSHINE_CLIENT_NAME`, `SUNSHINE_CLIENT_WIDTH`, `SUNSHINE_CLIENT_HEIGHT`, `SUNSHINE_CLIENT_FPS`, `SUNSHINE_CLIENT_HDR`, `SUNSHINE_CLIENT_GCMAP`, `SUNSHINE_CLIENT_HOST_AUDIO`, `SUNSHINE_CLIENT_ENABLE_SOPS`, `SUNSHINE_CLIENT_AUDIO_CONFIGURATION`, `SUNSHINE_CLIENT_AUDIO_SURROUND_PARAMS`.

Przykład z dokumentacji Sunshine (narzędzie QRes; Sunshine nie dostarcza go, trzeba je pobrać samemu), w aplikacji → Command Preparations:
- Do: `cmd /C "C:\Narzędzia\qres.exe /x:%SUNSHINE_CLIENT_WIDTH% /y:%SUNSHINE_CLIENT_HEIGHT% /r:%SUNSHINE_CLIENT_FPS%"`
- Undo: `C:\Narzędzia\qres.exe /x:2560 /y:1440 /r:144` (Twoja zwykła rozdzielczość).

Dla wszystkich aplikacji naraz służy `global_prep_cmd`. Nie łącz 3a i 3b: dwa mechanizmy będą przestawiać ten sam ekran.

## 4. Ustawienia aplikacji w Thumbcast (telefon)

Lista aplikacji PC → przytrzymaj aplikację → **Ustawienia Thumbcast dla aplikacji**:
- **Profil sterowania**: dowolny profil z biblioteki (presety i własne) albo „Profil domyślny” (globalny z ustawień).
- **Rozdzielczość strumienia** i **Liczba klatek**: te same opcje co w ustawieniach (360p–4K, 30–120 FPS) albo „Ustawienie globalne”.
- **Globalne** czyści wszystkie trzy pola dla tej aplikacji.

Działanie przy starcie strumienia (`Game.onCreate` → `ThumbcastAppSettings.applyToStream`):
- Nadpisuje tylko konfigurację tego strumienia. Ustawienia globalne się nie zmieniają. Bitrate zostaje globalny (przy niższej rozdzielczości to więcej bitów na piksel).
- Ustawienia działają dla każdej drogi startu: lista aplikacji, skrót do gry na ekranie głównym, wznowienie z ekranu PC.
- Brak albo uszkodzony plik przypisanego profilu (np. profil usunięty z biblioteki): strumień startuje z profilem globalnym i komunikatem. Dialog pokazuje wtedy ostrzeżenie, a zapis ustawia „Profil domyślny”.
- **Zmiana profilu w grze** (menu ☰ → Profil) w sesji z profilem aplikacji działa tylko do końca tej sesji: nie zmienia ani przypisania aplikacji, ani profilu globalnego. Bez profilu aplikacji działa jak dotąd (zapisuje profil globalny). Trwała zmiana: dialog aplikacji.

Zapis: plik SharedPreferences `ThumbcastAppSettings` (jak upstreamowe `HiddenApps`), klucz = uuid PC małymi literami, wartość = JSON:

```json
{"version":1,"apps":[{"appId":123456789,"appName":"WoW Forever","profile":"mmo-pad.thumbcast.json","width":1280,"height":720,"fps":60}]}
```

Dopasowanie aplikacji (`HostAppSettings.find`): (1) to samo id i nazwa, (2) ta sama nazwa i inne id (zmieniony obrazek), (3) to samo id i inna lub nieznana nazwa (wznowienie z ekranu PC podaje nazwę zastępczą). Szczegóły i testy: `control-android/.../apps/`, `AppStreamSettingsTest`. Ustawienia aplikacji nie są eksportowane razem z profilami; odinstalowanie aplikacji je usuwa (jak inne ustawienia).

## 5. Test (telefon + PC)

1. Sunshine: dodaj „Steam Big Picture” i grę z Battle.net (sekcja 2). Ustaw `dd_configuration_option = verify_only` (sekcja 3a), zapisz, zrestartuj Sunshine.
2. Thumbcast: Ustawienia → globalnie np. 1920×1080, 60 FPS, „Optymalizacja ustawień gry” włączona.
3. Przytrzymaj grę → Ustawienia Thumbcast dla aplikacji → profil np. „MMO – Pad+”, 720p, 60 FPS → Zapisz.
4. Uruchom grę. Oczekiwane: overlay z profilem MMO; na PC ekran przełącza się na 1280×720 (Ustawienia ekranu Windows albo log Sunshine); w grze okno pełnoekranowe ma 720p.
5. ☰ → Profil → inny profil: działa od razu, komunikat „Profil w tej sesji…”. Wyjdź i uruchom grę ponownie: znowu profil MMO. Globalny profil w ustawieniach bez zmian.
6. Rozłącz i wznów z ekranu PC (przytrzymaj PC → Wznowienie sesji): znowu 720p i profil MMO.
7. Zakończ grę w Thumbcast (przytrzymaj grę → Zakończenie sesji). Po ok. 3 s PC wraca do swojej rozdzielczości.
8. Uruchom „Steam Big Picture” (bez ustawień aplikacji): profil i rozdzielczość globalne, PC przełącza się na 1920×1080.
9. Usuń w bibliotece profil przypisany do gry, uruchom grę: komunikat o braku profilu, działa profil globalny. Dialog aplikacji pokazuje ostrzeżenie.
10. Wyłącz „Optymalizację ustawień gry” i uruchom grę: strumień 720p, ale PC nie zmienia rozdzielczości (w logu Sunshine ostrzeżenie o „Optimize game settings”).

## Źródła

- Konfiguracja Sunshine (`dd_*`, `global_prep_cmd`): https://github.com/LizardByte/Sunshine/blob/master/docs/configuration.md (repo przeniesione do `SunshineStream/Sunshine`, stary adres przekierowuje); wersja HTML: https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html
- Parsowanie opcji: https://github.com/LizardByte/Sunshine/blob/master/src/config.cpp (`dd::config_option_from_view`, `resolution_option_from_view`, `refresh_rate_option_from_view`, wartości domyślne `video_t::dd`)
- Zmiana i przywracanie ekranu: https://github.com/LizardByte/Sunshine/blob/master/src/display_device.cpp (`parse_resolution_option`, `parse_refresh_rate_option`, `revert_configuration`) i https://github.com/LizardByte/Sunshine/blob/master/src/nvhttp.cpp (`launch`, `resume`, `cancel`, `make_launch_session`)
- Zmienne `SUNSHINE_*` i id aplikacji: https://github.com/LizardByte/Sunshine/blob/master/src/process.cpp (`proc_t::execute`, `calculate_app_id`)
- Przykłady aplikacji (Desktop, Steam Big Picture, QRes): https://github.com/LizardByte/Sunshine/blob/master/docs/app_examples.md
- Etykiety web UI: https://github.com/LizardByte/Sunshine/blob/master/src_assets/common/assets/web/public/assets/locale/en.json
