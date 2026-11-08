package app.thumbcast.android.overlay

/**
 * Abstrakcja nad `android.view.MotionEvent`, żeby tłumaczenie na `TouchEvent` dało się testować
 * na czystym JVM. W produkcji implementuje ją [MotionEventSource] (cienki wrapper na MotionEvent),
 * w testach prosta klasa z listą wskaźników.
 */
interface MotionSource {
    /** Odpowiednik `MotionEvent.getActionMasked()`; wartości jak w [MotionActions]. */
    val actionMasked: Int

    /** Odpowiednik `MotionEvent.getActionIndex()`. */
    val actionIndex: Int

    val pointerCount: Int

    /** Czas zdarzenia w ms w bazie `SystemClock.uptimeMillis()` (`MotionEvent.getEventTime()`). */
    val eventTimeMs: Long

    fun pointerId(index: Int): Int

    /** Współrzędne w px w układzie overlaya (`MotionEvent.getX(index)`). */
    fun x(index: Int): Float

    fun y(index: Int): Float

    /** Odpowiednik `MotionEvent.getToolType(index)`; wartości jak w [MotionActions] (`TOOL_TYPE_*`). */
    fun toolType(index: Int): Int
}

/**
 * Wartości `MotionEvent.ACTION_*` skopiowane jako stałe, żeby [MotionTranslator] nie zależał
 * od android.jar. Wartości są stabilne od API 1/5 (dokumentacja MotionEvent).
 */
object MotionActions {
    const val ACTION_DOWN: Int = 0
    const val ACTION_UP: Int = 1
    const val ACTION_MOVE: Int = 2
    const val ACTION_CANCEL: Int = 3
    const val ACTION_OUTSIDE: Int = 4
    const val ACTION_POINTER_DOWN: Int = 5
    const val ACTION_POINTER_UP: Int = 6

    const val TOOL_TYPE_UNKNOWN: Int = 0
    const val TOOL_TYPE_FINGER: Int = 1
    const val TOOL_TYPE_STYLUS: Int = 2
    const val TOOL_TYPE_MOUSE: Int = 3
    const val TOOL_TYPE_ERASER: Int = 4

    /** Overlay obsługuje tylko palce; UNKNOWN traktujemy jak palec (niektóre ekrany go zgłaszają). */
    fun isFingerLike(toolType: Int): Boolean = toolType == TOOL_TYPE_FINGER || toolType == TOOL_TYPE_UNKNOWN
}
