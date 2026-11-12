# Moduł `:control-ui`

Wspólny motyw Compose i nowe ekrany Thumbcast (kreator pierwszego uruchomienia, poradnik „Jak podłączyć PC”).
Źródło decyzji: `docs/ui-audit.md` (D1, D2, D8, sekcje 3 i 4). Moduł powstał w pakiecie P0 (12.11.2026), kreator i
poradnik dodał pakiet P1 (12.11.2026). Na urządzeniu jeszcze niesprawdzone.

Zależności: Compose + Material 3 (BOM z `libs.versions.toml`), `activity-compose`, `androidx.core`.
Używają go `:app` (kontrakt, aktywności) i `:control-editor` (motyw). Moduł nie zależy od innych modułów Thumbcast.

## Motyw (`ThumbcastTheme.kt`)

- `@Composable fun ThumbcastTheme(content)`: `MaterialTheme` z `ThumbcastColorScheme`, `ThumbcastShapes`,
  `ThumbcastTypography`. Tylko ciemny, bez dynamic color.
- `ThumbcastColors`: paleta D1 jako stałe `Color` (te same wartości co `app/src/main/res/values/thumbcast_colors.xml`;
  przy zmianie poprawić oba pliki). Kolory spoza schematu M3: `Success`, `Warning`, `*Dim` (tła plakietek),
  `Scrim`, `MenuSurface`, `Divider` (nad wideo).
- Mapowanie na role M3:

  | Rola M3 | Kolor |
  |---|---|
  | `primary`, `secondary` / `onPrimary` | akcent `#5B8CFF` / `#FFFFFF` |
  | `background`, `surfaceContainerLowest`, `surfaceDim` | tło `#0F1115` |
  | `surface`, `surfaceContainerLow`, `surfaceContainer` | powierzchnia `#181B22` |
  | `surfaceVariant`, `surfaceContainerHigh(est)`, `secondaryContainer` | powierzchnia podniesiona `#222632` |
  | `onSurface`, `onBackground` / `onSurfaceVariant` | tekst `#E8EAF0` / tekst drugorzędny `#A3A9B8` |
  | `outlineVariant` | obrys `#2E3340` (dekoracyjny) |
  | `outline` | `#737A8C` (poza D1: obrys pól tekstowych, kontrast ok. 4:1 z tłem) |
  | `tertiary` | sukces `#3DDC97` |
  | `error` / `errorContainer` | `#FF6B6B` / `#93000A` |
  | `primaryContainer`, `tertiaryContainer` | 20 % akcentu / sukcesu na powierzchni |

- Kształty: `extraSmall` 8 dp, `small` 12 dp, `medium` 16 dp (karty), `large` i `extraLarge` 28 dp (dialogi, arkusze).
- Typografia: skala M3 bez zmian (rozmiary jak `thumbcast_text_*` w `thumbcast_dimens.xml`).
- `ThumbcastDimens`: odstępy 4/8/16/24 dp, obszar dotyku 48 dp, ikona 24 dp, rogi 12/16/28 dp.
- Karta w Compose ma kolor jak `thumbcast_bg_card` po podaniu
  `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`; domyślny `Card` M3 bierze
  `surfaceContainerHighest` (powierzchnia podniesiona).

## Kontrakt (`UiContract.kt`, `app.thumbcast.ui.UiContract`)

Obiekt Kotlin ze stałymi `const` i funkcjami `@JvmStatic`, wołany z Javy w `:app` (`UiContract.EXTRA_PRESET_FILE`,
`UiContract.shouldShowOnboarding(...)`).

