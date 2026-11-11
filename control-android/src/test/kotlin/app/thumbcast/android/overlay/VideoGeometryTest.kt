package app.thumbcast.android.overlay

import app.thumbcast.engine.RectPx
import org.junit.Assert.assertEquals
import org.junit.Test

/** VideoGeometry: kolejność źródeł prostokąta wideo i rozdzielczość odniesienia. */
class VideoGeometryTest {

    @Test
    fun defaultIsWholeView() {
        val v = VideoGeometry().viewport(2400, 1080)
        assertEquals(2400, v.widthPx)
        assertEquals(1080, v.heightPx)
        assertEquals(RectPx(0, 0, 2400, 1080), v.videoRect)
        assertEquals(2400, v.videoRefWidth)
        assertEquals(1080, v.videoRefHeight)
    }

    @Test
    fun streamResolutionGivesCenteredPillarboxAndRefOfRect() {
        val g = VideoGeometry().withStreamResolution(1920, 1080)
        assertEquals(VideoRect(240, 0, 2160, 1080), g.videoRect(2400, 1080))
        val v = g.viewport(2400, 1080)
        assertEquals(RectPx(240, 0, 2160, 1080), v.videoRect)
        // Bez jawnego odniesienia: rozmiar prostokąta.
        assertEquals(1920, v.videoRefWidth)
        assertEquals(1080, v.videoRefHeight)
    }

    @Test
    fun explicitRectWinsOverStreamResolutionInAnyOrder() {
        val a = VideoGeometry().withStreamResolution(1920, 1080).withVideoRect(VideoRect(10, 20, 1010, 520), 1920, 1080)
        val b = VideoGeometry().withVideoRect(VideoRect(10, 20, 1010, 520), 1920, 1080).withStreamResolution(1280, 720)
        for (g in listOf(a, b)) {
            val v = g.viewport(2400, 1080)
            assertEquals(RectPx(10, 20, 1010, 520), v.videoRect)
            assertEquals(1920, v.videoRefWidth)
            assertEquals(1080, v.videoRefHeight)
        }
    }

    @Test
    fun zeroReferenceFallsBackToRectSize() {
        val v = VideoGeometry().withVideoRect(VideoRect(0, 0, 800, 600), 0, 0).viewport(1000, 600)
        assertEquals(800, v.videoRefWidth)
        assertEquals(600, v.videoRefHeight)
    }

    @Test
    fun degenerateRectKeepsReferenceAtLeastOne() {
        val v = VideoGeometry().withVideoRect(VideoRect(5, 5, 5, 5), 0, 0).viewport(100, 100)
        assertEquals(1, v.videoRefWidth)
        assertEquals(1, v.videoRefHeight)
    }

    @Test
    fun viewportFollowsViewSize() {
        val g = VideoGeometry().withStreamResolution(1920, 1080)
        assertEquals(VideoRect(0, 150, 1600, 1050), g.videoRect(1600, 1200))
        assertEquals(1200, g.viewport(1600, 1200).heightPx)
    }
}
