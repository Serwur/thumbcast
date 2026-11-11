package app.thumbcast.engine

import app.thumbcast.model.MouseBtn

/** Zegar testowy: przesuwany ręcznie. */
class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMs(): Long = now

    fun advance(ms: Long) {
        now += ms
    }
}

/** Rodzaj zarejestrowanego wywołania InputSink. */
enum class SinkCall { PAD, KEY, MOVE, BUTTON, POSITION, WHEEL, HWHEEL, APP }

/** Jedno wywołanie InputSink z przyczyną podaną przez ActionPolicy tuż przed nim. */
data class SinkRecord(
    val call: SinkCall,
    val cause: EmitCause?,
    val buttons: Int = 0,
    val lx: Short = 0,
    val ly: Short = 0,
    val rx: Short = 0,
    val ry: Short = 0,
    val lt: Byte = 0,
    val rt: Byte = 0,
    val code: String = "",
    val mouse: MouseBtn? = null,
    val down: Boolean = false,
    val dx: Int = 0,
    val dy: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val refW: Int = 0,
    val refH: Int = 0,
    val wheel: Int = 0,
    val app: String = ""
)

/** Krawędź wciśnięcia (0 -> 1) na wyjściu, z przyczyną. */
data class PressEdge(val what: String, val cause: EmitCause?)

/**
 * Sink testowy: rejestr wszystkich wywołań z przyczyną (EmitCauseListener) i śledzenie stanu wyjścia
 * (wciśnięte klawisze, przyciski myszy, ostatni padState) oraz krawędzi wciśnięcia.
 */
class FakeInputSink : InputSink, EmitCauseListener {

    val records: ArrayList<SinkRecord> = ArrayList()
    val pressEdges: ArrayList<PressEdge> = ArrayList()

    val keysDown: LinkedHashSet<String> = LinkedHashSet()
    val mouseDown: LinkedHashSet<MouseBtn> = LinkedHashSet()
    var lastPad: SinkRecord? = null
        private set

    /** Największa liczba jednocześnie aktywnych wejść dyskretnych (bity pada + klawisze + przyciski myszy). */
    var maxSimultaneous: Int = 0
        private set

    private var cause: EmitCause? = null

    private fun track() {
        val bits = Integer.bitCount(lastPad?.buttons ?: 0)
        val n = bits + keysDown.size + mouseDown.size
        if (n > maxSimultaneous) maxSimultaneous = n
    }

    fun resetMaxSimultaneous() {
        maxSimultaneous = 0
    }

    override fun onEmitCause(cause: EmitCause?) {
        this.cause = cause
    }

    override fun padState(
        buttons: Int, leftStickX: Short, leftStickY: Short,
        rightStickX: Short, rightStickY: Short, leftTrigger: Byte, rightTrigger: Byte
    ) {
        val r = SinkRecord(
            SinkCall.PAD, cause, buttons = buttons, lx = leftStickX, ly = leftStickY,
            rx = rightStickX, ry = rightStickY, lt = leftTrigger, rt = rightTrigger
        )
        val prev = lastPad
        val prevButtons = prev?.buttons ?: 0
        val newBits = buttons and prevButtons.inv()
        var bit = 1
        while (bit <= 0x8000) {
            if ((newBits and bit) != 0) pressEdges.add(PressEdge("pad:" + Integer.toHexString(bit), cause))
            bit = bit shl 1
        }
        val prevLt = prev?.lt ?: 0.toByte()
        val prevRt = prev?.rt ?: 0.toByte()
        if (prevLt.toInt() == 0 && leftTrigger.toInt() != 0) pressEdges.add(PressEdge("trigger:LT", cause))
        if (prevRt.toInt() == 0 && rightTrigger.toInt() != 0) pressEdges.add(PressEdge("trigger:RT", cause))
        lastPad = r
        records.add(r)
        track()
    }

    override fun key(code: String, down: Boolean) {
        records.add(SinkRecord(SinkCall.KEY, cause, code = code, down = down))
        if (down) {
            if (keysDown.add(code)) pressEdges.add(PressEdge("key:" + code, cause))
        } else {
            keysDown.remove(code)
        }
        track()
    }

    override fun mouseMoveRelative(dx: Int, dy: Int) {
        records.add(SinkRecord(SinkCall.MOVE, cause, dx = dx, dy = dy))
    }

    override fun mouseButton(button: MouseBtn, down: Boolean) {
        records.add(SinkRecord(SinkCall.BUTTON, cause, mouse = button, down = down))
        if (down) {
            if (mouseDown.add(button)) pressEdges.add(PressEdge("mouse:" + button.name, cause))
        } else {
            mouseDown.remove(button)
        }
        track()
    }

    override fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int) {
        records.add(SinkRecord(SinkCall.POSITION, cause, x = x, y = y, refW = refWidth, refH = refHeight))
    }

    override fun mouseWheel(highResDelta: Int) {
        records.add(SinkRecord(SinkCall.WHEEL, cause, wheel = highResDelta))
    }

    override fun mouseHScroll(highResDelta: Int) {
        records.add(SinkRecord(SinkCall.HWHEEL, cause, wheel = highResDelta))
    }

    override fun app(action: String) {
        records.add(SinkRecord(SinkCall.APP, cause, app = action))
        pressEdges.add(PressEdge("app:" + action, cause))
    }

    /** Czy stan wyjścia jest zerowy (nic wciśnięte, pad zerowy). */
    fun isZero(): Boolean {
        val p = lastPad
        val padZero = p == null || (p.buttons == 0 && p.lx.toInt() == 0 && p.ly.toInt() == 0 &&
            p.rx.toInt() == 0 && p.ry.toInt() == 0 && p.lt.toInt() == 0 && p.rt.toInt() == 0)
        return padZero && keysDown.isEmpty() && mouseDown.isEmpty()
    }

    fun ofCall(c: SinkCall): List<SinkRecord> {
        val out = ArrayList<SinkRecord>()
        for (r in records) if (r.call == c) out.add(r)
        return out
    }

    fun clear() {
        records.clear()
        pressEdges.clear()
    }
}
