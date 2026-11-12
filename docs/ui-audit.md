# Audyt UI i plan przebudowy (12.11.2026)

Źródło: dwa audyty kodu (tylko odczyt, bez urządzenia): poza strumieniem (U1–U20) i w strumieniu (G1–G17).
Ten plik jest wspólną specyfikacją dla agentów wykonujących pakiety P0–P4. Decyzje w sekcji 2 są wiążące.

## 1. Ustalenia

### Poza strumieniem
| ID | Waga | Gdzie | Problem | Poprawka |
|---|---|---|---|---|
| U1 | krytyczne | `PcView.java:127-184` | Brak pierwszego uruchomienia; nowy użytkownik widzi wieczny spinner. | Kreator (sekcja 3), hak w `PcView.onCreate`. |
| U2 | krytyczne | `utils/HelpLauncher.java:40-49`, `PcView.java:163`, `utils/Dialog.java:92` | Pomoc otwiera angielską wiki upstreamu o GeForce Experience. | Własny poradnik offline; `launchSetupGuide`/`launchTroubleshooting` przekierowane w bloku THUMBCAST. |
| U3 | krytyczne | `activity_pc_view.xml:21-46`, `values-pl/strings.xml:11-13` | Pusty stan bez instrukcji („GeForce Experience SHIELD”). | Po ok. 5 s karta „Nie widzę komputera” z „Jak podłączyć PC” i „Dodaj adres ręcznie”. |
| U4 | krytyczne | `res/xml/preferences.xml` | Ok. 50 opcji na jednej liście, 9 kategorii, bez ikon, sekcja Thumbcast na 5. miejscu. | Nowy `thumbcast_preferences.xml` (D3): Sterowanie, Obraz, Dźwięk, Aplikacja, Pomoc, a reszta w „Zaawansowane”. |
| U5 | ważne | `values-pl/strings.xml:113,181,185`, `thumbcast_strings.xml:5-6` | Żargon: „Szybkość transmisji wideo”, „Tempo klatek”, „Warstwa sterowania”. | Słownik D7. |
| U6 | ważne | `values*/strings.xml` (11 miejsc) | „Moonlight” i „GFE” w tekstach widocznych dla użytkownika. | Nadpisania we flavorze `nonRoot` (D4). |
| U7 | ważne | `values/styles.xml`, `values-v29/styles.xml` | Brak palety i marki. | D1, `Theme.Thumbcast`. |
| U8 | ważne | `activity_pc_view.xml:49-88` (+land) | Ikony 70×65 dp `fitXY` bez etykiet i `contentDescription`. | Pasek z tytułem „Twoje komputery”, ikony 24 dp w polu 48 dp z opisami. |
| U9 | ważne | `grid/PcGridAdapter.java:53-91` | Stan PC tylko przez alpha 0,4 i małą kłódkę. | Karta z plakietką tekstową (Dostępny / Niedostępny / Wymaga parowania) i kolorem jako drugim sygnałem. |
| U10 | ważne | `PcView.java:434-436` | Dialog PIN nie mówi, gdzie wpisać kod. | Kroki: „Na PC otwórz https://localhost:47990 → PIN → wpisz kod”, duży PIN, link do poradnika. |
| U11 | ważne | `PcView.java:363-389` | Menu PC: żargon (WOL, „Zakończenie usług NVIDIA GameStream”), „Usuń PC” w środku listy. | Ukryć pozycję EOL GameStream, „Szczegóły” przenieść do diagnostyki, „Usuń” na koniec. |
| U12 | ważne | `AndroidManifest.xml:132,184` | Etykiety po angielsku na sztywno. | Zasoby tekstowe. |
| U13 | ważne | `activity_add_computer_manually.xml` | Brak podpowiedzi, skąd wziąć IP; przycisk „OK”. | Podpowiedź (Sunshine / Tailscale) i przycisk „Dodaj”. |
| U14 | ważne | `ProfileLibraryActivity.java:122-236`, `thumbcast_profile_item.xml` | Każdy tap otwiera listę 7 akcji; dwa importy obok siebie. | „Ustaw jako domyślny” i „Edytuj” widoczne w wierszu, reszta pod ⋮, ikona typu profilu, jedna akcja „Dodaj profil”. |
| U15 | ważne | `AppView.java:411-440` | Ustawienia gry ukryte pod długim przytrzymaniem. | Widoczna ikona ⋮ na kafelku. |
| U16 | drobne | `HostAddressesDialog.java:41-50` | Tap w adres od razu pyta o usunięcie. | Ikona kosza, nagłówki grup. |
| U17 | drobne | `ThumbcastAppSettingsDialog.java:194-203` | Notka 12 sp z alpha 0,75. | 14 sp, kolor drugorzędny z palety. |
| U18 | drobne | `PcView.java:403-411` | Kluczowe komunikaty jako Toast („Parowanie…”). | Dialog postępu. |
| U19 | drobne | `values/thumbcast_strings.xml` | 4 klucze bez PL, prawdopodobnie martwe. | Sprząta orkiestrator. |
| U20 | drobne | `control-editor/.../EditorStore.kt:27` | Domyślny `darkColorScheme()`. | `ThumbcastTheme` z `:control-ui`. |

