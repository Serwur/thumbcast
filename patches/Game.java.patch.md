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

> **Od 11.11.2026 (sesja Thumbcast):** cała logika z dawnych metod `createThumbcastOverlay()` i `onThumbcastAppAction()`
> jest w `app/src/main/java/com/limelight/thumbcast/ThumbcastSession.java`. `Game` ma jedno pole i jednolinijkowe haki
> (poniżej). Dawny opis z polami `thumbcastOverlay`/`thumbcastPadReporter` jest nieaktualny; pewny stan: `patches/0001-thumbcast.diff`.
> Punkt 7 z listy wyżej („menu” jako no-op) też jest nieaktualny: `"menu"` i systemowy Back otwierają menu strumienia.

## 1. Importy

`FrameLayout`, `View` i reszta są już zaimportowane w v12.2. Po importach `com.limelight.*`:

```java
+ // THUMBCAST:
+ import com.limelight.thumbcast.ThumbcastSession;
+ // THUMBCAST END
```

## 2. Pole klasy `Game`

Kotwica: `private VirtualController virtualController;`. Tuż po niej:

```java
+ // THUMBCAST:
+ private ThumbcastSession thumbcast; // null = tryb legacy
+ // THUMBCAST END
```

## 3. `Game.onCreate()`: utworzenie sesji

Kotwica (po pętli `touchContextMap`): `if (prefConfig.onscreenController) {` (blok VirtualController).
Jedna wstawka przed nim i jedna zmieniona linia warunku:

```java
+ // THUMBCAST:
+ thumbcast = ThumbcastSession.createOrNull(this, ThumbcastSession.activityHost(this, this), prefConfig,
+         conn, controllerHandler, keyboardTranslator, streamView);
+ // THUMBCAST END
~ if (prefConfig.onscreenController && thumbcast == null) {
```

`createOrNull` zwraca null w trybie legacy i przy błędzie profilu (Toast `thumbcast_profile_error`); wtedy upstream tworzy
`VirtualController` jak zwykle (`onscreenController` jest wymuszone). Sesja dokłada overlay jako ostatnie dziecko
`(FrameLayout) streamView.getParent()` (to samo rzutowanie co upstream dla `VirtualController`), ustawia listener layoutu
`StreamView` → `setVideoRect(l, t, r, b, prefConfig.width, prefConfig.height)`, tworzy `HostPadReporterImpl` i stosuje
rozmiar kontrolek (`ScaleTransform`). `activityHost(this, this)`: klawiatura przez upstreamowe `GameGestures.toggleKeyboard()`,
rozłączenie przez `finish()` (tak jak Back w upstreamie: `onStop()` → `stopConnection()`).

## 4. Routing dotyku

Kotwica: gałąź palca `else {` w `handleMotionEvent(View, MotionEvent)`, przed `if (virtualController != null &&`
(`MoveButtons`/`ResizeButtons`):

```java
+ // THUMBCAST: overlay obsługuje palce w całości (do 10 wskaźników); touchContextMap pomijamy.
+ if (thumbcast != null && thumbcast.consumesFingerTouches()) return true;
+ // THUMBCAST END
```

Zwykle martwe (overlay konsumuje palce, zanim dotrą do `backgroundTouchView`), ale `handleMotionEvent` jest też wołane
z `onGenericMotionEvent`. Trzy palce → klawiatura nie działa w trybie Thumbcast; zastępuje je kontrolka `soft_keyboard` i menu.

## 5. Back i akcje aplikacji

Nowa metoda obok `toggleKeyboard()` (v12.2 nie nadpisuje `onBackPressed`; w manifeście `Game` ma
`enableOnBackInvokedCallback="false"`, więc przycisk i gest Back przychodzą tu):

```java
+ // THUMBCAST: systemowy Back w trybie Thumbcast otwiera menu strumienia (Rozłącz jest w menu).
+ @Override
+ public void onBackPressed() {
+     if (thumbcast == null || !thumbcast.onBackPressed()) super.onBackPressed();
+ }
+ // THUMBCAST END
```

Syntetyczny Back z prawego przycisku myszy i przyciski pada obsługuje wcześniej `handleKeyDown/Up` (bez zmian), więc tu trafia
tylko Back systemowy. Akcje `Output.App` z overlaya rozdziela sesja (mapa akcja → `Runnable`, klucze `AppActions.*`):
`menu` → menu strumienia, `soft_keyboard` → klawiatura, `toggle_overlay` → ukryj/pokaż kontrolki.

