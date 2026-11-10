# Patch: `app/src/main/java/com/limelight/Game.java` (upstream moonlight-android v12.2)

Zweryfikowane na kodzie upstreamu: tag **v12.2 = commit `b48494cb96bff23d8886c4775cc4f39a1075495d`**
(skrót `b48494cb96bf`, 12.09.2026, „Version 12.2”; w dniu sprawdzenia, 10.11.2026, to także HEAD `master`).
Źródło: `raw.githubusercontent.com` i `api.github.com` (tagi, commity).

To nadal **opis krok po kroku, a nie gotowy `git diff`**: narzędzie, którym czytałem upstream, podaje
tylko krótkie cytaty (bez numerów linii i bez gwarancji białych znaków), więc nie da się uczciwie
wygenerować diffu, który przejdzie `git apply`. Wszystkie „kotwice” poniżej (linie do znalezienia
w pliku) są **dosłownymi cytatami z v12.2**; fragmenty `+` dodajemy. Po nałożeniu zrób własny diff:
`git diff v12.2 -- app/src/main/java/com/limelight/Game.java > patches/Game.java.diff`.

Zasada: zmiany w `Game.java` w blokach `// THUMBCAST:` … `// THUMBCAST END` (łatwy `grep -n THUMBCAST`).

## Co sprawdzono na upstreamie (i jest zgodne z założeniami)

- `Game extends Activity` (nie AppCompat), pola prywatne: `TouchContext[] touchContextMap = new TouchContext[2]`,
  `ControllerHandler controllerHandler`, `KeyboardTranslator keyboardTranslator`,
  `VirtualController virtualController`, `PreferenceConfiguration prefConfig`, `NvConnection conn`,
  `StreamView streamView`.
- `backgroundTouchView` to **zmienna lokalna** w `onCreate` (`View backgroundTouchView = findViewById(R.id.backgroundTouchView);`
  + `backgroundTouchView.setOnTouchListener(this);`), nie pole. Nasz kod go nie potrzebuje.
- Kolejność w `onCreate`: `setPreferredOrientationForCurrentDisplay()` → `streamView = findViewById(R.id.surfaceView)`
  → … → `conn = new NvConnection(getApplicationContext(), ...)` → `controllerHandler = new ControllerHandler(this, conn, this, prefConfig);`
  → `keyboardTranslator = new KeyboardTranslator();` → pętla `touchContextMap` → blok `if (prefConfig.onscreenController) {` (VirtualController).
  Wszystkie potrzebne obiekty (`conn`, `controllerHandler`, `keyboardTranslator`, `streamView`) istnieją w miejscu bloku VirtualController.
- Rodzic `StreamView`: `activity_game.xml` ma korzeń `<merge>`, więc rodzicem jest FrameLayout zawartości aktywności;
  upstream sam robi `(FrameLayout)streamView.getParent()` dla `VirtualController` i tak samo robimy my.
  `StreamView` ma `match_parent` + `layout_gravity="center"`, a `StreamView.onMeasure` przy ustawionym
  `setDesiredAspectRatio(...)` (wołane w `prepareDisplayForRendering()`, nie w `onCreate`) dopasowuje rozmiar widoku
  do proporcji: **rozmiar `StreamView` po layoucie = prostokąt wideo**.
- `Game` dostaje dotyk przez `backgroundTouchView` (`onTouch` → `view.requestUnbufferedDispatch(event)` na `ACTION_DOWN`,
  potem `return handleMotionEvent(view, event);`). `StreamView` nie obsługuje dotyku. Overlay dodany jako ostatnie dziecko
  rodzica leży nad nimi i dostaje dotyk pierwszy.
- `onWindowFocusChanged` w `Game` zeruje `modifierFlags` i woła `inputCaptureProvider.onWindowFocusChanged(hasFocus)`;
  nie dotyka kontrolek ekranowych, więc nic tu nie dodajemy (overlay sam woła `releaseAll()` w swoim `onWindowFocusChanged(false)`).
- Fokus klawiatury: `StreamView` ma `focusable`/`focusableInTouchMode` i listener klawiszy (`setOnKeyListener(this)`,
  `setInputCallbacks(this)`). Overlay ma `isFocusable = false`, więc fokusu nie kradnie.

## Różnice względem poprzedniej (spekulatywnej) wersji tego pliku

1. Blokadę orientacji robi `setPreferredOrientationForCurrentDisplay()` (nie blok VirtualController) i zależy ona od
   `prefConfig.onscreenController`. Rozwiązanie: wymuszamy `onscreenController = true` w `PreferenceConfiguration`
   (patrz `preferences.patch.md`, pkt 4), więc nic nie trzeba kopiować.
