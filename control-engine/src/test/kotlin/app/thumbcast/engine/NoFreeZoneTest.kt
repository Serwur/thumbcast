package app.thumbcast.engine

import app.thumbcast.model.ProfileMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wolna strefa wyłączona (`gestures.freeZone = none`, EdgeSwipeOnlyGestures, 12.11.2026): dotyk poza kontrolkami
 * nic nie wysyła, poza gestem menu od górnej krawędzi. Kontrolki działają bez zmian.
 */
class NoFreeZoneTest {

    private val f1x = 1300f
    private val f1y = 400f
    private val f2x = 1500f
    private val f2y = 400f

    private fun assertNoViolations(r: Rig) = assertEquals(0L, r.engine.violationCount, "naruszenia: " + r.engine.violations)

    @Test
    fun factoryPicksEdgeSwipeOnly() {
        val config = ProfileAdapter.toConfig(noFreeZoneProfile(), EngineTuning())
        val g = FreeZoneGestures.create(config, testViewport(), PxRect(0f, 0f, W.toFloat(), H.toFloat()), NoopEmitter)
        assertTrue(g is EdgeSwipeOnlyGestures)
    }

    @Test
    fun dragTapLongPressAndTwoFingersGiveNothing() {
        val r = Rig(noFreeZoneProfile())
        // Przeciągnięcie (w trybie kamery prawa gałka, w touchpadzie ruch myszy).
        r.down(1, f1x, f1y)
        r.advance(16)
        r.move(1, f1x + 60f, f1y + 20f)
        r.advance(16)
        r.move(1, f1x + 160f, f1y + 40f)
        r.up(1)
        // Tap.
        r.advance(100)
        r.down(2, f1x, f1y)
        r.advance(50)
        r.up(2)
        // Long-press z tickami i przy UP.
        r.advance(100)
        r.down(3, f1x, f1y)
        for (i in 0 until 40) {
            r.advance(16)
            r.tick()
        }
        r.up(3)
        r.advance(100)
        r.down(4, f1x, f1y)
        r.advance(700)
        r.up(4)
        // Dwa palce: tap, rozsuwanie (pinch) i przewijanie.
        r.advance(100)
        r.down(5, f1x, f1y)
        r.down(6, f2x, f2y)
        r.advance(40)
        r.up(5)
        r.up(6)
        r.down(7, f1x, f1y)
        r.down(8, f2x, f2y)
        r.moveAll(TouchPointer(7, f1x - 80f, f1y), TouchPointer(8, f2x + 80f, f2y))
        r.moveAll(TouchPointer(7, f1x - 80f, f1y + 120f), TouchPointer(8, f2x + 80f, f2y + 120f))
        r.up(7)
        r.up(8)
        assertTrue(r.sink.records.isEmpty(), "wolna strefa none nic nie wysyła: " + r.sink.records)
        assertNoViolations(r)
    }

    @Test
    fun edgeSwipeFromTopStillOpensMenuOnce() {
        val r = Rig(noFreeZoneProfile(rightStick = true))
        r.down(1, 1200f, 10f)
        r.move(1, 1200f, 60f)
        r.move(1, 1205f, 200f)
        r.move(1, 1205f, 400f)
        r.up(1)
        assertEquals(listOf("menu"), r.sink.ofCall(SinkCall.APP).map { it.app })
        assertEquals(1, r.sink.records.size, "tylko menu: " + r.sink.records)
        assertEquals(TouchAction.MOVE, r.sink.records[0].cause?.action)
        assertNoViolations(r)
    }

    @Test
    fun edgeSwipeDisabledGivesNothing() {
        val r = Rig(noFreeZoneProfile(edgeSwipe = false))
        r.down(1, 1200f, 10f)
        r.move(1, 1200f, 300f)
        r.up(1)
        assertTrue(r.sink.records.isEmpty())
    }

    @Test
    fun sidewaysOrLateDownwardSwipeFromEdgeGivesNothing() {
        val r = Rig(noFreeZoneProfile())
        // W bok z paska krawędzi: palec przestaje być kandydatem, późniejszy ruch w dół też nie otwiera menu.
        r.down(1, 1200f, 10f)
        r.move(1, 1300f, 15f)
        r.move(1, 1300f, 400f)
        r.up(1)
        // Start poniżej paska krawędzi.
        r.down(2, 1200f, 200f)
        r.move(2, 1200f, 600f)
        r.up(2)
        // Edge swipe na tickach (bez ruchu) nie zachodzi.
        r.down(3, 1200f, 10f)
        for (i in 0 until 10) {
            r.advance(16)
            r.tick()
        }
        r.up(3)
        assertTrue(r.sink.records.isEmpty(), "" + r.sink.records)
    }

    @Test
    fun controlsWorkAlongsideIgnoredFreeZone() {
        val r = Rig(noFreeZoneProfile(rightStick = true))
        r.down(1, 1200f, 750f) // widoczna prawa gałka (stała)
        r.move(1, 1260f, 750f)
        r.down(2, Pts.A_X, Pts.A_Y)
        r.down(3, f1x, f1y) // wolna strefa: nic
        r.advance(16)
        r.move(3, f1x + 80f, f1y)
        val pad = r.sink.lastPad
        assertTrue(pad != null && pad.rx > 0 && pad.buttons == PadFlags.A, "prawa gałka i A z kontrolek: $pad")
        assertTrue(r.sink.ofCall(SinkCall.MOVE).isEmpty() && r.mouseButtons().isEmpty())
        r.up(1)
        r.up(2)
        r.up(3)
        assertTrue(r.sink.isZero())
        assertNoViolations(r)
    }

    @Test
    fun keyboardMouseProfileHasNoMouseLook() {
        val r = Rig(noFreeZoneProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, f1x, f1y)
        r.advance(16)
        r.move(1, f1x + 100f, f1y)
        r.up(1)
        assertTrue(r.sink.records.isEmpty(), "bez RMB-look i ruchu myszy: " + r.sink.records)
    }

    @Test
    fun cancelAndReleaseAllForgetEdgeCandidate() {
        val r = Rig(noFreeZoneProfile())
        r.down(1, 1200f, 10f)
        r.cancel()
        r.down(2, 1200f, 300f)
        r.move(2, 1200f, 700f)
        r.up(2)
        r.down(3, 1200f, 10f)
        r.engine.releaseAll()
        r.sink.clear()
        r.advance(100)
        r.tick()
        assertTrue(r.sink.records.isEmpty())
        assertNoViolations(r)
    }

    private object NoopEmitter : IntentEmitter {
        override fun emit(intent: Intent) {
        }
    }
}
