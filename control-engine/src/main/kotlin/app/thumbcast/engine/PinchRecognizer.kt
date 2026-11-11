package app.thumbcast.engine

import kotlin.math.hypot

/** Szczypanie: zmiana odległości dwóch palców -> mouseWheel (120 = 1 ząbek, pxPerNotch px na ząbek). */
class PinchRecognizer(
    private val config: PinchConfig,
    private val tuning: EngineTuning,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:pinch"

    private var a: Int = -1
    private var b: Int = -1
    private var anchor: Float = 0f

    fun isActive(): Boolean = a != -1

    fun involves(pointerId: Int): Boolean = pointerId == a || pointerId == b

    val enabled: Boolean get() = config.enabled && config.pxPerNotch > 0f

    /** a = pierwszy palec, b = palec, którego DOWN rozpoczął szczypanie. */
    fun begin(first: TrackedPointer, second: TrackedPointer) {
        a = first.id
        b = second.id
        anchor = hypot(second.x - first.x, second.y - first.y)
    }

    /** causePointerId = palec, którego ruch (w bieżącym zdarzeniu) spowodował zmianę. */
    fun move(pa: TrackedPointer, pb: TrackedPointer, causePointerId: Int) {
        if (a == -1) return
        val d = hypot(pb.x - pa.x, pb.y - pa.y)
        val diff = d - anchor
        var notches = (diff / config.pxPerNotch).toInt()
        if (notches == 0) return
        anchor += notches.toFloat() * config.pxPerNotch
        val cap = tuning.maxNotchesPerEvent
        if (notches > cap) notches = cap
        if (notches < -cap) notches = -cap
        // Rozsuwanie palców = przybliżenie = kółko w górę (dodatnie).
        emitter.emit(Intent(causePointerId, bindingId, BindingKind.HOLD, Wheel(notches * 120)))
    }

    val firstId: Int get() = a
    val secondId: Int get() = b

    fun end() {
        a = -1
        b = -1
        anchor = 0f
    }
}
