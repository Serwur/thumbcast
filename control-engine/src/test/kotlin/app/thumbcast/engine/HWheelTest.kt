package app.thumbcast.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kółko poziome (HWheel): mapowanie na InputSink.mouseHScroll i reguły ActionPolicy. */
class HWheelTest {

    private fun ev(action: TouchAction, id: Int, t: Long, vararg ids: Int): TouchEvent {
        val list = ArrayList<TouchPointer>()
        for (i in ids) list.add(TouchPointer(i, 10f, 10f))
        return TouchEvent(action, id, list, t)
    }

    @Test
    fun mapperSendsHorizontalScrollAndSkipsZero() {
        val sink = FakeInputSink()
        val mapper = OutputMapper(sink)
        mapper.apply(HWheel(0))
        mapper.apply(HWheel(-120))
        mapper.apply(Wheel(240))
        assertEquals(listOf(SinkCall.HWHEEL, SinkCall.WHEEL), sink.records.map { it.call })
        assertEquals(-120, sink.ofCall(SinkCall.HWHEEL).single().wheel)
        assertEquals(240, sink.ofCall(SinkCall.WHEEL).single().wheel)
    }

    @Test
    fun policyPassesHWheelFromTouchCycle() {
        val sink = FakeInputSink()
        val policy = ActionPolicy(sink)
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        policy.endCycle()
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 110L, 1), false)
        policy.emit(Intent(1, "gesture:scroll", BindingKind.HOLD, HWheel(60)))
        policy.endCycle()
        val r = sink.ofCall(SinkCall.HWHEEL).single()
        assertEquals(60, r.wheel)
        assertEquals(TouchAction.MOVE, r.cause?.action)
        assertEquals(0, policy.violations.size)
    }

    @Test
    fun policyRejectsHWheelFromTimeOrDeadPointer() {
        val sink = FakeInputSink()
        val policy = ActionPolicy(sink)
        policy.beginTouchCycle(ev(TouchAction.DOWN, 1, 100L, 1), false)
        policy.pointerDown(1)
        policy.endCycle()
        // R7: tick (sam czas) nie przewija.
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 200L, 1), true)
        policy.emit(Intent(1, "gesture:scroll", BindingKind.HOLD, HWheel(120)))
        policy.endCycle()
        // R3: palec 2 nie dotyka.
        policy.beginTouchCycle(ev(TouchAction.MOVE, -1, 210L, 1, 2), false)
        policy.emit(Intent(2, "gesture:scroll", BindingKind.HOLD, HWheel(120)))
        policy.endCycle()
        // R1: poza cyklem.
        policy.emit(Intent(1, "gesture:scroll", BindingKind.HOLD, HWheel(120)))
        assertTrue(sink.ofCall(SinkCall.HWHEEL).isEmpty())
        assertEquals(3, policy.violations.size)
    }
}
