package app.thumbcast.android.host

/**
 * Fasada wysyłania klawiatury i myszy do hosta.
 *
 * Sygnatury odzwierciedlają metody `com.limelight.nvstream.NvConnection` z upstreamu
 * (zweryfikowane na tagu v12.2 = commit b48494cb96bf, 12.09.2026; wszystkie sześć metod ma
 * dokładnie te nazwy, kolejność i typy parametrów, wszystkie `public void`, bez `throws`).
 * Implementacja w `patches/` (`HostConnectionImpl`) to więc czysta delegacja 1:1.
 *
 * Wszystkie metody są wołane z wątku UI (tak jak w upstreamie; NvConnection przekazuje dane
 * do JNI bez blokowania).
 */
interface HostConnection {
    /** Odpowiednik `NvConnection.sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags)`. */
    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte)

    /** Odpowiednik `NvConnection.sendMouseMove(short deltaX, short deltaY)`: ruch względny. */
    fun sendMouseMove(deltaX: Short, deltaY: Short)

    /** Odpowiednik `NvConnection.sendMousePosition(short x, short y, short referenceWidth, short referenceHeight)`. */
    fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short)

    /** Odpowiednik `NvConnection.sendMouseButtonDown(byte mouseButton)`. */
    fun sendMouseButtonDown(mouseButton: Byte)

    /** Odpowiednik `NvConnection.sendMouseButtonUp(byte mouseButton)`. */
    fun sendMouseButtonUp(mouseButton: Byte)

    /** Odpowiednik `NvConnection.sendMouseHighResScroll(short scrollAmount)`; 120 = 1 ząbek. */
    fun sendMouseHighResScroll(scrollAmount: Short)
}

/**
 * Stałe protokołu po stronie hosta używane przez adaptery.
 *
 * Wartości zweryfikowane z upstreamem v12.2 (commit b48494cb96bf): klasy
 * `com.limelight.nvstream.input.KeyboardPacket` i `MouseButtonPacket` (obie `byte`).
 * Implementacja w `patches/` może używać stałych upstreamu; te tutaj służą adapterom
 * i testom JVM (bez zależności od upstreamu).
 */
object HostProtocol {
    /** `KeyboardPacket.KEY_DOWN` = 0x03 (zweryfikowane). */
    const val KEY_DOWN: Byte = 0x03

    /** `KeyboardPacket.KEY_UP` = 0x04 (zweryfikowane). */
    const val KEY_UP: Byte = 0x04

    /** Bity modyfikatorów (`KeyboardPacket.MODIFIER_*`, typ `byte`; zweryfikowane). */
    const val MODIFIER_SHIFT: Int = 0x01
    const val MODIFIER_CTRL: Int = 0x02
    const val MODIFIER_ALT: Int = 0x04

    /** `KeyboardPacket.MODIFIER_META` = 0x08 (nieużywany przez model; dla kompletności). */
    const val MODIFIER_META: Int = 0x08

    /**
     * Czwarty argument `sendKeyboardInput`. Upstream wysyła 0, gdy kod jest znormalizowany (QWERTY),
     * a `MoonBridge.SS_KBE_FLAG_NON_NORMALIZED` dla klawiatur z innym układem. Nasze kody pochodzą
     * z `KeyboardTranslator.translate(keycode, -1)` (zakłada QWERTY; tak samo robi upstreamowe
     * `keyboardEvent(boolean, short)`, które przekazuje `(byte)0`), więc 0 jest poprawne.
     */
    const val KEY_FLAGS_NONE: Byte = 0x00

    /** `MouseButtonPacket.BUTTON_*` (zweryfikowane; uwaga: środkowy = 2, prawy = 3). */
    const val MOUSE_BUTTON_LEFT: Byte = 0x01
    const val MOUSE_BUTTON_MIDDLE: Byte = 0x02
    const val MOUSE_BUTTON_RIGHT: Byte = 0x03

    /** `MouseButtonPacket.BUTTON_X1` / `BUTTON_X2` (model ich nie używa). */
    const val MOUSE_BUTTON_X1: Byte = 0x04
    const val MOUSE_BUTTON_X2: Byte = 0x05
}
