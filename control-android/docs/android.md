# `:control-android`: overlay dotykowy i adaptery do upstreamu

Moduł Androida (pakiet `app.thumbcast.android`, minSdk 26, compileSdk 37), który łączy czysty
silnik wejścia (`:control-engine`) z ekranem strumienia (`Game` z upstreamu moonlight-android).
Nie zawiera logiki „co wysłać”: tę ma silnik i jego `ActionPolicy` (1 dotyk = 1 wejście).
Ten moduł robi trzy rzeczy: **tłumaczy dotyk**, **rysuje kontrolki** i **przekazuje wyjście do hosta**.

> Stan: kod napisany bez możliwości kompilacji (brak sieci i kotlinc w środowisku). Nic tu nie było
> budowane ani uruchamiane. Pierwszy build z siecią będzie pierwszą weryfikacją.

## Jak działa overlay

```
MotionEvent ──► ThumbcastOverlay.onTouchEvent
                 │  requestUnbufferedDispatch (ACTION_DOWN)
                 ▼
               MotionTranslator  (MotionSource ◄─ MotionEventSource)
                 │  DOWN/UP per wskaźnik (także POINTER_DOWN/UP), MOVE zbiorczo, CANCEL
                 │  ≤ 10 palców, tylko TOOL_TYPE_FINGER
                 ▼
               InputEngine.onTouch(TouchEvent)        (:control-engine)
                 ▼
               CompositeInputSink : InputSink
                 ├─ PadSink      ─► HostPadReporter.reportOscState(...)   ─► ControllerHandler
                 ├─ KeyboardSink ─► AndroidKeyCodes ─► KeyTranslator ─► HostConnection.sendKeyboardInput
                 ├─ MouseSink    ─► HostConnection.sendMouse*             ─► NvConnection
                 └─ app(action)  ─► AppActionHandler (UI, nic do hosta)
```

- **Jeden View.** `ThumbcastOverlay` rysuje wszystkie kontrolki na Canvas: joystick (pływający:
  podstawa pojawia się tam, gdzie wylądował kciuk; stały: w środku strefy), przyciski okrągłe,
  zaokrąglone i prostokątne (`shape`), modyfikatory (z wizualnym stanem LATCH), przyciski systemowe.
  Etykieta to `label` z profilu albo skrót wyjścia (`A`, `LT`, `M`). Przezroczystość: `global.opacity`.
- **Przyciski systemowe** (od 12.11.2026, G15). Kontrolka `system` z akcją `menu`, `soft_keyboard` albo
  `toggle_overlay` jest rysowana jako pigułka (kolor `thumbcast_menu_surface`, po wciśnięciu akcent) z ikoną
  z linii (`OverlayRenderer.SystemIcon`: trzy kreski, klawiatura, oko), a nie glifem czcionki; etykieta z profilu
  (`☰`, `⌨`) zostaje w pliku, ale nie jest rysowana. Inna akcja: zwykły przycisk z etykietą. Obszar dotyku:
  `SystemTouchTargets` powiększa `rect` kontrolek `system` do min. 48 dp w każdej osi (wokół środka, w granicach
  ekranu, do sąsiedniej kontrolki najwyżej połowa odstępu, więc nic nie nachodzi na wejście gry). Krok jest
  ostatni w profilu efektywnym i zależy od rozmiaru widoku, więc silnik (hit test, strefa aktywacji) i rysowanie
  mają ten sam prostokąt; zmiana rozmiaru widoku podaje silnikowi nowy profil tylko, gdy prostokąty się zmieniły.
  Format profilu i pliki bez zmian. Gdy sąsiad jest blisko (Big Picture: ☰ i ⌨ prawie stykają się, pod
  spodem LT), obszar zostaje mniejszy niż 48 dp (ok. 46 × 43 dp na telefonie 20:9); pozostałe presety mają pełne 48 dp.
- **Stan wizualny = stan silnika** (od 11.11.2026). Overlay nie robi własnego hit testu. „Wciśnięcie”,
  LATCH, palec → kontrolka (także w strefie aktywacji i po slide-to-press), środek, promień i wychylenie
  gałki bierze z `InputEngine.visualState()`. Haptyka wynika z przejść tego stanu przed i po zdarzeniu
  (`TouchFeedback`): wibruje tam, gdzie silnik naprawdę wcisnął. Podstawa gałki ma promień silnika
  (pełne wychylenie na obwodzie): stała = połowa krótszego boku `rect`, pływająca = 15% krótszego boku ekranu.
