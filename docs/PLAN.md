> **Uwaga (10.11.2026).** Plan wygenerowany 8.11.2026 (nagłówek poniżej podaje datę opracowania 7.11.2026). Część decyzji została zmieniona w [DECISIONS.md](DECISIONS.md), które ma pierwszeństwo przed tym dokumentem. Plan zostaje w repo jako kopia źródłowa, bez poprawek treści.

# Plan techniczny: dotykowy kontroler do streamingu gry MMO (WoW: Forever) z własnego PC na Androida

Data opracowania: 7 listopada 2026
Robocza nazwa projektu: **Thumbcast** (propozycje nazw w sekcji 11)

**Oznaczenia w tekście**
- **[Z]**: zweryfikowane w źródle (link w sekcji „Źródła”, numer w nawiasie, np. [Z:3]).
- **[W]**: mój wniosek lub rekomendacja, oparta na źródłach, ale niepotwierdzona wprost.
- **[?]**: rzecz niepewna, do sprawdzenia testem albo w kolejnej wersji dokumentacji.

---

## 0. Streszczenie decyzji (TL;DR)

1. **Architekturę Sunshine + fork moonlight-android potwierdzam.** Robimy fork **upstreamu** `moonlight-stream/moonlight-android` (v12.2, GPLv3), a nie forka Artemis. Warstwę streamingu (`nvstream`, `binding/video`, `binding/audio`, JNI `moonlight-core`) zostawiamy bez zmian. Całe wejście dotykowe zastępujemy nowym modułem w Kotlinie. [W]
2. **Domyślny tryb to „Pad+” (hybryda).** Ruch, kamera i skille idą jako wirtualny pad Xbox (natywny Gamepad UI gry). Dotknięcie celu, szczypanie (zoom) i klik w interfejs idą jako mysz. Pełny tryb klawiatura+mysz jest drugim, równorzędnym profilem. Uzasadnienie i zastrzeżenia są w sekcji 4. [W]
3. **Ważne odkrycie po stronie PC.** Od Sunshine v2026.906 pady na Windows obsługuje płatny sterownik **Virtual HID Driver** (licencja roczna lub dożywotnia). Bez licencji zostaje tylko ViGEmBus (EOL, emulacja X360/DS4, instalowany ręcznie), a Sunshine już go nie dołącza [Z:10][Z:11]. Klawiatura i mysz działają bez licencji przez Win32 [Z:12]. Ma to bezpośredni wpływ na tryb domyślny i na onboarding.
4. **Polityka Blizzarda.** EULA zakazuje automatyzacji i botów [Z:14]. W kwestii mapowania wejścia obowiązuje nieformalna reguła wsparcia „one key press = one action”, a 29.10.2026 Blizzard odmówił oceniania konkretnych konfiguracji [Z:15]. Projekt egzekwuje zasadę 1 dotyk = 1 wejście w kodzie: silnik polityk, schemat profilu bez sekwencji i testy właściwości.
5. **Dystrybucja w kolejności:** GitHub Releases, potem F-Droid, na końcu Google Play. Własne `applicationId` i nazwa bez „WoW” i bez „Moonlight”. Od 30.09.2026 obowiązuje weryfikacja deweloperów Androida dla sideloadu w 4 krajach, globalnie od 2027 [Z:20], więc rejestrujemy aplikację od początku.

---

## 1. Kontekst i weryfikacja założeń

### 1.1 Gra: WoW: Forever
- Premiera 4.11.2026, platformy Windows i macOS [Z:1].
- Natywny „Gamepad (Alpha)” [Z:2][Z:3]:
  - włączenie: Options > Gameplay > Gamepad > „Enable Gamepad UI (Alpha)”; da się to zrobić też na ekranie logowania;
  - wspierane są „all XInput-compatible controllers” [Z:2];
  - lewa gałka porusza postacią, prawa obraca kamerę;
  - zoom to **LB+RB + prawa gałka przód/tył** [Z:2];
  - LB to cele przyjazne, RB to cele wrogie (soft-targeting, pierścień celowania prawą gałką) [Z:3];
  - A = skok, B = anuluj, X = interakcja/atak, Y = menu kontekstowe; w zestawie bazowym są stałe [Z:3];
  - akcje: 4 zestawy po 8 przycisków (D-pad + przyciski twarzowe), przełączane trzymaniem LT, RT lub LT+RT. Daje to 28 slotów konfigurowalnych i 4 stałe [Z:3]. LB+RB+D-pad lewo/prawo zmienia strony pasków, łącznie 84 sloty [Z:2];
  - skróty LB+RB+przycisk: B plecak, Up quest tracker, Down czat, L3 centrowanie kamery [Z:2][Z:3];
  - **klawiatura i mysz działają równolegle z włączonym Gamepad UI** [Z:3];
  - czat nadal wymaga klawiatury [Z:2];
  - niektóre menu w becie wymagają myszy [Z:3];
  - ustawienia: progi „Back Pedal” i „Stick Run” (domyślnie 0.6 i 0.5), czułość kamery, inwersja, Follow Camera, Compact Action Bar [Z:3][Z:4].
- [?] Nie wiadomo, czy gra rozpoznaje wirtualnego DS4 jako „XInput-compatible”. Bezpiecznie jest emulować **Xbox 360/Xbox Series**.
- [?] Nie wiadomo, czy ruch myszy lub kółko przełącza HUD z trybu pad na KBM (migotanie UI). Mein-MMO pisze o przełączaniu „za naciśnięciem przycisku” [Z:5]. To kluczowy test etapu MVP (T-03 w sekcji 9).

### 1.2 Serwer: Sunshine (LizardByte)
- Aktualny stabilny release to v2026.914.233613 (15.09.2026), pre-release v2026.1018 [Z:10].
- Wejście od v2026.906 obsługuje **libvirtualhid**. Na Windows AMD64 zastępuje ViGEmBus; profile pada to X360, Xbox One, Xbox Series, DS4, DualSense, Switch Pro i generic [Z:11].
- Konfiguracja `gamepad_driver` przyjmuje wartości `all`, `virtualhid` (wymaga płatnej licencji, wyłącza fallback ViGEm) albo `vigembus` (EOL, tylko X360/DS4) [Z:9].
- ViGEmBus zarchiwizowano 2.11.2023 [Z:13]. Od v2026.516 Sunshine nie instaluje go automatycznie; da się go doinstalować ze strony Troubleshooting w web UI [Z:10].
- libvirtualhid na Windows: „A license is required to create virtual gamepads, keyboards, or Raw Input-visible mice”. Klawiatura i mysz mają fallback Win32, **pady nie mają fallbacku** poza ViGEm [Z:12].
- macOS jako host: libvirtualhid obsługuje tylko klawiaturę i mysz, pady pojawiły się dopiero w pre-release v2026.1017 [Z:10][Z:12]. **Na Macu tryb KBM jest jedyną pewną opcją.** [W]
- Opcje istotne dla nas [Z:9]: `controller`, `gamepad` (auto/x360/xseries/…), `keyboard`, `mouse`, `high_resolution_scrolling` (domyślnie włączone), `native_pen_touch`, `key_repeat_delay` (500 ms), `always_send_scancodes`, `lan_encryption_mode` (0), `wan_encryption_mode` (1), `upnp` (wyłączone), `origin_web_ui_allowed` (lan), `ping_timeout` (10 s).

**Kwestionuję architekturę: alternatywy po stronie PC**

| Opcja | Plusy | Minusy | Werdykt |
|---|---|---|---|
| Sunshine (upstream) | Aktywny rozwój, dokumentacja, najlepsza zgodność z Moonlight | Pady na Win wymagają płatnej licencji albo starego ViGEm | **Domyślnie** |
| Apollo (fork Sunshine, GPLv3) | Wirtualny ekran SudoVDA dopasowany do rozdzielczości telefonu (np. 2400×1080, 20:9) [Z:17] | Rozjeżdża się z upstreamem, Windows-only; sterownik pada nie jest jasno opisany [?] | Opcjonalnie, „dla zaawansowanych” |
| Steam Remote Play / Parsec | Gotowe | Zamknięte, brak kontroli nad warstwą wejścia | Odrzucam |

Rekomendacja [W]: aplikacja wspiera dowolny host GameStream (Sunshine/Apollo), a dokumentacja prowadzi przez Sunshine. Dla trybu Pad+ onboarding pokazuje dwie ścieżki: (a) licencja Virtual HID od LizardByte, (b) ręczna instalacja ViGEmBus i `gamepad_driver = vigembus`, `gamepad = x360`. Ścieżka (b) jest darmowa, ale oparta na porzuconym sterowniku. Dla użytkownika bez pada po stronie PC zostaje tryb KBM, który działa zawsze.

