package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import kotlin.math.roundToInt

/** Migawka stanu pada w jednostkach protokołu. */
data class PadSnapshot(
    val buttons: Int,
    val leftStickX: Short,
    val leftStickY: Short,
    val rightStickX: Short,
    val rightStickY: Short,
    val leftTrigger: Byte,
    val rightTrigger: Byte
) {
    companion object {
        val ZERO: PadSnapshot = PadSnapshot(0, 0, 0, 0, 0, 0, 0)
    }
}

/**
 * Agreguje efekty do stanu wyjścia i przekazuje go do InputSink.
 * - Pad: jeden zbiorczy padState; przyciski i triggery wysyłane od razu, osie koalescencyjnie w flush()
 *   (najwyżej 1 pakiet osi na cykl). padState wysyłany tylko przy zmianie.
 * - Klawisze i przyciski myszy: deduplikacja (nie wysyłamy DOWN dla już wciśniętego).
 * Tylko ActionPolicy wywołuje metody tej klasy (InputEngine nie trzyma do niej referencji).
 */
class OutputMapper(private val sink: InputSink) {

    private var buttons: Int = 0
    private var leftX: Float = 0f
    private var leftY: Float = 0f
    private var rightX: Float = 0f
    private var rightY: Float = 0f
    private var leftTrigger: Int = 0
    private var rightTrigger: Int = 0
    private var axesDirty: Boolean = false
    private var lastSent: PadSnapshot? = null

    private val keysDown: LinkedHashSet<String> = LinkedHashSet()
    private val mouseDown: LinkedHashSet<MouseBtn> = LinkedHashSet()

    fun apply(effect: OutputEffect) {
        when (effect) {
            is PadButtonSet -> {
                val flag = PadFlags.flagOf(effect.button)
                buttons = if (effect.pressed) (buttons or flag) else (buttons and flag.inv())
                sendPadIfChanged()
            }
            is TriggerSet -> {
                val v = effect.value.coerceIn(0, 255)
                if (effect.trigger == PadTrigger.LT) {
                    leftTrigger = v
                } else {
                    rightTrigger = v
                }
                sendPadIfChanged()
            }
            is StickSet -> {
                val x = effect.x.coerceIn(-1f, 1f)
                val y = effect.y.coerceIn(-1f, 1f)
                if (effect.stick == PadStick.LEFT) {
                    leftX = x
                    leftY = y
                } else {
                    rightX = x
                    rightY = y
                }
                axesDirty = true
            }
            is KeySet -> {
                if (effect.pressed) {
                    if (keysDown.add(effect.code)) sink.key(effect.code, true)
                } else {
                    if (keysDown.remove(effect.code)) sink.key(effect.code, false)
                }
            }
            is MouseButtonSet -> {
                if (effect.pressed) {
                    if (mouseDown.add(effect.button)) sink.mouseButton(effect.button, true)
                } else {
                    if (mouseDown.remove(effect.button)) sink.mouseButton(effect.button, false)
                }
            }
            is MouseMove -> {
                if (effect.dx != 0 || effect.dy != 0) sink.mouseMoveRelative(effect.dx, effect.dy)
            }
            is MouseAt -> sink.mousePosition(effect.x, effect.y, effect.refWidth, effect.refHeight)
            is Wheel -> {
                if (effect.delta != 0) sink.mouseWheel(effect.delta)
            }
            is HWheel -> {
                if (effect.delta != 0) sink.mouseHScroll(effect.delta)
            }
            is AppAction -> sink.app(effect.action)
        }
    }

    /** Wysyła zaległy stan osi (koniec cyklu). */
    fun flush() {
        if (axesDirty) sendPadIfChanged()
    }

    /**
     * Zerowy stan: puszcza klawisze i przyciski myszy i wysyła zerowy padState (nawet jeśli był już zerowy).
     * Wyjątek: gdy pad nigdy nie był używany (np. profil KBM), padState nie jest wysyłany, żeby nie tworzyć
     * na hoście wirtualnego pada bez potrzeby.
     */
    fun releaseAll() {
        val keys = ArrayList<String>(keysDown)
        keysDown.clear()
        for (k in keys) sink.key(k, false)
        val mice = ArrayList<MouseBtn>(mouseDown)
        mouseDown.clear()
        for (m in mice) sink.mouseButton(m, false)
        buttons = 0
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        leftTrigger = 0
        rightTrigger = 0
        axesDirty = false
        if (lastSent != null) send(PadSnapshot.ZERO)
    }

    fun isHeld(key: HeldKey): Boolean = when (key) {
        is HeldPad -> (buttons and PadFlags.flagOf(key.button)) != 0
        is HeldTrigger -> (if (key.trigger == PadTrigger.LT) leftTrigger else rightTrigger) > 0
        is HeldStick -> if (key.stick == PadStick.LEFT) (leftX != 0f || leftY != 0f) else (rightX != 0f || rightY != 0f)
        is HeldKeyCode -> keysDown.contains(key.code)
        is HeldMouse -> mouseDown.contains(key.button)
    }

    /** Bieżące wychylenie gałki (-1..1), [x, y]. */
    fun stickValue(stick: PadStick): FloatArray =
        if (stick == PadStick.LEFT) floatArrayOf(leftX, leftY) else floatArrayOf(rightX, rightY)

    fun snapshot(): PadSnapshot = PadSnapshot(
        buttons,
        axis(leftX), axis(leftY), axis(rightX), axis(rightY),
        leftTrigger.toByte(), rightTrigger.toByte()
    )

    private fun sendPadIfChanged() {
        axesDirty = false
        val s = snapshot()
        if (s != lastSent) send(s)
    }

    private fun send(s: PadSnapshot) {
        lastSent = s
        sink.padState(s.buttons, s.leftStickX, s.leftStickY, s.rightStickX, s.rightStickY, s.leftTrigger, s.rightTrigger)
    }

    private fun axis(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
}

/** Flagi przycisków = stałe ControllerPacket z upstreamu (CONTRACT.md). */
object PadFlags {
    const val A: Int = 0x1000
    const val B: Int = 0x2000
    const val X: Int = 0x4000
    const val Y: Int = 0x8000
    const val UP: Int = 0x0001
    const val DOWN: Int = 0x0002
    const val LEFT: Int = 0x0004
    const val RIGHT: Int = 0x0008
    const val LB: Int = 0x0100
    const val RB: Int = 0x0200
    const val START: Int = 0x0010
    const val BACK: Int = 0x0020
    const val L3: Int = 0x0040
    const val R3: Int = 0x0080
    const val GUIDE: Int = 0x0400

    fun flagOf(b: PadButton): Int = when (b) {
        PadButton.A -> A
        PadButton.B -> B
        PadButton.X -> X
        PadButton.Y -> Y
        PadButton.UP -> UP
        PadButton.DOWN -> DOWN
        PadButton.LEFT -> LEFT
        PadButton.RIGHT -> RIGHT
        PadButton.LB -> LB
        PadButton.RB -> RB
        PadButton.START -> START
        PadButton.BACK -> BACK
        PadButton.L3 -> L3
        PadButton.R3 -> R3
        PadButton.GUIDE -> GUIDE
    }
}
