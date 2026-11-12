package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tap dwoma palcami w trybie kamery (`freeZone = camera`, [GestureArbiter], 12.11.2026): PPM w punkcie pierwszego
 * palca, gdy `tapTarget.enabled` (przełącznik „Gesty myszy”). Kamera przeciąganiem, tap LPM, long-press PPM i pinch
 * działają jak dotąd.
 */
class CameraTwoFingerTapTest {

    private val fx = Pts.FREE_X
    private val fy = Pts.FREE_Y

    private fun assertNoViolations(r: Rig) = assertEquals(0L, r.engine.violationCount, "naruszenia: " + r.engine.violations)

    private fun buttons(r: Rig): List<Pair<MouseBtn?, Boolean>> = r.mouseButtons().map { it.mouse to it.down }

    private fun profile(tap: Boolean = true, pinch: Boolean = true) = testProfile().let {
        it.copy(gestures = it.gestures.copy(
            tapTarget = it.gestures.tapTarget.copy(enabled = tap),
            pinchZoom = it.gestures.pinchZoom.copy(enabled = pinch)
        ))
    }

    private fun twoFingerTap(r: Rig, firstUp: Int) {
        r.down(1, fx, fy)
        r.advance(25)
        r.down(2, fx + 150f, fy + 10f)
        r.advance(80)
        r.up(firstUp)
        r.advance(15)
        r.up(3 - firstUp)
    }

    @Test
    fun twoFingerTapRightClicksWithoutMovingCursor() {
        for (pinch in listOf(true, false)) {
            for (firstUp in listOf(1, 2)) {
                val r = Rig(profile(pinch = pinch))
                twoFingerTap(r, firstUp)
                val what = "pinch=$pinch firstUp=$firstUp"
                assertEquals(listOf(MouseBtn.RIGHT to true, MouseBtn.RIGHT to false), buttons(r), what)
                assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty(), "PPM dwoma palcami nie rusza kursora: $what")
                assertTrue(r.sink.ofCall(SinkCall.WHEEL).isEmpty(), what)
                assertTrue(r.sink.ofCall(SinkCall.PAD).all { it.rx.toInt() == 0 && it.ry.toInt() == 0 }, "bez kamery: $what")
                assertNoViolations(r)
            }
        }
    }

    @Test
    fun withoutTapThereIsNoTwoFingerRightClick() {
        for (pinch in listOf(true, false)) {
            val r = Rig(profile(tap = false, pinch = pinch))
            twoFingerTap(r, 1)
            assertTrue(r.mouseButtons().isEmpty(), "pinch=$pinch: " + r.sink.records)
            assertNoViolations(r)
        }
    }

    @Test
    fun pinchStillScrollsAndCancelsRightClick() {
        val r = Rig(profile())
        r.down(1, fx, fy)
        r.down(2, fx + 150f, fy)
        r.move(2, fx + 250f, fy) // rozsunięcie o 100 px = 2 ząbki
        r.up(2)
        r.up(1)
        assertEquals(listOf(240), r.sink.ofCall(SinkCall.WHEEL).map { it.wheel })
        assertTrue(r.mouseButtons().isEmpty(), "pinch to nie tap: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun movingPairWithoutPinchGivesNothing() {
        val r = Rig(profile(pinch = false))
        r.down(1, fx, fy)
        r.down(2, fx + 150f, fy)
        r.moveAll(TouchPointer(1, fx + 60f, fy), TouchPointer(2, fx + 210f, fy))
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "para w ruchu bez pinchu: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun slowPairGivesNothingAndLongPressStillWorks() {
        val r = Rig(profile())
        r.down(1, fx, fy)
        r.down(2, fx + 150f, fy)
        r.advance(400)
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "za wolno: " + r.sink.records)
        // Jeden palec przytrzymany: long-press PPM jak dotąd.
        r.down(3, fx, fy)
        r.advance(500)
        r.tick()
        r.up(3)
        assertEquals(listOf(MouseBtn.RIGHT to true, MouseBtn.RIGHT to false), buttons(r))
        assertNoViolations(r)
    }

    @Test
    fun reusedPointerIdOfLiftedPairFingerKeepsItsCamera() {
        // Regresja (100k iteracji polityki): para (1, 2), 1 w górę, nowy palec dostaje id 1 i kręci kamerą, potem
        // ruch czekającego palca 2 nie może przestawić nowego palca 1 (kamera zostałaby trzymana po UP).
        val r = Rig(profile())
        r.down(1, fx, fy)
        r.down(2, fx + 150f, fy)
        r.up(1)
        r.down(1, fx, fy + 100f)
        r.advance(16)
        r.move(1, fx + 80f, fy + 100f)
        assertTrue(r.sink.lastPad!!.rx.toInt() != 0, "nowy palec 1 = kamera")
        r.move(2, fx + 250f, fy)
        r.advance(16)
        r.up(1)
        r.up(2)
        assertTrue(r.sink.isZero(), "kamera zwolniona")
        assertTrue(r.mouseButtons().isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun cameraDragFingerDoesNotPairForRightClick() {
        val r = Rig(profile())
        r.down(1, fx, fy)
        r.advance(16)
        r.move(1, fx + 80f, fy)
        r.down(2, fx + 300f, fy) // drugi palec przy kamerze: pinch jak dotąd, bez PPM
        r.up(2)
        r.up(1)
        assertTrue(r.mouseButtons().isEmpty(), "bez kliku: " + r.sink.records)
        assertNoViolations(r)
    }
}
