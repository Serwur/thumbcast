# CONTRACT: wspólne typy i granice modułów (Thumbcast)

Źródło prawdy dla wszystkich agentów. Nie zmieniaj nazw ani sygnatur stąd bez zapisania uwagi w `/home/claude/thumbcast/CONTRACT-NOTES.md`.
Plan nadrzędny: `/home/claude/plan-wow-forever-controller.md` (sekcje 4, 5, 6, 7, 8).

Pakiet bazowy: `app.thumbcast`. Język nowych modułów: Kotlin (JVM target 17). Katalog projektu: `/home/claude/thumbcast/`.

## Katalogi i właściciele (NIE edytuj cudzych katalogów)
| Katalog | Właściciel | Zawartość |
|---|---|---|
| `control-model/` + `presets/` | agent MODEL | model profilu, walidacja, migracje, JSON presety |
| `control-engine/` | agent ENGINE | silnik wejścia (czysty JVM), ActionPolicy, testy |
| `control-android/` + `patches/` | agent ANDROID | ThumbcastOverlay, adaptery do upstreamu, patch Game.java |
| katalog główny (`settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `.github/`, `docs/`, `fastlane/`, `*.md` poza CONTRACT) | agent SCAFFOLD | build, CI, dokumentacja |

## Moduł `:control-model` (pakiet `app.thumbcast.model`), czysty JVM, kotlinx.serialization
```kotlin
enum class PadButton { A, B, X, Y, UP, DOWN, LEFT, RIGHT, LB, RB, START, BACK, L3, R3, GUIDE }
enum class PadTrigger { LT, RT }
enum class PadStick { LEFT, RIGHT }
enum class MouseBtn { LEFT, RIGHT, MIDDLE }

// Dokładnie jedno wyjście na kontrolkę (polityka 1 dotyk = 1 wejście).
sealed interface Output {
    data class Pad(val button: PadButton) : Output
    data class Trigger(val trigger: PadTrigger) : Output
    data class Stick(val stick: PadStick) : Output            // oś analogowa
    data class Key(val code: String) : Output                 // np. "A", "1", "SPACE", "ESC", "F5" (stały słownik w modelu)
    data class Mouse(val button: MouseBtn) : Output           // przycisk w punkcie dotyku lub bieżącej pozycji
    object MouseWheel : Output
    object MouseLook : Output                                 // ruch względny (kamera w trybie KBM)
    data class App(val action: String) : Output               // "menu", "soft_keyboard", "toggle_overlay"; nic nie idzie do hosta
}

enum class Behavior { HOLD, LATCH }                           // LATCH tylko dla modyfikatorów
enum class ProfileMode { PAD_PLUS, KEYBOARD_MOUSE }

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)   // znormalizowane 0..1

sealed interface Control { val id: String; val rect: Rect; val label: String? }
data class StickControl(override val id: String, override val rect: Rect, override val label: String?,
    val output: Output /* Output.Stick lub Output.Key-cztery-kierunki NIE; w KBM: pole keys */, 
    val floating: Boolean, val deadzone: Float, val outerDeadzone: Float, val curve: Curve,
    val kbmKeys: StickKeys? /* up,down,left,right; tylko tryb KBM */, val kbmThreshold: Float) : Control
data class ButtonControl(override val id: String, override val rect: Rect, override val label: String?,
    val output: Output, val behavior: Behavior, val shape: Shape) : Control
// Control ma też ModifierControl (jak ButtonControl, ale wolno LATCH) i SystemControl (output: Output.App).

data class Curve(val type: CurveType, val exponent: Float = 1f)           // LINEAR | POWER
data class StickKeys(val up: String, val down: String, val left: String, val right: String)

data class GesturesConfig(val cameraDrag: CameraDrag, val tapTarget: TapTarget, val longPress: LongPress,
    val pinchZoom: PinchZoom, val edgeSwipeTop: EdgeSwipe)

data class Profile(val schema: Int, val id: String, val name: String, val tags: Map<String,String>,
    val mode: ProfileMode, val handedness: Handedness, val global: GlobalSettings,
    val gestures: GesturesConfig, val controls: List<Control>)

