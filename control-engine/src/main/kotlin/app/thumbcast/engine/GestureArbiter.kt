package app.thumbcast.engine

import app.thumbcast.model.GestureZone
import kotlin.math.hypot

// Wolna strefa w trybie freeZone = camera (tabela Pad+). Rozpoznawacze produkują wyłącznie Intent przez
// IntentEmitter (ActionPolicy). O tym, który gest dostaje palec, decyduje GestureArbiter (jeden palec = jeden gest).

/**
 * Przydział palców wolnej strefy do gestów. Stan palca:
 * PENDING (jeszcze nic nie wysłał) -> CAMERA | LONG_PRESS | PINCH | TWO_TAP | CONSUMED; tap i edge swipe kończą się
 * od razu.
 *
 * Tap dwoma palcami (12.11.2026, gdy `tap.enabled`): drugi palec dotyka, gdy pierwszy jeszcze nic nie wysłał
 * (PENDING, bez ruchu); oba bez ruchu i bez kółka z pinchu, od pierwszego DOWN do ostatniego UP najwyżej
 * tap.maxMs + twoFingerTapExtraMs -> RMB w punkcie DOWN pierwszego palca, w cyklu UP ostatniego palca pary.
 * Przy włączonym pinchu para i tak jest pinchem (PINCH), a tap dwoma palcami jest śledzony obok; bez pinchu para
 * ma stan TWO_TAP (ruch pary nic nie daje). Żaden z palców pary nie da tapu LMB ani long-pressu.
 */
