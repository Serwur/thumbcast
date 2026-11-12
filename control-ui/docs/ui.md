# Moduł `:control-ui`

Wspólny motyw Compose i nowe ekrany Thumbcast (kreator pierwszego uruchomienia, poradnik „Jak podłączyć PC”).
Źródło decyzji: `docs/ui-audit.md` (D1, D2, D8). Moduł powstał w pakiecie P0 (12.11.2026); treść ekranów dodaje P1.

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

## Aktywności

Zadeklarowane w `src/main/AndroidManifest.xml` (`exported=false`, bez blokady orientacji jak ekrany `:app`
poza grą, `configChanges` jak w `:app`), motyw okna `Theme.ThumbcastUi` (tło `#0F1115`, bez paska akcji),
`enableEdgeToEdge` z ciemnymi paskami systemowymi. Stan P0: zaślepki (`StubScreen`) z tytułem i przyciskiem
„Zamknij”; `OnboardingActivity` zawsze zwraca `RESULT_CANCELED`.

## Teksty

`res/values/strings.xml` (angielski, domyślny) i `res/values-pl/strings.xml` (polski, główny), prefiks `ui_`,
bo zasoby bibliotek łączą się z zasobami `:app`. Wspólne słowa dla widoków `:app` są w
`app/src/main/res/values*/thumbcast_strings_common.xml`.
