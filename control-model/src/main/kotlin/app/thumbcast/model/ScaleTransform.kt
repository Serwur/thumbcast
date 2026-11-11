package app.thumbcast.model

/**
 * Rozmiar kontrolek (ustawienie użytkownika, np. 85% na telefonie): każdy `rect` skalowany wokół
 * własnego środka o [factor], potem wsuwany z powrotem w 0..1, jeśli wystaje (przesunięcie, nie
 * przycięcie, żeby kontrolka zachowała rozmiar). Gałki skalują się tak samo jak przyciski: promień
 * gałki w overlayu wynika z jej `rect`, więc zachowanie (deadzone, sprintLock) jest względne i spójne.
 *
 * Nic poza `controls[*].rect` się nie zmienia (wyjścia, gesty, strefy). Wynik zaokrąglony do siatki
 * 1e-4 (jak [HandednessTransform]), więc dla `factor = 1` zwraca profil równy wejściowemu
 * dla presetów zapisanych z dokładnością do 4 miejsc.
 */
object ScaleTransform {

    const val MIN_FACTOR = 0.3f
    const val MAX_FACTOR = 2.0f

    private const val GRID = 10000.0
    private const val MIN_SIZE = 1.0 / GRID

    /** Skaluje wszystkie kontrolki profilu. `factor` jest przycinany do [MIN_FACTOR]..[MAX_FACTOR]. */
    fun scale(p: Profile, factor: Float): Profile {
        val f: Double = clampFactor(factor)
        if (f == 1.0) return p
        return p.copy(controls = p.controls.map { scaleControl(it, f) })
    }

    /** Skaluje jeden prostokąt wokół środka i wsuwa go w 0..1. */
    fun scaleRect(r: Rect, factor: Float): Rect = scaleRect(r, clampFactor(factor))

    private fun scaleRect(r: Rect, f: Double): Rect {
        val w: Double = (r.w.toDouble() * f).coerceIn(MIN_SIZE, 1.0)
        val h: Double = (r.h.toDouble() * f).coerceIn(MIN_SIZE, 1.0)
        val cx: Double = r.x.toDouble() + r.w.toDouble() / 2.0
        val cy: Double = r.y.toDouble() + r.h.toDouble() / 2.0
        val x: Double = (cx - w / 2.0).coerceIn(0.0, 1.0 - w)
        val y: Double = (cy - h / 2.0).coerceIn(0.0, 1.0 - h)
        val sw: Double = snap(w)
        val sh: Double = snap(h)
        // Po zaokrągleniu x+w nadal nie może przekroczyć 1.
        val sx: Double = snap(x).coerceAtMost(1.0 - sw)
        val sy: Double = snap(y).coerceAtMost(1.0 - sh)
        return Rect(sx.toFloat(), sy.toFloat(), sw.toFloat(), sh.toFloat())
    }

    private fun scaleControl(c: Control, f: Double): Control = when (c) {
        is StickControl -> c.copy(rect = scaleRect(c.rect, f))
        is ButtonControl -> c.copy(rect = scaleRect(c.rect, f))
        is ModifierControl -> c.copy(rect = scaleRect(c.rect, f))
        is SystemControl -> c.copy(rect = scaleRect(c.rect, f))
    }

    private fun clampFactor(factor: Float): Double {
        if (factor.isNaN()) return 1.0
        return factor.toDouble().coerceIn(MIN_FACTOR.toDouble(), MAX_FACTOR.toDouble())
    }

    private fun snap(v: Double): Double {
        val rounded: Double = Math.round(v * GRID).toDouble() / GRID
        return rounded.coerceIn(0.0, 1.0)
    }
}
