package app.thumbcast.android.sink

import app.thumbcast.android.host.HostPadReporter

/**
 * Adapter stanu pada: `InputSink.padState(...)` -> `ControllerHandler.reportOscState(...)`.
 *
 * Kolejność parametrów różni się: silnik podaje (przyciski, gałki, triggery), a upstream
 * oczekuje (przyciski, lsX, lsY, rsX, rsY, lt, rt). Tu nie ma różnicy, ale trzymamy jawne
 * nazwy, żeby nie pomylić osi.
 *
 * Deduplikacja: identyczny stan nie jest wysyłany ponownie (silnik i tak wysyła tylko zmiany,
 * a upstream ma własną retransmisję).
 *
 * [reset] wysyła stan zerowy tylko wtedy, gdy wcześniej wysłano stan niezerowy. Dzięki temu profil
 * KBM (który nigdy nie dotyka pada) nie generuje pakietów pada, bo na hoście bez sterownika pada
 * mogłoby to próbować utworzyć wirtualny kontroler.
 */
class PadSink(private val reporter: HostPadReporter) {

    private var hasLast: Boolean = false
    private var lastButtons: Int = 0
    private var lastLsX: Short = 0
    private var lastLsY: Short = 0
    private var lastRsX: Short = 0
    private var lastRsY: Short = 0
    private var lastLt: Byte = 0
    private var lastRt: Byte = 0

    /** Ostatnio wysłane flagi przycisków (dla nakładki debug). */
    val lastSentButtons: Int
        get() = lastButtons

    /** Zwraca true, jeśli pakiet został faktycznie wysłany. */
    fun padState(
        buttons: Int,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
        leftTrigger: Byte,
        rightTrigger: Byte,
    ): Boolean {
        val isZero: Boolean = buttons == 0 && leftStickX.toInt() == 0 && leftStickY.toInt() == 0 &&
            rightStickX.toInt() == 0 && rightStickY.toInt() == 0 && leftTrigger.toInt() == 0 && rightTrigger.toInt() == 0
        if (!hasLast && isZero) {
            // Pierwszy pakiet pada nie może być pusty: pad „pojawia się” dopiero przy realnym wejściu.
            return false
        }
        if (hasLast &&
            buttons == lastButtons &&
            leftStickX == lastLsX && leftStickY == lastLsY &&
            rightStickX == lastRsX && rightStickY == lastRsY &&
            leftTrigger == lastLt && rightTrigger == lastRt
        ) {
            return false
        }
        hasLast = true
        lastButtons = buttons
        lastLsX = leftStickX
        lastLsY = leftStickY
        lastRsX = rightStickX
        lastRsY = rightStickY
        lastLt = leftTrigger
        lastRt = rightTrigger
        reporter.reportOscState(buttons, leftStickX, leftStickY, rightStickX, rightStickY, leftTrigger, rightTrigger)
        return true
    }

    /** Release all: stan zerowy, jeśli ostatnio wysłany stan był niezerowy. */
    fun reset() {
        val zeroS: Short = 0
        val zeroB: Byte = 0
        padState(0, zeroS, zeroS, zeroS, zeroS, zeroB, zeroB)
    }

    /** Czy ostatnio wysłany stan był niezerowy (dla testów i debug). */
    val isActive: Boolean
        get() = hasLast && (lastButtons != 0 || lastLsX.toInt() != 0 || lastLsY.toInt() != 0 ||
            lastRsX.toInt() != 0 || lastRsY.toInt() != 0 || lastLt.toInt() != 0 || lastRt.toInt() != 0)
}
