package app.thumbcast.engine

import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode

/**
 * Silnik wejścia: TouchEvent -> PointerTracker -> HitTest -> rozpoznawacze -> ActionPolicy -> OutputMapper -> InputSink.
 *
 * Wszystko, co trafia do sinka, przechodzi przez ActionPolicy (silnik nie ma referencji do OutputMapper).
 * Silnik nie ma żadnego timera: działa tylko w reakcji na onTouch, onTick (tick czasu powiązany z aktywnymi
 * palcami; bez palców nic nie robi) i releaseAll. Wywołania muszą przychodzić z jednego wątku (UI).
 */
class InputEngine(profile: Profile, sink: InputSink, private val clock: Clock, viewport: Viewport) {

    private var currentProfile: Profile = profile
    private var currentViewport: Viewport = viewport
    private var tuning: EngineTuning = EngineTuning()
    private var config: EngineConfig = ProfileAdapter.toConfig(profile, tuning)

    private val policy: ActionPolicy = ActionPolicy(sink)
    private val tracker: PointerTracker = PointerTracker(tuning.maxPointers)

    private var hitTest: HitTest = HitTest(config.controls, viewport, config.leftHanded, tuning)
    private var sticks: HashMap<String, StickRecognizer> = HashMap()
    private var buttons: HashMap<String, ButtonRecognizer> = HashMap()
    private var gestures: GestureArbiter = GestureArbiter(config, viewport, hitTest.screenBounds, policy)
    private var lastEventTimeMs: Long = Long.MIN_VALUE

    init {
        rebuild()
    }

    /** Log naruszeń ActionPolicy (ograniczony do ostatnich 1000 wpisów). */
    val violations: List<PolicyViolation> get() = policy.violations

    /** Łączna liczba naruszeń (także te, które wypadły z logu). */
    val violationCount: Long get() = policy.violationCount

    /** Bieżący stan pada (debug overlay, testy). */
    fun padSnapshot(): PadSnapshot = policy.padSnapshot()

    /** Niezmiennik „1 palec = 1 wejście” w stanie polityki (testy). */
    internal fun debugMaxBindingsPerPointer(): Int = policy.maxBindingsPerPointer()

    /** Konstruktor testowy/edytora: gotowa konfiguracja zamiast profilu. */
    constructor(config: EngineConfig, profile: Profile, sink: InputSink, clock: Clock, viewport: Viewport) :
        this(profile, sink, clock, viewport) {
        this.config = config
        this.tuning = config.tuning
        rebuild()
    }

    fun onTouch(e: TouchEvent) {
        if (e.timeMs > lastEventTimeMs) lastEventTimeMs = e.timeMs
        policy.beginTouchCycle(e, false)
        try {
            when (e.action) {
                TouchAction.DOWN -> handleDown(e)
                TouchAction.MOVE -> handleMove(e)
                TouchAction.UP -> handleUp(e)
                TouchAction.CANCEL -> handleCancel()
            }
        } finally {
            policy.endCycle()
        }
    }

    /**
     * Tick czasu (np. raz na klatkę z Choreographer/postOnAnimation), poza kontraktem (CONTRACT-NOTES, ENGINE).
     * Traktowany jak syntetyczny MOVE aktywnych palców bez zmiany pozycji: służy wyłącznie do rozpoznania
     * long-pressu trzymanego palca i do wygaszania kamery. Bez aktywnego palca nic nie robi.
     */
    fun onTick(nowMs: Long) {
        if (tracker.size == 0) return
        val t = if (nowMs < lastEventTimeMs) lastEventTimeMs else nowMs
        val list = ArrayList<TouchPointer>()
        for (p in tracker.all()) list.add(TouchPointer(p.id, p.x, p.y))
        val synthetic = TouchEvent(TouchAction.MOVE, -1, list, t)
        policy.beginTouchCycle(synthetic, true)
        try {
            for (p in tracker.all()) {
                if (p.owner == GestureOwner) gestures.onMove(p, tracker, t, true)
            }
        } finally {
            policy.endCycle()
        }
    }

    /** Tick z zegara silnika. */
    fun onTick() {
        onTick(clock.nowMs())
    }

    /** CANCEL, utrata fokusu, rozłączenie: zerowy stan pada, puszczone klawisze i przyciski myszy. */
    fun releaseAll() {
        resetRecognizers()
        tracker.clear()
        policy.releaseAll(clock.nowMs())
    }

    fun setProfile(p: Profile) {
        releaseAll()
        currentProfile = p
        config = ProfileAdapter.toConfig(p, tuning)
        rebuild()
    }

    fun setViewport(v: Viewport) {
        if (v == currentViewport) return
        // Zmiana geometrii (obrót, insets) w trakcie dotyku: bezpieczniej zwolnić wszystko niż przeliczać.
        if (tracker.size > 0) releaseAll()
        currentViewport = v
        rebuild()
    }