- **Podział klas.** `ThumbcastOverlay` to klej `View` (dotyk, impuls czasu przez `FrameTicker`, cykl życia,
  wykluczenia gestów). Rysowanie: `OverlaySnapshotBuilder` (czysta migawka z układu i stanu silnika) →
  `OverlayRenderer` (Canvas, bez znajomości silnika; do ponownego użycia w edytorze układu).
  `VideoGeometry` (czysta): prostokąt wideo i odniesienie → `Viewport`. `EffectiveProfile` (czysta):
  profil bazowy → przekształcenia (`ProfileStep`, np. skalowanie, `setProfileTransforms`) → ukrycie kontrolek;
  na końcu `SystemTouchTargets` (obszar dotyku przycisków systemowych, zależny od rozmiaru widoku).
  `GamepadPresenceMonitor`: wykrywanie pada fizycznego, publiczne i używalne poza overlayem.
- **Multitouch.** Do 10 wskaźników (`MotionTranslator.MAX_POINTERS`); 11. palec jest ignorowany w całości.
  `ACTION_POINTER_DOWN/UP` dają osobne `DOWN/UP` dla właściwego `pointerId`. `MOVE` niesie wszystkie
  śledzone palce, bez próbek historycznych (koalescencja do jednego zdarzenia na ramkę wejścia).
- **Release all.** `ACTION_CANCEL`, `onWindowFocusChanged(false)`, `onDetachedFromWindow`, zmiana profilu
  i ukrycie kontrolek wołają `engine.releaseAll()`, a potem bezpiecznik adapterów
  (`CompositeInputSink.releaseAll()`: puszcza klawisze i przyciski myszy, wysyła zerowy stan pada).
- **Viewport.** Silnik dostaje `Viewport(widthPx, heightPx, videoRect, refW, refH)`. Prostokąt wideo:
  1. jawnie z `setVideoRect(l, t, r, b, refW, refH)` (Game przekazuje prostokąt `StreamView`), albo
  2. z `setStreamResolution(w, h)`: wycentrowany letterbox/pillarbox (`ViewportMath.fitCentered`), albo
  3. cały widok.
  Konstrukcja `Viewport`/`RectPx` jest w jednym miejscu: `EngineBridge`.
- **Haptyka.** API 29+: `VibrationEffect.createPredefined(EFFECT_TICK)` (lub `EFFECT_CLICK` dla `"click"`);
  API 26–28: `createOneShot(10 ms, DEFAULT_AMPLITUDE)` albo amplituda z `haptics.strength`, gdy wibrator
  ją obsługuje. Respektuje `haptics.enabled/onPress/onRelease` z profilu i systemowe
  `HAPTIC_FEEDBACK_ENABLED`. Joystick nie wibruje.
- **Gest „wstecz”.** Na API 29+ strefy joysticków dotykające krawędzi (pas 32 dp) trafiają do
  `setSystemGestureExclusionRects`, przycięte symetrycznie do łącznie 200 dp wysokości na krawędź
  (limit Androida; nadmiar system i tak by zignorował).
- **Pad BT.** `GamepadPresenceMonitor` (`InputManager.InputDeviceListener`, callback `onPresenceChanged`) wykrywa zewnętrzny pad (`SOURCE_GAMEPAD`/`SOURCE_JOYSTICK`,
  niewirtualny, na API 29+ `isExternal`). Przy `global.autoHideOnGamepad` kontrolki znikają, a zostają
  kontrolki systemowe i gesty (silnik dostaje profil bez stick/button/modifier). Akcja `toggle_overlay`
  robi to samo ręcznie.
- **Debug.** `debugEnabled = true` pokazuje: liczbę i id aktywnych wskaźników (z kółkami w miejscu palców),
  ostatnie flagi pada (hex), opóźnienie `event.eventTime → send` (ostatnie i p95 z 256 próbek),
  liczbę naruszeń polityki (`engine.violations.size`). Opóźnienie mierzymy w `SendListener`
  wołanym synchronicznie z adapterów, w bazie `SystemClock.uptimeMillis()` (ta sama co `MotionEvent`).
- **Impuls czasu.** Gdy dotyka choć jeden palec, overlay raz na klatkę (`postOnAnimation`) woła
  `InputEngine.onTick(now)` (założenie: metoda spoza kontraktu, opisana w ActionPolicy ENGINE). Silnik
  dopuszcza w tym cyklu tylko rozpoznanie long-press, zwolnienia i wygaszanie kamery; czas sam z siebie
  nie wciska niczego nowego. Bez palców nie ma impulsów.
