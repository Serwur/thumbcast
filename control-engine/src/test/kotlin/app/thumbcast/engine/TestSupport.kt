package app.thumbcast.engine

import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.CameraDrag
import app.thumbcast.model.CameraOutput
import app.thumbcast.model.Control
import app.thumbcast.model.Curve
import app.thumbcast.model.CurveType
import app.thumbcast.model.FreeZoneMode
import app.thumbcast.model.GesturesConfig
import app.thumbcast.model.GlobalSettings
import app.thumbcast.model.Handedness
import app.thumbcast.model.LongPress
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.PinchZoom
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import app.thumbcast.model.StickKeys
import app.thumbcast.model.SystemControl
import app.thumbcast.model.TapTarget
import app.thumbcast.model.Touchpad

// Profil testowy budowany inline (nie czytamy presetów z dysku).
// Ekran testowy 2000 x 1000 px, wideo 1920 x 1080 na całym ekranie.
//
// Rozkład (prawa ręka), px:
//   move   (gałka, pływająca)  x 80..560,   y 450..950
//   a      (A)                 x 1720..1900, y 700..860
//   x      (X)                 x 1520..1700, y 700..860
//   lt     (LT, modyfikator LATCH) x 1400..1600, y 100..220
//   map    (klawisz M)         x 900..1000,  y 300..400
//   chat   (App soft_keyboard) x 40..160,    y 100..190
// Wolna strefa kamery: prawa połowa poza kontrolkami, np. (1300, 500). Pasek edge swipe: y < 50.

const val W: Int = 2000
const val H: Int = 1000

object Pts {
    const val STICK_X: Float = 300f
    const val STICK_Y: Float = 700f
    const val A_X: Float = 1810f
    const val A_Y: Float = 780f
    const val X_X: Float = 1610f
    const val X_Y: Float = 780f
    const val LT_X: Float = 1500f
    const val LT_Y: Float = 160f
    const val MAP_X: Float = 950f
    const val MAP_Y: Float = 350f
    const val CHAT_X: Float = 100f
    const val CHAT_Y: Float = 145f
    const val FREE_X: Float = 1300f
    const val FREE_Y: Float = 500f
}

fun testViewport(): Viewport = Viewport(W, H, RectPx(0, 0, W, H), 1920, 1080)

fun testControls(kbm: Boolean): List<Control> {
    val list = ArrayList<Control>()
    list.add(
        StickControl(
            id = "move", rect = Rect(0.04f, 0.45f, 0.24f, 0.50f), label = null,
            output = Output.Stick(PadStick.LEFT), floating = true, deadzone = 0.12f, outerDeadzone = 0.04f,
            curve = Curve(CurveType.LINEAR), kbmKeys = if (kbm) StickKeys("W", "S", "A", "D") else null,
            kbmThreshold = 0.35f
        )
    )
    if (kbm) {
        list.add(ButtonControl(id = "a", rect = Rect(0.86f, 0.70f, 0.09f, 0.16f), output = Output.Key("SPACE")))
        list.add(ButtonControl(id = "x", rect = Rect(0.76f, 0.70f, 0.09f, 0.16f), output = Output.Key("1")))
        list.add(
            ModifierControl(
                id = "lt", rect = Rect(0.70f, 0.10f, 0.10f, 0.12f), output = Output.Key("SHIFT"),
                behavior = Behavior.LATCH
            )
        )
    } else {
        list.add(ButtonControl(id = "a", rect = Rect(0.86f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.A)))
        list.add(ButtonControl(id = "x", rect = Rect(0.76f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.X)))
        list.add(
            ModifierControl(
                id = "lt", rect = Rect(0.70f, 0.10f, 0.10f, 0.12f), output = Output.Trigger(PadTrigger.LT),
                behavior = Behavior.LATCH
            )
        )
    }
    list.add(ButtonControl(id = "map", rect = Rect(0.45f, 0.30f, 0.05f, 0.10f), output = Output.Key("M")))
    list.add(SystemControl(id = "chat", rect = Rect(0.02f, 0.10f, 0.06f, 0.09f), output = Output.App("soft_keyboard")))
    return list
}

