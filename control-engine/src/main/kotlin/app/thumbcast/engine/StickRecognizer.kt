package app.thumbcast.engine

import app.thumbcast.model.CurveType
import app.thumbcast.model.StickKeys
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Czysta matematyka gałki (testowana osobno). */
object StickMath {

    /** Usuwa -0f (data class porównuje Float przez equals, a -0f != 0f). */
    fun clean(v: Float): Float = if (v == 0f) 0f else v

    fun applyCurve(v: Float, curve: CurveSpec): Float {
        val c = v.coerceIn(0f, 1f)
        if (curve.type == CurveType.POWER) {
            val exp = if (curve.exponent > 0f) curve.exponent else 1f
            return c.toDouble().pow(exp.toDouble()).toFloat()
        }
        return c
    }

    /**
     * „Rescaled radial deadzone”: wektor surowy (|v| = 1 na obrysie) -> wyjście.
     * |v| <= deadzone daje 0, |v| >= 1 - outer daje 1, pomiędzy liniowe przeskalowanie (bez skoku na granicy),
     * potem krzywa. Kierunek zachowany. Zwraca [x, y].
     */
    fun shape(rawX: Float, rawY: Float, deadzone: Float, outerDeadzone: Float, curve: CurveSpec): FloatArray {
        val m = hypot(rawX, rawY)
        if (m <= deadzone || m <= 0f) return floatArrayOf(0f, 0f)
        val span = 1f - deadzone - outerDeadzone
        val scaled = if (span <= 0f) 1f else ((m - deadzone) / span).coerceIn(0f, 1f)
        val out = applyCurve(scaled, curve)
        return floatArrayOf(clean(rawX / m * out), clean(rawY / m * out))
    }

    /**
     * Czy surowe wychylenie o module [m] daje w [shape] pełne 100% (ta sama arytmetyka co w [shape]:
     * poza martwą strefą i przeskalowany moduł ≥ 1, czyli m ≥ 1 - outerDeadzone).
     */
    fun saturated(m: Float, deadzone: Float, outerDeadzone: Float): Boolean {
        if (m <= deadzone || m <= 0f) return false
        val span = 1f - deadzone - outerDeadzone
        return span <= 0f || (m - deadzone) / span >= 1f
    }

    /** Pełne wychylenie (moduł 1) w kierunku wektora surowego; wymaga |v| > 0. Przy nasyceniu równe [shape]. */
    fun full(rawX: Float, rawY: Float): FloatArray {
        val m = hypot(rawX, rawY)
        return floatArrayOf(clean(rawX / m), clean(rawY / m))
    }

    /**
     * 8 kierunków dla trybu KBM. y dodatnie = w górę. Zwraca zbiór 0..2 sąsiednich klawiszy.
     * Sektory po 45 stopni wyśrodkowane na osiach i przekątnych.
     */
    fun directionKeys(x: Float, y: Float, threshold: Float, keys: StickKeys): Set<String> {
        val m = hypot(x, y)
        if (m <= 0f || m < threshold) return emptySet()
        val deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
        var sector = (deg / 45.0).roundToInt() % 8
        if (sector < 0) sector += 8
        val out = LinkedHashSet<String>()
        when (sector) {
            0 -> out.add(keys.right)
            1 -> { out.add(keys.up); out.add(keys.right) }
            2 -> out.add(keys.up)
            3 -> { out.add(keys.up); out.add(keys.left) }
            4 -> out.add(keys.left)
            5 -> { out.add(keys.down); out.add(keys.left) }
            6 -> out.add(keys.down)
            else -> { out.add(keys.down); out.add(keys.right) }
        }
        return out
    }
}