2. **Maska pada (krytyczne).** `ControllerHandler.getAttachedControllerMask()` ustawia bit 0 tylko przy
   `readPreferences(context).onscreenController`, a `getActiveControllerMask()` przy `multiController` dodaje bit 0 tylko
   przy `prefConfig.onscreenController`. W `Game.onCreate` jest też `if (prefConfig.onscreenController) { // If we're using OSC, always set at least gamepad 1.` →
   `gamepadMask |= 1`. Bez tej flagi pakiety pada nr 0 (`reportOscState`) trafiałyby na hosta z maską bez bitu 0.
   Wymuszenie flagi w `PreferenceConfiguration.readPreferences` (jedno miejsce) załatwia wszystkie trzy.
3. Blok VirtualController owijamy **warunkiem w jednej linii**, a logikę Thumbcast wynosimy do metody
   `createThumbcastOverlay()`. Przy błędzie profilu metoda zwraca `false` i upstream normalnie tworzy `VirtualController`
   (fallback do legacy działa „za darmo”, bo `onscreenController` jest wymuszone).
4. Listener layoutu używa parametrów `left/top/right/bottom` (współrzędne w rodzicu), a nie `getLocationOnScreen`.
5. Sprzątanie idzie do `stopConnection()` (przed `conn.stop()`) i `onDestroy()`; wcześniej mówiłem ogólnie „onDestroy”.
6. Dodano obsługę PiP (`onConfigurationChanged`), którą upstream robi dla `virtualController`.
7. `"menu"`: w v12.2 nie znalazłem w `Game` menu kontekstowego, więc akcja jest na razie no-op z logiem.

---

## 1. Importy

`Toast`, `FrameLayout`, `MotionEvent`, `PreferenceConfiguration`, `KeyboardTranslator` są już zaimportowane w v12.2.
Dodajemy po istniejących importach `com.limelight.*`:

```java
+ // THUMBCAST:
+ import com.limelight.thumbcast.HostConnectionImpl;
+ import com.limelight.thumbcast.HostPadReporterImpl;
+ import com.limelight.thumbcast.ThumbcastKeyTranslator;
+ import app.thumbcast.android.ThumbcastOverlayFactory;
+ import app.thumbcast.android.ThumbcastProfiles;
+ import app.thumbcast.android.overlay.ThumbcastOverlay;
+ import app.thumbcast.model.Profile;
+ // THUMBCAST END
```

## 2. Pola klasy `Game`

Zakotwiczenie: linia `private VirtualController virtualController;`. Tuż po niej:

```java
+ // THUMBCAST:
+ private ThumbcastOverlay thumbcastOverlay;          // null = tryb legacy
+ private HostPadReporterImpl thumbcastPadReporter;
+ // THUMBCAST END
```

## 3. `Game.onCreate()`: utworzenie overlaya

Kotwica (dosłownie z v12.2, po pętli `touchContextMap`):

```java
        if (prefConfig.onscreenController) {
            // create virtual onscreen controller
            virtualController = new VirtualController(controllerHandler,
                    (FrameLayout)streamView.getParent(),
                    this);
            virtualController.refreshLayout();
            virtualController.show();
        }
```

(Blok złożony z pojedynczych dosłownych linii zwróconych przez odczyt: `if (prefConfig.onscreenController) {`,
`// create virtual onscreen controller`, `virtualController = new VirtualController(controllerHandler,`,
`(FrameLayout)streamView.getParent(),`, `this);`, `virtualController.refreshLayout();`, `virtualController.show();`.
Treść jest pewna, wcięcia i łamanie linii sprawdź w pliku.) Zmiana to **jedna zmieniona linia warunku i jedna wstawiona linia przed**:

```java
+ // THUMBCAST:
+ boolean thumbcastActive = createThumbcastOverlay();
+ // THUMBCAST END
~ if (prefConfig.onscreenController && !thumbcastActive) {
      // create virtual onscreen controller
      ... (reszta bloku bez zmian)
  }
```

Uwagi:
- Wszystkie obiekty użyte w `createThumbcastOverlay()` (`conn`, `controllerHandler`, `keyboardTranslator`, `streamView`) są już
  zainicjalizowane w tym miejscu (kolejność wyżej).
- Blok `if (prefConfig.usbDriver) { // Start the USB driver` następuje po bloku VirtualController i zostaje bez zmian.

