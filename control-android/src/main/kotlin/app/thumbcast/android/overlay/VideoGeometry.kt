package app.thumbcast.android.overlay

import app.thumbcast.engine.Viewport

/**
 * Geometria wideo nad overlayem (czyste JVM, bez stanu widoku): skąd wziąć prostokąt wideo
 * i rozdzielczość odniesienia dla `Viewport` silnika.
 *
 * Kolejność źródeł prostokąta:
 * 1. jawny [explicitRect] (Game podaje prostokąt `StreamView` w układzie overlaya),
 * 2. rozdzielczość strumienia: wycentrowany letterbox/pillarbox ([ViewportMath.fitCentered]),
 * 3. cały widok.
 *
 * Rozdzielczość odniesienia (`sendMousePosition`): [refWidth]/[refHeight] > 0, inaczej rozmiar prostokąta
 * (tak robi upstream dla dotyku absolutnego). Obiekt niezmienny; zmiany przez `with*`.
 */
data class VideoGeometry(
    val explicitRect: VideoRect? = null,
    val refWidth: Int = 0,
    val refHeight: Int = 0,
    val streamWidth: Int = 0,
    val streamHeight: Int = 0,
) {
    fun withVideoRect(rect: VideoRect, refWidth: Int, refHeight: Int): VideoGeometry =
        copy(explicitRect = rect, refWidth = refWidth, refHeight = refHeight)

    fun withStreamResolution(width: Int, height: Int): VideoGeometry =
        copy(streamWidth = width, streamHeight = height)

    /** Prostokąt wideo w px widoku o rozmiarze [viewWidth] x [viewHeight]. */
    fun videoRect(viewWidth: Int, viewHeight: Int): VideoRect {
        val explicit: VideoRect? = explicitRect
        if (explicit != null) return explicit
        if (streamWidth > 0 && streamHeight > 0) {
            return ViewportMath.fitCentered(viewWidth, viewHeight, streamWidth, streamHeight)
        }
        return VideoRect(0, 0, viewWidth, viewHeight)
    }

    /** `Viewport` silnika dla widoku [viewWidth] x [viewHeight]. */
    fun viewport(viewWidth: Int, viewHeight: Int): Viewport {
        val video: VideoRect = videoRect(viewWidth, viewHeight)
        val refW: Int = if (refWidth > 0) refWidth else maxOf(video.width, 1)
        val refH: Int = if (refHeight > 0) refHeight else maxOf(video.height, 1)
        return EngineBridge.viewport(viewWidth, viewHeight, video, refW, refH)
    }
}
