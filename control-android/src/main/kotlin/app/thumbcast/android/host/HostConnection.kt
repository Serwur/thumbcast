package app.thumbcast.android.host

/**
 * Fasada wysyłania klawiatury i myszy do hosta.
 *
 * Sygnatury odzwierciedlają metody `com.limelight.nvstream.NvConnection` z upstreamu v12.2
 * (plan, sekcja 2.2): nazwy, kolejność i typy parametrów są takie same, dzięki czemu
 * implementacja w `patches/` (`HostConnectionImpl`) to czysta delegacja 1:1.
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
 * UWAGA: wartości pochodzą z pamięci o moonlight-common-c / upstreamie (`KeyboardPacket`,
 * `MouseButtonPacket`), a nie z kodu sprawdzonego w tej sesji. Implementacja w `patches/`
 * powinna używać stałych upstreamu; te tutaj służą adapterom i testom JVM.
 * Do sprawdzenia przy pierwszym buildzie z forkiem (patches/APPLY.md, sekcja „Założenia”).
 */
object HostProtocol {
    /** `KeyboardPacket.KEY_DOWN` [?]. */
    const val KEY_DOWN: Byte = 0x03

    /** `KeyboardPacket.KEY_UP` [?]. */
    const val KEY_UP: Byte = 0x04

    /** Bity modyfikatorów (`KeyboardPacket.MODIFIER_*`) [?]. */
    const val MODIFIER_SHIFT: Int = 0x01
    const val MODIFIER_CTRL: Int = 0x02
    const val MODIFIER_ALT: Int = 0x04

    /** Flagi klawisza: 0 = kod znormalizowany (VK), bez dodatkowych flag. */
    const val KEY_FLAGS_NONE: Byte = 0x00

    /** `MouseButtonPacket.BUTTON_*` [?]. */
    const val MOUSE_BUTTON_LEFT: Byte = 0x01
    const val MOUSE_BUTTON_MIDDLE: Byte = 0x02
    const val MOUSE_BUTTON_RIGHT: Byte = 0x03
}
