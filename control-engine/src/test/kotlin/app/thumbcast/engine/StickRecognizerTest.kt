package app.thumbcast.engine

import app.thumbcast.model.Control
import app.thumbcast.model.CurveType
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.StickControl
import app.thumbcast.model.StickKeys
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickRecognizerTest {

    private val linear: CurveSpec = CurveSpec(CurveType.LINEAR, 1f)

    @Test
    fun deadzoneGivesZero() {
        val v = StickMath.shape(0.10f, 0f, 0.12f, 0.04f, linear)
        assertEquals(0f, v[0])
        assertEquals(0f, v[1])
    }

    @Test
    fun rescaledDeadzoneHasNoJumpAtEdge() {
        // Tuż za martwą strefą wyjście jest bliskie zeru (brak skoku).
        val v = StickMath.shape(0.121f, 0f, 0.12f, 0.04f, linear)
        assertTrue(v[0] > 0f && v[0] < 0.01f, "wyjście tuż za strefą: ${v[0]}")
        // Połowa zakresu: (0.5 - 0.12) / 0.84.
        val half = StickMath.shape(0.5f, 0f, 0.12f, 0.04f, linear)
        assertEquals(0.38f / 0.84f, half[0], 1e-4f)
    }

    @Test
    fun outerDeadzoneSaturates() {
        val v = StickMath.shape(0.97f, 0f, 0.12f, 0.04f, linear)
        assertEquals(1f, v[0], 1e-6f)
        val over = StickMath.shape(3f, 4f, 0.12f, 0.04f, linear)
        assertEquals(1f, hypot(over[0], over[1]), 1e-5f)
        assertEquals(0.6f, over[0], 1e-5f) // kierunek zachowany
    }

    @Test
    fun powerCurve() {
        assertEquals(0.25f, StickMath.applyCurve(0.5f, CurveSpec(CurveType.POWER, 2f)), 1e-6f)
        assertEquals(0.5f, StickMath.applyCurve(0.5f, linear), 1e-6f)
        assertEquals(1f, StickMath.applyCurve(1.5f, CurveSpec(CurveType.POWER, 2f)), 1e-6f)
    }

    @Test
    fun eightDirections() {
        val k = StickKeys("W", "S", "A", "D")
        assertEquals(setOf("D"), StickMath.directionKeys(1f, 0f, 0.35f, k))
        assertEquals(setOf("W", "D"), StickMath.directionKeys(0.7f, 0.7f, 0.35f, k))
        assertEquals(setOf("W"), StickMath.directionKeys(0f, 1f, 0.35f, k))
        assertEquals(setOf("S", "A"), StickMath.directionKeys(-0.7f, -0.7f, 0.35f, k))
        assertEquals(emptySet<String>(), StickMath.directionKeys(0.3f, 0f, 0.35f, k))
    }

    @Test
    fun floatingStickFullDeflectionAndRelease() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        assertEquals(0, r.sink.records.size, "środek pływającej gałki = zero, nic nie wysyłamy")
        // Promień pływającej gałki = 0.15 * 1000 px = 150 px.
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(32767, r.sink.lastPad!!.lx.toInt())
        assertEquals(0, r.sink.lastPad!!.ly.toInt())
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 150f) // w górę ekranu = gałka w górę (dodatnie Y)
        assertEquals(0, r.sink.lastPad!!.lx.toInt())
        assertEquals(32767, r.sink.lastPad!!.ly.toInt())
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun floatingStickDeadzoneAndHalf() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 15f, Pts.STICK_Y) // 0.10 < 0.12
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size)
        r.move(1, Pts.STICK_X + 75f, Pts.STICK_Y) // 0.5
        val expected = Math.round(0.38f / 0.84f * 32767f)
        assertTrue(abs(r.sink.lastPad!!.lx.toInt() - expected) <= 1)
    }

    @Test
    fun captureOnDownKeepsStickOutsideRect() {
        val r = Rig()
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        // Palec wyjeżdża daleko poza rect gałki (nad przycisk A): nadal steruje gałką, A nie jest wciśnięte.
        r.move(1, Pts.A_X, Pts.A_Y)
        assertTrue(r.sink.lastPad!!.lx.toInt() > 32000)
        assertEquals(0, r.sink.lastPad!!.buttons)
    }

    @Test
    fun kbmStickEmitsWasdWithEightDirections() {
        val r = Rig(testProfile(mode = ProfileMode.KEYBOARD_MOUSE))
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(setOf("D"), r.sink.keysDown)
        r.move(1, Pts.STICK_X + 106f, Pts.STICK_Y - 106f)
        assertEquals(setOf("D", "W"), r.sink.keysDown)
        r.move(1, Pts.STICK_X - 150f, Pts.STICK_Y)
        assertEquals(setOf("A"), r.sink.keysDown)
        // Zwolnienia przed wciśnięciami: po "W+D" najpierw idą W- i D-, dopiero potem A+.
        val keys = r.sink.ofCall(SinkCall.KEY)
        val lastThree = keys.subList(keys.size - 3, keys.size)
        assertEquals(false, lastThree[0].down)
        assertEquals(false, lastThree[1].down)
        assertEquals("A", lastThree[2].code)
        assertEquals(true, lastThree[2].down)
        r.up(1)
        assertTrue(r.sink.keysDown.isEmpty())
        assertEquals(0, r.sink.ofCall(SinkCall.PAD).size, "w KBM gałka nie wysyła stanu pada")
    }

    // ------------------------------------------------------------------ sprintLock (histereza nasycenia, 11.11.2026)
    //
    // Gałka pływająca: promień 150 px, martwa strefa 0.12 (18 px), nasycenie od 1 - 0.04 = 0.96 (144 px).

    /** Profil testowy z gałką „move” w wariancie sprintLock (opcjonalnie stałą zamiast pływającej). */
    private fun sprintProfile(
        sprintLock: Boolean = true,
        mode: ProfileMode = ProfileMode.PAD_PLUS,
        floating: Boolean = true
    ): Profile {
        val kbm = mode == ProfileMode.KEYBOARD_MOUSE
        val controls = ArrayList<Control>()
        for (c in testControls(kbm)) {
            controls.add(if (c is StickControl) c.copy(sprintLock = sprintLock, floating = floating) else c)
        }
        return testProfile(mode = mode, controls = controls)
    }

    private fun lx(r: Rig): Int = r.sink.lastPad!!.lx.toInt()
    private fun ly(r: Rig): Int = r.sink.lastPad!!.ly.toInt()
    private fun mag(r: Rig): Double = hypot(lx(r).toDouble(), ly(r).toDouble())

    @Test
    fun sprintLockEntersOnlyAtSaturation() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 100f) // 0.667: przed nasyceniem zwykła krzywa
        val partial = Math.round((100f / 150f - 0.12f) / 0.84f * 32767f)
        assertTrue(abs(ly(r) - partial) <= 1, "przed nasyceniem: ${ly(r)} zamiast $partial")
        // Powrót bez nasycenia: brak histerezy, wychylenie maleje.
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 60f)
        assertTrue(ly(r) in 1 until partial, "bez wejścia w sprint wychylenie maleje: ${ly(r)}")
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 145f) // 0.967 ≥ 0.96: nasycenie
        assertEquals(32767, ly(r))
        assertEquals(0, lx(r))
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockKeepsFullDeflectionWithUpdatedDirectionWhenThumbDriftsBack() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 200f) // poza obrysem: sprint
        assertEquals(32767, ly(r))
        // Kciuk cofa się do środka obrysu i skręca: (30, 40) px = 0.2, 0.267 (|v| = 0.333).
        r.move(1, Pts.STICK_X + 30f, Pts.STICK_Y - 40f)
        assertEquals(Math.round(0.6f * 32767f), lx(r), "kierunek za palcem (x)")
        assertEquals(Math.round(0.8f * 32767f), ly(r), "kierunek za palcem (y)")
        assertEquals(32767.0, mag(r), 2.0)
        // Tuż przy martwej strefie (20 px = 0.133 > 0.12) nadal 100%, w lewo.
        r.move(1, Pts.STICK_X - 20f, Pts.STICK_Y)
        assertEquals(-32767, lx(r))
        assertEquals(0, ly(r))
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockExitsInDeadzoneAndRepushReenters() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(32767, lx(r))
        r.move(1, Pts.STICK_X + 10f, Pts.STICK_Y) // 0.067 ≤ 0.12: koniec sprintu, wyjście 0
        assertEquals(0, lx(r))
        assertEquals(0, ly(r))
        // Dalej zwykła krzywa: połowa obrysu daje (0.5 - 0.12) / 0.84.
        r.move(1, Pts.STICK_X + 75f, Pts.STICK_Y)
        val half = Math.round(0.38f / 0.84f * 32767f)
        assertTrue(abs(lx(r) - half) <= 1, "po wyjściu ze sprintu zwykła krzywa: ${lx(r)}")
        // Ponowne dociśnięcie do nasycenia włącza sprint, cofnięcie do 0.4 trzyma 100%.
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(32767, lx(r))
        r.move(1, Pts.STICK_X + 60f, Pts.STICK_Y)
        assertEquals(32767, lx(r))
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockEndsWithFingerUpAndDoesNotCarryToNextTouch() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 60f, Pts.STICK_Y)
        assertEquals(32767, lx(r))
        r.up(1)
        assertTrue(r.sink.isZero(), "UP zeruje gałkę")
        // Nowy dotyk zaczyna bez sprintu: 0.4 daje zwykłą krzywą.
        r.down(2, Pts.STICK_X, Pts.STICK_Y)
        r.move(2, Pts.STICK_X + 60f, Pts.STICK_Y)
        val expected = Math.round((0.4f - 0.12f) / 0.84f * 32767f)
        assertTrue(abs(lx(r) - expected) <= 1, "nowy dotyk bez sprintu: ${lx(r)}")
        r.up(2)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockEndsWithCancelAndReleaseAll() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 150f)
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 60f)
        assertEquals(32767, ly(r))
        r.cancel()
        assertTrue(r.sink.isZero(), "CANCEL zeruje gałkę")
        r.down(2, Pts.STICK_X, Pts.STICK_Y)
        r.move(2, Pts.STICK_X, Pts.STICK_Y - 60f)
        assertTrue(ly(r) in 1 until 32767, "po CANCEL bez sprintu: ${ly(r)}")
        r.move(2, Pts.STICK_X, Pts.STICK_Y - 150f)
        r.move(2, Pts.STICK_X, Pts.STICK_Y - 60f)
        assertEquals(32767, ly(r))
        r.engine.releaseAll()
        assertTrue(r.sink.isZero(), "releaseAll zeruje gałkę")
        assertEquals(PadSnapshot.ZERO, r.engine.padSnapshot())
        r.down(3, Pts.STICK_X, Pts.STICK_Y)
        r.move(3, Pts.STICK_X, Pts.STICK_Y - 60f)
        assertTrue(ly(r) in 1 until 32767, "po releaseAll bez sprintu: ${ly(r)}")
        r.up(3)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockEmitsNothingOnTicks() {
        val r = Rig(sprintProfile())
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 60f, Pts.STICK_Y)
        val before = r.sink.records.size
        repeat(20) {
            r.advance(16)
            r.tick()
        }
        assertEquals(before, r.sink.records.size, "tick nie zmienia gałki w sprincie")
        assertEquals(32767, lx(r))
        // Każda zmiana gałki ma przyczynę w zdarzeniu dotyku tego palca.
        for (rec in r.sink.ofCall(SinkCall.PAD)) {
            val c = rec.cause!!
            assertEquals(CycleKind.TOUCH, c.cycle)
            assertEquals(1, c.pointerId)
        }
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockWorksTheSameOnFixedStick() {
        // Stała gałka: środek rect (320, 700), promień min(480, 500) / 2 = 240 px; martwa strefa 28.8 px.
        val r = Rig(sprintProfile(floating = false))
        r.down(1, 320f, 700f)
        r.move(1, 320f, 700f - 240f)
        assertEquals(32767, ly(r))
        r.move(1, 320f + 60f, 700f - 80f) // 0.25, 0.333: 100% w kierunku (0.6, 0.8)
        assertEquals(Math.round(0.6f * 32767f), lx(r))
        assertEquals(Math.round(0.8f * 32767f), ly(r))
        r.move(1, 320f + 20f, 700f) // 0.083: martwa strefa, koniec sprintu
        assertEquals(0, lx(r))
        assertEquals(0, ly(r))
        r.move(1, 320f + 120f, 700f) // 0.5: zwykła krzywa
        assertTrue(abs(lx(r) - Math.round(0.38f / 0.84f * 32767f)) <= 1)
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    @Test
    fun sprintLockOffKeepsNormalCurveAfterSaturation() {
        val r = Rig(sprintProfile(sprintLock = false))
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
        assertEquals(32767, lx(r))
        r.move(1, Pts.STICK_X + 60f, Pts.STICK_Y)
        val expected = Math.round((0.4f - 0.12f) / 0.84f * 32767f)
        assertTrue(abs(lx(r) - expected) <= 1, "bez sprintLock zwykła krzywa po nasyceniu: ${lx(r)}")
        r.up(1)
        assertTrue(r.sink.isZero())
    }

    @Test
    fun sprintLockInKbmKeepsDirectionKeysUntilDeadzone() {
        // KBM: 100% = klawisze kierunku trzymane. Próg 0.35 po krzywej odpowiada |v| = 0.414 (62 px).
        val off = Rig(sprintProfile(sprintLock = false, mode = ProfileMode.KEYBOARD_MOUSE))
        val on = Rig(sprintProfile(sprintLock = true, mode = ProfileMode.KEYBOARD_MOUSE))
        for (r in listOf(off, on)) {
            r.down(1, Pts.STICK_X, Pts.STICK_Y)
            r.move(1, Pts.STICK_X + 150f, Pts.STICK_Y)
            assertEquals(setOf("D"), r.sink.keysDown)
            r.move(1, Pts.STICK_X + 40f, Pts.STICK_Y) // 0.267: poniżej progu
        }
        assertEquals(emptySet<String>(), off.sink.keysDown, "bez sprintLock klawisz puszczony poniżej progu")
        assertEquals(setOf("D"), on.sink.keysDown, "sprint trzyma D poniżej progu")
        // Kierunek za palcem: skos w górę-prawo blisko środka (|v| = 0.189 > 0.12).
        on.move(1, Pts.STICK_X + 20f, Pts.STICK_Y - 20f)
        assertEquals(setOf("D", "W"), on.sink.keysDown)
        // Martwa strefa kończy sprint: wszystko puszczone, potem zwykły próg.
        on.move(1, Pts.STICK_X + 10f, Pts.STICK_Y)
        assertTrue(on.sink.keysDown.isEmpty())
        on.move(1, Pts.STICK_X + 40f, Pts.STICK_Y)
        assertTrue(on.sink.keysDown.isEmpty(), "po wyjściu ze sprintu zwykły próg")
        for (r in listOf(off, on)) {
            r.up(1)
            assertTrue(r.sink.keysDown.isEmpty())
            assertEquals(0, r.sink.ofCall(SinkCall.PAD).size)
            assertEquals(0, r.engine.violations.size)
        }
    }
}
