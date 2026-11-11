package app.thumbcast.engine

import kotlin.math.hypot

/**
 * Wolna strefa wyłączona (`gestures.freeZone = none`, 12.11.2026): dotyk poza kontrolkami nic nie wysyła do hosta.
 * Bez kamery, tapu, long-pressu, pinchu i ruchu myszy, niezależnie od ustawień `cameraDrag`, `tapTarget`,
 * `longPress`, `pinchZoom` i `touchpad`. Jedyny gest to edge swipe z góry (`app("menu")`, akcja lokalna), gdy
 * `edgeSwipeTop.enabled`; rozpoznawany tak samo jak w [GestureArbiter] i [TouchpadGestures].
 *
 * 1 dotyk = 1 wejście: palec daje najwyżej jedną krawędź (menu), wyłącznie w cyklu MOVE. Tick niczego nie robi.
 */
class EdgeSwipeOnlyGestures(
    private val config: EngineConfig,
    screen: PxRect,
    emitter: IntentEmitter
) : FreeZoneGestures {

    val edge: EdgeSwipeRecognizer = EdgeSwipeRecognizer(config.edgeSwipe, config.tuning, screen, emitter)

    /** Palce, które zaczęły w pasku krawędzi i jeszcze mogą otworzyć menu (id -> największe przesunięcie). */
    private val candidates: HashMap<Int, Float> = HashMap()

    /** Czy palec może jeszcze dać edge swipe (testy). */
    fun isEdgeCandidate(pointerId: Int): Boolean = candidates.containsKey(pointerId)

    override fun onDown(p: TrackedPointer, tracker: PointerTracker) {
        if (edge.isCandidate(p.downY)) candidates[p.id] = 0f
    }

    override fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean) {
        if (isTick) return
        val maxMove = candidates[p.id] ?: return
        val dx = p.x - p.downX
        val dy = p.y - p.downY
        val moved = maxOf(maxMove, hypot(dx, dy))
        candidates[p.id] = moved
        if (moved <= config.tap.maxMovePx) return
        if (!edge.isDownward(dx, dy)) {
            // Ruch w bok albo w górę: to już nie jest gest menu (jak w pozostałych trybach wolnej strefy).
            candidates.remove(p.id)
            return
        }
        if (edge.reached(dy)) {
            edge.fire(p.id)
            candidates.remove(p.id)
        }
    }

    override fun onUp(p: TrackedPointer, t: Long, fire: Boolean) {
        candidates.remove(p.id)
    }

    override fun reset() {
        candidates.clear()
    }
}
