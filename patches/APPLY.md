# Jak nałożyć patche Thumbcast na świeży fork moonlight-android v12.2

Ten katalog zawiera **opis** zmian w upstreamie (`*.patch.md`) i **nowe pliki** do skopiowania
(`app/src/main/java/com/limelight/thumbcast/*.java`). Nie ma tu kodu upstreamu: w sesji, w której
to powstało, repozytorium moonlight-android nie było dostępne (brak sieci). Wszystkie odwołania do
upstreamu pochodzą z planu (`plan-wow-forever-controller.md`, sekcje 2.1–2.3) i są oznaczone **[?]**
tam, gdzie wymagają sprawdzenia na kodzie.

## Zawartość

| Plik | Co robi |
|---|---|
| `Game.java.patch.md` | przełącznik `control_layer`, overlay jako ostatnie dziecko `FrameLayout`, pominięcie `touchContextMap` dla palców, akcje aplikacji, sprzątanie |
| `preferences.patch.md` | nowe preferencje (`list_control_layer`, `list_thumbcast_profile`, `checkbox_thumbcast_debug`), `PreferenceConfiguration`, `StreamSettings`, stringi, kopiowanie presetów do assetów |
| `manifest-and-appid.patch.md` | `applicationId`, minSdk 26, zależność `:control-android`, manifest, nazwa, ikona |
| `app/src/main/java/com/limelight/thumbcast/HostConnectionImpl.java` | fasada `HostConnection` -> `NvConnection` (delegacja 1:1) |
| `app/src/main/java/com/limelight/thumbcast/HostPadReporterImpl.java` | fasada `HostPadReporter` -> `ControllerHandler.reportOscState` + retransmisja 25/50/75 ms tego samego stanu |
| `app/src/main/java/com/limelight/thumbcast/ThumbcastKeyTranslator.java` | `KeyTranslator` -> upstreamowy `KeyboardTranslator` |

## Kolejność kroków

1. **Fork i checkout tagu.** Forkujemy `moonlight-stream/moonlight-android`, tworzymy gałąź
   `thumbcast/main` od tagu v12.2 (`git checkout -b thumbcast/main v12.2`), inicjalizujemy submoduły
   (`git submodule update --init --recursive`: `moonlight-common-c` w `app/src/main/jni/moonlight-core`).
   Sprawdzamy, że czysty upstream buduje się lokalnie (NDK 29.0.14206865, plan 1.3) **zanim** cokolwiek zmienimy.
2. **Struktura buildu.** Dokładamy katalogi `control-model/`, `control-engine/`, `control-android/`,
   `presets/`, `gradle/libs.versions.toml` z repo Thumbcast (wariant „scalenie” z `settings.gradle.kts`
   SCAFFOLD). Upstreamowy `settings.gradle` (Groovy) zastępujemy `settings.gradle.kts` i odkomentowujemy
   `include(":app")`. Build musi przejść **bez** zmian w `:app` (moduły są jeszcze nieużywane).
3. **`applicationId` i zależność** wg `manifest-and-appid.patch.md`. Build + instalacja obok oryginału.
4. **Nowe pliki Java.** Kopiujemy `patches/app/src/main/java/com/limelight/thumbcast/` do
   `app/src/main/java/com/limelight/thumbcast/`. Teraz kompilator pokaże rozjazdy sygnatur
   (`NvConnection`, `ControllerHandler.reportOscState`, `KeyboardTranslator.translate`): poprawiamy
   tylko te trzy pliki, nie fasady w `:control-android` (fasady mają odzwierciedlać upstream; jeśli
   upstream ma inną sygnaturę, poprawiamy fasadę **i** wpisujemy to do `CONTRACT-NOTES.md`).
5. **Preferencje** wg `preferences.patch.md` (najpierw zasoby, potem `PreferenceConfiguration`, potem
   `StreamSettings`). Build.
6. **`Game.java`** wg `Game.java.patch.md`, sekcjami 1–8 po kolei. Po sekcji 3 i 5 test ręczny:
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
- Retransmisja w `HostPadReporterImpl`: jeśli upstream przeniesie retransmisję do `reportOscState`
  albo `ControllerHandler`, wyłączyć naszą (`new HostPadReporterImpl(controllerHandler, false)`),
  inaczej stany będą wysyłane podwójnie (nadal identyczne, więc bez skutków dla polityki, ale niepotrzebny ruch).

