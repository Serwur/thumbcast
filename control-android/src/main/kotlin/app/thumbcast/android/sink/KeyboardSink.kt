package app.thumbcast.android.sink

import app.thumbcast.android.host.HostConnection
import app.thumbcast.android.host.HostProtocol
import app.thumbcast.android.host.KeyTranslator

/**
 * Adapter klawiatury: `InputSink.key(code, down)` -> keycode Androida ([AndroidKeyCodes])
 * -> kod hosta ([KeyTranslator], w aplikacji upstreamowy `KeyboardTranslator`)
 * -> `NvConnection.sendKeyboardInput(keyMap, direction, modifier, flags)`.
 *
 * Bajt `modifier` odzwierciedla aktualnie trzymane modyfikatory z overlaya (SHIFT/CTRL/ALT),
 * tak jak upstream przekazuje stan modyfikatorów fizycznej klawiatury. Nie generuje to żadnych
 * dodatkowych naciśnięć: każdy `key()` daje dokładnie jeden pakiet.
 *
 * Nieznane kody są ignorowane (zliczane w [droppedCount]); walidator modelu i tak je odrzuca.
 */
class KeyboardSink(
    private val connection: HostConnection,
    private val translator: KeyTranslator,
) {
    private var modifierBits: Int = 0
    private val pressed: LinkedHashSet<String> = LinkedHashSet()

    var droppedCount: Int = 0
        private set

    /** Zwraca true, jeśli wysłano pakiet. */
    fun key(code: String, down: Boolean): Boolean {
        val androidCode: Int = AndroidKeyCodes.toAndroidKeyCode(code)
        if (androidCode == 0) {
            droppedCount++
            return false
        }
        val hostCode: Short = translator.toHostKeyCode(androidCode)
        if (hostCode.toInt() == 0) {
            droppedCount++
            return false
        }
        if (down) {
            // Drugi DOWN tego samego klawisza bez UP (np. dwa przyciski z tym samym kodem) nie jest
            // wysyłany ponownie: host dostałby powtórzenie, którego użytkownik nie wykonał.
            if (!pressed.add(code)) {
                return false
            }
        } else {
            if (!pressed.remove(code)) {
                return false
            }
        }
        val bit: Int = modifierBitOf(code)
        if (bit != 0) {
            modifierBits = if (down) (modifierBits or bit) else (modifierBits and bit.inv())
        }
        val direction: Byte = if (down) HostProtocol.KEY_DOWN else HostProtocol.KEY_UP
        connection.sendKeyboardInput(hostCode, direction, modifierBits.toByte(), HostProtocol.KEY_FLAGS_NONE)
        return true
    }

    /** Zwalnia wszystkie klawisze trzymane przez overlay (w odwrotnej kolejności wciśnięcia). */
    fun releaseAll() {
        val codes: List<String> = pressed.toList().reversed()
        for (code in codes) {
            key(code, false)
        }
        pressed.clear()
        modifierBits = 0
    }

    val pressedKeys: Set<String>
        get() = pressed

    private fun modifierBitOf(code: String): Int = when (code) {
        "SHIFT" -> HostProtocol.MODIFIER_SHIFT
        "CTRL" -> HostProtocol.MODIFIER_CTRL
        "ALT" -> HostProtocol.MODIFIER_ALT
        else -> 0
    }
}
