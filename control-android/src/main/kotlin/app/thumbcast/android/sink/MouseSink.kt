package app.thumbcast.android.sink

import app.thumbcast.android.host.HostConnection
import app.thumbcast.android.host.HostProtocol
import app.thumbcast.model.MouseBtn

/**
 * Adapter myszy: `InputSink.mouse*` -> `NvConnection.sendMouse*`.
 *
 * - Ruch względny jest dzielony na kawałki mieszczące się w `Short` (bez gubienia reszty).
 * - Kółko pionowe i poziome: `highResDelta` (120 = 1 ząbek) obcinane do zakresu `Short`.
 * - Przyciski: śledzimy stan, żeby [releaseAll] zwolnił tylko to, co faktycznie wciśnięto.
 */
class MouseSink(private val connection: HostConnection) {

    private val pressed: LinkedHashSet<MouseBtn> = LinkedHashSet()

    fun mouseMoveRelative(dx: Int, dy: Int) {
        var remX: Int = dx
        var remY: Int = dy
        while (remX != 0 || remY != 0) {
            val stepX: Int = clampShort(remX)
            val stepY: Int = clampShort(remY)
            connection.sendMouseMove(stepX.toShort(), stepY.toShort())
            remX -= stepX
            remY -= stepY
        }
    }

    fun mouseButton(button: MouseBtn, down: Boolean) {
        if (down) {
            if (!pressed.add(button)) {
                return
            }
            connection.sendMouseButtonDown(hostButtonOf(button))
        } else {
            if (!pressed.remove(button)) {
                return
            }
            connection.sendMouseButtonUp(hostButtonOf(button))
        }
    }

    fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int) {
        if (refWidth <= 0 || refHeight <= 0) {
            return
        }
        val cx: Int = clampRange(x, 0, refWidth - 1)
        val cy: Int = clampRange(y, 0, refHeight - 1)
        connection.sendMousePosition(
            clampShort(cx).toShort(),
            clampShort(cy).toShort(),
            clampShort(refWidth).toShort(),
            clampShort(refHeight).toShort(),
        )
    }

    fun mouseWheel(highResDelta: Int) {
        if (highResDelta == 0) {
            return
        }
        connection.sendMouseHighResScroll(clampShort(highResDelta).toShort())
    }

    /** Kółko poziome (dodatnie = w prawo). */
    fun mouseHScroll(highResDelta: Int) {
        if (highResDelta == 0) {
            return
        }
        connection.sendMouseHighResHScroll(clampShort(highResDelta).toShort())
    }

    fun releaseAll() {
        val buttons: List<MouseBtn> = pressed.toList()
        for (b in buttons) {
            mouseButton(b, false)
        }
        pressed.clear()
    }

    val pressedButtons: Set<MouseBtn>
        get() = pressed

    companion object {
        fun hostButtonOf(button: MouseBtn): Byte = when (button) {
            MouseBtn.LEFT -> HostProtocol.MOUSE_BUTTON_LEFT
            MouseBtn.RIGHT -> HostProtocol.MOUSE_BUTTON_RIGHT
            MouseBtn.MIDDLE -> HostProtocol.MOUSE_BUTTON_MIDDLE
        }

        private fun clampShort(v: Int): Int = clampRange(v, Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

        private fun clampRange(v: Int, lo: Int, hi: Int): Int {
            if (v < lo) return lo
            if (v > hi) return hi
            return v
        }
    }
}
