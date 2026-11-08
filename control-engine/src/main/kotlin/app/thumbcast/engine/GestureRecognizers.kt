package app.thumbcast.engine

import app.thumbcast.model.GestureZone
import app.thumbcast.model.MouseBtn
import app.thumbcast.model.PadStick
import kotlin.math.abs
import kotlin.math.hypot

// Rozpoznawacze gestów w wolnej strefie. Każdy produkuje wyłącznie Intent przez IntentEmitter (ActionPolicy).
// O tym, który gest dostaje palec, decyduje GestureArbiter (jeden palec = jeden gest).

/** Przeliczenie px overlaya -> układ wideo (mousePosition). */
class VideoMapper(private val viewport: Viewport) {
    /** [x, y] w układzie videoRefWidth x videoRefHeight albo null, gdy punkt jest poza obrazem (letterbox). */
    fun toVideo(x: Float, y: Float): IntArray? {
        val r = viewport.videoRect
        if (r.width <= 0 || r.height <= 0 || viewport.videoRefWidth <= 0 || viewport.videoRefHeight <= 0) return null
        if (!r.contains(x, y)) return null
        val vx = ((x - r.left.toFloat()) * viewport.videoRefWidth.toFloat() / r.width.toFloat()).toInt()
        val vy = ((y - r.top.toFloat()) * viewport.videoRefHeight.toFloat() / r.height.toFloat()).toInt()
        return intArrayOf(vx.coerceIn(0, viewport.videoRefWidth - 1), vy.coerceIn(0, viewport.videoRefHeight - 1))
    }

    val refWidth: Int get() = viewport.videoRefWidth
    val refHeight: Int get() = viewport.videoRefHeight
}

/** Tap: krótki dotyk bez ruchu -> mousePosition + LMB down/up w punkcie. */
class TapRecognizer(
    private val config: TapConfig,
    private val video: VideoMapper,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:tap"

    fun matches(p: TrackedPointer, upTimeMs: Long, maxMoveSeen: Float): Boolean {
        if (!config.enabled) return false
        if (maxMoveSeen > config.maxMovePx) return false
        return upTimeMs - p.downTimeMs <= config.maxMs
    }

    /** Wywoływane w cyklu UP palca. */
    fun fire(pointerId: Int, x: Float, y: Float) {
        val v = video.toVideo(x, y) ?: return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, true)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, false)))
    }
}

/**
 * Long-press: palec trzymany bez ruchu co najmniej minMs -> mousePosition + RMB w punkcie.
 * Rozpoznanie zachodzi WYŁĄCZNIE w cyklu zdarzenia (MOVE albo tick przekazany jako syntetyczny MOVE aktywnego palca)
 * lub przy UP. RMB jest trzymany do UP palca (stan = palec). Daje jedno wejście ZAMIAST tapu, nigdy oba.
 */
class LongPressRecognizer(
    private val config: LongPressConfig,
    private val tapMaxMovePx: Float,
    private val video: VideoMapper,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:longpress"

    fun ready(p: TrackedPointer, nowMs: Long, maxMoveSeen: Float): Boolean {
        if (!config.enabled) return false
        if (maxMoveSeen > tapMaxMovePx) return false
        return nowMs - p.downTimeMs >= config.minMs
    }

    /** RMB w dół (palec nadal dotyka). false, gdy punkt jest poza obrazem. */
    fun press(pointerId: Int, x: Float, y: Float): Boolean {
        val v = video.toVideo(x, y) ?: return false
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, true)))
        return true
    }

    fun release(pointerId: Int) {
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, false)))
    }
}

/**
 * Kamera.
 * - PAD_PLUS: prędkość palca (px/ms, EMA ~2 próbki) -> krzywa -> wychylenie prawej gałki; po zatrzymaniu palca
 *   wychylenie wygasa liniowo do zera w decayMs (tylko maleje, więc ActionPolicy dopuszcza to w cyklu TICK).
 * - KBM (mouseLook): RMB w dół na początku gestu, mouseMoveRelative (delta px x czułość, z akumulacją reszt
 *   ułamkowych), RMB w górę na końcu.
 * Jeden palec kamery naraz.
 */
