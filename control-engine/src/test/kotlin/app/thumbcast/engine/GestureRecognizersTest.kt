package app.thumbcast.engine

import app.thumbcast.model.ProfileMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureRecognizersTest {

    private fun buttonSeq(r: Rig): List<String> {
        val out = ArrayList<String>()
        for (rec in r.mouseButtons()) out.add((rec.mouse?.name ?: "?") + (if (rec.down) "+" else "-"))
        return out
    }

    // ------------------------------------------------------------------ tap

    @Test
    fun tapGivesPositionAndLeftClickInVideoSpace() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.advance(100)
        r.up(1)
        val pos = r.sink.ofCall(SinkCall.POSITION)
        assertEquals(1, pos.size)
        assertEquals(960, pos[0].x)
        assertEquals(540, pos[0].y)
        assertEquals(1920, pos[0].refW)
        assertEquals(1080, pos[0].refH)
        assertEquals(listOf("LEFT+", "LEFT-"), buttonSeq(r))
        // Kolejność: najpierw pozycja, potem klik.
        assertEquals(SinkCall.POSITION, r.sink.records[0].call)
        assertTrue(r.sink.isZero())
    }

    @Test
    fun tapWithLetterboxedVideo() {
        val r = Rig()
        r.engine.setViewport(Viewport(W, H, RectPx(200, 0, 1800, 900), 1920, 1080))
        r.down(1, 1000f, 450f)
        r.up(1)
        val pos = r.sink.ofCall(SinkCall.POSITION)
        assertEquals(960, pos[0].x)
        assertEquals(540, pos[0].y)
        // Dotyk w czarnym pasie: brak kliknięcia.
        r.sink.clear()
        r.down(2, 1900f, 450f)
        r.up(2)
        assertEquals(0, r.sink.records.size)
    }

    @Test
    fun tooLongIsNotTap() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.advance(300) // > maxMs 220, < longPress 450
        r.up(1)
        assertEquals(0, r.sink.records.size)
    }

    @Test
    fun movedIsNotTap() {
        val r = Rig()
        r.down(1, 600f, 300f) // lewa wolna część: poza strefą kamery
        r.move(1, 640f, 300f)
        r.up(1)
        assertEquals(0, r.sink.records.size)
    }

    // ------------------------------------------------------------------ long-press

    @Test
    fun longPressRecognizedOnTickWhileFingerDown() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.advance(200)
        r.tick()
        assertEquals(0, r.mouseButtons().size)
        r.advance(260)
        r.tick()
        assertEquals(listOf("RIGHT+"), buttonSeq(r))
        assertEquals(1, r.sink.ofCall(SinkCall.POSITION).size)
        r.advance(500)
        r.tick()
        assertEquals(1, r.mouseButtons().size, "trzymanie dalej nie daje kolejnych wejść")
        r.up(1)
        assertEquals(listOf("RIGHT+", "RIGHT-"), buttonSeq(r), "long-press zamiast tapu, nie oba")
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun longPressRecognizedAtUpWithoutTicks() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.advance(600)
        r.up(1)
        assertEquals(listOf("RIGHT+", "RIGHT-"), buttonSeq(r))
    }

    @Test
    fun longPressCancelledByMovement() {
        val r = Rig()
        r.down(1, 600f, 300f)
        r.move(1, 650f, 300f)
        r.advance(600)
        r.tick()
        r.up(1)
        assertEquals(0, r.mouseButtons().size)
    }

    @Test
    fun noFingerNoTimeOutput() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.up(1)
        r.sink.clear()
        r.advance(5000)
        r.tick()
        r.engine.onTick()
        assertEquals(0, r.sink.records.size)
    }

    // ------------------------------------------------------------------ kamera (PAD_PLUS)

    @Test
    fun cameraDragDeflectsRightStickAndDecays() {
        val r = Rig()
        r.down(1, Pts.FREE_X, Pts.FREE_Y)
        r.advance(16)
        r.move(1, Pts.FREE_X + 20f, Pts.FREE_Y)
        val afterFirst = r.sink.lastPad!!.rx.toInt()
        assertTrue(afterFirst > 0, "przeciągnięcie w prawo = prawa gałka w prawo")
        assertEquals(0, r.sink.lastPad!!.lx.toInt())
        r.advance(16)
        r.move(1, Pts.FREE_X + 40f, Pts.FREE_Y)
        val peak = r.sink.lastPad!!.rx.toInt()
        // Palec stoi: wygasanie w decayMs (60 ms) przez ticki.
        r.advance(30)
        r.tick()
        val mid = r.sink.lastPad!!.rx.toInt()
        assertTrue(mid in 1 until peak, "połowa wygaszania: $mid z $peak")
        r.advance(40)
        r.tick()
        assertEquals(0, r.sink.lastPad!!.rx.toInt())
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
        assertEquals(0, r.mouseButtons().size, "przeciągnięcie nie jest tapem")
    }

    @Test
    fun cameraDragUpIsStickUp() {
        val r = Rig()
        r.down(1, Pts.FREE_X, Pts.FREE_Y)
        r.advance(16)
        r.move(1, Pts.FREE_X, Pts.FREE_Y - 30f)
        assertTrue(r.sink.lastPad!!.ry.toInt() > 0)
    }

    @Test
    fun cameraSpeedCurveIsMonotonic() {
        val cfg = ProfileAdapter.toConfig(testProfile()).camera
        val t = EngineTuning()
        val a = CameraMath.deflection(0.3f, 0f, cfg, t)[0]
        val b = CameraMath.deflection(0.6f, 0f, cfg, t)[0]
        val c = CameraMath.deflection(5f, 0f, cfg, t)[0]
        assertTrue(a > 0f && a < b && b <= c)
        assertEquals(1f, c, 1e-5f)
        assertEquals(0f, CameraMath.deflection(0.1f, 0f, cfg, t)[0], "martwa strefa 4 px / ramkę")
    }

    // ------------------------------------------------------------------ kamera (KBM)

    @Test
    fun kbmCameraRmbLookWithFractionalRemainders() {
        val r = Rig(testProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, Pts.FREE_X, Pts.FREE_Y)
        r.move(1, Pts.FREE_X + 13f, Pts.FREE_Y) // start gestu (> 12 px)
        assertEquals(listOf("RIGHT+"), buttonSeq(r))
        var sum = 0
        for (m in r.sink.ofCall(SinkCall.MOVE)) sum += m.dx
        // 13 * 1.2 = 15.6 -> 15 (reszta 0.6)
        assertEquals(15, sum)
        r.move(1, Pts.FREE_X + 13.5f, Pts.FREE_Y) // 0.5 * 1.2 = 0.6, + 0.6 = 1.2 -> 1
        sum = 0
        for (m in r.sink.ofCall(SinkCall.MOVE)) sum += m.dx
        assertEquals(16, sum)
        r.up(1)
        assertEquals(listOf("RIGHT+", "RIGHT-"), buttonSeq(r))
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size)
    }

    // ------------------------------------------------------------------ pinch

    @Test
    fun pinchProducesWheelNotches() {
        val r = Rig()
        r.down(1, 1000f, 500f)
        r.down(2, 1100f, 500f)
        r.move(2, 1185f, 500f) // +85 px -> 2 ząbki (40 px/ząbek)
        val wheels = r.sink.ofCall(SinkCall.WHEEL)
        var total = 0
        for (w in wheels) total += w.wheel
        assertEquals(240, total)
        r.move(1, 1045f, 500f) // odległość 140 -> względem kotwicy 180: -40 -> -1 ząbek
        total = 0
        for (w in r.sink.ofCall(SinkCall.WHEEL)) total += w.wheel
        assertEquals(120, total)
        r.up(2)
        r.up(1)
        assertEquals(0, r.mouseButtons().size, "szczypanie nie daje tapu")
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun pinchInterruptsCameraCleanly() {
        val r = Rig()
        r.down(1, Pts.FREE_X, Pts.FREE_Y)
        r.advance(16)
        r.move(1, Pts.FREE_X + 30f, Pts.FREE_Y)
        assertTrue(r.sink.lastPad!!.rx.toInt() != 0)
        r.down(2, Pts.FREE_X + 200f, Pts.FREE_Y)
        assertEquals(0, r.sink.lastPad!!.rx.toInt(), "kamera zwolniona przed szczypaniem")
        assertEquals(0, r.engine.violations.size)
    }

    // ------------------------------------------------------------------ edge swipe

    @Test
    fun edgeSwipeFromTopOpensMenuOnce() {
        val r = Rig()
        r.down(1, 1200f, 10f)
        r.move(1, 1200f, 60f)
        r.move(1, 1200f, 200f)
        r.up(1)
        val apps = r.sink.ofCall(SinkCall.APP)
        assertEquals(1, apps.size)
        assertEquals("menu", apps[0].app)
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size, "edge swipe nie rusza kamery")
        assertEquals(0, r.mouseButtons().size)
    }

    // ------------------------------------------------------------------ wiele palców

    @Test
    fun stickButtonAndCameraSimultaneously() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        r.down(2, Pts.A_X, Pts.A_Y)
        r.down(3, Pts.FREE_X, Pts.FREE_Y)
        r.advance(16)
        r.move(3, Pts.FREE_X + 30f, Pts.FREE_Y)
        val p = r.sink.lastPad!!
        assertEquals(PadFlags.A, p.buttons)
        assertEquals(32767, p.lx.toInt())
        assertTrue(p.rx.toInt() > 0)
        r.cancel()
        assertTrue(r.sink.isZero())
    }
}
