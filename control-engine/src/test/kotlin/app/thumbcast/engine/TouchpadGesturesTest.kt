package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.ProfileMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wolna strefa w trybie touchpada (TouchpadGestures). Punkty F1 i F2 leżą w wolnej strefie także w profilu
 * z widoczną prawą gałką (touchpadProfile(rightStick = true)).
 */
class TouchpadGesturesTest {

    private val f1x = 1300f
    private val f1y = 400f
    private val f2x = 1500f
    private val f2y = 400f

    private fun buttonSeq(r: Rig): List<String> {
        val out = ArrayList<String>()
        for (rec in r.mouseButtons()) out.add((rec.mouse?.name ?: "?") + (if (rec.down) "+" else "-"))
        return out
    }

    private fun moves(r: Rig): List<Pair<Int, Int>> = r.sink.ofCall(SinkCall.MOVE).map { Pair(it.dx, it.dy) }

    private fun assertNoViolations(r: Rig) = assertEquals(0L, r.engine.violationCount, "naruszenia: " + r.engine.violations)

    // ------------------------------------------------------------------ 1 palec: kursor

    @Test
    fun oneFingerDragMovesMouseWithFractionalRemainder() {
        val r = Rig(touchpadProfile(sensitivity = 1.5f))
        r.down(1, f1x, f1y)
        r.move(1, f1x + 5f, f1y) // w progu tapu: jeszcze nic
        assertTrue(r.sink.records.isEmpty())
        r.move(1, f1x + 20f, f1y - 4f) // start: przesunięcie od punktu DOWN, 20 x 1.5 = 30, -4 x 1.5 = -6
        r.move(1, f1x + 21f, f1y - 4f) // 1.5 -> 1, reszta 0.5
        r.move(1, f1x + 22f, f1y - 4f) // 1.5 + 0.5 -> 2
        r.up(1)
        assertEquals(listOf(Pair(30, -6), Pair(1, 0), Pair(2, 0)), moves(r))
        for (rec in r.sink.records) assertEquals(TouchAction.MOVE, rec.cause?.action)
        assertTrue(r.mouseButtons().isEmpty(), "ruch kursora nie klika")
        assertTrue(r.sink.ofCall(SinkCall.PAD).isEmpty(), "touchpad nie rusza prawej gałki")
        assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun cameraModeSameSwipeDrivesStickNotMouse() {
        // Kontrola: ta sama ścieżka w Pad+ (freeZone = camera) to prawa gałka, a nie mysz. Nigdy oba naraz.
        val r = Rig(testProfile())
        r.down(1, f1x, 500f)
        r.advance(16)
        r.move(1, f1x + 40f, 500f)
        r.up(1)
        assertTrue(r.sink.ofCall(SinkCall.PAD).isNotEmpty())
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty())
    }

    // ------------------------------------------------------------------ 1 palec: tap