### W strumieniu
Obecne menu to przyciemnienie 60% i panel przy prawej krawędzi `#202124`. Zawiera 11 jednakowych wierszy tekstu bez ikon i podziałów. Na górze są „Rozłącz” i „Zakończ sesję…”, a „Wróć do gry” jest na końcu.

| ID | Waga | Gdzie | Problem | Poprawka |
|---|---|---|---|---|
| G1 | krytyczne | `ThumbcastMenuModel.java:139-175`, `ThumbcastMenuView.java:259-266` | Brak ikon, sekcji i separatorów. | `Item` z ikoną, sekcją i wartością; ikony 24 dp, nagłówki, linie 1 dp. |
| G2 | krytyczne | `ThumbcastMenuModel.java:141-142,173` | Akcje wyjścia na górze, „Wróć do gry” na dole. | „Wróć do gry” na górze (wypełniony przycisk, domyślny fokus), wyjścia w sekcji na dole. |
| G3 | ważne | `ThumbcastMenuModel.java:150-172` | Przełączniki jako tekst „…: wł./wył.”. | `Kind.SWITCH` z rysowanym przełącznikiem; stan także w `contentDescription`. |
| G4 | ważne | `values-pl/thumbcast_strings.xml:74,147` | „Rozłącz” i „Zakończ sesję” różnią się tylko nawiasem. | „Wstrzymaj i wyjdź” (podpis „Gra działa dalej na PC”) oraz „Zamknij grę na PC” (podpis „Niezapisane postępy przepadną”, kolor błędu). |
| G5 | ważne | `ThumbcastSession.java:799` | Ostrzeżenie w szarym tytule 14 sp, technicznym językiem. | Tytuł „Zamknąć grę na PC?”, akapit 16 sp w pełnym kontraście. |
| G6 | ważne | `ThumbcastSession` | Nowy użytkownik nie wie, jak otworzyć menu. | Jednorazowa pigułka przy starcie strumienia (4 s lub do pierwszego otwarcia). Flaga w `ThumbcastSettings`. |
| G7 | ważne | `ThumbcastSession.java:500` | Toast ok. 230 znaków po przełączeniu gestów myszy. | Podpis pod wierszem w menu, bez toastu. |
| G8 | ważne | strings `:79-80` | „Ukryj przyciski przy podłączonym padzie” zawija się i myli z „Ukryj przyciski”. | „Chowaj przyciski, gdy gram padem”. |
| G9 | ważne | `ThumbcastMenuModel.java:166-172` | Dwa zależne przełączniki statystyk. | Jeden wiersz „Statystyki: Wył. / Podstawowe / Szczegółowe” (zapis bez zmian: dwie flagi). |
| G10 | ważne | `buildProfiles` | Glify `✓★‹`, brak podziału na wbudowane i własne. | Ikony wektorowe, sekcje „Wbudowane” i „Moje”, strzałka wstecz w nagłówku. |
| G11 | ważne | `ThumbcastMenuView.java:59-67` | Kolory zaszyte w kodzie. | `thumbcast_colors.xml` (D1). |
| G12 | drobne | `ThumbcastMenuView.java:113` | Panel bez zaokrągleń i animacji. | Rogi 28 dp od strony wideo, wjazd 150 ms. |
| G13 | drobne | `MenuInputRouter.kt:220-224` | Brak zawijania fokusu i podpowiedzi klawiszy pada. | Zawijanie w `MenuFocus.move` i stopka „A Wybierz · B Wstecz”. |
| G14 | drobne | `ThumbcastStatsView.java:31` | 11 sp, długie „opóźnienie”. | 12 sp, „60 FPS · 23 ms”. |
| G15 | drobne | `ControlLayout.kt:104-108`, `OverlayRenderer.kt:76-102` | Przyciski systemowe ☰ ⌨ to glify czcionki, mniejsze niż 48 dp. | Osobny wygląd (pigułka, ikona jako `Path`), większy obszar dotyku. |
| G16 | drobne | strings `:153-155` | Długie błędy z surowym `e.getMessage()`. | Jedno zdanie i wskazówka; szczegół trafia do logu. |
| G17 | drobne | `ThumbcastMenuView.java:276-279` | Stepper „Rozmiar kontrolek” z gołymi − i +. | Ikony w kółkach, wartość w pigułce, słowo „przyciski”. |

