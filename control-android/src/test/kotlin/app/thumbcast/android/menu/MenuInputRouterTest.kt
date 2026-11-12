package app.thumbcast.android.menu

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wejście z klawiatury i pada przy menu w grze. Stałe `KeyEvent.*` są stałymi kompilacji (Kotlin je wkleja),
 * więc test działa na czystym JVM.
 */
class MenuInputRouterTest {

    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP
    private val pad = 7
    private val keyboard = 3

    private fun result(route: MenuKeyRoute, command: MenuCommand = MenuCommand.NONE) = MenuKeyResult(route, command)

    @Test
    fun closedMenuPassesEverything() {
        val r = MenuInputRouter()
        assertEquals(MenuKeyResult.PASS, r.onKey(false, down, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
        assertEquals(MenuKeyResult.PASS, r.onKey(false, up, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
        assertEquals(MenuKeyResult.PASS, r.onKey(false, down, KeyEvent.KEYCODE_W, 0, keyboard))
    }

    @Test
    fun openMenuConsumesAndMapsNavigation() {
        val r = MenuInputRouter()
        val cases = listOf(
            KeyEvent.KEYCODE_DPAD_UP to MenuCommand.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to MenuCommand.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to MenuCommand.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to MenuCommand.RIGHT,
            KeyEvent.KEYCODE_BUTTON_A to MenuCommand.ACTIVATE,
            KeyEvent.KEYCODE_ENTER to MenuCommand.ACTIVATE,
            KeyEvent.KEYCODE_DPAD_CENTER to MenuCommand.ACTIVATE,
            KeyEvent.KEYCODE_BUTTON_B to MenuCommand.BACK,
            KeyEvent.KEYCODE_BACK to MenuCommand.BACK,
            KeyEvent.KEYCODE_ESCAPE to MenuCommand.BACK,
            KeyEvent.KEYCODE_BUTTON_X to MenuCommand.NONE,
            KeyEvent.KEYCODE_BUTTON_R1 to MenuCommand.NONE,
            KeyEvent.KEYCODE_W to MenuCommand.NONE,
        )
        for ((code, cmd) in cases) {
            assertEquals("down $code", result(MenuKeyRoute.CONSUME, cmd), r.onKey(true, down, code, 0, keyboard))
            assertEquals("up $code", MenuKeyResult.CONSUME, r.onKey(true, up, code, 0, keyboard))
        }
    }

    @Test
    fun systemKeysGoToSystem() {
        val r = MenuInputRouter()
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER)) {
            assertEquals(MenuKeyResult.SYSTEM, r.onKey(true, down, code, 0, keyboard))
            assertEquals(MenuKeyResult.SYSTEM, r.onKey(true, up, code, 0, keyboard))
        }
    }

    @Test
    fun releaseOfKeyPressedInMenuIsConsumedAfterClose() {
        val r = MenuInputRouter()
        // A wybiera „Wróć do gry”: menu zamyka się przed puszczeniem A.
        assertEquals(result(MenuKeyRoute.CONSUME, MenuCommand.ACTIVATE), r.onKey(true, down, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
        assertEquals(MenuKeyResult.CONSUME, r.onKey(false, down, KeyEvent.KEYCODE_BUTTON_A, 1, pad))
        assertEquals(MenuKeyResult.CONSUME, r.onKey(false, up, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
        // Następne wciśnięcie A po zamknięciu menu idzie do hosta.
        assertEquals(MenuKeyResult.PASS, r.onKey(false, down, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
        assertEquals(MenuKeyResult.PASS, r.onKey(false, up, KeyEvent.KEYCODE_BUTTON_A, 0, pad))
    }

    @Test
    fun newPressAfterLostReleasePasses() {
        val r = MenuInputRouter()
        r.onKey(true, down, KeyEvent.KEYCODE_BUTTON_B, 0, pad)
        assertEquals(MenuKeyResult.PASS, r.onKey(false, down, KeyEvent.KEYCODE_BUTTON_B, 0, pad))
        assertEquals(MenuKeyResult.PASS, r.onKey(false, up, KeyEvent.KEYCODE_BUTTON_B, 0, pad))
    }

    @Test
    fun keyHeldBeforeMenuReleasesOnHost() {
        val r = MenuInputRouter()
        // W trzymane w grze; menu otwiera się dotykiem; powtórzenia są zjadane, puszczenie idzie do hosta.
        assertEquals(MenuKeyResult.PASS, r.onKey(false, down, KeyEvent.KEYCODE_W, 0, keyboard))
        assertEquals(MenuKeyResult.CONSUME, r.onKey(true, down, KeyEvent.KEYCODE_W, 5, keyboard))
        assertEquals(MenuKeyResult.PASS, r.onKey(true, up, KeyEvent.KEYCODE_W, 0, keyboard))
        // Strzałka trzymana od gry nie przewija menu powtórzeniami.
        assertEquals(MenuKeyResult.CONSUME, r.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 3, keyboard))
        assertEquals(MenuKeyResult.PASS, r.onKey(true, up, KeyEvent.KEYCODE_DPAD_DOWN, 0, keyboard))
    }

    @Test
    fun repeatsMoveButDoNotReactivate() {
        val r = MenuInputRouter()
        assertEquals(MenuCommand.DOWN, r.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 0, keyboard).command)
        assertEquals(MenuCommand.DOWN, r.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 1, keyboard).command)
        assertEquals(MenuCommand.ACTIVATE, r.onKey(true, down, KeyEvent.KEYCODE_ENTER, 0, keyboard).command)
        assertEquals(MenuCommand.NONE, r.onKey(true, down, KeyEvent.KEYCODE_ENTER, 1, keyboard).command)
        assertEquals(MenuCommand.BACK, r.onKey(true, down, KeyEvent.KEYCODE_ESCAPE, 0, keyboard).command)
        assertEquals(MenuCommand.NONE, r.onKey(true, down, KeyEvent.KEYCODE_ESCAPE, 1, keyboard).command)
    }

    @Test
    fun multipleIsConsumedWhileOpen() {
        val r = MenuInputRouter()
        assertEquals(MenuKeyResult.CONSUME, r.onKey(true, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN, 0, keyboard))
        assertEquals(MenuKeyResult.PASS, r.onKey(false, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN, 0, keyboard))
    }

    @Test
    fun hatGivesOneCommandPerPress() {
        val r = MenuInputRouter()
        assertEquals(listOf(MenuCommand.DOWN), r.onAxes(pad, 0f, 1f, 0f, 0f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 1f, 0f, 0f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0f))
        assertEquals(listOf(MenuCommand.UP), r.onAxes(pad, 0f, -1f, 0f, 0f))
        assertEquals(listOf(MenuCommand.RIGHT), r.onAxes(pad, 1f, 0f, 0f, 0f))
        assertEquals(listOf(MenuCommand.LEFT), r.onAxes(pad, -1f, 0f, 0f, 0f))
    }

    @Test
    fun stickUsesHysteresis() {
        val r = MenuInputRouter()
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0.4f))
        assertEquals(listOf(MenuCommand.DOWN), r.onAxes(pad, 0f, 0f, 0f, 0.6f))
        // Drżenie wokół progu wejścia nie daje kolejnych kroków.
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0.45f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0.7f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0.35f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, 0f, 0.2f))
        assertEquals(listOf(MenuCommand.DOWN), r.onAxes(pad, 0f, 0f, 0f, 0.8f))
        assertEquals(listOf(MenuCommand.UP), r.onAxes(pad, 0f, 0f, 0f, -0.9f))
        assertEquals(emptyList<MenuCommand>(), r.onAxes(pad, 0f, 0f, Float.NaN, Float.NaN))
    }

    @Test
    fun hatAndDpadKeysFromOneDeviceGiveOneCommand() {
        // Hat pierwszy: klawisze D-pad z tego pada są duplikatami.
        val a = MenuInputRouter()
        assertEquals(listOf(MenuCommand.DOWN), a.onAxes(pad, 0f, 1f, 0f, 0f))
        assertEquals(result(MenuKeyRoute.CONSUME), a.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 0, pad))
        // Klawiatura (inne urządzenie) nadal przewija strzałkami.
        assertEquals(MenuCommand.DOWN, a.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 0, keyboard).command)

