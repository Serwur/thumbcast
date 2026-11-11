package app.thumbcast.engine

import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.CameraOutput
import app.thumbcast.model.Control
import app.thumbcast.model.Curve
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadStick
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl

/**
 * Profile (model) -> EngineConfig (silnik). Jedyne miejsce w :control-engine, które czyta pola modelu
 * spoza sygnatur kontraktu (patrz CONTRACT-NOTES.md, sekcje MODEL i ENGINE).
 *
 * Leworęczność: HandednessTransform.mirror (MODEL) zapisuje w profilu już odbite rect i strefę kamery
 * i przełącza pole handedness. Profil jest więc zawsze w docelowej orientacji, a silnik NIE odbija go
 * drugi raz (leftHanded = false). EngineConfig.leftHanded zostaje do odbicia w czasie działania
 * (np. podgląd w edytorze) i do testów.
 *
 * Akordy (ButtonControl.chord) nie są obsługiwane (od 11.11.2026 odrzuca je walidator): pole jest ignorowane, przycisk daje
 * tylko swoje `output` (bezpieczniej: nigdy więcej niż jedno wejście z dotyku).
 */
object ProfileAdapter {

    fun toConfig(p: Profile, tuning: EngineTuning = EngineTuning()): EngineConfig {
        val g = p.gestures
        val cam = g.cameraDrag
        val kbm = p.mode == ProfileMode.KEYBOARD_MOUSE
        val controls = ArrayList<ControlSpec>()
        for (c in p.controls) {
            val spec = toSpec(c)
            if (spec != null) controls.add(spec)
        }
        return EngineConfig(
            mode = p.mode,
            leftHanded = false,
            slideToPress = p.global.slideToPress,
            camera = CameraConfig(
                zone = cam.zone,
                mouseLook = kbm || cam.output == CameraOutput.MOUSE_LOOK,
                sensitivity = cam.sensitivity,
                curve = curveOf(cam.curve),
                deadzonePx = cam.deadzonePx,
                invertY = cam.invertY,
                maxDeflection = cam.maxDeflection,
                decayMs = cam.decayMs.toLong()
            ),
            tap = TapConfig(g.tapTarget.enabled, g.tapTarget.maxMovePx, g.tapTarget.maxMs.toLong()),
            longPress = LongPressConfig(g.longPress.enabled, g.longPress.minMs.toLong()),
            pinch = PinchConfig(g.pinchZoom.enabled, g.pinchZoom.pxPerNotch),
            edgeSwipe = EdgeSwipeConfig(g.edgeSwipeTop.enabled),
            controls = controls,
            tuning = tuning
        )
    }

    fun curveOf(c: Curve): CurveSpec = CurveSpec(c.type, c.exponent)

    private fun toSpec(c: Control): ControlSpec? = when (c) {
        is StickControl -> {
            val o = c.output
            val stick = if (o is Output.Stick) o.stick else PadStick.LEFT
            StickSpec(
                id = c.id, rect = c.rect, floating = c.floating,
                deadzone = c.deadzone, outerDeadzone = c.outerDeadzone, curve = curveOf(c.curve),
                padStick = stick, kbmKeys = c.kbmKeys, kbmThreshold = c.kbmThreshold
            )
        }
        is ButtonControl -> ButtonSpec(c.id, c.rect, c.output, false)
        is ModifierControl -> ButtonSpec(c.id, c.rect, c.output, c.behavior == Behavior.LATCH)
        is SystemControl -> ButtonSpec(c.id, c.rect, c.output, false)
    }
}
