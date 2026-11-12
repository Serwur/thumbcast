package app.thumbcast.engine

import app.thumbcast.model.FreeZoneMode
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
    fun padProfileWithMouseGesturesOnScrollsAndClicksAtPoint() {
        val base = noFreeZoneProfile(rightStick = true)
        val on = MouseGesturesTransform.apply(base, true)
        assertEquals(FreeZoneMode.DIRECT, on.gestures.freeZone)
        val r = Rig(on)
        dragAndTap(r)
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty(), "bez kursora względnego")
        assertTrue(r.sink.ofCall(SinkCall.WHEEL).isNotEmpty(), "przeciągnięcie przewija w pionie")
        assertTrue(r.sink.ofCall(SinkCall.HWHEEL).isNotEmpty(), "i w poziomie")
        // Kursor w punkcie dotyku: raz na start przewijania, raz przy tapie (1300, 400) px -> (1248, 432) w wideo.
        assertEquals(listOf(1248 to 432, 1248 to 432), r.sink.ofCall(SinkCall.POSITION).map { it.x to it.y })
        val left = r.mouseButtons().filter { it.mouse == MouseBtn.LEFT }.map { it.down }
        assertEquals(listOf(true, false), left, "tap = LPM, przeciągnięcie bez kliku")
        assertTrue(r.sink.ofCall(SinkCall.PAD).all { it.rx.toInt() == 0 && it.ry.toInt() == 0 }, "bez kamery")
        // Tap dwoma palcami = sam PPM, kursor bez ruchu.
        r.advance(300)
        r.down(3, fx, fy)
        r.advance(20)
        r.down(4, fx + 150f, fy)
        r.advance(60)
        r.up(4)
        r.up(3)
        assertEquals(listOf(true, false), r.mouseButtons().filter { it.mouse == MouseBtn.RIGHT }.map { it.down })
        assertEquals(2, r.sink.ofCall(SinkCall.POSITION).size, "PPM dwoma palcami nie rusza kursora")
        assertNoViolations(r)
    }

    @Test
    fun directProfileWithMouseGesturesOffSendsNothingButMenuSwipe() {
        val r = Rig(MouseGesturesTransform.apply(directProfile(rightStick = true), false))
        dragAndTap(r)
        assertTrue(r.sink.records.isEmpty(), "direct wyłączony: " + r.sink.records)
        r.down(3, 1200f, 10f)
        r.move(3, 1200f, 60f)
        r.move(3, 1205f, 400f)
        r.up(3)
        assertEquals(listOf("menu"), r.sink.ofCall(SinkCall.APP).map { it.app })
        assertNoViolations(r)
    }

    @Test
    fun touchpadProfileWithMouseGesturesOnStaysTouchpad() {
        val r = Rig(MouseGesturesTransform.apply(touchpadProfile(rightStick = true), true))
        dragAndTap(r)
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isNotEmpty(), "gładzik: przeciągnięcie rusza kursorem")
        assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty(), "gładzik klika w miejscu kursora")
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
        // Tap dwoma palcami = sam PPM, kursor bez ruchu.
        r.advance(300)
        r.down(3, fx, fy)
        r.advance(20)
        r.down(4, fx + 150f, fy)
        r.advance(60)
        r.up(3)
        r.up(4)
        assertEquals(listOf(MouseBtn.LEFT to true, MouseBtn.LEFT to false, MouseBtn.RIGHT to true, MouseBtn.RIGHT to false),
            r.mouseButtons().map { it.mouse to it.down })
        assertEquals(listOf(1248 to 432), r.sink.ofCall(SinkCall.POSITION).map { it.x to it.y }, "pozycja tylko z tapu; PPM dwoma palcami nie rusza kursora")
        assertNoViolations(r)
    }

    @Test
    fun cameraProfileWithMouseGesturesOffHasNoTwoFingerRightClick() {
        val r = Rig(MouseGesturesTransform.apply(testProfile(), false))
        r.down(3, fx, fy)
        r.advance(20)
        r.down(4, fx + 150f, fy)
        r.advance(60)
        r.up(4)
        r.up(3)
        assertTrue(r.mouseButtons().isEmpty(), "bez PPM: " + r.sink.records)
        assertNoViolations(r)
    }
}
