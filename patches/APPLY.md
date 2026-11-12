# Jak nałożyć patche Thumbcast na świeży fork moonlight-android v12.2

> **Stan 11.11.2026: nałożone** w tym repo (gałąź `thumbcast/hooks`, commity `thumbcast: ...`). Źródłem prawdy jest teraz kod w `app/`
> (bloki `THUMBCAST`, `git grep -n THUMBCAST -- app/`) i pewny diff `patches/0001-thumbcast.diff` (`git diff v12.2 -- app/`).
> Kopie plików Java z `patches/app/` usunięte (są w `app/src/main/java/com/limelight/thumbcast/`). Opisy `*.patch.md` zostają jako
> instrukcja ponownego nałożenia przy konflikcie z upstreamem. Różnice względem opisów:
> - `assets.srcDir` w `app/build.gradle` dostaje `File` (`layout.buildDirectory.dir(...).get().asFile`), bo AGP 9.4 odrzuca `Provider`
>   („You cannot add Provider instances to the Android SourceSet API”).
> - Nazwy profili w `thumbcast_profile_names` to zasoby `@string/thumbcast_profile_*` (EN „MMO – Keyboard”, PL „MMO – Klawiatura”).
> - `onThumbcastAppAction` używa stałych `AppActions.*` z `:control-model` zamiast literałów.
> - Zmiany w `Game.java` (od 11.11.2026, `ThumbcastSession`): 34 dodane linie w 9 blokach (import, pole, utworzenie sesji,
>   PiP ×2, `onDestroy`, `onBackPressed`, palce w `handleMotionEvent`, `stopConnection`), 1 zmieniona linia upstreamu
>   (warunek bloku VirtualController). Wcześniej ~95 linii z dwiema metodami. Logika jest w `com.limelight.thumbcast.ThumbcastSession`.
> - Menu strumienia (☰, gest od górnej krawędzi, systemowy Back): Rozłącz, ukryj/pokaż przyciski, klawiatura, rozmiar kontrolek
>   (na żywo), ukrywanie przy podłączonym padzie. Ustawienia: `ThumbcastSettings` (klucze `list_thumbcast_control_scale`,
>   `checkbox_thumbcast_hide_on_gamepad`), bez zmian w `PreferenceConfiguration`.
> - Testy ręczne z kroku 6 (stream, overlay, A w „Kontrolery gier”) **nie zrobione**: brak urządzenia i hosta w tej sesji.

Ten katalog zawiera **opis** zmian w upstreamie (`*.patch.md`) i **nowe pliki** do skopiowania
(`app/src/main/java/com/limelight/thumbcast/*.java`). Nie ma tu kodu upstreamu.

**Stan weryfikacji (10.11.2026):** opisy i pliki Java zostały porównane z prawdziwym kodem upstreamu
**moonlight-stream/moonlight-android, tag v12.2 = commit `b48494cb96bff23d8886c4775cc4f39a1075495d`**
(„Version 12.2”, 12.09.2026; w dniu sprawdzenia to też HEAD `master`) oraz z `moonlight-common-c` (`master`,
`src/Limelight.h`; submoduł upstreamu może wskazywać inny commit, ale te stałe są częścią stabilnego protokołu).
Odczyt szedł przez narzędzie, które zwraca tylko streszczenia i krótkie cytaty, więc **nie ma gotowych diffów**
i nic nie było kompilowane. Opisy zawierają dosłowne kotwice (linie do znalezienia w pliku). Po nałożeniu zapisz
własny, pewny diff: `git diff v12.2 -- app/ > patches/0001-thumbcast.diff`.

## Zawartość

| Plik | Co robi |
|---|---|
| `Game.java.patch.md` | przełącznik `control_layer`, overlay jako ostatnie dziecko `FrameLayout`, pominięcie `touchContextMap` dla palców, akcje aplikacji, sprzątanie |
| `preferences.patch.md` | nowe preferencje (`list_control_layer`, `list_thumbcast_profile`, `checkbox_thumbcast_debug`), `PreferenceConfiguration`, `StreamSettings`, stringi, kopiowanie presetów do assetów |
| `manifest-and-appid.patch.md` | `applicationId`, minSdk 26, zależność `:control-android`, manifest, nazwa, ikona |
| `host-addresses.patch.md` | jeden wpis PC dla LAN i Tailscale/ZeroTier: łączenie adresów, kolejność odpytywania, migracja bazy, dialog „Adresy połączenia” (12.11.2026) |
| `app-settings.patch.md` | ustawienia Thumbcast dla aplikacji hosta: profil, rozdzielczość i FPS na aplikację (menu aplikacji w `AppView`, hak w `Game.onCreate`), fallback profilu (12.11.2026) |
| `app/src/main/java/com/limelight/thumbcast/HostConnectionImpl.java` | fasada `HostConnection` -> `NvConnection` (delegacja 1:1) |
| `app/src/main/java/com/limelight/thumbcast/HostPadReporterImpl.java` | fasada `HostPadReporter` -> `ControllerHandler.reportOscState` + retransmisja 25/50/75 ms tego samego stanu |
| `app/src/main/java/com/limelight/thumbcast/ThumbcastKeyTranslator.java` | `KeyTranslator` -> upstreamowy `KeyboardTranslator` |
| `app/src/main/java/com/limelight/thumbcast/ThumbcastSession.java` | cała logika Thumbcast w `Game`: profil, skala, overlay, akcje aplikacji, menu, pad, PiP, sprzątanie |
| `app/src/main/java/com/limelight/thumbcast/ThumbcastSettings.java` | rozmiar kontrolek i ukrywanie przy padzie (SharedPreferences, też z menu w grze) |

