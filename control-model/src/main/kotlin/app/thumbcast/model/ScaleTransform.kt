package app.thumbcast.model

/**
 * Rozmiar kontrolek (ustawienie użytkownika): kontrolki skalowane o [factor] względem punktu
 * zaczepienia zależnego od położenia na ekranie, a nie względem własnego środka.
 *
 * Ekran dzieli się w każdej osi na trzy pasy. Grupa, której środek leży w lewej trzeciej części,
 * trzyma się lewej krawędzi ekranu (`x -> x * factor`), w prawej trzeciej części prawej krawędzi,
 * a w środkowej jej środek jest przesuwany liniowo tak, żeby środkowy pas wypełnił miejsce między
 * pasami bocznymi. Oś Y działa tak samo (góra, środek, dół). Grupa przycisków w lewym dolnym rogu
 * maleje więc razem z odstępami w stronę tego rogu, zamiast maleć każdy wokół własnego środka (wtedy
 * odstępy między nimi rosły; test właściciela 11.11.2026).
 *
 * Grupa to kontrolki stykające się lub prawie stykające (odstęp w każdej osi do [CLUSTER_GAP]
 * mniejszego wymiaru pary), np. romb ABXY albo D-pad. Cała grupa ma jedno zaczepienie (środek jej
 * obrysu), więc zachowuje kształt także wtedy, gdy leży na granicy pasów. Pływająca gałka to strefa,
 * a nie przycisk, więc zawsze jest osobną grupą.
 *
 * Dla `factor <= 1` odwzorowania są uporządkowane (lewe <= środkowe <= prawe), więc kontrolki, które
 * się nie nakładały, w typowych układach nadal się nie nakładają. Przy powiększaniu sąsiednie
 * kontrolki z różnych grup mogą na siebie zachodzić. Na końcu każda kontrolka jest wsuwana w 0..1
 * (przesunięcie, nie przycięcie). Gałki skalują się tak samo jak przyciski: promień gałki w overlayu
 * wynika z jej `rect`, więc zachowanie (deadzone, sprintLock) jest względne i spójne.
 *
 * Nic poza `controls[*].rect` się nie zmienia (wyjścia, gesty, strefy). Wynik zaokrąglony do siatki
 * 1e-4 (jak [HandednessTransform]), więc dla `factor = 1` zwraca profil równy wejściowemu
 * dla presetów zapisanych z dokładnością do 4 miejsc.
 */
object ScaleTransform {

    const val MIN_FACTOR = 0.3f

    /** Powyżej 1.5 środkowy pas miałby ujemną skalę (odwzorowanie przestaje być rosnące). */
    const val MAX_FACTOR = 1.45f

    /** Odstęp łączący kontrolki w grupę, jako ułamek mniejszego wymiaru pary w danej osi. */
    const val CLUSTER_GAP = 0.3f

    private const val GRID = 10000.0
    private const val MIN_SIZE = 1.0 / GRID
    private const val THIRD = 1.0 / 3.0

    /** Skaluje wszystkie kontrolki profilu. `factor` jest przycinany do [MIN_FACTOR]..[MAX_FACTOR]. */
    fun scale(p: Profile, factor: Float): Profile {
        val f: Double = clampFactor(factor)
        if (f == 1.0) return p
        val rects: List<Rect> = p.controls.map { it.rect }
        val joinable: List<Boolean> = p.controls.map { !(it is StickControl && it.floating) }
        val groups: IntArray = clusters(rects, joinable)
        val bounds = HashMap<Int, Rect>()
        for (i in rects.indices) {
            val prev: Rect? = bounds[groups[i]]
            bounds[groups[i]] = if (prev == null) rects[i] else union(prev, rects[i])
        }
        return p.copy(controls = p.controls.mapIndexed { i, c ->
            withRect(c, scaleIn(rects[i], bounds.getValue(groups[i]), f))
        })
    }