object ProfileCodec {
    fun decode(json: String): Result<Profile>     // odrzuca nieznane pola i typy, wymusza reguły z sekcji 5.3 planu
    fun encode(profile: Profile): String
    const val CURRENT_SCHEMA = 1
}
object ProfileValidator { fun validate(p: Profile): List<String> }        // pusta lista = OK; reguły z sekcji 5.3
```
Schemat JSON ma odpowiadać przykładowi z sekcji 5.3 planu (pola: schema, id, name, tags, mode, reference, handedness, global, gestures, controls). `mode` w JSON: `"pad_plus"` | `"keyboard_mouse"`.
Zakazane w modelu i parserze: typy `sequence`, `macro`, `repeat`, `turbo`, `delay`, `toggle_auto`; `ignoreUnknownKeys = false`.

## Moduł `:control-engine` (pakiet `app.thumbcast.engine`), czysty JVM, zależy od `:control-model`
```kotlin
// Wejście platformowo-niezależne (overlay Androida tłumaczy MotionEvent na to).
enum class TouchAction { DOWN, MOVE, UP, CANCEL }                  // DOWN/UP dotyczą pojedynczego wskaźnika
data class TouchPointer(val id: Int, val x: Float, val y: Float)   // px w układzie overlaya
data class TouchEvent(val action: TouchAction, val actionPointerId: Int,
                      val pointers: List<TouchPointer>, val timeMs: Long)

// Wyjście do hosta. Implementują: FakeInputSink (testy, w control-engine) i adaptery w :control-android.
interface InputSink {
    fun padState(buttons: Int, leftStickX: Short, leftStickY: Short,
                 rightStickX: Short, rightStickY: Short, leftTrigger: Byte, rightTrigger: Byte)
    fun key(code: String, down: Boolean)
    fun mouseMoveRelative(dx: Int, dy: Int)
    fun mouseButton(button: MouseBtn, down: Boolean)
    fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int)   // pozycja w układzie wideo
    fun mouseWheel(highResDelta: Int)                                   // 120 = 1 ząbek
    fun app(action: String)
}

interface Clock { fun nowMs(): Long }

class InputEngine(profile: Profile, sink: InputSink, clock: Clock, viewport: Viewport) {
    fun onTouch(e: TouchEvent)
    fun releaseAll()                       // CANCEL, utrata fokusu, rozłączenie: zerowy stan
    fun setProfile(p: Profile)
    fun setViewport(v: Viewport)
    val violations: List<PolicyViolation>  // log naruszeń ActionPolicy
}
data class Viewport(val widthPx: Int, val heightPx: Int, val videoRect: RectPx /* prostokąt wideo po letterboxie */,
                    val videoRefWidth: Int, val videoRefHeight: Int)
```
Flagi przycisków pada w `padState` = stałe `ControllerPacket` z upstreamu: A=0x1000 B=0x2000 X=0x4000 Y=0x8000 UP=0x0001 DOWN=0x0002 LEFT=0x0004 RIGHT=0x0008 LB=0x0100 RB=0x0200 START=0x0010 BACK=0x0020 L3=0x0040 R3=0x0080 GUIDE=0x0400. Osie ±32767, triggery 0..255 (jako Byte, 0xFF = pełne wychylenie).

## Moduł `:control-android` (pakiet `app.thumbcast.android`), zależy od `:control-engine`
- `ThumbcastOverlay : View` (jeden View, Canvas, do 10 wskaźników, `requestUnbufferedDispatch`), tłumaczy `MotionEvent` na `TouchEvent`.
- Adaptery `InputSink` do upstreamu `moonlight-android` (NIE jest w repo; patrz sekcja 2.2 i 2.3 planu): `PadSink` → `ControllerHandler.reportOscState(int buttonFlags, short lsX, short lsY, short rsX, short rsY, byte lt, byte rt)`, `KeyboardSink` → `NvConnection.sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags)` przez `KeyboardTranslator`, `MouseSink` → `NvConnection.sendMouse*`.
- Ponieważ kod upstreamu nie jest dostępny offline, adaptery kompilują się wyłącznie względem cienkich interfejsów-fasad (`HostConnection`, `HostPadReporter`) zdefiniowanych w `:control-android`, a ich implementacje przeciw prawdziwemu upstreamowi są w `patches/` jako opisany diff.

## Reguły dla wszystkich
- Środowisko: Java 21 i Gradle są w systemie, ale **nie ma sieci i nie ma kotlinc** (nie da się pobrać Kotlin Gradle plugin ani zależności). Kodu Kotlin nie da się tu skompilować. Pisz więc ostrożnie, prosto składniowo, bez magii, i **nie twierdź, że kod był kompilowany ani że testy przeszły**. Jeśli jakaś weryfikacja jest możliwa (np. walidacja JSON/YAML w Pythonie, `python3 -m json.tool`, sprawdzenie parą nawiasów skryptem), wykonaj ją i podaj wynik.
- Zasada nadrzędna: 1 dotyk = 1 wejście. Żadnych makr, sekwencji, turbo, opóźnień ani timerów generujących naciśnięcia (plan sekcja 6).
- Nazwy i grafiki: bez „WoW", „Blizzard", „Moonlight" w nazwach aplikacji, pakietów i zasobów.
- Pisz komentarze i dokumenty po polsku, identyfikatory kodu po angielsku.
- Na końcu podaj w raporcie: listę utworzonych plików, co jest niezweryfikowane, i otwarte pytania. Nie edytuj plików poza swoim katalogiem.
