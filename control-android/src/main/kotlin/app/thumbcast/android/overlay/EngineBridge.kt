package app.thumbcast.android.overlay

import app.thumbcast.engine.Clock
import app.thumbcast.engine.RectPx
import app.thumbcast.engine.Viewport

/**
 * JEDYNE miejsce w :control-android, które konstruuje typy `Viewport`, `RectPx` i `Clock` z :control-engine.
 *
 * Kontrakt podaje `Viewport(widthPx, heightPx, videoRect: RectPx, videoRefWidth, videoRefHeight)`,
 * ale nie definiuje `RectPx`. ZAŁOŻENIE (CONTRACT-NOTES.md, sekcja ANDROID):
 * `data class RectPx(val left: Int, val top: Int, val right: Int, val bottom: Int)` w pakiecie
 * `app.thumbcast.engine`, prawa i dolna krawędź wyłączne (jak android.graphics.Rect).
 * Jeśli ENGINE wybierze inny kształt, wystarczy poprawić [toRectPx].
 */
object EngineBridge {

    fun toRectPx(r: VideoRect): RectPx = RectPx(r.left, r.top, r.right, r.bottom)

    fun viewport(widthPx: Int, heightPx: Int, video: VideoRect, refWidth: Int, refHeight: Int): Viewport =
        Viewport(widthPx, heightPx, toRectPx(video), refWidth, refHeight)

    /**
     * Zegar silnika w tej samej bazie co `MotionEvent.getEventTime()` (`SystemClock.uptimeMillis()`),
     * żeby czasy `TouchEvent.timeMs` i `Clock.nowMs()` były porównywalne.
     */
    val uptimeClock: Clock = object : Clock {
        override fun nowMs(): Long = android.os.SystemClock.uptimeMillis()
    }
}

/** Prostokąt wideo w px widoku (całkowite, prawa/dolna krawędź wyłączna). */
data class VideoRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

/**
 * Liczenie prostokąta wideo (letterbox / pillarbox) w czystym JVM.
 *
 * Używane, gdy `Game` nie poda prostokąta jawnie przez `setVideoRect(...)`: wtedy zakładamy, że
 * `StreamView` jest wycentrowany w rodzicu i zachowuje proporcje strumienia (upstream:
 * `setDesiredAspectRatio`, plan 2.3 pkt 5) [?].
 */
object ViewportMath {

    fun fitCentered(viewWidth: Int, viewHeight: Int, streamWidth: Int, streamHeight: Int): VideoRect {
        if (viewWidth <= 0 || viewHeight <= 0 || streamWidth <= 0 || streamHeight <= 0) {
            return VideoRect(0, 0, maxOf(viewWidth, 0), maxOf(viewHeight, 0))
        }
        val viewAspect: Double = viewWidth.toDouble() / viewHeight.toDouble()
        val streamAspect: Double = streamWidth.toDouble() / streamHeight.toDouble()
        return if (viewAspect > streamAspect) {
            // Widok szerszy: pasy po bokach.
            val w: Int = Math.round(viewHeight * streamAspect).toInt()
            val left: Int = (viewWidth - w) / 2
            VideoRect(left, 0, left + w, viewHeight)
        } else {
            // Widok wyższy (lub równy): pasy u góry i u dołu.
            val h: Int = Math.round(viewWidth / streamAspect).toInt()
            val top: Int = (viewHeight - h) / 2
            VideoRect(0, top, viewWidth, top + h)
        }
    }

    /**
     * Przesunięcie prostokąta podanego w układzie innego widoku (np. `StreamView` na ekranie)
     * do układu overlaya: odejmujemy położenie overlaya na ekranie.
     */
    fun toOverlayCoords(rectOnScreen: VideoRect, overlayScreenX: Int, overlayScreenY: Int): VideoRect =
        VideoRect(
            rectOnScreen.left - overlayScreenX,
            rectOnScreen.top - overlayScreenY,
            rectOnScreen.right - overlayScreenX,
            rectOnScreen.bottom - overlayScreenY,
        )
}
