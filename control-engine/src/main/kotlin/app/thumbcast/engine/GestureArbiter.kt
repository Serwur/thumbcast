package app.thumbcast.engine

import app.thumbcast.model.GestureZone
import kotlin.math.hypot

// Rozpoznawacze gestów w wolnej strefie produkują wyłącznie Intent przez IntentEmitter (ActionPolicy).
// O tym, który gest dostaje palec, decyduje GestureArbiter (jeden palec = jeden gest).

/**
 * Przydział palców wolnej strefy do gestów. Stan palca:
 * PENDING (jeszcze nic nie wysłał) -> CAMERA | LONG_PRESS | PINCH | CONSUMED; tap i edge swipe kończą się od razu.
 */
class GestureArbiter(
    private val config: EngineConfig,
    viewport: Viewport,
    screen: PxRect,
    private val emitter: IntentEmitter
) {
    enum class State { PENDING, CAMERA, LONG_PRESS, PINCH, CONSUMED }

    private class G(val fromEdge: Boolean) {
        var state: State = State.PENDING
        var maxMove: Float = 0f
    }

    private val video: VideoMapper = VideoMapper(viewport)
    val tap: TapRecognizer = TapRecognizer(config.tap, video, emitter)
    val longPress: LongPressRecognizer = LongPressRecognizer(config.longPress, config.tap.maxMovePx, video, emitter)
    val camera: CameraDragRecognizer = CameraDragRecognizer(config.camera, config.tuning, emitter)
    val pinch: PinchRecognizer = PinchRecognizer(config.pinch, config.tuning, emitter)
    val edge: EdgeSwipeRecognizer = EdgeSwipeRecognizer(config.edgeSwipe, config.tuning, screen, emitter)

    private val states: LinkedHashMap<Int, G> = LinkedHashMap()
    private val midX: Float = screen.centerX
    private val cameraRightSide: Boolean = CameraZones.isRightSide(config.camera.zone, config.leftHanded)

    fun stateOf(pointerId: Int): State? = states[pointerId]?.state

    fun onDown(p: TrackedPointer, tracker: PointerTracker) {
        val g = G(edge.isCandidate(p.downY))
        states[p.id] = g
        // Drugi palec wolnej strefy -> szczypanie (jeśli pierwszy jeszcze nie dał wejścia jednorazowego).
        if (pinch.enabled && !pinch.isActive()) {
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
            val first = other
            if (count == 1 && first != null) {
                val fg = states[first.id]
                if (fg != null) {
                    if (fg.state == State.CAMERA) camera.end(first.id)
                    fg.state = State.PINCH
                    g.state = State.PINCH
                    pinch.begin(first, p)
                    return
                }
            }
        }
        if (pinch.isActive()) g.state = State.CONSUMED
    }

    /** Prawdziwe zdarzenie dotyku (MOVE) albo tick (isTick): pozycje już zaktualizowane w trackerze. */
    fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean) {
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
                // Przy ruchu dowolnego z dwóch palców; kotwica odległości zapobiega podwójnemu liczeniu.
                val pa = tracker.get(pinch.firstId) ?: return
                val pb = tracker.get(pinch.secondId) ?: return
                pinch.move(pa, pb, p.id)
            }
            State.LONG_PRESS, State.CONSUMED -> {
            }
        }
    }

    /**
     * Koniec palca. fire = false przy przerwaniu (podwójny DOWN tego samego id): zwalniamy, ale nie
     * rozpoznajemy tapu ani long-pressu.
     */
    fun onUp(p: TrackedPointer, t: Long, fire: Boolean) {
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
                val og = states[otherId]
                if (og != null) og.state = State.CONSUMED
            }
            State.CONSUMED -> {
            }
        }
    }

    fun reset() {
        states.clear()
        camera.reset()
        pinch.end()
    }
}

object CameraZones {
    /** Leworęczność zamienia strefy gestów lewa/prawa (plan 5.4). */
    fun isRightSide(zone: GestureZone, leftHanded: Boolean): Boolean {
        val right = zone == GestureZone.RIGHT_FREE
        return if (leftHanded) !right else right
    }
}
