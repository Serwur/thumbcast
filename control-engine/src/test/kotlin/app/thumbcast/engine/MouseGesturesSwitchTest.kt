package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.MouseGesturesTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Przełącznik „Gesty myszy” (12.11.2026) w silniku: profil efektywny z [MouseGesturesTransform] daje oczekiwane
 * wyjście. Właściwości polityki na losowych profilach: `ActionPolicyPropertyTest.mouseGesturesSwitchChaoticStreams`.
 */
class MouseGesturesSwitchTest {

    private val fx = 1300f
    private val fy = 400f

    private fun assertNoViolations(r: Rig) = assertEquals(0L, r.engine.violationCount, "naruszenia: " + r.engine.violations)

    private fun dragAndTap(r: Rig) {
        r.down(1, fx, fy)
        r.advance(16)
        r.move(1, fx + 60f, fy + 20f)
        r.advance(16)
        r.move(1, fx + 160f, fy + 40f)
        r.up(1)
        r.advance(200)
        r.down(2, fx, fy)
        r.advance(50)
        r.up(2)
    }

    @Test
    fun padProfileWithMouseGesturesOnMovesCursorAndClicks() {
        val base = noFreeZoneProfile(rightStick = true)
        val r = Rig(MouseGesturesTransform.apply(base, true))
        dragAndTap(r)
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isNotEmpty(), "przeciągnięcie rusza kursorem")
        val left = r.mouseButtons().filter { it.mouse == MouseBtn.LEFT }.map { it.down }
        assertEquals(listOf(true, false), left, "tap = LPM")
        assertTrue(r.sink.ofCall(SinkCall.PAD).all { it.rx.toInt() == 0 && it.ry.toInt() == 0 }, "bez kamery")
        assertNoViolations(r)
    }

    @Test
    fun padProfileWithMouseGesturesOffSendsNothing() {
        val r = Rig(MouseGesturesTransform.apply(noFreeZoneProfile(rightStick = true), false))
        dragAndTap(r)
        assertTrue(r.sink.records.isEmpty(), "domyślnie pad bez myszy: " + r.sink.records)
    }

    @Test
    fun touchpadProfileWithMouseGesturesOffSendsNothingButMenuSwipe() {
        val r = Rig(MouseGesturesTransform.apply(touchpadProfile(rightStick = true), false))
        dragAndTap(r)
        assertTrue(r.sink.records.isEmpty(), "touchpad wyłączony: " + r.sink.records)
        r.down(3, 1200f, 10f)
        r.move(3, 1200f, 60f)
        r.move(3, 1205f, 400f)
        r.up(3)
        assertEquals(listOf("menu"), r.sink.ofCall(SinkCall.APP).map { it.app })
        assertNoViolations(r)
    }

    @Test
    fun cameraProfileWithMouseGesturesOffKeepsCameraWithoutClicks() {
        val r = Rig(MouseGesturesTransform.apply(testProfile(), false))
        dragAndTap(r)
        // Long-press (przy UP po progu) też nic nie daje.
        r.advance(100)
        r.down(3, fx, fy)
        r.advance(700)
        r.up(3)
        assertTrue(r.mouseButtons().isEmpty(), "bez klików myszy")
        assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty(), "bez pozycji myszy")
        assertTrue(r.sink.ofCall(SinkCall.PAD).any { it.rx.toInt() != 0 || it.ry.toInt() != 0 }, "kamera = prawa gałka")
        assertNoViolations(r)
    }

    @Test
    fun cameraProfileWithMouseGesturesOnClicksAtPoint() {
        val r = Rig(MouseGesturesTransform.apply(testProfile(), true))
        r.down(2, fx, fy)
        r.advance(50)
        r.up(2)
        assertEquals(1, r.sink.ofCall(SinkCall.POSITION).size, "tap w punkcie")
        assertEquals(listOf(true, false), r.mouseButtons().map { it.down })
        assertNoViolations(r)
    }
}
