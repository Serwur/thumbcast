package app.thumbcast.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandednessTransformTest {

    @Test
    fun mirrorFormula() {
        val r = HandednessTransform.mirrorRect(Rect(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals(0.6f, r.x)
        assertEquals(0.2f, r.y)
        assertEquals(0.3f, r.w)
        assertEquals(0.4f, r.h)
    }

    @Test
    fun mirrorSwapsZonesAndHandedness() {
        val p = TestFixtures.padProfile()
        val m = HandednessTransform.mirror(p)
        assertEquals(Handedness.LEFT, m.handedness)
        assertEquals(GestureZone.LEFT_FREE, m.gestures.cameraDrag.zone)
        // Wyjścia się nie zmieniają.
        assertEquals(p.controls.map { it.id }, m.controls.map { it.id })
        assertEquals((p.controls[0] as StickControl).output, (m.controls[0] as StickControl).output)
    }

    @Test
    fun doubleMirrorIsIdentityForPresets() {
        for (f in TestFixtures.presetFiles()) {
            val p = ProfileCodec.decode(f.readText(Charsets.UTF_8)).getOrThrow()
            val mm = HandednessTransform.mirror(HandednessTransform.mirror(p))
            assertEquals(p, mm, "${f.name}: podwójne lustro nie jest tożsamością")
        }
    }

    @Test
    fun mirroredPresetsStayValid() {
        for (f in TestFixtures.presetFiles()) {
            val p = ProfileCodec.decode(f.readText(Charsets.UTF_8)).getOrThrow()
            val errors = ProfileValidator.validate(HandednessTransform.mirror(p))
            assertTrue(errors.isEmpty(), "${f.name} po lustrze: $errors")
        }
    }

    @Test
    fun toHandednessIsNoOpWhenAlreadyThere() {
        val p = TestFixtures.padProfile()
        assertEquals(p, HandednessTransform.toHandedness(p, Handedness.RIGHT))
    }

    /** Właściwość: dla rect na siatce 1e-4 (jak w edytorze) podwójne lustro = tożsamość, a wynik mieści się w 0..1. */
    @Test
    fun doubleMirrorPropertyOnGrid(): Unit = runBlocking<Unit> {
        checkAll(Arb.int(0, 9999), Arb.int(1, 10000)) { a, b ->
            val w = minOf(a, b).coerceAtLeast(1)
            val x = (maxOf(a, b) - w).coerceAtLeast(0)
            // Ta sama konwersja co w HandednessTransform.snap (double -> float), żeby porównanie było dokładne.
            val rect = Rect((x / 10000.0).toFloat(), 0.1f, (w / 10000.0).toFloat(), 0.2f)
            val once = HandednessTransform.mirrorRect(rect)
            assertTrue(once.x >= 0f && once.x + once.w <= 1f + 1e-4f, "lustro wyszło poza ekran: $rect -> $once")
            assertEquals(rect, HandednessTransform.mirrorRect(once))
        }
    }
}
