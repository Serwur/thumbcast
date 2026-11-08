package app.thumbcast.engine

import app.thumbcast.model.Output

/**
 * Przycisk (także modyfikator i przycisk systemowy).
 * - HOLD: wciśnięty dokładnie tak długo, jak palec (każdy palec jest osobnym właścicielem stanu),
 * - LATCH: DOWN przełącza stan, UP nic nie robi (1 dotyk = 1 zmiana stanu),
 * - wyjścia jednorazowe (App, MouseWheel): jedno zdarzenie przy DOWN.
 * Wyjścia Stick i MouseLook na przycisku nie mają sensu i są ignorowane (walidator modelu powinien je odrzucić).
 */
class ButtonRecognizer(
    val spec: ButtonSpec,
    private val emitter: IntentEmitter
) {
    val bindingId: String = "control:" + spec.id

    private var latched: Boolean = false
    private val pressedBy: LinkedHashSet<Int> = LinkedHashSet()

    private val isLatch: Boolean = spec.latch && heldEffect(true) != null

    /** Czy przycisk bierze udział w slide-to-press (tylko HOLD z trwałym stanem). */
    val slideable: Boolean = !isLatch && heldEffect(true) != null

    val isLatched: Boolean get() = latched

    fun onDown(pointerId: Int) {
        val press = heldEffect(true)
        if (press == null) {
            val shot = oneShotEffect() ?: return
            emitter.emit(Intent(pointerId, bindingId, BindingKind.ONE_SHOT, shot))
            return
        }
        if (isLatch) {
            val release = heldEffect(false) ?: return
            if (latched) {
                latched = false
                emitter.emit(Intent(pointerId, bindingId, BindingKind.LATCH, release))
            } else {
                latched = true
                emitter.emit(Intent(pointerId, bindingId, BindingKind.LATCH, press))
            }
            return
        }
        pressedBy.add(pointerId)
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, press))
    }

    fun onUp(pointerId: Int) {
        if (isLatch) return
        if (!pressedBy.remove(pointerId)) return
        val release = heldEffect(false) ?: return
        emitter.emit(Intent(pointerId, bindingId, BindingKind.HOLD, release))
    }

    fun reset() {
        latched = false
        pressedBy.clear()
    }

    /** Przeniesienie stanu LATCH po przebudowie (setViewport); stan wyjścia trzyma ActionPolicy. */
    fun restoreLatched(value: Boolean) {
        if (isLatch) latched = value
    }

    private fun heldEffect(pressed: Boolean): OutputEffect? {
        val o = spec.output
        return when (o) {
            is Output.Pad -> PadButtonSet(o.button, pressed)
            is Output.Trigger -> TriggerSet(o.trigger, if (pressed) 255 else 0)
            is Output.Key -> KeySet(o.code, pressed)
            is Output.Mouse -> MouseButtonSet(o.button, pressed)
            else -> null
        }
    }

    private fun oneShotEffect(): OutputEffect? {
        val o = spec.output
        if (o is Output.App) return AppAction(o.action)
        if (o == Output.MouseWheel) return Wheel(120)
        return null
    }

}
