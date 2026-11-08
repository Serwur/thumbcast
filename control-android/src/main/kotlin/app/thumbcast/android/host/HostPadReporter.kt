package app.thumbcast.android.host

/**
 * Fasada wysyłania stanu wirtualnego pada.
 *
 * Odzwierciedla `com.limelight.binding.input.ControllerHandler.reportOscState(int buttonFlags,
 * short leftStickX, short leftStickY, short rightStickX, short rightStickY, byte leftTrigger, byte rightTrigger)`
 * z upstreamu v12.2 (plan, sekcja 2.2). Upstream zapisuje stan do kontekstu 0 (łączonego z padem
 * fizycznym przez OR / maxByMagnitude) i wysyła pakiet.
 *
 * Retransmisja tego samego stanu po 25/50/75 ms istnieje w upstreamie w `VirtualController`,
 * NIE w `reportOscState` (plan 2.2). Czy implementacja w `patches/` ma ją powtórzyć, opisuje
 * `patches/APPLY.md` (otwarte pytanie).
 */
interface HostPadReporter {
    fun reportOscState(
        buttonFlags: Int,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
        leftTrigger: Byte,
        rightTrigger: Byte,
    )
}