## 2. Decyzje (wiążące)

**D1 Paleta (tylko ciemna)** w `app/src/main/res/values/thumbcast_colors.xml`, w Compose w `ThumbcastTheme.kt`.

Ekrany:

| Rola | Kolor |
|---|---|
| tło | `#0F1115` |
| powierzchnia | `#181B22` |
| powierzchnia podniesiona | `#222632` |
| obrys | `#2E3340` |
| tekst | `#E8EAF0` |
| tekst drugorzędny | `#A3A9B8` |
| akcent | `#5B8CFF` |
| tekst na akcencie | `#FFFFFF` |
| sukces / online | `#3DDC97` |
| ostrzeżenie | `#FFB84D` |
| błąd | `#FF6B6B` |
| wypełnienie przycisku błędu | `#93000A` |

Nad wideo:

| Rola | Kolor |
|---|---|
| scrim | `#99000000` |
| powierzchnia menu | `#F2181B22` |
| linie | `#33FFFFFF` |

Kolor nigdy nie jest jedynym sygnałem, zawsze idzie z ikoną lub tekstem. Bez dynamic color.

**D2 Technologia.**
- W `:app` zostają widoki platformy (`android.app.Activity`, `PreferenceFragment`) i VectorDrawable. Nie dodajemy AppCompat ani Material Components.
- Nowe ekrany (kreator, poradnik) są w Compose + Material 3 w nowym module `:control-ui`, który ma też `ThumbcastTheme`.
- `:control-editor` używa `ThumbcastTheme` z `:control-ui`.

**D3 Ustawienia.** `StreamSettings` w bloku THUMBCAST ładuje `res/xml/thumbcast_preferences.xml` zamiast `preferences.xml`. Klucze są te same, więc kod `findPreference` działa bez zmian, a upstreamowy `preferences.xml` zostaje nietknięty.
- Układ: Sterowanie, Obraz, Dźwięk, Aplikacja, Pomoc, a dalej zagnieżdżony ekran „Zaawansowane”.
- Do „Zaawansowane” trafiają: `frame_pacing`, `video_format`, `checkbox_full_range`, `checkbox_enable_hdr`, `checkbox_unlock_fps`, `checkbox_reduce_refresh_rate`, `checkbox_disable_warnings`, sterownik USB, emulacja myszy padem, czujniki ruchu, `checkbox_absolute_mouse_mode`, `checkbox_thumbcast_debug`, `list_control_layer`, a także stare OSC (tylko przy warstwie `legacy`).
- `checkbox_enable_sops` dostaje opis: od niej zależy zmiana rozdzielczości PC.

**D4 Teksty upstreamu z „Moonlight” i „GFE”.** Nadpisania z tymi samymi nazwami zasobów idą do `app/src/nonRoot/res/values/thumbcast_overrides.xml` i `values-pl/thumbcast_overrides.xml`; flavor ma pierwszeństwo przed `main`. Pozostałe języki zostają bez zmian (decyzja właściciela później).

**D5 Pliki tekstów.**
- Istniejącego `values*/thumbcast_strings.xml` nikt nie edytuje.
- Pakiet, który chce zmienić tekst, dodaje nowy klucz we własnym pliku (PL i EN) i przełącza na niego kod; stare klucze sprząta orkiestrator.
- Pliki: `thumbcast_strings_common.xml` (P0), `thumbcast_strings_launcher.xml` (P2), `thumbcast_strings_settings.xml` (P3), `thumbcast_strings_library.xml` (P3), `thumbcast_strings_game.xml` (P4). Kreator i poradnik mają teksty we własnym module `:control-ui`.