### 1.3 Klient: moonlight-android, wybór bazy
- Upstream: v12.2 (12.09), targetSdk 36, compileSdk 37, minSdk 21, NDK 29.0.14206865, AGP 9.4.0, Gradle 9.7.1, Java 17. Flavory `root` i `nonRoot` (`com.limelight`). Zależności: bouncycastle, okhttp 5.5, jmdns, jcodec, ShieldControllerExtensions [Z:6][Z:7].
- Artemis (`ClassicOldSong/moonlight-android`, GPLv3) ma już własne przyciski klawiatury z importem i eksportem, „free joystick” i tryby myszy [Z:8]. Kod jest jednak duży i rozbieżny, zorientowany na Apollo.
- **Decyzja [W]:** fork upstreamu, bo dużo łatwiej rebase’ować się na nowe wersje. Pomysły (i w razie potrzeby fragmenty kodu) z Artemis są dozwolone, bo oba projekty to GPLv3; atrybucję zapisujemy w NOTICE.

---

## 2. Wynik badania kodu moonlight-android

Wszystkie ścieżki są względne do `app/src/main/java/com/limelight/` i zweryfikowane listingiem drzewa git [Z:6a].

### 2.1 Mapa modułów

| Obszar | Pliki/klasy | Rola |
|---|---|---|
| Lista PC i parowanie | `PcView`, `AppView`, `computers/ComputerManagerService`, `computers/IdentityManager`, `nvstream/http/NvHTTP`, `nvstream/http/PairingManager`, `discovery/DiscoveryService`, `nvstream/mdns/*`, `preferences/AddComputerManually`, `nvstream/wol/WakeOnLanSender` | Odkrywanie hostów (mDNS), parowanie PIN-em, ręczne dodawanie IP (tu wpisujemy IP Tailscale), WoL |
| Sesja strumienia | `Game.java` (Activity), `ui/StreamView` (SurfaceView `@id/surfaceView`), `res/layout/activity_game.xml` | Activity strumienia: tu podpinamy overlay |
| Połączenie | `nvstream/NvConnection`, `nvstream/jni/MoonBridge`, `jni/moonlight-core/moonlight-common-c` (submoduł) | API wysyłania wejścia do hosta |
| Wideo/audio | `binding/video/MediaCodecDecoderRenderer`, `binding/audio/AndroidAudioRenderer`, `binding/video/PerfOverlayListener` | Bez zmian; z overlaya wydajności bierzemy metryki |
| Dotyk | `binding/input/touch/TouchContext`, `AbsoluteTouchContext`, `RelativeTouchContext` | Obecny „dotyk jako mysz”. Do zastąpienia w naszym trybie |
| Wirtualny pad | `binding/input/virtual_controller/VirtualController`, `VirtualControllerElement`, `VirtualControllerConfigurationLoader`, `AnalogStick`, `DigitalButton`, `DigitalPad`, `LeftTrigger`/`RightTrigger`, `LeftAnalogStick`/`RightAnalogStick` | Obecny OSC. Do zastąpienia (zostawiamy jako tryb „legacy”) |
| Pady fizyczne | `binding/input/ControllerHandler`, `binding/input/driver/*` (sterowniki USB Xbox), `nvstream/input/ControllerPacket` | Pady BT/USB, rumble, sensory, łączenie kontekstów |
| Klawiatura | `binding/input/KeyboardTranslator`, `nvstream/input/KeyboardPacket` | Tłumaczenie keycode Androida na kod hosta |
| Przechwytywanie myszy | `binding/input/capture/*` (`InputCaptureManager`, `AndroidNativePointerCaptureProvider`) | Mysz fizyczna, pointer capture |
| Ustawienia | `preferences/PreferenceConfiguration`, `preferences/StreamSettings` | Klucze `checkbox_show_onscreen_controls`, `seekbar_osc_opacity`, `seekbar_deadzone` (7), `checkbox_touchscreen_trackpad` (true), `checkbox_multi_controller` (true), `checkbox_vibrate_osc` (true), … [Z:6e] |
| Gesty | `ui/GameGestures` | Interfejs (`toggleKeyboard()` i inne) |

### 2.2 Jak to działa dziś (szczegóły ważne dla integracji)

- **Tworzenie OSC** [Z:6b]: w `Game.onCreate()`, gdy `prefConfig.onscreenController` jest włączone, wykonuje się `new VirtualController(controllerHandler, (FrameLayout) streamView.getParent(), this)`, potem `refreshLayout()` i `show()`. Przy włączonym OSC orientacja jest zablokowana na landscape.
- **Routing dotyku** [Z:6b]:
  - `backgroundTouchView.setOnTouchListener(this)`; w `onTouch` jest `view.requestUnbufferedDispatch(event)` na ACTION_DOWN, potem `handleMotionEvent()`;
  - zdarzenia joysticka idą do `controllerHandler.handleMotionEvent()`, mysz/stylus/touchpad do gałęzi pointer, palce do `touchContextMap`;
  - **`touchContextMap` to `TouchContext[2]`, czyli tylko 2 palce**;
  - natywne `sendTouchEvent` jest zakomentowane (TODO);
  - stuknięcie trzema palcami (<300 ms) wywołuje `toggleKeyboard()`;
  - rozdzielczość referencyjna to 1280×720.
- **VirtualController** [Z:6c]:
  - stan trzyma `ControllerInputContext` (`short inputMap`, `byte leftTrigger/rightTrigger`, `short left/rightStickX/Y`);
  - `sendControllerInputContext()` wysyła stan natychmiast **i retransmituje go po 25, 50 i 75 ms**. To obejście gubienia pakietów przez host; powtarzany jest ten sam stan, nie nowe naciśnięcia;
  - wysyłka idzie przez `controllerHandler.reportOscState(int buttonFlags, short lsX, short lsY, short rsX, short rsY, byte lt, byte rt)`, który zapisuje stan do `defaultContext` i woła `sendControllerInputPacket` [Z:6d];
  - tryby edycji `Active → MoveButtons → ResizeButtons`; zapis do SharedPreferences „OSC” jako JSON `{LEFT, TOP, WIDTH, HEIGHT}` per `elementId` [Z:6f][Z:6g];
  - siatka 128×72 jednostek przeskalowana do wysokości ekranu [Z:6f];
  - `VirtualControllerElement.onTouchEvent` **ignoruje wskaźniki z `getActionIndex() != 0`**. Każdy element to osobny `View`, więc multitouch opiera się na dzieleniu zdarzeń przez `FrameLayout` [Z:6g];
  - `AnalogStick` ma stałą pozycję, martwą strefę 30% promienia, double-tap 350 ms i dead-zone timeout 150 ms [Z:6h].
- **ControllerHandler** [Z:6d]:
  - `MAX_GAMEPADS = 16`;
  - konteksty o tym samym numerze kontrolera są **łączone**: przyciski przez OR, osie przez `maxByMagnitude`. Dzięki temu pad fizyczny i wirtualny na kontrolerze 0 współpracują bez naszej logiki;
  - typ pada (Xbox/PS/Nintendo) rozpoznawany po VID; `sendControllerArrival` wysyła typ, `supportedButtonFlags` i capabilities;
  - fallback rumble na wibrację telefonu (`vibrateOsc`).
- **API wysyłania** (`NvConnection`, wszystkie metody blokowane tylko w trybie „monkey”) [Z:6i]:
  - `sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags)`;
  - `sendMouseMove(short dx, short dy)`, `sendMousePosition(short x, short y, short refW, short refH)`, `sendMouseMoveAsMousePosition(...)`;
  - `sendMouseButtonDown/Up(byte button)`;
  - `sendMouseHighResScroll(short)` (120 = 1 ząbek, zgodnie z `Game`);
  - `sendControllerInput(short controllerNumber, short activeGamepadMask, int buttonFlags, byte lt, byte rt, short lsX, short lsY, short rsX, short rsY)`, `sendControllerArrivalEvent(...)`;
  - `sendTouchEvent(...)` zwraca `LI_ERR_UNSUPPORTED`, jeśli host nie ma `LI_FF_PEN_TOUCH_EVENTS` [Z:6j];
  - `sendUtf8Text(String)`.
- **Flagi przycisków** (`ControllerPacket`) [Z:6k]: `A_FLAG=0x1000`, `B=0x2000`, `X=0x4000`, `Y=0x8000`, `UP=0x0001`, `DOWN=0x0002`, `LEFT=0x0004`, `RIGHT=0x0008`, `LB=0x0100`, `RB=0x0200`, `PLAY(Start)=0x0010`, `BACK=0x0020`, `LS_CLK=0x0040`, `RS_CLK=0x0080`, `SPECIAL(Guide)=0x0400`, a dla Sunshine także `PADDLE1..4=0x010000..0x080000`, `TOUCHPAD=0x100000`, `MISC=0x200000`.
- **Kontrolery w protokole** [Z:6j]: Sunshine akceptuje numery 0–15. Zamiast pustego pakietu zalecany jest `LiSendControllerArrivalEvent` (host wybiera wtedy lepszy emulowany typ). Dostępne typy: `LI_CTYPE_XBOX=0x01`, `LI_CTYPE_PS=0x02` i inne [Z:6l].

