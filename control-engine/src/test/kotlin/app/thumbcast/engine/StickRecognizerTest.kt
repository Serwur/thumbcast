package app.thumbcast.engine

import app.thumbcast.model.CurveType
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.StickKeys
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickRecognizerTest {

    private val linear: CurveSpec = CurveSpec(CurveType.LINEAR, 1f)

    @Test
    fun deadzoneGivesZero() {
        val v = StickMath.shape(0.10f, 0f, 0.12f, 0.04f, linear)
        assertEquals(0f, v[0])
        assertEquals(0f, v[1])
    }

    @Test
    fun rescaledDeadzoneHasNoJumpAtEdge() {
        // Tuż za martwą strefą wyjście jest bliskie zeru (brak skoku).
        val v = StickMath.shape(0.121f, 0f, 0.12f, 0.04f, linear)
        assertTrue(v[0] > 0f && v[0] < 0.01f, "wyjście tuż za strefą: ${v[0]}")
        // Połowa zakresu: (0.5 - 0.12) / 0.84.
        val half = StickMath.shape(0.5f, 0f, 0.12f, 0.04f, linear)
        assertEquals(0.38f / 0.84f, half[0], 1e-4f)
    }

    @Test
    fun outerDeadzoneSaturates() {
        val v = StickMath.shape(0.97f, 0f, 0.12f, 0.04f, linear)
        assertEquals(1f, v[0], 1e-6f)
        val over = StickMath.shape(3f, 4f, 0.12f, 0.04f, linear)
        assertEquals(1f, hypot(over[0], over[1]), 1e-5f)
        assertEquals(0.6f, over[0], 1e-5f) // kierunek zachowany
    }

    @Test
    fun powerCurve() {
        assertEquals(0.25f, StickMath.applyCurve(0.5f, CurveSpec(CurveType.POWER, 2f)), 1e-6f)
        assertEquals(0.5f, StickMath.applyCurve(0.5f, linear), 1e-6f)
        assertEquals(1f, StickMath.applyCurve(1.5f, CurveSpec(CurveType.POWER, 2f)), 1e-6f)
    }

    @Test
    fun eightDirections() {
        val k = StickKeys("W", "S", "A", "D")
        assertEquals(setOf("D"), StickMath.directionKeys(1f, 0f, 0.35f, k))
        assertEquals(setOf("W", "D"), StickMath.directionKeys(0.7f, 0.7f, 0.35f, k))
        assertEquals(setOf("W"), StickMath.directionKeys(0f, 1f, 0.35f, k))
        assertEquals(setOf("S", "A"), StickMath.directionKeys(-0.7f, -0.7f, 0.35f, k))
        assertEquals(emptySet<String>(), StickMath.directionKeys(0.3f, 0f, 0.35f, k))
    }

    @Test
    fun floatingStickFullDeflectionAndRelease() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        assertEquals(0, r.sink.records.size, "środek pływającej gałki = zero, nic nie wysyłamy")
        // Promień pływającej gałki = 0.15 * 1000 px = 150 px.
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(32767, r.sink.lastPad!!.lx.toInt())
        assertEquals(0, r.sink.lastPad!!.ly.toInt())
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 150f) // w górę ekranu = gałka w górę (dodatnie Y)
        assertEquals(0, r.sink.lastPad!!.lx.toInt())
        assertEquals(32767, r.sink.lastPad!!.ly.toInt())
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun floatingStickDeadzoneAndHalf() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 15f, Pts.STICK_Y) // 0.10 < 0.12
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size)
        r.move(1, Pts.STICK_X + 75f, Pts.STICK_Y) // 0.5
        val expected = Math.round(0.38f / 0.84f * 32767f)
        assertTrue(abs(r.sink.lastPad!!.lx.toInt() - expected) <= 1)
    }

    @Test
    fun captureOnDownKeepsStickOutsideRect() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        // Palec wyjeżdża daleko poza rect gałki (nad przycisk A): nadal steruje gałką, A nie jest wciśnięte.
        r.move(1, Pts.A_X, Pts.A_Y)
        assertTrue(r.sink.lastPad!!.lx.toInt() > 32000)
        assertEquals(0, r.sink.lastPad!!.buttons)
    }

    @Test
    fun kbmStickEmitsWasdWithEightDirections() {
        val r = Rig(testProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(setOf("D"), r.sink.keysDown)
        r.move(1, Pts.STICK_X + 106f, Pts.STICK_Y - 106f)
        assertEquals(setOf("D", "W"), r.sink.keysDown)
        r.move(1, Pts.STICK_X - 150f, Pts.STICK_Y)
        assertEquals(setOf("A"), r.sink.keysDown)
        // Zwolnienia przed wciśnięciami: po "W+D" najpierw idą W- i D-, dopiero potem A+.
        val keys = r.sink.ofCall(SinkCall.KEY)
        val lastThree = keys.subList(keys.size - 3, keys.size)
        assertEquals(false, lastThree[0].down)
        assertEquals(false, lastThree[1].down)
        assertEquals("A", lastThree[2].code)
        assertEquals(true, lastThree[2].down)
        r.up(1)
        assertTrue(r.sink.keysDown.isEmpty())
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size, "w KBM gałka nie wysyła stanu pada")
    }
}
