package app.thumbcast.android.overlay

import app.thumbcast.engine.Clock
import app.thumbcast.engine.EngineVisualState
import app.thumbcast.engine.InputEngine
import app.thumbcast.engine.InputSink
import app.thumbcast.engine.PointerVisual
import app.thumbcast.engine.StickVisual
import app.thumbcast.engine.TouchAction
import app.thumbcast.engine.TouchEvent
import app.thumbcast.engine.TouchPointer
import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.GlobalSettings
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.MouseBtn
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migawka rysowania z prawdziwym silnikiem (czyste JVM): wygląd = to, co silnik rozpoznał.
 * Ekran 2000 x 1000. A: x 1720..1900, y 700..860; X: x 1520..1700; LT (LATCH): x 1400..1600, y 100..220;
 * gałka pływająca „move”: x 80..560, y 450..950.
 */
class OverlaySnapshotTest {

    private val w = 2000
    private val h = 1000

    private object NoSink : InputSink {
        override fun padState(buttons: Int, leftStickX: Short, leftStickY: Short, rightStickX: Short, rightStickY: Short, leftTrigger: Byte, rightTrigger: Byte) {}
        override fun key(code: String, down: Boolean) {}
        override fun mouseMoveRelative(dx: Int, dy: Int) {}
        override fun mouseButton(button: MouseBtn, down: Boolean) {}
        override fun mousePosition(x: Int, y: Int, refWidth: Int, refHeight: Int) {}
        override fun mouseWheel(highResDelta: Int) {}
        override fun app(action: String) {}
    }

    private class Rig(val profile: Profile, w: Int, h: Int) {
        var now: Long = 1000L
        val engine = InputEngine(profile, NoSink, object : Clock { override fun nowMs(): Long = now }, VideoGeometry().viewport(w, h))
        val layout = ControlLayout.of(profile, w, h)
        private val pos = LinkedHashMap<Int, TouchPointer>()

        fun down(id: Int, x: Float, y: Float) {
            pos[id] = TouchPointer(id, x, y)
            engine.onTouch(TouchEvent(TouchAction.DOWN, id, ArrayList(pos.values), now))
        }

        fun move(id: Int, x: Float, y: Float) {
            now += 16
            pos[id] = TouchPointer(id, x, y)
            engine.onTouch(TouchEvent(TouchAction.MOVE, -1, ArrayList(pos.values), now))
        }

        fun up(id: Int) {
            now += 16
            engine.onTouch(TouchEvent(TouchAction.UP, id, ArrayList(pos.values), now))
            pos.remove(id)
        }

        fun snapshot(debug: DebugInfo? = null): OverlaySnapshot =
            OverlaySnapshotBuilder.build(layout, engine.visualState(), profile.global.opacity, debug)
    }

    private fun profile(slideToPress: Boolean = false, opacity: Float = 0.8f): Profile = Profile(
        schema = 1,
        id = "t",
        name = "t",
        mode = ProfileMode.PAD_PLUS,
        global = GlobalSettings(slideToPress = slideToPress, opacity = opacity),
        controls = listOf(
            StickControl(id = "move", rect = Rect(0.04f, 0.45f, 0.24f, 0.50f), floating = true),
            ButtonControl(id = "a", rect = Rect(0.86f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.A)),
            ButtonControl(id = "x", rect = Rect(0.76f, 0.70f, 0.09f, 0.16f), output = Output.Pad(PadButton.X)),
            ModifierControl(
                id = "lt", rect = Rect(0.70f, 0.10f, 0.10f, 0.12f), output = Output.Trigger(PadTrigger.LT),
                behavior = Behavior.LATCH,
            ),
        ),
    )

    private fun OverlaySnapshot.button(id: String): ButtonItem =
        items.first { it.control.control.id == id } as ButtonItem

    private fun OverlaySnapshot.stick(id: String): StickItem =
        items.first { it.control.control.id == id } as StickItem

    @Test
    fun itemsFollowLayoutOrderAndOpacity() {
        val s = Rig(profile(opacity = 0.8f), w, h).snapshot()
        assertEquals(listOf("move", "a", "x", "lt"), s.items.map { it.control.control.id })
        assertEquals(204, s.alpha)
        assertNull(s.debug)
        assertFalse(s.button("a").pressed)
    }

    @Test
    fun hitSlopPressIsDrawnPressed() {
        val r = Rig(profile(), w, h)
        // Pod grafiką A (y > 860), ale w strefie aktywacji silnika: dawny hit test rysunku tego nie widział.
        r.down(1, 1810f, 875f)
        assertFalse(r.layout.byId("a")!!.bounds.contains(1810f, 875f))
        assertTrue(r.snapshot().button("a").pressed)
        r.up(1)
        assertFalse(r.snapshot().button("a").pressed)
    }

    @Test
    fun slideToPressMovesPressedLook() {
        val r = Rig(profile(slideToPress = true), w, h)
        r.down(1, 1610f, 780f)
        assertTrue(r.snapshot().button("x").pressed)
        r.move(1, 1810f, 780f)
        val s = r.snapshot()
        assertTrue(s.button("a").pressed)
        assertFalse("dawny właściciel z DOWN nie zostaje podświetlony", s.button("x").pressed)
    }

    @Test
    fun latchStaysPressedAfterUp() {
        val r = Rig(profile(), w, h)
        r.down(1, 1500f, 160f)
        r.up(1)
        assertTrue(r.snapshot().button("lt").pressed)
        r.down(2, 1500f, 160f)
        r.up(2)
        assertFalse(r.snapshot().button("lt").pressed)
    }

