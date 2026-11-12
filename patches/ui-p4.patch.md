# Patch: menu w grze (pakiet P4)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Specyfikacja: `docs/ui-audit.md`
(G1–G17, decyzje D1, D5, D6, D7, D9). Pewny stan po scaleniu wszystkich pakietów: nowy `git diff v12.2 -- app/`
(`0001-thumbcast.diff` odświeża orkiestrator).

## Zmiany w plikach upstreamu

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `app/src/main/java/com/limelight/Game.java` | `connectionStarted()` → `runOnUiThread` → `hideSystemUi(1000);` | pod tą linią blok `THUMBCAST`: `if (thumbcast != null) thumbcast.onConnectionStarted();` |

Hak jest potrzebny, bo podpowiedź „jak otworzyć menu” (G6) ma się pokazać po zniknięciu okna „Łączenie…”, a nie
w `onCreate` (pod oknem postępu wygasłaby, zanim użytkownik ją zobaczy). Hak tylko pokazuje napis: nic nie wysyła
do hosta i nie zmienia wejścia.

## Pliki Thumbcast w `app/` (bez zmian upstreamu)

- `thumbcast/ThumbcastMenuModel.java`: strona menu to `Page` (tytuł, podstrona, blok wstępu, wiersze). `Item` ma
  `Kind` (PRIMARY, SECONDARY, DANGER, TILE, ROW, NAV, SWITCH, STEPPER, SEGMENTED, CHOICE), sekcję, ikonę, podpis,
  stan i domyślne zaznaczenie z pada. Strona główna: „Wróć do gry”; kafle Klawiatura i Ukryj/Pokaż przyciski;
  Sterowanie (Profil sterowania → strona profili, Gesty myszy z krótkim podpisem zależnym od rodzaju gestów,
  Rozmiar przycisków, Chowaj przyciski, gdy gram padem); Obraz (Statystyki: Wył. / Podstawowe / Szczegółowe);
  Wyjście (Wstrzymaj i wyjdź, Zamknij grę na PC). Profile w sekcjach „Wbudowane” i „Moje”. Potwierdzenie
  zamknięcia gry: ikona ostrzeżenia, tytuł, akapit, „Anuluj” (domyślne zaznaczenie), „Zamknij grę”.
  Od 12.11.2026 akapit mówi wprost, że gra z launchera (Battle.net, Steam) zamknie się tylko z poleceniem Undo
  w Sunshine (`docs/host-apps.md`, sekcja 2a), a komunikat po `/cancel` to „Zakończono sesję na PC” (Thumbcast nie
  widzi procesów PC). To samo w potwierdzeniu na liście: `applist_quit_confirmation` nadpisane w
  `app/src/nonRoot/res/values{,-pl}/thumbcast_overrides.xml`.
- `thumbcast/ThumbcastMenuView.java`: przepisany widok. Panel przy krawędzi END z rogami 28 dp od strony wideo,
  wjazd 150 ms, scrim i kolory z `thumbcast_colors.xml`. Stały nagłówek, przewijana treść, stopka „A Wybierz · B Wstecz”
  w trybie zaznaczenia. Przełącznik rysowany (tor i gałka), segmenty, stepper z ikonami w kółkach i wartością
  w pigułce. Stan w `contentDescription` i w `AccessibilityNodeInfo` (Switch, RadioButton). Nadal bez fokusu Androida:
  klawiaturę i pad obsługuje `MenuInputRouter` przez `ThumbcastSession`.
- `thumbcast/ThumbcastMenuHintView.java` (nowy): pigułka z ikoną menu i tekstem; nie bierze dotyku, układa się
  pod przyciskami na środku u góry.
- `thumbcast/ThumbcastSession.java`: budowanie stron (tytuł = nazwa gry z `Game.EXTRA_APP_NAME`), `onBackRequested`,
  animacja wjazdu, powtarzanie trzymanego kierunku pada w menu (`MenuInputRouter.heldVertical`, 400 ms, potem co 120 ms),
  podpowiedź przy starcie (`onConnectionStarted`, 4 s albo do otwarcia menu), bez toastu po „Gesty myszy”,
  krótkie komunikaty błędów profilu (szczegóły w logu). Akcje wyjścia bez zmian: „Wstrzymaj i wyjdź” = `finish()`,
  „Zamknij grę” = `finish()` i `/cancel` po zatrzymaniu strumienia.
- `thumbcast/ThumbcastHostQuit.java`: komunikaty jednym zdaniem ze wskazówką, kod HTTP i wyjątek tylko w logu.
- `thumbcast/ThumbcastSettings.java`: `thumbcast_menu_hint_shown` (`isMenuHintShown`, `setMenuHintShown`).
- `thumbcast/ThumbcastStatsView.java`: 12 sp, kolory z palety.
- `res/values/thumbcast_strings_game.xml`, `res/values-pl/thumbcast_strings_game.xml` (nowe, D5): wszystkie teksty
  menu w grze, podpowiedzi i nakładki statystyk (`thumbcast_game_*`). Stare klucze w `thumbcast_strings.xml`
  (`thumbcast_menu_*`, `thumbcast_end_session_*`, `thumbcast_stats_basic*`, `thumbcast_stats_waiting`,
  `thumbcast_profile_error`) nie są już używane przez menu; sprząta je orkiestrator. `thumbcast_mouse_gestures_*`
  używa dalej biblioteka profili (`ThumbcastSession.mouseGesturesHint`).
- `res/drawable/thumbcast_ic_game_menu.xml` (nowy, D6): Material Symbols Rounded „menu”.

## `:control-android`

- `menu/MenuInputRouter.kt`: `MenuFocus.move` z zawijaniem, `MenuFocus.step` (segmenty, bez zawijania),
  `MenuInputRouter.heldVertical()`.
- `stats/StatsMode.kt`: `StatsToggles.withMode(mode)`; zapis bez zmian (dwie flagi).
- `stats/StatsFormatter.kt`: tylko opis; linia „60 FPS · 23 ms” pochodzi z nowych wzorców w zasobach.
- `overlay/SystemTouchTargets.kt` (nowy): obszar dotyku przycisków systemowych min. 48 dp (bez nachodzenia na inne
  kontrolki), krok na końcu profilu efektywnego w `ThumbcastOverlay`; format profilu bez zmian.
- `overlay/OverlayRenderer.kt`: przyciski systemowe jako pigułka z ikoną z linii (menu, klawiatura, oko).
- Testy: `MenuInputRouterTest` (zawijanie, `step`, `heldVertical`), `StatsTogglesTest` (`withMode`), `StreamStatsTest`
  (linia kompaktowa), `SystemTouchTargetsTest` (nowy).

## Do sprawdzenia na urządzeniu

Wygląd panelu na różnych szerokościach (300–420 dp), wjazd, przewijanie padem, TalkBack, podpowiedź po połączeniu
(raz), pigułki ☰/⌨ i ich obszar dotyku na Big Picture (ok. 46 × 43 dp, ciasno obok LT), nakładka statystyk 12 sp.
