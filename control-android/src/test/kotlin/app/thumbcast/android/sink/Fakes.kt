package app.thumbcast.android.sink

import app.thumbcast.android.host.HostConnection
import app.thumbcast.android.host.HostPadReporter

/** Fałszywe fasady upstreamu: zapisują wywołania jako tekst, żeby asercje były czytelne. */
class RecordingHostConnection : HostConnection {
    val calls: ArrayList<String> = ArrayList()

    override fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        calls.add("key " + Integer.toHexString(keyMap.toInt() and 0xFFFF) + " dir=" + keyDirection + " mod=" + modifier + " flags=" + flags)
    }

    override fun sendMouseMove(deltaX: Short, deltaY: Short) {
        calls.add("move $deltaX,$deltaY")
    }

    override fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {
        calls.add("pos $x,$y/$referenceWidth x $referenceHeight")
    }

    override fun sendMouseButtonDown(mouseButton: Byte) {
        calls.add("mdown $mouseButton")
    }

    override fun sendMouseButtonUp(mouseButton: Byte) {
        calls.add("mup $mouseButton")
    }

    override fun sendMouseHighResScroll(scrollAmount: Short) {
        calls.add("scroll $scrollAmount")
    }
}

class RecordingPadReporter : HostPadReporter {
    val calls: ArrayList<String> = ArrayList()

    override fun reportOscState(
        buttonFlags: Int,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
        leftTrigger: Byte,
        rightTrigger: Byte,
    ) {
        calls.add(
            "pad 0x" + Integer.toHexString(buttonFlags) +
                " ls=" + leftStickX + "," + leftStickY +
                " rs=" + rightStickX + "," + rightStickY +
                " lt=" + (leftTrigger.toInt() and 0xFF) + " rt=" + (rightTrigger.toInt() and 0xFF),
        )
    }
}
