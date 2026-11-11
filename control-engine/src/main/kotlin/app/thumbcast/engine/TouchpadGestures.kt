package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Wolna strefa w trybie touchpada (`gestures.freeZone = touchpad`): cała część ekranu bez kontrolek działa jak
 * gładzik laptopa. Tabela gestów (control-model/docs/profile-schema.md):
 *
 * | gest | wyjście |
 * |---|---|
 * | 1 palec, ruch > tap.maxMovePx | MouseMove (sensitivity, reszty ułamkowe w [RelativeAccumulator]) |
 * | 1 palec, tap | LMB down + up w cyklu UP palca (w miejscu kursora, bez mousePosition) |
 * | 2 palce, tap | RMB down + up w cyklu UP drugiego palca |
 * | 1 palec, trzymany ≥ longPress.minMs bez ruchu | LMB down (ONE_SHOT_TIMED; może zajść na ticku), dalej ruch z wciśniętym LMB, LMB up przy UP |
 * | 2 palce, ruch środka > tap.maxMovePx | kółko: oś zablokowana na starcie gestu (większa składowa), Wheel albo HWheel |
 * | edge swipe z góry | app("menu") |
 * | pinch | wyłączony |
 *
 * 1 dotyk = 1 wejście: każdy palec daje najwyżej jedną krawędź (LMB z tapu albo z przytrzymania, RMB z tapu dwoma
 * palcami przypisany drugiemu palcowi, menu z edge swipe). Ruch kursora i kółko to wejścia bez trwałego stanu
 * (jak kamera RMB-look i pinch) i powstają wyłącznie w cyklu MOVE palca. Tick niczego nie przesuwa ani nie przewija;
 * może jedynie rozpoznać przytrzymanie palca, który nadal dotyka (R7, jak long-press w Pad+).
 */
