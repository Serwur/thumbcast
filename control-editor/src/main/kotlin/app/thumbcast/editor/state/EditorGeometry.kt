package app.thumbcast.editor.state

import app.thumbcast.model.Rect

/**
 * Geometria edytora na prostokątach znormalizowanych 0..1 (cały ekran edytora = cały widok overlaya).
 * Czyste funkcje, bez Androida. Każdy wynik:
 * - mieści się na ekranie (`x + w <= 1`, `y + h <= 1`), boki w [MIN_SIZE, 1],
 * - jest zaokrąglony do 4 miejsc po przecinku (tak zapisują edytor i presety, por. HandednessTransform).
 */
object EditorGeometry {

    /** Krok siatki przyciągania: 1% ekranu. */
    const val GRID: Float = 0.01f

    /** Najmniejszy bok kontrolki (2% ekranu). */
    const val MIN_SIZE: Float = 0.02f

    private const val DECIMALS: Double = 10000.0

    fun round4(v: Float): Float = (Math.round(v.toDouble() * DECIMALS).toDouble() / DECIMALS).toFloat()

    fun snap(v: Float, enabled: Boolean): Float {
        if (!enabled) return v
        val steps: Long = Math.round(v.toDouble() / GRID.toDouble())
        return (steps.toDouble() * GRID.toDouble()).toFloat()
    }

    /** Obcina prostokąt do ekranu (najpierw rozmiar, potem położenie) i zaokrągla. */
    fun clamp(r: Rect): Rect {
        val w: Float = round4(finite(r.w, MIN_SIZE).coerceIn(MIN_SIZE, 1f))
        val h: Float = round4(finite(r.h, MIN_SIZE).coerceIn(MIN_SIZE, 1f))
        val x: Float = round4(finite(r.x, 0f).coerceIn(0f, 1f - w)).coerceAtMost(round4(1f - w))
        val y: Float = round4(finite(r.y, 0f).coerceIn(0f, 1f - h)).coerceAtMost(round4(1f - h))
        return Rect(x.coerceAtLeast(0f), y.coerceAtLeast(0f), w, h)
    }

    /** Przesunięcie o (dx, dy) od położenia [base]; przyciąga lewy górny róg do siatki. */
    fun move(base: Rect, dx: Float, dy: Float, snap: Boolean): Rect =
        clamp(base.copy(x = snap(base.x + dx, snap), y = snap(base.y + dy, snap)))

    /** Uchwyt w prawym dolnym rogu: lewy górny róg stoi, boki zmieniają się niezależnie. */
    fun resizeCorner(base: Rect, dx: Float, dy: Float, snap: Boolean): Rect {
        // maxOf: profil z pliku może mieć kontrolkę mniejszą niż MIN_SIZE przy samej krawędzi.
        val w: Float = snap(base.w + dx, snap).coerceIn(MIN_SIZE, maxOf(MIN_SIZE, 1f - base.x))
        val h: Float = snap(base.h + dy, snap).coerceIn(MIN_SIZE, maxOf(MIN_SIZE, 1f - base.y))
        return clamp(base.copy(w = w, h = h))
    }

    /**
     * Skalowanie jednolite względem środka (szczypanie, suwak rozmiaru): proporcje boków zostają.
     * Przy przyciąganiu do siatki przyciągana jest wysokość, a szerokość idzie za nią w tej samej proporcji.
     */
    fun scaleUniform(base: Rect, factor: Float, snap: Boolean): Rect {
        // Początek szczypania (współczynnik 1) nie może przesunąć kontrolki przez przyciąganie do siatki.
        if (base.w <= 0f || base.h <= 0f || Math.abs(factor - 1f) < 1e-4f) return clamp(base)
        val minFactor: Float = maxOf(MIN_SIZE / base.w, MIN_SIZE / base.h)
        val maxFactor: Float = minOf(1f / base.w, 1f / base.h)
        val f: Float = if (minFactor > maxFactor) 1f else finite(factor, 1f).coerceIn(minFactor, maxFactor)
        var h: Float = base.h * f
        if (snap) h = snap(h, true).coerceIn(MIN_SIZE, 1f)
        val w: Float = (h * base.w / base.h).coerceIn(MIN_SIZE, 1f)
        val cx: Float = base.x + base.w * 0.5f
        val cy: Float = base.y + base.h * 0.5f
        return clamp(Rect(snap(cx - w * 0.5f, snap), snap(cy - h * 0.5f, snap), w, h))
    }

    /** Suwak rozmiaru: ustawia wysokość, szerokość w tej samej proporcji, środek bez zmian. */
    fun withHeight(base: Rect, height: Float, snap: Boolean): Rect =
        if (base.h <= 0f) clamp(base) else scaleUniform(base, height / base.h, snap)

    private fun finite(v: Float, fallback: Float): Float = if (v.isNaN() || v.isInfinite()) fallback else v
}