Nowa metoda (wstaw obok `toggleKeyboard()` albo na końcu klasy):

```java
+ // THUMBCAST:
+ private boolean createThumbcastOverlay() {
+     if (prefConfig.controlLayer != PreferenceConfiguration.ControlLayer.THUMBCAST) {
+         return false;
+     }
+     Profile profile = ThumbcastProfiles.loadOrNull(this, prefConfig.thumbcastProfileFile);
+     if (profile == null) {
+         // Błąd odczytu/walidacji: komunikat i fallback do VirtualController (bez crasha w trakcie sesji).
+         Toast.makeText(this, getString(R.string.thumbcast_profile_error,
+                 String.valueOf(ThumbcastProfiles.getLastError())), Toast.LENGTH_LONG).show();
+         return false;
+     }
+     thumbcastPadReporter = new HostPadReporterImpl(controllerHandler);
+     thumbcastOverlay = ThumbcastOverlayFactory.create(
+             this,
+             profile,
+             new HostConnectionImpl(conn),
+             thumbcastPadReporter,
+             action -> onThumbcastAppAction(action),
+             new ThumbcastKeyTranslator(keyboardTranslator));
+     FrameLayout parent = (FrameLayout) streamView.getParent();
+     // Ostatnie dziecko FrameLayout = rysowane na wierzchu i pierwsze dostaje dotyk.
+     parent.addView(thumbcastOverlay, new FrameLayout.LayoutParams(
+             FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
+     thumbcastOverlay.setDebugEnabled(prefConfig.thumbcastDebugOverlay);
+     thumbcastOverlay.setStreamResolution(prefConfig.width, prefConfig.height);
+     // Prostokąt wideo = rozmiar StreamView po layoucie (StreamView.onMeasure dopasowuje go do
+     // proporcji, a layout_gravity=center go wyśrodkowuje). Współrzędne l/t/r/b są w układzie rodzica,
+     // czyli tym samym co overlay (MATCH_PARENT, bez paddingu).
+     streamView.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) ->
+             thumbcastOverlay.setVideoRect(l, t, r, b, prefConfig.width, prefConfig.height));
+     return true;
+ }
+ // THUMBCAST END
```

Rzutowanie `(FrameLayout) streamView.getParent()` jest identyczne z tym, które upstream robi dla `VirtualController`.
`prefConfig.width/height` to publiczne pola `PreferenceConfiguration` (`public int width, height, fps;`) = rozdzielczość strumienia.
Upstreamowy `AbsoluteTouchContext` podaje do `sendMousePosition` rozmiar widoku (`targetView.getWidth()/getHeight()`) jako
odniesienie; rozdzielczość strumienia jest równoważna (host skaluje względem odniesienia), a `StreamView` = prostokąt wideo.
`ThumbcastProfiles.getLastError()` to `@JvmStatic` getter prywatnego settera; z Javy działa jak podano.

## 4. Routing dotyku

W v12.2 palce trafiają do gałęzi `else` w `handleMotionEvent(View view, MotionEvent event)` (mysz, rysik, gumka i touchpad
mają swoje gałęzie wcześniej; w gałęzi palca brak jawnego `TOOL_TYPE_FINGER`). Kotwica (dosłownie):

```java
            else
            {
                if (virtualController != null &&
                        (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons ||
                         virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)) {
                    // Ignore presses when the virtual controller is being configured
                    return true;
                }
```

Wstaw **przed** tym `if`em (jako pierwsza instrukcja po `{`):

```java
+ // THUMBCAST: overlay obsługuje palce w całości (do 10 wskaźników); touchContextMap pomijamy.
+ if (thumbcastOverlay != null) {
+     return true;
+ }
+ // THUMBCAST END
```

To zabezpieczenie jest zwykle martwe (overlay konsumuje gest palca, zanim dotrze do `backgroundTouchView`), ale
`handleMotionEvent` jest też wołane z `onGenericMotionEvent` (`handleMotionEvent(null, event)`), więc zostaje jako pas bezpieczeństwa.
Gałąź joysticka (`controllerHandler.handleMotionEvent(event)`) oraz klawiatura/mysz fizyczna bez zmian.

- Stuknięcie trzema palcami (`threeFingerDownTime` → klawiatura ekranowa) w trybie Thumbcast **nie działa** (palce nie dochodzą
  do upstreamu); zastępuje je kontrolka `system` z `{"app": "soft_keyboard"}`.