**D6 Ikony.**
- Material Symbols Rounded jako VectorDrawable 24 dp, kolor przez `android:tint` lub `?android:attr/textColorPrimary`, bez `#FFFFFF` na sztywno.
- Wspólny zestaw tworzy P0 w `app/src/main/res/drawable/thumbcast_ic_<nazwa>.xml`: computer, add, settings, help, lock, wifi_off, gamepad, sports_esports, keyboard, touch_app, mouse, tune, more_vert, delete, vpn_key, play_arrow, close, check, star, arrow_back, chevron_right, visibility, visibility_off, speed, logout, power_settings_new, warning, format_size, remove, edit, upload, download, share, info, open_in_new.
- Pakiet, który potrzebuje innej ikony, dodaje ją z własnym prefiksem: `thumbcast_ic_pc_*`, `thumbcast_ic_set_*`, `thumbcast_ic_game_*`.

**D7 Słownik.**

| Zamiast | Piszemy |
|---|---|
| kontrolki | przyciski |
| Szybkość transmisji wideo | Jakość obrazu |
| Tempo klatek | Płynność (FPS) |
| Warstwa sterowania | Rodzaj sterowania |
| Rozłącz (gra działa dalej) | Wstrzymaj i wyjdź |
| Zakończ sesję | Zamknij grę na PC |
| Ukryj przyciski przy podłączonym padzie | Chowaj przyciski, gdy gram padem |

Bez zmian zostają: „Statystyki” (Wył. / Podstawowe / Szczegółowe), „Gesty myszy”, „Profil sterowania”, „Komputer” (w tekstach „PC” jest dozwolone).

Pisz krótko, w drugiej osobie, bez żargonu. Termin techniczny może pojawić się w nawiasie albo w podpisie, nigdy w tytule.

**D8 Kontrakt `:control-ui`** (P0 tworzy go razem z zaślepkami aktywności): `app.thumbcast.ui.UiContract`.
- Nazwy klas: `OnboardingActivity`, `SetupGuideActivity`.
- Wynik kreatora: extra z nazwą pliku presetu i `hideOnGamepad: Boolean`. Zapis w preferencjach robi `:app` (P2) istniejącym mechanizmem profilu domyślnego (`list_thumbcast_profile`).
- Wejście poradnika: opcjonalna sekcja startowa: `install`, `gamepad`, `pair`, `apps`, `remote`, `troubleshoot`.
- Klucz `thumbcast_onboarding_version` (int) i stała bieżącej wersji.

**D9 Zakazy dla wszystkich pakietów.** Nie edytować:
- `docs/HANDOFF.md`, `CLAUDE.md`, `docs/PLAN.md`, `patches/0001-thumbcast.diff`;
- `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `AGENTS.md`;
- istniejących `values*/thumbcast_strings.xml`.

Pozostałe zasady:
- Zmiany w upstreamowych plikach `app/` tylko w blokach `// THUMBCAST` … `// THUMBCAST END` (w XML `<!-- THUMBCAST -->` … `<!-- THUMBCAST END -->`); nowe pliki w `com.limelight.thumbcast` albo z prefiksem `thumbcast_`.
- Nazwy bez „WoW”, „Blizzard” i „Moonlight”.
- FAIR_PLAY: UI niczego nie wysyła do hosta samo z siebie.
- Każdy pakiet opisuje swoje zmiany w `app/` we własnym `patches/ui-<pakiet>.patch.md`.

## 3. Kreator pierwszego uruchomienia (P1)

Kreator ma 4 kroki, każdy z przyciskiem „Pomiń” i paskiem postępu:
1. **Powitanie.** „Graj w gry z PC na telefonie”, 3 korzyści, przyciski [Zaczynamy] i [Pomiń].
2. **Czym grasz najczęściej?** Kafle z ikonami:

   | Kafel | Preset | `hideOnGamepad` |
   |---|---|---|
   | Dotyk – układ Xbox | `gamepad-xbox` | false |
   | Dotyk – układ PlayStation | `gamepad-playstation` | false |
   | Pad Xbox 360 / One / Series | `gamepad-xbox` | true |
   | Pad PS4 / PS5 | `gamepad-playstation` | true |
   | Mysz i klawiatura (MMO) | `mmo-keyboard` | false |

   Domyślnie zaznaczony jest Pad PS4 / PS5. Pod kaflami dopisek: „Zmienisz to w Ustawieniach”.
3. **Przygotuj PC.** Skrót poradnika w 3 punktach oraz [Pokaż pełny poradnik] i [Wyślij sobie link] (systemowy share sheet z adresem pobierania Sunshine).
4. **Połącz.** Instrukcja: „Telefon i PC w tym samym Wi-Fi. Wybierz komputer z listy i wpisz PIN w Sunshine”. Na końcu kreator wraca do listy PC.

