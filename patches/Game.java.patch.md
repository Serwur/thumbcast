# Patch: `app/src/main/java/com/limelight/Game.java` (upstream moonlight-android v12.2)

To jest **opis** zmian, a nie gotowy `git diff`: kodu upstreamu nie było w tej sesji.
Kontekst (nazwy pól, metod, kolejność w `onCreate()`) pochodzi z planu, sekcja 2.2 i 2.3 [Z:6b],
a miejsca oznaczone **[?]** trzeba dopasować do faktycznego kodu podczas nakładania.
Fragmenty oznaczone `+` dodajemy, `~` zmieniamy. Kodu upstreamu nie kopiujemy.

Zasada: zmiany w `Game.java` mają być **małe i zgrupowane** w blokach oznaczonych komentarzem
`// THUMBCAST:` (początek) i `// THUMBCAST END` (koniec), żeby przy rebase dało się je
znaleźć `grep -n THUMBCAST`.

---

## 1. Importy

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

Obok istniejących pól `virtualController`, `controllerHandler`, `conn`, `streamView`,
`keyboardTranslator` [?: nazwy pól z planu 2.2; `keyboardTranslator` może się nazywać inaczej]:

```java
+ // THUMBCAST:
+ private ThumbcastOverlay thumbcastOverlay;          // null = tryb legacy
+ private HostPadReporterImpl thumbcastPadReporter;
+ // THUMBCAST END
```

## 3. `Game.onCreate()`: utworzenie overlaya

Upstream (plan 2.2): gdy `prefConfig.onscreenController` jest włączone, `onCreate()` tworzy
`new VirtualController(controllerHandler, (FrameLayout) streamView.getParent(), this)`, potem woła
`refreshLayout()` i `show()`; z włączonym OSC orientacja jest blokowana na landscape.

Zmiana: **wokół** tego bloku dodajemy przełącznik `control_layer`. Blok legacy zostaje bez zmian
w gałęzi `else`, żeby rebase miał minimalny konflikt.

```java
+ // THUMBCAST:
+ boolean thumbcastActive = false;
+ if (prefConfig.controlLayer == PreferenceConfiguration.ControlLayer.THUMBCAST) {
+     Profile profile = ThumbcastProfiles.loadOrNull(this, prefConfig.thumbcastProfileFile);
+     if (profile == null) {
+         // Błąd walidacji/odczytu: komunikat i powrót do legacy (bez crasha w trakcie sesji).
+         Toast.makeText(this, getString(R.string.thumbcast_profile_error,
+                 String.valueOf(ThumbcastProfiles.getLastError())), Toast.LENGTH_LONG).show();
+     } else {
+         thumbcastPadReporter = new HostPadReporterImpl(controllerHandler);
+         thumbcastOverlay = ThumbcastOverlayFactory.create(
+                 this,
+                 profile,
+                 new HostConnectionImpl(conn),
+                 thumbcastPadReporter,
+                 action -> onThumbcastAppAction(action),
+                 new ThumbcastKeyTranslator(keyboardTranslator));
+         FrameLayout parent = (FrameLayout) streamView.getParent();
+         // Ostatnie dziecko FrameLayout = rysowane na wierzchu i jako pierwsze dostaje dotyk.
+         parent.addView(thumbcastOverlay, new FrameLayout.LayoutParams(
+                 FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
+         thumbcastOverlay.setDebugEnabled(prefConfig.thumbcastDebugOverlay);
+         thumbcastOverlay.setStreamResolution(prefConfig.width, prefConfig.height);
+         // Jak przy OSC: overlay zakłada landscape [?: skopiować wywołanie blokady orientacji z gałęzi OSC].
+         thumbcastActive = true;
+     }
+ }
+ if (!thumbcastActive) {
+ // THUMBCAST END
      // ... istniejący blok upstreamu: if (prefConfig.onscreenController) { new VirtualController(...); ... }
+ // THUMBCAST:
+ }
+ // THUMBCAST END
```

Uwagi:
- `conn` (NvConnection) i `controllerHandler` muszą już istnieć w tym miejscu `onCreate()`.
  Wg planu VirtualController dostaje `controllerHandler` w tym samym miejscu, więc ten warunek
  jest spełniony dla `controllerHandler`; dla `conn` sprawdzić kolejność [?]. Jeśli `conn` powstaje
  później, przenieść cały blok THUMBCAST za jego utworzenie (nadal przed `streamView` dostaje surface).
- `ThumbcastProfiles.getLastError()`: w Kotlinie to `@JvmStatic var lastError` z prywatnym setterem,
  więc z Javy wołamy `ThumbcastProfiles.getLastError()`.
- `setDebugEnabled(...)` to setter właściwości `debugEnabled`.
- `prefConfig.width/height` = rozdzielczość strumienia [?: nazwy pól PreferenceConfiguration].

## 4. Prostokąt wideo (letterbox)

Plan 2.3 pkt 5: proporcje wideo ustala `StreamView.setDesiredAspectRatio(...)`. Najprostsze
i odporne na zmiany upstreamu: po każdym layoucie `streamView` przekazać jego prostokąt
w układzie overlaya. Dodać po utworzeniu overlaya:

