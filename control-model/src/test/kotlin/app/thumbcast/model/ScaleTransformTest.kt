package app.thumbcast.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScaleTransformTest {

    private fun near(expected: Float, actual: Float, msg: String = "") =
        assertTrue(abs(expected - actual) <= 1.5e-4f, "$msg oczekiwano $expected, jest $actual")

    @Test
    fun centreRectScalesAroundItsCentre() {
        val r = ScaleTransform.scaleRect(Rect(0.4f, 0.4f, 0.2f, 0.2f), 0.5f)
        near(0.45f, r.x)
        near(0.45f, r.y)
        near(0.1f, r.w)
        near(0.1f, r.h)
    }

    @Test
    fun growingRectAtEdgeIsShiftedInside() {
        // Gałka przy lewej i dolnej krawędzi: po powiększeniu środek musiałby wyjść poza ekran.
        val r = ScaleTransform.scaleRect(Rect(0.0f, 0.6f, 0.3f, 0.4f), 1.3f)
        near(0.39f, r.w)
        near(0.52f, r.h)
        assertEquals(0f, r.x)
        assertTrue(r.y + r.h <= 1f, "wystaje w dół: $r")
        near(0.48f, r.y)
    }

    @Test
    fun sizeIsCappedAtFullScreen() {
        val r = ScaleTransform.scaleRect(Rect(0.1f, 0.1f, 0.8f, 0.8f), 2.0f)
        assertEquals(Rect(0f, 0f, 1f, 1f), r)
    }

    @Test
    fun factorIsClamped() {
        val tiny = ScaleTransform.scaleRect(Rect(0.4f, 0.4f, 0.2f, 0.2f), 0.01f)
        near(0.2f * ScaleTransform.MIN_FACTOR, tiny.w)
        val nan = ScaleTransform.scaleRect(Rect(0.4f, 0.4f, 0.2f, 0.2f), Float.NaN)
        near(0.2f, nan.w)
    }

    @Test
    fun identityFactorReturnsSameProfile() {
        val p = TestFixtures.padProfile()
        assertSame(p, ScaleTransform.scale(p, 1.0f))
    }

    @Test
    fun onlyRectsChange() {
        val p = TestFixtures.kbmProfile()
        val s = ScaleTransform.scale(p, 0.85f)
        assertEquals(p.copy(controls = emptyList()), s.copy(controls = emptyList()))
        assertEquals(p.controls.size, s.controls.size)
        for (i in p.controls.indices) {
            val a = p.controls[i]
            val b = s.controls[i]
            assertEquals(a.id, b.id)
            assertEquals(a::class, b::class)
            near(a.rect.w * 0.85f, b.rect.w, a.id)
            near(a.rect.h * 0.85f, b.rect.h, a.id)
        }
        // Reszta pól kontrolki bez zmian (np. sprintLock, deadzone, kbmKeys gałki).
        val stickBefore = p.controls[0] as StickControl
        val stickAfter = s.controls[0] as StickControl
        assertEquals(stickBefore.copy(rect = stickAfter.rect), stickAfter)
    }

    @Test
    fun scaledPresetsStayValid() {
        for (f in TestFixtures.presetFiles()) {
            val p = ProfileCodec.decode(f.readText(Charsets.UTF_8)).getOrThrow()
            for (factor in listOf(0.6f, 0.7f, 0.85f, 1.0f, 1.15f, 1.3f)) {
                val errors = ProfileValidator.validate(ScaleTransform.scale(p, factor))
                assertTrue(errors.isEmpty(), "${f.name} x$factor: $errors")
            }
        }
    }

    @Test
    fun bottomLeftClusterShrinksTowardCorner() {
        // Dwa przyciski w lewym dolnym rogu: rozmiary, odstęp i odległość od rogu maleją razem.
        val a = ScaleTransform.scaleRect(Rect(0.05f, 0.80f, 0.10f, 0.15f), 0.8f)
        val b = ScaleTransform.scaleRect(Rect(0.20f, 0.80f, 0.10f, 0.15f), 0.8f)
        near(0.04f, a.x)
        near(0.08f, a.w)
        near(0.16f, b.x)
        near(0.04f, b.x - (a.x + a.w), "odstęp")
        near(0.04f, 1f - (a.y + a.h), "odległość od dolnej krawędzi")
        near(0.12f, a.h)
    }

    @Test
    fun topRightIsAnchoredToTopRightCorner() {
        val r = ScaleTransform.scaleRect(Rect(0.85f, 0.05f, 0.10f, 0.10f), 0.5f)
        near(0.025f, 1f - (r.x + r.w), "od prawej")
        near(0.05f, r.w)
        near(0.025f, r.y, "od góry")
    }

    @Test
    fun bottomCentreKeepsHorizontalCentreAndBottomAnchor() {
        val r = ScaleTransform.scaleRect(Rect(0.45f, 0.85f, 0.10f, 0.10f), 0.5f)
        near(0.5f, r.x + r.w / 2f, "środek w poziomie")
        near(0.025f, 1f - (r.y + r.h), "od dołu")
    }

    /** Właściwość: przy zmniejszaniu kontrolki, które się nie nakładały, nadal się nie nakładają. */
    @Test
    fun shrinkingNeverCreatesOverlap(): Unit = runBlocking<Unit> {
        checkAll(Arb.int(0, 9000), Arb.int(0, 9000), Arb.int(200, 1000), Arb.int(0, 2000), Arb.int(30, 100)) { x1, y, w, gap, pct ->
            val a = Rect(x1 / 10000f, y / 10000f, w / 10000f, w / 10000f)
            val x2 = x1 + w + gap
            if (x2 + w > 10000 || y + w > 10000) return@checkAll
            val b = Rect(x2 / 10000f, y / 10000f, w / 10000f, w / 10000f)
            val f = pct / 100f
            val sa = ScaleTransform.scaleRect(a, f)
            val sb = ScaleTransform.scaleRect(b, f)
            assertTrue(sa.x + sa.w <= sb.x + 2e-4f, "nakładanie: $a, $b x$f -> $sa, $sb")
        }
    }

    /** Właściwość: dla dowolnego rect na siatce i współczynnika 0.3..1.45 wynik mieści się w 0..1 i ma rozmiar ~w*factor. */
    @Test
    fun resultAlwaysInsideScreen(): Unit = runBlocking<Unit> {
        checkAll(Arb.int(0, 9999), Arb.int(1, 10000), Arb.int(0, 9999), Arb.int(30, 145)) { a, b, yy, pct ->
            val w = minOf(a, b).coerceAtLeast(1)
            val x = (maxOf(a, b) - w).coerceAtLeast(0)
            val h = (10000 - yy).coerceAtLeast(1)
            val rect = Rect((x / 10000.0).toFloat(), (yy / 10000.0).toFloat(), (w / 10000.0).toFloat(), (h / 10000.0).toFloat())
            val factor = pct / 100f
            val r = ScaleTransform.scaleRect(rect, factor)
            assertTrue(r.x >= 0f && r.y >= 0f, "ujemne: $rect x$factor -> $r")
            assertTrue(r.x + r.w <= 1f + 1e-4f && r.y + r.h <= 1f + 1e-4f, "poza ekranem: $rect x$factor -> $r")
            assertTrue(r.w > 0f && r.h > 0f && r.w <= 1f && r.h <= 1f, "rozmiar: $rect x$factor -> $r")
            near(minOf(1f, maxOf(1e-4f, rect.w * factor)), r.w, "$rect x$factor")
        }
    }

    @Test
    fun diamondKeepsShapeAcrossBands() {
        // Romb ABXY z mmo-pad-plus: Y w środkowym pasie, A w dolnym. Grupa skaluje się jednolicie.
        fun b(id: String, x: Float, y: Float) =
            ButtonControl(id = id, rect = Rect(x, y, 0.072f, 0.162f), output = Output.Pad(PadButton.A))
        val p = TestFixtures.padProfile().copy(
            controls = listOf(b("y", 0.824f, 0.509f), b("x", 0.744f, 0.649f), b("b", 0.904f, 0.649f), b("a", 0.824f, 0.789f)),
        )
        val r = ScaleTransform.scale(p, 0.85f).controls.associate { it.id to it.rect }
        val y = r.getValue("y"); val x = r.getValue("x"); val bb = r.getValue("b"); val a = r.getValue("a")
        near(0.14f * 0.85f, x.y - y.y, "Y-X")
        near(0.14f * 0.85f, a.y - x.y, "X-A")
        near(0.08f * 0.85f, y.x - x.x, "X-Y")
        near(0.08f * 0.85f, bb.x - y.x, "Y-B")
        near(0.049f * 0.85f, 1f - (a.y + a.h), "od dołu")
        near(0.024f * 0.85f, 1f - (bb.x + bb.w), "od prawej")
    }
}
