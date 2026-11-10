package app.thumbcast.android.host

/**
 * Fasada wysyłania stanu wirtualnego pada.
 *
 * Odzwierciedla `com.limelight.binding.input.ControllerHandler.reportOscState(int buttonFlags,
 * short leftStickX, short leftStickY, short rightStickX, short rightStickY, byte leftTrigger, byte rightTrigger)`
 * z upstreamu v12.2 (commit b48494cb96bf; sygnatura zweryfikowana, metoda `public void`).
 * Upstream zapisuje stan do `defaultContext` (kontroler nr 0), nadpisując całą mapę przycisków
 * (`inputMap = buttonFlags`), a `sendControllerInputPacket` łączy go z padami fizycznymi
 * (OR przycisków, `maxByMagnitude` osi) i wysyła pakiet. Metoda nie ma blokad ani wątków.
 *
 * Retransmisja tego samego stanu po 25/50/75 ms istnieje w upstreamie w
 * `VirtualController.sendControllerInputContext()` (zweryfikowane), NIE w `reportOscState`.
 * Powtarza ją więc `HostPadReporterImpl` w `patches/` (silnik Thumbcast jej nie robi).
 *
 * UWAGA: pakiet pada 0 jest sensowny dla hosta tylko wtedy, gdy bit 0 maski pada jest ustawiony
 * (`getAttachedControllerMask` i `getActiveControllerMask` robią to wyłącznie przy
 * `prefConfig.onscreenController == true`). Patche wymuszają tę flagę w trybie Thumbcast.
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
