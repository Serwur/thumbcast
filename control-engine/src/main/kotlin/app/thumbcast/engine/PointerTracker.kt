package app.thumbcast.engine

/** Właściciel wskaźnika ustalony przy DOWN („capture on down”). */
sealed interface PointerOwner

data class ControlOwner(val controlId: String) : PointerOwner

/** Wolna strefa: gesty (kamera, tap, long-press, pinch, edge swipe). */
object GestureOwner : PointerOwner

class TrackedPointer(
    val id: Int,
    var owner: PointerOwner,
    val downX: Float,
    val downY: Float,
    val downTimeMs: Long
) {
    var x: Float = downX
    var y: Float = downY
    var lastTimeMs: Long = downTimeMs
}

/**
 * Mapa pointerId -> właściciel. Wskaźnik należy do kontrolki lub gestu, w której wylądował, aż do UP/CANCEL.
 * Jedyna zmiana właściciela w trakcie życia palca to slide-to-press (transfer), wykonywany przez InputEngine
 * w kolejności: zwolnij stary -> transfer -> wciśnij nowy.
 */
class PointerTracker(private val maxPointers: Int) {

    private val pointers: LinkedHashMap<Int, TrackedPointer> = LinkedHashMap()

    val size: Int get() = pointers.size

    fun isTracked(id: Int): Boolean = pointers.containsKey(id)

    fun get(id: Int): TrackedPointer? = pointers[id]

    /** null, gdy przekroczono limit wskaźników (palec jest wtedy ignorowany do UP). */
    fun down(id: Int, x: Float, y: Float, timeMs: Long, owner: PointerOwner): TrackedPointer? {
        if (pointers.containsKey(id)) return null
        if (pointers.size >= maxPointers) return null
        val p = TrackedPointer(id, owner, x, y, timeMs)
        pointers[id] = p
        return p
    }

    fun update(id: Int, x: Float, y: Float, timeMs: Long): TrackedPointer? {
        val p = pointers[id] ?: return null
        p.x = x
        p.y = y
        p.lastTimeMs = timeMs
        return p
    }

    fun transfer(id: Int, owner: PointerOwner) {
        val p = pointers[id] ?: return
        p.owner = owner
    }

    fun remove(id: Int): TrackedPointer? = pointers.remove(id)

    fun clear() {
        pointers.clear()
    }

    fun ids(): List<Int> = ArrayList<Int>(pointers.keys)

    fun all(): List<TrackedPointer> = ArrayList<TrackedPointer>(pointers.values)
}
