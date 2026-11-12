package app.thumbcast.engine

import app.thumbcast.model.MouseBtn

/**
 * Tap: krótki dotyk bez ruchu -> mousePosition + LMB down/up w punkcie. Tap dwoma palcami (12.11.2026, rozpoznaje
 * [GestureArbiter]) -> mousePosition + RMB down/up w punkcie pierwszego palca ([fireRight]).
 */
class TapRecognizer(
    private val config: TapConfig,
    private val video: VideoMapper,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:tap"
    val rightBindingId: String = "gesture:tap2"

    fun matches(p: TrackedPointer, upTimeMs: Long, maxMoveSeen: Float): Boolean {
        if (!config.enabled) return false
        if (maxMoveSeen > config.maxMovePx) return false
        return upTimeMs - p.downTimeMs <= config.maxMs
    }

    /** Wywoływane w cyklu UP palca. */
    fun fire(pointerId: Int, x: Float, y: Float) {
        click(pointerId, bindingId, MouseBtn.LEFT, x, y)
    }

    /** Tap dwoma palcami: wywoływane w cyklu UP ostatniego palca pary, (x, y) = punkt DOWN pierwszego palca. */
    fun fireRight(pointerId: Int, x: Float, y: Float) {
        click(pointerId, rightBindingId, MouseBtn.RIGHT, x, y)
    }

    private fun click(pointerId: Int, bid: String, button: MouseBtn, x: Float, y: Float) {
        val v = video.toVideo(x, y) ?: return
        emitter.emit(Intent(pointerId, bid, BindingKind.ONE_SHOT, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bid, BindingKind.ONE_SHOT, MouseButtonSet(button, true)))
        emitter.emit(Intent(pointerId, bid, BindingKind.ONE_SHOT, MouseButtonSet(button, false)))
    }
}