- **Mysz i rysik.** `ACTION_DOWN` z narzędziem innym niż palec zwraca `false`, więc gest trafia do
  widoku pod spodem (upstreamowa obsługa myszy fizycznej zostaje).

## Jak podpiąć (w `Game`)

Jedyny punkt wejścia to `ThumbcastOverlayFactory.create(...)`:

```java
ThumbcastOverlay overlay = ThumbcastOverlayFactory.create(
        this,                                   // Context
        profile,                                // app.thumbcast.model.Profile (np. ThumbcastProfiles.loadOrNull)
        new HostConnectionImpl(conn),           // fasada NvConnection (patches/)
        new HostPadReporterImpl(controllerHandler), // fasada ControllerHandler (patches/)
        action -> onThumbcastAppAction(action), // "menu" | "soft_keyboard" | "toggle_overlay"
        new ThumbcastKeyTranslator(keyboardTranslator)); // opcjonalnie; domyślnie WindowsVkKeyTranslator
((FrameLayout) streamView.getParent()).addView(overlay,
        new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)); // ostatnie dziecko = na wierzchu
overlay.setStreamResolution(width, height);  // albo setVideoRect(...) z prostokąta StreamView
```

Pełny opis zmian w upstreamie: `patches/Game.java.patch.md`, kolejność kroków: `patches/APPLY.md`.

Publiczne API overlaya: `setProfile(Profile)`, `setVideoRect(...)`, `setStreamResolution(w, h)`,
`setControlsHidden(Boolean)` / `toggleControlsHidden()` / `controlsHidden` (`isControlsHidden()`),
`setProfileTransforms(List<ProfileStep>)`, `releaseAll()`, `debugEnabled`.
Wszystko na wątku UI.

## Menu w grze: klawiatura i pad (`menu/MenuInputRouter`, 12.11.2026)

`MenuInputRouter` decyduje, co zrobić z klawiszem przy menu w grze (`PASS` = zwykła ścieżka `Game` do hosta,
`CONSUME` = zjedzone, `SYSTEM` = głośność i zasilanie do systemu) i tłumaczy klawisze oraz osie pada na
`MenuCommand` (UP/DOWN/LEFT/RIGHT/ACTIVATE/BACK). `MenuFocus.move` liczy zaznaczenie wiersza (od 12.11.2026
z zawijaniem: z ostatniego w dół na pierwszy i odwrotnie), `MenuFocus.step` wartość segmentów (bez zawijania).
Używa go `ThumbcastSession` w `:app`; widok menu (`ThumbcastMenuView`) dostaje tylko polecenia. Router nie ma
timerów: krzyżak i gałka dają jeden krok na wychylenie. `heldVertical()` mówi, czy kierunek pionowy jest nadal
trzymany; sesja powtarza wtedy krok (400 ms, potem co 120 ms), żeby przewinąć długą listę. To tylko nawigacja
w menu: przy otwartym menu nic nie idzie do hosta (FAIR_PLAY). Klawisze mają powtórzenia systemowe.

Strony menu: `menu/MenuPage` (MAIN, PROFILES, END_SESSION; `back()` = z podstrony na główną, z głównej
zamknięcie). END_SESSION to potwierdzenie „Zamknij grę na PC” (dawniej „Zakończ sesję”, `MenuPageTest`).

Wygląd menu (przebudowa UI 12.11.2026, `docs/ui-audit.md` G1–G17, opis w `patches/ui-p4.patch.md`): panel przy
krawędzi END z rogami 28 dp od strony wideo, wjazd 150 ms, kolory z `thumbcast_colors.xml`; nagłówek (nazwa gry
i zamknięcie, na podstronie strzałka wstecz), „Wróć do gry” jako wypełniony przycisk z domyślnym zaznaczeniem,
kafle Klawiatura i Ukryj/Pokaż przyciski, sekcje Sterowanie (Profil sterowania, Gesty myszy, Rozmiar przycisków,
Chowaj przyciski, gdy gram padem), Obraz (Statystyki: Wył. / Podstawowe / Szczegółowe) i na dole Wyjście
(Wstrzymaj i wyjdź, Zamknij grę na PC → potwierdzenie). Stopka „A Wybierz · B Wstecz” po pierwszym poleceniu
z pada lub klawiatury. Lewo/prawo zmienia wartość (stepper, segmenty, przełącznik) albo przechodzi między kaflami.

## Nakładka statystyk (`stats/`, 12.11.2026)

