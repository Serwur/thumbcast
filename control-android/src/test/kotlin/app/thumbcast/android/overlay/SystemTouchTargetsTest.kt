package app.thumbcast.android.overlay

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.SystemControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Minimalny obszar dotyku przycisków systemowych (G15): 48 dp, bez wchodzenia na inne kontrolki. */
class SystemTouchTargetsTest {

    // Telefon 20:9 w poziomie: 2400 x 1080 px przy gęstości 2,75 (873 x 393 dp).
    private val w = 2400
    private val h = 1080
    private val density = 2.75f
    private val minPx = SystemTouchTargets.MIN_TOUCH_DP * density

    private fun profile(vararg controls: app.thumbcast.model.Control) = Profile(
        schema = 1, id = "t", name = "t", mode = ProfileMode.PAD_PLUS, controls = controls.toList(),
    )

    private fun px(r: Rect) = PxRect(r.x * w, r.y * h, (r.x + r.w) * w, (r.y + r.h) * h)

    private fun overlap(a: PxRect, b: PxRect) =
        a.left < b.right - 0.01f && b.left < a.right - 0.01f && a.top < b.bottom - 0.01f && b.top < a.bottom - 0.01f

    private val menu = SystemControl(id = "app_menu", rect = Rect(0.015f, 0.02f, 0.045f, 0.09f), output = Output.App("menu"))
    private val chat = SystemControl(id = "chat", rect = Rect(0.065f, 0.02f, 0.045f, 0.09f), output = Output.App("soft_keyboard"))

    @Test
    fun smallSystemButtonsGrowToMinimumWithoutOverlapping() {
        val l1 = ButtonControl(id = "l1", rect = Rect(0.02f, 0.15f, 0.077f, 0.092f), output = Output.Pad(PadButton.LB))
        val out = SystemTouchTargets.apply(profile(menu, chat, l1), w, h, minPx)
        val m = px(out.controls[0].rect)
        val c = px(out.controls[1].rect)
        assertTrue("menu ${m.width}x${m.height}", m.width >= minPx - 0.5f && m.height >= minPx - 0.5f)
        assertTrue("chat ${c.width}x${c.height}", c.width >= minPx - 0.5f && c.height >= minPx - 0.5f)
        assertFalse(overlap(m, c))
        assertFalse(overlap(m, px(l1.rect)))
        assertFalse(overlap(c, px(l1.rect)))
        // Zwykła kontrolka bez zmian, ten sam obiekt.
        assertSame(l1, out.controls[2])
        // Środek ☰ przy krawędzi może się przesunąć, ale prostokąt zostaje na ekranie.
        val r = out.controls[0].rect
        assertTrue(r.x >= 0f && r.y >= 0f && r.x + r.w <= 1f && r.y + r.h <= 1f)
    }

    @Test
    fun largeEnoughProfileIsReturnedUnchanged() {
        val big = SystemControl(id = "m", rect = Rect(0.4f, 0.4f, 0.1f, 0.2f), output = Output.App("menu"))
        val p = profile(big)
        assertSame(p, SystemTouchTargets.apply(p, w, h, minPx))
    }

    @Test
    fun unknownSizeLeavesProfileAlone() {
        val p = profile(menu, chat)
        assertSame(p, SystemTouchTargets.apply(p, 0, 0, minPx))
    }

    @Test
    fun growsOnlyTheShortAxisAndKeepsCentreWhenThereIsRoom() {
        val m = SystemControl(id = "m", rect = Rect(0.5f, 0.5f, 0.1f, 0.02f), output = Output.App("menu"))
        val r = SystemTouchTargets.apply(profile(m), w, h, minPx).controls[0].rect
        assertEquals(0.1f, r.w, 1e-6f)
        assertEquals(0.5f, r.x, 1e-6f)
        assertEquals(minPx / h, r.h, 1e-5f)
        assertEquals(0.51f, r.y + r.h / 2f, 1e-5f)
    }

    @Test
    fun blockedSideGivesRoomToTheOtherSide() {
        // Przycisk w lewym górnym rogu: rośnie w prawo i w dół.
        val corner = SystemControl(id = "m", rect = Rect(0f, 0f, 0.01f, 0.02f), output = Output.App("menu"))
        val r = SystemTouchTargets.apply(profile(corner), w, h, minPx).controls[0].rect
        assertEquals(0f, r.x, 1e-6f)
        assertEquals(0f, r.y, 1e-6f)
        assertEquals(minPx / w, r.w, 1e-5f)
        assertEquals(minPx / h, r.h, 1e-5f)
    }

    /**
     * Presety: przyciski systemowe rosną, nigdy nie wchodzą na inne kontrolki. Gdzie sąsiad jest blisko
     * (Big Picture: ☰ i ⌨ ciasno obok siebie i LT pod spodem, ok. 46 × 43 dp), obszar jest mniejszy niż 48 dp, ale nie mniejszy
     * niż 40 dp; pozostałe presety (także domyślny „Pad – PlayStation”) dostają pełne 48 dp.
     */
    @Test
    fun shippedPresetsGetLargerTouchTargetsWithoutOverlaps() {
        val dir = File(System.getProperty("thumbcast.presetsDir") ?: "../presets")
        val files = dir.listFiles { f -> f.name.endsWith(".thumbcast.json") }.orEmpty()
        assertTrue("brak presetów w $dir", files.isNotEmpty())
        for (f in files) {
            val p = ProfileCodec.decode(f.readText()).getOrThrow()
            val out = SystemTouchTargets.apply(p, w, h, minPx)
            val rects = out.controls.map { px(it.rect) }
            for ((i, c) in out.controls.withIndex()) {
                if (c !is SystemControl) {
                    assertEquals("${f.name}: ${c.id}", p.controls[i], c)
                    continue
                }
                val r = rects[i]
                val before = px(p.controls[i].rect)
                assertTrue("${f.name}: ${c.id} zmalał", r.width >= before.width - 0.01f && r.height >= before.height - 0.01f)
                val floor = if (f.name.startsWith("big-picture")) 40f * density else minPx
                assertTrue("${f.name}: ${c.id} ${r.width}x${r.height}", r.width >= floor - 0.5f && r.height >= floor - 0.5f)
                for ((j, o) in rects.withIndex()) {
                    if (j != i) assertFalse("${f.name}: ${c.id} nachodzi na ${out.controls[j].id}", overlap(r, o))
                }
            }
        }
    }
}
