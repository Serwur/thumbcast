# CLAUDE.md

Projekt Thumbcast: dotykowe sterowanie do streamingu gier z PC na Androida (Sunshine + fork Moonlight). Dokumenty po polsku, identyfikatory kodu po angielsku.

## Moduły
- `control-model` (JVM): profil JSON, walidacja, migracje. Zakazane typy: sequence, macro, repeat, turbo, delay, toggle_auto. Wolna strefa `gestures.freeZone`: `camera` | `touchpad` | `none` (od 12.11.2026, tylko gest menu; presety Pad – Xbox/PlayStation) | `direct` (od 12.11.2026, mysz bezpośrednia: tap = klik w punkcie, 2 palce = PPM, przeciągnięcie = przewijanie; Big Picture i „Gesty myszy” na padach).
- `control-engine` (JVM): `InputEngine`, rozpoznawanie gestów, `ActionPolicy`. Wszystkie wyjścia idą przez politykę. Szczegóły: `control-engine/docs/engine.md`.
- `control-android`: `ThumbcastOverlay` i adaptery. Retransmisja stanu pada (25/50/75 ms) jest tylko w `HostPadReporterImpl`, silnik jej nie robi.
  Biblioteka profili (`library/ProfileRepository`, import/eksport) i `EditorContract`; opis `docs/profiles.md`.
  Nakładka statystyk w grze (`stats/`: liczby z liczników upstreamu, opóźnienie = RTT + host + telefon) i strony menu w grze (`menu/MenuPage`, „Zakończ sesję” = `/cancel`); opis `control-android/docs/android.md`.
  Profil domyślny dostarczany z aplikacją: `ThumbcastProfiles.DEFAULT_PRESET` = `gamepad-playstation.thumbcast.json` (od 12.11.2026; jedno źródło, `PreferenceConfiguration` go używa). Przełącznik „Gesty myszy” na profil: `MouseGesturesTransform` (`control-model`), zapis na telefonie `thumbcast_mouse_gestures:<plik>`, nie w pliku profilu.
  Adresy PC (`net/HostAddresses`, czysta logika z testami JVM): jeden wpis PC = slot LAN + adresy alternatywne (Tailscale, ZeroTier); odpowiedź przez VPN nie nadpisuje LAN, kolejność LAN → alternatywne. Most w `:app`: `thumbcast/ThumbcastAddressBook`, dialog `HostAddressesDialog`; opis `patches/host-addresses.patch.md`.
  Menu w grze: `menu/MenuInputRouter` (klawiatura/pad przy otwartym menu nie idą do hosta, nawigacja padem); zmiana profilu w trakcie gry w `ThumbcastSession` (`:app`).
  Ustawienia aplikacji hosta (`apps/AppStreamSettings`, `AppStreamOverrides`, testy JVM): profil, rozdzielczość i FPS na aplikację Sunshine, klucz = uuid PC + id aplikacji (zapasowo nazwa). Most w `:app`: `thumbcast/ThumbcastAppSettings` (hak w `Game.onCreate`), dialog `ThumbcastAppSettingsDialog` (menu aplikacji w `AppView`); opis `docs/host-apps.md`, `patches/app-settings.patch.md`.
- `control-editor` (Compose): `LayoutEditorActivity`, edytor układu (od 11.11.2026). Opis: `control-editor/docs/editor.md`.
- `control-ui` (Compose, od 12.11.2026): `ThumbcastTheme`, `UiContract`, kreator pierwszego uruchomienia (`OnboardingActivity`) i poradnik „Jak podłączyć PC” (`SetupGuideActivity`, offline). Opis: `control-ui/docs/ui.md`. Wygląd całej aplikacji: specyfikacja `docs/ui-audit.md` (paleta D1, ikony `thumbcast_ic_*`, słownik D7, teksty w plikach `thumbcast_strings_<obszar>.xml`), `Theme.Thumbcast` w `:app`; ustawienia z `xml/thumbcast_preferences.xml` (nowa preferencja także w upstreamowym `preferences.xml`, bo czyta go `setDefaultValues`).
- `patches/`: opisy zmian w `:app` (`*.patch.md`) i pewny diff `0001-thumbcast.diff` (`git diff v12.2 -- app/`). Od 11.11.2026 nałożone w `app/` (bloki `THUMBCAST`).

## Zasady
- 1 dotyk = 1 wejście. Bez makr, sekwencji, turbo, timerów generujących naciśnięcia (`FAIR_PLAY.md`).
- Nazwy, pakiety i zasoby bez „WoW", „Blizzard", „Moonlight".
- Kontrakt typów: `CONTRACT.md` i `CONTRACT-NOTES.md`.

