package app.thumbcast.android.overlay

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice

/**
 * Wykrywanie fizycznego pada (BT/USB) podłączonego do telefonu. Używa `InputManager.InputDeviceListener`,
 * więc reaguje na podłączenie, odłączenie i zmianę urządzenia bez odpytywania.
 *
 * Pad = urządzenie z `SOURCE_GAMEPAD` albo `SOURCE_JOYSTICK`, niewirtualne (wirtualne urządzenia, np. wstrzykiwane
 * przez system albo aplikacje przez uinput, nie są padem gracza), na API 29+ dodatkowo `isExternal`
 * (wbudowane sterowanie konsol przenośnych się nie liczy). Reguła: [isPhysicalGamepad].
 *
 * Callback [Listener.onPresenceChanged] przychodzi tylko przy zmianie stanu, na wątku, który wołał [start]
 * (looper UI). Klasa nie zna profilu ani overlaya: co zrobić z informacją (np. `global.autoHideOnGamepad`),
 * decyduje użytkownik. [start]/[stop] są idempotentne; wywołania z wątku UI.
 */
class GamepadPresenceMonitor internal constructor(
    private val inputManager: InputManager?,
    private val listener: Listener,
    private val scanner: Scanner,
) {

    constructor(context: Context, listener: Listener) :
        this(context.getSystemService(InputManager::class.java), listener, Scanner { anyPhysicalGamepadConnected() })

    fun interface Listener {
        fun onPresenceChanged(present: Boolean)
    }

    /** Źródło stanu (podmieniane w testach). */
    fun interface Scanner {
        fun anyPhysicalGamepad(): Boolean
    }

    /** Ostatni znany stan (aktualizowany przez [refresh] i zdarzenia urządzeń po [start]). */
    var isPresent: Boolean = false
        private set

    var isStarted: Boolean = false
        private set

    private val deviceListener: InputManager.InputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refresh()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refresh()
        }
    }

    /** Rejestruje nasłuch i od razu sprawdza stan (callback, jeśli różny od [isPresent]). */
    fun start() {
        if (isStarted) return
        isStarted = true
        inputManager?.registerInputDeviceListener(deviceListener, null)
        refresh()
    }

    /** Wyrejestrowuje nasłuch. [isPresent] zostaje (ostatni znany stan). */
    fun stop() {
        if (!isStarted) return
        isStarted = false
        inputManager?.unregisterInputDeviceListener(deviceListener)
    }

    /** Ponowne sprawdzenie urządzeń; callback tylko przy zmianie. */
    fun refresh() {
        val now: Boolean = scanner.anyPhysicalGamepad()
        if (now == isPresent) return
        isPresent = now
        listener.onPresenceChanged(now)
    }

    companion object {

        /** Czysta reguła „to jest fizyczny pad gracza” (testowana na JVM). */
        @JvmStatic
        fun isPhysicalGamepad(sources: Int, isVirtual: Boolean, isExternal: Boolean, sdkInt: Int): Boolean {
            if (isVirtual) return false
            val isPad: Boolean = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isPad) return false
            if (sdkInt >= Build.VERSION_CODES.Q && !isExternal) return false
            return true
        }

        @JvmStatic
        fun isPhysicalGamepad(dev: InputDevice): Boolean {
            val external: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dev.isExternal else true
            return isPhysicalGamepad(dev.sources, dev.isVirtual, external, Build.VERSION.SDK_INT)
        }

        /** Jednorazowe sprawdzenie wszystkich urządzeń wejścia (bez nasłuchu). */
        @JvmStatic
        fun anyPhysicalGamepadConnected(): Boolean {
            val ids: IntArray = InputDevice.getDeviceIds() ?: return false
            for (id in ids) {
                val dev: InputDevice = InputDevice.getDevice(id) ?: continue
                if (isPhysicalGamepad(dev)) return true
            }
            return false
        }
    }
}
