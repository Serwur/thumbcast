package app.thumbcast.engine

/**
 * Zamiana przesunięć ułamkowych na całkowite kroki bez gubienia reszty (ruch myszy, kółko touchpada).
 * Reszta po części całkowitej (obcięcie w stronę zera) przechodzi do następnego wywołania [add].
 */
class RelativeAccumulator {
    private var remX: Float = 0f
    private var remY: Float = 0f

    /** Całkowity krok X z ostatniego [add]. */
    var stepX: Int = 0
        private set

    /** Całkowity krok Y z ostatniego [add]. */
    var stepY: Int = 0
        private set

    /** Dodaje przesunięcie (już przeskalowane). true, gdy powstał niezerowy krok całkowity ([stepX], [stepY]). */
    fun add(fx: Float, fy: Float): Boolean {
        val tx = fx + remX
        val ty = fy + remY
        stepX = tx.toInt()
        stepY = ty.toInt()
        remX = tx - stepX.toFloat()
        remY = ty - stepY.toFloat()
        return stepX != 0 || stepY != 0
    }

    fun reset() {
        remX = 0f
        remY = 0f
        stepX = 0
        stepY = 0
    }
}
