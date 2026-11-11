package app.thumbcast.android.overlay

/**
 * Wyliczanie prostokątów `View.setSystemGestureExclusionRects` (API 29+) dla stref joysticka.
 *
 * Android honoruje najwyżej 200 dp wykluczenia w pionie na każdą boczną krawędź
 * (dokumentacja `View.setSystemGestureExclusionRects`); nadmiar jest po cichu ignorowany od dołu
 * listy, więc sami przycinamy strefy tak, żeby suma wysokości po każdej stronie ≤ limitu.
 * Przycinamy symetrycznie wokół środka strefy (kciuk ląduje zwykle w jej środku).
 *
 * Tylko strefy dotykające pasa krawędzi (szerokość [edgeBandPx]) są istotne dla gestu „wstecz”;
 * pozostałe pomijamy, żeby nie zużywać limitu.
 */
object GestureExclusion {

    const val MAX_EXCLUSION_DP: Float = 200f

    /** Szerokość pasa przy krawędzi, w którym działa systemowy gest „wstecz”. */
    const val EDGE_BAND_DP: Float = 32f

    /** Strefy gałek z układu, przycięte do limitu (pas krawędzi [EDGE_BAND_DP]). */
    fun forSticks(layout: ControlLayout, viewWidth: Float, density: Float): List<PxRect> {
        val zones: ArrayList<PxRect> = ArrayList()
        for (c in layout.controls) {
            if (c.kind == LaidOutControl.Kind.STICK) zones.add(c.bounds)
        }
        return compute(zones, viewWidth, density, EDGE_BAND_DP * density)
    }

    fun compute(
        zones: List<PxRect>,
        viewWidth: Float,
        density: Float,
        edgeBandPx: Float,
    ): List<PxRect> {
        val budgetPx: Float = MAX_EXCLUSION_DP * density
        var leftUsed: Float = 0f
        var rightUsed: Float = 0f
        val out: ArrayList<PxRect> = ArrayList()
        for (z in zones) {
            val touchesLeft: Boolean = z.left <= edgeBandPx
            val touchesRight: Boolean = z.right >= viewWidth - edgeBandPx
            if (!touchesLeft && !touchesRight) {
                continue
            }
            // Strefa na całą szerokość zużywa budżet obu krawędzi; bierzemy mniejszy z nich.
            val used: Float = if (touchesLeft && touchesRight) {
                maxOf(leftUsed, rightUsed)
            } else if (touchesLeft) {
                leftUsed
            } else {
                rightUsed
            }
            val remaining: Float = budgetPx - used
            if (remaining <= 0f) {
                continue
            }
            val clipped: PxRect = clipHeight(z, remaining)
            out.add(clipped)
            val h: Float = clipped.height
            if (touchesLeft) leftUsed += h
            if (touchesRight) rightUsed += h
        }
        return out
    }

    private fun clipHeight(z: PxRect, maxHeight: Float): PxRect {
        if (z.height <= maxHeight) {
            return z
        }
        val half: Float = maxHeight * 0.5f
        val cy: Float = z.centerY
        return PxRect(z.left, cy - half, z.right, cy + half)
    }
}