```java
+ // THUMBCAST:
+ if (thumbcastOverlay != null) {
+     streamView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
+         int[] sv = new int[2];
+         int[] ov = new int[2];
+         v.getLocationOnScreen(sv);
+         thumbcastOverlay.getLocationOnScreen(ov);
+         int left = sv[0] - ov[0];
+         int top = sv[1] - ov[1];
+         // ref = rozdzielczość strumienia, jak w AbsoluteTouchContext upstreamu [?: tam może być rozmiar widoku].
+         thumbcastOverlay.setVideoRect(left, top, left + v.getWidth(), top + v.getHeight(),
+                 prefConfig.width, prefConfig.height);
+     });
+ }
+ // THUMBCAST END
```

Jeśli `StreamView` wypełnia cały ekran, a letterbox robi dekoder wewnątrz Surface [?], ten
prostokąt będzie za duży; wtedy zostaje `setStreamResolution(...)` (overlay liczy prostokąt
z proporcji, `ViewportMath.fitCentered`) i usuwamy wywołanie `setVideoRect`.

## 5. Routing dotyku: `onTouch()` / `handleMotionEvent()`

Plan 2.2: `backgroundTouchView.setOnTouchListener(this)`, w `onTouch` jest
`view.requestUnbufferedDispatch(event)` na ACTION_DOWN, potem `handleMotionEvent()`; palce trafiają do
`touchContextMap` (`TouchContext[2]`), mysz/stylus/touchpad do gałęzi pointer, joystick do
`controllerHandler.handleMotionEvent()`.

Overlay jest **nad** `backgroundTouchView` i konsumuje każdy gest zaczęty palcem (zwraca `true`),
a gesty myszy/rysika oddaje (`false` na ACTION_DOWN innym niż palec). Mimo to dodajemy jawne
zabezpieczenie w gałęzi palców, bo `handleMotionEvent` jest też wołane z `dispatchGenericMotionEvent`
/ `onGenericMotionEvent` i innych ścieżek [?]:

```java
  // w handleMotionEvent(...), tuż PRZED pętlą/odczytem touchContextMap dla TOOL_TYPE_FINGER
+ // THUMBCAST: overlay obsługuje palce w całości (do 10 wskaźników); touchContextMap pomijamy.
+ if (thumbcastOverlay != null && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
+     return true;
+ }
+ // THUMBCAST END
```

- Gałęzie myszy fizycznej, klawiatury i joysticka (`controllerHandler.handleMotionEvent`) bez zmian.
- Stuknięcie trzema palcami (`toggleKeyboard()`) w trybie Thumbcast **nie działa** (palce nie docierają
  do upstreamu). Zastępuje je kontrolka `system` z `{"app": "soft_keyboard"}`.
- `ACTION_CANCEL`/utrata fokusu: overlay sam woła `engine.releaseAll()`. Nie trzeba nic dodawać
  w `Game.onWindowFocusChanged`.

## 6. Obsługa akcji aplikacji

Nowa metoda w `Game`:

```java
+ // THUMBCAST:
+ private void onThumbcastAppAction(String action) {
+     switch (action) {
+         case "soft_keyboard":
+             toggleKeyboard();            // istniejąca metoda GameGestures
+             break;
+         case "menu":
+             openContextMenu(streamView); // [?] upstream ma menu kontekstowe Game; w E2 bottom sheet z planu 7 pkt 5
+             break;
+         case "toggle_overlay":
+             // Overlay już przełączył widoczność kontrolek (fabryka); tu tylko ewentualna aktualizacja UI.
+             break;
+         default:
+             break;
+     }
+ }
+ // THUMBCAST END
```

## 7. Sprzątanie

W `onDestroy()` (albo w miejscu, gdzie upstream zatrzymuje połączenie, np. `stopConnection()` [?]):

```java
+ // THUMBCAST:
+ if (thumbcastOverlay != null) {
+     thumbcastOverlay.releaseAll();
+ }
+ if (thumbcastPadReporter != null) {
+     thumbcastPadReporter.cancelPending();
+ }
+ // THUMBCAST END
```

`releaseAll()` musi pójść **przed** zamknięciem `NvConnection`, żeby host dostał zerowy stan.
Odpięcie widoku (`onDetachedFromWindow`) i tak woła `releaseAll()`, ale wtedy połączenie może już nie istnieć.

## 8. Rumble i pad BT

- Rumble: bez zmian. `ControllerHandler` dalej wibruje telefonem, gdy `vibrateOsc` jest włączone
  i nie ma pada fizycznego [Z:6d]. W trybie Thumbcast `prefConfig.onscreenController` może być
  wyłączone; sprawdzić, czy fallback rumble nie zależy od tej flagi [?].
- Pad BT: overlay sam nasłuchuje `InputManager.InputDeviceListener` (upstream ma pusty
  `onInputDeviceAdded` [Z:6d]), więc w `Game` nic nie dodajemy.
