package app.thumbcast.android.overlay

/**
 * Prostokąt w px (float), niezależny od `android.graphics.RectF`, żeby logika układu była
 * testowalna na czystym JVM.
 */
data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    val centerX: Float
        get() = (left + right) * 0.5f

    val centerY: Float
        get() = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}