        // Klawisze pierwsze: hat z tego pada jest ignorowany, lewa gałka działa.
        val b = MenuInputRouter()
        assertEquals(MenuCommand.DOWN, b.onKey(true, down, KeyEvent.KEYCODE_DPAD_DOWN, 0, pad).command)
        assertEquals(emptyList<MenuCommand>(), b.onAxes(pad, 0f, 1f, 0f, 0f))
        assertEquals(listOf(MenuCommand.UP), b.onAxes(pad, 0f, 0f, 0f, -1f))
    }

    @Test
    fun resetAxesForgetsDirection() {
        val r = MenuInputRouter()
        assertEquals(listOf(MenuCommand.DOWN), r.onAxes(pad, 0f, 1f, 0f, 0f))
        r.resetAxes()
        assertEquals(listOf(MenuCommand.DOWN), r.onAxes(pad, 0f, 1f, 0f, 0f))
    }

    @Test
    fun focusMovesAndWrapsAround() {
        assertEquals(-1, MenuFocus.move(-1, 1, 0, 0))
        // Pierwsze polecenie: wiersz domyślny bez ruchu.
        assertEquals(5, MenuFocus.move(-1, 1, 7, 5))
        assertEquals(5, MenuFocus.move(-1, -1, 7, 5))
        assertEquals(6, MenuFocus.move(5, 1, 7, 5))
        assertEquals(4, MenuFocus.move(5, -1, 7, 5))
        // Zawijanie: z ostatniego w dół na pierwszy, z pierwszego w górę na ostatni.
        assertEquals(0, MenuFocus.move(6, 1, 7, 5))
        assertEquals(6, MenuFocus.move(0, -1, 7, 5))
        // Jeden wiersz: zostaje na miejscu.
        assertEquals(0, MenuFocus.move(0, 1, 1, 0))
        assertEquals(0, MenuFocus.move(0, -1, 1, 0))
        // Indeks spoza listy (lista się skróciła) i domyślny spoza zakresu.
        assertEquals(2, MenuFocus.move(9, 1, 3, 2))
        assertTrue(MenuFocus.move(-1, 1, 3, 9) == 2)
    }

    @Test
    fun stepClampsWithoutWrapping() {
        assertEquals(-1, MenuFocus.step(0, 1, 0))
        assertEquals(1, MenuFocus.step(0, 1, 3))
        assertEquals(2, MenuFocus.step(2, 1, 3))
        assertEquals(0, MenuFocus.step(0, -1, 3))
        assertEquals(1, MenuFocus.step(2, -1, 3))
    }

    @Test
    fun heldVerticalFollowsLatchedAxes() {
        val r = MenuInputRouter()
        assertEquals(MenuCommand.NONE, r.heldVertical())
        r.onAxes(pad, 0f, 1f, 0f, 0f)
        assertEquals(MenuCommand.DOWN, r.heldVertical())
        r.onAxes(pad, 0f, 0f, 0f, 0f)
        assertEquals(MenuCommand.NONE, r.heldVertical())
        // Gałka z histerezą: trzymana powyżej progu wyjścia nadal daje kierunek.
        r.onAxes(pad, 0f, 0f, 0f, -0.9f)
        assertEquals(MenuCommand.UP, r.heldVertical())
        r.onAxes(pad, 0f, 0f, 0f, -0.4f)
        assertEquals(MenuCommand.UP, r.heldVertical())
        r.onAxes(pad, 0f, 0f, 0f, -0.1f)
        assertEquals(MenuCommand.NONE, r.heldVertical())
        // Sam kierunek poziomy nie przewija listy.
        r.onAxes(pad, 1f, 0f, 0f, 0f)
        assertEquals(MenuCommand.NONE, r.heldVertical())
        r.onAxes(pad, 0f, 1f, 0f, 0f)
        r.resetAxes()
        assertEquals(MenuCommand.NONE, r.heldVertical())
    }
}
