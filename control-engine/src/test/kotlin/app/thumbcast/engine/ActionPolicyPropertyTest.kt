package app.thumbcast.engine

import app.thumbcast.model.Control
import app.thumbcast.model.FreeZoneMode
import app.thumbcast.model.MouseGesturesTransform
import app.thumbcast.model.Output
import app.thumbcast.model.PadStick
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.StickControl
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.util.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Testy właściwości zasady 1 dotyk = 1 wejście (plan 6.2 pkt 7, check CI „policy-tests”).
 *
 * Kotest generuje ziarno, z którego budujemy deterministyczny strumień zdarzeń (poprawny albo chaotyczny:
 * CANCEL, podwójny DOWN tego samego id, UP bez DOWN, MOVE nieznanych palców, cofający się czas, releaseAll
 * w trakcie, zmiana viewportu, ticki). Ziarno jest w komunikacie błędu, więc porażkę da się odtworzyć.
 * Część profili (wybór z ziarna) ma gałkę z `sprintLock = true`. Strumienie touchpada (`freeZone = touchpad`) mają
 * dodatkowo pary palców w wolnej strefie i wspólny ruch kilku palców w jednym MOVE (tap dwoma palcami, przewijanie).
 *
 * Sprawdzane po każdym kroku:
 *  P1. każda krawędź wciśnięcia ma przyczynę: cykl TOUCH (DOWN/MOVE/UP, nie CANCEL) albo TICK tylko dla long-pressu,
 *      a jej palec był w tym momencie aktywny (DOWN bez UP/CANCEL),
 *  P2. jeden cykl życia palca daje najwyżej jedną krawędź wciśnięcia (poza klawiszami gałki WASD w KBM),
 *      więc liczba krawędzi <= liczba DOWN z przypisanym wyjściem,
 *  P3. tick (czas bez dotyku) nie zmienia stanu pada poza wygaszaniem prawej gałki, i nic nie robi bez palca,
 *  P4. w stanie polityki żaden palec nie trzyma dwóch wiązań, a liczba jednocześnie wciśniętych wejść
 *      dyskretnych (PAD) nie przekracza liczby aktywnych palców,
 *  P5. silnik nie generuje naruszeń polityki (ActionPolicy jest siatką bezpieczeństwa; naruszenie = błąd silnika),
 * a na końcu:
 *  P6. po releaseAll() stan wyjścia jest zerowy, a przesuwanie zegara i ticki bez dotyku nie dają żadnego wyjścia.
 */
class ActionPolicyPropertyTest {

    private val iterations: Int = System.getProperty("thumbcast.policy.iterations")?.toIntOrNull() ?: 1000

