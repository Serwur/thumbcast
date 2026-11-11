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
    fun scalesAroundCentre() {
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

    /** Właściwość: dla dowolnego rect na siatce i współczynnika 0.3..2.0 wynik mieści się w 0..1 i ma rozmiar ~w*factor. */
    @Test
    fun resultAlwaysInsideScreen(): Unit = runBlocking<Unit> {
        checkAll(Arb.int(0, 9999), Arb.int(1, 10000), Arb.int(0, 9999), Arb.int(30, 200)) { a, b, yy, pct ->
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
}