| Element | Wartość / sygnatura |
|---|---|
| Klasy aktywności | `ONBOARDING_ACTIVITY` = `app.thumbcast.ui.OnboardingActivity`, `SETUP_GUIDE_ACTIVITY` = `app.thumbcast.ui.SetupGuideActivity` |
| Wynik kreatora | `RESULT_OK` + `EXTRA_PRESET_FILE` (String, np. `gamepad-playstation.thumbcast.json`) + `EXTRA_HIDE_ON_GAMEPAD` (Boolean); `RESULT_CANCELED` = pominięto |
| Presety kreatora | `PRESET_GAMEPAD_XBOX`, `PRESET_GAMEPAD_PLAYSTATION`, `PRESET_MMO_KEYBOARD` (nazwy plików z `presets/`) |
| Wejście poradnika | `EXTRA_GUIDE_SECTION` (String, opcjonalny): `SECTION_INSTALL` `install`, `SECTION_GAMEPAD` `gamepad`, `SECTION_PAIR` `pair`, `SECTION_APPS` `apps`, `SECTION_REMOTE` `remote`, `SECTION_TROUBLESHOOT` `troubleshoot`; lista `GUIDE_SECTIONS` |
| Wersja kreatora | `PREF_ONBOARDING_VERSION` = `thumbcast_onboarding_version` (int, domyślne SharedPreferences aplikacji), `CURRENT_ONBOARDING_VERSION` = 1 |
| Logika | `shouldShowOnboarding(storedVersion, hasPcs)`, `normalizeGuideSection(raw)`, `isValidPresetFile(name)` |
| Intencje | `onboardingIntent(ctx)`, `setupGuideIntent(ctx, section)`, `guideSectionFrom(intent)` |
| Wynik | `onboardingResultData(OnboardingResult)` (dla kreatora), `parseOnboardingResult(resultCode, data)` → `OnboardingResult?` (`getPresetFile()`, `isHideOnGamepad()`) |

Podział odpowiedzialności:
- Kreator niczego nie zapisuje. `:app` po wyniku zapisuje preset w `list_thumbcast_profile` (mechanizm profilu
  domyślnego), „Chowaj przyciski, gdy gram padem” w `ThumbcastSettings` i wersję `CURRENT_ONBOARDING_VERSION`
  w `PREF_ONBOARDING_VERSION` (także przy `RESULT_CANCELED`).
- Gdy lista PC nie jest pusta, `:app` nie uruchamia kreatora, tylko zapisuje wersję.
- Ponowne uruchomienie z Ustawień (Pomoc → „Uruchom konfigurację od nowa”) używa tego samego intentu i tej samej
  obsługi wyniku.
- Poradnik nie zwraca wyniku. Nieznana sekcja jest ignorowana (poradnik otwiera się od początku).

Testy JVM: `src/test/.../UiContractTest.kt` (logika kontraktu, istnienie presetów kreatora w `presets/`).
P1 nie zmienił publicznego API kontraktu (bez dodatków).

## Aktywności

Zadeklarowane w `src/main/AndroidManifest.xml` (`exported=false`, bez blokady orientacji jak ekrany `:app`
poza grą, `configChanges` jak w `:app`), motyw okna `Theme.ThumbcastUi` (tło `#0F1115`, bez paska akcji),
`enableEdgeToEdge` z ciemnymi paskami systemowymi. Treść ma najwyżej 720 dp szerokości i jest wyśrodkowana
(tablet, poziom); wszystko się przewija, więc działa też przy dużej czcionce systemowej.

### Kreator (`OnboardingActivity`, `OnboardingScreen.kt`)

Pięć kroków (sekcja 3 audytu; krok 4 dodany 12.11.2026), na każdym „Krok N z 5”, pasek postępu i „Pomiń”
u góry, na dole „Wstecz” (od kroku 2) i przycisk główny. Każdy krok ma dużą ikonę nad tytułem, każdy punkt ikonę
z numerem w rogu (`IconStep`), dopiski mają małą ikonę (`NoteRow`):
1. Powitanie: tytuł, 4 korzyści z ikonami (dotyk/pad, PC robi pracę, parowanie PIN-em, poza domem), [Zaczynamy].
2. „Czym grasz najczęściej?”: 5 kafli (wybór jednokrotny, `Role.RadioButton`, kółko wyboru obok koloru),
   domyślnie „Pad PS4 / PS5”, dopisek „Zmienisz to w Ustawieniach”. Na szerokim ekranie (≥ 560 dp) dwie kolumny.
3. „Zainstaluj Sunshine na PC”: [Wyślij sobie link] (share sheet: podpowiedź z nazwą pliku i adres
   `releases/latest`) i [Pokaż pełny poradnik] (sekcja `install`) nad krokami, potem 8 punktów prowadzących przez
   GitHub dla laika: strona GitHub bez konta, sekcja Assets i „Show all … assets”, plik
   `Sunshine-Windows-AMD64-installer.msi` (Ctrl+F `installer.msi`), instalator, `https://localhost:47990`,
   ostrzeżenie przeglądarki o certyfikacie, login i hasło.