Menu strumienia (od 11.11.2026, poprawka po teście na Androidzie 16) nie jest osobnym oknem. Wcześniejszy `AlertDialog`
z `FLAG_NOT_FOCUSABLE` i skopiowanym `setSystemUiVisibility` aktywności rozjeżdżał przy edge-to-edge/immersive miejsce
rysowania i obszar dotyku (tap trafiał w pozycję niżej). Teraz `ThumbcastMenuView` jest dokładany jako ostatnie dziecko
tego samego `FrameLayout` co StreamView i overlay: przyciemnione tło na cały ekran (tap = „Wróć do gry”, konsumuje każdy
dotyk, więc overlay nic nie dostaje), panel boczny przy krawędzi END, przewijany, wiersze min. 52 dp; rozmiar kontrolek
to wiersz z − / + (kolejne wartości `ThumbcastSettings.SCALE_PERCENTS`, zmiana na żywo). Pozycje i akcje są
w `ThumbcastMenuModel`. Przy otwarciu `overlay.releaseAll()`. Back przy otwartym menu je zamyka, PiP i `onDestroy` zamykają
menu. Okno nie traci fokusu (StreamView go zachowuje, elementy menu nie są fokusowalne), więc klawiatura ekranowa po
zamknięciu idzie przez zwykły `post` bez dawnego opóźnienia 200 ms. `Game.java` bez zmian.

## 6. Sprzątanie

`stopConnection()`, na początku bloku `if (connecting || connected) {`:

```java
+ // THUMBCAST: zerowy stan na hosta przed zamknięciem połączenia.
+ if (thumbcast != null) thumbcast.onStopConnection();
+ // THUMBCAST END
```

`onDestroy()`, po `super.onDestroy();`, przed `if (controllerHandler != null) {` (anuluje retransmisje, wyrejestrowuje
listener padów, zamyka menu):

```java
+ // THUMBCAST:
+ if (thumbcast != null) thumbcast.onDestroy();
+ // THUMBCAST END
```

## 7. Picture-in-Picture

W `onConfigurationChanged`, po `virtualController.hide()` (wejście w PiP) i po `virtualController.show()` (wyjście):

```java
+ // THUMBCAST:
+ if (thumbcast != null) thumbcast.onPictureInPictureChanged(true);   // false w gałęzi wyjścia
+ // THUMBCAST END
```

W PiP sesja woła `releaseAll()`, zamyka menu i chowa overlay.

## 8. Rumble i pad BT

- Rumble: `ControllerHandler.handleRumble` wibruje telefonem warunkiem
  `!foundMatchingDevice && prefConfig.onscreenController && !prefConfig.onlyL3R3 && prefConfig.vibrateOsc`.
  Z wymuszonym `onscreenController = true` (pkt 2 wyżej) rumble działa w trybie Thumbcast; `vibrateOsc` użytkownik włącza
  w ustawieniach (preferencja `checkbox_vibrate_osc`). Dopóki `onlyL3R3` jest włączone, wibracja jest wyłączona.
- Pad BT: `ControllerHandler` implementuje `InputManager.InputDeviceListener`; `ThumbcastSession` rejestruje własny listener
  (ukrywanie kontrolek przy podłączonym padzie, ustawienie `checkbox_thumbcast_hide_on_gamepad`), więc nic nie dodajemy w `Game`.
- Połączenie z padem fizycznym: `sendControllerInputPacket` łączy `defaultContext` (nasz `reportOscState`) z padami
  fizycznymi tego samego `controllerNumber` przez OR przycisków i `maxByMagnitude` osi, więc nasz stan i fizyczny pad 0 się sumują.

## 9. Menu w grze zatrzymuje klawiaturę i pada (12.11.2026)

- Na początku `handleKeyDown` i `handleKeyUp` blok THUMBCAST pyta `thumbcast.routeKeyEvent(event)`:
  `KEY_PASS` = dalej jak w upstreamie, `KEY_CONSUMED` = `return true`, `KEY_SYSTEM` = `return false` (głośność, zasilanie).
  `handleKeyMultiple` zwraca `true`, gdy `routeKeyEvent` zjada zdarzenie.
- Na początku `handleMotionEvent` blok THUMBCAST: `if (thumbcast != null && thumbcast.consumeMotionEvent(event)) return true;`
  (przy otwartym menu nic z pada, myszy ani touchpada nie idzie do hosta; krzyżak i lewa gałka nawigują po menu).