class TouchpadGestures(
    private val config: EngineConfig,
    screen: PxRect,
    private val emitter: IntentEmitter
) : FreeZoneGestures {

    enum class State {
        /** Jeszcze nic nie wysłał: tap, przytrzymanie, edge swipe albo ruch kursora. */
        PENDING,
        /** Przesuwa kursor. */
        POINTER,
        /** Przeciąganie: LMB trzymany od przytrzymania do UP. */
        DRAG,
        /** Jeden z dwóch palców: tap dwoma palcami albo (po ruchu) przewijanie. */
        TWO,
        /** Jeden z dwóch palców przewijania. */
        SCROLL,
        /** Nic już nie zrobi do UP. */
        CONSUMED
    }

    private class Finger(val fromEdge: Boolean, val downTimeMs: Long) {
        var state: State = State.PENDING
        var maxMove: Float = 0f
    }

    val edge: EdgeSwipeRecognizer = EdgeSwipeRecognizer(config.edgeSwipe, config.tuning, screen, emitter)

    private val fingers: LinkedHashMap<Int, Finger> = LinkedHashMap()
    private val slop: Float = config.tap.maxMovePx

    // Kursor (POINTER albo DRAG): jeden palec naraz.
    private val cursor: RelativeAccumulator = RelativeAccumulator()
    private var cursorPointer: Int = -1
    private var cursorLastX: Float = 0f
    private var cursorLastY: Float = 0f

    // Para palców (TWO / SCROLL).
    private var pairA: Int = -1
    private var pairB: Int = -1
    private var pairStartMs: Long = 0L
    private var pairTapValid: Boolean = false
    private var anchorX: Float = 0f
    private var anchorY: Float = 0f
    private var axis: Int = AXIS_NONE
    private var scrollLast: Float = 0f
    private val scroll: RelativeAccumulator = RelativeAccumulator()

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
            if (of != null && op != null && (of.state == State.PENDING || of.state == State.POINTER)) {
                // Drugi palec: tap dwoma palcami albo przewijanie. Ruch kursora pierwszego palca się kończy
                // (nic nie trzyma, więc nie ma czego zwalniać).
                pairTapValid = of.state == State.PENDING && of.maxMove <= slop
                if (of.state == State.POINTER) stopCursor()
                of.state = State.TWO
                f.state = State.TWO
                pairA = otherId
                pairB = p.id
                pairStartMs = of.downTimeMs
                anchorX = (op.x + p.x) / 2f
                anchorY = (op.y + p.y) / 2f
                axis = AXIS_NONE
                scroll.reset()
                return
            }
        }
        // Trzeci palec albo drugi przy przeciąganiu: nic nie robi i unieważnia tap dwoma palcami.
        f.state = State.CONSUMED
        pairTapValid = false
    }

    override fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean) {
        val f = fingers[p.id] ?: return
        val dx = p.x - p.downX
        val dy = p.y - p.downY
        if (!isTick) {
            val dist = hypot(dx, dy)
            if (dist > f.maxMove) f.maxMove = dist
        }
        when (f.state) {
            State.PENDING -> {
                if (holdReady(f, t)) {
                    startDrag(p, f)
                    return
                }
                if (isTick || f.maxMove <= slop) return
                if (f.fromEdge && edge.isDownward(dx, dy)) {
                    if (edge.reached(dy)) {
                        edge.fire(p.id)
                        f.state = State.CONSUMED
                    }
                    return
                }
                f.state = State.POINTER
                cursorPointer = p.id
                cursorLastX = p.downX
                cursorLastY = p.downY
                cursor.reset()
                moveCursor(p, BIND_POINTER)
            }
            State.POINTER -> if (!isTick) moveCursor(p, BIND_POINTER)
            State.DRAG -> if (!isTick) moveCursor(p, BIND_DRAG)
            State.TWO -> if (!isTick) pairMove(p, f, tracker)
            State.SCROLL -> if (!isTick) scrollMove(p, tracker)
            State.CONSUMED -> {
            }
        }
    }

    override fun onUp(p: TrackedPointer, t: Long, fire: Boolean) {
        val f = fingers.remove(p.id) ?: return
        when (f.state) {
            State.PENDING -> {
                if (!fire) {
                    // przerwanie: nic
                } else if (tapMatches(f, t)) {
                    click(p.id, BIND_TAP, BindingKind.ONE_SHOT, MouseBtn.LEFT)
                } else if (holdReady(f, t)) {
                    // Próg czasu minął bez ticka: klik przy UP (dalej jedno wejście zamiast tapu).
                    click(p.id, BIND_DRAG, BindingKind.ONE_SHOT_TIMED, MouseBtn.LEFT)
                }
            }
            State.POINTER -> stopCursor()
            State.DRAG -> {
                emitter.emit(Intent(p.id, BIND_DRAG, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.LEFT, false)))
                stopCursor()
            }
            State.TWO -> {
                val otherId = if (p.id == pairA) pairB else pairA
                val other = fingers[otherId]
                if (other != null && other.state == State.TWO) {
                    // Pierwszy palec pary w górę: czekamy na drugi (tap dwoma palcami kończy się na ostatnim UP).
                    if (f.maxMove > slop) pairTapValid = false
                } else {
                    val maxMs = config.tap.maxMs + config.tuning.twoFingerTapExtraMs
                    if (fire && pairTapValid && config.tap.enabled && f.maxMove <= slop && t - pairStartMs <= maxMs) {
                        click(p.id, BIND_RIGHT_TAP, BindingKind.ONE_SHOT, MouseBtn.RIGHT)
                    }
                    endPair()
                }
            }
            State.SCROLL -> {
                val otherId = if (p.id == pairA) pairB else pairA
                val other = fingers[otherId]
                if (other != null) other.state = State.CONSUMED
                endPair()
            }
            State.CONSUMED -> {
            }
        }
    }

    override fun reset() {
        fingers.clear()
        stopCursor()
        endPair()
    }

    // ------------------------------------------------------------------ pomocnicze

    private fun tapMatches(f: Finger, t: Long): Boolean =
        config.tap.enabled && f.maxMove <= slop && t - f.downTimeMs <= config.tap.maxMs

    private fun holdReady(f: Finger, t: Long): Boolean =
        config.longPress.enabled && f.maxMove <= slop && t - f.downTimeMs >= config.longPress.minMs

    /** Przytrzymanie rozpoznane, palec nadal dotyka: LMB w dół, dalej ruch z trzymanym przyciskiem. */
    private fun startDrag(p: TrackedPointer, f: Finger) {
        f.state = State.DRAG
        emitter.emit(Intent(p.id, BIND_DRAG, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.LEFT, true)))
        cursorPointer = p.id
        cursorLastX = p.x
        cursorLastY = p.y
        cursor.reset()
    }

    private fun click(pointerId: Int, bindingId: String, kind: BindingKind, button: MouseBtn) {
        emitter.emit(Intent(pointerId, bindingId, kind, MouseButtonSet(button, true)))
        emitter.emit(Intent(pointerId, bindingId, kind, MouseButtonSet(button, false)))
    }

    private fun moveCursor(p: TrackedPointer, bindingId: String) {
        if (p.id != cursorPointer) return
        val dx = p.x - cursorLastX
        val dy = p.y - cursorLastY
        cursorLastX = p.x
        cursorLastY = p.y
        if (dx == 0f && dy == 0f) return
        val s = config.touchpad.sensitivity
        if (cursor.add(dx * s, dy * s)) {
            emitter.emit(Intent(p.id, bindingId, BindingKind.HOLD, MouseMove(cursor.stepX, cursor.stepY)))
        }
    }

    private fun stopCursor() {
        cursorPointer = -1
        cursor.reset()
    }

    /** Para przed decyzją: tap dwoma palcami albo start przewijania z blokadą osi. */
    private fun pairMove(p: TrackedPointer, f: Finger, tracker: PointerTracker) {
        if (f.maxMove > slop) pairTapValid = false
        val a = tracker.get(pairA)
        val b = tracker.get(pairB)
        if (a == null || b == null) {
            // Drugi palec pary już podniesiony: jednym palcem nie przewijamy, a po ruchu nie będzie też tapu.
            if (f.maxMove > slop) {
                f.state = State.CONSUMED
                endPair()
            }
            return
        }
        val cx = (a.x + b.x) / 2f
        val cy = (a.y + b.y) / 2f
        val ddx = cx - anchorX
        val ddy = cy - anchorY
        if (hypot(ddx, ddy) <= slop) return
        // Oś wybierana raz, na starcie gestu.
        axis = if (abs(ddx) > abs(ddy)) AXIS_H else AXIS_V
        pairTapValid = false
        fingers[pairA]?.state = State.SCROLL
        fingers[pairB]?.state = State.SCROLL
        scrollLast = if (axis == AXIS_V) anchorY else anchorX
        scroll.reset()
        scrollMove(p, tracker)
    }

    /**
     * Przewijanie po zablokowanej osi. Kierunek jak na telefonie: palce w dół = kółko w górę (dodatnie),
     * palce w lewo = kółko w prawo (dodatnie). Wyjście przypisane palcowi z bieżącego zdarzenia.
     */
    private fun scrollMove(p: TrackedPointer, tracker: PointerTracker) {
        val a = tracker.get(pairA) ?: return
        val b = tracker.get(pairB) ?: return
        val c = if (axis == AXIS_V) (a.y + b.y) / 2f else (a.x + b.x) / 2f
        val d = c - scrollLast
        scrollLast = c
        if (d == 0f) return
        val px = config.touchpad.scrollPxPerNotch
        if (!(px > 0f)) return
        val units = d * WHEEL_NOTCH / px
        val value = if (axis == AXIS_V) units else -units
        if (!scroll.add(value, 0f)) return
        val cap = config.tuning.maxNotchesPerEvent * WHEEL_NOTCH.toInt()
        val delta = scroll.stepX.coerceIn(-cap, cap)
        val effect: OutputEffect = if (axis == AXIS_V) Wheel(delta) else HWheel(delta)
        emitter.emit(Intent(p.id, BIND_SCROLL, BindingKind.HOLD, effect))
    }

    private fun endPair() {
        pairA = -1
        pairB = -1
        pairTapValid = false
        axis = AXIS_NONE
        scroll.reset()
    }

    companion object {
        const val BIND_POINTER: String = "gesture:pointer"
        const val BIND_TAP: String = "gesture:tap"
        const val BIND_RIGHT_TAP: String = "gesture:tap2"
        const val BIND_DRAG: String = "gesture:drag"
        const val BIND_SCROLL: String = "gesture:scroll"

        private const val AXIS_NONE: Int = 0
        private const val AXIS_V: Int = 1
        private const val AXIS_H: Int = 2
        private const val WHEEL_NOTCH: Float = 120f
    }
}
