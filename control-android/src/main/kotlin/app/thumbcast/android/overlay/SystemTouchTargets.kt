package app.thumbcast.android.overlay

import app.thumbcast.model.Control
import app.thumbcast.model.Profile
import app.thumbcast.model.Rect
import app.thumbcast.model.SystemControl

/**
 * Minimalny obszar dotyku przycisków systemowych (menu, klawiatura, ukrycie przycisków; G15, 12.11.2026).
 *
 * Presety mają ☰ i ⌨ o boku ok. 35–40 dp na telefonie, a strefa aktywacji silnika (`HitTest`: grafika
 * + `max(8 px, 20 %)`) dokłada tylko kilka dp. Ten krok powiększa `rect` kontrolek `system` do co najmniej
 * [minPx] w każdej osi, zanim profil trafi do silnika i rysowania, więc silnik i rysunek mają ten sam prostokąt.
 * Format profilu i plik się nie zmieniają; inne kontrolki (wejście gry) zostają bez zmian.
 *
 * Zasady (czysta funkcja, testy w `SystemTouchTargetsTest`):
 * - rośnie tylko oś krótsza niż [minPx], symetrycznie wokół środka;
 * - nie wychodzi poza ekran (brak miejsca z jednej strony = więcej z drugiej);
 * - nie wchodzi na inne kontrolki: do sąsiada (prostokąt nachodzący w drugiej osi) zostaje najwyżej połowa
 *   odstępu, więc dwa sąsiednie przyciski systemowe nigdy się nie nakładają;
 * - gdy miejsca brak, przycisk rośnie, ile się da (bez przesuwania sąsiadów).
 *
 * Rysowanie (`OverlayRenderer`) pokazuje w tym prostokącie mniejszą pigułkę z ikoną, więc wygląd zostaje zwarty.
 */
object SystemTouchTargets {

    /** Minimalny bok obszaru dotyku w dp (Android: 48 dp). */
    const val MIN_TOUCH_DP: Float = 48f

    fun apply(profile: Profile, widthPx: Int, heightPx: Int, minPx: Float): Profile {
        if (widthPx <= 0 || heightPx <= 0 || minPx <= 0f) return profile
        if (profile.controls.none { it is SystemControl }) return profile
        val w: Float = widthPx.toFloat()
        val h: Float = heightPx.toFloat()
        val minW: Float = minPx / w
        val minH: Float = minPx / h
        val original: List<Rect> = profile.controls.map { it.rect }
        var changed = false
        val out: ArrayList<Control> = ArrayList(profile.controls.size)
        for ((i, c) in profile.controls.withIndex()) {
            if (c !is SystemControl || (c.rect.w >= minW && c.rect.h >= minH)) {
                out.add(c)
                continue
            }
            val r: Rect = grow(c.rect, i, original, minW, minH)
            if (r != c.rect) changed = true
            out.add(c.copy(rect = r))
        }
        return if (changed) profile.copy(controls = out) else profile
    }

    private fun grow(r: Rect, self: Int, all: List<Rect>, minW: Float, minH: Float): Rect {
        // Najpierw pion (sąsiedzi = prostokąty nachodzące w poziomie), potem poziom dla nowego pionu.
        val (top, bottom) = growAxis(
            r.y, r.y + r.h, minH,
            neighbours(all, self) { o -> overlaps(o.x, o.x + o.w, r.x, r.x + r.w) }.map { it.y to it.y + it.h },
        )
        val (left, right) = growAxis(
            r.x, r.x + r.w, minW,
            neighbours(all, self) { o -> overlaps(o.y, o.y + o.h, top, bottom) }.map { it.x to it.x + it.w },
        )
        return Rect(left, top, right - left, bottom - top)
    }

    private inline fun neighbours(all: List<Rect>, self: Int, pred: (Rect) -> Boolean): List<Rect> =
        all.filterIndexed { j, o -> j != self && o.w > 0f && o.h > 0f && pred(o) }

    private fun overlaps(a0: Float, a1: Float, b0: Float, b1: Float): Boolean = a0 < b1 && b0 < a1

    /**
     * Odcinek [start, end] (0..1) wydłużony do [min]. Miejsce przed i za odcinkiem: do krawędzi ekranu
     * w całości, do sąsiada połowa odstępu (sąsiad, który już nachodzi, blokuje tę stronę).
     */
    private fun growAxis(start: Float, end: Float, min: Float, others: List<Pair<Float, Float>>): Pair<Float, Float> {
        val need: Float = min - (end - start)
        if (need <= 0f) return start to end
        var roomBefore: Float = start
        var roomAfter: Float = 1f - end
        for ((o0, o1) in others) {
            if (o1 <= start) roomBefore = minOf(roomBefore, (start - o1) / 2f)
            else if (o0 >= end) roomAfter = minOf(roomAfter, (o0 - end) / 2f)
            else {
                // Już nachodzi (profil tak zapisano): nie rośnij w żadną stronę ku niemu.
                if (o0 < start) roomBefore = 0f
                if (o1 > end) roomAfter = 0f
            }
        }
        roomBefore = roomBefore.coerceAtLeast(0f)
        roomAfter = roomAfter.coerceAtLeast(0f)
        var before: Float = minOf(need / 2f, roomBefore)
        val after: Float = minOf(need - before, roomAfter)
        before = minOf(need - after, roomBefore)
        return (start - before) to (end + after)
    }
}