## Kolejność kroków

1. **Fork i checkout tagu.** Forkujemy `moonlight-stream/moonlight-android`, tworzymy gałąź
   `thumbcast/main` od tagu v12.2 (`git checkout -b thumbcast/main v12.2`), inicjalizujemy submoduły
   (`git submodule update --init --recursive`; jedyny submoduł to `moonlight-common-c` w ścieżce
   `app/src/main/jni/moonlight-core/moonlight-common-c`, wg `.gitmodules` v12.2).
   Sprawdzamy, że czysty upstream buduje się lokalnie (NDK 29.0.14206865, plan 1.3) **zanim** cokolwiek zmienimy.
2. **Struktura buildu.** Dokładamy katalogi `control-model/`, `control-engine/`, `control-android/`,
   `presets/`, `gradle/libs.versions.toml` z repo Thumbcast (wariant „scalenie” z `settings.gradle.kts`
   SCAFFOLD). Upstreamowy `settings.gradle` (Groovy) zastępujemy `settings.gradle.kts` i odkomentowujemy
   `include(":app")`. Build musi przejść **bez** zmian w `:app` (moduły są jeszcze nieużywane).
3. **`applicationId` i zależność** wg `manifest-and-appid.patch.md` (uwaga: id ustawia się we flavorze `nonRoot`, flavor
   `root` usuwamy z powodu `minSdk 26`). Build + instalacja obok oryginału.
4. **Nowe pliki Java.** Kopiujemy `patches/app/src/main/java/com/limelight/thumbcast/` do
   `app/src/main/java/com/limelight/thumbcast/`. Sygnatury `NvConnection.send*`, `ControllerHandler.reportOscState`
   i `KeyboardTranslator.translate(int, int)` zostały porównane z v12.2 (patrz niżej); kompilator i tak
   jest ostatecznym sędzią. Ewentualne rozjazdy poprawiamy w tych trzech plikach, nie w fasadach w `:control-android`
   (jeśli upstream ma inną sygnaturę, poprawiamy fasadę **i** wpisujemy to do `CONTRACT-NOTES.md`).
5. **Preferencje** wg `preferences.patch.md` (najpierw zasoby, potem `PreferenceConfiguration`, potem
   `StreamSettings`). Build.
6. **`PreferenceConfiguration`: wymuszenie `onscreenController = true`** w trybie Thumbcast (`preferences.patch.md`, pkt 4)
   MUSI być zrobione przed testem na hoście: bez niego bit 0 maski pada nie jest ustawiany i host może nie widzieć pada.
   **`Game.java`** wg `Game.java.patch.md`, sekcjami 1–8 po kolei. Po sekcji 3 i 5 test ręczny:
   strumień pulpitu, overlay widoczny, A z profilu Pad+ widoczne w „Kontrolery gier” Windows.
7. **Testy**: `:control-model:test`, `:control-engine:test`, `:control-android:testDebugUnitTest`,
   na urządzeniu `:control-android:connectedDebugAndroidTest` (po dodaniu aliasów androidx.test).
8. **Commit** każdego kroku osobno (`thumbcast: <krok>`), żeby rebase rozwiązywał konflikty małymi porcjami.

## Na co uważać przy rebase na nowy upstream

- Wszystkie zmiany w plikach upstreamu są w blokach `// THUMBCAST:` … `// THUMBCAST END`
  (w XML: `<!-- THUMBCAST: -->` … `<!-- THUMBCAST END -->`). Po rebase: `git grep -n "THUMBCAST"`
  i porównanie liczby bloków przed i po.
- **Nowe pliki trzymamy w osobnych plikach** (`thumbcast_strings.xml`, `thumbcast_arrays.xml`,
  pakiet `com.limelight.thumbcast`), żeby nie konfliktowały z upstreamem.
