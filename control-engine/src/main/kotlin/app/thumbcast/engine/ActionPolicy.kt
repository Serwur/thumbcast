package app.thumbcast.engine

import kotlin.math.abs

/** Rodzaj cyklu obsługi, w którym powstało wyjście. */
enum class CycleKind {
    /** Prawdziwe zdarzenie dotyku przekazane do onTouch. */
    TOUCH,
    /** Tick czasu przekazany jako syntetyczny MOVE aktywnych palców (InputEngine.onTick). */
    TICK,
    /** InputEngine.releaseAll() (utrata fokusu, rozłączenie, zmiana profilu). */
    RELEASE_ALL
}

/** Przyczyna wysłania wyjścia (dla testów i overlaya debug). */
data class EmitCause(
    val cycleId: Long,
    val cycle: CycleKind,
    val action: TouchAction?,
    val eventTimeMs: Long,
    /** -1 dla RELEASE_ALL i zerowania przy CANCEL. */
    val pointerId: Int,
    val bindingId: String,
    val kind: BindingKind?
)

/** Opcjonalny interfejs sinka: dostaje przyczynę PRZED każdym wywołaniem metody InputSink, null po zakończeniu cyklu. */
interface EmitCauseListener {
    fun onEmitCause(cause: EmitCause?)
}

/** Odrzucona intencja (naruszenie zasady 1 dotyk = 1 wejście). */
data class PolicyViolation(
    val timeMs: Long,
    val cycle: CycleKind?,
    val pointerId: Int,
    val bindingId: String,
    val effect: String,
    val reason: String
)

/**
 * ActionPolicy (plan 6.2): jedyna droga do OutputMapper, a więc do InputSink.
 *
 * Reguły:
 *  R1. Każde wyjście musi powstać wewnątrz cyklu (onTouch / onTick / releaseAll) i na tym samym wątku.
 *      Intencja spoza cyklu (timer, inny wątek) jest odrzucana.
 *  R2. Wskaźnik wciśnięcia musi należeć do zdarzenia bieżącego cyklu (lista pointers lub actionPointerId).
 *  R3. Wciśnięcie (stan aktywny) wymaga żywego palca (DOWN bez UP/CANCEL). Przy UP palca wolno mu tylko wejście
 *      jednorazowe (tap albo long-press rozpoznany przy UP); nic trwałego.
 *  R4. Jeden palec = jedno logiczne wejście naraz: zmiana wiązania palca jest dozwolona tylko, gdy poprzednie
 *      wiązanie tego palca niczego już nie trzyma (slide-to-press: najpierw zwolnij, potem wciśnij).
 *  R5. ONE_SHOT / ONE_SHOT_TIMED: najwyżej jedno takie wiązanie i jedna krawędź wciśnięcia na cykl życia palca
 *      (tap XOR long-press XOR edge swipe XOR przycisk App).
 *  R6. LATCH zmienia stan wyłącznie w cyklu DOWN tego samego palca.
 *  R7. Cykl TICK (czas bez ruchu): dozwolone tylko ONE_SHOT_TIMED (long-press), zwolnienia oraz wygaszanie
 *      wychylenia gałki (moduł nie rośnie, kierunek bez zmiany znaku). Nic innego nie może powstać z samego czasu.
 *  R8. Zwolnić trwały stan może tylko jego właściciel (palec+wiązanie); wspólny stan (dwa palce na A) jest zwalniany
 *      na wyjściu dopiero przy ostatnim właścicielu.
 *  R9. Po UP palca nic nie może po nim zostać wciśnięte (poza LATCH): resztki są zwalniane i logowane.
 * Wyjątki (plan 6.2): wygaszanie kamery do zera (R7). Retransmisję identycznego stanu (25/50/75 ms) robi
 * adapter po stronie upstreamu (HostPadReporter w :control-android), nie silnik; nie zmienia ona stanu.
 */
