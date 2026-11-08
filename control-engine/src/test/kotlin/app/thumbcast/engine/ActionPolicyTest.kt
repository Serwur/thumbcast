package app.thumbcast.engine

import app.thumbcast.model.MouseBtn
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testy reguł ActionPolicy wywoływanej bezpośrednio (bez rozpoznawaczy). */
class ActionPolicyTest {

    private val sink: FakeInputSink = FakeInputSink()
    private val policy: ActionPolicy = ActionPolicy(sink)

    private fun ev(action: TouchAction, id: Int, t: Long, vararg ids: Int): TouchEvent {
        val list = ArrayList<TouchPointer>()
        for (i in ids) list.add(TouchPointer(i, 10f, 10f))
        return TouchEvent(action, id, list, t)
    }

    private fun downCycle(id: Int, t: Long, body: () -> Unit) {
        policy.beginTouchCycle(ev(TouchAction.DOWN, id, t, id), false)
        policy.pointerDown(id)
        body()
        policy.endCycle()
    }

    private fun pressA(id: Int): Intent = Intent(id, "control:a", BindingKind.HOLD, PadButtonSet(PadButton.A, true))
    private fun releaseA(id: Int): Intent = Intent(id, "control:a", BindingKind.HOLD, PadButtonSet(PadButton.A, false))

    @Test
    fun outputOutsideCycleIsRejected() {
        policy.emit(pressA(1))
        assertEquals(0, sink.records.size)
        assertEquals(1, policy.violations.size)
        assertTrue(policy.violations[0].reason.contains("brak przyczyny"))
    }

    @Test
    fun outputAfterCycleEndedIsRejected() {
        downCycle(1, 100L) { policy.emit(pressA(1)) }
        // „Timer” po zakończeniu cyklu próbuje zwolnić i wcisnąć ponownie.
        policy.emit(releaseA(1))
        policy.emit(pressA(1))
        assertEquals(1, sink.ofCall(SinkCall.PAD).size)
        assertEquals(2, policy.violations.size)
    }

    @Test
    fun outputFromOtherThreadIsRejected() {
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        val t = Thread { policy.emit(pressA(1)) }
        t.start()
        t.join()
        policy.endCycle()
        assertEquals(0, sink.records.size)
        assertEquals(1, policy.violations.size)
        assertTrue(policy.violations[0].reason.contains("wątku"))
    }

