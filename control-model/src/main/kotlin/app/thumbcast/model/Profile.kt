package app.thumbcast.model

import kotlinx.serialization.Serializable

/** Prostokąt znormalizowany 0..1 względem bezpiecznego obszaru ekranu. */
@Serializable
data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

/** Krzywa odpowiedzi. LINEAR: exponent musi być 1. POWER: exponent 1.0..3.0. */
@Serializable
data class Curve(val type: CurveType, val exponent: Float = 1f)

/** Klawisze kierunków joysticka w trybie KEYBOARD_MOUSE (kody z KeyCodes). */
@Serializable
data class StickKeys(val up: String, val down: String, val left: String, val right: String)

/** Układ odniesienia, w którym projektowano profil (informacyjnie, do skalowania z kotwicami). */
@Serializable
data class Reference(val aspect: String = "20:9", val unit: String = "dp-normalized")

@Serializable
data class Haptics(
    val enabled: Boolean = true,
    val strength: Float = 0.6f,
    val onPress: HapticEffect = HapticEffect.TICK,
    val onRelease: HapticEffect = HapticEffect.NONE,
)

@Serializable
data class GlobalSettings(
    val opacity: Float = 0.55f,
    val haptics: Haptics = Haptics(),
    val slideToPress: Boolean = false,
    val autoHideOnGamepad: Boolean = true,
    /** Nic nie włącza: od 11.11.2026 walidator odrzuca każdy `chord` (FAIR_PLAY.md). Pole zostaje bez migracji. */
    val allowChords: Boolean = false,
)

@Serializable
data class CameraDrag(
    val zone: GestureZone = GestureZone.RIGHT_FREE,
    val output: CameraOutput = CameraOutput.PAD_RIGHT_STICK,
    val sensitivity: Float = 1.2f,
    val curve: Curve = Curve(CurveType.POWER, 1.6f),
    val deadzonePx: Float = 4f,
    val invertY: Boolean = false,
    val maxDeflection: Float = 1.0f,
    /** Wygaszanie wychylenia kamery do zera po zatrzymaniu palca (wyjątek w ActionPolicy). */
    val decayMs: Int = 60,
)

@Serializable
data class TapTarget(
    val enabled: Boolean = true,
    val output: TapOutput = TapOutput.MOUSE_LEFT_AT_POINT,
    val maxMovePx: Float = 12f,
    val maxMs: Int = 220,
)

/** Long-press to rozpoznanie gestu: daje jedno wejście ZAMIAST tapu, nigdy oba. */
@Serializable
data class LongPress(
    val enabled: Boolean = true,
    val output: LongPressOutput = LongPressOutput.MOUSE_RIGHT_AT_POINT,
    val minMs: Int = 450,
)

@Serializable
data class PinchZoom(
    val enabled: Boolean = true,
    val output: PinchOutput = PinchOutput.MOUSE_WHEEL,
    val pxPerNotch: Float = 40f,
)

@Serializable
data class EdgeSwipe(
    val enabled: Boolean = true,
    val output: EdgeSwipeOutput = EdgeSwipeOutput.APP_MENU,
)

@Serializable
data class GesturesConfig(
    val cameraDrag: CameraDrag = CameraDrag(),
    val tapTarget: TapTarget = TapTarget(),
    val longPress: LongPress = LongPress(),
    val pinchZoom: PinchZoom = PinchZoom(),
    val edgeSwipeTop: EdgeSwipe = EdgeSwipe(),
)

@Serializable
data class Profile(
    val schema: Int,
    val id: String,
    val name: String,
    val tags: Map<String, String> = emptyMap(),
    val mode: ProfileMode,
    val reference: Reference = Reference(),
    val handedness: Handedness = Handedness.RIGHT,
    val global: GlobalSettings = GlobalSettings(),
    val gestures: GesturesConfig = GesturesConfig(),
    val controls: List<Control>,
)
