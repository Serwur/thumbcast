package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.PadStick
import kotlin.math.hypot

/**
 * Kamera z przeciągnięcia palca w wolnej strefie (tabela gestów Pad+). Jeden palec kamery naraz.
 * Dwie implementacje, wybierane z [CameraConfig.mouseLook]:
 * - [StickCameraDrag] (PAD_PLUS, `pad_right_stick`): prędkość palca -> wychylenie prawej gałki z wygaszaniem,
 * - [MouseLookCameraDrag] (KBM albo `mouse_look`): RMB trzymany przez gest + ruch względny myszy.
 */
interface CameraDragRecognizer {
    val bindingId: String
    val activePointerId: Int

    fun isActive(): Boolean

    /** Start gestu w cyklu MOVE, w którym palec przekroczył próg ruchu; (fromX, fromY) = punkt DOWN. */
    fun begin(pointerId: Int, fromX: Float, fromY: Float, fromT: Long, x: Float, y: Float, t: Long)

    /** Prawdziwe zdarzenie dotyku z pozycją palca (także MOVE innego palca: wtedy dx = dy = 0). */
    fun move(pointerId: Int, x: Float, y: Float, t: Long)

    /** Cykl TICK (czas bez ruchu) albo zdarzenie bez ruchu tego palca: tylko wygaszanie. */
    fun decay(pointerId: Int, t: Long)

    fun end(pointerId: Int)

    fun reset()

    companion object {
        const val BINDING_ID: String = "gesture:camera"

        fun create(config: CameraConfig, tuning: EngineTuning, emitter: IntentEmitter): CameraDragRecognizer =
            if (config.mouseLook) MouseLookCameraDrag(config, tuning, emitter) else StickCameraDrag(config, tuning, emitter)
    }
}

/**
 * Kamera na prawej gałce: prędkość palca (px/ms, EMA ~2 próbki) -> krzywa -> wychylenie prawej gałki. Po zatrzymaniu
 * palca wychylenie wygasa liniowo do zera w decayMs (tylko maleje, więc ActionPolicy dopuszcza to w cyklu TICK).
 */
class StickCameraDrag(
    private val config: CameraConfig,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) : CameraDragRecognizer {
    override val bindingId: String = CameraDragRecognizer.BINDING_ID

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
    private var decayT: Long = 0

    override val activePointerId: Int get() = pointer

    override fun isActive(): Boolean = pointer != -1

    override fun begin(pointerId: Int, fromX: Float, fromY: Float, fromT: Long, x: Float, y: Float, t: Long) {
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
        move(pointerId, x, y, t)
    }

    override fun move(pointerId: Int, x: Float, y: Float, t: Long) {
        if (pointerId != pointer) return
        val dx = x - lastX
        val dy = y - lastY
        val dtRaw = t - lastT
        lastX = x
        lastY = y
        if (dtRaw > 0) lastT = t
        val sign = if (config.invertY) -1f else 1f
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

    override fun decay(pointerId: Int, t: Long) {
        if (pointerId != pointer) return
        if (outX == 0f && outY == 0f) return
        hasEma = false
        // Czas wygaszania tylko rośnie (zdarzenia mogą mieć starszy znacznik niż poprzedni tick).
        if (t > decayT) decayT = t
        val f: Float = if (config.decayMs <= 0L) 0f else (1f - (decayT - stopT).toFloat() / config.decayMs.toFloat()).coerceIn(0f, 1f)
        setOut(pointerId, stopX * f, stopY * f)
    }

    override fun end(pointerId: Int) {
        if (pointerId != pointer) return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, StickSet(PadStick.RIGHT, 0f, 0f)))
        pointer = -1
        outX = 0f
        outY = 0f
    }

    override fun reset() {
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

/**
 * RMB-look (KBM albo `mouse_look`): RMB w dół na początku gestu, mouseMoveRelative (delta px x czułość,
 * z akumulacją reszt ułamkowych), RMB w górę na końcu. Bez wygaszania (ruch myszy nie ma stanu).
 */
class MouseLookCameraDrag(
    private val config: CameraConfig,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) : CameraDragRecognizer {
    override val bindingId: String = CameraDragRecognizer.BINDING_ID

    private var pointer: Int = -1
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private val acc: RelativeAccumulator = RelativeAccumulator()

    override val activePointerId: Int get() = pointer

    override fun isActive(): Boolean = pointer != -1

    override fun begin(pointerId: Int, fromX: Float, fromY: Float, fromT: Long, x: Float, y: Float, t: Long) {
        if (pointer != -1) return
        pointer = pointerId
        lastX = fromX
        lastY = fromY
        acc.reset()
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseButtonSet(MouseBtn.RIGHT, true)))
        move(pointerId, x, y, t)
    }

    override fun move(pointerId: Int, x: Float, y: Float, t: Long) {
        if (pointerId != pointer) return
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        if (dx == 0f && dy == 0f) return
        val sign = if (config.invertY) -1f else 1f
        val scale = config.sensitivity * tuning.kbmMouseScale
        if (acc.add(dx * scale, dy * scale * sign)) {
            emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseMove(acc.stepX, acc.stepY)))
        }
    }

    override fun decay(pointerId: Int, t: Long) {
        // Ruch myszy nie ma stanu do wygaszenia.
    }

    override fun end(pointerId: Int) {
        if (pointerId != pointer) return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, MouseButtonSet(MouseBtn.RIGHT, false)))
        pointer = -1
    }

    override fun reset() {
        pointer = -1
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