### 2.3 Punkty wpięcia nowej warstwy [W]

1. **`Game.onCreate()`**: zamiast `VirtualController` (lub obok niego, przełączane preferencją `control_layer = legacy|thumbcast`) tworzymy `ThumbcastOverlay(conn, controllerHandler, streamView, profileRepo)`. Dodajemy go jako ostatnie dziecko `FrameLayout` rodzica `StreamView`, żeby leżał nad strumieniem.
2. **`Game.onTouch()` / `handleMotionEvent()`**: gdy nasza warstwa jest aktywna, zdarzenia z palców (`TOOL_TYPE_FINGER`) **nie trafiają do `touchContextMap`**, bo overlay konsumuje je w całości. Gałęzie myszy fizycznej, klawiatury i joysticka zostają bez zmian.
3. **Wyjście pada**: używamy `controllerHandler.reportOscState(...)`, czyli kontekstu 0 łączonego z padem fizycznym; dostajemy za darmo retransmisję i rumble na telefonie. Alternatywa to własny numer kontrolera z `sendControllerArrivalEvent(type=LI_CTYPE_XBOX)`, ale wtedy gra widzi dwa pady [?]. W MVP: `reportOscState`.
4. **Wyjście KBM**: bezpośrednio `NvConnection.sendKeyboardInput/sendMouse*`. Kody klawiszy bierzemy przez `KeyboardTranslator` (keycode Androida na kod hosta), żeby nie duplikować tablic.
5. **Hook geometrii**: prostokąt wideo w `StreamView` (aspect ratio z `setDesiredAspectRatio`, letterbox) jest potrzebny do mapowania dotknięcia na `sendMousePosition`.
6. **Preferencje**: nowa sekcja w `StreamSettings` (XML preferences) plus własne ekrany Compose dla profili.
7. **Auto-ukrywanie** overlaya po podłączeniu pada BT: `InputManager.InputDeviceListener` w overlayu (ControllerHandler ma pusty `onInputDeviceAdded`, więc nie kolidujemy [Z:6d]).

---

## 3. Stack i narzędzia

| Element | Wybór | Uzasadnienie |
|---|---|---|
| IDE | Android Studio (stabilne, zgodne z AGP 9.5) | Standard; NDK i CMake dla `moonlight-core` |
| Język | **Kotlin 2.4.21** [Z:22] dla nowych modułów, Java 17 w kodzie upstream | Nie przepisujemy upstreamu, żeby ułatwić rebase |
| Build | AGP **9.4.0** (jak upstream) z przejściem na 9.5.0 [Z:23]; Gradle wrapper 9.7.1 (upstream), AGP 9.5 wymaga ≥ 9.6.0 [Z:23] | Trzymamy wersje upstreamu i aktualizujemy razem z rebase |
| Kotlin w AGP 9 | „built-in Kotlin” (AGP 9 ma przewodnik migracji) [Z:23] [?] | Mniej pluginów; sprawdzić przy konfiguracji |
| NDK | **29.0.14206865** (jak upstream) [Z:7] | Zgodność z `moonlight-common-c` |
| SDK | compileSdk 37, targetSdk 36 (wymóg Play od 31.08.2026 [Z:21]), **minSdk 26** | API 26 daje `VibrationEffect`, adaptacyjne ikony i stabilne `requestUnbufferedDispatch`. Upstream ma 21, ale dla grupy docelowej (telefony do gier) 26 jest bezpieczne [W] |
| UI edytora i ekranów | **Jetpack Compose**, BOM **2026.09.00** (foundation 1.12.1, material3 1.4.0) [Z:24] | Drag/scale/gesty w edytorze, szybkie iteracje UI |
| UI overlaya w grze | **Własny `View` + Canvas** (jeden widok na wszystkie kontrolki) | Pełna kontrola nad pointerId, brak rekompozycji w ścieżce krytycznej, minimalne opóźnienie [W] |
| Serializacja profili | `kotlinx.serialization` (JSON), wersja zgodna z Kotlin 2.4 [?] | Wersjonowany schemat, walidacja przy imporcie |
| Przechowywanie | Pliki JSON w `filesDir/profiles/` plus DataStore (Preferences) na ustawienia globalne | Profile są dokumentami do eksportu, więc pliki są naturalne; bez Room |
| Import/eksport | Storage Access Framework (`ACTION_CREATE_DOCUMENT`/`OPEN_DOCUMENT`), share intent, opcjonalnie QR (ZXing, Apache-2.0) | Zgodne z F-Droid (bez GMS) |
| Testy | JUnit 5 / kotlin.test, **property-based (Kotest property)** dla silnika polityk, Robolectric dla logiki View, Compose UI test, instrumentacja Espresso na urządzeniu, „fake NvConnection” (interfejs `InputSink`) | Kluczowe: dowód, że 1 dotyk daje 1 wejście |
| Jakość | ktlint/detekt (tylko nowe moduły), Android Lint, LeakCanary (debug) | |
| CI | **GitHub Actions**: build debug/release (z submodułami), testy JVM, lint, artefakt APK, testy na emulatorze (`reactivecircus/android-emulator-runner`), release podpisany sekretem, sumy SHA-256 | Standard; F-Droid i tak zbuduje ze źródeł |
| Zależności zakazane | Firebase, Crashlytics, Play Services | Wymóg F-Droid [Z:19]. Crash reporting lokalny z eksportem logu |

---

## 4. Kluczowa decyzja: pad czy klawiatura+mysz

### 4.1 Porównanie

| Kryterium | Tryb PAD (wirtualny Xbox) | Tryb KBM (klawisze + mysz) |
|---|---|---|
| Wsparcie gry | Natywny Gamepad UI: duże ikony, glyphy przycisków, radialne menu, soft-targeting LB/RB, flyouty klasowe [Z:2][Z:3][Z:4] | Klasyczny UI zaprojektowany pod mysz; na 6" ekranie drobny |
| Ruch | **Analogowy** (lewa gałka; progi chód/bieg ustawiane w grze) | Cyfrowy WASD (8 kierunków), brak płynnego chodu |
| Kamera | Prawa gałka, czyli „prędkość” obrotu. Przeciąganie trzeba przeliczać na wychylenie | Mysz: RMB trzymany + ruch względny, czyli „pozycja”. Przeciąganie jest naturalne 1:1 |
| Celowanie | Soft-target w grze (LB/RB + prawa gałka) [Z:3] | Klik na jednostce (tap-to-target), Tab |
| Liczba akcji | 28 slotów na stronę (modyfikatory LT/RT), 84 łącznie [Z:2][Z:3]. Na dotyku trzeba trzymać modyfikator | Praktycznie bez limitu (1–0, -, =, Shift/Ctrl/Alt+…, F1–F12) |
| Zoom | LB+RB + prawa gałka przód/tył [Z:2]. Jedno wejście użytkownika wymagałoby akordu 3 wejść | Kółko myszy (`sendMouseHighResScroll`) to 1 wejście |
| Klik w UI (loot, NPC, menu) | Kursor gamepadowy/focus, niektóre menu wymagają myszy [Z:3] | Naturalny |
| Czat | Wymaga klawiatury [Z:2] | Klawiatura ekranowa i `sendUtf8Text` |
| Host Windows bez licencji | **Potrzebny ViGEmBus (EOL)** albo płatny Virtual HID [Z:9][Z:11] | Działa (fallback Win32) [Z:12] |
| Host macOS | Pady tylko w pre-release Sunshine [Z:10] [?] | Działa (CoreGraphics; wymaga zgody Accessibility) [Z:12] |
| Ryzyko „UI flip” | Mieszanie z myszą może przełączać HUD [?] | Brak |
| Zgodność z polityką | Bardzo czysta: każdy wirtualny przycisk to dokładnie 1 przycisk pada | Czysta przy 1 klawisz na przycisk; akordy (Shift+1) to szara strefa, patrz 6.2 |
| Pad BT + dotyk | Naturalne łączenie kontekstu 0 (OR/max) [Z:6d] | Pad BT i tak idzie jako pad, więc tryb KBM i pad mieszają się w grze |

### 4.2 Rekomendacja

**Domyślnie: tryb „Pad+” (hybrydowy)** [W]
- Lewy kciuk: pływający joystick, czyli lewa gałka (analog).
- Prawy kciuk: klaster przycisków A/B/X/Y + D-pad (8 slotów), przyciski LT, RT i LB, RB jako **trzymane modyfikatory**. Każdy to osobny przycisk pada, więc każdy dotyk daje jedno wejście.
- Przeciąganie po wolnej prawej połowie ekranu działa jako prawa gałka (kamera) z krzywą czułości.
- Tap na świecie gry: `sendMousePosition` + lewy klik (target), bo KBM działa równolegle z Gamepad UI [Z:3].
- Szczypanie: kółko myszy (zoom), czyli jedno wejście.
- Long-press: prawy klik w punkcie (interakcja, loot).

