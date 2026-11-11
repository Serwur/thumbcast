package app.thumbcast.android.menu

import android.view.KeyEvent
import kotlin.math.abs

/** Polecenie nawigacji menu w grze (klawiatura albo pad). */
enum class MenuCommand {
    NONE,
    UP,
    DOWN,
    LEFT,
    RIGHT,

    /** A, Enter, środek D-pada, spacja. */
    ACTIVATE,

    /** B, Back, Escape. */
    BACK,
}

/** Dokąd idzie zdarzenie klawisza. */
enum class MenuKeyRoute {
    /** Zwykła ścieżka `Game` (host). */
    PASS,

    /** Zjedzone przez menu: nic nie trafia do hosta. */
    CONSUME,

    /** Do systemu (głośność, zasilanie): `Game` zwraca false bez wysyłania do hosta. */
    SYSTEM,
}

/** Wynik [MenuInputRouter.onKey]: trasa i polecenie dla menu (NONE, gdy nic nie robić). */
data class MenuKeyResult(val route: MenuKeyRoute, val command: MenuCommand) {
    companion object {
        @JvmField val PASS: MenuKeyResult = MenuKeyResult(MenuKeyRoute.PASS, MenuCommand.NONE)
        @JvmField val CONSUME: MenuKeyResult = MenuKeyResult(MenuKeyRoute.CONSUME, MenuCommand.NONE)
        @JvmField val SYSTEM: MenuKeyResult = MenuKeyResult(MenuKeyRoute.SYSTEM, MenuCommand.NONE)
    }
}

/**
 * Wejście fizyczne (klawiatura, pad) przy menu w grze, bez API widoków (testy JVM w `MenuInputRouterTest`).
 *
 * Zasady (12.11.2026):
 * - Menu otwarte: każde wciśnięcie klawisza albo przycisku pada jest zjadane (nic nie idzie do hosta); D-pad,
 *   strzałki, A/Enter i B/Back/Escape dają [MenuCommand]. Wyjątek: klawisze systemowe ([isSystemKey], głośność,
 *   zasilanie) idą do systemu.
 * - Puszczenie klawisza, którego wciśnięcie zjadło menu, jest też zjadane, także po zamknięciu menu (A, które
 *   wybrało „Wróć do gry”, nie dociera do hosta ani jako puszczenie, ani jako powtórzenie wciśnięcia).
 * - Puszczenie klawisza wciśniętego przed otwarciem menu idzie zwykłą ścieżką do hosta: klawisz klawiatury nie
 *   zostaje trzymany na hoście (stan pada sesja i tak zeruje przy otwarciu menu).
 * - Ruch osi pada przy otwartym menu ([onAxes]) jest zjadany przez wołającego; krzyżak (hat) i lewa gałka dają
 *   kierunek z histerezą: jedno polecenie na wychylenie, bez powtarzania w czasie (żadnych timerów).
 * - Pady, które zgłaszają krzyżak naraz jako hat i jako klawisze D-pad (upstream `ControllerHandler` tłumi wtedy
 *   klawisze), dają jedno polecenie: dla danego urządzenia wygrywa źródło, które pierwsze dało kierunek.
 */
class MenuInputRouter {

    /** Klawisze, których wciśnięcie zjadło menu, aż do ich puszczenia. */
    private val consumedDowns: HashSet<Int> = HashSet()

    /** Urządzenia, których krzyżak działa jako hat (klawisze D-pad z nich są duplikatami). */
    private val hatDevices: HashSet<Int> = HashSet()

    /** Urządzenia, których krzyżak działa jako klawisze D-pad (hat z nich jest duplikatem). */
    private val keyDpadDevices: HashSet<Int> = HashSet()

    private val axes: HashMap<Int, AxisLatch> = HashMap()

    private class AxisLatch {
        var stickX: Int = 0
        var stickY: Int = 0
        var x: Int = 0
        var y: Int = 0
    }

