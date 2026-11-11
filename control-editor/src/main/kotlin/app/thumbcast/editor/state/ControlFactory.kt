package app.thumbcast.editor.state

import app.thumbcast.model.AppActions
import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.CameraOutput
import app.thumbcast.model.Control
import app.thumbcast.model.FreeZoneMode
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import app.thumbcast.model.StickKeys
import app.thumbcast.model.SystemControl

/** Rodzaj kontrolki dodawanej z panelu. */
enum class NewControlKind(val idPrefix: String) {
    BUTTON("button"),
    STICK("stick"),
    MODIFIER("modifier"),
    SYSTEM("system"),
}

fun Control.kind(): NewControlKind = when (this) {
    is ButtonControl -> NewControlKind.BUTTON
    is StickControl -> NewControlKind.STICK
    is ModifierControl -> NewControlKind.MODIFIER
    is SystemControl -> NewControlKind.SYSTEM
}

fun Control.withRect(r: Rect): Control = when (this) {
    is StickControl -> copy(rect = r)
    is ButtonControl -> copy(rect = r)
    is ModifierControl -> copy(rect = r)
    is SystemControl -> copy(rect = r)
}

fun Control.withId(id: String): Control = when (this) {
    is StickControl -> copy(id = id)
    is ButtonControl -> copy(id = id)
    is ModifierControl -> copy(id = id)
    is SystemControl -> copy(id = id)
}

fun Control.withLabel(label: String?): Control = when (this) {
    is StickControl -> copy(label = label)
    is ButtonControl -> copy(label = label)
    is ModifierControl -> copy(label = label)
    is SystemControl -> copy(label = label)
}

/** Kształt grafiki (gałka zawsze koło, nie ma pola `shape`). */
fun Control.shapeOrNull(): Shape? = when (this) {
    is StickControl -> null
    is ButtonControl -> shape
    is ModifierControl -> shape
    is SystemControl -> shape
}

fun Control.withShape(shape: Shape): Control = when (this) {
    is StickControl -> this
    is ButtonControl -> copy(shape = shape)
    is ModifierControl -> copy(shape = shape)
    is SystemControl -> copy(shape = shape)
}

/** Jedyne wyjście kontrolki. */
fun Control.output(): Output = when (this) {
    is StickControl -> output
    is ButtonControl -> output
    is ModifierControl -> output
    is SystemControl -> output
}

/**
 * Nowe wyjście; typ musi pasować do kontrolki (inaczej kontrolka bez zmian). Zmiana wyjścia nigdy nie
 * dodaje akordu, a przycisk systemowy przyjmuje tylko akcje aplikacji.
 */
fun Control.withOutput(o: Output): Control = when (this) {
    is StickControl -> if (o is Output.Stick) copy(output = o) else this
    is ButtonControl -> copy(output = o)
    is ModifierControl -> copy(output = o)
    is SystemControl -> if (o is Output.App) copy(output = o) else this
}

/**
 * Domyślne kontrolki dodawane z panelu. Rozmiary podane są w „kwadratowych” jednostkach wysokości ekranu,
 * a szerokość dzieli się przez proporcję ekranu ([aspect] = szerokość / wysokość w px), żeby przycisk
 * wyglądał jak koło, a nie elipsa. Nowa kontrolka trafia na środek ekranu.
 */
object ControlFactory {

    /** Proporcja ekranu odniesienia z profilu (20:9), gdy rozmiar płótna nie jest jeszcze znany. */
    const val DEFAULT_ASPECT: Float = 20f / 9f

    private const val BUTTON_SIZE = 0.16f
    private const val STICK_SIZE = 0.40f
    private const val MODIFIER_H = 0.11f
    private const val MODIFIER_W = 0.20f
    private const val SYSTEM_H = 0.09f
    private const val SYSTEM_W = 0.13f

    /** Klawisze kierunków nowej gałki w trybie keyboard_mouse (walidator ich tam wymaga). */
    val DEFAULT_KBM_KEYS: StickKeys = StickKeys(up = "W", down = "S", left = "A", right = "D")

    fun create(kind: NewControlKind, profile: Profile, aspect: Float): Control {
        val a: Float = if (aspect.isNaN() || aspect <= 0f) DEFAULT_ASPECT else aspect
        val id: String = uniqueId(profile, kind.idPrefix)
        val kbm: Boolean = profile.mode == ProfileMode.KEYBOARD_MOUSE
        return when (kind) {
            NewControlKind.BUTTON -> ButtonControl(
                id = id,
                rect = centered(BUTTON_SIZE / a, BUTTON_SIZE),
                output = if (kbm) Output.Key("1") else Output.Pad(PadButton.A),
                shape = Shape.CIRCLE,
            )
            NewControlKind.STICK -> StickControl(
                id = id,
                rect = centered(STICK_SIZE / a, STICK_SIZE),
                output = Output.Stick(freeStick(profile)),
                floating = false,
                kbmKeys = if (kbm) DEFAULT_KBM_KEYS else null,
            )
            NewControlKind.MODIFIER -> ModifierControl(
                id = id,
                rect = centered(MODIFIER_W / a, MODIFIER_H),
                output = if (kbm) Output.Key("SHIFT") else Output.Trigger(PadTrigger.LT),
                behavior = Behavior.HOLD,
                shape = Shape.ROUNDED_RECT,
            )
            NewControlKind.SYSTEM -> SystemControl(
                id = id,
                rect = centered(SYSTEM_W / a, SYSTEM_H),
                output = Output.App(AppActions.MENU),
                shape = Shape.ROUNDED_RECT,
            )
        }
    }

    /** Kopia z nowym id, przesunięta o 3% w prawo i w dół (w granicach ekranu), zawsze bez akordu. */
    fun duplicate(source: Control, profile: Profile): Control {
        val r: Rect = source.rect
        val moved: Rect = EditorGeometry.move(r, DUPLICATE_OFFSET, DUPLICATE_OFFSET, snap = false)
        val copy: Control = source.withId(uniqueId(profile, source.kind().idPrefix)).withRect(moved)
        return if (copy is ButtonControl) copy.copy(chord = null) else copy
    }

    private const val DUPLICATE_OFFSET = 0.03f

    /** `prefix_N` z najmniejszym N >= 1, którego nie ma w profilu. */
    fun uniqueId(profile: Profile, prefix: String): String {
        val used: Set<String> = profile.controls.mapTo(HashSet()) { it.id }
        var n = 1
        while ("${prefix}_$n" in used) n++
        return "${prefix}_$n"
    }

    /**
     * Gałka pada, której jeszcze nic nie steruje: w Pad+ prawą zajmuje kamera `pad_right_stick`
     * (przy wolnej strefie camera). Gdy obie zajęte, LEFT (walidator pokaże konflikt).
     */
    fun freeStick(profile: Profile): PadStick {
        val used: HashSet<PadStick> = HashSet()
        if (profile.mode == ProfileMode.PAD_PLUS &&
            profile.gestures.freeZone == FreeZoneMode.CAMERA &&
            profile.gestures.cameraDrag.output == CameraOutput.PAD_RIGHT_STICK
        ) {
            used.add(PadStick.RIGHT)
        }
        if (profile.mode == ProfileMode.PAD_PLUS) {
            for (c in profile.controls) {
                if (c is StickControl) (c.output as? Output.Stick)?.let { used.add(it.stick) }
            }
        }
        return PadStick.values().firstOrNull { it !in used } ?: PadStick.LEFT
    }

    private fun centered(w: Float, h: Float): Rect =
        EditorGeometry.clamp(Rect(0.5f - w * 0.5f, 0.5f - h * 0.5f, w, h))
}
