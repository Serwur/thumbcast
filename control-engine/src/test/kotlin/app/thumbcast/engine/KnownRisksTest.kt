package app.thumbcast.engine

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.Curve
import app.thumbcast.model.CurveType
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testy charakteryzujące trzy otwarte ryzyka z docs/HANDOFF.md („Znane ryzyka”, 11.11.2026).
 * Opisują OBECNE zachowanie. Ryzyka 1 i 2 czekają na decyzję właściciela (patrz HANDOFF.md); gdy zapadnie,
 * asercje oznaczone „ZACHOWANIE DO DECYZJI” trzeba odwrócić razem z poprawką. `sprintLock` rozstrzygnięty
 * 11.11.2026 (histereza nasycenia); test poniżej opisuje już nowe zachowanie.
 */
class KnownRisksTest {

    // ------------------------------------------------------------------ ryzyko 1: gałka RIGHT + kamera

    // Stała gałka „look” na prawej gałce pada: x 1100..1300, y 500..700 (środek 1200, 600, promień 100 px).
    private val lookStick: StickControl = StickControl(
        id = "look", rect = Rect(0.55f, 0.50f, 0.10f, 0.20f), label = null,
        output = Output.Stick(PadStick.RIGHT), floating = false, deadzone = 0.12f, outerDeadzone = 0.04f,
        curve = Curve(CurveType.LINEAR), kbmKeys = null, kbmThreshold = 0.35f
    )

    // Wolny punkt w strefie kamery (prawa połowa), z dala od kontrolek testowych.
    private val camX: Float = 1800f
    private val camY: Float = 400f

    private fun rightStickProfileRig(): Rig = Rig(testProfile(controls = testControls(false) + lookStick))

    @Test
    fun validatorAcceptsStickRightTogetherWithPadRightStickCamera() {
        // Kamera w testProfile() to PAD_RIGHT_STICK (Pad+), a gestures.cameraDrag nie ma przełącznika enabled.
        val errors = ProfileValidator.validate(testProfile(controls = testControls(false) + lookStick))
        assertTrue(errors.isEmpty(), "ZACHOWANIE DO DECYZJI: walidator dziś to przepuszcza, są błędy: $errors")
    }

    @Test
    fun cameraOverwritesStickRightAndLeavesStaleDeflectionAfterCameraFingerUp() {
        val r = rightStickProfileRig()
        // Palec 1: gałka look w prawo do oporu.
        r.down(1, 1200f, 600f)
        r.move(1, 1300f, 600f)
        assertEquals(32767, r.sink.lastPad!!.rx.toInt())
        // Palec 2: kamera w lewo. Ostatni zapis wygrywa (cicha „fuzja” przez nadpisanie).
        r.down(2, camX, camY)
        r.advance(16)
        r.move(2, camX - 40f, camY)
        val camRx = r.sink.lastPad!!.rx.toInt()
        assertTrue(camRx < 0, "kamera nadpisała gałkę look: $camRx")
        // Palec 2 w górę: zero kamery nie jest wysyłane, bo oś trzyma jeszcze palec 1 (R8, wspólny stan).
        r.up(2)
        val afterUp = r.sink.lastPad!!.rx.toInt()
        assertEquals(camRx, afterUp, "ZACHOWANIE DO DECYZJI: po puszczeniu kamery zostaje jej wychylenie, choć palec 1 trzyma +100%")
        // Dopiero ruch palca 1 albo jego UP naprawia stan.
        r.up(1)
        assertTrue(r.sink.isZero())
    }

    @Test
    fun cameraDecayAfterStickRightOverwriteIsLoggedAsViolation() {
        val r = rightStickProfileRig()
        // Palec 2: kamera w prawo.
        r.down(2, camX, camY)
        r.advance(16)
        r.move(2, camX + 40f, camY)
        assertTrue(r.sink.lastPad!!.rx.toInt() > 0)
        // Palec 1: gałka look w lewo nadpisuje oś.
        r.down(1, 1200f, 600f)
        r.move(1, 1100f, 600f)
        assertEquals(-32767, r.sink.lastPad!!.rx.toInt())
        // Palec 2 stoi: wygaszanie kamery ma inny znak niż bieżąca wartość osi, więc R7 je odrzuca.
        r.advance(30)
        r.tick()
        assertTrue(
            r.engine.violations.any { it.bindingId == "gesture:camera" && it.reason.contains("upływ czasu") },
            "ZACHOWANIE DO DECYZJI: poprawny (wg walidatora) profil daje naruszenie R7, są: ${r.engine.violations}"
        )
        r.up(1)
        r.up(2)
        assertTrue(r.sink.isZero())
    }

    // ------------------------------------------------------------------ ryzyko 2: chord i sprintLock