    @Test
    fun padPlusValidStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, withSprintLock(testProfile(), seed), chaotic = false, slide = false).run()
        }
    }

    @Test
    fun padPlusChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, withSprintLock(testProfile(), seed), chaotic = true, slide = false).run()
        }
    }

    @Test
    fun padPlusSlideToPressChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, withSprintLock(testProfile(slideToPress = true), seed), chaotic = true, slide = true).run()
        }
    }

    @Test
    fun keyboardMouseChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, withSprintLock(testProfile(mode = ProfileMode.KEYBOARD_MOUSE), seed), chaotic = true, slide = false).run()
        }
    }

    /** Touchpad (freeZone = touchpad), poprawne strumienie; część profili z widoczną prawą gałką (Big Picture). */
    @Test
    fun touchpadValidStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, touchpadFor(seed), chaotic = false, slide = false, touchpad = true).run()
        }
    }

    /** Touchpad: chaos wielu palców (pary palców, ruch kilku palców w jednym zdarzeniu, CANCEL, podwójny DOWN...). */
    @Test
    fun touchpadMultiFingerChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            Scenario(seed, touchpadFor(seed), chaotic = true, slide = false, touchpad = true).run()
        }
    }

    /**
     * Wolna strefa wyłączona (freeZone = none, 12.11.2026): ten sam chaos wielu palców co przy touchpadzie. Poza
     * zwykłymi właściwościami: żadnego wyjścia myszy, a prawa gałka rusza się tylko z widocznej kontrolki.
     */
    @Test
    fun noFreeZoneMultiFingerChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            val r = Random(seed xor NONE_SALT)
            val mode = if (r.nextInt(4) == 0) ProfileMode.KEYBOARD_MOUSE else ProfileMode.PAD_PLUS
            val profile = withSprintLock(noFreeZoneProfile(mode = mode, rightStick = r.nextBoolean()), seed)
            Scenario(seed, profile, chaotic = true, slide = false, touchpad = true, noFreeZone = true).run()
        }
    }

    /**
     * Przełącznik „Gesty myszy” (12.11.2026): profil efektywny z [MouseGesturesTransform] (kamera z gestami albo bez,
     * touchpad, none; Pad+ albo KBM; z prawą gałką albo bez; wł. albo wył.) przechodzi walidator, ma żądany stan
     * przełącznika i spełnia te same właściwości polityki przy chaosie wielu palców. Wynik `none` dodatkowo bez myszy.
     */
    @Test
    fun mouseGesturesSwitchChaoticStreams(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            val r = Random(seed xor MOUSE_SALT)
            val mode = if (r.nextInt(4) == 0) ProfileMode.KEYBOARD_MOUSE else ProfileMode.PAD_PLUS
            val base = when (r.nextInt(3)) {
                0 -> testProfile(mode = mode).let {
                    it.copy(gestures = it.gestures.copy(
                        tapTarget = it.gestures.tapTarget.copy(enabled = r.nextBoolean()),
                        longPress = it.gestures.longPress.copy(enabled = r.nextBoolean()),
                        pinchZoom = it.gestures.pinchZoom.copy(enabled = r.nextBoolean())
                    ))
                }
                1 -> touchpadProfile(mode = mode, rightStick = r.nextBoolean())
                else -> noFreeZoneProfile(mode = mode, rightStick = r.nextBoolean())
            }
            val want = r.nextBoolean()
            val p = withSprintLock(MouseGesturesTransform.apply(base, want), seed)
            assertEquals(emptyList(), ProfileValidator.validate(p), "seed=$seed")
            assertEquals(want, MouseGesturesTransform.isEnabled(p), "seed=$seed")
            val zone = p.gestures.freeZone
            Scenario(seed, p, chaotic = true, slide = false, touchpad = zone != FreeZoneMode.CAMERA,
                noFreeZone = zone == FreeZoneMode.NONE).run()
        }
    }

    @Test
    fun clockWithoutTouchNeverEmits(): Unit = runBlocking<Unit> {
        checkAll(iterations, Arb.long()) { seed ->
            val rnd = Random(seed)
            val sink = FakeInputSink()
            val clock = FakeClock(rnd.nextInt(100000).toLong())
            val profile = if (rnd.nextBoolean()) testProfile() else touchpadProfile()
            val engine = InputEngine(profile, sink, clock, testViewport())
            for (i in 0 until 50) {
                clock.advance(rnd.nextInt(1000).toLong())
                if (rnd.nextBoolean()) engine.onTick() else engine.onTick(clock.now + rnd.nextInt(5000) - 2500)
            }
            assertEquals(0, sink.records.size, "seed=$seed")
            engine.releaseAll()
            sink.clear()
            clock.advance(10000)
            engine.onTick()
            assertEquals(0, sink.records.size, "seed=$seed")
        }
    }

    /**
     * Mniej więcej połowa strumieni dostaje gałkę z `sprintLock = true` (histereza nasycenia, 11.11.2026).
     * Wybór z ziarna (z osobną solą, żeby nie korelował ze strumieniem zdarzeń), więc porażka jest odtwarzalna.
     */
    private fun withSprintLock(p: Profile, seed: Long): Profile {
        if (!Random(seed xor SPRINT_SALT).nextBoolean()) return p
        val controls = ArrayList<Control>()
        for (c in p.controls) controls.add(if (c is StickControl) c.copy(sprintLock = true) else c)
        return p.copy(controls = controls)
    }

    /**
     * Profil touchpada z ziarna (osobna sól): Pad+ albo KBM, z widoczną prawą gałką albo bez, różna czułość i
     * długość ząbka, czasem z gałką `sprintLock`.
     */
    private fun touchpadFor(seed: Long): Profile {
        val r = Random(seed xor TOUCHPAD_SALT)
        val mode = if (r.nextInt(4) == 0) ProfileMode.KEYBOARD_MOUSE else ProfileMode.PAD_PLUS
        val p = touchpadProfile(
            mode = mode,
            sensitivity = 0.5f + r.nextInt(30) / 10f,
            scrollPxPerNotch = (4 + r.nextInt(60)).toFloat(),
            rightStick = r.nextBoolean()
        )
        return withSprintLock(p, seed)
    }

    private companion object {
        const val SPRINT_SALT: Long = 0x5EED_5B1A7L
        const val TOUCHPAD_SALT: Long = 0x70C4_9AD5L
        const val NONE_SALT: Long = 0x0F2E_E20EL
        const val MOUSE_SALT: Long = 0x3A05_E6E5L
    }

    /** Jeden strumień zdarzeń i jego sprawdzenia. */
    private class Scenario(
        private val seed: Long,
        profile: Profile,
        private val chaotic: Boolean,
        private val slide: Boolean,
        private val touchpad: Boolean = false,
        private val noFreeZone: Boolean = false
    ) {
        private val hasRightStickControl: Boolean =
            profile.controls.any { it is StickControl && (it.output as? Output.Stick)?.stick == PadStick.RIGHT }
        private val rnd: Random = Random(seed)
        private val sink: FakeInputSink = FakeInputSink()
        private val clock: FakeClock = FakeClock(1000L)
        private val engine: InputEngine = InputEngine(profile, sink, clock, testViewport())
        private val kbm: Boolean = profile.mode == ProfileMode.KEYBOARD_MOUSE
        private val sprint: Boolean = profile.controls.any { it is StickControl && it.sprintLock }

        /** Model testu: palce, które dostały DOWN i nie dostały UP/CANCEL (pozycje). */
        private val live: LinkedHashMap<Int, TouchPointer> = LinkedHashMap()
        /** Numer cyklu życia palca (rośnie przy każdym DOWN tego id). */
        private val lifetime: HashMap<Int, Int> = HashMap()
        private var lifetimeCounter: Int = 0
        private val edgesPerLifetime: HashMap<Int, Int> = HashMap()
        private var downs: Int = 0
        private var slideEdges: Int = 0
        private var countedEdges: Int = 0
        private var time: Long = 1000L

        private val hotspots: Array<FloatArray> = arrayOf(
            floatArrayOf(Pts.STICK_X, Pts.STICK_Y), floatArrayOf(Pts.A_X, Pts.A_Y), floatArrayOf(Pts.X_X, Pts.X_Y),
            floatArrayOf(Pts.LT_X, Pts.LT_Y), floatArrayOf(Pts.MAP_X, Pts.MAP_Y), floatArrayOf(Pts.CHAT_X, Pts.CHAT_Y),
            floatArrayOf(Pts.FREE_X, Pts.FREE_Y), floatArrayOf(1000f, 500f), floatArrayOf(1200f, 10f),
            floatArrayOf(1715f, 780f)
        )

        /** Wolna strefa i widoczna prawa gałka (touchpadProfile(rightStick = true)) dla strumieni touchpada. */
        private val touchpadSpots: Array<FloatArray> = arrayOf(
            floatArrayOf(1300f, 400f), floatArrayOf(1500f, 400f), floatArrayOf(1200f, 500f), floatArrayOf(800f, 250f),
            floatArrayOf(1200f, 750f), floatArrayOf(1300f, 20f)
        )

        fun run() {
            val steps = 20 + rnd.nextInt(80)
            for (i in 0 until steps) step()
            if (noFreeZone) checkNoFreeZoneOutput()
            // Na koniec: releaseAll i zegar bez dotyku.
            engine.releaseAll()
            live.clear()
            check(sink.isZero(), "stan niezerowy po releaseAll")
            check(engine.padSnapshot() == PadSnapshot.ZERO, "pad niezerowy po releaseAll")
            sink.clear()
            for (k in 0 until 5) {
                clock.advance(rnd.nextInt(2000).toLong())
                engine.onTick()
            }
            check(sink.records.isEmpty(), "wyjście bez dotyku po releaseAll")
            check(engine.violationCount == 0L, "naruszenia: " + engine.violations)
            check(countedEdges <= downs, "krawędzie $countedEdges > DOWN $downs")
        }

        /** freeZone = none: bez myszy (ruch, przyciski, pozycja, kółka), prawa gałka tylko z kontrolki. */
        private fun checkNoFreeZoneOutput() {
            for (rec in sink.records) {
                val mouse = rec.call == SinkCall.MOVE || rec.call == SinkCall.BUTTON || rec.call == SinkCall.POSITION ||
                    rec.call == SinkCall.WHEEL || rec.call == SinkCall.HWHEEL
                check(!mouse, "wolna strefa none dała wyjście myszy: $rec")
                if (rec.call == SinkCall.PAD && !hasRightStickControl) {
                    check(rec.rx.toInt() == 0 && rec.ry.toInt() == 0, "prawa gałka bez kontrolki (kamera?): $rec")
                }
                val binding = rec.cause?.bindingId ?: ""
                if (rec.call == SinkCall.APP && binding.startsWith("gesture:")) {
                    check(rec.app == "menu" && binding == "gesture:edge", "gest inny niż edge swipe: $rec")
                }
            }
        }

        private fun step() {
            val r = rnd.nextInt(100)
            if (r < 12) advanceTime()
            if (touchpad && rnd.nextInt(100) < 25) {
                // Gesty wielu palców: para palców w wolnej strefie albo wspólny ruch wszystkich palców w jednym MOVE.
                if (rnd.nextBoolean()) doPairDown() else doMoveTogether()
                return
            }
            when {
                r < 30 -> doDown()
                r < 60 -> doMove()
                r < 75 -> doUp()
                r < 88 -> doTick()
                r < 91 -> doCancel()
                chaotic && r < 94 -> doDoubleDown()
                chaotic && r < 96 -> doUpWithoutDown()
                chaotic && r < 97 -> doReleaseAll()
                chaotic && r < 98 -> doViewportChange()
                chaotic -> doBackwardsTime()
                else -> doMove()
            }
        }

        private fun advanceTime() {
            time += rnd.nextInt(600).toLong()
            clock.now = time
        }

        private fun point(): FloatArray {
            if (touchpad && rnd.nextInt(100) < 40) {
                val h = touchpadSpots[rnd.nextInt(touchpadSpots.size)]
                return floatArrayOf(h[0] + rnd.nextInt(41) - 20, h[1] + rnd.nextInt(41) - 20)
            }
            if (rnd.nextInt(100) < 60) {
                val h = hotspots[rnd.nextInt(hotspots.size)]
                return floatArrayOf(h[0] + rnd.nextInt(21) - 10, h[1] + rnd.nextInt(21) - 10)
            }
            val margin = if (chaotic) 200 else 0
            return floatArrayOf((rnd.nextInt(W + 2 * margin) - margin).toFloat(), (rnd.nextInt(H + 2 * margin) - margin).toFloat())
        }

        private fun freeId(): Int {
            for (k in 0 until 20) {
                val id = rnd.nextInt(12)
                if (!live.containsKey(id)) return id
            }
            return -1
        }

        private fun doDown() {
            val id = freeId()
            if (id < 0) return
            val p = point()
            live[id] = TouchPointer(id, p[0], p[1])
            beginLifetime(id)
            send(TouchEvent(TouchAction.DOWN, id, ArrayList(live.values), time), TouchAction.DOWN)
        }

        /** Dwa palce blisko siebie w wolnej strefie, w osobnych zdarzeniach DOWN (tap dwoma palcami, przewijanie). */
        private fun doPairDown() {
            val base = touchpadSpots[rnd.nextInt(3)]
            for (k in 0 until 2) {
                val id = freeId()
                if (id < 0) return
                val x = base[0] + k * (60 + rnd.nextInt(120)) + rnd.nextInt(11) - 5
                val y = base[1] + rnd.nextInt(41) - 20
                live[id] = TouchPointer(id, x, y)
                beginLifetime(id)
                send(TouchEvent(TouchAction.DOWN, id, ArrayList(live.values), time), TouchAction.DOWN)
                if (rnd.nextBoolean()) time += rnd.nextInt(60).toLong()
                clock.now = time
            }
        }

        /** Wszystkie palce przesunięte o (prawie) ten sam wektor w jednym zdarzeniu MOVE. */
        private fun doMoveTogether() {
            if (live.isEmpty()) return
            val big = rnd.nextInt(100) < 50
            val range = if (big) 80 else 14
            val dx = (rnd.nextInt(2 * range + 1) - range).toFloat()
            val dy = (rnd.nextInt(2 * range + 1) - range).toFloat()
            for (id in ArrayList(live.keys)) {
                val old = live[id] ?: continue
                live[id] = TouchPointer(id, old.x + dx + rnd.nextInt(5) - 2, old.y + dy + rnd.nextInt(5) - 2)
            }
            time += rnd.nextInt(20).toLong()
            clock.now = time
            send(TouchEvent(TouchAction.MOVE, -1, ArrayList(live.values), time), TouchAction.MOVE)
        }

        private fun doDoubleDown() {
            if (live.isEmpty()) return
            val ids = ArrayList(live.keys)
            val id = ids[rnd.nextInt(ids.size)]
            val p = point()
            // Stary cykl życia kończy się (zgubiony UP), zaczyna nowy.
            live[id] = TouchPointer(id, p[0], p[1])
            beginLifetime(id)
            send(TouchEvent(TouchAction.DOWN, id, ArrayList(live.values), time), TouchAction.DOWN)
        }

        private fun doMove() {
            if (live.isEmpty() && !chaotic) return
            val ids = ArrayList(live.keys)
            for (id in ids) {
                if (rnd.nextInt(100) < 60) {
                    val old = live[id] ?: continue
                    val big = rnd.nextInt(100) < 30
                    val range = if (big) 400 else 16
                    val nx = old.x + (rnd.nextInt(2 * range + 1) - range).toFloat()
                    val ny = old.y + (rnd.nextInt(2 * range + 1) - range).toFloat()
                    live[id] = TouchPointer(id, nx, ny)
                }
            }
            val list = ArrayList(live.values)
            if (chaotic && rnd.nextInt(100) < 20) list.add(TouchPointer(50 + rnd.nextInt(5), 500f, 500f))
            time += rnd.nextInt(20).toLong()
            clock.now = time
            send(TouchEvent(TouchAction.MOVE, -1, list, time), TouchAction.MOVE)
        }

        private fun doUp() {
            if (live.isEmpty()) return
            val ids = ArrayList(live.keys)
            val id = ids[rnd.nextInt(ids.size)]
            val list = ArrayList(live.values)
            send(TouchEvent(TouchAction.UP, id, list, time), TouchAction.UP)
            live.remove(id)
        }

        private fun doUpWithoutDown() {
            val id = 30 + rnd.nextInt(5)
            send(TouchEvent(TouchAction.UP, id, listOf(TouchPointer(id, 100f, 100f)), time), TouchAction.UP)
        }

        private fun doCancel() {
            send(TouchEvent(TouchAction.CANCEL, -1, ArrayList(live.values), time), TouchAction.CANCEL)
            live.clear()
        }

        private fun doReleaseAll() {
            engine.releaseAll()
            live.clear()
            check(sink.isZero(), "stan niezerowy po releaseAll (w trakcie)")
        }

        private fun doViewportChange() {
            val vp = if (rnd.nextBoolean()) testViewport() else Viewport(W, H, RectPx(100, 0, 1900, 1000), 1920, 1080)
            val hadFingers = live.isNotEmpty()
            engine.setViewport(vp)
            // Zmiana geometrii przy dotyku zwalnia wszystko; palce są dla silnika zakończone.
            if (hadFingers && vp != lastViewport) live.clear()
            lastViewport = vp
        }

        private var lastViewport: Viewport = testViewport()

        private fun doBackwardsTime() {
            if (live.isEmpty()) return
            val t = time - rnd.nextInt(300).toLong()
            send(TouchEvent(TouchAction.MOVE, -1, ArrayList(live.values), t), TouchAction.MOVE)
        }

        private fun doTick() {
            time += rnd.nextInt(200).toLong()
            clock.now = time
            val before = sink.records.size
            val edgesBefore = sink.pressEdges.size
            val padBefore = sink.lastPad
            val liveBefore = HashSet(live.keys)
            sink.resetMaxSimultaneous()
            engine.onTick(time)
            if (liveBefore.isEmpty()) {
                check(sink.records.size == before, "tick bez palca dał wyjście")
            }
            var prevPad = padBefore
            for (i in before until sink.records.size) {
                val rec = sink.records[i]
                val c = rec.cause
                check(c != null && c.cycle == CycleKind.TICK, "wyjście ticka bez przyczyny TICK: $rec")
                if (rec.call == SinkCall.PAD) {
                    val p0 = prevPad
                    val b0 = p0?.buttons ?: 0
                    check(rec.buttons == b0, "tick zmienił przyciski pada")
                    check(rec.lx == (p0?.lx ?: 0.toShort()) && rec.ly == (p0?.ly ?: 0.toShort()), "tick zmienił lewą gałkę")
                    check(rec.lt == (p0?.lt ?: 0.toByte()) && rec.rt == (p0?.rt ?: 0.toByte()), "tick zmienił triggery")
                    check(abs(rec.rx.toInt()) <= abs((p0?.rx ?: 0.toShort()).toInt()), "tick zwiększył kamerę X")
                    check(abs(rec.ry.toInt()) <= abs((p0?.ry ?: 0.toShort()).toInt()), "tick zwiększył kamerę Y")
                    prevPad = rec
                } else if (rec.call == SinkCall.POSITION || rec.call == SinkCall.BUTTON) {
                    check(c != null && c.kind == BindingKind.ONE_SHOT_TIMED, "tick dał wejście inne niż long-press: $rec")
                } else {
                    check(false, "tick dał niedozwolone wyjście: $rec")
                }
            }
            checkEdgesFrom(edgesBefore, liveBefore, null)
            checkInvariants(liveBefore.size)
        }

        private fun send(e: TouchEvent, action: TouchAction) {
            val before = sink.records.size
            val edgesBefore = sink.pressEdges.size
            val liveBefore = HashSet(live.keys)
            if (action == TouchAction.DOWN) liveBefore.add(e.actionPointerId)
            if (action == TouchAction.DOWN) downs += 1
            sink.resetMaxSimultaneous()
            engine.onTouch(e)
            // P1: przyczyny wszystkich nowych wywołań.
            for (i in before until sink.records.size) {
                val c = sink.records[i].cause
                check(c != null, "wywołanie bez przyczyny: " + sink.records[i])
                if (c != null) {
                    check(c.cycle == CycleKind.TOUCH, "wywołanie onTouch spoza cyklu TOUCH: $c")
                    check(c.action == action, "przyczyna z innego zdarzenia: $c")
                    check(c.eventTimeMs == e.timeMs, "przyczyna z innego czasu: $c")
                }
            }
            check(edgesBefore <= sink.pressEdges.size, "rejestr krawędzi")
            checkEdgesFrom(edgesBefore, liveBefore, action)
            checkInvariants(liveBefore.size)
        }

        private fun checkEdgesFrom(start: Int, liveBefore: Set<Int>, action: TouchAction?) {
            for (i in start until sink.pressEdges.size) {
                val edge = sink.pressEdges[i]
                val c = edge.cause
                check(c != null, "krawędź bez przyczyny: $edge")
                if (c == null) continue
                if (c.cycle == CycleKind.TICK) {
                    check(c.kind == BindingKind.ONE_SHOT_TIMED, "krawędź z samego czasu: $edge")
                } else {
                    check(c.cycle == CycleKind.TOUCH, "krawędź spoza dotyku: $edge")
                    check(c.action != TouchAction.CANCEL, "krawędź z CANCEL: $edge")
                    if (action != null) check(c.action == action, "krawędź z innego zdarzenia: $edge")
                }
                check(liveBefore.contains(c.pointerId), "krawędź palca, który nie dotyka: $edge")
                // Jedyny opisany wyjątek (engine.md): gałka w KBM trzyma do dwóch klawiszy kierunku (skos WASD).
                val stickKey = kbm && (c.bindingId == "control:move" || c.bindingId == "control:look")
                if (stickKey) continue
                if (slide && c.bindingId.startsWith("control:") && c.action == TouchAction.MOVE) {
                    // Slide-to-press: kolejny przycisk tego samego palca po zwolnieniu poprzedniego.
                    // Nie liczymy go jako drugiego dotyku; P4 pilnuje, że nigdy nie są wciśnięte dwa naraz.
                    slideEdges += 1
                    continue
                }
                val lt = lifetime[c.pointerId] ?: -1
                val n = (edgesPerLifetime[lt] ?: 0) + 1
                edgesPerLifetime[lt] = n
                countedEdges += 1
                check(n <= 1, "więcej niż jedna krawędź z jednego dotyku: $edge")
            }
        }

        private fun checkInvariants(liveCount: Int) {
            check(engine.debugMaxBindingsPerPointer() <= 1, "palec trzyma dwa wiązania")
            if (!kbm) {
                check(sink.maxSimultaneous <= liveCount, "jednocześnie ${sink.maxSimultaneous} wejść przy $liveCount palcach")
            }
            check(engine.violationCount == 0L, "naruszenia: " + engine.violations)
        }

        private fun beginLifetime(id: Int) {
            lifetimeCounter += 1
            lifetime[id] = lifetimeCounter
        }

        private fun check(cond: Boolean, msg: String) {
            if (!cond) fail("seed=$seed chaotic=$chaotic slide=$slide sprintLock=$sprint touchpad=$touchpad: $msg")
        }
    }
}
