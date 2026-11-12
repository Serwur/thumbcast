package app.thumbcast.android.stats

import app.thumbcast.android.overlay.PxRect

/**
 * Położenie nakładki statystyk: prawy górny róg obszaru bezpiecznego (bez wycięcia ekranu i pasków systemowych),
 * tak żeby nie zasłaniała kontrolek. Czysty JVM: testy w `StatsPlacementTest`.
 *
 * Kandydaci: x = przy prawej krawędzi albo tuż na lewo od którejś kontrolki, y = przy górnej krawędzi albo tuż pod
 * którąś kontrolką. Wygrywa kandydat bez kolizji najbliżej prawego górnego rogu; przesunięcie w dół liczy się
 * podwójnie (napis ma zostać u góry: krótka linia nad RB/RT, a szczegóły np. pod RB/RT albo na lewo od nich).
 * Gdy nic nie jest wolne (kontrolki w całym rogu): prawy górny róg mimo kolizji (kontrolki są rysowane nad
 * nakładką, a dotyk i tak trafia do nich, bo nakładka go nie przechwytuje).
 */
object StatsPlacement {

    /** Styk krawędzi po zaokrągleniach float to nie kolizja. */
    private const val EPS: Float = 0.5f

    /** Waga przesunięcia w dół względem przesunięcia w lewo. */
    private const val DOWN_WEIGHT: Float = 2f

    /** Wynik: lewy górny róg nakładki w px rodzica; [collides] = nie znaleziono wolnego miejsca. */
    data class Position(val x: Float, val y: Float, val collides: Boolean)

    /**
     * @param containerW, containerH rozmiar rodzica (ekran aktywności) w px
     * @param safe obszar bezpieczny w px rodzica (ekran minus wcięcia wycięcia i pasków)
     * @param boxW, boxH rozmiar nakładki w px
     * @param margin odstęp od krawędzi obszaru bezpiecznego i od kontrolek w px
     * @param obstacles prostokąty kontrolek (i innych napisów) w px rodzica
     */
    @JvmStatic
    fun place(
        containerW: Float,
        containerH: Float,
        safe: PxRect,
        boxW: Float,
        boxH: Float,
        margin: Float,
        obstacles: List<PxRect>,
    ): Position {
        val minX: Float = maxOf(0f, safe.left) + margin
        val maxRight: Float = minOf(containerW, safe.right) - margin
        val minY: Float = maxOf(0f, safe.top) + margin
        val maxBottom: Float = minOf(containerH, safe.bottom) - margin
        val startX: Float = maxOf(minX, maxRight - boxW)
        val startY: Float = minY
        val solid: List<PxRect> = obstacles.filter { it.width > 0f && it.height > 0f }

        val xs: ArrayList<Float> = arrayListOf(startX)
        val ys: ArrayList<Float> = arrayListOf(startY)
        for (o in solid) {
            val x: Float = o.left - margin - boxW
            if (x >= minX && x <= startX) xs.add(x)
            val y: Float = o.bottom + margin
            if (y >= startY && y + boxH <= maxBottom) ys.add(y)
        }

        var best: Position? = null
        var bestCost: Float = Float.MAX_VALUE
        for (x in xs) {
            for (y in ys) {
                val cost: Float = (startX - x) + (y - startY) * DOWN_WEIGHT
                if (cost >= bestCost) continue
                if (collides(x, y, boxW, boxH, margin, solid)) continue
                best = Position(x, y, false)
                bestCost = cost
            }
        }
        return best ?: Position(startX, startY, true)
    }

    private fun collides(x: Float, y: Float, w: Float, h: Float, margin: Float, obstacles: List<PxRect>): Boolean {
        val l: Float = x - margin + EPS
        val t: Float = y - margin + EPS
        val r: Float = x + w + margin - EPS
        val b: Float = y + h + margin - EPS
        return obstacles.any { o -> o.left < r && o.right > l && o.top < b && o.bottom > t }
    }
}
