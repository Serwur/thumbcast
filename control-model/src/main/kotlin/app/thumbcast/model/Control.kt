package app.thumbcast.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kontrolka na overlayu. W JSON rodzaj wyznacza pole `type`: "stick" | "button" | "modifier" | "system".
 * Każdy inny `type` (w tym "sequence", "macro", "turbo", "repeat", "delay", "toggle_auto") jest odrzucany przez parser.
 */
@Serializable
sealed interface Control {
    val id: String
    val rect: Rect
    val label: String?
}

/**
 * Joystick. `output` to Output.Stick (w obu trybach określa rolę gałki).
 * W trybie KEYBOARD_MOUSE silnik zamiast osi emituje klawisze z `kbmKeys` po przekroczeniu `kbmThreshold`
 * (8 kierunków), więc `kbmKeys` jest wtedy wymagane.
 */
@Serializable
@SerialName("stick")
data class StickControl(
    override val id: String,
    override val rect: Rect,
    override val label: String? = null,
    @Serializable(with = OutputSerializer::class)
    val output: Output = Output.Stick(PadStick.LEFT),
    val floating: Boolean = true,
    val deadzone: Float = 0.12f,
    val outerDeadzone: Float = 0.04f,
    val curve: Curve = Curve(CurveType.LINEAR),
    val kbmKeys: StickKeys? = null,
    val kbmThreshold: Float = 0.35f,
    /** Przesunięcie poza obrys utrzymuje 100% wychylenia, dopóki palec dotyka (stan wejścia, nie automat). */
    val sprintLock: Boolean = false,
    val icon: String? = null,
) : Control

/**
 * Zwykły przycisk. `behavior` musi być HOLD (LATCH tylko w ModifierControl).
 * `chord` (opcjonalny, domyślnie brak): pole zostaje w schemacie bez migracji, ale od 11.11.2026 akordy nie są obsługiwane.
 * Walidator odrzuca każdą wartość, także przy `global.allowChords = true` (FAIR_PLAY.md: 1 dotyk = 1 wejście).
 */
@Serializable
@SerialName("button")
data class ButtonControl(
    override val id: String,
    override val rect: Rect,
    override val label: String? = null,
    @Serializable(with = OutputSerializer::class)
    val output: Output,
    val behavior: Behavior = Behavior.HOLD,
    val shape: Shape = Shape.CIRCLE,
    val icon: String? = null,
    @Serializable(with = OutputSerializer::class)
    val chord: Output? = null,
) : Control

/** Modyfikator (LT/RT/LB/RB albo SHIFT/CTRL/ALT). Jako jedyny może mieć `behavior = LATCH`. */
@Serializable
@SerialName("modifier")
data class ModifierControl(
    override val id: String,
    override val rect: Rect,
    override val label: String? = null,
    @Serializable(with = OutputSerializer::class)
    val output: Output,
    val behavior: Behavior = Behavior.HOLD,
    val shape: Shape = Shape.ROUNDED_RECT,
    val icon: String? = null,
) : Control

/** Przycisk systemowy aplikacji (menu, klawiatura ekranowa, ukrycie overlaya). Nic nie wysyła do hosta. */
@Serializable
@SerialName("system")
data class SystemControl(
    override val id: String,
    override val rect: Rect,
    override val label: String? = null,
    @Serializable(with = AppOutputSerializer::class)
    val output: Output.App,
    val shape: Shape = Shape.ROUNDED_RECT,
    val icon: String? = null,
) : Control