class ActionPolicy(
    sink: InputSink,
    private val maxViolations: Int = 1000
) : IntentEmitter {

    /** Jedyna instancja OutputMapper; nikt poza polityką nie ma do niej dostępu. */
    private val mapper: OutputMapper = OutputMapper(sink)

    /** Sink może (opcjonalnie) przyjmować przyczyny wyjść, np. FakeInputSink albo overlay debug. */
    private val causeListener: EmitCauseListener? = sink as? EmitCauseListener

    private data class Holder(val pointerId: Int, val bindingId: String, val latch: Boolean)

    private val holders: HashMap<HeldKey, ArrayList<Holder>> = HashMap()
    private val pointerBinding: HashMap<Int, String> = HashMap()
    private val oneShotBinding: HashMap<Int, String> = HashMap()
    private val oneShotEdges: HashMap<Int, Int> = HashMap()
    private val livePointers: HashSet<Int> = HashSet()

    private val violationLog: ArrayList<PolicyViolation> = ArrayList()
    private var violationTotal: Long = 0

    // Stan bieżącego cyklu.
    private var inCycle: Boolean = false
    private var cycleThread: Thread? = null
    private var cycleKind: CycleKind? = null
    private var cycleEvent: TouchEvent? = null
    private var cyclePointerIds: HashSet<Int> = HashSet()
    private var cycleId: Long = 0
    private var lastAxisCause: EmitCause? = null

    val violations: List<PolicyViolation> get() = violationLog

    /** Bieżący stan pada (debug, testy). */
    fun padSnapshot(): PadSnapshot = mapper.snapshot()

    /** Czy dany stan jest trzymany na wyjściu (debug, testy). */
    fun isHeld(key: HeldKey): Boolean = mapper.isHeld(key)

    /** Niezmiennik R4 (testy): największa liczba różnych wiązań trzymanych przez jeden palec; ma być <= 1. */
    fun maxBindingsPerPointer(): Int {
        val per = HashMap<Int, HashSet<String>>()
        for (list in holders.values) {
            for (h in list) {
                if (h.latch) continue
                var set = per[h.pointerId]
                if (set == null) {
                    set = HashSet()
                    per[h.pointerId] = set
                }
                set.add(h.bindingId)
            }
        }
        var max = 0
        for (s in per.values) if (s.size > max) max = s.size
        return max
    }
    val violationCount: Long get() = violationTotal

    // ---------------------------------------------------------------- cykle

    fun beginTouchCycle(e: TouchEvent, synthetic: Boolean) {
        beginCycle(if (synthetic) CycleKind.TICK else CycleKind.TOUCH, e)
        val ids = HashSet<Int>()
        for (p in e.pointers) ids.add(p.id)
        ids.add(e.actionPointerId)
        cyclePointerIds = ids
    }

    fun endCycle() {
        if (!inCycle) return
        val axisCause = lastAxisCause
        if (axisCause != null) causeListener?.onEmitCause(axisCause)
        mapper.flush()
        causeListener?.onEmitCause(null)
        inCycle = false
        cycleThread = null
        cycleKind = null
        cycleEvent = null
        cyclePointerIds = HashSet()
        lastAxisCause = null
    }

    /** Palec zaczyna cykl życia (wywołuje InputEngine przy DOWN). */
    fun pointerDown(pointerId: Int) {
        val e = cycleEvent
        if (!inCycle || cycleKind != CycleKind.TOUCH || e == null || e.action != TouchAction.DOWN || e.actionPointerId != pointerId) {
            record(pointerId, "-", "pointerDown", "DOWN poza cyklem DOWN tego palca")
            return
        }
        livePointers.add(pointerId)
        pointerBinding.remove(pointerId)
        oneShotBinding.remove(pointerId)
        oneShotEdges.remove(pointerId)
    }

    /** Palec kończy cykl życia (UP lub ponowny DOWN tego samego id). Zwalnia resztki (R9). */
    fun pointerUp(pointerId: Int) {
        if (!inCycle) {
            record(pointerId, "-", "pointerUp", "UP poza cyklem")
            return
        }
        val leftovers = LinkedHashSet<HeldKey>()
        for ((key, list) in holders) {
            for (h in list) {
                if (!h.latch && h.pointerId == pointerId) leftovers.add(key)
            }
        }
        for (key in leftovers) {
            val list = holders[key] ?: continue
            var bindingId = "-"
            val iter = list.iterator()
            while (iter.hasNext()) {
                val h = iter.next()
                if (!h.latch && h.pointerId == pointerId) {
                    bindingId = h.bindingId
                    iter.remove()
                }
            }
            record(pointerId, bindingId, key.toString(), "wejście pozostało po zakończeniu palca; wymuszone zwolnienie")
            if (list.isEmpty()) {
                holders.remove(key)
                forward(Effects.releaseOf(key), causeFor(pointerId, bindingId, null))
            }
        }
        livePointers.remove(pointerId)
        pointerBinding.remove(pointerId)
        oneShotBinding.remove(pointerId)
        oneShotEdges.remove(pointerId)
    }

    /** CANCEL wewnątrz cyklu dotyku: zerowy stan z przyczyną = zdarzenie CANCEL. */
    fun cancelAll() {
        if (!inCycle) {
            record(-1, "-", "cancelAll", "CANCEL poza cyklem")
            return
        }
        resetState()
        emitZeroState(causeFor(-1, "policy:cancel", null))
    }

    /** releaseAll() silnika: osobny cykl RELEASE_ALL. */
    fun releaseAll(nowMs: Long) {
        val nested = inCycle
        if (!nested) beginCycle(CycleKind.RELEASE_ALL, null)
        resetState()
        emitZeroState(EmitCause(cycleId, CycleKind.RELEASE_ALL, null, nowMs, -1, "policy:releaseAll", null))
        if (!nested) endCycle()
    }

    // ---------------------------------------------------------------- intencje

    override fun emit(intent: Intent) {
        // Kolejność sprawdzeń jest częścią zachowania (decyduje, który powód naruszenia trafia do logu):
        // R1, R2, R7, R6, potem dla wciśnięć R3, R4, R5, a dla zwolnień R8. R9 egzekwuje pointerUp.
        val e = ruleR1Cause(intent) ?: return
        val press = Effects.isPress(intent.effect)
        if (!ruleR2PointerInEvent(intent, press)) return
        if (!ruleR7TimeAlone(intent, press)) return
        if (!ruleR6LatchOnDown(intent, e)) return
        if (press) {
            if (!ruleR3LivePointer(intent, e)) return
            if (!ruleR4OneBindingPerPointer(intent)) return
            if (!ruleR5OneShotOnce(intent)) return
            commitPress(intent)
            return
        }
        ruleR8ReleaseByOwner(intent)
    }

    // ---------------------------------------------------------------- reguły (wywoływane w kolejności z emit)

    /** R1: wyjście tylko w otwartym cyklu z dotykiem/tickiem, na wątku cyklu. Zwraca zdarzenie cyklu albo null. */
    private fun ruleR1Cause(intent: Intent): TouchEvent? {
        val pid = intent.pointerId
        val bid = intent.bindingId
        val effect = intent.effect
        if (!inCycle) {
            record(pid, bid, effect.toString(), "brak przyczyny: wyjście poza cyklem onTouch/onTick")
            return null
        }
        if (Thread.currentThread() !== cycleThread) {
            record(pid, bid, effect.toString(), "brak przyczyny: wyjście z innego wątku")
            return null
        }
        val kindNow = cycleKind
        val e = cycleEvent
        if (e == null || (kindNow != CycleKind.TOUCH && kindNow != CycleKind.TICK)) {
            record(pid, bid, effect.toString(), "brak przyczyny: cykl bez zdarzenia dotyku")
            return null
        }
        return e
    }

    /** R2 (tylko wciśnięcia; zwolnienie zawsze zmniejsza stan, a jego przyczyną jest bieżące zdarzenie). */
    private fun ruleR2PointerInEvent(intent: Intent, press: Boolean): Boolean {
        if (press && !cyclePointerIds.contains(intent.pointerId)) {
            record(intent.pointerId, intent.bindingId, intent.effect.toString(), "brak przyczyny: wskaźnik spoza zdarzenia")
            return false
        }
        return true
    }

    /** R7: w cyklu TICK tylko long-press (ONE_SHOT_TIMED), zwolnienia i wygaszanie gałki. */
    private fun ruleR7TimeAlone(intent: Intent, press: Boolean): Boolean {
        val effect = intent.effect
        if (cycleKind == CycleKind.TICK && press && intent.kind != BindingKind.ONE_SHOT_TIMED) {
            if (!(effect is StickSet && isDecay(effect, intent.pointerId, intent.bindingId))) {
                record(intent.pointerId, intent.bindingId, effect.toString(), "sam upływ czasu nie może tworzyć wejścia")
                return false
            }
        }
        return true
    }

    /** R6: LATCH zmienia stan tylko w cyklu DOWN tego samego palca. */
    private fun ruleR6LatchOnDown(intent: Intent, e: TouchEvent): Boolean {
        if (intent.kind != BindingKind.LATCH) return true
        val pid = intent.pointerId
        if (cycleKind != CycleKind.TOUCH || e.action != TouchAction.DOWN || e.actionPointerId != pid) {
            record(pid, intent.bindingId, intent.effect.toString(), "LATCH zmienia stan tylko przy DOWN")
            return false
        }
        return true
    }

    /** R3: wciśnięcie wymaga żywego palca; w CANCEL nic, przy UP palca tylko wejście jednorazowe. */
    private fun ruleR3LivePointer(intent: Intent, e: TouchEvent): Boolean {
        val pid = intent.pointerId
        val bid = intent.bindingId
        val effect = intent.effect
        if (!livePointers.contains(pid)) {
            record(pid, bid, effect.toString(), "wciśnięcie bez aktywnego palca")
            return false
        }
        if (e.action == TouchAction.CANCEL) {
            record(pid, bid, effect.toString(), "wciśnięcie w cyklu CANCEL")
            return false
        }
        if (e.action == TouchAction.UP && e.actionPointerId == pid &&
            intent.kind != BindingKind.ONE_SHOT && intent.kind != BindingKind.ONE_SHOT_TIMED) {
            record(pid, bid, effect.toString(), "wciśnięcie trwałe przy UP palca")
            return false
        }
        return true
    }

    /** R4: palec zmienia wiązanie tylko, gdy poprzednie niczego nie trzyma (LATCH należy do kontrolki, nie do palca). */
    private fun ruleR4OneBindingPerPointer(intent: Intent): Boolean {
        if (intent.kind == BindingKind.LATCH) return true
        val pid = intent.pointerId
        val bid = intent.bindingId
        val current = pointerBinding[pid]
        if (current != null && current != bid && holdsAnything(pid, current)) {
            record(pid, bid, intent.effect.toString(), "drugie wejście z jednego palca (trzyma $current)")
            return false
        }
        return true
    }

    /** R5: najwyżej jedno wiązanie jednorazowe i jedna krawędź wciśnięcia na cykl życia palca. */
    private fun ruleR5OneShotOnce(intent: Intent): Boolean {
        if (intent.kind != BindingKind.ONE_SHOT && intent.kind != BindingKind.ONE_SHOT_TIMED) return true
        val pid = intent.pointerId
        val bid = intent.bindingId
        val effect = intent.effect
        val used = oneShotBinding[pid]
        if (used != null && used != bid) {
            record(pid, bid, effect.toString(), "drugi gest jednorazowy z jednego palca (był $used)")
            return false
        }
        if (isOneShotEdge(effect)) {
            val count = oneShotEdges[pid] ?: 0
            if (count >= 1) {
                record(pid, bid, effect.toString(), "powtórzone wejście jednorazowe")
                return false
            }
            oneShotEdges[pid] = count + 1
        }
        oneShotBinding[pid] = bid
        return true
    }

    /** Wciśnięcie przeszło reguły R1–R7: zapis wiązania i właściciela, wysłanie. */
    private fun commitPress(intent: Intent) {
        val pid = intent.pointerId
        val bid = intent.bindingId
        val latch = intent.kind == BindingKind.LATCH
        if (!latch) pointerBinding[pid] = bid
        val heldKey = Effects.heldKeyOf(intent.effect)
        if (heldKey != null) addHolder(heldKey, Holder(if (latch) -1 else pid, bid, latch))
        forward(intent.effect, causeFor(pid, bid, intent.kind))
    }

    /** R8: zwolnienie, zero albo efekt pusty. Zwalnia tylko właściciel; wspólny stan schodzi przy ostatnim. */
    private fun ruleR8ReleaseByOwner(intent: Intent) {
        val effect = intent.effect
        val pid = intent.pointerId
        val bid = intent.bindingId
        val heldKey = Effects.heldKeyOf(effect) ?: return // np. MouseMove(0,0), Wheel(0): nic do wysłania
        val list = holders[heldKey]
        val latch = intent.kind == BindingKind.LATCH
        val holderPid = if (latch) -1 else pid
        var removed = false
        if (list != null) {
            val iter = list.iterator()
            while (iter.hasNext()) {
                val h = iter.next()
                if (h.pointerId == holderPid && h.bindingId == bid && h.latch == latch) {
                    iter.remove()
                    removed = true
                }
            }
        }
        if (!removed) {
            if (list != null && list.isNotEmpty()) {
                record(pid, bid, effect.toString(), "próba zwolnienia cudzego wejścia")
            }
            // Nic nie było trzymane przez nikogo: zwolnienie jest no-op, nie wysyłamy.
            return
        }
        if (list != null && list.isEmpty()) {
            holders.remove(heldKey)
            forward(effect, causeFor(pid, bid, intent.kind))
        }
        // Gdy zostali inni właściciele, stan na wyjściu pozostaje wciśnięty.
    }

    // ---------------------------------------------------------------- pomocnicze

    private fun beginCycle(kind: CycleKind, e: TouchEvent?) {
        if (inCycle) {
            record(-1, "-", kind.toString(), "zagnieżdżony cykl; poprzedni zamknięty")
            endCycle()
        }
        inCycle = true
        cycleThread = Thread.currentThread()
        cycleKind = kind
        cycleEvent = e
        cyclePointerIds = HashSet()
        cycleId += 1
        lastAxisCause = null
    }

    private fun resetState() {
        holders.clear()
        pointerBinding.clear()
        oneShotBinding.clear()
        oneShotEdges.clear()
        livePointers.clear()
    }

    private fun emitZeroState(cause: EmitCause) {
        causeListener?.onEmitCause(cause)
        mapper.releaseAll()
        lastAxisCause = null
    }

    private fun forward(effect: OutputEffect, cause: EmitCause) {
        causeListener?.onEmitCause(cause)
        mapper.apply(effect)
        if (effect is StickSet) lastAxisCause = cause
    }

    private fun causeFor(pid: Int, bid: String, kind: BindingKind?): EmitCause {
        val e = cycleEvent
        return EmitCause(cycleId, cycleKind ?: CycleKind.TOUCH, e?.action, e?.timeMs ?: 0L, pid, bid, kind)
    }

    private fun addHolder(key: HeldKey, h: Holder) {
        var list = holders[key]
        if (list == null) {
            list = ArrayList()
            holders[key] = list
        }
        if (!list.contains(h)) list.add(h)
    }

    private fun holdsAnything(pid: Int, bid: String): Boolean {
        for (list in holders.values) {
            for (h in list) {
                if (!h.latch && h.pointerId == pid && h.bindingId == bid) return true
            }
        }
        return false
    }

    private fun isOneShotEdge(e: OutputEffect): Boolean =
        e is AppAction || e is Wheel || e is HWheel || Effects.isHolding(e)

    /** Wygaszanie: ten sam właściciel, moduł nie rośnie na żadnej osi, bez zmiany znaku. */
    private fun isDecay(e: StickSet, pid: Int, bid: String): Boolean {
        val list = holders[HeldStick(e.stick)] ?: return false
        var own = false
        for (h in list) if (h.pointerId == pid && h.bindingId == bid) own = true
        if (!own) return false
        val cur = mapper.stickValue(e.stick)
        val eps = 1e-4f
        val okX = abs(e.x) <= abs(cur[0]) + eps && e.x * cur[0] >= 0f
        val okY = abs(e.y) <= abs(cur[1]) + eps && e.y * cur[1] >= 0f
        return okX && okY
    }

    private fun record(pid: Int, bid: String, effect: String, reason: String) {
        violationTotal += 1
        if (violationLog.size >= maxViolations) violationLog.removeAt(0)
        val t = cycleEvent?.timeMs ?: 0L
        violationLog.add(PolicyViolation(t, cycleKind, pid, bid, effect, reason))
    }
}

/** Odbiorca intencji z rozpoznawaczy. W silniku jedyną implementacją jest ActionPolicy. */
interface IntentEmitter {
    fun emit(intent: Intent)
}