## Dokumenty i decyzje
- `docs/PLAN.md`: plan techniczny (wygenerowany 8.11.2026, kopia źródłowa, nie poprawiać treści).
- `docs/DECISIONS.md` (10.11.2026) **ma pierwszeństwo przed PLAN.md**. Skrót: host = Sunshine (GPLv3) lub forki Apollo/Vibepollo; pad w Windows = ViGEmBus 1.17+ (darmowy, zarchiwizowany) albo płatny LizardByte Virtual HID Driver; klient wysyła standardowe pakiety pada niezależnie od sterownika hosta; własnego streamingu ani sterownika nie robimy; zdalnie przez Tailscale/ZeroTier (nie port forwarding, nie tunele HTTP); tryb Klawiatura+Mysz to plan awaryjny dla hostów bez pada.
- Repo: `Serwur/thumbcast` (nazwa zmieniona 11.11.2026, dawniej `wow-portable`). Agent nie zmienia ustawień repo. Stan i następne kroki: `docs/HANDOFF.md`.
- Otwarte decyzje: landscape lock (DECISIONS.md, pkt h). Rozstrzygnięte 11.11.2026 (pkt j): repo = scalona historia upstreamu v12.2 (remote `upstream`), `applicationId` `io.github.serwur.thumbcast`.
- Konfiguracja hosta i testy: `docs/host-setup.md`, aplikacje w Sunshine i zmiana rozdzielczości PC: `docs/host-apps.md`, `docs/test-plan.md` (hosty H1 Virtual HID, H2 ViGEmBus, H3 bez pada, H4 macOS; scenariusz T-10 dla trybu bez pada).

## Uwagi
- Kod powstał offline. Od 11.11.2026 `:app:assembleNonRootDebug`/`Release` przechodzi z nałożonymi patchami, testy JVM (`control-model`, `control-engine` z 10^5 iteracji polityki, `control-android` unit) i lint są zielone; testy na urządzeniu jeszcze nie. Build: `JAVA_HOME` = JBR Android Studio (systemowa Java 1.8 się nie nadaje). Start: `URUCHOMIENIE.md`, `docs/NEXT-STEPS.md`.
- `onTick(nowMs)` w `InputEngine` jest poza kontraktem, ale używany przez nakładkę.
- `LICENSE` zawiera pełny tekst GPLv3, identyczny z https://www.gnu.org/licenses/gpl-3.0.txt (35149 B, SHA-256 `3972dc97...6986`, sprawdzone 11.11.2026). Upstreamowy `LICENSE.txt` (starsza wersja tekstu z linkami http) usunięty. Nagłówek projektu i SPDX są w `NOTICE`.
- Lint presetów: `.github/scripts/check_presets.py` sprawdza pola `type`/`behavior` i klucze JSON, nie dowolne napisy (etykieta „Delay” jest dozwolona). Uruchamiany w jobie `lint`.
- `gradle/libs.versions.toml` ma aliasy `androidx-test-runner` (1.7.0) i `androidx-test-ext-junit` (1.3.0). Zależności `androidTestImplementation` (runner, ext-junit) w `control-android/build.gradle.kts` są odkomentowane (10.11.2026); `libs.junit4` nadal zakomentowane.
- `:control-android` używa z `kotlinx.serialization` tylko API `JsonElement` (`apps/AppStreamSettings`, zależność `implementation`, od 12.11.2026); plugin `kotlin-serialization` jest tam zbędny.

## Upstream zweryfikowany (moonlight-android v12.2, b48494cb96bf, 10.11.2026)
- W trybie Thumbcast `PreferenceConfiguration` musi wymuszać `onscreenController=true` (bit 0 maski pada, landscape, rumble). Patrz `patches/preferences.patch.md`.
- `KeyboardTranslator.translate(int, int)` ma 2 parametry. `applicationId` ustawia się we flavorze `nonRoot`; flavor `root` do usunięcia (maxSdk 25 vs minSdk 26).
- Patche to opisy kroków z kotwicami z v12.2, nie gotowe pliki `.patch`. Po nałożeniu zrób własny `git diff v12.2`.
- Patche nałożone i skompilowane 11.11.2026 (debug i release); nie testowane na urządzeniu ani z hostem.
- Zatwierdzone 10.11.2026 (DECISIONS.md pkt i): wymuszamy `onscreenController=true`, usuwamy flavor `root` i sufiks `.unofficial` po zmianie `applicationId`.
