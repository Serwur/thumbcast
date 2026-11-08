package app.thumbcast.android.overlay

import app.thumbcast.engine.TouchAction
import app.thumbcast.engine.TouchEvent
import app.thumbcast.engine.TouchPointer

/**
 * Tłumaczy zdarzenia Androida ([MotionSource]) na `TouchEvent` z kontraktu silnika.
 *
 * Reguły:
 * - `ACTION_DOWN` i `ACTION_POINTER_DOWN` -> `DOWN` dla wskaźnika z `actionIndex`;
 * - `ACTION_UP` i `ACTION_POINTER_UP` -> `UP` dla wskaźnika z `actionIndex`;
 *   lista `pointers` zawiera też podnoszony wskaźnik (z ostatnią pozycją), tak jak MotionEvent;
 * - `ACTION_MOVE` -> jeden `MOVE` z bieżącymi pozycjami (`actionPointerId = NO_POINTER`);
 *   próbki historyczne są pomijane (plan 5.2: koalescencja, 1 pakiet osi na ramkę wejścia);
 * - `ACTION_CANCEL` -> `CANCEL` (`actionPointerId = NO_POINTER`) i wyczyszczenie stanu;
 * - limit [maxPointers] (domyślnie 10): wskaźniki ponad limit są ignorowane w całości
 *   (nie dostają DOWN, nie pojawiają się w MOVE, ich UP jest pomijany);
 * - `ACTION_DOWN` przy niepustym stanie (zgubiony UP/CANCEL) najpierw emituje `CANCEL`;
 * - wskaźniki inne niż palec (`TOOL_TYPE_MOUSE/STYLUS/ERASER`) są ignorowane.
 *
 * Klasa jest stanowa (zbiór śledzonych wskaźników) i nie jest wątkowo bezpieczna; używać z wątku UI.
 */
class MotionTranslator(private val maxPointers: Int = MAX_POINTERS) {

    private val tracked: LinkedHashSet<Int> = LinkedHashSet()

    val trackedPointerIds: Set<Int>
        get() = tracked

    fun reset() {
        tracked.clear()
    }

    fun translate(src: MotionSource): List<TouchEvent> {
        val out: ArrayList<TouchEvent> = ArrayList(2)
        val time: Long = src.eventTimeMs
        when (src.actionMasked) {
            MotionActions.ACTION_DOWN -> {
                if (tracked.isNotEmpty()) {
                    out.add(TouchEvent(TouchAction.CANCEL, NO_POINTER, pointersOf(src), time))
                    tracked.clear()
                }
                addDown(src, out, time)
            }
            MotionActions.ACTION_POINTER_DOWN -> addDown(src, out, time)
            MotionActions.ACTION_MOVE -> {
                if (tracked.isNotEmpty()) {
                    out.add(TouchEvent(TouchAction.MOVE, NO_POINTER, pointersOf(src), time))
                }
            }
            MotionActions.ACTION_UP, MotionActions.ACTION_POINTER_UP -> {
                val id: Int = src.pointerId(src.actionIndex)
                if (tracked.contains(id)) {
                    val pointers: List<TouchPointer> = pointersOf(src)
                    tracked.remove(id)
                    out.add(TouchEvent(TouchAction.UP, id, pointers, time))
                }
                if (src.actionMasked == MotionActions.ACTION_UP) {
                    // Ostatni palec podniesiony: po tym nic nie może zostać śledzone.
                    tracked.clear()
                }
            }
            MotionActions.ACTION_CANCEL -> {
                if (tracked.isNotEmpty()) {
                    out.add(TouchEvent(TouchAction.CANCEL, NO_POINTER, pointersOf(src), time))
                }
                tracked.clear()
            }
            else -> {
                // ACTION_OUTSIDE, HOVER_* itp.: overlay ich nie obsługuje.
            }
        }
        return out
    }

    private fun addDown(src: MotionSource, out: MutableList<TouchEvent>, time: Long) {
        val id: Int = src.pointerId(src.actionIndex)
        if (tracked.contains(id)) {
            return
        }
        if (!MotionActions.isFingerLike(src.toolType(src.actionIndex))) {
            // Mysz/rysik/gumka: nie nasz wskaźnik (obsługuje go upstream, gałąź pointer w Game).
            return
        }
        if (tracked.size >= maxPointers) {
            return
        }
        tracked.add(id)
        out.add(TouchEvent(TouchAction.DOWN, id, pointersOf(src), time))
    }

    /** Wszystkie śledzone wskaźniki obecne w zdarzeniu, w kolejności indeksów MotionEvent. */
    private fun pointersOf(src: MotionSource): List<TouchPointer> {
        val list: ArrayList<TouchPointer> = ArrayList(src.pointerCount)
        var i: Int = 0
        while (i < src.pointerCount) {
            val id: Int = src.pointerId(i)
            if (tracked.contains(id)) {
                list.add(TouchPointer(id, src.x(i), src.y(i)))
            }
            i++
        }
        return list
    }

    companion object {
        const val MAX_POINTERS: Int = 10

        /** `actionPointerId` dla MOVE i CANCEL (nie dotyczą pojedynczego wskaźnika). */
        const val NO_POINTER: Int = -1
    }
}