## Założenia wynikające WYŁĄCZNIE z planu (nie z kodu, który widziałem)

Żadnego pliku upstreamu nie widziałem w tej sesji. Wszystko poniżej trzeba potwierdzić przy kroku 4–6.

1. Sygnatury `NvConnection`: `sendKeyboardInput(short, byte, byte, byte)`, `sendMouseMove(short, short)`,
   `sendMousePosition(short, short, short, short)`, `sendMouseButtonDown(byte)`, `sendMouseButtonUp(byte)`,
   `sendMouseHighResScroll(short)` (plan 2.2 [Z:6i]).
2. `ControllerHandler.reportOscState(int, short, short, short, short, byte, byte)` jest publiczne
   i zapisuje do kontekstu 0 (plan 2.2 [Z:6d]).
3. Retransmisja 25/50/75 ms jest w `VirtualController`, a nie w `reportOscState` (plan 2.2 [Z:6c]);
   stąd jej kopia w `HostPadReporterImpl`.
4. W `Game.onCreate()` istnieją `prefConfig`, `controllerHandler`, `conn`, `streamView`, a rodzic
   `streamView` jest `FrameLayout` (plan 2.2 [Z:6b]: `(FrameLayout) streamView.getParent()`).
5. Routing dotyku: `backgroundTouchView` + `onTouch` + `handleMotionEvent`, palce do `touchContextMap`
   (`TouchContext[2]`), trzy palce -> `toggleKeyboard()` (plan 2.2 [Z:6b]).
6. Rozdzielczość referencyjna dotyku w upstreamie to 1280×720 (plan 2.2), ale dla `sendMousePosition`
   przekazujemy rozdzielczość strumienia / prostokąt wideo (overlay pozwala na oba).
7. Proporcje wideo ustala `StreamView.setDesiredAspectRatio` (plan 2.3 pkt 5); zakładamy, że
   rozmiar `StreamView` po layoucie = prostokąt wideo.
8. `ControllerHandler.onInputDeviceAdded` jest pusty (plan 2.3 pkt 7), więc nasz listener nie koliduje.
9. Klucze preferencji upstreamu (`checkbox_show_onscreen_controls` itd.) wg planu 2.1 [Z:6e];
   struktura `preferences.xml`, nazwa metody odczytu w `PreferenceConfiguration` i API preferencji
   (`androidx.preference` vs `android.preference`) to **moje założenia**.
10. Sygnatura `KeyboardTranslator.translate` **nie jest w planie**; wariant w `ThumbcastKeyTranslator`
    to założenie (komentarz w pliku podaje alternatywę). Tak samo pole `keyboardTranslator` w `Game`.
11. Stałe protokołu w `HostProtocol` (`:control-android`): `KEY_DOWN=0x03`, `KEY_UP=0x04`,
    `MODIFIER_SHIFT/CTRL/ALT=0x01/0x02/0x04`, `MOUSE_BUTTON_LEFT/MIDDLE/RIGHT=1/2/3` oraz format kodu
    hosta `0x80xx` (VK z prefiksem) są **z mojej pamięci o moonlight-common-c**, nie z planu.
    Do sprawdzenia w `KeyboardPacket`, `MouseButtonPacket` i `KeyboardTranslator`. Przy rozjeździe:
    poprawić `HostProtocol` (jedno miejsce).
12. `applicationId` upstreamu `com.limelight` i flavory `root`/`nonRoot` (plan 1.3); sposób ustawiania
    suffiksu dla flavorów, authorities providerów i `shortcuts.xml` to założenia.

## Otwarte pytania

- Czy w trybie Thumbcast blokować orientację na landscape jak przy OSC (zakładam, że tak)?
- Czy `prefConfig.onscreenController` ma być wymuszane na `false`, gdy `control_layer = thumbcast`
  (żeby fallback rumble i inne zależności od OSC działały przewidywalnie)?
- Menu w grze (plan 7 pkt 5, bottom sheet) nie jest częścią tego patcha; `"menu"` otwiera menu
  kontekstowe upstreamu [?] jako tymczasowe rozwiązanie.