4. „Zainstaluj sterownik pada”: zawsze, niezależnie od kafla z kroku 2 (decyzja właściciela 12.11.2026). ViGEmBus
   opisany jako sterownik wymagany do grania padem (bez „za darmo”); 6 punktów: link, żółta informacja
   „archived” to norma, plik `ViGEmBus_1.22.0_x64_x86_arm64.exe`, instalacja, restart, Sunshine → Input → Xbox 360.
   Dopisek: są płatne zamienniki (LizardByte Virtual HID Driver), ViGEmBus wystarczy. Przyciski jak w kroku 3
   (link ViGEmBus, poradnik od sekcji `gamepad`).
5. „Połącz”: 3 punkty (to samo Wi-Fi, wybór PC, PIN w Sunshine), dopiski o dodaniu PC po IP i o tym, gdzie
   zawsze jest poradnik, przycisk [Przejdź do listy komputerów].

Mapowanie kafli na wynik (`onboarding/OnboardingFlow.kt`, `OnboardingChoice`):

| Kafel | `EXTRA_PRESET_FILE` | `EXTRA_HIDE_ON_GAMEPAD` |
|---|---|---|
| Dotyk – układ Xbox | `gamepad-xbox.thumbcast.json` | false |
| Dotyk – układ PlayStation | `gamepad-playstation.thumbcast.json` | false |
| Pad Xbox 360 / One / Series | `gamepad-xbox.thumbcast.json` | true |
| Pad PS4 / PS5 (domyślny) | `gamepad-playstation.thumbcast.json` | true |
| Mysz i klawiatura (MMO) | `mmo-keyboard.thumbcast.json` | false |

Wynik i nawigacja (decyzja P1, logika w `OnboardingState`, testy `OnboardingFlowTest`):

| Akcja | Krok 1 | Kroki 2–5 |
|---|---|---|
| „Pomiń” | `RESULT_CANCELED` (nic nie wybrano, `:app` zostawia ustawienia domyślne) | `RESULT_OK` z bieżącym zaznaczeniem (domyślnie Pad PS4 / PS5) |
| Wstecz (przycisk, gest systemowy) | zamknięcie z `RESULT_CANCELED` | poprzedni krok, wybór zostaje |
| Przycisk główny | następny krok | następny krok; na kroku 5 `RESULT_OK` z zaznaczeniem |

Uzasadnienie „Pomiń” od kroku 2: kafel jest już widocznie zaznaczony, więc użytkownik, który go zostawił,
dostaje to, co widział. W obu przypadkach `:app` zapisuje wersję kreatora (patrz kontrakt). Stan (krok, kafel)
przetrwa obrót ekranu (`rememberSaveable`).

### Poradnik (`SetupGuideActivity`, `SetupGuideScreen.kt`)

Pasek u góry ze strzałką wstecz i tytułem „Jak podłączyć PC”, wstęp z licznikiem „Zrobione: N z 5”, sześć
rozwijanych kart w kolejności `UiContract.GUIDE_SECTIONS` (sekcja 4 audytu): instalacja Sunshine, sterownik pada
(ViGEmBus), parowanie, gry, poza domem, „Nie działa?”. Każda karta: ikona, „N. Tytuł”, kroki z ikonami i numerem
(`string-array` + `GuideSectionUi.stepIcons`, ta sama kolejność), kod (`https://localhost:47990`, `100.x.y.z`,
nazwy plików) czcionką o stałej szerokości, dopiski z ikonami (`notes`). Sekcje Sunshine i sterownika prowadzą
przez GitHub krok po kroku (Assets, nazwa pliku, ostrzeżenia Windows i przeglądarki); sterownik nie jest
opisany jako darmowy, płatne zamienniki są w dopisku. „Dodaj gry” i „Nie działa?” opisują zamykanie gry z launchera
(Undo `taskkill` w Sunshine, `docs/host-apps.md` sekcja 2a); „Nie działa?” ma skok do „Dodaj gry”.