    @Test
    fun pressNeedsLivePointerFromEvent() {
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 100L, 1), false)
        policy.emit(pressA(1)) // palec 1 nie miał DOWN
        policy.emit(pressA(7)) // palca 7 nie ma w zdarzeniu
        policy.endCycle()
        assertEquals(0, sink.records.size)
        assertEquals(2, policy.violations.size)
    }

    @Test
    fun onePointerOneInput() {
        downCycle(1, 100L) {
            policy.emit(pressA(1))
            policy.emit(Intent(1, "control:x", BindingKind.HOLD, PadButtonSet(PadButton.X, true)))
        }
        assertEquals(PadFlags.A, sink.lastPad!!.buttons)
        assertEquals(1, policy.violations.size)
        assertEquals(1, policy.maxBindingsPerPointer())
    }

    @Test
    fun switchBindingAllowedAfterRelease() {
        downCycle(1, 100L) { policy.emit(pressA(1)) }
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 120L, 1), false)
        policy.emit(releaseA(1))
        policy.emit(Intent(1, "control:x", BindingKind.HOLD, PadButtonSet(PadButton.X, true)))
        policy.endCycle()
        assertEquals(PadFlags.X, sink.lastPad!!.buttons)
        assertEquals(0, policy.violations.size)
    }

    @Test
    fun timeAloneCannotPress() {
        downCycle(1, 100L) { }
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 600L, 1), true)
        policy.emit(pressA(1))
        policy.emit(Intent(1, "gesture:tap", BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, true)))
        policy.endCycle()
        assertEquals(0, sink.records.size)
        assertEquals(2, policy.violations.size)
    }

    @Test
    fun longPressAllowedInTick() {
        downCycle(1, 100L) { }
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 600L, 1), true)
        policy.emit(Intent(1, "gesture:longpress", BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, true)))
        policy.endCycle()
        assertEquals(1, sink.ofCall(SinkCall.BUTTON).size)
        assertEquals(0, policy.violations.size)
    }

    @Test
    fun decayAllowedGrowthRejectedInTick() {
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        policy.emit(Intent(1, "gesture:camera", BindingKind.HOLD, StickSet(PadStick.RIGHT, 0.8f, 0f)))
        policy.endCycle()
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 130L, 1), true)
        policy.emit(Intent(1, "gesture:camera", BindingKind.HOLD, StickSet(PadStick.RIGHT, 0.4f, 0f)))
        policy.endCycle()
        assertEquals(0, policy.violations.size)
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 140L, 1), true)
        policy.emit(Intent(1, "gesture:camera", BindingKind.HOLD, StickSet(PadStick.RIGHT, 0.6f, 0f)))
        policy.emit(Intent(1, "gesture:camera", BindingKind.HOLD, StickSet(PadStick.RIGHT, -0.2f, 0f)))
        policy.endCycle()
        assertEquals(2, policy.violations.size)
        assertEquals(Math.round(0.4f * 32767f), sink.lastPad!!.rx.toInt())
    }

    @Test
    fun oneShotOnlyOncePerFinger() {
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        policy.endCycle()
        policy.beginTouchCycle(ev(TouchAction.UP, 1, 150L, 1), false)
        policy.emit(Intent(1, "gesture:tap", BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, true)))
        policy.emit(Intent(1, "gesture:tap", BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, false)))
        policy.emit(Intent(1, "gesture:tap", BindingKind.ONE_SHOT, MouseButtonSet(MouseBtn.LEFT, true)))
        policy.emit(Intent(1, "gesture:longpress", BindingKind.ONE_SHOT_TIMED, MouseButtonSet(MouseBtn.RIGHT, true)))
        policy.pointerUp(1)
        policy.endCycle()
        assertEquals(1, sink.pressEdges.size)
        assertEquals(2, policy.violations.size)
        assertTrue(sink.isZero())
    }

    @Test
    fun holdPressRejectedAtUp() {
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        policy.endCycle()
        policy.beginTouchCycle(ev(TouchAction.UP, 1, 150L, 1), false)
        policy.emit(pressA(1))
        policy.pointerUp(1)
        policy.endCycle()
        assertEquals(0, sink.records.size)
        assertEquals(1, policy.violations.size)
    }

    @Test
    fun latchOnlyOnDown() {
        downCycle(1, 100L) { }
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 120L, 1), false)
        policy.emit(Intent(1, "control:lt", BindingKind.LATCH, KeySet("SHIFT", true)))
        policy.endCycle()
        assertEquals(0, sink.records.size)
        assertEquals(1, policy.violations.size)
        downCycle(2, 130L) { policy.emit(Intent(2, "control:lt", BindingKind.LATCH, KeySet("SHIFT", true))) }
        assertEquals(setOf("SHIFT"), sink.keysDown)
    }

    @Test
    fun leftoverIsForceReleasedAtUp() {
        downCycle(1, 100L) { policy.emit(pressA(1)) }
        policy.beginTouchCycle(ev(TouchAction.UP, 1, 150L, 1), false)
        policy.pointerUp(1) // rozpoznawacz „zapomniał” zwolnić
        policy.endCycle()
        assertEquals(0, sink.lastPad!!.buttons)
        assertEquals(1, policy.violations.size)
    }

    @Test
    fun cannotReleaseSomeoneElsesInput() {
        downCycle(1, 100L) { policy.emit(pressA(1)) }
        downCycle(2, 110L) { policy.emit(releaseA(2)) }
        assertEquals(PadFlags.A, sink.lastPad!!.buttons)
        assertEquals(1, policy.violations.size)
    }

    @Test
    fun cancelAndReleaseAllGiveZeroStateWithCause() {
        downCycle(1, 100L) {
            policy.emit(pressA(1))
            policy.emit(Intent(1, "control:a", BindingKind.HOLD, KeySet("M", true)))
        }
        policy.beginTouchCycle(ev(TouchAction.CANCEL, -1, 120L, 1), false)
        policy.cancelAll()
        policy.endCycle()
        assertTrue(sink.isZero())
        val last = sink.records[sink.records.size - 1]
        assertEquals(TouchAction.CANCEL, last.cause?.action)
        downCycle(3, 130L) { policy.emit(pressA(3)) }
        policy.releaseAll(140L)
        assertTrue(sink.isZero())
        assertEquals(CycleKind.RELEASE_ALL, sink.records[sink.records.size - 1].cause?.cycle)
        assertEquals(PadSnapshot.ZERO, policy.padSnapshot())
    }

    @Test
    fun everyRecordHasCause() {
        downCycle(1, 100L) { policy.emit(pressA(1)) }
        policy.beginTouchCycle(ev(TouchAction.UP, 1, 150L, 1), false)
        policy.emit(releaseA(1))
        policy.pointerUp(1)
        policy.endCycle()
        for (r in sink.records) {
            val c = assertNotNull(r.cause)
            assertEquals(1, c.pointerId)
        }
    }
}