    @Test
    fun tapClicksLeftAtCursorOnUp() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(100)
        r.move(1, f1x + 3f, f1y + 2f)
        assertTrue(r.sink.records.isEmpty())
        r.up(1)
        assertEquals(listOf("LEFT+", "LEFT-"), buttonSeq(r))
        assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty(), "klik w miejscu kursora, bez mousePosition")
        for (rec in r.sink.records) {
            assertEquals(TouchAction.UP, rec.cause?.action)
            assertEquals(1, rec.cause?.pointerId)
        }
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun tooSlowForTapAndTooShortForHoldGivesNothing() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(300) // > tap 220, < long-press 450
        r.up(1)
        assertTrue(r.sink.records.isEmpty())
    }

    @Test
    fun holdWithoutTicksClicksOnceOnUp() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(600)
        r.up(1)
        assertEquals(listOf("LEFT+", "LEFT-"), buttonSeq(r))
        assertNoViolations(r)
    }

    // ------------------------------------------------------------------ przytrzymanie i przeciąganie

    @Test
    fun holdThenDragKeepsLeftButtonForWholeTouch() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(200)
        r.tick()
        assertTrue(r.sink.records.isEmpty(), "przed progiem tick nic nie daje")
        r.advance(300)
        r.tick() // rozpoznanie przytrzymania trzymanego palca (ONE_SHOT_TIMED, cykl TICK)
        val press = r.mouseButtons().single()
        assertEquals(MouseBtn.LEFT, press.mouse)
        assertTrue(press.down)
        assertEquals(CycleKind.TICK, press.cause?.cycle)
        assertEquals(BindingKind.ONE_SHOT_TIMED, press.cause?.kind)
        r.advance(16)
        r.move(1, f1x + 50f, f1y + 10f)
        r.advance(16)
        r.move(1, f1x + 80f, f1y + 10f)
        assertEquals(listOf(Pair(50, 10), Pair(30, 0)), moves(r))
        assertTrue(r.sink.mouseDown.contains(MouseBtn.LEFT), "LMB trzymany podczas przeciągania")
        r.advance(50)
        r.tick()
        r.tick()
        assertEquals(2, moves(r).size, "tick nie rusza kursora")
        r.up(1)
        assertEquals(listOf("LEFT+", "LEFT-"), buttonSeq(r))
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun dragAfterMoveIsCursorNotHold() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.move(1, f1x + 30f, f1y)
        r.advance(1000)
        r.tick()
        r.move(1, f1x + 40f, f1y)
        r.up(1)
        assertTrue(r.mouseButtons().isEmpty(), "palec, który już przesuwa kursor, nie staje się przeciąganiem")
        assertEquals(2, moves(r).size)
    }

    // ------------------------------------------------------------------ 2 palce: tap

    @Test
    fun twoFingerTapIsRightClickOnLastUp() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(30)
        r.down(2, f2x, f2y)
        r.advance(80)
        r.up(1)
        assertTrue(r.sink.records.isEmpty(), "pierwszy palec w górę: jeszcze nic")
        r.advance(30)
        r.up(2)
        assertEquals(listOf("RIGHT+", "RIGHT-"), buttonSeq(r))
        for (rec in r.sink.records) {
            assertEquals(2, rec.cause?.pointerId)
            assertEquals(TouchAction.UP, rec.cause?.action)
        }
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun slowTwoFingerTapGivesNothing() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        r.advance(400)
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty())
    }

    @Test
    fun thirdFingerCancelsTwoFingerTap() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        r.down(3, 1300f, 480f)
        r.advance(50)
        r.up(3)
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty())
    }

    @Test
    fun twoFingerSpreadDoesNotZoom() {
        // Pinch w trybie touchpada jest wyłączony: rozsuwanie bez ruchu środka nic nie daje.
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        // Oba palce w jednym zdarzeniu MOVE (silnik najpierw aktualizuje wszystkie pozycje, potem rozpoznaje).
        for (i in 1..10) r.moveAll(TouchPointer(1, f1x - 8f * i, f1y), TouchPointer(2, f2x + 8f * i, f2y))
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun twoFingersMovedInOneEventScrollOnce() {
        val r = Rig(touchpadProfile(scrollPxPerNotch = 24f))
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        r.moveAll(TouchPointer(1, f1x, f1y + 24f), TouchPointer(2, f2x, f2y + 24f))
        r.moveAll(TouchPointer(1, f1x, f1y + 48f), TouchPointer(2, f2x, f2y + 48f))
        r.up(1)
        r.up(2)
        assertEquals(listOf(120, 120), r.sink.ofCall(SinkCall.WHEEL).map { it.wheel })
        assertNoViolations(r)
    }

    // ------------------------------------------------------------------ 2 palce: przewijanie

    @Test
    fun twoFingerVerticalScrollLocksAxis() {
        val r = Rig(touchpadProfile(scrollPxPerNotch = 24f))
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        r.move(1, f1x, f1y + 20f) // środek +10: w progu
        assertTrue(r.sink.records.isEmpty())
        r.move(2, f2x, f2y + 20f) // środek +20 > 12: start, oś pionowa, 20 px = 100 jednostek
        r.move(1, f1x + 100f, f1y + 20f) // ruch w bok po blokadzie osi: nic
        r.move(2, f2x + 100f, f2y + 20f)
        r.move(1, f1x + 100f, f1y + 44f)
        r.move(2, f2x + 100f, f2y + 44f) // +24 px = +120
        r.up(1)
        r.up(2)
        val wheel = r.sink.ofCall(SinkCall.WHEEL)
        assertEquals(220, wheel.sumOf { it.wheel }, "palce w dół = kółko w górę (dodatnie)")
        assertTrue(wheel.all { it.wheel > 0 })
        assertTrue(r.sink.ofCall(SinkCall.HWHEEL).isEmpty(), "oś pozioma zablokowana")
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty(), "przewijanie nie rusza kursora")
        assertTrue(r.mouseButtons().isEmpty(), "przewijanie nie klika")
        for (rec in wheel) assertEquals(TouchAction.MOVE, rec.cause?.action)
        assertNoViolations(r)
    }

    @Test
    fun twoFingerHorizontalScrollLocksAxis() {
        val r = Rig(touchpadProfile(scrollPxPerNotch = 24f))
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        r.move(1, f1x - 48f, f1y + 5f)
        r.move(2, f2x - 48f, f2y + 5f) // środek: -48 w poziomie, +5 w pionie -> oś pozioma
        r.move(1, f1x - 48f, f1y + 60f)
        r.move(2, f2x - 48f, f2y + 60f) // ruch w pionie po blokadzie: nic
        r.up(1)
        r.up(2)
        val h = r.sink.ofCall(SinkCall.HWHEEL)
        assertEquals(240, h.sumOf { it.wheel }, "palce w lewo = kółko w prawo (dodatnie)")
        assertTrue(r.sink.ofCall(SinkCall.WHEEL).isEmpty(), "oś pionowa zablokowana")
        assertTrue(r.mouseButtons().isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun scrollAfterCursorMoveWithSecondFinger() {
        // Palec przesuwa kursor, drugi dołącza: od tej chwili przewijanie, kursor stoi; tapu już nie ma.
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.move(1, f1x + 30f, f1y)
        val movesBefore = moves(r).size
        r.down(2, f2x, f2y)
        r.move(1, f1x + 30f, f1y - 30f)
        r.move(2, f2x, f2y - 30f)
        r.up(2)
        r.up(1)
        assertEquals(movesBefore, moves(r).size)
        assertTrue(r.sink.ofCall(SinkCall.WHEEL).sumOf { it.wheel } < 0, "palce w górę = kółko w dół")
        assertTrue(r.mouseButtons().isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun ticksDuringTwoFingersGiveNothing() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.down(2, f2x, f2y)
        for (i in 0 until 10) {
            r.advance(100)
            r.tick()
        }
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "trzymanie dwóch palców nie jest przytrzymaniem ani tapem")
    }

    // ------------------------------------------------------------------ edge swipe, sprzątanie, gałka

    @Test
    fun edgeSwipeStillOpensMenu() {
        val r = Rig(touchpadProfile())
        r.down(1, 1200f, 10f)
        r.move(1, 1205f, 150f)
        r.up(1)
        assertEquals(listOf("menu"), r.sink.ofCall(SinkCall.APP).map { it.app })
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty())
    }

    @Test
    fun cancelReleasesHeldDragAndForgetsState() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.advance(500)
        r.tick()
        assertTrue(r.sink.mouseDown.contains(MouseBtn.LEFT))
        r.cancel()
        assertTrue(r.sink.isZero())
        r.sink.clear()
        r.advance(10)
        r.down(2, f1x, f1y)
        r.advance(50)
        r.up(2)
        assertEquals(listOf("LEFT+", "LEFT-"), buttonSeq(r), "po CANCEL zwykły tap działa")
        assertNoViolations(r)
    }

    @Test
    fun releaseAllReleasesHeldDragAndTicksStaySilent() {
        val r = Rig(touchpadProfile())
        r.down(1, f1x, f1y)
        r.down(2, Pts.A_X, Pts.A_Y) // drugi palec na przycisku A (kontrolka), nie w wolnej strefie
        r.advance(500)
        r.tick()
        assertTrue(r.sink.mouseDown.contains(MouseBtn.LEFT))
        r.engine.releaseAll()
        assertTrue(r.sink.isZero())
        r.sink.clear()
        r.advance(1000)
        r.tick()
        assertTrue(r.sink.records.isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun bigPictureRightStickControlAndTouchpadTogether() {
        val r = Rig(touchpadProfile(rightStick = true))
        r.down(1, 1200f, 750f) // widoczna prawa gałka (stała)
        r.move(1, 1260f, 750f)
        r.down(2, f1x, f1y) // wolna strefa: mysz
        r.move(2, f1x + 40f, f1y)
        val pad = r.sink.lastPad
        assertTrue(pad != null && pad.rx > 0, "prawa gałka z kontrolki")
        assertEquals(listOf(Pair(40, 0)), moves(r))
        r.up(1)
        r.up(2)
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun keyboardMouseProfileUsesTouchpadToo() {
        val r = Rig(touchpadProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, f1x, f1y)
        r.move(1, f1x + 20f, f1y)
        r.up(1)
        assertEquals(listOf(Pair(20, 0)), moves(r))
        assertTrue(r.mouseButtons().isEmpty(), "bez RMB-look")
    }
}
