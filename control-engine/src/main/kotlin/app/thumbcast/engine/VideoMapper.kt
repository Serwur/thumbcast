package app.thumbcast.engine

/** Przeliczenie px overlaya -> układ wideo (mousePosition). */
class VideoMapper(private val viewport: Viewport) {
    /** [x, y] w układzie videoRefWidth x videoRefHeight albo null, gdy punkt jest poza obrazem (letterbox). */
    fun toVideo(x: Float, y: Float): IntArray? {
        val r = viewport.videoRect
        if (r.width <= 0 || r.height <= 0 || viewport.videoRefWidth <= 0 || viewport.videoRefHeight <= 0) return null
        if (!r.contains(x, y)) return null
        val vx = ((x - r.left.toFloat()) * viewport.videoRefWidth.toFloat() / r.width.toFloat()).toInt()
        val vy = ((y - r.top.toFloat()) * viewport.videoRefHeight.toFloat() / r.height.toFloat()).toInt()
        return intArrayOf(vx.coerceIn(0, viewport.videoRefWidth - 1), vy.coerceIn(0, viewport.videoRefHeight - 1))
    }

    val refWidth: Int get() = viewport.videoRefWidth
    val refHeight: Int get() = viewport.videoRefHeight
}
