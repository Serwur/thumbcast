package app.thumbcast.engine

import app.thumbcast.model.MouseBtn

/**
 * Long-press: palec trzymany bez ruchu co najmniej minMs -> mousePosition + RMB w punkcie.
 * Rozpoznanie zachodzi WYŁĄCZNIE w cyklu zdarzenia (MOVE albo tick przekazany jako syntetyczny MOVE aktywnego palca)
 * lub przy UP. RMB jest trzymany do UP palca (stan = palec). Daje jedno wejście ZAMIAST tapu, nigdy oba.
 */
class LongPressRecognizer(
    private val config: LongPressConfig,
    private val tapMaxMovePx: Float,
    private val video: VideoMapper,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:longpress"

    fun ready(p: TrackedPointer, nowMs: Long, maxMoveSeen: Float): Boolean {
        if (!config.enabled) return false
        if (maxMoveSeen > tapMaxMovePx) return false
        return nowMs - p.downTimeMs >= config.minMs
    }

    /** RMB w dół (palec nadal dotyka). false, gdy punkt jest poza obrazem. */
    fun press(pointerId: Int, x: Float, y: Float): Boolean {
        val v = video.toVideo(x, y) ?: return false
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseAt(v[0], v[1], video.refWidth, video.refHeight)))
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, true)))
        return true
    }

    fun release(pointerId: Int) {
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, false)))
    }
}
