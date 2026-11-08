package app.thumbcast.android.overlay

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Czyste JVM: letterbox, wykluczenia gestów, układ kontrolek, percentyle opóźnień. */
class GeometryTest {

    @Test
    fun fitCenteredPillarbox() {
        // Telefon 2400x1080 (20:9), strumień 1920x1080 (16:9): pasy po bokach.
        val r = ViewportMath.fitCentered(2400, 1080, 1920, 1080)
        assertEquals(VideoRect(240, 0, 2160, 1080), r)
    }

    @Test
    fun fitCenteredLetterbox() {
        // Tablet 1600x1200 (4:3), strumień 16:9: pasy u góry i u dołu.
        val r = ViewportMath.fitCentered(1600, 1200, 1920, 1080)
        assertEquals(VideoRect(0, 150, 1600, 1050), r)
    }

    @Test
    fun fitCenteredDegenerateInputs() {
        assertEquals(VideoRect(0, 0, 100, 50), ViewportMath.fitCentered(100, 50, 0, 0))
    }

    @Test
    fun toOverlayCoordsSubtractsOffset() {
        val r = ViewportMath.toOverlayCoords(VideoRect(240, 10, 2160, 1090), 0, 10)
        assertEquals(VideoRect(240, 0, 2160, 1080), r)
    }

    @Test
    fun gestureExclusionRespects200dpPerEdge() {
        val density = 3f
        val width = 2400f
        val budget = 200f * density
        // Dwie wysokie strefy przy lewej krawędzi: łącznie 1200 px > 600 px budżetu.
        val zones = listOf(
            PxRect(0f, 300f, 800f, 1100f),
            PxRect(0f, 0f, 300f, 400f),
            PxRect(1000f, 0f, 1200f, 400f), // nie dotyka krawędzi: pominięta
        )
        val out = GestureExclusion.compute(zones, width, density, 32f * density)
        var leftSum = 0f
        for (r in out) leftSum += r.height
        assertTrue("suma $leftSum > $budget", leftSum <= budget + 0.01f)
        assertEquals(1, out.size)
        // Przycięta symetrycznie wokół środka (y=700).
        assertEquals(700f, out[0].centerY, 0.01f)
        assertEquals(600f, out[0].height, 0.01f)
    }

    @Test
    fun gestureExclusionSeparateBudgetPerEdge() {
        val density = 2f
        val zones = listOf(
            PxRect(0f, 0f, 300f, 400f),
            PxRect(1700f, 0f, 2000f, 400f),
        )
        val out = GestureExclusion.compute(zones, 2000f, density, 64f)
        assertEquals(2, out.size)
        assertEquals(400f, out[0].height, 0.01f)
        assertEquals(400f, out[1].height, 0.01f)
    }

    private fun sampleProfile(): Profile = Profile(
        schema = 1,
        id = "t",
        name = "t",
        mode = ProfileMode.PAD_PLUS,
        controls = listOf(
            StickControl(id = "move", rect = Rect(0f, 0.5f, 0.3f, 0.5f)),
            ButtonControl(id = "a", rect = Rect(0.8f, 0.7f, 0.1f, 0.2f), output = Output.Pad(PadButton.A)),
            ButtonControl(id = "a2", rect = Rect(0.85f, 0.75f, 0.1f, 0.2f), label = "Top", output = Output.Pad(PadButton.B)),
            SystemControl(id = "menu", rect = Rect(0f, 0f, 0.05f, 0.08f), output = Output.App("menu")),
        ),
    )

    @Test
    fun layoutScalesToPixelsAndHitTestPrefersTopmost() {
        val layout = ControlLayout.of(sampleProfile(), 2000, 1000)
        val a = layout.byId("a")!!
        assertEquals(1600f, a.bounds.left, 0.01f)
        assertEquals(700f, a.bounds.top, 0.01f)
        assertEquals("A", a.text)
        // Punkt w części wspólnej "a" i "a2": wygrywa później zdefiniowane "a2".
        assertEquals("a2", layout.hitTest(1750f, 800f)!!.control.id)
        assertEquals("a", layout.hitTest(1650f, 720f)!!.control.id)
        assertNull(layout.hitTest(1000f, 100f))
        assertEquals(LaidOutControl.Kind.STICK, layout.byId("move")!!.kind)
        assertTrue(layout.byId("move")!!.floating)
    }

    @Test
    fun hiddenControlsKeepOnlySystemControls() {
        val hidden = ControlLayout.withControlsHidden(sampleProfile())
        assertEquals(listOf("menu"), hidden.controls.map { it.id })
    }

    @Test
    fun latencyPercentiles() {
        val s = LatencyStats(8)
        assertEquals(-1L, s.percentile(95))
        var i = 1L
        while (i <= 10L) {
            s.add(i)
            i++
        }
        // Bufor kołowy 8: zostają 3..10.
        assertEquals(8, s.size)
        assertEquals(10L, s.percentile(95))
        assertEquals(6L, s.percentile(50))
        assertEquals(10L, s.last)
    }
}
