package app.thumbcast.editor.state

import app.thumbcast.model.AppActions
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.KeyCodes
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.MouseBtn
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl

/** Grupa wyjść w panelu właściwości. */
enum class OutputGroup { PAD, TRIGGER, KEY, MOUSE, STICK, APP }

/** Wyjścia jednej grupy w kolejności wyświetlania. */
data class OutputOptions(val group: OutputGroup, val outputs: List<Output>)

/**
 * Wyjścia, które edytor w ogóle pozwala wybrać dla danego typu kontrolki i trybu profilu.
 * To podzbiór tego, co dopuszcza `ProfileValidator` (bez kółka i ruchu myszy na przyciskach, bez akcji
 * aplikacji poza kontrolką systemową, bez wyjść pada w trybie keyboard_mouse). Każda kontrolka ma
 * dokładnie jedno wyjście; akordów i typów zakazanych nie da się tu wybrać (FAIR_PLAY.md).
 */
object OutputCatalog {

    /** Klawisze w kolejności: litery, cyfry, F1–F12, specjalne, strzałki, modyfikatory. */
    val KEYS: List<String> = buildList {
        addAll(KeyCodes.LETTERS.sorted())
        addAll(KeyCodes.DIGITS.sorted())
        addAll((1..12).map { "F$it" })
        addAll(listOf("SPACE", "ESC", "TAB", "ENTER", "-", "="))
        addAll(listOf("UP", "DOWN", "LEFT", "RIGHT"))
        addAll(listOf("SHIFT", "CTRL", "ALT"))
    }

    private val MOUSE: List<Output> = MouseBtn.values().map { Output.Mouse(it) }

    fun optionsFor(control: Control, mode: ProfileMode): List<OutputOptions> = when (control) {
        is ButtonControl -> buttonOptions(mode)
        is ModifierControl -> modifierOptions(mode)
        is StickControl -> listOf(OutputOptions(OutputGroup.STICK, PadStick.values().map { Output.Stick(it) }))
        is SystemControl -> listOf(OutputOptions(OutputGroup.APP, AppActions.ALL.sorted().map { Output.App(it) }))
    }

    fun buttonOptions(mode: ProfileMode): List<OutputOptions> = buildList {
        if (mode == ProfileMode.PAD_PLUS) {
            add(OutputOptions(OutputGroup.PAD, PadButton.values().map { Output.Pad(it) }))
            add(OutputOptions(OutputGroup.TRIGGER, PadTrigger.values().map { Output.Trigger(it) }))
        }
        add(OutputOptions(OutputGroup.KEY, KEYS.map { Output.Key(it) }))
        add(OutputOptions(OutputGroup.MOUSE, MOUSE))
    }

    fun modifierOptions(mode: ProfileMode): List<OutputOptions> = buildList {
        if (mode == ProfileMode.PAD_PLUS) {
            add(OutputOptions(OutputGroup.TRIGGER, PadTrigger.values().map { Output.Trigger(it) }))
            add(OutputOptions(OutputGroup.PAD, listOf(Output.Pad(PadButton.LB), Output.Pad(PadButton.RB))))
        }
        add(OutputOptions(OutputGroup.KEY, KeyCodes.MODIFIERS.sorted().map { Output.Key(it) }))
    }

    fun groupOf(o: Output): OutputGroup = when (o) {
        is Output.Pad -> OutputGroup.PAD
        is Output.Trigger -> OutputGroup.TRIGGER
        is Output.Key -> OutputGroup.KEY
        is Output.Mouse, Output.MouseWheel, Output.MouseLook -> OutputGroup.MOUSE
        is Output.Stick -> OutputGroup.STICK
        is Output.App -> OutputGroup.APP
    }

    /** Czy edytor pozwala ustawić [output] na kontrolce [control] w trybie [mode]. */
    fun isAllowed(control: Control, mode: ProfileMode, output: Output): Boolean =
        optionsFor(control, mode).any { output in it.outputs }
}