    @Test
    fun chordIsIgnoredByEngineEvenWhenAllowed() {
        val chordButton: Control = ButtonControl(
            id = "c", rect = Rect(0.45f, 0.60f, 0.06f, 0.12f), output = Output.Pad(PadButton.Y),
            chord = Output.Pad(PadButton.LB)
        )
        val base = testProfile(controls = testControls(false) + chordButton)
        val profile = base.copy(global = base.global.copy(allowChords = true))
        assertTrue(ProfileValidator.validate(profile).isEmpty(), "akord LB+Y jest poprawny przy allowChords = true")
        val r = Rig(profile)
        r.down(1, 960f, 660f)
        val p = r.sink.lastPad
        assertNotNull(p)
        assertEquals(PadFlags.Y, p.buttons, "ZACHOWANIE DO DECYZJI: silnik wysyła tylko output (Y), bez LB z chord")
        assertEquals(1, r.sink.pressEdges.size)
        r.up(1)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }

    /** Ta sama trajektoria palca na gałce ruchu dla sprintLock = false i true. */
    private fun stickTrace(sprintLock: Boolean): List<Pair<Int, Int>> {
        val controls = ArrayList<Control>()
        for (c in testControls(false)) {
            controls.add(if (c is StickControl) c.copy(sprintLock = sprintLock) else c)
        }
        val r = Rig(testProfile(controls = controls))
        val out = ArrayList<Pair<Int, Int>>()
        fun snap() {
            val p = r.sink.lastPad
            out.add(Pair(p?.lx?.toInt() ?: 0, p?.ly?.toInt() ?: 0))
        }
        // Gałka pływająca: środek w punkcie DOWN, promień 15% krótszego boku = 150 px.
        r.down(1, Pts.STICK_X, Pts.STICK_Y)
        snap()
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 100f) // w obrysie
        snap()
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 300f) // daleko poza obrysem
        snap()
        r.move(1, Pts.STICK_X + 20f, Pts.STICK_Y - 300f) // poza obrysem, lekko w bok
        snap()
        r.move(1, Pts.STICK_X, Pts.STICK_Y - 60f) // powrót do obrysu
        snap()
        r.up(1)
        snap()
        assertEquals(0, r.engine.violations.size)
        return out
    }

    @Test
    fun sprintLockIsHysteresisUntilDeadzoneOnlyWhileFingerIsDown() {
        // Decyzja właściciela 11.11.2026: sprintLock = histereza nasycenia (szczegóły w StickRecognizerTest).
        val off = stickTrace(false)
        val on = stickTrace(true)
        // Do nasycenia i poza obrysem obie wersje są identyczne (poza obrysem i tak jest 100%).
        assertEquals(off.subList(0, 4), on.subList(0, 4))
        assertEquals(32767, off[2].second)
        // Powrót do obrysu: bez flagi wychylenie maleje, ze sprintLock zostaje 100% (kierunek za palcem, tu w górę).
        assertTrue(off[4].second in 1 until 32767, "powrót do obrysu bez sprintLock: ${off[4]}")
        assertEquals(Pair(0, 32767), on[4], "powrót do obrysu ze sprintLock")
        // Brak zatrzasku po palcu: UP zeruje w obu wersjach (nic nie zostaje po palcu).
        assertEquals(Pair(0, 0), off[5])
        assertEquals(Pair(0, 0), on[5])
    }

    // ------------------------------------------------------------------ ryzyko 3: long-press i ticki

    @Test
    fun longPressWithFrameTicksFiresWhileFingerIsDownWithinOneFrame() {
        // Tak jak ThumbcastOverlay: tick raz na klatkę (~16 ms), dopóki palec dotyka; palec nie rusza się.
        val r = Rig()
        val downAt = r.clock.now
        r.down(1, 1000f, 500f)
        var firedAt = -1L
        var frames = 0
        while (frames < 60 && firedAt < 0) {
            r.advance(16)
            r.tick()
            frames += 1
            if (r.sink.mouseDown.isNotEmpty()) firedAt = r.clock.now
        }
        assertTrue(firedAt > 0, "RMB w dół przed UP palca")
        val latency = firedAt - downAt - 450L
        assertTrue(latency in 0L..16L, "long-press najpóźniej klatkę po progu minMs: $latency ms")
        val rmb = r.mouseButtons()
        assertEquals(1, rmb.size)
        assertEquals(CycleKind.TICK, rmb[0].cause?.cycle)
        // Trzymanie dalej nic nie dodaje; UP zwalnia RMB.
        repeat(30) {
            r.advance(16)
            r.tick()
        }
        assertEquals(1, r.mouseButtons().size)
        r.up(1)
        assertEquals(2, r.mouseButtons().size)
        assertTrue(r.sink.isZero())
        assertEquals(0, r.engine.violations.size)
    }
}
