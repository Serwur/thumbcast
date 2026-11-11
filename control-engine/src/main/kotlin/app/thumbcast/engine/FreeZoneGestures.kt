package app.thumbcast.engine

import app.thumbcast.model.FreeZoneMode

/**
 * Właściciel wolnej strefy (palce, które przy DOWN nie trafiły w kontrolkę). Jedna implementacja na profil
 * (`gestures.freeZone`), więc jedno przeciągnięcie nigdy nie daje naraz kamery i ruchu myszy:
 * - [GestureArbiter]: tabela Pad+ (kamera, tap i long-press w punkcie, pinch, edge swipe),
 * - [TouchpadGestures]: tabela touchpada (kursor, kliki w miejscu kursora, przewijanie dwoma palcami, edge swipe).
 *
 * Implementacje produkują wyłącznie intencje przez [IntentEmitter] (ActionPolicy) i działają tylko w wywołaniach
 * z cyklu dotyku albo ticka aktywnego palca. Tick ([onMove] z `isTick = true`) może co najwyżej rozpoznać gest
 * trzymanego palca (long-press, ONE_SHOT_TIMED) albo wygasić wychylenie kamery.
 */
interface FreeZoneGestures {
    fun onDown(p: TrackedPointer, tracker: PointerTracker)

    /** Prawdziwe zdarzenie dotyku (MOVE) albo tick (isTick): pozycje już zaktualizowane w trackerze. */
    fun onMove(p: TrackedPointer, tracker: PointerTracker, t: Long, isTick: Boolean)

    /** Koniec palca. fire = false przy przerwaniu (podwójny DOWN tego samego id): zwolnij, ale nie rozpoznawaj tapu. */
    fun onUp(p: TrackedPointer, t: Long, fire: Boolean)

    /** CANCEL, releaseAll: zapomnij stan bez emitowania (zerowy stan wysyła polityka). */
    fun reset()

    companion object {
        fun create(config: EngineConfig, viewport: Viewport, screen: PxRect, emitter: IntentEmitter): FreeZoneGestures =
            when (config.freeZone) {
                FreeZoneMode.CAMERA -> GestureArbiter(config, viewport, screen, emitter)
                FreeZoneMode.TOUCHPAD -> TouchpadGestures(config, screen, emitter)
            }
    }
}
