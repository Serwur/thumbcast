package app.thumbcast.android.host

import android.view.KeyEvent

/**
 * Tłumaczenie keycode Androida (`KeyEvent.KEYCODE_*`) na kod klawisza hosta (`keyMap` dla
 * `sendKeyboardInput`). Zwraca 0, gdy klawisza nie da się przetłumaczyć.
 *
 * W aplikacji wstrzykujemy implementację delegującą do upstreamowego `KeyboardTranslator`
 * (patches/, `ThumbcastKeyTranslator`), żeby nie duplikować tablic (plan 2.3 pkt 4).
 */
fun interface KeyTranslator {
    fun toHostKeyCode(androidKeyCode: Int): Short
}

/**
 * Zapasowy translator: keycode Androida -> Windows Virtual-Key z prefiksem 0x80 w starszym bajcie.
 * Zweryfikowane z upstreamowym `KeyboardTranslator` (v12.2, b48494cb96bf):
 * `return (short) ((KEY_PREFIX << 8) | translated)` z `KEY_PREFIX = (short) 0x80`; nieprzetłumaczalne
 * klawisze dają 0. Wartości VK poniżej zgadzają się z `switch` upstreamu (litery 0x41+, cyfry 0x30+,
 * F1 = 0x70+, SPACE 0x20, ESC 0x1B, TAB 0x09, ENTER 0x0D, MINUS 0xBD, EQUALS 0xBB, strzałki 0x25..0x28,
 * lewe/prawe SHIFT 0xA0/0xA1, CTRL 0xA2/0xA3, ALT 0xA4/0xA5).
 *
 * Obejmuje tylko klawisze ze słownika `KeyCodes` modelu. Używany w testach JVM i jako domyślny
 * parametr fabryki, gdy aplikacja nie poda własnego translatora.
 */
object WindowsVkKeyTranslator : KeyTranslator {
    private const val KEY_PREFIX: Int = 0x80

    override fun toHostKeyCode(androidKeyCode: Int): Short {
        val vk: Int = virtualKeyOf(androidKeyCode)
        if (vk == 0) {
            return 0
        }
        return ((KEY_PREFIX shl 8) or vk).toShort()
    }

    /** Sam kod VK (bez prefiksu) albo 0. */
    fun virtualKeyOf(androidKeyCode: Int): Int {
        val k: Int = androidKeyCode
        if (k >= KeyEvent.KEYCODE_A && k <= KeyEvent.KEYCODE_Z) {
            return 0x41 + (k - KeyEvent.KEYCODE_A)
        }
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) {
            return 0x30 + (k - KeyEvent.KEYCODE_0)
        }
        if (k >= KeyEvent.KEYCODE_F1 && k <= KeyEvent.KEYCODE_F12) {
            return 0x70 + (k - KeyEvent.KEYCODE_F1)
        }
        return when (k) {
            KeyEvent.KEYCODE_SHIFT_LEFT -> 0xA0
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xA1
            KeyEvent.KEYCODE_CTRL_LEFT -> 0xA2
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xA3
            KeyEvent.KEYCODE_ALT_LEFT -> 0xA4
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xA5
            KeyEvent.KEYCODE_SPACE -> 0x20
            KeyEvent.KEYCODE_ESCAPE -> 0x1B
            KeyEvent.KEYCODE_TAB -> 0x09
            KeyEvent.KEYCODE_ENTER -> 0x0D
            KeyEvent.KEYCODE_MINUS -> 0xBD
            KeyEvent.KEYCODE_EQUALS -> 0xBB
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
            KeyEvent.KEYCODE_DPAD_UP -> 0x26
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x28
            else -> 0
        }
    }
}
