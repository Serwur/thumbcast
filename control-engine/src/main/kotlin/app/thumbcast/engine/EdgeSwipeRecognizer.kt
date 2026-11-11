package app.thumbcast.engine

import kotlin.math.abs

/** Przeciągnięcie od górnej krawędzi w dół -> app("menu"). Jedno wejście na palec. */
class EdgeSwipeRecognizer(
    private val config: EdgeSwipeConfig,
    private val tuning: EngineTuning,
    private val screen: PxRect,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "gesture:edge"

    fun isCandidate(y: Float): Boolean {
        if (!config.enabled) return false
        val band = maxOf(tuning.edgeBandMinPx, tuning.edgeBandFraction * screen.height)
        return y - screen.top <= band
    }

    /** Czy ruch (od punktu DOWN) jest jeszcze zgodny z przeciąganiem w dół. */
    fun isDownward(dx: Float, dy: Float): Boolean = dy > 0f && dy >= abs(dx)

    fun reached(dy: Float): Boolean {
        val min = maxOf(tuning.edgeSwipeMinPx, tuning.edgeSwipeMinFraction * screen.height)
        return dy >= min
    }

    fun fire(pointerId: Int) {
        emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, AppAction("menu")))
    }
}
