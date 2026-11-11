package app.thumbcast.engine

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** InputEngine.visualState(): wygląd idzie za tym, co silnik rozpoznał (jedno źródło prawdy). */
class VisualStateTest {

    private val eps = 1e-4f

    @Test
    fun hitSlopPressIsVisible() {
        val r = Rig()
        // A: grafika y 700..860; strefa aktywacji 32 px. y = 875 jest poza grafiką, ale w strefie.
        r.down(1, Pts.A_X, 875f)
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons, "silnik wcisnął A")
        val s = r.engine.visualState()
        assertEquals("a", s.controlOf(1))
        assertTrue(s.isPressed("a"))
        assertTrue(s.heldIds.contains("a"))
        r.up(1)
        val after = r.engine.visualState()
        assertFalse(after.isPressed("a"))
        assertTrue(after.pointers.isEmpty())
    }

    @Test
    fun slideToPressMovesOwner() {
        val r = Rig(testProfile(slideToPress = true))
        r.down(1, Pts.X_X, Pts.X_Y)
        assertEquals("x", r.engine.visualState().controlOf(1))
        r.move(1, Pts.A_X, Pts.A_Y)
        val s = r.engine.visualState()
        assertEquals("a", s.controlOf(1))
        assertTrue(s.isPressed("a"))
        assertFalse(s.isPressed("x"))
        assertEquals(PadFlags.A, r.sink.lastPad!!.buttons)
    }

    @Test
    fun noSlideWithoutSettingKeepsOwner() {
        val r = Rig(testProfile(slideToPress = false))
        r.down(1, Pts.X_X, Pts.X_Y)
        r.move(1, Pts.A_X, Pts.A_Y)
        val s = r.engine.visualState()
        assertEquals("x", s.controlOf(1))
        assertFalse(s.isPressed("a"))
    }

    @Test
    fun floatingStickRadiusAndCentreComeFromRecognizer() {
        val r = Rig()
        val idle = r.engine.visualState().sticks.getValue("move")
        assertFalse(idle.active)
        // Pływająca: 15% krótszego boku (1000 px).
        assertEquals(150f, idle.radius, eps)

        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 75f, Pts.STICK_Y)
        val s = r.engine.visualState().sticks.getValue("move")
        assertTrue(s.active)
        assertEquals(1, s.pointerId)
        assertEquals(Pts.STICK_X, s.centerX, eps)
        assertEquals(Pts.STICK_Y, s.centerY, eps)
        assertEquals(idle.radius, s.radius, eps)
        assertEquals(0.5f, s.deflectionX, eps)
        assertEquals(0f, s.deflectionY, eps)
        assertTrue(r.sink.lastPad!!.lx > 0, "wyjście w prawo")
    }

    @Test
    fun deflectionIsClampedToUnitCircleInScreenAxes() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X, Pts.STICK_Y + 600f) // daleko w dół ekranu
        val s = r.engine.visualState().sticks.getValue("move")
        assertEquals(0f, s.deflectionX, eps)
        assertEquals(1f, s.deflectionY, eps)
        assertEquals(1f, hypot(s.deflectionX, s.deflectionY), eps)
    }

    @Test
    fun fixedStickUsesRectGeometry() {
        val controls = listOf(
            StickControl(
                id = "move", rect = Rect(0.05f, 0.40f, 0.20f, 0.40f), output = Output.Stick(PadStick.LEFT),
                floating = false
            ),
            ButtonControl(id = "a", rect = Rect(0.86f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.A))
        )
        val r = Rig(testProfile(controls = controls))
        // rect px: x 100..500, y 400..800 -> środek (300, 600), promień min(400, 400) / 2 = 200.
        val idle = r.engine.visualState().sticks.getValue("move")
        assertEquals(300f, idle.centerX, eps)
        assertEquals(600f, idle.centerY, eps)
        assertEquals(200f, idle.radius, eps)
        r.down(1, 350f, 600f)
        val s = r.engine.visualState().sticks.getValue("move")
        assertTrue(s.active)
        assertEquals(idle.centerX, s.centerX, eps)
        assertEquals(idle.radius, s.radius, eps)
        assertEquals(0.25f, s.deflectionX, eps)
    }

    @Test
    fun latchVisibleAfterUpAndClearedByReleaseAll() {
        val r = Rig()
        r.down(1, Pts.LT_X, Pts.LT_Y)
        r.up(1)
        val s = r.engine.visualState()
        assertTrue(s.latchedIds.contains("lt"))
        assertTrue(s.isPressed("lt"))
        r.engine.releaseAll()
        assertFalse(r.engine.visualState().isPressed("lt"))
    }

    @Test
    fun gestureFingerHasNoControlAndCancelClearsPointers() {
        val r = Rig()
        r.down(1, Pts.FREE_X, Pts.FREE_Y)
        r.down(2, Pts.A_X, Pts.A_Y)
        val s = r.engine.visualState()
        assertEquals(2, s.pointers.size)
        assertNull(s.controlOf(1))
        assertEquals("a", s.controlOf(2))
        r.cancel()
        assertTrue(r.engine.visualState().pointers.isEmpty())
    }

    @Test
    fun readingStateHasNoSideEffects() {
        val r = Rig()
        r.down(1, Pts.A_X, Pts.A_Y)
        val calls = r.sink.records.size
        repeat(3) { r.engine.visualState() }
        assertEquals(calls, r.sink.records.size)
        assertEquals(0, r.engine.violations.size)
    }
}