    // ------------------------------------------------------------------ obsługa zdarzeń

    private fun handleDown(e: TouchEvent) {
        val pid = e.actionPointerId
        val pos = find(e, pid) ?: return
        if (tracker.isTracked(pid)) {
            // Podwójny DOWN tego samego id (zgubiony UP): najpierw kończymy stary palec, bez tapu.
            finishPointer(pid, e.timeMs, false)
        }
        val spec = hitTest.hit(pos.x, pos.y)
        val owner: PointerOwner = if (spec != null) ControlOwner(spec.id) else GestureOwner
        val p = tracker.down(pid, pos.x, pos.y, e.timeMs, owner) ?: return
        policy.pointerDown(pid)
        if (owner is ControlOwner) {
            controlDown(owner.controlId, p)
        } else {
            gestures.onDown(p, tracker)
        }
    }

    private fun handleMove(e: TouchEvent) {
        for (tp in e.pointers) {
            val p = tracker.update(tp.id, tp.x, tp.y, e.timeMs) ?: continue
            val owner = p.owner
            if (owner is ControlOwner) {
                controlMove(owner.controlId, p)
            } else {
                gestures.onMove(p, tracker, e.timeMs, false)
            }
        }
    }

    private fun handleUp(e: TouchEvent) {
        val pid = e.actionPointerId
        if (!tracker.isTracked(pid)) return // UP bez DOWN (lub palec ponad limit): ignorujemy
        val pos = find(e, pid)
        if (pos != null) tracker.update(pid, pos.x, pos.y, e.timeMs)
        finishPointer(pid, e.timeMs, true)
    }

    private fun handleCancel() {
        resetRecognizers()
        tracker.clear()
        policy.cancelAll()
    }

    private fun finishPointer(pid: Int, t: Long, fire: Boolean) {
        val p = tracker.get(pid) ?: return
        val owner = p.owner
        if (owner is ControlOwner) {
            val s = sticks[owner.controlId]
            if (s != null) s.onUp(pid)
            val b = buttons[owner.controlId]
            if (b != null) b.onUp(pid)
        } else {
            gestures.onUp(p, t, fire)
        }
        tracker.remove(pid)
        policy.pointerUp(pid)
    }

    private fun controlDown(id: String, p: TrackedPointer) {
        val s = sticks[id]
        if (s != null) {
            val r = hitTest.rectOf(id) ?: return
            s.onDown(p.id, p.x, p.y, r, currentViewport)
            return
        }
        val b = buttons[id]
        if (b != null) b.onDown(p.id)
    }

    private fun controlMove(id: String, p: TrackedPointer) {
        val s = sticks[id]
        if (s != null) {
            s.onMove(p.id, p.x, p.y)
            return
        }
        val b = buttons[id] ?: return
        if (!config.slideToPress || !b.slideable) return
        val target = hitTest.hit(p.x, p.y) ?: return
        if (target.id == id) return
        val nb = buttons[target.id] ?: return
        if (!nb.slideable) return
        // Slide-to-press: najpierw zwolnij stary, potem wciśnij nowy (nigdy dwa naraz z jednego palca).
        b.onUp(p.id)
        tracker.transfer(p.id, ControlOwner(target.id))
        nb.onDown(p.id)
    }

    // ------------------------------------------------------------------ budowa komponentów

    private fun rebuild() {
        tuning = config.tuning
        hitTest = HitTest(config.controls, currentViewport, config.leftHanded, tuning)
        val kbm = config.mode == ProfileMode.KEYBOARD_MOUSE
        val previouslyLatched = HashSet<String>()
        for (b in buttons.values) if (b.isLatched) previouslyLatched.add(b.spec.id)
        val ns = HashMap<String, StickRecognizer>()
        val nb = HashMap<String, ButtonRecognizer>()
        for (c in config.controls) {
            when (c) {
                is StickSpec -> {
                    ns[c.id] = StickRecognizer(c, kbm, tuning, policy)
                }
                is ButtonSpec -> {
                    val b = ButtonRecognizer(c, policy)
                    // setProfile robi wcześniej releaseAll (zbiór pusty); setViewport bez palców zachowuje LATCH.
                    if (previouslyLatched.contains(c.id)) b.restoreLatched(true)
                    nb[c.id] = b
                }
            }
        }
        sticks = ns
        buttons = nb
        gestures = GestureArbiter(config, currentViewport, hitTest.screenBounds, policy)
    }

    private fun resetRecognizers() {
        for (s in sticks.values) s.reset()
        for (b in buttons.values) b.reset()
        gestures.reset()
    }

    private fun find(e: TouchEvent, id: Int): TouchPointer? {
        for (tp in e.pointers) if (tp.id == id) return tp
        return null
    }

    /** Profil, z którego zbudowano silnik (dla overlaya). */
    val activeProfile: Profile get() = currentProfile
}