Dlaczego tak: natywny Gamepad UI jest czytelny na telefonie, analogowy ruch jest lepszy od WASD, a liczba przycisków pada jest stała, więc mapowanie 1:1 z polityką wynika z samej konstrukcji. Gesty myszy domykają luki pada: zoom, klik w UI i cel dotknięciem.

**Warunek:** test T-03 (czy mysz przełącza HUD). Jeśli przełącza i psuje rozgrywkę, Pad+ wpada w wariant „Pad-czysty”:
- tap: brak (cele przez LB/RB);
- zoom: przycisk „LB+RB” (dwa przyciski pada trzymane jednym dotykiem; to akord, patrz 6.2) albo natywny zoom gry z D-padem w trybie skrótów;
- klik w UI: wirtualny kursor pada gry.

**Drugi równorzędny tryb: „Klawiatura+Mysz”.** Profil dla hostów macOS, dla hostów Windows bez sterownika pada i dla graczy z klasycznymi keybindami lub addonami.
- Joystick daje WASD z progiem 8 kierunków; opcjonalnie „analog przez mysz” jest **zakazany** (to już automatyzacja obrotu).
- Przyciski mapują się na 1–0, -, =, F-klawisze.
- Przeciąganie kamery: RMB down na początku gestu, potem relatywne `sendMouseMove`, RMB up na końcu.
- Tap: LMB w punkcie. Pinch: kółko.

**Tryb hybrydowy jest per profil, nie per kontrolka.** Każda kontrolka ma `output` typu `pad` / `key` / `mouse`, a profil ma `mode` wyznaczający domyślne wyjście gestów. Dzięki temu da się np. dodać w Pad+ przycisk „M” (mapa) jako klawisz.

**Pad fizyczny BT:** gdy jest podłączony, overlay chowa joystick i przyciski, a gesty myszy (tap, pinch, drag kamery w trybie KBM) zostają. Przełącznik w menu w grze.

---

## 5. Architektura aplikacji

### 5.1 Moduły Gradle

```
:app                       (upstream, minimalne zmiany: Game.java hooki, manifest, nazwa, ikona)
:core-stream               (opcjonalnie później: wydzielenie nvstream/binding z app; w MVP zostaje w :app)
:control-model             (Kotlin/JVM, bez Androida) – model profilu, schemat, walidacja, migracje
:control-engine            (Kotlin/JVM) – silnik: PointerTracker, HitTest, GestureRecognizers, ActionPolicy, OutputMapper
:control-android           (Android lib) – ThumbcastOverlay (View/Canvas), haptyka, InputSink → NvConnection/ControllerHandler
:control-editor            (Android lib, Compose) – edytor układu, lista profili, import/eksport
:presets                   (zasoby JSON) – profile domyślne (neutralne nazwy: „MMO – Pad+”, „MMO – Klawiatura”)
```
Rozdzielenie `model` i `engine` (czysty JVM) daje szybkie testy właściwości bez emulatora. [W]

### 5.2 Przepływ zdarzeń (dotyk → Sunshine)

```
MotionEvent (Android, unbuffered dispatch)
  → ThumbcastOverlay.onTouchEvent        [jeden View, wszystkie pointerId, Choreographer-free ścieżka]
  → PointerTracker                       [pointerId → właściciel: kontrolka | gest | brak; "capture on down"]
  → HitTest (warstwy, z-order, strefy aktywacji > grafika)
  → Recognizers: Stick | Button | CameraDrag | Tap | LongPress | Pinch | EdgeSwipe
  → Intent (np. ButtonDown(id), StickMove(id,x,y), CameraDelta(dx,dy), Zoom(steps), TapAt(x,y))
  → ActionPolicy                         [egzekwuje 1 dotyk = 1 wejście; odrzuca i loguje naruszenia]
  → OutputMapper (profil: pad/key/mouse, krzywe, martwe strefy, mirror leworęczny)
  → InputSink
        ├─ PadSink   → ControllerHandler.reportOscState(flags, lsX, lsY, rsX, rsY, lt, rt)
        │              (retransmisja 25/50/75 ms tego samego stanu – istniejąca w upstream)
        ├─ KeySink   → NvConnection.sendKeyboardInput(vk, DOWN/UP, mods=0, flags)
        └─ MouseSink → sendMousePosition / sendMouseMove / sendMouseButtonDown/Up / sendMouseHighResScroll
  → moonlight-common-c (ENet/UDP, szyfrowanie) → Sunshine → libvirtualhid / ViGEm / Win32 → gra
```
- Wątek: wysyłka idzie z wątku UI (`NvConnection` jest nieblokujący w JNI, tak jak upstream). Pomiar czasu `event.eventTime → send` trafia do overlaya debug. [W]
- Koalescencja: dla osi wysyłamy najwyżej 1 pakiet na ramkę wejścia; dla przycisków natychmiast. Historyczne próbki `MotionEvent` są wykorzystywane tylko do wygładzania kamery.

### 5.3 Model danych profilu (przykład JSON, schemat v1)

```json
{
  "schema": 1,
  "id": "6b0f1c2e-2f7a-4d4e-9b8a-0d6f3d2c9a11",
  "name": "Kapłan – Pad+",
  "tags": { "game": "mmo-generic", "class": "priest", "character": "Elarion@Serwer" },
  "mode": "pad_plus",
  "reference": { "aspect": "20:9", "unit": "dp-normalized" },
  "handedness": "right",
  "global": {
    "opacity": 0.55,
    "haptics": { "enabled": true, "strength": 0.6, "onPress": "tick", "onRelease": "none" },
    "slideToPress": false,
    "autoHideOnGamepad": true
  },
  "gestures": {
    "cameraDrag": { "zone": "right_free", "output": "pad_right_stick",
                    "sensitivity": 1.2, "curve": { "type": "power", "exponent": 1.6 },
                    "deadzonePx": 4, "invertY": false, "maxDeflection": 1.0, "decayMs": 60 },
    "tapTarget":  { "enabled": true, "output": "mouse_left_at_point", "maxMovePx": 12, "maxMs": 220 },
    "longPress":  { "enabled": true, "output": "mouse_right_at_point", "minMs": 450 },
    "pinchZoom":  { "enabled": true, "output": "mouse_wheel", "pxPerNotch": 40 },
    "edgeSwipeTop": { "enabled": true, "output": "app_menu" }
  },
  "controls": [
    { "id": "move", "type": "stick", "output": { "pad": "left_stick" },
      "rect": { "x": 0.04, "y": 0.45, "w": 0.24, "h": 0.50 }, "floating": true,
      "deadzone": 0.12, "outerDeadzone": 0.04, "curve": { "type": "linear" }, "sprintLock": false },
    { "id": "a", "type": "button", "label": "Skok", "icon": "jump", "output": { "pad": "A" },
      "rect": { "x": 0.86, "y": 0.70, "w": 0.09, "h": 0.16 }, "shape": "circle" },
    { "id": "x", "type": "button", "label": "Akcja", "output": { "pad": "X" },
      "rect": { "x": 0.76, "y": 0.60, "w": 0.09, "h": 0.16 } },
    { "id": "lt", "type": "modifier", "label": "LT", "output": { "pad": "LT" }, "behavior": "hold",
      "rect": { "x": 0.70, "y": 0.05, "w": 0.10, "h": 0.12 } },
    { "id": "map", "type": "button", "label": "Mapa", "output": { "key": "M" },
      "rect": { "x": 0.47, "y": 0.02, "w": 0.06, "h": 0.09 } },
    { "id": "chat", "type": "system", "output": { "app": "soft_keyboard" },
      "rect": { "x": 0.02, "y": 0.02, "w": 0.06, "h": 0.09 } }
  ]
}
```

Reguły schematu (egzekwowane w `control-model`) [W]:
- `output` zawiera **dokładnie jedno** pole: `pad` (jeden przycisk lub jedna oś), `key` (jeden klawisz), `mouse` (jeden przycisk, kółko albo ruch) lub `app` (akcja lokalna, która nic nie wysyła do hosta).
- **Nie istnieją** typy `sequence`, `macro`, `repeat`, `turbo`, `delay`, `toggle_auto`. Parser odrzuca nieznane pola (`ignoreUnknownKeys = false`) i nieznane typy.
- `behavior` przyjmuje tylko `hold` (stan = stan palca) albo `latch` (jedno dotknięcie wciska, następne zwalnia; tylko dla modyfikatorów LT/RT/Shift). `latch` to nadal 1 dotyk = 1 zmiana stanu.
- `chord` (opcjonalny, wyłączony domyślnie, patrz 6.2): `{ "pad": ["LB","RB"] }` albo `{ "key": "1", "mod": "SHIFT" }`, maks. 1 modyfikator + 1 przycisk, wciśnięte i zwolnione razem.
- Współrzędne są znormalizowane (0..1) względem bezpiecznego obszaru ekranu. Przy innym aspekcie skalujemy z zachowaniem kotwic (lewa i prawa krawędź). Migracje schematu (`schema: n → n+1`) są testowane.
- Eksport to plik `.thumbcast.json` (UTF-8), opcjonalnie podpisany sumą SHA-256 w nagłówku (integralność, nie bezpieczeństwo).

