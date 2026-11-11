package app.thumbcast.editor.state

import app.thumbcast.model.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGeometryTest {

    private val eps = 1e-4f

    private fun assertOnScreen(r: Rect) {
        assertTrue("x >= 0: $r", r.x >= 0f)
        assertTrue("y >= 0: $r", r.y >= 0f)
        assertTrue("w >= MIN: $r", r.w >= EditorGeometry.MIN_SIZE - eps)
        assertTrue("h >= MIN: $r", r.h >= EditorGeometry.MIN_SIZE - eps)
        assertTrue("x + w <= 1: $r", r.x + r.w <= 1f + eps)
        assertTrue("y + h <= 1: $r", r.y + r.h <= 1f + eps)
    }

    @Test
    fun moveSnapsToOnePercentGrid() {
        val r = EditorGeometry.move(Rect(0.1f, 0.1f, 0.1f, 0.1f), 0.1234f, 0.0567f, snap = true)
        assertEquals(0.22f, r.x, eps)
        assertEquals(0.16f, r.y, eps)
        assertEquals(0.1f, r.w, eps)
    }

    @Test
    fun moveWithoutSnapRoundsToFourDecimals() {
        val r = EditorGeometry.move(Rect(0.1f, 0.1f, 0.1f, 0.1f), 0.123456f, 0f, snap = false)
        assertEquals(0.2235f, r.x, 1e-6f)
    }

    @Test
    fun moveNeverLeavesScreen() {
        val base = Rect(0.5f, 0.5f, 0.2f, 0.3f)
        val far = EditorGeometry.move(base, 5f, 5f, snap = false)
        assertEquals(0.8f, far.x, eps)
        assertEquals(0.7f, far.y, eps)
        val neg = EditorGeometry.move(base, -5f, -5f, snap = true)
        assertEquals(0f, neg.x, eps)
        assertEquals(0f, neg.y, eps)
        assertOnScreen(far)
        assertOnScreen(neg)
    }

    @Test
    fun resizeCornerKeepsTopLeftAndClampsToScreen() {
        val base = Rect(0.7f, 0.6f, 0.1f, 0.1f)
        val r = EditorGeometry.resizeCorner(base, 0.5f, 0.5f, snap = true)
        assertEquals(0.7f, r.x, eps)
        assertEquals(0.6f, r.y, eps)
        assertEquals(0.3f, r.w, eps)
        assertEquals(0.4f, r.h, eps)
        val tiny = EditorGeometry.resizeCorner(base, -1f, -1f, snap = false)
        assertEquals(EditorGeometry.MIN_SIZE, tiny.w, eps)
        assertEquals(EditorGeometry.MIN_SIZE, tiny.h, eps)
    }

    @Test
    fun scaleUniformKeepsCenterAndRatio() {
        val base = Rect(0.4f, 0.4f, 0.08f, 0.2f)
        val r = EditorGeometry.scaleUniform(base, 1.5f, snap = false)
        assertEquals(0.3f, r.h, eps)
        assertEquals(0.12f, r.w, eps)
        assertEquals(base.x + base.w / 2f, r.x + r.w / 2f, eps)
        assertEquals(base.y + base.h / 2f, r.y + r.h / 2f, eps)
    }

    @Test
    fun scaleUniformIsLimitedByScreenAndMinimum() {
        val base = Rect(0.4f, 0.4f, 0.1f, 0.2f)
        val big = EditorGeometry.scaleUniform(base, 100f, snap = true)
        assertEquals(1f, big.h, eps)
        assertEquals(0.5f, big.w, eps)
        assertOnScreen(big)
        val small = EditorGeometry.scaleUniform(base, 0.0001f, snap = false)
        assertEquals(EditorGeometry.MIN_SIZE, small.w, eps)
        assertOnScreen(small)
    }

    @Test
    fun clampHandlesNonFiniteValues() {
        val r = EditorGeometry.clamp(Rect(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, 2f))
        assertOnScreen(r)
    }

    @Test
    fun withHeightSetsSize() {
        val r = EditorGeometry.withHeight(Rect(0.4f, 0.4f, 0.1f, 0.2f), 0.3f, snap = true)
        assertEquals(0.3f, r.h, eps)
        assertEquals(0.15f, r.w, eps)
    }
}