- `ControllerHandler` (bloki THUMBCAST): `releaseAllControllerInput()` zeruje stan każdego pada fizycznego, który coś
  trzymał (sesja woła to przy otwarciu menu, razem z `overlay.releaseAll()`), a `setThumbcastInputBlocked(boolean)`
  blokuje `reportControllerState` (pady z własnego sterownika USB omijają `Game`).

## 10. Nakładka statystyk i „Zakończ sesję” (12.11.2026)

**`Game.onCreate`**, w istniejącym bloku THUMBCAST tworzenia sesji (kotwica: `thumbcast = ThumbcastSession.createOrNull(`),
po `createOrNull(...)`:

```java
+ // Nakładka statystyk Thumbcast (włączana w menu w grze) zamiast upstreamowego tekstu performanceOverlay.
+ if (thumbcast != null) thumbcast.attachPerfStats(decoderRenderer, performanceOverlayView);
```

`decoderRenderer` i `performanceOverlayView` istnieją już w tym miejscu (renderer tworzony przed `conn = new NvConnection(`).
Sesja chowa upstreamowy `TextView` i zeruje `prefConfig.enablePerfOverlay` tylko w obiekcie tego strumienia (renderer nie
składa wtedy upstreamowego tekstu, a gałąź wyjścia z PiP `if (prefConfig.enablePerfOverlay)` nie pokazuje widoku).
Preferencja zapisana się nie zmienia. W trybie legacy (`thumbcast == null`) wszystko jak w upstreamie.

**`Game.stopConnection()`**, kotwica: `new Thread() {` z `conn.stop();` (komentarz upstreamu „Stop may take a few hundred ms…”):

```java
+ // THUMBCAST: „Zakończ sesję” z menu w grze: po zatrzymaniu strumienia zamknij aplikację na hoście (/cancel).
+ final Runnable thumbcastAfterStop = thumbcast != null ? thumbcast.takeAfterStopTask() : null;
+ // THUMBCAST END
  new Thread() {
      public void run() {
          conn.stop();
+         // THUMBCAST:
+         if (thumbcastAfterStop != null) thumbcastAfterStop.run();
+         // THUMBCAST END
      }
  }.start();
```

`takeAfterStopTask()` zwraca zadanie tylko po „Zakończ sesję” (raz): `ThumbcastHostQuit` buduje `NvHTTP` z intencji `Game`
(`EXTRA_HOST`, `EXTRA_PORT`, `EXTRA_HTTPS_PORT`, `EXTRA_UNIQUEID`, `EXTRA_SERVER_CERT`) i woła `quitApp()` (to samo `/cancel`
co `ServerHelper.doQuit` z listy PC i aplikacji), wynik jako Toast. Jeśli strumień nie był zestawiony (`stopConnection` nic nie
robi), zadanie uruchamia `ThumbcastSession.onDestroy` w osobnym wątku. „Rozłącz” bez zmian (`finish()`).

**`MediaCodecDecoderRenderer`** (`app/src/main/java/com/limelight/binding/video/`), bloki THUMBCAST:
- importy `app.thumbcast.android.stats.StreamStats` i `StreamStatsListener` po `import android.os.SystemClock;`;
- pole `private volatile StreamStatsListener thumbcastStatsListener;` po `private PerfOverlayListener perfListener;`;
- po `setRenderTarget(...)`: `setThumbcastStatsListener(listener)` i `buildThumbcastStats()` (te same liczniki co upstreamowy
  tekst: `lastWindowVideoStats` + `activeWindowVideoStats`, `getFps()`, dekoder i kodek z `videoFormat`,
  `MoonBridge.getEstimatedRttInfo()`);
- kotwica `// Flip stats windows roughly every second` + `if (SystemClock.uptimeMillis() >= activeWindowVideoStats.measurementStartTimestamp + 1000) {`:
  na początku bloku `if (thumbcastStatsListener != null) listener.onStreamStats(buildThumbcastStats());`. Upstreamowy tekst
  (`if (prefs.enablePerfOverlay)`) bez zmian.

**`res/xml/preferences.xml`**, kategoria Thumbcast (blok THUMBCAST): `CheckBoxPreference` `checkbox_thumbcast_perf_advanced`
(„Szczegółowe statystyki wydajności”, domyślnie `false`) przed `checkbox_thumbcast_debug`. Upstreamowe
`checkbox_enable_perf_overlay` bez zmian: ten sam klucz przełącza „Statystyki” w menu w grze.