Dane liczbowe z upstreamowych liczników (`MediaCodecDecoderRenderer`, `VideoStats`, `MoonBridge.getEstimatedRttInfo`),
bez własnych pomiarów. Blok THUMBCAST w rendererze co ok. 1 s (przy zmianie okna statystyk) woła
`StreamStatsListener.onStreamStats(StreamStats)`, tylko gdy słuchacz jest ustawiony (nakładka włączona).
- `StreamStats.fromCounters`: suma dwóch okien (ok. 2 s, jak upstream), średnie na odebraną klatkę, RTT z górnych
  32 bitów (`-1` = brak), czas hosta w 0,1 ms (0 klatek z czasem = brak danych).
- `latencyEstimateMs` = **RTT sieci + średni czas hosta + średni czas telefonu**, zaokrąglone:
  RTT (ENet, połączenie sterujące): wejście idzie do PC, obraz wraca do telefonu, po jednej drodze w każdą stronę;
  czas hosta: przechwycenie → zakodowana klatka, podawany przez Sunshine (brak = 0);
  czas telefonu: pierwszy pakiet klatki odebrany → klatka zdekodowana (upstreamowy „end-to-end client latency”,
  zawiera dekodowanie). Nie wlicza renderowania gry, czekania na przechwycenie, kolejki wyświetlania, odświeżania
  ekranu i próbkowania dotyku, więc prawdziwe opóźnienie jest większe. Etykieta (od 12.11.2026): „X ms”.
  `null` (wyświetlane „–”), gdy RTT albo czas telefonu nieznany.
- `StatsFormatter` + `StatsLabels` (wzorce z zasobów EN/PL): `basic` = „60 FPS · 24 ms”, bez opóźnienia „60 FPS”
  (FPS = klatki wyświetlone; od 12.11.2026, G14), `advanced` = ta linia i: rozdzielczość, kodek, FPS strumienia; dekoder; odebrane
  i wyświetlone FPS; utrata klatek w sieci; RTT i wariancja; czas hosta min/maks/śr.; dekodowanie i czas telefonu;
  rozbicie opóźnienia na składniki.
- `StatsToggles`/`StatsMode`: zapis to dwie flagi, „Statystyki” (klucz upstreamu `checkbox_enable_perf_overlay`,
  ten sam co pole w ustawieniach) i „Szczegółowe statystyki” (`checkbox_thumbcast_perf_advanced`, domyślnie wył.).
  Menu w grze ma od 12.11.2026 jeden wiersz z segmentami Wył. / Podstawowe / Szczegółowe: `withMode(mode)`
  (Wył. zostawia flagę szczegółów, więc ponowne włączenie w ustawieniach wraca do poprzedniego trybu).
  `toggleEnabled`/`toggleAdvanced` zostały dla zgodności.
- `StatsPlacement`: prawy górny róg obszaru bezpiecznego (wycięcie, paski). Kandydaci: x przy prawej krawędzi
  albo tuż na lewo od kontrolki, y przy górze albo tuż pod kontrolką; wygrywa wolny kandydat najbliżej rogu
  (przesunięcie w dół liczy się podwójnie). Nic wolnego: róg mimo kolizji. Kontrolki z
  `ThumbcastOverlay.controlBounds()` (profil efektywny: skala, ukrycie przy padzie).
- Widok i podpięcie są w `:app` (`ThumbcastStatsView`, `ThumbcastSession`): widok pod overlayem kontrolek, bez dotyku
  i fokusu, 12 sp, tło `thumbcast_scrim`. Testy: `StreamStatsTest`, `StatsTogglesTest`, `StatsPlacementTest`.

## Fasady (dlaczego nie wołamy upstreamu wprost)

Kod upstreamu nie jest w tym repozytorium, więc moduł kompiluje się względem cienkich interfejsów:
- `HostConnection`: `sendKeyboardInput`, `sendMouseMove`, `sendMousePosition`, `sendMouseButtonDown/Up`,
  `sendMouseHighResScroll`, `sendMouseHighResHScroll` (od 11.11.2026; sygnatury zweryfikowane z `NvConnection` w upstreamie v12.2, commit b48494cb96bf);
- `HostPadReporter`: `reportOscState(int, short, short, short, short, byte, byte)`;
- `KeyTranslator`: keycode Androida → kod hosta (w aplikacji przez upstreamowy `KeyboardTranslator`).

Implementacje są w `patches/app/src/main/java/com/limelight/thumbcast/`. `HostPadReporterImpl`
powtarza ten sam stan po 25/50/75 ms, tak jak upstreamowy `VirtualController.sendControllerInputContext()`
(zweryfikowane na kodzie v12.2; sam `reportOscState` nie retransmituje). To jedyny
„czasowy” element i wysyła wyłącznie identyczny stan.