- `Game.onCreate()` jest najczęściej zmienianym plikiem upstreamu. Przy konflikcie: przyjąć wersję
  upstreamu i nałożyć bloki THUMBCAST od nowa wg `Game.java.patch.md` (opis jest po to, żeby nie
  rozwiązywać diffu „na ślepo”).
- Zmiana sygnatur `NvConnection.send*`, `ControllerHandler.reportOscState` lub `KeyboardTranslator`
  w upstreamie psuje tylko trzy pliki z `com.limelight.thumbcast` (kompilacja to wykryje).
- Zmiana `touchContextMap` / `handleMotionEvent` (np. upstream doda natywne `sendTouchEvent`, dziś
  zakomentowane wg planu 2.2): sprawdzić, czy nasz warunek „palec + overlay aktywny -> return true” jest
  nadal przed wszystkimi gałęziami, które wysyłają dotyk do hosta.
- AGP/Gradle: trzymamy wersje upstreamu (plan 3); podbijamy razem z rebase, w version catalog.
- Retransmisja w `HostPadReporterImpl` (potwierdzona w `VirtualController`): jeśli upstream przeniesie retransmisję do `reportOscState`
  albo `ControllerHandler`, wyłączyć naszą (`new HostPadReporterImpl(controllerHandler, false)`),
  inaczej stany będą wysyłane podwójnie (nadal identyczne, więc bez skutków dla polityki, ale niepotrzebny ruch).

## Wyniki weryfikacji z upstreamem v12.2 (b48494cb96bf)

Legenda: ZGODNE = tak jak w planie/kodzie Thumbcast; POPRAWIONE = założenie było błędne i plik zmieniono; NIEPEWNE = nie dało się potwierdzić.

1. `NvConnection`: `sendKeyboardInput(short, byte, byte, byte)`, `sendMouseMove(short, short)`, `sendMousePosition(short, short, short, short)`,
   `sendMouseButtonDown(byte)`, `sendMouseButtonUp(byte)`, `sendMouseHighResScroll(short)`: **ZGODNE** (wszystkie `public void`, bez `throws`).
   Istnieją też `sendMouseScroll(byte)`, `sendMouseHScroll`, `sendMouseHighResHScroll`, `sendMouseMoveAsMousePosition`, `sendUtf8Text(String)`,
   `sendTouchEvent`, `sendPenEvent`, `sendControllerInput(short,short,int,byte,byte,short,short,short,short)`; nie używamy ich.
2. `ControllerHandler.reportOscState(int, short, short, short, short, byte, byte)`: **ZGODNE**, `public void`, pisze do `defaultContext`
   (kontroler nr 0, `inputMap = buttonFlags`, bez skalowania i deadzone) i woła `sendControllerInputPacket(defaultContext)`; brak blokad.
   Pakiet łączy się z padami fizycznymi o tym samym numerze (OR przycisków, `maxByMagnitude` osi).
3. Retransmisja 25/50/75 ms jest w `VirtualController.sendControllerInputContext()`, nie w `reportOscState`: **ZGODNE** (komentarz upstreamu:
   „GFE sometimes discards gamepad packets”). `HostPadReporterImpl` zostaje.
4. `Game.onCreate()`: pola `prefConfig`, `controllerHandler`, `conn`, `streamView`, `keyboardTranslator` istnieją i są ustawione przed blokiem
   VirtualController: **ZGODNE**. Rodzic `StreamView` to FrameLayout (korzeń `activity_game.xml` to `<merge>`): **ZGODNE**.
5. Routing dotyku (`backgroundTouchView` jako zmienna LOKALNA, `onTouch` → `handleMotionEvent`, palce do `touchContextMap`
   `TouchContext[2]`, trzy palce → klawiatura): **ZGODNE**, z drobnym uściśleniem: palec to końcowa gałąź `else`, nie ma jawnego `TOOL_TYPE_FINGER`.
   Miejsce wstawki wskazane w `Game.java.patch.md`, pkt 4.
6. Referencja dotyku dla `sendMousePosition`: upstreamowy `AbsoluteTouchContext` przekazuje `targetView.getWidth()/getHeight()` (rozmiar widoku),
   a nie 1280×720: **POPRAWIONE** (założenie 6 z planu było mylące). `RelativeTouchContext` używa `REFERENCE_HORIZ_RES/VERT_RES` (stałe w `Game`).
   Dla nas oba warianty są równoważne; patch podaje rozdzielczość strumienia.
7. `StreamView.setDesiredAspectRatio` + `onMeasure` ustalają rozmiar widoku (wołane w `prepareDisplayForRendering()`), `layout_gravity=center`
   w `<merge>`: rozmiar `StreamView` = prostokąt wideo: **ZGODNE**. Patch używa `OnLayoutChangeListener`.