    /** Skaluje jeden prostokąt (jako osobną grupę) względem jego punktu zaczepienia i wsuwa go w 0..1. */
    fun scaleRect(r: Rect, factor: Float): Rect = scaleIn(r, r, clampFactor(factor))

    /** Skaluje `r` przekształceniem grupy o obrysie `group`. */
    private fun scaleIn(r: Rect, group: Rect, f: Double): Rect {
        val w: Double = (r.w.toDouble() * f).coerceIn(MIN_SIZE, 1.0)
        val h: Double = (r.h.toDouble() * f).coerceIn(MIN_SIZE, 1.0)
        val x: Double = mapAxis(r.x.toDouble(), group.x.toDouble(), group.w.toDouble(), f).coerceIn(0.0, 1.0 - w)
        val y: Double = mapAxis(r.y.toDouble(), group.y.toDouble(), group.h.toDouble(), f).coerceIn(0.0, 1.0 - h)
        val sw: Double = snap(w)
        val sh: Double = snap(h)
        // Po zaokrągleniu x+w nadal nie może przekroczyć 1.
        val sx: Double = snap(x).coerceAtMost(1.0 - sw)
        val sy: Double = snap(y).coerceAtMost(1.0 - sh)
        return Rect(sx.toFloat(), sy.toFloat(), sw.toFloat(), sh.toFloat())
    }

    /**
     * Jedna oś: nowy początek odcinka zaczynającego się w `v`, należącego do grupy o początku `gStart`
     * i długości `gSize`. Grupa jest skalowana jednolicie o `f` względem swojego zaczepienia.
     */
    private fun mapAxis(v: Double, gStart: Double, gSize: Double, f: Double): Double {
        val gEnd: Double = gStart + gSize
        val centre: Double = gStart + gSize / 2.0
        return when {
            // Pas przy początku osi (lewo/góra): zaczepienie = krawędź ekranu 0.
            centre < THIRD -> v * f
            // Pas przy końcu osi (prawo/dół): zaczepienie = krawędź ekranu 1.
            centre > 2.0 * THIRD -> 1.0 - (1.0 - gEnd) * f - (gEnd - v) * f
            // Środek: środek grupy odwzorowany liniowo z [1/3, 2/3] na [f/3, 1 - f/3].
            else -> f * THIRD + (centre - THIRD) * (3.0 - 2.0 * f) + (v - centre) * f
        }
    }

    /** Numer grupy dla każdej kontrolki (union-find po parach leżących blisko siebie). */
    private fun clusters(rects: List<Rect>, joinable: List<Boolean>): IntArray {
        val parent = IntArray(rects.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in rects.indices) {
            if (!joinable[i]) continue
            for (j in i + 1 until rects.size) {
                if (joinable[j] && close(rects[i], rects[j])) parent[find(i)] = find(j)
            }
        }
        return IntArray(rects.size) { find(it) }
    }

    private fun close(a: Rect, b: Rect): Boolean {
        val dx: Float = maxOf(0f, b.x - (a.x + a.w), a.x - (b.x + b.w))
        val dy: Float = maxOf(0f, b.y - (a.y + a.h), a.y - (b.y + b.h))
        return dx <= CLUSTER_GAP * minOf(a.w, b.w) && dy <= CLUSTER_GAP * minOf(a.h, b.h)
    }

    private fun union(a: Rect, b: Rect): Rect {
        val x: Float = minOf(a.x, b.x)
        val y: Float = minOf(a.y, b.y)
        return Rect(x, y, maxOf(a.x + a.w, b.x + b.w) - x, maxOf(a.y + a.h, b.y + b.h) - y)
    }

    private fun withRect(c: Control, r: Rect): Control = when (c) {
        is StickControl -> c.copy(rect = r)
        is ButtonControl -> c.copy(rect = r)
        is ModifierControl -> c.copy(rect = r)
        is SystemControl -> c.copy(rect = r)
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