Zasady uruchamiania:
- Kreator nie pokazuje się, gdy baza PC nie jest pusta (aktualizacja aplikacji); wtedy tylko zapisuje wersję.
- Ponowne uruchomienie: Ustawienia → Pomoc → „Uruchom konfigurację od nowa”.

## 4. Poradnik „Jak podłączyć PC” (P1)

Treść jest offline (PL i EN), w sekcjach rozwijanych z listą kontrolną. Wejścia do poradnika:
- ikona pomocy w `PcView`;
- karta pustego stanu;
- dialog PIN;
- Ustawienia → Pomoc;
- `HelpLauncher`.

Sekcje:
1. **Zainstaluj Sunshine na PC (Windows).**
   - Pobierz instalator `.msi` z https://github.com/SunshineStream/Sunshine/releases (v2026.914+, jak w `docs/host-setup.md`).
   - Zainstaluj, otwórz `https://localhost:47990` i ustaw login oraz hasło.
2. **Wirtualny pad.**
   - Wariant darmowy: ViGEmBus 1.17+ (https://github.com/nefarius/ViGEmBus/releases), restart PC, potem w Sunshine Configuration → Input ustaw typ pada.
   - Wariant płatny: LizardByte Virtual HID Driver.
   - Bez sterownika zostaje profil Mysz i klawiatura.
3. **Sparuj.** To samo Wi-Fi → wybierz PC → wpisz PIN w Sunshine (zakładka PIN).
4. **Dodaj gry.** W Sunshine zakładka Applications, np. Steam Big Picture. Rozdzielczość na grę ustawia się w aplikacji: przytrzymaj grę → Ustawienia gry.
5. **Poza domem.**
   - Tailscale na PC i telefonie (https://tailscale.com/download).
   - Potem przytrzymaj PC → Adresy połączenia → dodaj adres `100.x.y.z`.
   - Nie przekierowuj portów.
6. **Nie działa?**
   - PC niewidoczny: zapora, to samo Wi-Fi, adres ręcznie.
   - Pad nie działa: krok 2.
   - Obraz tnie: niższa jakość obrazu.

Źródła treści: `docs/host-setup.md`, `docs/host-apps.md`, `docs/tailscale.md`, `docs/DECISIONS.md`. `URUCHOMIENIE.md` jest dla dewelopera, nie używać.

## 5. Pakiety

Najpierw P0, potem P1–P4 równolegle (rozłączne pliki).

| Pakiet | Zakres | Pliki (właściciel) |
|---|---|---|
| P0 Fundament | D1, D2, D6, D8, U7, U12 | `thumbcast_colors.xml`, `thumbcast_dimens.xml`, `thumbcast_styles.xml` (`Theme.Thumbcast`), wspólne ikony, `thumbcast_strings_common.xml`, `AndroidManifest.xml` (motyw ekranów poza `Game`, etykiety), moduł `:control-ui` (build, manifest, `ThumbcastTheme.kt`, `UiContract.kt`, zaślepki aktywności), `settings.gradle.kts`, `app/build.gradle` (zależność), `control-editor` (podpięcie motywu, U20) |
| P1 Kreator i poradnik | sekcje 3–4 | tylko `control-ui/**` (bez zmiany kontraktu bez potrzeby) |
| P2 Ekran startowy | U1 (hak), U2, U3, U8–U11, U13, U15–U18 | `PcView.java`, `AppView.java`, `grid/PcGridAdapter.java`, `utils/HelpLauncher.java`, layouty PC, AppView i dodawania PC (nowe `thumbcast_*` lub bloki THUMBCAST), `HostAddressesDialog.java`, `ThumbcastAppSettingsDialog.java`, `thumbcast_strings_launcher.xml`, `thumbcast_ic_pc_*` |
| P3 Ustawienia i biblioteka profili | U4–U6, U14 | `preferences/StreamSettings.java`, nowe `xml/thumbcast_preferences.xml`, `app/src/nonRoot/res/**`, `ProfileLibraryActivity.java` i jego layouty, `thumbcast_strings_settings.xml`, `thumbcast_strings_library.xml`, `thumbcast_ic_set_*` |
| P4 Menu w grze | G1–G17 | `ThumbcastMenuView.java`, `ThumbcastMenuModel.java`, `ThumbcastSession.java`, `ThumbcastStatsView.java`, `ThumbcastSettings.java`, `control-android/**` (menu, stats, overlay i testy, `docs/android.md`), `thumbcast_strings_game.xml`, `thumbcast_ic_game_*` |
