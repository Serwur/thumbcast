package app.thumbcast.android.stats

import app.thumbcast.android.overlay.PxRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Położenie nakładki statystyk: prawy górny róg, omijanie kontrolek i wycięcia ekranu. */
class StatsPlacementTest {

    // Telefon 20:9 w poziomie, 2400 x 1080 px.
    private val w = 2400f
    private val h = 1080f
    private val full = PxRect(0f, 0f, w, h)
    private val margin = 8f

    /** Prostokąt z ułamków ekranu, jak `rect` w presetach. */
    private fun frac(x: Float, y: Float, rw: Float, rh: Float) = PxRect(x * w, y * h, (x + rw) * w, (y + rh) * h)

    // „Pad – PlayStation” / „Pad – Xbox”: RB i RT przy prawej krawędzi, od y = 0,150.
    private val rb = frac(0.813f, 0.150f, 0.077f, 0.092f)
    private val rt = frac(0.903f, 0.150f, 0.077f, 0.092f)

    private fun overlaps(x: Float, y: Float, bw: Float, bh: Float, r: PxRect) =
        r.left < x + bw && r.right > x && r.top < y + bh && r.bottom > y

    @Test
    fun basicLineFitsAboveShoulderButtonsInPadPresets() {
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(rb, rt))
        assertEquals(w - margin - 330f, p.x, 0.01f)
        assertEquals(margin, p.y, 0.01f)
        assertFalse(p.collides)
        assertFalse(overlaps(p.x, p.y, 330f, 48f, rb))
        assertFalse(overlaps(p.x, p.y, 330f, 48f, rt))
    }

    @Test
    fun respectsCutoutAndSystemBarInsets() {
        // Wycięcie po prawej (telefon obrócony) i pasek stanu u góry.
        val safe = PxRect(0f, 60f, w - 110f, h)
        val p = StatsPlacement.place(w, h, safe, 330f, 48f, margin, emptyList())
        assertEquals(w - 110f - margin - 330f, p.x, 0.01f)
        assertEquals(60f + margin, p.y, 0.01f)
    }

    @Test
    fun movesLeftOfControlInTopRightCorner() {
        // Profil lustrzany: ☰ w prawym górnym rogu.
        val menu = frac(0.94f, 0.017f, 0.043f, 0.076f)
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(menu))
        assertFalse(p.collides)
        assertEquals(menu.left - margin - 330f, p.x, 0.01f)
        assertEquals(margin, p.y, 0.01f)
    }

    @Test
    fun tallBoxFitsBetweenStartAndShoulderButtons() {
        val start = frac(0.541f, 0.135f, 0.054f, 0.072f)
        val p = StatsPlacement.place(w, h, full, 400f, 300f, margin, listOf(rb, rt, start))
        assertFalse(p.collides)
        for (o in listOf(rb, rt, start)) assertFalse(overlaps(p.x, p.y, 400f, 300f, o))
        // Między START a RB jest miejsce: od prawej krawędzi START do lewej RB.
        assertTrue(p.x >= start.right && p.x + 400f <= rb.left)
    }

    @Test
    fun wideAdvancedBoxInPadPresetAvoidsAllControls() {
        // Prawa górna połowa presetu „Pad – PlayStation” (rect z pliku); szczegóły ok. 9 linii po 10–11 sp.
        val controls = listOf(
            rb, rt,
            frac(0.477f, 0.020f, 0.045f, 0.100f), // guide
            frac(0.541f, 0.135f, 0.054f, 0.072f), // start
            frac(0.595f, 0.575f, 0.045f, 0.100f), // r3
            frac(0.852f, 0.586f, 0.060f, 0.133f), // y
            frac(0.015f, 0.020f, 0.045f, 0.090f), // ☰
        )
        val bw = 700f
        val bh = 340f
        val p = StatsPlacement.place(w, h, full, bw, bh, margin, controls)
        assertFalse(p.collides)
        for (o in controls) assertFalse(overlaps(p.x, p.y, bw, bh, o))
        // Przy prawej krawędzi, tuż pod RB/RT.
        assertEquals(w - margin - bw, p.x, 0.01f)
        assertEquals(rb.bottom + margin, p.y, 0.01f)
    }

    @Test
    fun prefersMovingLeftOverMovingDown() {
        // Kontrolka 100 px w rogu: w lewo o ~100 px tańsze niż w dół o ~100 px.
        val corner = PxRect(w - 108f, 0f, w, 100f)
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(corner))
        assertEquals(margin, p.y, 0.01f)
        assertEquals(corner.left - margin - 330f, p.x, 0.01f)
    }

    @Test
    fun goesDownTheRightEdgeWhenTopRowIsFull() {
        val topRow = PxRect(0f, 0f, w, 120f)
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(topRow))
        assertFalse(p.collides)
        assertEquals(w - margin - 330f, p.x, 0.01f)
        assertEquals(120f + margin, p.y, 0.01f)
    }

    @Test
    fun fallsBackToCornerWhenNothingIsFree() {
        val everything = PxRect(0f, 0f, w, h)
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(everything))
        assertTrue(p.collides)
        assertEquals(w - margin - 330f, p.x, 0.01f)
        assertEquals(margin, p.y, 0.01f)
    }

    @Test
    fun emptyObstaclesAreIgnored() {
        val p = StatsPlacement.place(w, h, full, 330f, 48f, margin, listOf(PxRect(2200f, 10f, 2200f, 10f)))
        assertFalse(p.collides)
        assertEquals(w - margin - 330f, p.x, 0.01f)
    }

    @Test
    fun boxWiderThanScreenStaysInside() {
        val p = StatsPlacement.place(300f, 200f, PxRect(0f, 0f, 300f, 200f), 500f, 48f, margin, emptyList())
        assertEquals(margin, p.x, 0.01f)
        assertEquals(margin, p.y, 0.01f)
    }
}
