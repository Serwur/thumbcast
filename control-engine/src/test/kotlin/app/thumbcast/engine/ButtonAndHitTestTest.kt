package app.thumbcast.engine

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.Handedness
import app.thumbcast.model.HandednessTransform
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ButtonAndHitTestTest {

    @Test
    fun holdButtonFollowsFinger() {
        val r = Rig()
        r.down(1, Pts.A_X, Pts.A_Y)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
        r.up(1)
        assertEquals(0, r.sink.lastPad!!.buttons)
        assertEquals(2, r.sink.ofCall(SinkCall.PAD).size)
    }

    @Test
    fun padFlagsMatchContract() {
        assertEquals(0x1000, PadFlags.A)
        assertEquals(0x2000, PadFlags.B)
        assertEquals(0x4000, PadFlags.X)
        assertEquals(0x8000, PadFlags.Y)
        assertEquals(0x0001, PadFlags.UP)
        assertEquals(0x0100, PadFlags.LB)
        assertEquals(0x0400, PadFlags.GUIDE)
    }

    @Test
    fun latchTogglesOnDownOnly() {
        val r = Rig()
        r.down(1, Pts.LT_X, Pts.LT_Y)
        assertEquals(-1, r.sink.lastPad!!.lt.toInt(), "LT = 0xFF")
        r.up(1)
        assertEquals(-1, r.sink.lastPad!!.lt.toInt(), "LATCH przeżywa UP")
        r.down(2, Pts.LT_X, Pts.LT_Y)
        assertEquals(0, r.sink.lastPad!!.lt.toInt())
        r.up(2)
        assertEquals(0, r.sink.lastPad!!.lt.toInt())
        assertEquals(1, r.sink.pressEdges.size)
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun latchDoesNotBlockFingerAndReleaseAllClearsIt() {
        val r = Rig(testProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, Pts.LT_X, Pts.LT_Y)
        r.up(1)
        assertEquals(setOf("SHIFT"), r.sink.keysDown)
        r.engine.releaseAll()
        assertTrue(r.sink.isZero())
    }

    @Test
    fun twoFingersOnSameButtonShareState() {
        val r = Rig()
        r.down(1, Pts.A_X, Pts.A_Y)
        r.down(2, Pts.A_X + 10f, Pts.A_Y)
        r.up(1)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons, "A trzymane, dopóki dotyka drugi palec")
        r.up(2)
        assertEquals(0, r.sink.lastPad!!.buttons)
        assertEquals(1, r.sink.pressEdges.size)
    }

    @Test
    fun latchSurvivesViewportChangeWithoutFingers() {
        val r = Rig()
        r.down(1, Pts.LT_X, Pts.LT_Y)
        r.up(1)
        r.engine.setViewport(Viewport(W, H, RectPx(0, 0, W, 900), 1920, 1080))
        assertEquals(-1, r.sink.lastPad!!.lt.toInt())
        r.down(2, Pts.LT_X, Pts.LT_Y)
        assertEquals(0, r.sink.lastPad!!.lt.toInt(), "jedno dotknięcie zwalnia LATCH także po zmianie viewportu")
    }

    @Test
    fun noSlideWithoutSlideToPress() {
        val r = Rig(testProfile(slideToPress = false))
        r.down(1, Pts.X_X, Pts.X_Y)
        r.move(1, Pts.A_X, Pts.A_Y)
        assertEquals(PadFlags.X, r.sink.lastPad!!.buttons)
        r.up(1)
        assertEquals(0, r.sink.lastPad!!.buttons)
    }

    @Test
    fun slideToPressReleasesBeforePressing() {
        val r = Rig(testProfile(slideToPress = true))
        r.down(1, Pts.X_X, Pts.X_Y)
        r.move(1, Pts.A_X, Pts.A_Y)
        val pads = r.sink.ofCall(SinkCall.PAD)
        val seq = ArrayList<Int>()
        for (p in pads) seq.add(p.buttons)
        assertEquals(listOf(PadFlags.X, 0, PadFlags.A), seq)
        for (v in seq) assertTrue(Integer.bitCount(v) <= 1, "nigdy dwa przyciski z jednego palca")
        r.up(1)
        assertEquals(0, r.sink.lastPad!!.buttons)
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun systemButtonSendsAppOnce() {
        val r = Rig()
        r.down(1, Pts.CHAT_X, Pts.CHAT_Y)
        r.move(1, Pts.CHAT_X + 5f, Pts.CHAT_Y)
        r.up(1)
        val apps = r.sink.ofCall(SinkCall.APP)
        assertEquals(1, apps.size)
        assertEquals("soft_keyboard", apps[0].app)
    }

    @Test
    fun keyButtonInPadProfile() {
        val r = Rig()
        r.down(1, Pts.MAP_X, Pts.MAP_Y)
        assertEquals(setOf("M"), r.sink.keysDown)
        r.up(1)
        assertTrue(r.sink.keysDown.isEmpty())
    }

    @Test
    fun activationZoneLargerThanGraphic() {
        val r = Rig()
        r.down(1, 1910f, Pts.A_Y) // 10 px na prawo od grafiki A (margines 32 px)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
        r.up(1)
        // Między X (do 1700) i A (od 1720): bliżej A.
        r.down(2, 1714f, Pts.A_Y)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
    }

    @Test
    fun zOrderLaterControlWins() {
        val controls = ArrayList<Control>(testControls(false))
        // Nowa kontrolka na końcu listy leży dokładnie na A, więc jest wyżej w z-order.
        controls.add(ButtonControl(id = "top", rect = Rect(0.86f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.Y)))
        val r = Rig(testProfile(controls = controls))
        r.down(1, Pts.A_X, Pts.A_Y)
        assertEquals(PadFlags.Y, r.sink.lastPad!!.buttons)
    }

    @Test
    fun toPxWithSafeAreaAndMirror() {
        val base = PxRect(100f, 0f, 1900f, 1000f)
        val p = HitTest.toPx(Rect(0f, 0.5f, 0.1f, 0.2f), base, false)
        assertEquals(100f, p.left, 1e-3f)
        assertEquals(280f, p.right, 1e-3f)
        assertEquals(500f, p.top, 1e-3f)
        val m = HitTest.toPx(Rect(0f, 0.5f, 0.1f, 0.2f), base, true)
        assertEquals(1720f, m.left, 1e-3f)
        assertEquals(1900f, m.right, 1e-3f)
    }

    @Test
    fun hitTestUsesSafeArea() {
        val cfg = ProfileAdapter.toConfig(testProfile())
        val vp = Viewport(W, H, RectPx(0, 0, W, H), 1920, 1080, RectPx(100, 0, 1900, 1000))
        val ht = HitTest(cfg.controls, vp, false, cfg.tuning)
        // A: x = 100 + 0.86 * 1800 = 1648 .. 1810.
        assertEquals("a", ht.hit(1700f, 780f)?.id)
        assertNull(ht.hit(1300f, 500f))
    }

    @Test
    fun leftHandedMirrorInEngine() {
        val cfg = ProfileAdapter.toConfig(testProfile()).copy(leftHanded = true)
        val r = Rig(testProfile(), cfg)
        // A po lustrze: x = 1 - 0.86 - 0.09 = 0.05 -> 100..280 px.
        r.down(1, 190f, Pts.A_Y)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
        r.up(1)
        // Strefa kamery też zamieniona: przeciągnięcie po lewej wolnej części porusza prawą gałką.
        r.down(2, 700f, 500f)
        r.advance(16)
        r.move(2, 720f, 500f)
        assertTrue(r.sink.lastPad!!.rx.toInt() > 0)
    }

    @Test
    fun mirroredProfileFromModelIsNotMirroredTwice() {
        val mirrored = HandednessTransform.mirror(testProfile())
        assertEquals(Handedness.LEFT, mirrored.handedness)
        val r = Rig(mirrored)
        r.down(1, 190f, Pts.A_Y)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
    }
}