class CameraDragRecognizer(
    private val config: CameraConfig,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:camera"

    private var pointer: Int = -1
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastT: Long = 0
    private var emaVx: Float = 0f
    private var emaVy: Float = 0f
    private var hasEma: Boolean = false
    private var outX: Float = 0f
    private var outY: Float = 0f
    private var stopX: Float = 0f
    private var stopY: Float = 0f
    private var stopT: Long = 0
    private var remX: Float = 0f
    private var remY: Float = 0f
    private var decayT: Long = 0

    val activePointerId: Int get() = pointer

    fun isActive(): Boolean = pointer != -1

    /** Start gestu w cyklu MOVE, w którym palec przekroczył próg ruchu; (fromX, fromY) = punkt DOWN. */
    fun begin(pointerId: Int, fromX: Float, fromY: Float, fromT: Long, x: Float, y: Float, t: Long) {
        if (pointer != -1) return
        pointer = pointerId
        lastX = fromX
        lastY = fromY
        lastT = fromT
        hasEma = false
        emaVx = 0f
        emaVy = 0f
        outX = 0f
        outY = 0f
        stopX = 0f
        stopY = 0f
        stopT = t
        decayT = t
        remX = 0f
        remY = 0f
        if (config.mouseLook) {
            emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseButtonSet(MouseBtn.RIGHT, true)))
        }
        move(pointerId, x, y, t)
    }

    /** Prawdziwe zdarzenie dotyku z pozycją palca (także MOVE innego palca: wtedy dx = dy = 0). */
    fun move(pointerId: Int, x: Float, y: Float, t: Long) {
        if (pointerId != pointer) return
        val dx = x - lastX
        val dy = y - lastY
        val dtRaw = t - lastT
        lastX = x
        lastY = y
        if (dtRaw > 0) lastT = t
        val sign = if (config.invertY) -1f else 1f
        if (config.mouseLook) {
            if (dx == 0f && dy == 0f) return
            val scale = config.sensitivity * tuning.kbmMouseScale
            val fx = dx * scale + remX
            val fy = dy * scale * sign + remY
            val ix = fx.toInt()
            val iy = fy.toInt()
            remX = fx - ix.toFloat()
            remY = fy - iy.toFloat()
            if (ix != 0 || iy != 0) {
                emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseMove(ix, iy)))
            }
            return
        }
        if (dx == 0f && dy == 0f) {
            decay(pointerId, t)
            return
        }
        val dt = if (dtRaw >= 1L) dtRaw.toFloat() else 1f
        val vx = dx / dt
        val vy = dy / dt
        val n = if (tuning.cameraEmaSamples < 1) 1 else tuning.cameraEmaSamples
        val alpha = 2f / (n.toFloat() + 1f)
        if (!hasEma) {
            emaVx = vx
            emaVy = vy
            hasEma = true
        } else {
            emaVx = alpha * vx + (1f - alpha) * emaVx
            emaVy = alpha * vy + (1f - alpha) * emaVy
        }
        val v = CameraMath.deflection(emaVx, emaVy, config, tuning)
        // Ekran: y w dół; gałka: y w górę. Przeciągnięcie w górę = gałka w górę (invertY odwraca).
        setOut(pointerId, v[0], -v[1] * sign)
        stopX = outX
        stopY = outY
        stopT = if (t > lastT) t else lastT
        decayT = stopT
    }

    /** Cykl TICK (czas bez ruchu) albo zdarzenie bez ruchu tego palca: tylko wygaszanie. */
    fun decay(pointerId: Int, t: Long) {
        if (pointerId != pointer || config.mouseLook) return
        if (outX == 0f && outY == 0f) return
        hasEma = false
        // Czas wygaszania tylko rośnie (zdarzenia mogą mieć starszy znacznik niż poprzedni tick).
        if (t > decayT) decayT = t
        val f: Float = if (config.decayMs <= 0L) 0f else (1f - (decayT - stopT).toFloat() / config.decayMs.toFloat()).coerceIn(0f, 1f)
        setOut(pointerId, stopX * f, stopY * f)
    }

    fun end(pointerId: Int) {
        if (pointerId != pointer) return
        if (config.mouseLook) {
            emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseButtonSet(MouseBtn.RIGHT, false)))
        } else {
            emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, StickSet(PadStick.RIGHT, 0f, 0f)))
        }
        pointer = -1
        outX = 0f
        outY = 0f
    }

    fun reset() {
        pointer = -1
        outX = 0f
        outY = 0f
        hasEma = false
    }

    private fun setOut(pointerId: Int, xIn: Float, yIn: Float) {
        val x = StickMath.clean(xIn)
        val y = StickMath.clean(yIn)
        if (x == outX && y == outY) return
        outX = x
        outY = y
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, StickSet(PadStick.RIGHT, x, y)))
    }
}