### 5.4 Multitouch, martwe strefy, krzywe, haptyka, leworęczność

- **Multitouch** [W]: jeden overlay obsługuje do 10 wskaźników. Zasada „capture on down”: wskaźnik należy do kontrolki, w której wylądował, aż do UP/CANCEL. Bez `slideToPress` przesunięcie palca poza przycisk nie zwalnia go; opcjonalny `slideToPress` najpierw zwalnia poprzedni przycisk, dopiero potem wciska nowy (nigdy dwa naraz z jednego palca). ACTION_CANCEL i utrata fokusu (`onWindowFocusChanged`) robią „release all” i wysyłają zerowy stan. Usuwamy tym samym ograniczenie upstreamu do 2 palców [Z:6b].
- **Joystick**: pływający (centrum tam, gdzie kciuk wylądował w strefie) lub stały. Martwa strefa radialna (domyślnie 12%) i zewnętrzna (4%), skalowanie „rescaled radial deadzone”, żeby nie było skoku przy wyjściu ze strefy. Opcjonalnie „sprint lock”: przesunięcie poza obrys utrzymuje 100%. To stan wejścia, nie automat; ruch nadal trwa tylko, dopóki palec dotyka. Wymiana na wartości `short` (±32767).
- **Kamera (drag → prawa gałka)**: prędkość palca (px/ms, wygładzanie EMA ~2 próbki) przechodzi przez krzywą `power` (wykładnik 1.2–2.0) do wychylenia gałki. Po zatrzymaniu palca wychylenie wygasa w `decayMs`. W trybie KBM: delta px × czułość daje `sendMouseMove` (z akumulacją reszt ułamkowych, tak jak `RelativeTouchContext` [Z:6m]).
- **Haptyka**: `VibrationEffect.createPredefined(EFFECT_TICK)` na naciśnięcie (API 29+), a na API 26–28 `createOneShot(10 ms)`. Szanujemy systemowe ustawienie haptyki. Rumble z gry (`handleRumble`) mapuje się na wibrację telefonu z suwakiem siły (istniejące `vibrateOsc` [Z:6d]).
- **Leworęczność**: transformacja `x → 1 - x - w` dla wszystkich `rect` i zamiana stref gestów lewa/prawa. Mapowanie wyjść się nie zmienia (lewa gałka zostaje ruchem, tylko po prawej stronie ekranu).
- **Obszary systemowe**: `WindowInsets` (wycięcie aparatu, gest „wstecz”). `setSystemGestureExclusionRects` dla stref joysticka (Android ogranicza wykluczenia do 200 dp na krawędź), tryb immersive sticky.

---

## 6. Zgodność z polityką Blizzarda: zabezpieczenia w projekcie

### 6.1 Stan wiedzy
- EULA (rev. 21.03.2024) [Z:14]:
  - 1.C.ii.2 zakazuje botów („any code and/or software, not expressly authorized by Blizzard, that allows the automated control of a Game”);
  - 1.C.ii.4 zakazuje nieautoryzowanego oprogramowania, które „changes and/or facilitates the gameplay”;
  - 1.C.v zakazuje „unauthorized third-party cloud computing” i streamingu z serwerów osób trzecich.
- Input broadcasting jest zakazany od 3.11.2020 [Z:16]. Wsparcie Blizzarda (10.02.2021): „one key press = one action is the general rule of thumb”. 29.10.2026 Blizzard Support odmówił oceniania konkretnych konfiguracji [Z:15].
- Historyczne zgłoszenia banów za WoWMapper/ConsolePort (2021) to plotki, a bany anulowano po apelacji [Z:18].
- **Wniosek [W]:**
  - Nie ma oficjalnej zgody na żadne narzędzie mapujące. Stream z własnego PC to nie „third-party cloud”, bo gra działa na komputerze gracza (moja interpretacja 1.C.v).
  - Remapowanie 1:1 jest w praktyce tolerowane, ale nie gwarantowane. Ryzyko jest niezerowe.
  - Bezpieczniej jest emitować natywne wejście pada, które gra oficjalnie obsługuje, niż złożone sekwencje klawiszy.

### 6.2 Zabezpieczenia (wymagania twarde)
1. **Silnik polityk (`ActionPolicy`)** w czystym Kotlinie:
   - każdy `pointerId` może w danej chwili trzymać **co najwyżej jedno logiczne wejście**;
   - każda zmiana stanu wyjścia musi mieć przyczynę w zdarzeniu dotyku (DOWN/MOVE/UP) z tego samego cyklu obsługi;
   - zdarzenia bez przyczyny (z timera, z innego wątku) są odrzucane i logowane.
   - Wyjątki: retransmisja **identycznego** stanu przez upstream (25/50/75 ms) oraz wygaszanie wychylenia kamery do zera.
2. **Schemat profilu bez sekwencji.** W kodzie nie ma typów makro, opóźnień, turbo ani auto-repeat, więc import cudzego profilu nie doda automatyzacji. Walidator odrzuca nieznane typy.
3. **Brak automatów czasowych**: żadnych timerów generujących naciśnięcia. Long-press to *rozpoznanie* gestu, które daje **jedno** wejście zamiast tapu, nie oba. Auto-repeat klawisza po stronie hosta (`key_repeat_delay`) zachowuje się jak fizyczna klawiatura i nie jest naszą logiką.
4. **Akordy (chord)**:
   - domyślnie wyłączone;
   - po włączeniu: maks. 1 modyfikator + 1 klawisz/przycisk, wciskane w tej samej ramce i zwalniane razem, mapowane na **jedno** wiązanie w grze (np. Shift+1 w KBM albo LB+RB jako „tryb skrótów”);
   - UI pokazuje ostrzeżenie, że to szara strefa.
   - Rekomendacja [W]: zamiast akordów używać modyfikatorów `latch` i `hold` jako osobnych przycisków.
5. **Zero analizy obrazu**: aplikacja nie czyta pikseli strumienia (brak OCR i CV), więc nie ma reakcji warunkowych na stan gry.
6. **Jedno połączenie**: upstream wymusza jedną sesję semaforem `connectionAllowed` [Z:6i]. Nie dodajemy multi-host ani broadcastu.
7. **Testy właściwości w CI**: dla losowych strumieni zdarzeń dotyku liczba „press edges” na wyjściu ≤ liczba DOWN z przypisanym wyjściem, a każda krawędź ma przyczynę. Build failuje przy naruszeniu.
8. **Przejrzystość**: ekran „Zasady fair play” przy pierwszym uruchomieniu, dokument `FAIR_PLAY.md` w repo, zakaz PR-ów dodających makra (CONTRIBUTING). Aplikacja nie jest zatwierdzona przez Blizzard i mówi to wprost.
9. **Brak integracji z grą**: żadnych addonów wymaganych do działania, żadnego czytania pamięci ani plików gry.

---

## 7. Ekrany i UX

1. **Start / Hosty** (Compose; logika z `PcView` + `ComputerManagerService`): lista PC (mDNS i ręczne IP), status online, przycisk „Dodaj przez Tailscale” (pole na `100.x.y.z` lub nazwę MagicDNS), Wake-on-LAN.
2. **Parowanie**: PIN (logika `PairingManager`) z instrukcją „otwórz Sunshine Web UI → PIN” i deep-linkiem do `https://<ip>:47990` (port web UI; [?] zależy od `port`).
3. **Diagnostyka hosta**:
   - checklista: wersja Sunshine; `gamepad_driver` (wyjaśnienie licencji Virtual HID kontra ViGEm) [Z:9];
   - test pada: wyślij A, użytkownik potwierdza w „Ustawienia > Urządzenia” Windows;
   - test klawiatury i myszy;
   - ping i bitrate.
4. **Aplikacje hosta** (`AppView`): wybór „Desktop” lub wpisu gry; preset „Pad+” przypięty do wpisu.
5. **Ekran strumienia** (`Game` + overlay):
   - kontrolki, mini-pasek z ikoną menu (krawędź górna albo przycisk ☰);
   - menu w grze (bottom sheet): profil, przezroczystość, ukryj kontrolki, klawiatura ekranowa, tryb Pad+/KBM, statystyki, rozłącz;
   - nakładka „debug wejścia”: aktywne wskaźniki, wysyłane flagi, opóźnienie event→send.
