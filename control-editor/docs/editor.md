# Edytor układu (`:control-editor`)

Stan: 11.11.2026, MVP z planu (sekcja 7 „Edytor układu”, etap E2) bez trybu testu na żywo. Testy JVM logiki zielone; na urządzeniu sprawdzone tylko na emulatorze (patrz „Ograniczenia”).

## Kontrakt

- Aktywność: `app.thumbcast.editor.LayoutEditorActivity` (manifest modułu, `exported=false`, `sensorLandscape`, pełny ekran immersive, wycięcie ekranu `shortEdges`).
- Wejście: extra `app.thumbcast.editor.extra.PROFILE_JSON` (`String`, JSON profilu). Profil może łamać reguły walidatora (otwiera się z listą problemów), ale nie może mieć typów zakazanych ani wielu wyjść (`ProfileCodec.decodeUnvalidated`). Brak extra lub zły JSON: komunikat i `RESULT_CANCELED`.
- Wynik: `RESULT_OK` z tym samym extra = JSON po edycji, przechodzący `ProfileValidator.validate` i pełny `ProfileCodec.decode(encode(...))`. Zamknięcie bez zapisu: `RESULT_CANCELED`.
- Wygodnie: `LayoutEditorActivity.intent(context, json)` i `EXTRA_PROFILE_JSON` jako stała.
- Back przy niezapisanych zmianach pyta, czy porzucić. Zapis zamyka edytor.
- Motyw: `ThumbcastTheme` z `:control-ui` (paleta D1 z `docs/ui-audit.md`, od 12.11.2026; wcześniej domyślny `darkColorScheme()`).

## Funkcje

- Płótno na cały ekran, współrzędne profilu 0..1 = ekran. Kontrolki rysuje `OverlayRenderer` z `:control-android` (`ControlLayout` + `OverlaySnapshotBuilder`, `EngineVisualState.EMPTY`), tak samo jak w grze. Edytor dorysowuje przerywaną linią strefę dotyku (`rect`), szachownicę, siatkę co 10% i zaznaczenie.
- Dotknięcie zaznacza, przeciąganie przesuwa, uchwyt w prawym dolnym rogu zmienia szerokość i wysokość niezależnie, szczypanie (dwa palce w dowolnym miejscu) skaluje zaznaczoną kontrolkę z zachowaniem proporcji. Przyciąganie do siatki 1% (wyłączalne). Kontrolka nie wychodzi poza ekran, najmniejszy bok 2%, współrzędne zaokrąglane do 4 miejsc.
- Panel (300 dp, po prawej lub lewej, ukrywany): cofnij/ponów (100 kroków; gest i ruch suwaka to jeden krok, pisanie etykiety lub nazwy też), zapisz, zamknij, licznik problemów z listą; nazwa profilu, przezroczystość globalna, siatka, lustro całego układu (`HandednessTransform.mirror`); dodaj przycisk, gałkę, modyfikator, systemowy; duplikuj, usuń; etykieta (pusta = nazwa wyjścia), rozmiar (suwak, proporcje zostają), wyjście, kształt, `hold`/`latch` (tylko modyfikator), `floating`/`deadzone`/`sprintLock` (tylko gałka).
- Wyjścia do wyboru (`OutputCatalog`) to podzbiór tego, co dopuszcza walidator: przycisk = pad, triggery (tylko Pad+), klawisze, przyciski myszy; modyfikator = LT/RT/LB/RB (tylko Pad+) lub SHIFT/CTRL/ALT; gałka = lewa/prawa; systemowy = akcje aplikacji. Akordów i typów zakazanych (sequence, macro, repeat, turbo, delay, toggle_auto) nie da się utworzyć; duplikat nigdy nie kopiuje akordu, a akord z importu można usunąć przyciskiem (FAIR_PLAY.md).
- Nowa gałka dostaje wolną gałkę pada (prawą zajmuje kamera `pad_right_stick`), w trybie `keyboard_mouse` klawisze W/S/A/D.
- Zapis zablokowany, dopóki walidator zgłasza błędy (np. konflikt dwóch gałek, pusta nazwa).

## Budowa

- `state/` (czysty Kotlin, testy JVM JUnit 4 w `src/test`): `EditorState` (niezmienny stan + operacje + historia), `EditorGeometry`, `OutputCatalog`, `ControlFactory`, `EditorIo`. Test `FairPlayEditorTest` wykonuje 2 × 3000 losowych operacji i sprawdza: unikalne id, brak akordów i `latch` na przyciskach, prostokąty na ekranie, `validateRaw` czysty, brak błędów walidatora poza konfliktem gałek.
- `ui/` (cienkie Compose): `EditorScreen`, `EditorCanvas`, `PropertiesPanel`, `EditorStore` (referencja do stanu).
- Teksty: `res/values` (angielski) i `res/values-pl`. Komunikaty walidatora pochodzą z `:control-model` i są po polsku.

## Ograniczenia

- Brak trybu testu na żywo, tła ze zrzutu klatki, linii pomocniczych przy przeciąganiu, walidacji nachodzenia kontrolek, resetu do presetu, ikon, krzywych gałki, edycji `kbmKeys`, `outerDeadzone` i ustawień gestów (plan sekcja 7).
- Historia cofania nie przetrwa śmierci procesu (zostaje bieżący i ostatnio zapisany profil).
- Proporcja ekranu edytora to proporcja telefonu; profil jest znormalizowany do ekranu, więc na innym ekranie kontrolki zmienią kształt (jak w grze).
- Zmiana trybu profilu (Pad+ / Klawiatura+Mysz) nie jest w edytorze.
