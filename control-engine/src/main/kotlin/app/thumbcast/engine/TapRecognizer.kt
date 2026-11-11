package app.thumbcast.engine

import app.thumbcast.model.MouseBtn

/** Tap: krótki dotyk bez ruchu -> mousePosition + LMB down/up w punkcie. */
class TapRecognizer(
    private val config: TapConfig,
    private val video: VideoMapper,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:tap"

    fun matches(p: TrackedPointer, upTimeMs: Long, maxMoveSeen: Float): Boolean {
        if (!config.enabled) return false
        if (maxMoveSeen > config.maxMovePx) return false
        return upTimeMs - p.downTimeMs <= config.maxMs
    }

    /** Wywoływane w cyklu UP palca. */
    fun fire(pointerId: Int, x: Float, y: Float) {
        val v = video.toVideo(x, y) ?: return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, true)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, false)))
    }
}