6. **Profile**: lista z tagami (gra, klasa, postać), duplikuj, eksport/import (SAF, share, QR), domyślny per host i aplikacja.
7. **Edytor układu** (Compose, pełny ekran landscape, tło to zrzut ostatniej klatki albo szachownica):
   - przeciąganie (snap do siatki 2%, linie pomocnicze, symetria lewa/prawa);
   - szczypanie skaluje zaznaczoną kontrolkę; suwak rozmiaru i przezroczystości per kontrolka;
   - panel właściwości: etykieta, ikona (własny zestaw neutralnych ikon), wyjście (pad / klawisz / mysz / aplikacja), kształt, `hold`/`latch`;
   - strefa aktywacji większa od grafiki;
   - podgląd „tryb testu” na żywo (pokazuje, co zostałoby wysłane, bez połączenia);
   - cofnij/ponów, reset do presetu, lustrzane odbicie (leworęczny), walidacja nachodzenia kontrolek.
8. **Ustawienia**: czułości i krzywe (wykres na żywo), martwe strefy, haptyka, auto-ukrywanie przy padzie BT, bitrate i rozdzielczość (z upstreamu), „Zasady fair play”, licencje OSS (GPLv3 + atrybucje).
9. **Onboarding** (3 karty): instalacja Sunshine, ustawienia w grze (Enable Gamepad UI, Show Input Prompts, Compact Action Bar [Z:3][Z:4]), Tailscale.

### 7.1 Domyślny układ „MMO – Pad+” (pod WoW: Forever) [W]
- **Lewa połowa**: pływający joystick (strefa 0–35% szerokości, 40–100% wysokości), nad nim **LB** (cel przyjazny) i **LT** (modyfikator).
- **Prawa dolna ćwiartka**: romb A/B/X/Y (A = skok, X = akcja/atak, B = anuluj, Y = kontekst [Z:3]), na lewo od niego D-pad jako 4 osobne przyciski (sloty akcji), nad rombem **RB** (cel wrogi) i **RT**.
- **Prawa górna i środkowa część**: wolna strefa drag = kamera; tap = cel kliknięciem; long-press = interakcja; pinch = zoom.
- **Górny pasek** (mały): ☰ menu aplikacji, ⌨ czat, Start (menu gry), Back (zmiana fokusu ramek [Z:2]), L3 jako „centruj kamerę” (LB+RB+L3 w grze to akord, więc oddajemy sam L3, a resztę robi gracz [?]).
- Przezroczystość 55%, glyphy jak w grze, bo „Show Input Prompts” w grze pokazuje przyciski Xbox [Z:3].
- Wskazówka w onboardingu: w grze progi Back Pedal i Stick Run ustawić na ~0.3 [Z:3] (lepsza kontrola na dotykowym joysticku).

### 7.2 Domyślny układ „MMO – Klawiatura”
- Joystick daje WASD (8 kierunków, próg 0.35), Q/E strafe jako przyciski.
- Prawy klaster 3×4: 1–0, -, = (12 przycisków). Spacja (skok), Tab (cel), Esc, M, B, ⌨.
- Drag po wolnej strefie to RMB-look, tap to LMB na punkcie, pinch to kółko.

---

## 8. Etapy, pracochłonność, kryteria ukończenia

Szacunek dla 1 doświadczonego dewelopera Androida w pełnym wymiarze (×2–2,5 przy pracy po godzinach) [W].

| Etap | Zakres | Czas | Kryteria ukończenia |
|---|---|---|---|
| **E0. Fundament** | Fork, rename (`applicationId`, nazwa, ikona), build lokalny z NDK i submodułem, CI (build + testy + artefakt), szkielet modułów, `InputSink` z implementacją fake | 3–5 dni | Zielony CI; APK instaluje się obok oryginalnego Moonlight; streaming pulpitu działa jak w upstream |
| **E1. MVP** | Overlay (1 View, multitouch 10 pkt), joystick, 12 przycisków, PadSink + KeySink + MouseSink, drag kamery, tap-target, pinch-zoom, ActionPolicy + testy właściwości, 2 presety JSON (bez edytora; ręczna edycja pliku), test T-03 | 4–6 tyg. | Przejście pętli „zadanie questowe + walka z 3 mobami + loot” w WoW: Forever na 2 telefonach bez dotykania PC; p95 opóźnienia event→send < 4 ms; 0 naruszeń polityki w 10⁵ losowych sekwencji |
| **E2. Beta** | Edytor Compose (drag, scale, opacity, właściwości, undo), profile per klasa i postać, import/eksport SAF, leworęczność, haptyka, krzywe z podglądem, auto-hide przy padzie BT, menu w grze | 4–6 tyg. | 5 testerów tworzy własny układ w < 5 min bez instrukcji; import/eksport round-trip bez strat (test); crash-free sessions > 99% |
| **E3. 1.0** | Onboarding i diagnostyka hosta, ekran Tailscale, fair-play, i18n PL/EN, dostępność (TalkBack w menu, kontrast), F-Droid metadata (fastlane), podpisane releasy, rejestracja w Android Developer Console | 3–4 tyg. | Release na GitHub i zgłoszenie do F-Droid zaakceptowane; checklisty testów z sekcji 9 zaliczone; dokumentacja użytkownika |
| **E4. Play** | Listing Play (polityki, Data safety, zrzuty bez assetów Blizzarda), track zamknięty → produkcyjny | 1–2 tyg. + review | Aplikacja opublikowana; brak odrzuceń za IP/Device Abuse |
| **E5. Rozszerzenia** | Żyroskop jako kamera (prawa gałka, opcjonalnie), układy na tablety i foldy, udostępnianie przez QR, integracja z wirtualnym ekranem Apollo, presety dla innych gier, tryb „kontroler na telefonie, obraz na TV” | ciągłe | Każda funkcja z testem polityki i flagą feature |

Łącznie do 1.0: ok. **12–17 tygodni** pełnego etatu.

---

## 9. Plan testów i metryki

**Urządzenia (minimum)**
- Flagowiec z ekranem 120 Hz (np. Pixel serii 9/10 lub Galaxy S2x);
- średniak z Androidem 12–13 (np. Galaxy A5x);
- starszy z API 26–28 (granica minSdk);
- jeden z wycięciem w ekranie i agresywnym gestem „wstecz” (Xiaomi/HyperOS);
- tablet 11";
- pady: Xbox Wireless (BT), DualSense (BT), tani generyk.

**Hosty**
- Windows 11 + Sunshine stable + (a) Virtual HID z licencją, (b) ViGEmBus x360, (c) bez sterownika pada (tylko KBM);
- macOS + Sunshine (KBM; pady pre-release [?]).

**Pomiary opóźnień**
- Event→send w aplikacji (logowane, histogram).
- Statystyki Moonlight (`PerfOverlayListener`: decode, sieć, utracone ramki).
- **Touch-to-photon**: kamera 240 fps nagrywa jednocześnie palec i ekran telefonu. Postać skacze (A), mierzymy klatki od kontaktu do ruchu na ekranie. Cel: LAN 5 GHz ≤ 60 ms p50, Tailscale direct (LTE/5G) ≤ 100 ms p50 [W].

**Scenariusze w grze (WoW: Forever)**
- T-01 ruch analogowy: chód/bieg (progi), backpedal, strafe; brak „zawieszonej gałki” po oderwaniu palca przy utracie pakietów.
- T-02 kamera: obrót 360° jednym przeciągnięciem, precyzja małych ruchów, brak dryfu.
- **T-03 hybryda: tap-target i pinch-zoom przy włączonym Gamepad UI.** Czy HUD przełącza się na KBM, czy wraca do pada po użyciu gałki? Wynik decyduje o Pad+ albo Pad-czysty.
- T-04 walka: 3 umiejętności z dwóch zestawów (LT/RT) w rotacji, rzucanie w ruchu.
- T-05 interakcje: loot (long-press i X), rozmowa z NPC, sprzedaż u vendora, plecak (LB+RB+B).
- T-06 czat: klawiatura ekranowa i `sendUtf8Text` z polskimi znakami.
- T-07 pad BT: podłączenie w trakcie sesji (auto-hide), rozłączenie (powrót overlaya), równoczesny pad i dotyk.
- T-08 odporność: rozmowa telefoniczna, obrót ekranu, przejście Wi-Fi→LTE (reconnect upstreamu), ekran zablokowany; wszystkie wejścia zwolnione.
- T-09 polityka: fuzzing wejścia (test właściwości) i ręczny przegląd presetów.

**Metryki jakości**
- p50/p95 event→send;
- touch-to-photon;
- odsetek „ghost input” (wejścia bez dotyku) = 0;
- „stuck input” po CANCEL = 0;
- crash-free sessions ≥ 99,5% (1.0);
- czas stworzenia układu < 5 min;
- ocena SUS edytora ≥ 75;
- zużycie baterii: < 15%/h ponad samo dekodowanie wideo [W].

---

## 10. Połączenie poza domem (Tailscale)