class GestureArbiter(
    private val config: EngineConfig,
    viewport: Viewport,
    screen: PxRect,
    private val emitter: IntentEmitter
) : FreeZoneGestures {
    enum class State { PENDING, CAMERA, LONG_PRESS, PINCH, TWO_TAP, CONSUMED }

    private class G(val fromEdge: Boolean) {
        var state: State = State.PENDING
        var maxMove: Float = 0f
    }

    private val video: VideoMapper = VideoMapper(viewport)
    val tap: TapRecognizer = TapRecognizer(config.tap, video, emitter)
    val longPress: LongPressRecognizer = LongPressRecognizer(config.longPress, config.tap.maxMovePx, video, emitter)
    val camera: CameraDragRecognizer = CameraDragRecognizer.create(config.camera, config.tuning, emitter)
    val pinch: PinchRecognizer = PinchRecognizer(config.pinch, config.tuning, emitter)
    val edge: EdgeSwipeRecognizer = EdgeSwipeRecognizer(config.edgeSwipe, config.tuning, screen, emitter)

    private val states: LinkedHashMap<Int, G> = LinkedHashMap()
    private val midX: Float = screen.centerX
    private val cameraRightSide: Boolean = CameraZones.isRightSide(config.camera.zone, config.leftHanded)
    private val slop: Float = config.tap.maxMovePx

    // Tap dwoma palcami: para (pierwszy, drugi palec), start = DOWN pierwszego, punkt kliknięcia = DOWN pierwszego.
    private var pairA: Int = -1
    private var pairB: Int = -1
    private var pairStartMs: Long = 0L
    private var pairTapValid: Boolean = false
    private var pairX: Float = 0f
    private var pairY: Float = 0f

    fun stateOf(pointerId: Int): State? = states[pointerId]?.state

    override fun onDown(p: TrackedPointer, tracker: PointerTracker) {
        val g = G(edge.isCandidate(p.downY))
        states[p.id] = g
        // Każdy kolejny palec przy trwającej parze unieważnia tap dwoma palcami.
        if (pairA != -1) pairTapValid = false
        var other: TrackedPointer? = null
        var count = 0
        for ((id, og) in states) {
            if (id == p.id) continue
            if (og.state == State.PENDING || og.state == State.CAMERA) {
                val tp = tracker.get(id)
                if (tp != null) {
                    other = tp
                    count += 1
                }
            }
        }
        val first = if (count == 1) other else null
        val fg = if (first != null) states[first.id] else null
        if (first != null && fg != null) {
            // Drugi palec wolnej strefy, gdy pierwszy jeszcze nic nie wysłał: możliwy tap dwoma palcami (PPM).
            val tapPair = config.tap.enabled && pairA == -1 && fg.state == State.PENDING && fg.maxMove <= slop
            if (pinch.enabled && !pinch.isActive()) {
                // Drugi palec wolnej strefy -> szczypanie (jeśli pierwszy jeszcze nie dał wejścia jednorazowego).
                if (fg.state == State.CAMERA) camera.end(first.id)
                fg.state = State.PINCH
                g.state = State.PINCH
                pinch.begin(first, p)
                if (tapPair) beginPair(first, p)
                return
            }
            if (tapPair) {
                fg.state = State.TWO_TAP
                g.state = State.TWO_TAP
                beginPair(first, p)
                return
            }
        }
        if (pinch.isActive()) g.state = State.CONSUMED
    }

    /** Prawdziwe zdarzenie dotyku (MOVE) albo tick (isTick): pozycje już zaktualizowane w trackerze. */
    override fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean) {
        val g = states[p.id] ?: return
        val dx = p.x - p.downX
        val dy = p.y - p.downY
        val dist = hypot(dx, dy)
        if (!isTick && dist > g.maxMove) g.maxMove = dist
        when (g.state) {
            State.PENDING -> {
                if (longPress.ready(p, t, g.maxMove)) {
                    g.state = if (longPress.press(p.id, p.downX, p.downY)) State.LONG_PRESS else State.CONSUMED
                    return
                }
                if (isTick || g.maxMove <= config.tap.maxMovePx) return
                if (g.fromEdge && edge.isDownward(dx, dy)) {
                    if (edge.reached(dy)) {
                        edge.fire(p.id)
                        g.state = State.CONSUMED
                    }
                    return
                }
                val inZone = if (cameraRightSide) p.downX >= midX else p.downX < midX
                if (inZone && !camera.isActive()) {
                    g.state = State.CAMERA
                    camera.begin(p.id, p.downX, p.downY, p.downTimeMs, p.x, p.y, t)
                } else {
                    g.state = State.CONSUMED
                }
            }
            State.CAMERA -> {
                if (isTick) camera.decay(p.id, t) else camera.move(p.id, p.x, p.y, t)
            }
            State.PINCH -> {
                if (isTick) return
                if (g.maxMove > slop) pairTapValid = false
                // Przy ruchu dowolnego z dwóch palców; kotwica odległości zapobiega podwójnemu liczeniu.
                val pa = tracker.get(pinch.firstId) ?: return
                val pb = tracker.get(pinch.secondId) ?: return
                if (pinch.move(pa, pb, p.id)) pairTapValid = false
            }
            State.TWO_TAP -> {
                if (isTick || g.maxMove <= slop) return
                // Ruch pary bez pinchu: nic (ani PPM, ani kamera). Oba palce kończą bez wyjścia. Tylko palce w stanie
                // TWO_TAP: id podniesionego palca pary mógł już dostać nowy palec (np. kamera), którego nie ruszamy.
                for (id in intArrayOf(pairA, pairB)) {
                    val pg = states[id]
                    if (pg != null && pg.state == State.TWO_TAP) pg.state = State.CONSUMED
                }
                endPair()
            }
            State.LONG_PRESS, State.CONSUMED -> {
            }
        }
    }

    /**
     * Koniec palca. fire = false przy przerwaniu (podwójny DOWN tego samego id): zwalniamy, ale nie
     * rozpoznajemy tapu ani long-pressu.
     */
    override fun onUp(p: TrackedPointer, t: Long, fire: Boolean) {
        val g = states.remove(p.id) ?: return
        when (g.state) {
            State.PENDING -> {
                if (!fire) {
                    // nic
                } else if (tap.matches(p, t, g.maxMove)) {
                    tap.fire(p.id, p.downX, p.downY)
                } else if (longPress.ready(p, t, g.maxMove)) {
                    // Próg czasu minął bez ticka: rozpoznanie przy UP (dalej jedno wejście zamiast tapu).
                    if (longPress.press(p.id, p.downX, p.downY)) longPress.release(p.id)
                }
            }
            State.CAMERA -> camera.end(p.id)
            State.LONG_PRESS -> longPress.release(p.id)
            State.PINCH -> {
                val otherId = if (p.id == pinch.firstId) pinch.secondId else pinch.firstId
                pinch.end()
                if (!fire || g.maxMove > slop) pairTapValid = false
                val og = states[otherId]
                // Drugi palec pary czeka na swój UP jako TWO_TAP (możliwy PPM), inaczej nic już nie zrobi.
                val waits = og != null && pairTapValid && isPair(p.id, otherId)
                if (og != null) og.state = if (waits) State.TWO_TAP else State.CONSUMED
                if (!waits) endPair()
            }
            State.TWO_TAP -> {
                val otherId = if (p.id == pairA) pairB else pairA
                val og = states[otherId]
                if (og != null && og.state == State.TWO_TAP) {
                    // Pierwszy palec pary w górę: czekamy na drugi.
                    if (!fire || g.maxMove > slop) pairTapValid = false
                } else {
                    val maxMs = config.tap.maxMs + config.tuning.twoFingerTapExtraMs
                    if (fire && pairTapValid && config.tap.enabled && g.maxMove <= slop && t - pairStartMs <= maxMs) {
                        tap.fireRight(p.id, pairX, pairY)
                    }
                    endPair()
                }
            }
            State.CONSUMED -> {
            }
        }
    }

    override fun reset() {
        states.clear()
        camera.reset()
        pinch.end()
        endPair()
    }

    private fun beginPair(first: TrackedPointer, second: TrackedPointer) {
        pairA = first.id
        pairB = second.id
        pairStartMs = first.downTimeMs
        pairTapValid = true
        pairX = first.downX
        pairY = first.downY
    }

    private fun isPair(a: Int, b: Int): Boolean =
        pairA != -1 && ((a == pairA && b == pairB) || (a == pairB && b == pairA))

    private fun endPair() {
        pairA = -1
        pairB = -1
        pairTapValid = false
    }
}

object CameraZones {
    /** Leworęczność zamienia strefy gestów lewa/prawa (plan 5.4). */
    fun isRightSide(zone: GestureZone, leftHanded: Boolean): Boolean {
        val right = zone == GestureZone.RIGHT_FREE
        return if (leftHanded) !right else right
    }
}
