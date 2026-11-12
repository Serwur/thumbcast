package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import kotlin.math.hypot

/**
 * Wolna strefa jako mysz bezpośrednia (`gestures.freeZone = direct`, 12.11.2026): kursor idzie tam, gdzie palec
 * dotknął (pozycja bezwzględna przez [VideoMapper], jak `mouse_left_at_point`), zamiast gładzika z kursorem względnym.
 *
 * | gest | wyjście |
 * |---|---|
 * | 1 palec, tap (ruch ≤ tap.maxMovePx, czas ≤ tap.maxMs) | `MouseAt` w punkcie DOWN + LMB down + up w cyklu UP palca |
 * | 2 palce, tap (oba bez ruchu, od pierwszego DOWN do ostatniego UP ≤ tap.maxMs + twoFingerTapExtraMs) | `MouseAt` w punkcie DOWN pierwszego palca + RMB down + up w cyklu UP ostatniego palca |
 * | 1 palec, ruch > tap.maxMovePx | `MouseAt` w punkcie DOWN, potem `Wheel` i `HWheel` z ruchu palca (obie osie naraz) |
 * | 2 palce, ruch któregoś > tap.maxMovePx | nic (para kończy się bez wyjścia) |
 * | 1 palec trzymany bez ruchu dłużej niż tap.maxMs | nic (long-press w tym trybie nie działa) |
 * | edge swipe z góry | app("menu") |
 *
 * Przewijanie jak na telefonie (treść idzie za palcem): palec w dół = kółko w górę (dodatnie), palec w lewo =
 * kółko w prawo (dodatnie); `touchpad.scrollPxPerNotch` px drogi palca na 120 jednostek, reszty ułamkowe
 * akumulowane osobno dla każdej osi, najwyżej `maxNotchesPerEvent` ząbków na zdarzenie i oś. Punkt DOWN poza
 * obrazem (letterbox): tap nic nie daje (jak TapRecognizer), a przeciągnięcie nie przewija.
 *
 * Tap dwoma palcami klika w punkcie pierwszego palca (nie w środku pary): pierwszy palec zwykle wskazuje cel,
 * a drugi tylko „dopowiada” prawy klik; środek pary leżałby obok celu. Przeciągnięcie dwoma palcami nic nie robi,
 * bo jeden palec już przewija, a drugi mechanizm przewijania tylko zwiększałby ryzyko przypadkowych wejść.
 *
 * 1 dotyk = 1 wejście: palec daje najwyżej jedną krawędź (LMB z tapu, RMB z tapu dwoma palcami przypisany
 * ostatniemu palcowi pary, menu z edge swipe). Przewijanie to wejście bez trwałego stanu (jak przewijanie
 * touchpada i pinch), powstaje wyłącznie w cyklu MOVE palca. Tap nie przewija (przewijanie zaczyna się dopiero
 * po przekroczeniu progu ruchu, a palec po ruchu nie może już dać tapu), przeciągnięcie nie klika, a tap dwoma
 * palcami nie daje LMB (palce pary nie są już pojedynczymi palcami). Tick niczego nie robi.
 */