- W komentarzu upstreamu natywny `trySendTouchEvent` jest zakomentowany (`TODO: Re-enable native touch ...`); `NvConnection` ma
  już `sendTouchEvent`/`sendPenEvent`, ale nie używamy ich (zasada 1 dotyk = 1 wejście z mapowaniem przez profil).

## 5. Akcje aplikacji

```java
+ // THUMBCAST:
+ private void onThumbcastAppAction(String action) {
+     switch (action) {
+         case "soft_keyboard":
+             toggleKeyboard();   // istniejąca metoda Game (toggleSoftInput(0, 0)), ta sama co przy 3 palcach
+             break;
+         case "menu":
+             // v12.2 nie ma w Game menu kontekstowego do otwarcia [niezweryfikowane do końca]; E2: bottom sheet.
+             LimeLog.info("Thumbcast: menu requested");
+             break;
+         case "toggle_overlay":
+             // Overlay już przełączył widoczność kontrolek (fabryka).
+             break;
+         default:
+             break;
+     }
+ }
+ // THUMBCAST END
```

(`LimeLog` jest używany w `Game`/`ControllerHandler` upstreamu; jeśli import `com.limelight.LimeLog` nie jest obecny, dodaj lub usuń linię.)

## 6. Sprzątanie

`stopConnection()` (wołane z `onStop()`; wewnątrz `if (connecting || connected) {`, po wyzerowaniu flag, przed
`controllerHandler.stop()` i uruchomieniem wątku z `conn.stop()`). Dodaj na początku tego bloku:

```java
+ // THUMBCAST: zerowy stan na hosta PRZED zamknięciem połączenia.
+ if (thumbcastOverlay != null) {
+     thumbcastOverlay.releaseAll();
+ }
+ // THUMBCAST END
```

(Ciała `stopConnection()` nie udało mi się zacytować w całości; opis: sprawdza `connecting || connected`, zeruje flagi, stopuje
`controllerHandler`, woła `UiHelper.notifyStreamEnded(this)`, `conn.stop()` w osobnym wątku. Dopasuj miejsce ręcznie.)

W `onStop()` upstream robi `virtualController.hide()` → `stopConnection()` → `finish()`. Dla overlaya wystarczy wywołanie
w `stopConnection()`; dodatkowo w `onDestroy()` (po `super.onDestroy();`, przed `if (controllerHandler != null) {`):

```java
+ // THUMBCAST:
+ if (thumbcastPadReporter != null) {
+     thumbcastPadReporter.cancelPending();
+ }
+ // THUMBCAST END
```

## 7. Picture-in-Picture

W `onConfigurationChanged` upstream chowa i pokazuje VirtualController. Kotwice (dosłownie):
`if (isInPictureInPictureMode()) {` → `isHidingOverlays = true;` → `if (virtualController != null) { virtualController.hide();`
oraz w `else` → `isHidingOverlays = false;` → `virtualController.show();`. Dodaj obok:

```java
  // w gałęzi PiP, po bloku virtualController.hide():
+ // THUMBCAST:
+ if (thumbcastOverlay != null) {
+     thumbcastOverlay.releaseAll();
+     thumbcastOverlay.setVisibility(View.GONE);
+ }
+ // THUMBCAST END

  // w gałęzi wyjścia z PiP, po bloku virtualController.show():
+ // THUMBCAST:
+ if (thumbcastOverlay != null) {
+     thumbcastOverlay.setVisibility(View.VISIBLE);
+ }
+ // THUMBCAST END
```

## 8. Rumble i pad BT

- Rumble: `ControllerHandler.handleRumble` wibruje telefonem warunkiem
  `!foundMatchingDevice && prefConfig.onscreenController && !prefConfig.onlyL3R3 && prefConfig.vibrateOsc`.
  Z wymuszonym `onscreenController = true` (pkt 2 wyżej) rumble działa w trybie Thumbcast; `vibrateOsc` użytkownik włącza
  w ustawieniach (preferencja `checkbox_vibrate_osc`). Dopóki `onlyL3R3` jest włączone, wibracja jest wyłączona.
- Pad BT: `ControllerHandler` implementuje `InputManager.InputDeviceListener`; overlay rejestruje własny listener niezależnie,
  więc nic nie dodajemy w `Game`.
- Połączenie z padem fizycznym: `sendControllerInputPacket` łączy `defaultContext` (nasz `reportOscState`) z padami
  fizycznymi tego samego `controllerNumber` przez OR przycisków i `maxByMagnitude` osi, więc nasz stan i fizyczny pad 0 się sumują.
