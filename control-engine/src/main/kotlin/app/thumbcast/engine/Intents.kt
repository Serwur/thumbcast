package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger

// Wewnętrzny język silnika: rozpoznawacze produkują Intent (z jednym efektem wyjścia),
// ActionPolicy go sprawdza i dopiero wtedy przekazuje do OutputMapper.

/** Jeden elementarny efekt na wyjściu (stan lub zdarzenie chwilowe). */
sealed interface OutputEffect

data class PadButtonSet(val button: PadButton, val pressed: Boolean) : OutputEffect

/** value 0..255. */
data class TriggerSet(val trigger: PadTrigger, val value: Int) : OutputEffect

/** x, y w zakresie -1..1; y dodatnie = gałka w górę (konwencja XInput). */
data class StickSet(val stick: PadStick, val x: Float, val y: Float) : OutputEffect

data class KeySet(val code: String, val pressed: Boolean) : OutputEffect

data class MouseButtonSet(val button: MouseBtn, val pressed: Boolean) : OutputEffect

data class MouseMove(val dx: Int, val dy: Int) : OutputEffect

data class MouseAt(val x: Int, val y: Int, val refWidth: Int, val refHeight: Int) : OutputEffect

/** Kółko pionowe: 120 = 1 ząbek, dodatnie = w górę (od użytkownika). */
data class Wheel(val delta: Int) : OutputEffect

/** Kółko poziome: 120 = 1 ząbek, dodatnie = w prawo. */
data class HWheel(val delta: Int) : OutputEffect

data class AppAction(val action: String) : OutputEffect

/**
 * Charakter wiązania, z którego pochodzi intencja.
 * - HOLD: stan wyjścia = stan palca (przyciski HOLD, gałki, kamera, pinch),
 * - LATCH: stan należy do kontrolki, nie do palca (przeżywa UP); przełączany tylko przez DOWN,
 * - ONE_SHOT: jednorazowe wejście w cyklu życia palca (tap, edge swipe, przycisk App),
 * - ONE_SHOT_TIMED: jednorazowe wejście rozpoznane po czasie trzymania (long-press); jedyny rodzaj
 *   wciśnięcia dozwolony w cyklu TIME (onTime powiązany z aktywnym palcem).
 */
enum class BindingKind { HOLD, LATCH, ONE_SHOT, ONE_SHOT_TIMED }

/** Intencja: efekt + palec, który go powoduje + logiczne wejście (wiązanie), do którego należy. */
data class Intent(val pointerId: Int, val bindingId: String, val kind: BindingKind, val effect: OutputEffect)

/** Klucz trwałego stanu wyjścia (coś, co może „wisieć” wciśnięte). */
sealed interface HeldKey
data class HeldPad(val button: PadButton) : HeldKey
data class HeldTrigger(val trigger: PadTrigger) : HeldKey
data class HeldStick(val stick: PadStick) : HeldKey
data class HeldKeyCode(val code: String) : HeldKey
data class HeldMouse(val button: MouseBtn) : HeldKey

object Effects {
    /** Klucz trwałego stanu, którego dotyczy efekt; null dla efektów chwilowych (ruch, pozycja, kółko, app). */
    fun heldKeyOf(e: OutputEffect): HeldKey? = when (e) {
        is PadButtonSet -> HeldPad(e.button)
        is TriggerSet -> HeldTrigger(e.trigger)
        is StickSet -> HeldStick(e.stick)
        is KeySet -> HeldKeyCode(e.code)
        is MouseButtonSet -> HeldMouse(e.button)
        else -> null
    }

    /** Czy efekt ustawia trwały stan na „aktywny” (wciśnięty / wychylony). */
    fun isHolding(e: OutputEffect): Boolean = when (e) {
        is PadButtonSet -> e.pressed
        is TriggerSet -> e.value > 0
        is StickSet -> e.x != 0f || e.y != 0f
        is KeySet -> e.pressed
        is MouseButtonSet -> e.pressed
        else -> false
    }

    /** Czy efekt jest „aktywnym” wejściem (wymaga przyczyny i własności palca). Zwolnienia i zera nie są. */
    fun isPress(e: OutputEffect): Boolean = when (e) {
        is MouseMove -> e.dx != 0 || e.dy != 0
        is MouseAt -> true
        is Wheel -> e.delta != 0
        is HWheel -> e.delta != 0
        is AppAction -> true
        else -> isHolding(e)
    }

    /** Efekt zwalniający dany trwały stan. */
    fun releaseOf(k: HeldKey): OutputEffect = when (k) {
        is HeldPad -> PadButtonSet(k.button, false)
        is HeldTrigger -> TriggerSet(k.trigger, 0)
        is HeldStick -> StickSet(k.stick, 0f, 0f)
        is HeldKeyCode -> KeySet(k.code, false)
        is HeldMouse -> MouseButtonSet(k.button, false)
    }
}