class DirectMouseGestures(
    private val config: EngineConfig,
    viewport: Viewport,
    screen: PxRect,
    private val emitter: IntentEmitter
) : FreeZoneGestures {

    enum class State {
        /** Jeszcze nic nie wysłał: tap, edge swipe albo przewijanie. */
        PENDING,
        /** Przewija (kursor już w punkcie DOWN). */
        SCROLL,
        /** Jeden z dwóch palców: możliwy tap dwoma palcami. */
        TWO,
        /** Nic już nie zrobi do UP. */
        CONSUMED
    }

    private class Finger(val fromEdge: Boolean, val downTimeMs: Long) {
        var state: State = State.PENDING
        var maxMove: Float = 0f
    }

    val edge: EdgeSwipeRecognizer = EdgeSwipeRecognizer(config.edgeSwipe, config.tuning, screen, emitter)

    private val video: VideoMapper = VideoMapper(viewport)
    private val fingers: LinkedHashMap<Int, Finger> = LinkedHashMap()
    private val slop: Float = config.tap.maxMovePx

    // Przewijanie: jeden palec naraz.
    private val scroll: RelativeAccumulator = RelativeAccumulator()
    private var scrollPointer: Int = -1
    private var scrollLastX: Float = 0f
    private var scrollLastY: Float = 0f

    // Para palców (TWO).
    private var pairA: Int = -1
    private var pairB: Int = -1
    private var pairStartMs: Long = 0L
    private var pairTapValid: Boolean = false
    private var pairX: Float = 0f
    private var pairY: Float = 0f

    fun stateOf(pointerId: Int): State? = fingers[pointerId]?.state

    override fun onDown(p: TrackedPointer, tracker: PointerTracker) {
        val others = ArrayList<Int>()
        for ((id, f) in fingers) if (id != p.id && f.state != State.CONSUMED) others.add(id)
        val f = Finger(others.isEmpty() && edge.isCandidate(p.downY), p.downTimeMs)
        fingers[p.id] = f
        if (others.isEmpty()) return
        if (others.size == 1 && pairA == -1) {
            val otherId = others[0]
            val of = fingers[otherId]
            val op = tracker.get(otherId)
            if (of != null && op != null && of.state == State.PENDING) {
                // Drugi palec, gdy pierwszy jeszcze nic nie wysłał: możliwy tap dwoma palcami (PPM).
                pairTapValid = of.maxMove <= slop
                of.state = State.TWO
                f.state = State.TWO
                pairA = otherId
                pairB = p.id
                pairStartMs = of.downTimeMs
                pairX = op.downX
                pairY = op.downY
                return
            }
        }
        // Palec przy przewijaniu albo trzeci palec: nic nie robi i unieważnia tap dwoma palcami.
        f.state = State.CONSUMED
        pairTapValid = false
    }

    override fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean) {
        // Sam upływ czasu nic tu nie rozpoznaje (bez long-pressu).
        if (isTick) return
        val f = fingers[p.id] ?: return
        val dx = p.x - p.downX
        val dy = p.y - p.downY
        val dist = hypot(dx, dy)
        if (dist > f.maxMove) f.maxMove = dist
        when (f.state) {
            State.PENDING -> {
                if (f.maxMove <= slop) return
                if (f.fromEdge && edge.isDownward(dx, dy)) {
                    if (edge.reached(dy)) {
                        edge.fire(p.id)
                        f.state = State.CONSUMED
                    }
                    return
                }
                startScroll(p, f)
            }
            State.SCROLL -> scrollMove(p)
            State.TWO -> {
                if (f.maxMove <= slop) return
                // Ruch pary: ani tap dwoma palcami, ani przewijanie. Oba palce kończą bez wyjścia. Tylko palce w stanie
                // TWO: id podniesionego palca pary mógł już dostać nowy palec, którego nie ruszamy.
                for (id in intArrayOf(pairA, pairB)) {
                    val pf = fingers[id]
                    if (pf != null && pf.state == State.TWO) pf.state = State.CONSUMED
                }
                endPair()
            }
            State.CONSUMED -> {
            }
        }
    }

    override fun onUp(p: TrackedPointer, t: Long, fire: Boolean) {
        val f = fingers.remove(p.id) ?: return
        when (f.state) {
            State.PENDING -> if (fire && tapMatches(f, t)) click(p.id, BIND_TAP, MouseBtn.LEFT, p.downX, p.downY)
            State.SCROLL -> stopScroll()
            State.TWO -> {
                val otherId = if (p.id == pairA) pairB else pairA
                val other = fingers[otherId]
                if (other != null && other.state == State.TWO) {
                    // Pierwszy palec pary w górę: czekamy na drugi (tap dwoma palcami kończy się na ostatnim UP).
                    if (!fire || f.maxMove > slop) pairTapValid = false
                } else {
                    val maxMs = config.tap.maxMs + config.tuning.twoFingerTapExtraMs
                    if (fire && pairTapValid && config.tap.enabled && f.maxMove <= slop && t - pairStartMs <= maxMs) {
                        click(p.id, BIND_RIGHT_TAP, MouseBtn.RIGHT, pairX, pairY)
                    }
                    endPair()
                }
            }
            State.CONSUMED -> {
            }
        }
    }

    override fun reset() {
        fingers.clear()
        stopScroll()
        endPair()
    }

    // ------------------------------------------------------------------ pomocnicze

    private fun tapMatches(f: Finger, t: Long): Boolean =
        config.tap.enabled && f.maxMove <= slop && t - f.downTimeMs <= config.tap.maxMs

    /** Kursor w punkcie (px overlaya) i klik; punkt poza obrazem: nic. Wywoływane w cyklu UP palca. */
    private fun click(pointerId: Int, bindingId: String, button: MouseBtn, x: Float, y: Float) {
        val v = video.toVideo(x, y) ?: return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(button, true)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(button, false)))
    }

    /** Start przewijania: kursor w punkcie DOWN (przewija się to, co pod palcem), potem ruch od tego punktu. */
    private fun startScroll(p: TrackedPointer, f: Finger) {
        val v = video.toVideo(p.downX, p.downY)
        if (v == null || scrollPointer != -1) {
            f.state = State.CONSUMED
            return
        }
        f.state = State.SCROLL
        scrollPointer = p.id
        scrollLastX = p.downX
        scrollLastY = p.downY
        scroll.reset()
        emitter.emit(Intent(p.id, BIND_SCROLL, BindingKind.HOLD, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        scrollMove(p)
    }

    /** Kółko pionowe i poziome z przesunięcia palca od poprzedniego zdarzenia (treść idzie za palcem). */
    private fun scrollMove(p: TrackedPointer) {
        if (p.id != scrollPointer) return
        val dx = p.x - scrollLastX
        val dy = p.y - scrollLastY
        scrollLastX = p.x
        scrollLastY = p.y
        if (dx == 0f && dy == 0f) return
        val px = config.touchpad.scrollPxPerNotch
        if (!(px > 0f)) return
        // x akumulatora = kółko poziome (palec w lewo = dodatnie), y = pionowe (palec w dół = dodatnie).
        if (!scroll.add(-dx * WHEEL_NOTCH / px, dy * WHEEL_NOTCH / px)) return
        val cap = config.tuning.maxNotchesPerEvent * WHEEL_NOTCH.toInt()
        val v = scroll.stepY.coerceIn(-cap, cap)
        val h = scroll.stepX.coerceIn(-cap, cap)
        if (v != 0) emitter.emit(Intent(p.id, BIND_SCROLL, BindingKind.HOLD, Wheel(v)))
        if (h != 0) emitter.emit(Intent(p.id, BIND_SCROLL, BindingKind.HOLD, HWheel(h)))
    }

    private fun stopScroll() {
        scrollPointer = -1
        scroll.reset()
    }

    private fun endPair() {
        pairA = -1
        pairB = -1
        pairTapValid = false
    }

    companion object {
        const val BIND_TAP: String = "gesture:tap"
        const val BIND_RIGHT_TAP: String = "gesture:tap2"
        const val BIND_SCROLL: String = "gesture:scroll"

        private const val WHEEL_NOTCH: Float = 120f
    }
}
