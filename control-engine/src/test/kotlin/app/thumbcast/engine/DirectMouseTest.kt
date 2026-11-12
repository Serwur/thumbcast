package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mysz bezpośrednia (`gestures.freeZone = direct`, [DirectMouseGestures], 12.11.2026): tap = kursor w punkcie
 * dotyku + LPM, tap dwoma palcami = PPM w punkcie pierwszego palca, przeciągnięcie = przewijanie w punkcie (pion
 * i poziom, treść idzie za palcem). Ekran 2000 x 1000 px, wideo 1920 x 1080 na całym ekranie (TestSupport).
 */
class DirectMouseTest {

    // Wolna strefa (poza kontrolkami testControls i prawą gałką look).
    private val fx = 1300f
    private val fy = 400f

    // (1300, 400) px -> (1300 * 1920 / 2000, 400 * 1080 / 1000) w układzie wideo.
    private val vx = 1248
    private val vy = 432

    private fun assertNoViolations(r: Rig) = assertEquals(0L, r.engine.violationCount, "naruszenia: " + r.engine.violations)

    private fun buttons(r: Rig): List<Pair<MouseBtn?, Boolean>> = r.mouseButtons().map { it.mouse to it.down }

    private fun wheels(r: Rig): List<Int> = r.sink.ofCall(SinkCall.WHEEL).map { it.wheel }

    private fun hwheels(r: Rig): List<Int> = r.sink.ofCall(SinkCall.HWHEEL).map { it.wheel }

    @Test
    fun factoryCreatesDirectGestures() {
        val config = ProfileAdapter.toConfig(directProfile(), EngineTuning())
        val noop = object : IntentEmitter {
            override fun emit(intent: Intent) {
            }
        }
        val g = FreeZoneGestures.create(config, testViewport(), PxRect(0f, 0f, W.toFloat(), H.toFloat()), noop)
        assertTrue(g is DirectMouseGestures, "direct -> DirectMouseGestures, jest ${g::class.simpleName}")
    }