fun testProfile(
    mode: ProfileMode = ProfileMode.PAD_PLUS,
    slideToPress: Boolean = false,
    handedness: Handedness = Handedness.RIGHT,
    controls: List<Control>? = null
): Profile {
    val kbm = mode == ProfileMode.KEYBOARD_MOUSE
    return Profile(
        schema = 1,
        id = "test-profile",
        name = "Test",
        mode = mode,
        handedness = handedness,
        global = GlobalSettings(slideToPress = slideToPress),
        gestures = GesturesConfig(
            cameraDrag = CameraDrag(
                output = if (kbm) CameraOutput.MOUSE_LOOK else CameraOutput.PAD_RIGHT_STICK,
                sensitivity = 1.2f, curve = Curve(CurveType.POWER, 1.6f), deadzonePx = 4f,
                invertY = false, maxDeflection = 1.0f, decayMs = 60
            ),
            tapTarget = TapTarget(enabled = true, maxMovePx = 12f, maxMs = 220),
            longPress = LongPress(enabled = true, minMs = 450),
            pinchZoom = PinchZoom(enabled = true, pxPerNotch = 40f)
        ),
        controls = controls ?: testControls(kbm)
    )
}

/**
 * Profil z wolną strefą w trybie touchpada (`gestures.freeZone = touchpad`). Z `rightStick = true` dochodzi widoczna
 * prawa gałka (jak w presecie Big Picture): x 1100..1300, y 600..900 px, czyli środek (1200, 750).
 */
fun touchpadProfile(
    mode: ProfileMode = ProfileMode.PAD_PLUS,
    sensitivity: Float = 1.0f,
    scrollPxPerNotch: Float = 24f,
    rightStick: Boolean = false
): Profile {
    val base = testProfile(mode = mode)
    val controls = ArrayList<Control>(base.controls)
    if (rightStick) {
        controls.add(
            StickControl(
                id = "look", rect = Rect(0.55f, 0.60f, 0.10f, 0.30f), label = null,
                output = Output.Stick(PadStick.RIGHT), floating = false, deadzone = 0.12f, outerDeadzone = 0.04f,
                curve = Curve(CurveType.LINEAR),
                kbmKeys = if (mode == ProfileMode.KEYBOARD_MOUSE) StickKeys("UP", "DOWN", "LEFT", "RIGHT") else null,
                kbmThreshold = 0.35f
            )
        )
    }
    return base.copy(
        gestures = base.gestures.copy(
            freeZone = FreeZoneMode.TOUCHPAD,
            touchpad = Touchpad(sensitivity = sensitivity, scrollPxPerNotch = scrollPxPerNotch)
        ),
        controls = controls
    )
}

/** Uchwyt testowy: silnik + fałszywy sink + zegar + wygodne zdarzenia. Pozycje palców są pamiętane. */
class Rig(profile: Profile = testProfile(), config: EngineConfig? = null) {
    val sink: FakeInputSink = FakeInputSink()
    val clock: FakeClock = FakeClock(1000L)
    val engine: InputEngine = if (config == null) {
        InputEngine(profile, sink, clock, testViewport())
    } else {
        InputEngine(config, profile, sink, clock, testViewport())
    }
    private val pos: LinkedHashMap<Int, TouchPointer> = LinkedHashMap()

    fun down(id: Int, x: Float, y: Float) {
        pos[id] = TouchPointer(id, x, y)
        engine.onTouch(TouchEvent(TouchAction.DOWN, id, ArrayList(pos.values), clock.now))
    }

    fun move(id: Int, x: Float, y: Float) {
        pos[id] = TouchPointer(id, x, y)
        engine.onTouch(TouchEvent(TouchAction.MOVE, -1, ArrayList(pos.values), clock.now))
    }

    /** Jedno zdarzenie MOVE z kilkoma palcami przesuniętymi naraz (jak MotionEvent z kilkoma wskaźnikami). */
    fun moveAll(vararg moved: TouchPointer) {
        for (tp in moved) pos[tp.id] = tp
        engine.onTouch(TouchEvent(TouchAction.MOVE, -1, ArrayList(pos.values), clock.now))
    }

    fun up(id: Int) {
        val list = ArrayList(pos.values)
        engine.onTouch(TouchEvent(TouchAction.UP, id, list, clock.now))
        pos.remove(id)
    }

    fun cancel() {
        engine.onTouch(TouchEvent(TouchAction.CANCEL, -1, ArrayList(pos.values), clock.now))
        pos.clear()
    }

    fun tick() {
        engine.onTick(clock.now)
    }

    fun advance(ms: Long) {
        clock.advance(ms)
    }

    fun mouseButtons(): List<SinkRecord> = sink.ofCall(SinkCall.BUTTON)
}