## Tabela klawiszy

`AndroidKeyCodes` mapuje cały słownik `KeyCodes` modelu na `KeyEvent.KEYCODE_*`: `A`–`Z`, `0`–`9`,
`F1`–`F12`, `SHIFT`/`CTRL`/`ALT` (lewe), `SPACE`, `ESC`, `TAB`, `ENTER`, `-`, `=`, strzałki.
`KeyboardSink` śledzi wciśnięte klawisze (drugi DOWN bez UP nie jest wysyłany) i bajt modyfikatorów
(SHIFT/CTRL/ALT trzymane na overlayu), który jest przekazywany w `sendKeyboardInput`.

## Testy

- **JVM (`src/test`, JUnit 4):** `MotionTranslatorTest` (DOWN/UP per wskaźnik, limit 10, CANCEL,
  zgubiony UP, narzędzia inne niż palec, losowa właściwość „każdy DOWN ma jeden UP”), `SinksTest`
  (adaptery na fałszywych fasadach, pokrycie `KeyCodes.ALL`), `GeometryTest` (letterbox, limit 200 dp,
  układ i hit test, percentyle), `VideoGeometryTest`, `EffectiveProfileTest`, `GamepadPresenceMonitorTest`
  (reguła „fizyczny pad”, callback tylko przy zmianie), `OverlaySnapshotTest` (migawka z prawdziwym silnikiem:
  wciśnięcie w strefie aktywacji, slide-to-press, LATCH, promień i gałka z silnika, haptyka z przejść),
  `MenuInputRouterTest` (menu w grze: które klawisze menu zjada, które idą do systemu albo do hosta, puszczenia
  po zamknięciu menu, krzyżak i gałka z histerezą, duplikaty hat + D-pad, zaznaczenie wiersza z zawijaniem,
  segmenty bez zawijania, trzymany kierunek), `MenuPageTest`, `SystemTouchTargetsTest` (obszar dotyku przycisków
  systemowych: min. 48 dp, bez nachodzenia, presety), `StreamStatsTest`, `StatsTogglesTest` (także segmenty
  `withMode`), `StatsPlacementTest` (nakładka statystyk). Testy nie dotykają klas Androida w czasie wykonania (stałe
  `KeyEvent.KEYCODE_*` są wklejane przez kompilator), więc Robolectric nie jest potrzebny. Jeśli kompilator
  jednak nie wklei stałych (np. zmiana w android.jar), `unitTests.isReturnDefaultValues` nie wystarczy:
  wtedy dodać Robolectric (`libs.robolectric`) do `SinksTest`.
- **Instrumentacja (`src/androidTest`):** szkic `MultiTouchInstrumentedTest`: dwa palce (joystick + A),
  `POINTER_UP` puszcza tylko A, `CANCEL` zeruje stan. Wymaga aliasów `androidx.test` w version catalog
  (zakomentowane w `build.gradle.kts`).

## Ograniczenia

- **Nie kompilowane.** Importy i API Androida sprawdzone tylko „na oko” i z dokumentacji z pamięci.
- **Stałe protokołu** (`HostProtocol`: kierunek klawisza, bity modyfikatorów, numery przycisków myszy,
  prefiks `0x80` kodu VK) zweryfikowane z upstreamowymi `KeyboardPacket`, `MouseButtonPacket`,
  `KeyboardTranslator` (v12.2, b48494cb96bf). Kompilacja nadal niesprawdzona.
- **Geometria rysunku** (`ControlLayout`) zakłada, że `rect` jest względem całego overlaya. Stan wciśnięć
  idzie z silnika, ale prostokąty grafiki nie: gdy overlay zacznie podawać `safeArea`, `ControlLayout` musi
  liczyć `rect` względem tego samego prostokąta (inaczej grafika rozjedzie się ze strefą trafienia).
- **Leworęczność** nie jest stosowana w overlayu: profil ma już być w odpowiedniej orientacji
  (`HandednessTransform` w modelu).
- **`WindowInsets`/wycięcie aparatu:** overlay nie podaje `safeArea` (pole opcjonalne silnika). Do zrobienia
  w E2 razem z trybem immersive.
- **Trzy palce → klawiatura** z upstreamu nie działa w trybie Thumbcast (palce nie trafiają do upstreamu);
  zastępuje to kontrolka `system` `soft_keyboard`.
- **Wydajność rysowania:** `onDraw` nie alokuje w pętli kontrolek, ale nakładka debug buduje napisy co klatkę
  (tylko w trybie debug).
