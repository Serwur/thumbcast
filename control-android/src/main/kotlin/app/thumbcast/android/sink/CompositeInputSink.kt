package app.thumbcast.android.sink

import app.thumbcast.android.host.AppActionHandler
import app.thumbcast.engine.InputSink
import app.thumbcast.model.MouseBtn

/**
 * Jeden `InputSink` dla silnika, rozdzielający wyjście na [PadSink], [KeyboardSink], [MouseSink],
 * a `app(action)` kieruje do callbacku UI ([AppActionHandler]); nic z tego nie idzie do hosta.
 *
 * [listener] (opcjonalny) dostaje informację o każdej wysyłce; nakładka debug liczy na tej
 * podstawie opóźnienie `event.eventTime -> send` i pokazuje ostatnie flagi pada.
 */
class CompositeInputSink(
    private val pad: PadSink,
    private val keyboard: KeyboardSink,
    private val mouse: MouseSink,
    private val appActions: AppActionHandler,
) : InputSink {

    /** Obserwator wysyłek (tylko diagnostyka; nie może niczego wysyłać). */
    interface SendListener {
        fun onSent(kind: String, detail: String)
    }

    var listener: SendListener? = null

    override fun padState(
        buttons: Int,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
        leftTrigger: Byte,
        rightTrigger: Byte,
    ) {
        val sent: Boolean = pad.padState(buttons, leftStickX, leftStickY, rightStickX, rightStickY, leftTrigger, rightTrigger)
        if (sent) {
            emitSent("pad", "0x" + Integer.toHexString(buttons))
        }
    }

    override fun key(code: String, down: Boolean) {
        val sent: Boolean = keyboard.key(code, down)
        if (sent) {
            emitSent("key", code + (if (down) " down" else " up"))
        }
    }

    override fun mouseMoveRelative(dx: Int, dy: Int) {
        mouse.mouseMoveRelative(dx, dy)
        emitSent("mouseMove", "$dx,$dy")
    }

    override fun mouseButton(button: MouseBtn, down: Boolean) {
        mouse.mouseButton(button, down)
        emitSent("mouseButton", button.name + (if (down) " down" else " up"))
    }

    override fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int) {
        mouse.mousePosition(x, y, refWidth, refHeight)
        emitSent("mousePos", "$x,$y/$refWidth x $refHeight")
    }

    override fun mouseWheel(highResDelta: Int) {
        mouse.mouseWheel(highResDelta)
        emitSent("wheel", highResDelta.toString())
    }

    override fun app(action: String) {
        appActions.onAppAction(action)
        emitSent("app", action)
    }

    /**
     * Bezpiecznik po stronie adapterów, wołany przez overlay po `engine.releaseAll()`:
     * zwalnia klawisze i przyciski myszy, które adaptery wciąż uważają za wciśnięte,
     * i wysyła zerowy stan pada. Silnik powinien to zrobić sam; tu chodzi o odporność na błąd.
     */
    fun releaseAll() {
        keyboard.releaseAll()
        mouse.releaseAll()
        pad.reset()
        emitSent("releaseAll", "")
    }

    val lastPadButtons: Int
        get() = pad.lastSentButtons

    private fun emitSent(kind: String, detail: String) {
        val l: SendListener? = listener
        if (l != null) {
            l.onSent(kind, detail)
        }
    }
}