object CameraMath {
    /** Prędkość (px/ms) -> wychylenie [x, y] w układzie ekranu (y w dół), moduł 0..maxDeflection. */
    fun deflection(vx: Float, vy: Float, config: CameraConfig, tuning: EngineTuning): FloatArray {
        val speed = hypot(vx, vy)
        val frame = if (tuning.referenceFrameMs > 0f) tuning.referenceFrameMs else 16.67f
        val dz = config.deadzonePx / frame
        if (speed <= dz || speed <= 0f) return floatArrayOf(0f, 0f)
        val sens = if (config.sensitivity > 0f) config.sensitivity else 1f
        val full = tuning.cameraFullSpeedPxPerMs / sens
        val norm = if (full <= 0f) 1f else ((speed - dz) / full).coerceIn(0f, 1f)
        val mag = StickMath.applyCurve(norm, config.curve) * config.maxDeflection.coerceIn(0f, 1f)
        return floatArrayOf(vx / speed * mag, vy / speed * mag)
    }
}

/** Szczypanie: zmiana odległości dwóch palców -> mouseWheel (120 = 1 ząbek, pxPerNotch px na ząbek). */
class PinchRecognizer(
    private val config: PinchConfig,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:pinch"

    private var a: Int = -1
    private var b: Int = -1
    private var anchor: Float = 0f

    fun isActive(): Boolean = a != -1

    fun involves(pointerId: Int): Boolean = pointerId == a || pointerId == b

    val enabled: Boolean get() = config.enabled && config.pxPerNotch > 0f

    /** a = pierwszy palec, b = palec, którego DOWN rozpoczął szczypanie. */
    fun begin(first: TrackedPointer, second: TrackedPointer) {
        a = first.id
        b = second.id
        anchor = hypot(second.x - first.x, second.y - first.y)
    }

    /** causePointerId = palec, którego ruch (w bieżącym zdarzeniu) spowodował zmianę. */
    fun move(pa: TrackedPointer, pb: TrackedPointer, causePointerId: Int) {
        if (a == -1) return
        val d = hypot(pb.x - pa.x, pb.y - pa.y)
        val diff = d - anchor
        var notches = (diff / config.pxPerNotch).toInt()
        if (notches == 0) return
        anchor += notches.toFloat() * config.pxPerNotch
        val cap = tuning.maxNotchesPerEvent
        if (notches > cap) notches = cap
        if (notches < -cap) notches = -cap
        // Rozsuwanie palców = przybliżenie = kółko w górę (dodatnie).
        emitter.emit(Intent(causePointerId, bindingId, BindingKind.HOLD, Wheel(notches * 120)))
    }

    val firstId: Int get() = a
    val secondId: Int get() = b

    fun end() {
        a = -1
        b = -1
        anchor = 0f
    }
}

/** Przeciągnięcie od górnej krawędzi w dół -> app("menu"). Jedno wejście na palec. */
class EdgeSwipeRecognizer(
    private val config: EdgeSwipeConfig,
    private val tuning: EngineTuning,
    private val screen: PxRect,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:edge"

    fun isCandidate(y: Float): Boolean {
        if (!config.enabled) return false
        val band = maxOf(tuning.edgeBandMinPx, tuning.edgeBandFraction * screen.height)
        return y - screen.top <= band
    }

    /** Czy ruch (od punktu DOWN) jest jeszcze zgodny z przeciąganiem w dół. */
    fun isDownward(dx: Float, dy: Float): Boolean = dy > 0f && dy >= abs(dx)

    fun reached(dy: Float): Boolean {
        val min = maxOf(tuning.edgeSwipeMinPx, tuning.edgeSwipeMinFraction * screen.height)
        return dy >= min
    }

    fun fire(pointerId: Int) {
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, AppAction("menu")))
    }
}

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