- Strony pobierania (`guide/GuideLinks`): Sunshine `https://github.com/SunshineStream/Sunshine/releases/latest`,
  ViGEmBus `https://github.com/nefarius/ViGEmBus/releases/latest` (od razu strona jednego wydania z Assets),
  Tailscale `https://tailscale.com/download`. Nazwy plików w tekstach sprawdzone 12.11.2026 (Sunshine
  v2026.914.233613, ViGEmBus v1.22.0, ostatnie wydanie zarchiwizowanego repo). Przy każdej
  [Wyślij link na PC] (share sheet z jedną linią podpowiedzi) i [Otwórz stronę] (`ACTION_VIEW`); adres da się
  zaznaczyć i skopiować. Brak aplikacji (`ActivityNotFoundException`): adres trafia do schowka, komunikat w snackbarze.
- „Nie działa?” ma przyciski „Zobacz: …” do sekcji pada i „Poza domem” (rozwija i przewija).
- Lista kontrolna: pole „Oznacz jako zrobione” w sekcjach 1–5 („Nie działa?” nie ma czego odhaczać), w nagłówku
  ✓ i napis „Zrobione”. Zapis we własnych SharedPreferences modułu `thumbcast_ui_guide`, klucz `done_sections`
  (zbiór id sekcji). Czysto informacyjne, nic poza poradnikiem tego nie czyta.
- Sekcja startowa: poprawna `EXTRA_GUIDE_SECTION` jest rozwinięta i lista zaczyna się od niej; bez niej rozwinięta
  jest pierwsza nieodhaczona sekcja (nowy użytkownik: instalacja), a gdy wszystko odhaczone, żadna
  (`SetupGuideModel.initialSection`, testy `SetupGuideModelTest`).

### Dostępność

Obszary dotyku ≥ 48 dp (przyciski, nagłówki kart 64 dp, wiersz pola wyboru), tytuły kroków i nagłówki kart jako
`heading`, nagłówek karty ma etykietę akcji „Rozwiń”/„Zwiń” i stan „Rozwinięte”/„Zwinięte”, numerowany krok jest
czytany jako jedna pozycja, „Krok N z 5” to `liveRegion`. Ikony są dekoracyjne (obok zawsze jest tekst), strzałka
wstecz ma opis „Wróć”. Kolory z palety D1; stan nigdy tylko kolorem (kółko wyboru, ✓ z napisem).

### Ikony (`UiIcons.kt`)

Material Symbols Rounded jako `ImageVector` w kodzie, te same ścieżki co `thumbcast_ic_*` w `:app` (D6), bez
zależności `material-icons-*` i bez zasobów `drawable` w module.

### Pliki

| Plik | Zawartość |
|---|---|
| `onboarding/OnboardingFlow.kt` | `OnboardingStep`, `OnboardingChoice`, `OnboardingState`, `OnboardingOutcome` (czysta logika) |
| `guide/SetupGuideModel.kt` | `GuideLinks`, `SetupGuideModel`, `parseInlineCode` (czysta logika) |
| `OnboardingActivity.kt`, `OnboardingScreen.kt` | kreator |
| `SetupGuideActivity.kt`, `SetupGuideScreen.kt` | poradnik, treść sekcji (`GUIDE_SECTIONS_UI`) |
| `UiComponents.kt` | `LinkActions` (otwieranie i share sheet), `InlineCodeText`, `IconStep`/`IconSteps`, `NoteRow`, `NumberedStep` |

## Teksty

`res/values/strings.xml` (angielski, domyślny) i `res/values-pl/strings.xml` (polski, główny), prefiks `ui_`,
bo zasoby bibliotek łączą się z zasobami `:app`. Kroki są w `string-array` (ta sama liczba pozycji w obu językach
i tyle samo ikon w kodzie; bez ikony krok dostaje sam numer),
kod zapisuje się w znakach `` ` ``. Treść poradnika pochodzi z `docs/host-setup.md`, `docs/host-apps.md`,
`docs/tailscale.md` i `docs/DECISIONS.md`; przy zmianie tych dokumentów sprawdź poradnik. Nazwy pozycji menu `:app`
użyte w tekstach: „Adresy połączenia”, „Dodaj adres”, „Ustawienia gry” (z audytu, U15), „Jakość obrazu” (D7).
Wspólne słowa dla widoków `:app` są w `app/src/main/res/values*/thumbcast_strings_common.xml`.