8. `ControllerHandler.onInputDeviceAdded`: klasa implementuje `InputManager.InputDeviceListener`; treści `onInputDeviceAdded` nie czytałem:
   **NIEPEWNE** (nasz listener w overlayu jest niezależny, więc to bez znaczenia dla poprawności).
9. Klucze preferencji: `checkbox_show_onscreen_controls`, `checkbox_vibrate_osc`, `checkbox_only_show_L3R3`, `checkbox_show_guide_button`,
   `seekbar_osc_opacity`, kategoria `category_onscreen_controls`: **ZGODNE**. API preferencji to **`android.preference.*`** (stare, `PreferenceFragment`),
   `readPreferences(Context)` jest statyczne: **POPRAWIONE** (w `preferences.patch.md`). Nazwa metody w `StreamSettings`: `SettingsFragment.onCreate`.
10. `KeyboardTranslator.translate`: **POPRAWIONE**. Jest instancyjna z **dwoma** parametrami: `public short translate(int keycode, int deviceId)`;
    `ThumbcastKeyTranslator` wołał trzyargumentową (`translate(keycode, 0, -1)`), która nie istnieje. Teraz `translate(androidKeyCode, -1)`,
    tak jak upstreamowe `Game.keyboardEvent(boolean, short)`. Pole `Game.keyboardTranslator` istnieje (`private KeyboardTranslator keyboardTranslator;`).
11. Stałe `HostProtocol`: **ZGODNE** z `KeyboardPacket` (KEY_DOWN 0x03, KEY_UP 0x04, MODIFIER_SHIFT/CTRL/ALT 0x01/0x02/0x04, META 0x08),
    `MouseButtonPacket` (LEFT 1, MIDDLE 2, RIGHT 3, X1 4, X2 5) i `Limelight.h`. Prefiks `0x80` w kodzie hosta: **ZGODNE**
    (`(KEY_PREFIX << 8) | vk`). Flagi `ControllerPacket` (A 0x1000 … SPECIAL_BUTTON_FLAG 0x0400, w C: `SPECIAL_FLAG`) zgodne z CONTRACT.md.
    Czwarty argument `sendKeyboardInput`: 0 dla znormalizowanych, `SS_KBE_FLAG_NON_NORMALIZED` (0x01) dla klawiatur z innym układem:
    nasze 0 jest poprawne dla `translate(code, -1)`.
12. `applicationId`: **POPRAWIONE**. Ustawiany we flavorach (`root`: `com.limelight.root`, `nonRoot`: `com.limelight`), nie w `defaultConfig`;
    `release` dodaje `.unofficial`, `debug` `.debug`; `minSdk 21`; flavor `root` ma `maxSdk 25` (kolizja z naszym minSdk 26). Provider ma
    `poster.${applicationId}`, brak `shortcuts.xml`. Szczegóły w `manifest-and-appid.patch.md`.
13. **Nowe (krytyczne):** bit 0 maski pada zależy od `prefConfig.onscreenController` (patrz pkt 6 kroków). Wymuszenie w `readPreferences`.
14. **NIEPEWNE:** pełne ciało `Game.stopConnection()`, kod `onGenericMotion`, `ShortcutHelper`, dokładne miejsca innych wystąpień
    `onscreenController` w `Game` poza pierwszymi 100 tys. znaków pliku (odczyt był obcięty). Sprawdzić `grep -n onscreenController`.
15. **NIEPEWNE:** zachowanie Sunshine/Apollo wobec pakietów pada z numerem 0 przy `multiController=false` (maska 1): bez zmian względem upstreamu.

## Otwarte pytania

- Landscape: odpowiedź z kodu: `setPreferredOrientationForCurrentDisplay()` wymusza `USER_LANDSCAPE` na ekranach nie-kwadratowych zawsze,
  a na „squarish” tylko przy `onscreenController`. Przy wymuszeniu flagi (pkt 13) zachowanie jest jak przy OSC. Decyzja produktowa
  (czy blokować) zostaje w `docs/DECISIONS.md`, pkt h.
- `onscreenController` nie wymuszamy na `false`, tylko na **`true`** (odwrotnie niż zakładało pytanie): inaczej maska pada nie ma bitu 0.
- Menu w grze: od 11.11.2026 w `ThumbcastSession` (w v12.2 `Game` nie ma własnego menu); od wieczora 11.11.2026 to widok
  `ThumbcastMenuView` nad overlayem w oknie aktywności, nie `AlertDialog` (patrz `Game.java.patch.md`, pkt 5). Back w trybie
  Thumbcast otwiera menu zamiast kończyć strumień, przy otwartym menu je zamyka; w trybie legacy bez zmian.