    @Test
    fun stickRadiusAndKnobComeFromEngine() {
        val r = Rig(profile(), w, h)
        val idle = r.snapshot().stick("move")
        val engineIdle = r.engine.visualState().sticks.getValue("move")
        assertEquals(engineIdle.radius, idle.radius, 1e-4f)
        assertEquals(150f, idle.radius, 1e-4f) // 15% krótszego boku
        assertFalse(idle.active)

        r.down(1, 300f, 700f)
        r.move(1, 375f, 700f)
        val s = r.snapshot().stick("move")
        val e = r.engine.visualState().sticks.getValue("move")
        assertTrue(s.active)
        assertEquals(e.radius, s.radius, 1e-4f)
        assertEquals(300f, s.centerX, 1e-4f)
        assertEquals(700f, s.centerY, 1e-4f)
        assertEquals(375f, s.knobX, 1e-3f)
        assertEquals(700f, s.knobY, 1e-3f)

        // Poza promieniem: gałka na obwodzie.
        r.move(1, 300f, 1000f)
        val far = r.snapshot().stick("move")
        assertEquals(300f, far.knobX, 1e-3f)
        assertEquals(850f, far.knobY, 1e-3f)
    }

    @Test
    fun stickWithoutEngineStateRestsInBoundsCentre() {
        val layout = ControlLayout.of(profile(), w, h)
        val s = OverlaySnapshotBuilder.build(layout, EngineVisualState.EMPTY, 1f, null).stick("move")
        assertEquals(320f, s.centerX, 1e-3f)
        assertEquals(700f, s.centerY, 1e-3f)
        assertEquals(240f, s.radius, 1e-3f)
        assertEquals(s.centerX, s.knobX, 0f)
    }

    @Test
    fun alphaClamps() {
        assertEquals(0, OverlaySnapshotBuilder.alphaOf(-1f))
        assertEquals(255, OverlaySnapshotBuilder.alphaOf(2f))
        assertEquals(0, OverlaySnapshotBuilder.alphaOf(Float.NaN))
        assertEquals(128, OverlaySnapshotBuilder.alphaOf(0.5f))
    }

    @Test
    fun debugOverlayUsesEnginePointers() {
        val r = Rig(profile(), w, h)
        r.down(3, 1810f, 780f)
        val info = DebugInfo(listOf(3), 0x1000, 2L, 3L, 10, 0, controlsHidden = true, gamepadPresent = true)
        val d = r.snapshot(info).debug!!
        assertEquals(listOf(DebugPointer(3, 1810f, 780f)), d.pointers)
        assertEquals(
            listOf("ptr 1 [3]", "pad 0x1000", "lat last 2 ms, p95 3 ms (n=10)", "policy violations 0", "controls hidden (gamepad)"),
            d.lines,
        )
        assertEquals(4, OverlaySnapshotBuilder.debugLines(info.copy(policyViolations = null)).size)
    }

    @Test
    fun textSizeIsClampedInDp() {
        assertEquals(18f * 2f, OverlayRenderer.textSizeFor(PxRect(0f, 0f, 500f, 500f), 2f), 1e-4f)
        assertEquals(9f * 2f, OverlayRenderer.textSizeFor(PxRect(0f, 0f, 10f, 10f), 2f), 1e-4f)
        assertEquals(32f, OverlayRenderer.textSizeFor(PxRect(0f, 0f, 100f, 200f), 2f), 1e-4f)
    }

    // ---------------------------------------------------------------- haptyka z przejść stanu

    private fun st(vararg owners: Pair<Int, String?>, sticks: Set<String> = setOf("move")): EngineVisualState =
        EngineVisualState(
            owners.map { PointerVisual(it.first, 0f, 0f, 0f, 0f, it.second) },
            emptySet(), emptySet(),
            sticks.associateWith { StickVisual(it, true, false, -1, 0f, 0f, 1f, 0f, 0f) },
        )

    @Test
    fun feedbackFromEngineTransitions() {
        val none = st()
        assertEquals(TouchFeedback.Result(1, 0), TouchFeedback.between(none, st(1 to "a")))
        assertEquals(TouchFeedback.Result(0, 1), TouchFeedback.between(st(1 to "a"), none))
        assertEquals("slide-to-press: samo wciśnięcie", TouchFeedback.Result(1, 0), TouchFeedback.between(st(1 to "x"), st(1 to "a")))
        assertEquals("gałka nie wibruje", TouchFeedback.Result(0, 0), TouchFeedback.between(none, st(1 to "move")))
        assertEquals("gest nie wibruje", TouchFeedback.Result(0, 0), TouchFeedback.between(none, st(1 to null)))
        assertEquals("ruch bez zmiany właściciela", TouchFeedback.Result(0, 0), TouchFeedback.between(st(1 to "a"), st(1 to "a")))
        assertEquals(TouchFeedback.Result(0, 1), TouchFeedback.between(st(1 to "a", 2 to "move"), st(2 to "move")))
    }

    @Test
    fun feedbackFiresForSlopPressOnRealEngine() {
        val r = Rig(profile(), w, h)
        val before = r.engine.visualState()
        r.down(1, 1810f, 875f)
        assertEquals(TouchFeedback.Result(1, 0), TouchFeedback.between(before, r.engine.visualState()))
    }
}
