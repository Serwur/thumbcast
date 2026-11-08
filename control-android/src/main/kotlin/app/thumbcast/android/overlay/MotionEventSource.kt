package app.thumbcast.android.overlay

import android.view.MotionEvent

/**
 * Implementacja [MotionSource] na prawdziwym `MotionEvent`. Obiekt wielokrotnego użytku:
 * overlay podmienia [event] przed każdym tłumaczeniem, żeby nie alokować w ścieżce dotyku.
 */
class MotionEventSource : MotionSource {

    var event: MotionEvent? = null

    private fun ev(): MotionEvent {
        val e: MotionEvent? = event
        if (e == null) {
            throw IllegalStateException("MotionEventSource bez zdarzenia")
        }
        return e
    }

    override val actionMasked: Int
        get() = ev().actionMasked

    override val actionIndex: Int
        get() = ev().actionIndex

    override val pointerCount: Int
        get() = ev().pointerCount

    override val eventTimeMs: Long
        get() = ev().eventTime

    override fun pointerId(index: Int): Int = ev().getPointerId(index)

    override fun x(index: Int): Float = ev().getX(index)

    override fun y(index: Int): Float = ev().getY(index)

    override fun toolType(index: Int): Int = ev().getToolType(index)
}
