package app.thumbcast.android.overlay

import app.thumbcast.engine.TouchAction
import app.thumbcast.engine.TouchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prosty MotionSource do testów: lista (id, x, y, toolType) w kolejności indeksów. */
class FakeMotion(
    override val actionMasked: Int,
    override val actionIndex: Int,
    private val ptrs: List<FakePtr>,
    override val eventTimeMs: Long,
) : MotionSource {
    override val pointerCount: Int
        get() = ptrs.size

    override fun pointerId(index: Int): Int = ptrs[index].id
    override fun x(index: Int): Float = ptrs[index].x
    override fun y(index: Int): Float = ptrs[index].y
    override fun toolType(index: Int): Int = ptrs[index].tool
}

data class FakePtr(val id: Int, val x: Float, val y: Float, val tool: Int = MotionActions.TOOL_TYPE_FINGER)

class MotionTranslatorTest {

    private fun ids(e: TouchEvent): List<Int> = e.pointers.map { it.id }

    @Test
    fun singleFingerDownMoveUp() {
        val t = MotionTranslator()
        val down = t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(0, 10f, 20f)), 100L))
        assertEquals(1, down.size)
        assertEquals(TouchAction.DOWN, down[0].action)
        assertEquals(0, down[0].actionPointerId)
        assertEquals(100L, down[0].timeMs)
        assertEquals(10f, down[0].pointers[0].x, 0f)

        val move = t.translate(FakeMotion(MotionActions.ACTION_MOVE, 0, listOf(FakePtr(0, 15f, 25f)), 108L))
        assertEquals(1, move.size)
        assertEquals(TouchAction.MOVE, move[0].action)
        assertEquals(MotionTranslator.NO_POINTER, move[0].actionPointerId)
        assertEquals(25f, move[0].pointers[0].y, 0f)

        val up = t.translate(FakeMotion(MotionActions.ACTION_UP, 0, listOf(FakePtr(0, 15f, 25f)), 120L))
        assertEquals(1, up.size)
        assertEquals(TouchAction.UP, up[0].action)
        assertEquals(0, up[0].actionPointerId)
        assertTrue(t.trackedPointerIds.isEmpty())
    }

    @Test
    fun pointerDownAndUpAreSeparateEventsForTheRightPointer() {
        val t = MotionTranslator()
        t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(0, 1f, 1f)), 0L))
        // Drugi palec (id 3) ląduje na indeksie 1.
        val pd = t.translate(
            FakeMotion(MotionActions.ACTION_POINTER_DOWN, 1, listOf(FakePtr(0, 1f, 1f), FakePtr(3, 50f, 50f)), 5L),
        )
        assertEquals(1, pd.size)
        assertEquals(TouchAction.DOWN, pd[0].action)
        assertEquals(3, pd[0].actionPointerId)
        assertEquals(listOf(0, 3), ids(pd[0]))

        // Pierwszy palec (id 0, indeks 0) podniesiony, drugi zostaje.
        val pu = t.translate(
            FakeMotion(MotionActions.ACTION_POINTER_UP, 0, listOf(FakePtr(0, 2f, 2f), FakePtr(3, 50f, 50f)), 9L),
        )
        assertEquals(1, pu.size)
        assertEquals(TouchAction.UP, pu[0].action)
        assertEquals(0, pu[0].actionPointerId)
        // Podnoszony wskaźnik jest jeszcze w liście (jak w MotionEvent), z ostatnią pozycją.
        assertEquals(listOf(0, 3), ids(pu[0]))
        assertEquals(setOf(3), t.trackedPointerIds)

        // Kolejny MOVE zawiera już tylko id 3.
        val mv = t.translate(FakeMotion(MotionActions.ACTION_MOVE, 0, listOf(FakePtr(3, 60f, 50f)), 12L))
        assertEquals(listOf(3), ids(mv[0]))
    }

    @Test
    fun tenPointerLimitIgnoresEleventh() {
        val t = MotionTranslator()
        val ptrs: ArrayList<FakePtr> = ArrayList()
        ptrs.add(FakePtr(0, 0f, 0f))
        t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, ArrayList(ptrs), 0L))
        var id: Int = 1
        while (id <= 10) {
            ptrs.add(FakePtr(id, id.toFloat(), 0f))
            val out = t.translate(FakeMotion(MotionActions.ACTION_POINTER_DOWN, ptrs.size - 1, ArrayList(ptrs), id.toLong()))
            if (id < 10) {
                assertEquals("palec $id", 1, out.size)
            } else {
                assertTrue("11. palec musi być zignorowany", out.isEmpty())
            }
            id++
        }
        assertEquals(10, t.trackedPointerIds.size)
        val mv = t.translate(FakeMotion(MotionActions.ACTION_MOVE, 0, ArrayList(ptrs), 50L))
        assertEquals(10, mv[0].pointers.size)
        // UP 11. palca (id 10, ostatni indeks) nie generuje zdarzenia.
        val up = t.translate(FakeMotion(MotionActions.ACTION_POINTER_UP, 10, ArrayList(ptrs), 60L))
        assertTrue(up.isEmpty())
    }

    @Test
    fun cancelClearsStateAndEmitsSingleCancel() {
        val t = MotionTranslator()
        t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(0, 1f, 1f)), 0L))
        val c = t.translate(FakeMotion(MotionActions.ACTION_CANCEL, 0, listOf(FakePtr(0, 1f, 1f)), 3L))
        assertEquals(1, c.size)
        assertEquals(TouchAction.CANCEL, c[0].action)
        assertTrue(t.trackedPointerIds.isEmpty())
        // Kolejny CANCEL bez aktywnych wskaźników nic nie emituje.
        assertTrue(t.translate(FakeMotion(MotionActions.ACTION_CANCEL, 0, listOf(FakePtr(0, 1f, 1f)), 4L)).isEmpty())
    }

    @Test
    fun downWithStaleStateEmitsCancelFirst() {
        val t = MotionTranslator()
        t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(0, 1f, 1f)), 0L))
        // Zgubiony UP: przychodzi nowy ACTION_DOWN.
        val out = t.translate(FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(1, 5f, 5f)), 10L))
        assertEquals(2, out.size)
        assertEquals(TouchAction.CANCEL, out[0].action)
        assertEquals(TouchAction.DOWN, out[1].action)
        assertEquals(1, out[1].actionPointerId)
        assertEquals(setOf(1), t.trackedPointerIds)
    }

    @Test
    fun nonFingerPointersAreIgnored() {
        val t = MotionTranslator()
        val out = t.translate(
            FakeMotion(MotionActions.ACTION_DOWN, 0, listOf(FakePtr(0, 1f, 1f, MotionActions.TOOL_TYPE_MOUSE)), 0L),
        )
        assertTrue(out.isEmpty())
        assertTrue(t.translate(FakeMotion(MotionActions.ACTION_MOVE, 0, listOf(FakePtr(0, 2f, 2f, MotionActions.TOOL_TYPE_MOUSE)), 1L)).isEmpty())
    }

    @Test
    fun moveWithoutTrackedPointersIsDropped() {
        val t = MotionTranslator()
        assertTrue(t.translate(FakeMotion(MotionActions.ACTION_MOVE, 0, listOf(FakePtr(0, 1f, 1f)), 0L)).isEmpty())
    }

    @Test
    fun everyDownHasExactlyOneMatchingUp() {
        // Mała właściwość: losowa sekwencja DOWN/UP z ≤ 10 palcami -> liczba DOWN == liczba UP po zakończeniu.
        val rnd = java.util.Random(42L)
        var iter: Int = 0
        while (iter < 200) {
            val t = MotionTranslator()
            val active: ArrayList<Int> = ArrayList()
            var downs: Int = 0
            var ups: Int = 0
            var nextId: Int = 0
            var time: Long = 0L
            var step: Int = 0
            while (step < 40) {
                time += 3L
                val addFinger: Boolean = active.isEmpty() || (active.size < 12 && rnd.nextBoolean())
                if (addFinger) {
                    val id: Int = nextId++
                    active.add(id)
                    val ptrs = active.map { FakePtr(it, it.toFloat(), 0f) }
                    val action: Int = if (active.size == 1) MotionActions.ACTION_DOWN else MotionActions.ACTION_POINTER_DOWN
                    for (e in t.translate(FakeMotion(action, active.size - 1, ptrs, time))) {
                        if (e.action == TouchAction.DOWN) downs++
                    }
                } else {
                    val idx: Int = rnd.nextInt(active.size)
                    val ptrs = active.map { FakePtr(it, it.toFloat(), 0f) }
                    val action: Int = if (active.size == 1) MotionActions.ACTION_UP else MotionActions.ACTION_POINTER_UP
                    for (e in t.translate(FakeMotion(action, idx, ptrs, time))) {
                        if (e.action == TouchAction.UP) ups++
                    }
                    active.removeAt(idx)
                }
                step++
            }
            // Podnieś wszystkie pozostałe palce.
            while (active.isNotEmpty()) {
                time += 3L
                val ptrs = active.map { FakePtr(it, it.toFloat(), 0f) }
                val action: Int = if (active.size == 1) MotionActions.ACTION_UP else MotionActions.ACTION_POINTER_UP
                for (e in t.translate(FakeMotion(action, 0, ptrs, time))) {
                    if (e.action == TouchAction.UP) ups++
                }
                active.removeAt(0)
            }
            assertEquals("iteracja $iter", downs, ups)
            assertTrue(t.trackedPointerIds.isEmpty())
            iter++
        }
    }
}
