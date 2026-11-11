package app.thumbcast.engine

import app.thumbcast.model.CurveType
import app.thumbcast.model.FreeZoneMode
import app.thumbcast.model.GestureZone
import app.thumbcast.model.Output
import app.thumbcast.model.PadStick
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickKeys

// Wewnętrzna, jawnie typowana konfiguracja silnika. Powstaje z Profile w ProfileAdapter
// (jedyne miejsce, które czyta pola modelu nieopisane w kontrakcie). Testy budują ją bezpośrednio.

data class CurveSpec(val type: CurveType, val exponent: Float)

data class CameraConfig(
    /** Strefa gestu (wolna część prawej/lewej połowy); przy leworęczności zamieniana. */
    val zone: GestureZone,
    /** true: RMB-look (RMB + ruch względny myszy); false: prędkość palca -> prawa gałka pada. */
    val mouseLook: Boolean,
    val sensitivity: Float,
    val curve: CurveSpec,
    /** Martwa strefa prędkości: przesunięcie w px na ramkę referencyjną (EngineTuning.referenceFrameMs). */
    val deadzonePx: Float,
    val invertY: Boolean,
    /** Maksymalne wychylenie prawej gałki 0..1. */
    val maxDeflection: Float,
    /** Czas liniowego wygaszania wychylenia do zera po zatrzymaniu palca. */
    val decayMs: Long
)

data class TapConfig(val enabled: Boolean, val maxMovePx: Float, val maxMs: Long)

data class LongPressConfig(val enabled: Boolean, val minMs: Long)

data class PinchConfig(val enabled: Boolean, val pxPerNotch: Float)

data class EdgeSwipeConfig(val enabled: Boolean)

sealed interface ControlSpec {
    val id: String
    val rect: Rect
}

data class StickSpec(
    override val id: String,
    override val rect: Rect,
    val floating: Boolean,
    val deadzone: Float,
    val outerDeadzone: Float,
    val curve: CurveSpec,
    val padStick: PadStick,
    /** Klawisze WASD; używane tylko gdy tryb profilu = KEYBOARD_MOUSE i pole nie jest null. */
    val kbmKeys: StickKeys?,
    /** Próg (0..1, po martwej strefie) od którego joystick w KBM wciska klawisze kierunków. */
    val kbmThreshold: Float,
    /**
     * Histereza nasycenia: po dojściu do 100% wychylenie zostaje 100% (kierunek za palcem),
     * dopóki palec nie wróci w martwą strefę albo nie zostanie podniesiony. Tylko z palcem, bez timerów.
     */
    val sprintLock: Boolean = false
) : ControlSpec

data class ButtonSpec(
    override val id: String,
    override val rect: Rect,
    val output: Output,
    val latch: Boolean
) : ControlSpec

/** Stałe strojenia silnika (nie są częścią profilu). */
data class EngineTuning(
    val maxPointers: Int = 10,
    /** Strefa aktywacji większa od grafiki: margines = max(hitSlopMinPx, hitSlopFraction * min(w, h)). */
    val hitSlopFraction: Float = 0.20f,
    val hitSlopMinPx: Float = 8f,
    /** Promień pływającego joysticka jako ułamek krótszego boku ekranu. */
    val floatingStickRadiusFraction: Float = 0.15f,
    /** Prędkość palca (px/ms) dająca pełne wychylenie kamery przy czułości 1.0. */
    val cameraFullSpeedPxPerMs: Float = 1.5f,
    /** Liczba próbek EMA prędkości kamery (alfa = 2 / (N + 1)). */
    val cameraEmaSamples: Int = 2,
    val referenceFrameMs: Float = 16.67f,
    /** Pasek górnej krawędzi dla edge swipe (ułamek wysokości, min. edgeBandMinPx). */
    val edgeBandFraction: Float = 0.05f,
    val edgeBandMinPx: Float = 16f,
    /** Minimalna droga w dół dla edge swipe (ułamek wysokości, min. edgeSwipeMinPx). */
    val edgeSwipeMinFraction: Float = 0.08f,
    val edgeSwipeMinPx: Float = 24f,
    /** Mnożnik px palca → jednostki myszy w KBM (dodatkowo do sensitivity). */
    val kbmMouseScale: Float = 1.0f,
    /** Bezpiecznik: maksymalna liczba ząbków kółka z jednego zdarzenia. */
    val maxNotchesPerEvent: Int = 10,
    /** Touchpad: tap dwoma palcami ma od pierwszego DOWN do ostatniego UP najwyżej tap.maxMs + tyle ms. */
    val twoFingerTapExtraMs: Long = 100L
)

/** Ustawienia touchpada (`gestures.touchpad`), używane przy [EngineConfig.freeZone] = TOUCHPAD. */
data class TouchpadSpec(
    /** Piksele myszy na piksel palca. */
    val sensitivity: Float = 1.0f,
    /** Droga środka dwóch palców (px) na 1 ząbek kółka (120 jednostek). */
    val scrollPxPerNotch: Float = 24f
)

data class EngineConfig(
    val mode: ProfileMode,
    val leftHanded: Boolean,
    val slideToPress: Boolean,
    val camera: CameraConfig,
    val tap: TapConfig,
    val longPress: LongPressConfig,
    val pinch: PinchConfig,
    val edgeSwipe: EdgeSwipeConfig,
    /** Kolejność = z-order: późniejsza kontrolka leży wyżej. */
    val controls: List<ControlSpec>,
    val tuning: EngineTuning = EngineTuning(),
    /** Właściciel wolnej strefy: kamera (Pad+) albo touchpad. Jeden na profil. */
    val freeZone: FreeZoneMode = FreeZoneMode.CAMERA,
    val touchpad: TouchpadSpec = TouchpadSpec()
)
