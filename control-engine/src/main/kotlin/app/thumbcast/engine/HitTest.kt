package app.thumbcast.engine

import app.thumbcast.model.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Prostokąt zmiennoprzecinkowy w px overlaya. */
data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    fun expanded(m: Float): PxRect = PxRect(left - m, top - m, right + m, bottom + m)

    /** Odległość punktu od prostokąta (0 wewnątrz). */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = max(max(left - x, 0f), x - right)
        val dy = max(max(top - y, 0f), y - bottom)
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        fun of(r: RectPx): PxRect = PxRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    }
}

/**
 * Rozmieszczenie kontrolek w px i trafianie dotykiem.
 * - rect znormalizowany (0..1) liczymy względem safeArea (WindowInsets) albo całego overlaya,
 * - leworęczność: x -> 1 - x - w (plan 5.4), wyjścia się nie zmieniają,
 * - z-order: późniejsza kontrolka na liście leży wyżej,
 * - strefa aktywacji większa od grafiki o margines (hitSlop); grafika ma pierwszeństwo przed strefą.
 */
class HitTest(
    controls: List<ControlSpec>,
    viewport: Viewport,
    leftHanded: Boolean,
    private val tuning: EngineTuning
) {
    data class Placed(val spec: ControlSpec, val rect: PxRect, val activation: PxRect, val z: Int)

    /** Obszar, względem którego liczone są rect kontrolek. */
    val controlBounds: PxRect = PxRect.of(viewport.safeArea ?: RectPx(0, 0, viewport.widthPx, viewport.heightPx))

    /** Cały overlay. */
    val screenBounds: PxRect = PxRect(0f, 0f, viewport.widthPx.toFloat(), viewport.heightPx.toFloat())

    val placed: List<Placed>

    init {
        val list = ArrayList<Placed>()
        var z = 0
        for (c in controls) {
            val r = toPx(c.rect, controlBounds, leftHanded)
            val slop = max(tuning.hitSlopMinPx, tuning.hitSlopFraction * min(r.width, r.height))
            list.add(Placed(c, r, r.expanded(slop), z))
            z += 1
        }
        placed = list
    }

    /** Kontrolka pod punktem albo null (wolna strefa gestów). */
    fun hit(x: Float, y: Float): ControlSpec? {
        // 1) Grafika, od najwyższej warstwy.
        for (i in placed.indices.reversed()) {
            val p = placed[i]
            if (p.rect.contains(x, y)) return p.spec
        }
        // 2) Strefa aktywacji: najbliższa grafika, przy remisie wyższa warstwa.
        var best: Placed? = null
        var bestDist = Float.MAX_VALUE
        for (i in placed.indices.reversed()) {
            val p = placed[i]
            if (!p.activation.contains(x, y)) continue
            val d = p.rect.distanceTo(x, y)
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return best?.spec
    }

    fun rectOf(id: String): PxRect? {
        for (p in placed) if (p.spec.id == id) return p.rect
        return null
    }

    companion object {
        fun toPx(r: Rect, base: PxRect, leftHanded: Boolean): PxRect {
            val nx = if (leftHanded) 1f - r.x - r.w else r.x
            val left = base.left + nx * base.width
            val top = base.top + r.y * base.height
            return PxRect(left, top, left + r.w * base.width, top + r.h * base.height)
        }
    }
}
