package app.thumbcast.engine

import kotlin.math.hypot
import kotlin.math.min

// Stan do rysowania (overlay, przyszły edytor układu), tylko do odczytu. Jedno źródło prawdy:
// to, co silnik naprawdę rozpoznał (właściciel palca po hit teście ze strefą aktywacji, slide-to-press,
// LATCH, środek i promień gałki), a nie osobny hit test po stronie rysowania.
// Nie wpływa na wyjście: budowany na żądanie z bieżących pól silnika, bez cyklu polityki.

/** Palec śledzony przez silnik. [controlId] == null: wolna strefa (gesty). */
data class PointerVisual(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val downX: Float,
    val downY: Float,
    val controlId: String?
)

/**
 * Gałka. Gdy [active], środek i promień są tymi samymi wartościami, z których rozpoznawacz liczy wyjście
 * (pływająca: punkt DOWN i ułamek krótszego boku ekranu; stała: środek i połowa krótszego boku `rect`).
 * Gdy nieaktywna: położenie spoczynkowe (środek `rect`) i promień, który silnik przyjmie przy DOWN.
 * [deflectionX]/[deflectionY]: surowe wychylenie palca (przed martwą strefą i krzywą) w osiach ekranu
 * (y w dół), obcięte do koła jednostkowego; 0 w spoczynku. Gałka rysowana jest w `centrum + wychylenie * promień`.
 */
data class StickVisual(
    val controlId: String,
    val floating: Boolean,
    val active: Boolean,
    val pointerId: Int,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val deflectionX: Float,
    val deflectionY: Float
)

/** Migawka stanu wizualnego silnika. */
data class EngineVisualState(
    /** Palce w kolejności DOWN (bez palców ponad limit, które silnik ignoruje). */
    val pointers: List<PointerVisual>,
    /** Przyciski HOLD wciśnięte przez co najmniej jeden palec. */
    val heldIds: Set<String>,
    /** Modyfikatory LATCH w stanie zatrzaśniętym. */
    val latchedIds: Set<String>,
    /** Wszystkie gałki profilu (także nieaktywne), po id kontrolki. */
    val sticks: Map<String, StickVisual>
) {
    /** Kontrolka będąca właścicielem palca albo null (gest lub palec nieznany). */
    fun controlOf(pointerId: Int): String? {
        for (p in pointers) if (p.pointerId == pointerId) return p.controlId
        return null
    }

    /** Czy na kontrolce leży jakiś palec (od DOWN albo po slide-to-press). */
    fun isTouched(controlId: String): Boolean {
        for (p in pointers) if (p.controlId == controlId) return true
        return false
    }

    /** Wygląd „wciśnięty”: palec na kontrolce, trzymany HOLD albo zatrzaśnięty LATCH. */
    fun isPressed(controlId: String): Boolean =
        isTouched(controlId) || heldIds.contains(controlId) || latchedIds.contains(controlId)

    companion object {
        val EMPTY: EngineVisualState = EngineVisualState(emptyList(), emptySet(), emptySet(), emptyMap())

        /** Wołane wyłącznie przez [InputEngine.visualState]. */
        internal fun capture(
            tracked: List<TrackedPointer>,
            sticks: Collection<StickRecognizer>,
            buttons: Collection<ButtonRecognizer>,
            hitTest: HitTest,
            viewport: Viewport,
            tuning: EngineTuning
        ): EngineVisualState {
            val pointers = ArrayList<PointerVisual>(tracked.size)
            for (p in tracked) {
                val owner = p.owner
                val cid = if (owner is ControlOwner) owner.controlId else null
                pointers.add(PointerVisual(p.id, p.x, p.y, p.downX, p.downY, cid))
            }
            val held = LinkedHashSet<String>()
            val latched = LinkedHashSet<String>()
            for (b in buttons) {
                if (b.isHeld) held.add(b.spec.id)
                if (b.isLatched) latched.add(b.spec.id)
            }
            val sv = LinkedHashMap<String, StickVisual>()
            for (s in sticks) {
                sv[s.spec.id] = stickVisual(s, tracked, hitTest, viewport, tuning)
            }
            return EngineVisualState(pointers, held, latched, sv)
        }

        private fun stickVisual(
            s: StickRecognizer,
            tracked: List<TrackedPointer>,
            hitTest: HitTest,
            viewport: Viewport,
            tuning: EngineTuning
        ): StickVisual {
            val id = s.spec.id
            val pid = s.activePointerId
            var finger: TrackedPointer? = null
            if (pid != -1) for (p in tracked) if (p.id == pid) finger = p
            if (finger != null) {
                val r = s.radiusPx
                var dx = (finger.x - s.centerXPx) / r
                var dy = (finger.y - s.centerYPx) / r
                val m = hypot(dx, dy)
                if (m > 1f) {
                    dx /= m
                    dy /= m
                }
                return StickVisual(
                    id, s.spec.floating, true, pid, s.centerXPx, s.centerYPx, r,
                    StickMath.clean(dx), StickMath.clean(dy)
                )
            }
            val rect = hitTest.rectOf(id) ?: PxRect(0f, 0f, 0f, 0f)
            return StickVisual(id, s.spec.floating, false, -1, rect.centerX, rect.centerY, restRadius(s.spec, rect, viewport, tuning), 0f, 0f)
        }

        /** Promień, który [StickRecognizer.onDown] przyjmie dla tej gałki (ta sama arytmetyka). */
        internal fun restRadius(spec: StickSpec, rect: PxRect, viewport: Viewport, tuning: EngineTuning): Float {
            val r = if (spec.floating) {
                tuning.floatingStickRadiusFraction * min(viewport.widthPx, viewport.heightPx).toFloat()
            } else {
                min(rect.width, rect.height) / 2f
            }
            return if (r < 1f) 1f else r
        }
    }
}