- Tailscale na PC (usługa, start z systemem) i na telefonie. W aplikacji dodajemy host ręcznie po IP `100.x` lub nazwie MagicDNS (upstream wspiera ręczne dodawanie i parowanie [Z:25]). **Parujemy w domu, w LAN**, co zaleca dokumentacja Moonlight [Z:25].
- Typ połączenia musi być **direct**; DERP jest wolny i z niskim throughputem [Z:26]. Dokumentacja Moonlight podaje wręcz, że stream przez sam relay zawodzi [Z:25]. Sprawdzenie: `tailscale ping <pc>` / `tailscale status` [Z:26].
- Poprawa szans na direct: zezwolić na UDP (wychodzące i powroty), włączyć UPnP/NAT-PMP na routerze lub przekierować UDP 41641 na PC [Z:26]. Na CGNAT operatora komórkowego direct zwykle działa, jeśli dom ma „łatwy” NAT [W]. Przy „hard NAT” po obu stronach rozważyć peer relay w tailnecie (np. domowy serwer Debian użytkownika jako relay) [Z:26][W].
- Nie otwierać portów Sunshine do internetu (TCP 47984/47989/48010, UDP 47998–48010 [Z:25]). Zostawić `upnp` wyłączone (domyślnie) [Z:9] i `origin_web_ui_allowed = lan` [Z:9]. [?] Sprawdzić, czy adresy 100.64/10 Sunshine traktuje jako LAN czy WAN (wpływa na szyfrowanie i dostęp do web UI).
- Szyfrowanie: `wan_encryption_mode = 1` domyślnie [Z:9]; WireGuard i tak szyfruje.
- Ustawienia strumienia na LTE/5G: 720p lub 1080p@60, 10–20 Mb/s, HEVC/AV1 jeśli dekoder telefonu wspiera [W]. Ograniczenie MTU w tunelu WireGuard może wymagać mniejszych pakietów [?].
- W aplikacji: ekran „Poza domem” z testem ping i jitter oraz komunikatem „połączenie przez relay, obniż bitrate”. Wykrywanie relay [?] jest możliwe tylko heurystycznie po RTT; nie mamy API Tailscale na Androidzie bez integracji z ich aplikacją.

---

## 11. Licencje, dystrybucja, nazewnictwo

**Licencjonowanie**
- Całość jest na **GPLv3-or-later** (upstream i `moonlight-common-c` są na GPLv3 [Z:6][Z:27]). Nasz kod też wychodzi na GPLv3. Repo publiczne od pierwszego dnia, tagi release odpowiadają źródłom.
- Plik NOTICE: Moonlight (i Artemis, jeśli coś przejmiemy), biblioteki (bouncycastle MIT-like, okhttp Apache-2.0, jmdns Apache-2.0).
- Kontrybucje: DCO (Signed-off-by), bez CLA, bo i tak nie można relicencjonować GPL kodu upstreamu.
- Ikony i grafiki: własne, na CC BY-SA 4.0 albo w ramach GPL.

**Kanały (w kolejności)**
1. **GitHub Releases**: podpisane APK, SHA-256, changelog; opcjonalnie Obtainium. Uwaga: weryfikacja deweloperów Androida od **30.09.2026** w Brazylii, Indonezji, Singapurze i Tajlandii, globalnie od 2027. Istnieje „advanced flow” dla power userów i konta „limited distribution” do 20 urządzeń bez opłaty i ID [Z:20]. Rejestrujemy aplikację w **Android Developer Console** (jest też ścieżka dla aplikacji open source [Z:20]).
2. **F-Droid**: brak GMS i Firebase, budowalne ze źródeł, metadane fastlane (upstream ma już `fastlane/metadata/android` [Z:6]). Moonlight jest w F-Droid jako `com.limelight` (GPL-3.0-or-later) [Z:28], co potwierdza wykonalność. Własne unikalne `applicationId`, np. `io.github.<login>.thumbcast` (F-Droid odrzuca ID podszywające się pod cudzą domenę [Z:19]).
3. **Google Play**:
   - targetSdk 36 [Z:21];
   - formularz Data safety: brak zbierania danych; jedyne połączenia to własny host i Tailscale po stronie systemu;
   - polityka Device and Network Abuse zakazuje m.in. „game cheating apps that affect the gameplay of other apps” i używania usług niezgodnie z ich ToS [Z:29]. **Pozycjonujemy aplikację jako uniwersalny klient streamingu z konfigurowalnym sterowaniem dotykowym**: presety „MMO”, „Akcja 3D”, bez nazwy gry w listingu i bez zrzutów z gry Blizzarda;
   - foreground service tylko jeśli konieczny (upstream go nie wymaga do streamingu [?]);
   - brak samoaktualizacji poza Play [Z:29].
- Ryzyko „game cheating apps” oceniam jako niskie przy mapowaniu 1:1 i braku analizy obrazu [W].

**Znaki towarowe**
- Bez „WoW”, „Warcraft”, „Azeroth”, „Blizzard”, „Hearth-” (Hearthstone), ikon klas i fontów gry.
- Nazwa bez „Moonlight”, żeby nie mylić z oficjalnym projektem. W opisie wolno napisać „bazuje na Moonlight (GPLv3)”.
- Wymienianie gry w opisie jako „zgodne z grami z natywną obsługą pada, np. …” to nominative use w README, nie w nazwie ani ikonie [W].

**Propozycje nazw (neutralne)**
1. **Thumbcast**: kciuk + stream; krótka i wolna od skojarzeń z grą.
2. **Kciukomat**: polska, zabawna, dobra na rynek PL, gorsza globalnie.
3. **Tactyl Stream**: dotyk i taktyka.
4. **Overthumb**
5. **PadLoom**: „tkany” układ przycisków, nawiązanie do edytora.
6. **Runepad** [?]: „rune” jest ogólne, ale kojarzy się z fantasy MMO; sprawdzić kolizje.

Przed wyborem sprawdzić EUIPO/USPTO, Google Play i GitHub pod kątem kolizji.

---

## 12. Ryzyka i otwarte pytania

| # | Ryzyko / pytanie | Typ | Prawd. | Wpływ | Rekomendacja |
|---|---|---|---|---|---|
| R1 | Blizzard uzna aplikację lub jej użycie za naruszenie (1.C.ii.4) | prawne | niskie–średnie | wysoki (bany użytkowników) | Twarde zabezpieczenia z sekcji 6; domyślny tryb przez natywne wejście pada; disclaimer; zero analizy obrazu; nie reklamować jako „WoW app”. Można zapytać Blizzard Support, choć zwykle odmawiają oceny [Z:15] |
| R2 | Pady na Windows wymagają płatnej licencji Virtual HID; ViGEmBus jest EOL i może przestać działać w przyszłych Windows lub Sunshine | techniczne/koszt | wysokie | średni | Tryb KBM jako pełnoprawna alternatywa; diagnostyka w onboardingu; monitorować release notes Sunshine |
| R3 | Mieszanie myszy i pada przełącza HUD gry (T-03) | techniczne | średnie | średni | Wariant Pad-czysty; test w E1 przed inwestycją w gesty |
| R4 | Gamepad w grze ma status „Alpha” [Z:3][Z:4], więc mapowania i zachowanie mogą się zmieniać | techniczne | wysokie | niski–średni | Profile jako dane (JSON), szybkie aktualizacje presetów bez nowej wersji aplikacji (import) |
| R5 | Rebase na upstream (zmiany w `Game.java`) | utrzymaniowe | średnie | średni | Minimalne hooki w upstreamie (≤ 50 linii), cała logika w modułach; comiesięczny rebase w CI (job „upstream-sync”) |
| R6 | Weryfikacja deweloperów Androida utrudni sideload od 2027 | dystrybucja | wysokie | średni | Rejestracja w Developer Console; F-Droid i Play jako główne kanały |
| R7 | Odrzucenie w Google Play (IP / Device Abuse) | dystrybucja | niskie | średni | Neutralny listing; zrzuty z otwartych gier lub pulpitu |
| R8 | Opóźnienia przez Tailscale relay / sieć komórkowa | techniczne | średnie | wysoki dla UX | Ekran diagnostyki, zalecenia direct; bitrate adaptacyjny w ustawieniach |
| R9 | Gesty systemowe Androida (wstecz, pasek) kradną dotyk przy krawędzi | UX | wysokie | średni | Immersive, wykluczenia gestów, kontrolki ≥ 24 dp od krawędzi |
| R10 | Host macOS: pady niewspierane w stabilnym Sunshine | techniczne | wysokie | niski | Tryb KBM dla macOS |
| Q1 | Czy WoW: Forever rozpoznaje wirtualnego DS4/DualSense? | otwarte | – | – | Test; domyślnie Xbox |
| Q2 | Czy WoW korzysta z Raw Input przy obrocie myszą (licencja wymagana dla „Raw Input-visible mice” [Z:12])? | otwarte | – | – | Test KBM bez licencji: RMB-look |
| Q3 | Własny numer kontrolera czy kontekst 0 (`reportOscState`)? | otwarte | – | – | MVP: kontekst 0; zbadać, czy gra lubi 2 pady |
| Q4 | Czy akordy (Shift+1, LB+RB) mieszczą się w „one key press = one action”? | prawne | – | – | Domyślnie wyłączone; włączane świadomie z ostrzeżeniem |

