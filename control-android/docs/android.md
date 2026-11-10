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
  Etykieta to `label` z profilu albo skrót wyjścia (`A`, `LT`, `M`, `☰`). Przezroczystość: `global.opacity`.
- **Stan wizualny ≠ stan wyjścia.** Overlay sam robi prosty hit test tylko do rysowania „wciśnięcia”
  i haptyki. O tym, co trafia do hosta, decyduje silnik. Jeśli silnik odrzuci dotyk (np. polityka),
  przycisk może się podświetlić bez wysyłki: to błąd kosmetyczny, nigdy naruszenie polityki.
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
- **Pad BT.** `InputManager.InputDeviceListener` wykrywa zewnętrzny pad (`SOURCE_GAMEPAD`/`SOURCE_JOYSTICK`,
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
`setControlsHidden(Boolean)` / `toggleControlsHidden()`, `releaseAll()`, `debugEnabled`.
Wszystko na wątku UI.

## Fasady (dlaczego nie wołamy upstreamu wprost)

Kod upstreamu nie jest w tym repozytorium, więc moduł kompiluje się względem cienkich interfejsów:
- `HostConnection`: `sendKeyboardInput`, `sendMouseMove`, `sendMousePosition`, `sendMouseButtonDown/Up`,
  `sendMouseHighResScroll` (sygnatury zweryfikowane z `NvConnection` w upstreamie v12.2, commit b48494cb96bf);
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
  układ i hit test, percentyle). Testy nie dotykają klas Androida w czasie wykonania (stałe
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
- **Hit test wizualny** zakłada, że `rect` jest względem całego overlaya i że późniejsza kontrolka jest
  na wierzchu. Jeśli silnik używa `safeArea` z `Viewport` albo innego z-order, podświetlenie może się
  rozjechać z faktycznym wyjściem (tylko wizualnie).
- **Leworęczność** nie jest stosowana w overlayu: profil ma już być w odpowiedniej orientacji
  (`HandednessTransform` w modelu).
- **`WindowInsets`/wycięcie aparatu:** overlay nie podaje `safeArea` (pole opcjonalne silnika). Do zrobienia
  w E2 razem z trybem immersive.
- **Trzy palce → klawiatura** z upstreamu nie działa w trybie Thumbcast (palce nie trafiają do upstreamu);
  zastępuje to kontrolka `system` `soft_keyboard`.
- **Wydajność rysowania:** `onDraw` nie alokuje w pętli kontrolek, ale nakładka debug buduje napisy co klatkę
  (tylko w trybie debug).
