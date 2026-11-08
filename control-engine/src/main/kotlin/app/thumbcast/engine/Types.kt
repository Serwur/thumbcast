package app.thumbcast.engine

import app.thumbcast.model.MouseBtn

// Typy z kontraktu (CONTRACT.md, moduł :control-engine). Nie zmieniać sygnatur bez wpisu w CONTRACT-NOTES.md.

/** Rodzaj zdarzenia dotyku. DOWN i UP dotyczą pojedynczego wskaźnika (actionPointerId). CANCEL anuluje cały gest. */
enum class TouchAction { DOWN, MOVE, UP, CANCEL }

/** Wskaźnik w pikselach układu overlaya. */
data class TouchPointer(val id: Int, val x: Float, val y: Float)

/** Zdarzenie dotyku niezależne od platformy (overlay Androida tłumaczy na nie MotionEvent). */
data class TouchEvent(
    val action: TouchAction,
    val actionPointerId: Int,
    val pointers: List<TouchPointer>,
    val timeMs: Long
)

/** Wyjście do hosta. Implementacje: FakeInputSink (testy) i adaptery w :control-android. */
interface InputSink {
    fun padState(
        buttons: Int, leftStickX: Short, leftStickY: Short,
        rightStickX: Short, rightStickY: Short, leftTrigger: Byte, rightTrigger: Byte
    )
    fun key(code: String, down: Boolean)
    fun mouseMoveRelative(dx: Int, dy: Int)
    fun mouseButton(button: MouseBtn, down: Boolean)
    /** Pozycja w układzie wideo (refWidth x refHeight). */
    fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int)
    /** 120 = 1 ząbek kółka. */
    fun mouseWheel(highResDelta: Int)
    fun app(action: String)
}

/** Źródło czasu. Musi mieć tę samą bazę co TouchEvent.timeMs (na Androidzie: SystemClock.uptimeMillis). */
interface Clock {
    fun nowMs(): Long
}

/**
 * Prostokąt w pikselach (lewy-górny włącznie, prawy-dolny wyłącznie), jak android.graphics.Rect.
 * Typ nie jest rozpisany w kontrakcie; założenie ENGINE opisane w CONTRACT-NOTES.md.
 */
data class RectPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left.toFloat() && x < right.toFloat() && y >= top.toFloat() && y < bottom.toFloat()
}

/**
 * Geometria ekranu.
 * - videoRect: prostokąt wideo po letterboxie (w px overlaya),
 * - videoRefWidth/Height: rozdzielczość strumienia (układ współrzędnych mousePosition),
 * - safeArea: opcjonalny bezpieczny obszar (WindowInsets: wycięcie aparatu, gesty systemowe).
 *   Rozszerzenie ENGINE względem kontraktu, z wartością domyślną, więc wywołania z 5 argumentami
 *   nadal się kompilują. Gdy null, rect kontrolek liczymy względem całego overlaya.
 */
data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val videoRect: RectPx,
    val videoRefWidth: Int,
    val videoRefHeight: Int,
    val safeArea: RectPx? = null
)