---

## 13. Pierwsze 10 kroków na jutro

1. Utworzyć publiczne repo GitHub (GPLv3), sforkować `moonlight-stream/moonlight-android`, `git submodule update --init --recursive`.
2. Zbudować upstream lokalnie (Android Studio, NDK 29.0.14206865, `local.properties` z `ndk.dir`) i przetestować stream pulpitu z Sunshine w LAN.
3. Na PC: zaktualizować Sunshine do v2026.914+, w web UI sprawdzić `gamepad_driver`. Zainstalować ViGEmBus z Troubleshooting **albo** licencję Virtual HID; ustawić `gamepad = x360`.
4. W WoW: Forever włączyć „Enable Gamepad UI (Alpha)” i „Show Input Prompts”. Przetestować oryginalny OSC Moonlight: czy gra reaguje na pad z telefonu?
5. **Wykonać ręcznie test T-03**: przy grze na OSC dotknąć ekranu (tryb trackpad i mysz) i przewinąć. Czy HUD przełącza się z pada? Zanotować wynik, bo od niego zależy tryb domyślny.
6. Zmienić `applicationId`, nazwę i ikonę (nazwa robocza Thumbcast); dodać flavor `thumbcast`; upewnić się, że APK instaluje się obok Moonlight.
7. Dodać workflow GitHub Actions: checkout z submodułami, setup JDK 17 + NDK, `./gradlew assembleDebug test lint`, upload APK.
8. Utworzyć moduły `:control-model` i `:control-engine` (Kotlin 2.4.21). Zdefiniować schemat profilu v1 (kotlinx.serialization) i pierwszy test właściwości „1 DOWN → ≤ 1 press”.
9. W `Game.java` dodać hook: preferencja `control_layer`, utworzenie pustego `ThumbcastOverlay` nad `StreamView` i przekierowanie palców do niego. Najpierw jeden przycisk „A” przez `reportOscState`.
10. Zainstalować Tailscale na PC i telefonie, dodać host po IP `100.x`, sprawdzić `tailscale ping` (direct czy DERP) i zmierzyć opóźnienie na LTE. Wynik zapisać w `docs/tests/`.

---

## Źródła

1. [Z:1] World of Warcraft: Forever (warcraft.wiki.gg): data premiery, platformy. https://warcraft.wiki.gg/wiki/Forever
2. [Z:2] Blizzard: Explore WoW: Forever with a Gamepad. https://worldofwarcraft.blizzard.com/news/24316974/ (także https://news.blizzard.com/article/24316974/wow)
3. [Z:3] Warcraft Tavern: Controller Guide (Gamepad UI, Settings, Targeting & Hotbars). https://www.warcrafttavern.com/forever/guides/controller/
4. [Z:4] FRVR: controller support covers all major gameplay and core UI (3.11.2026). https://frvr.com/blog/news/world-of-warcraft-forevers-controller-support-now-covers-all-major-gameplay-and-core-ui-as-blizzard-continues-to-improve-gamepad-controls/ ; Beebom: https://beebom.com/world-of-warcraft-forever-controller-support-breakdown-revealed/
5. [Z:5] Mein-MMO: WoW Forever mit Controller. https://mein-mmo.de/en/wow-playing-forever-with-controller-this-apparently-works-really-well,1587811/2/
6. [Z:6] moonlight-android (repo, README, LICENSE GPL-3.0): https://github.com/moonlight-stream/moonlight-android ; releases: https://github.com/moonlight-stream/moonlight-android/releases
   - [Z:6a] drzewo plików: https://api.github.com/repos/moonlight-stream/moonlight-android/git/trees/master?recursive=1
   - [Z:6b] `Game.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/Game.java
   - [Z:6c] `VirtualController.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualController.java
   - [Z:6d] `ControllerHandler.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/ControllerHandler.java
   - [Z:6e] `PreferenceConfiguration.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java
   - [Z:6f] `VirtualControllerConfigurationLoader.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerConfigurationLoader.java
   - [Z:6g] `VirtualControllerElement.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerElement.java
   - [Z:6h] `AnalogStick.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/virtual_controller/AnalogStick.java
   - [Z:6i] `NvConnection.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/nvstream/NvConnection.java
   - [Z:6j] `Limelight.h` (moonlight-common-c): https://raw.githubusercontent.com/moonlight-stream/moonlight-common-c/master/src/Limelight.h
   - [Z:6k] `ControllerPacket.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/nvstream/input/ControllerPacket.java
   - [Z:6l] `MoonBridge.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/nvstream/jni/MoonBridge.java
   - [Z:6m] `RelativeTouchContext.java`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/java/com/limelight/binding/input/touch/RelativeTouchContext.java
   - `activity_game.xml`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/src/main/res/layout/activity_game.xml ; `.gitmodules`: https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/.gitmodules
7. [Z:7] `app/build.gradle`, `build.gradle`, gradle-wrapper (upstream): https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/build.gradle ; https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/build.gradle ; https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/gradle/wrapper/gradle-wrapper.properties
8. [Z:8] Artemis (fork moonlight-android): https://github.com/ClassicOldSong/moonlight-android
9. [Z:9] Sunshine: dokumentacja konfiguracji: https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html
10. [Z:10] Sunshine releases: https://github.com/LizardByte/Sunshine/releases
11. [Z:11] Sunshine v2026.906.222525 (migracja na libvirtualhid, licencja Virtual HID): https://github.com/LizardByte/Sunshine/releases/tag/v2026.906.222525 ; libvirtualhid release: https://github.com/LizardByte/libvirtualhid/releases/tag/v2026.829.2338.54 ; PR WinUHid (zamknięty): https://github.com/LizardByte/Sunshine/pull/4948
12. [Z:12] libvirtualhid: przegląd, licencje, wsparcie platform, sterownik Windows: https://docs.lizardbyte.dev/projects/libvirtualhid ; https://docs.lizardbyte.dev/projects/libvirtualhid/latest/md_docs_2platform-support.html ; https://docs.lizardbyte.dev/projects/libvirtualhid/latest/md_LICENSES_2README.html ; https://docs.lizardbyte.dev/projects/libvirtualhid/latest/md_docs_2windows-driver.html
13. [Z:13] ViGEmBus (retired, archived): https://github.com/nefarius/ViGEmBus
14. [Z:14] Blizzard EULA: https://www.blizzard.com/en-us/legal/fba4d00f-c7e4-4883-b8b9-1b4500a402ea/blizzard-end-user-license-agreement
15. [Z:15] Kami Labs: multiboxing w WoW Forever, stanowisko Blizzarda (cytaty 2020–2026): https://kami-labs.fr/en/wow-forever/multiboxing-wow-forever-interdit/
16. [Z:16] Blizzard: Policy Update for Input Broadcasting Software (3.11.2020): https://us.forums.blizzard.com/en/wow/t/policy-update-for-input-broadcasting-software/707426
17. [Z:17] Apollo (fork Sunshine): https://github.com/ClassicOldSong/Apollo
18. [Z:18] Old Timers Guild: WoW Bans & ConsolePort/WoWMapper (2021, niepotwierdzone): https://forums.oldtimersguild.com/t/wow-bans-consoleport-wowmapping/32780 ; wątek o padach na forum Blizzarda (głosy graczy, nie staff): https://us.forums.blizzard.com/en/wow/t/ask-using-xbox360-controller-to-play-wow-is-it-allowed/396828
19. [Z:19] F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
20. [Z:20] Android developer verification: https://developer.android.com/developer-verification ; F-Droid open letter: https://f-droid.org/is/2026/02/24/open-letter-opposing-developer-verification.html
21. [Z:21] Google Play target API level: https://developer.android.com/google/play/requirements/target-sdk
22. [Z:22] Kotlin releases: https://kotlinlang.org/docs/releases.html
23. [Z:23] Android Gradle Plugin release notes: https://developer.android.com/build/releases/gradle-plugin
24. [Z:24] Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping
25. [Z:25] Moonlight Setup Guide (porty, VPN, Tailscale, parowanie): https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide
26. [Z:26] Tailscale: connection types (direct/DERP/peer relay): https://tailscale.com/kb/1257/connection-types
27. [Z:27] moonlight-common-c: https://github.com/moonlight-stream/moonlight-common-c
28. [Z:28] F-Droid: Moonlight (com.limelight): https://f-droid.org/packages/com.limelight/
29. [Z:29] Google Play: Device and Network Abuse policy: https://support.google.com/googleplay/android-developer/answer/9888379?hl=en