    /**
     * Zdarzenie klawisza (`KeyEvent.ACTION_DOWN` / `ACTION_UP` / `ACTION_MULTIPLE`). [menuOpen] = stan menu w chwili
     * zdarzenia.
     */
    fun onKey(menuOpen: Boolean, action: Int, keyCode: Int, repeatCount: Int, deviceId: Int): MenuKeyResult {
        if (!menuOpen) {
            if (!consumedDowns.contains(keyCode)) return MenuKeyResult.PASS
            return when (action) {
                KeyEvent.ACTION_UP -> {
                    consumedDowns.remove(keyCode)
                    MenuKeyResult.CONSUME
                }
                // Powtórzenie wciśnięcia, które zaczęło się w menu: nadal należy do menu.
                KeyEvent.ACTION_DOWN -> if (repeatCount > 0) {
                    MenuKeyResult.CONSUME
                } else {
                    // Nowe wciśnięcie (zgubione puszczenie): zwykła ścieżka.
                    consumedDowns.remove(keyCode)
                    MenuKeyResult.PASS
                }
                else -> MenuKeyResult.PASS
            }
        }
        if (isSystemKey(keyCode)) return MenuKeyResult.SYSTEM
        return when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount > 0 && !consumedDowns.contains(keyCode)) {
                    // Powtórzenie klawisza trzymanego od przed otwarciem menu: zjadamy, ale jego puszczenie ma
                    // dotrzeć do hosta (inaczej klawisz zostałby wciśnięty na hoście).
                    return MenuKeyResult.CONSUME
                }
                consumedDowns.add(keyCode)
                MenuKeyResult(MenuKeyRoute.CONSUME, commandForKeyDown(keyCode, repeatCount, deviceId))
            }
            KeyEvent.ACTION_UP ->
                if (consumedDowns.remove(keyCode)) MenuKeyResult.CONSUME else MenuKeyResult.PASS
            else -> MenuKeyResult.CONSUME
        }
    }

    /**
     * Osie pada przy otwartym menu: krzyżak (`AXIS_HAT_X/Y`, -1..1) i lewa gałka (`AXIS_X/Y`). Zwraca polecenia
     * (najwyżej jedno na oś) dla przejść kierunku do nowej wartości niezerowej.
     */
    fun onAxes(deviceId: Int, hatX: Float, hatY: Float, stickX: Float, stickY: Float): List<MenuCommand> {
        val latch = axes.getOrPut(deviceId) { AxisLatch() }
        latch.stickX = hysteresis(latch.stickX, stickX)
        latch.stickY = hysteresis(latch.stickY, stickY)
        var hx = hatDir(hatX)
        var hy = hatDir(hatY)
        if (hx != 0 || hy != 0) {
            if (keyDpadDevices.contains(deviceId)) {
                hx = 0
                hy = 0
            } else {
                hatDevices.add(deviceId)
            }
        }
        val x = if (hx != 0) hx else latch.stickX
        val y = if (hy != 0) hy else latch.stickY
        val out = ArrayList<MenuCommand>(2)
        if (y != latch.y && y != 0) out.add(if (y < 0) MenuCommand.UP else MenuCommand.DOWN)
        if (x != latch.x && x != 0) out.add(if (x < 0) MenuCommand.LEFT else MenuCommand.RIGHT)
        latch.x = x
        latch.y = y
        return out
    }

    /** Menu zamknięte albo otwarte od nowa: kierunki osi od zera (puszczenia klawiszy zostają śledzone). */
    fun resetAxes() {
        axes.clear()
    }

    private fun commandForKeyDown(keyCode: Int, repeatCount: Int, deviceId: Int): MenuCommand {
        val command = commandFor(keyCode)
        when (command) {
            MenuCommand.UP, MenuCommand.DOWN, MenuCommand.LEFT, MenuCommand.RIGHT -> {
                if (isDpadKey(keyCode)) {
                    if (hatDevices.contains(deviceId)) return MenuCommand.NONE
                    keyDpadDevices.add(deviceId)
                }
                return command
            }
            MenuCommand.ACTIVATE, MenuCommand.BACK -> return if (repeatCount == 0) command else MenuCommand.NONE
            MenuCommand.NONE -> return MenuCommand.NONE
        }
    }

    companion object {
        /** Próg wejścia i wyjścia kierunku lewej gałki (histereza). */
        const val STICK_ON: Float = 0.5f
        const val STICK_OFF: Float = 0.3f

        @JvmStatic
        fun commandFor(keyCode: Int): MenuCommand = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> MenuCommand.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> MenuCommand.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> MenuCommand.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> MenuCommand.RIGHT
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> MenuCommand.ACTIVATE
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> MenuCommand.BACK
            else -> MenuCommand.NONE
        }

        /** Klawisze, które przy otwartym menu obsługuje system, a nie menu ani host. */
        @JvmStatic
        fun isSystemKey(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_BRIGHTNESS_UP,
            KeyEvent.KEYCODE_BRIGHTNESS_DOWN -> true
            else -> false
        }

        private fun isDpadKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

        private fun hatDir(v: Float): Int = when {
            v.isNaN() -> 0
            v <= -0.5f -> -1
            v >= 0.5f -> 1
            else -> 0
        }

        private fun hysteresis(current: Int, v: Float): Int {
            if (v.isNaN()) return 0
            val a = abs(v)
            if (current != 0 && a >= STICK_OFF && (v < 0f) == (current < 0)) return current
            return if (a >= STICK_ON) (if (v < 0f) -1 else 1) else 0
        }
    }
}

/** Zaznaczenie wiersza menu przy nawigacji z klawiatury albo pada (bez widoków). */
object MenuFocus {
    /**
     * Następny wiersz: [current] < 0 (jeszcze nic nie zaznaczono) daje [defaultIndex] bez ruchu, inaczej przesunięcie
     * o [delta] obcięte do zakresu (bez zawijania). Pusta lista: -1.
     */
    @JvmStatic
    fun move(current: Int, delta: Int, count: Int, defaultIndex: Int): Int {
        if (count <= 0) return -1
        if (current < 0 || current >= count) return defaultIndex.coerceIn(0, count - 1)
        return (current + delta).coerceIn(0, count - 1)
    }
}
