package app.thumbcast.android.sink

import android.view.KeyEvent

/**
 * Tabela: kod logiczny z modelu (`KeyCodes` w :control-model) -> keycode Androida (`KeyEvent.KEYCODE_*`).
 *
 * Pokrywa cały słownik `KeyCodes.ALL`: litery A–Z, cyfry 0–9, F1–F12, SHIFT/CTRL/ALT (lewe),
 * SPACE, ESC, TAB, ENTER, "-", "=", strzałki UP/DOWN/LEFT/RIGHT.
 * Test `SinksTest.everyModelKeyCodeHasAndroidAndHostMapping` sprawdza pokrycie słownika modelu.
 */
object AndroidKeyCodes {

    private val TABLE: Map<String, Int> = buildTable()

    /** Keycode Androida albo [KeyEvent.KEYCODE_UNKNOWN] (0), gdy kodu nie ma w tabeli. */
    fun toAndroidKeyCode(code: String): Int {
        val value: Int? = TABLE[code]
        if (value == null) {
            return KeyEvent.KEYCODE_UNKNOWN
        }
        return value
    }

    fun isKnown(code: String): Boolean = TABLE.containsKey(code)

    val codes: Set<String>
        get() = TABLE.keys

    private fun buildTable(): Map<String, Int> {
        val m: HashMap<String, Int> = HashMap()
        var c: Char = 'A'
        while (c <= 'Z') {
            m[c.toString()] = KeyEvent.KEYCODE_A + (c - 'A')
            c++
        }
        var d: Int = 0
        while (d <= 9) {
            m[d.toString()] = KeyEvent.KEYCODE_0 + d
            d++
        }
        var f: Int = 1
        while (f <= 12) {
            m["F$f"] = KeyEvent.KEYCODE_F1 + (f - 1)
            f++
        }
        m["SHIFT"] = KeyEvent.KEYCODE_SHIFT_LEFT
        m["CTRL"] = KeyEvent.KEYCODE_CTRL_LEFT
        m["ALT"] = KeyEvent.KEYCODE_ALT_LEFT
        m["SPACE"] = KeyEvent.KEYCODE_SPACE
        m["ESC"] = KeyEvent.KEYCODE_ESCAPE
        m["TAB"] = KeyEvent.KEYCODE_TAB
        m["ENTER"] = KeyEvent.KEYCODE_ENTER
        m["-"] = KeyEvent.KEYCODE_MINUS
        m["="] = KeyEvent.KEYCODE_EQUALS
        m["UP"] = KeyEvent.KEYCODE_DPAD_UP
        m["DOWN"] = KeyEvent.KEYCODE_DPAD_DOWN
        m["LEFT"] = KeyEvent.KEYCODE_DPAD_LEFT
        m["RIGHT"] = KeyEvent.KEYCODE_DPAD_RIGHT
        return m
    }
}