    @Test
    fun tapMovesCursorToTouchedPointAndClicksLeft() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        r.advance(60)
        r.move(1, fx + 3f, fy - 2f) // drganie palca w progu tapu
        r.up(1)
        val calls = r.sink.records.map { it.call }
        assertEquals(listOf(SinkCall.POSITION, SinkCall.BUTTON, SinkCall.BUTTON), calls, "kolejność: pozycja, potem klik")
        val pos = r.sink.ofCall(SinkCall.POSITION).single()
        assertEquals(listOf(vx, vy, 1920, 1080), listOf(pos.x, pos.y, pos.refW, pos.refH), "punkt DOWN w układzie wideo")
        assertEquals(listOf(MouseBtn.LEFT to true, MouseBtn.LEFT to false), buttons(r))
        assertTrue(r.sink.records.all { it.cause?.action == TouchAction.UP && it.cause?.bindingId == "gesture:tap" })
        assertTrue(wheels(r).isEmpty() && hwheels(r).isEmpty(), "tap nie przewija")
        assertNoViolations(r)
    }

    @Test
    fun tapMapsThroughLetterboxedVideo() {
        val r = Rig(directProfile())
        // Wideo 1800 x 1000 px wyśrodkowane w poziomie (czarne pasy po 100 px), strumień 1920 x 1080.
        r.engine.setViewport(Viewport(W, H, RectPx(100, 0, 1900, 1000), 1920, 1080))
        r.down(1, 1000f, 500f)
        r.up(1)
        val pos = r.sink.ofCall(SinkCall.POSITION).single()
        assertEquals((1000 - 100) * 1920 / 1800 to 500 * 1080 / 1000, pos.x to pos.y)
        // Tap na czarnym pasie: poza obrazem, nic (jak mouse_left_at_point).
        r.sink.clear()
        r.advance(300)
        r.down(2, 1950f, 500f)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "tap poza obrazem: " + r.sink.records)
        // Przeciągnięcie z czarnego pasa też nic (nie przewija w nieznanym miejscu).
        r.down(3, 1950f, 500f)
        r.move(3, 1950f, 600f)
        r.up(3)
        assertTrue(r.sink.records.isEmpty(), "przewijanie spoza obrazu: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun slowTapAndHoldGiveNothing() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        for (i in 0 until 40) {
            r.advance(16)
            r.tick()
        }
        r.up(1)
        assertTrue(r.sink.records.isEmpty(), "przytrzymanie bez ruchu nic nie daje (bez long-pressu): " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun dragScrollsAtTouchPointLikeAPhone() {
        val r = Rig(directProfile(scrollPxPerNotch = 24f))
        r.down(1, fx, fy)
        r.advance(16)
        r.move(1, fx, fy + 30f) // palec w dół o 30 px (> próg 12): treść w dół = kółko w górę
        val pos = r.sink.ofCall(SinkCall.POSITION).single()
        assertEquals(vx to vy, pos.x to pos.y, "kursor w punkcie DOWN, nie w bieżącym")
        assertEquals(SinkCall.POSITION, r.sink.records.first().call, "pozycja przed kółkiem")
        assertEquals(listOf(150), wheels(r), "30 px * 120 / 24")
        r.advance(16)
        r.move(1, fx, fy + 6f) // w górę o 24 px = jeden ząbek w dół
        assertEquals(listOf(150, -120), wheels(r))
        r.advance(16)
        r.move(1, fx - 48f, fy + 6f) // w lewo o 48 px: treść w lewo = kółko w prawo (dodatnie)
        assertEquals(listOf(240), hwheels(r))
        r.advance(16)
        r.move(1, fx, fy + 6f) // w prawo o 48 px = w lewo
        assertEquals(listOf(240, -240), hwheels(r))
        r.up(1)
        assertTrue(r.mouseButtons().isEmpty(), "przeciągnięcie nie klika")
        assertEquals(1, r.sink.ofCall(SinkCall.POSITION).size, "kursor ustawiany raz na gest")
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty(), "bez ruchu względnego")
        assertTrue(r.sink.records.all { it.cause?.bindingId == "gesture:scroll" && it.cause?.action == TouchAction.MOVE })
        assertNoViolations(r)
    }

    @Test
    fun diagonalDragScrollsBothAxesAndKeepsFractions() {
        val r = Rig(directProfile(scrollPxPerNotch = 24f))
        r.down(1, fx, fy)
        r.move(1, fx + 24f, fy + 24f)
        assertEquals(listOf(120), wheels(r))
        assertEquals(listOf(-120), hwheels(r))
        // 1 px = 5 jednostek: reszty ułamkowe nie giną (wysoka rozdzielczość, płynnie).
        for (i in 1..4) r.move(1, fx + 24f, fy + 24f + i * 0.5f)
        assertEquals(listOf(120, 2, 3, 2, 3), wheels(r))
        r.up(1)
        assertTrue(r.mouseButtons().isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun hugeJumpIsCappedPerEvent() {
        val r = Rig(directProfile(scrollPxPerNotch = 4f))
        r.down(1, fx, fy)
        r.move(1, fx, fy + 500f)
        assertEquals(listOf(10 * 120), wheels(r), "najwyżej maxNotchesPerEvent ząbków na zdarzenie")
        assertNoViolations(r)
    }

    @Test
    fun twoFingerTapRightClicksAtFirstFinger() {
        for (firstUp in listOf(1, 2)) {
            val r = Rig(directProfile())
            r.down(1, fx, fy)
            r.advance(30)
            r.down(2, fx + 160f, fy + 20f)
            r.advance(80)
            r.up(firstUp)
            r.advance(20)
            r.up(3 - firstUp)
            assertEquals(listOf(MouseBtn.RIGHT to true, MouseBtn.RIGHT to false), buttons(r), "bez LPM (firstUp=$firstUp)")
            val pos = r.sink.ofCall(SinkCall.POSITION).single()
            assertEquals(vx to vy, pos.x to pos.y, "punkt pierwszego palca")
            assertTrue(r.sink.records.all { it.cause?.bindingId == "gesture:tap2" && it.cause?.pointerId == 3 - firstUp })
            assertTrue(wheels(r).isEmpty() && hwheels(r).isEmpty())
            assertNoViolations(r)
        }
    }

    @Test
    fun slowTwoFingerTapGivesNothing() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        r.down(2, fx + 160f, fy)
        r.advance(220 + 100 + 1)
        r.up(2)
        r.up(1)
        assertTrue(r.sink.records.isEmpty(), "za wolno: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun twoFingerDragDoesNothing() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        r.down(2, fx + 160f, fy)
        r.moveAll(TouchPointer(1, fx, fy + 60f), TouchPointer(2, fx + 160f, fy + 60f))
        r.moveAll(TouchPointer(1, fx + 40f, fy + 160f), TouchPointer(2, fx + 200f, fy + 160f))
        r.up(1)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "przeciągnięcie dwoma palcami: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun secondFingerDuringScrollIsIgnoredAndThirdFingerCancelsRightClick() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        r.move(1, fx, fy + 48f)
        r.down(2, fx + 200f, fy) // tap drugim palcem w trakcie przewijania: nic
        r.up(2)
        r.move(1, fx, fy + 96f)
        r.up(1)
        assertTrue(r.mouseButtons().isEmpty(), "bez kliku: " + r.sink.records)
        assertEquals(listOf(240, 240), wheels(r), "przewijanie trwa")
        // Trzy palce naraz: ani PPM, ani LPM.
        r.sink.clear()
        r.advance(300)
        r.down(3, fx, fy)
        r.down(4, fx + 160f, fy)
        r.down(5, fx + 320f, fy)
        r.up(5)
        r.up(4)
        r.up(3)
        assertTrue(r.sink.records.isEmpty(), "trzy palce: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun tapWorksWhileHoldingStickAndDragScrollsWhileHoldingButton() {
        val r = Rig(directProfile(rightStick = true))
        // Prawa gałka (look, środek 1200, 750) trzymana i wychylona.
        r.down(9, 1200f, 750f)
        r.move(9, 1260f, 750f)
        assertTrue(r.sink.lastPad!!.rx.toInt() > 0, "gałka działa")
        // Lewa gałka (pływająca) też trzymana.
        r.down(8, Pts.STICK_X, Pts.STICK_Y)
        r.move(8, Pts.STICK_X - 80f, Pts.STICK_Y)
        // Tap w wolnej strefie: zwykły LPM (palce na kontrolkach nie tworzą pary z palcem wolnej strefy).
        r.down(1, fx, fy)
        r.advance(50)
        r.up(1)
        assertEquals(listOf(MouseBtn.LEFT to true, MouseBtn.LEFT to false), buttons(r))
        assertEquals(vx to vy, r.sink.ofCall(SinkCall.POSITION).single().let { it.x to it.y })
        // Przycisk A trzymany, przeciągnięcie w wolnej strefie przewija.
        r.down(7, Pts.A_X, Pts.A_Y)
        r.down(2, fx, fy)
        r.move(2, fx, fy - 24f)
        assertEquals(listOf(-120), wheels(r))
        r.up(2)
        // Tap dwoma palcami przy trzymanych kontrolkach: PPM.
        r.advance(300)
        r.down(3, fx, fy)
        r.down(4, fx + 160f, fy)
        r.up(4)
        r.up(3)
        assertEquals(MouseBtn.RIGHT to false, buttons(r).last())
        val pad = r.sink.lastPad!!
        assertTrue(pad.rx.toInt() > 0 && pad.lx.toInt() < 0, "gałki dalej wychylone")
        r.up(7)
        r.up(8)
        r.up(9)
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun edgeSwipeOpensMenuWithoutScrolling() {
        val r = Rig(directProfile())
        r.down(1, 1200f, 10f)
        r.move(1, 1200f, 60f)
        r.move(1, 1205f, 400f)
        r.up(1)
        assertEquals(listOf("menu"), r.sink.ofCall(SinkCall.APP).map { it.app })
        assertTrue(r.sink.ofCall(SinkCall.POSITION).isEmpty() && wheels(r).isEmpty(), "menu bez przewijania")
        // Z paska krawędzi w bok: zwykłe przewijanie (to nie gest menu).
        r.sink.clear()
        r.down(2, 1200f, 10f)
        r.move(2, 1300f, 12f)
        r.up(2)
        assertEquals(listOf(-500), hwheels(r))
        assertTrue(r.sink.ofCall(SinkCall.APP).isEmpty())
        assertNoViolations(r)
    }

    @Test
    fun tapDisabledStillScrolls() {
        val r = Rig(directProfile(tapEnabled = false))
        r.down(1, fx, fy)
        r.up(1)
        r.down(2, fx, fy)
        r.down(3, fx + 160f, fy)
        r.up(3)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "tap wyłączony: " + r.sink.records)
        r.down(4, fx, fy)
        r.move(4, fx, fy + 24f)
        r.up(4)
        assertEquals(listOf(120), wheels(r))
        assertNoViolations(r)
    }

    @Test
    fun cancelAndReleaseAllLeaveNothingBehind() {
        val r = Rig(directProfile())
        r.down(1, fx, fy)
        r.move(1, fx, fy + 30f)
        r.cancel()
        r.sink.clear()
        // Po CANCEL ten sam id zaczyna od nowa: tap działa.
        r.down(1, fx, fy)
        r.up(1)
        assertEquals(listOf(MouseBtn.LEFT to true, MouseBtn.LEFT to false), buttons(r))
        r.down(2, fx, fy)
        r.down(3, fx + 160f, fy)
        r.engine.releaseAll()
        r.sink.clear()
        r.up(3)
        r.up(2)
        assertTrue(r.sink.records.isEmpty(), "po releaseAll stare palce nic nie dają: " + r.sink.records)
        assertNoViolations(r)
    }
}