/**
 * Gałka (pływająca lub stała). Obsługuje jeden palec naraz (pierwszy, który wylądował);
 * kolejny palec w tej samej kontrolce jest przechwycony, ale nic nie generuje.
 * Wyjście: lewa/prawa gałka pada albo (KBM + kbmKeys) klawisze kierunków z progiem 8 kierunków.
 *
 * `sprintLock` (histereza nasycenia, decyzja 11.11.2026): gdy palec dojdzie do nasycenia (|v| ≥ 1 - outerDeadzone),
 * gałka wchodzi w stan „sprint”: moduł wyjścia zostaje 100%, a kierunek idzie za bieżącym kątem palca, także gdy
 * kciuk cofnie się do obrysu. Stan kończy powrót w martwą strefę (|v| ≤ deadzone; dalej zwykła krzywa),
 * UP, CANCEL albo releaseAll. Stan istnieje tylko, dopóki ten palec dotyka; zmienia się wyłącznie w cyklu zdarzenia
 * tego palca (DOWN/MOVE), bez timerów i bez ticków.
 */
class StickRecognizer(
    val spec: StickSpec,
    private val kbm: Boolean,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "control:" + spec.id

    private var activePointer: Int = -1
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 1f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    /** Stan histerezy `sprintLock` (tylko przy aktywnym palcu). */
    private var sprinting: Boolean = false
    private val keysHeld: LinkedHashSet<String> = LinkedHashSet()

    val activePointerId: Int get() = activePointer

    fun onDown(pointerId: Int, x: Float, y: Float, rect: PxRect, viewport: Viewport) {
        if (activePointer != -1) return
        activePointer = pointerId
        sprinting = false
        if (spec.floating) {
            centerX = x
            centerY = y
            radius = tuning.floatingStickRadiusFraction * min(viewport.widthPx, viewport.heightPx).toFloat()
        } else {
            centerX = rect.centerX
            centerY = rect.centerY
            radius = min(rect.width, rect.height) / 2f
        }
        if (radius < 1f) radius = 1f
        update(pointerId, x, y)
    }

    fun onMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != activePointer) return
        update(pointerId, x, y)
    }

    fun onUp(pointerId: Int) {
        if (pointerId != activePointer) return
        if (useKeys()) {
            val held = ArrayList<String>(keysHeld)
            keysHeld.clear()
            for (k in held) emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, KeySet(k, false)))
        } else {
            emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, StickSet(spec.padStick, 0f, 0f)))
        }
        lastX = 0f
        lastY = 0f
        sprinting = false
        activePointer = -1
    }

    /** Bez emisji (stan wyjścia zeruje ActionPolicy/OutputMapper). */
    fun reset() {
        activePointer = -1
        lastX = 0f
        lastY = 0f
        sprinting = false
        keysHeld.clear()
    }

    private fun useKeys(): Boolean = kbm && spec.kbmKeys != null

    private fun update(pointerId: Int, x: Float, y: Float) {
        // Ekran: y w dół; gałka: y w górę.
        val rawX = (x - centerX) / radius
        val rawY = -(y - centerY) / radius
        val v = if (spec.sprintLock) sprintShape(rawX, rawY) else StickMath.shape(rawX, rawY, spec.deadzone, spec.outerDeadzone, spec.curve)
        val keys = spec.kbmKeys
        if (kbm && keys != null) {
            val want = StickMath.directionKeys(v[0], v[1], spec.kbmThreshold, keys)
            // Najpierw zwolnienia, potem wciśnięcia.
            val toRelease = ArrayList<String>()
            for (k in keysHeld) if (!want.contains(k)) toRelease.add(k)
            for (k in toRelease) {
                keysHeld.remove(k)
                emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, KeySet(k, false)))
            }
            for (k in want) {
                if (keysHeld.add(k)) emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, KeySet(k, true)))
            }
        } else {
            if (v[0] != lastX || v[1] != lastY) {
                lastX = v[0]
                lastY = v[1]
                emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, StickSet(spec.padStick, v[0], v[1])))
            }
        }
    }

    /** [StickMath.shape] z histerezą `sprintLock`: wejście przy nasyceniu, wyjście w martwej strefie. */
    private fun sprintShape(rawX: Float, rawY: Float): FloatArray {
        val m = hypot(rawX, rawY)
        if (m <= spec.deadzone || m <= 0f) {
            sprinting = false
        } else if (StickMath.saturated(m, spec.deadzone, spec.outerDeadzone)) {
            sprinting = true
        }
        if (sprinting) return StickMath.full(rawX, rawY)
        return StickMath.shape(rawX, rawY, spec.deadzone, spec.outerDeadzone, spec.curve)
    }
}
