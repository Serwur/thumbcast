package app.thumbcast.editor.state

import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.HandednessTransform
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    private val eps = 1e-4f

    private fun state(): EditorState = EditorState(Fixtures.padPlus())

    private fun EditorState.control(id: String): Control = profile.controls.first { it.id == id }

    @Test
    fun fixturesAreValid() {
        assertEquals(emptyList<String>(), ProfileValidator.validate(Fixtures.padPlus()))
        assertEquals(emptyList<String>(), ProfileValidator.validate(Fixtures.keyboardMouse()))
    }

    @Test
    fun dragIsOneUndoStepAndComputedFromGestureStart() {
        var s = state().select("a").beginGesture()
        s = s.dragMove("a", -0.1f, -0.1f)
        s = s.dragMove("a", -0.2f, -0.2f)
        s = s.endGesture()
        val r = s.control("a").rect
        assertEquals(0.62f, r.x, eps)
        assertEquals(0.59f, r.y, eps)
        assertEquals(1, s.undoStack.size)
        assertTrue(s.dirty)
        val undone = s.undo()
        assertEquals(Fixtures.padPlus(), undone.profile)
        assertFalse(undone.dirty)
        assertEquals(s.profile, undone.redo().profile)
    }

    @Test
    fun gestureWithoutChangeAddsNoUndoStep() {
        val s = state().beginGesture().dragMove("a", 0f, 0f).endGesture()
        assertTrue(s.undoStack.isEmpty())
        assertFalse(s.canUndo)
    }

    @Test
    fun rebaseKeepsMoveWhenPinchStarts() {
        var s = state().beginGesture().dragMove("a", -0.1f, 0f)
        val moved = s.control("a").rect
        s = s.rebaseGesture().pinchScale("a", 1.0f).endGesture()
        assertEquals(moved, s.control("a").rect)
        assertEquals(1, s.undoStack.size)
    }

    @Test
    fun labelEditsCoalesceIntoOneStep() {
        var s = state().select("a")
        for (t in listOf("J", "Ju", "Jum", "Jump")) s = s.setLabel("a", t)
        assertEquals("Jump", s.control("a").label)
        assertEquals(1, s.undoStack.size)
        s = s.setLabel("a", "")
        assertNull(s.control("a").label)
        assertNull(s.undo().control("a").label)
    }

    @Test
    fun newEditClearsRedo() {
        val s = state().setShape("a", Shape.RECT).undo()
        assertTrue(s.canRedo)
        val t = s.setShape("a", Shape.ROUNDED_RECT)
        assertFalse(t.canRedo)
    }

    @Test
    fun undoStackIsBounded() {
        var s = state()
        repeat(EditorState.MAX_UNDO + 20) { i -> s = s.setName("n$i") ; s = s.select(null).setShape("a", if (i % 2 == 0) Shape.RECT else Shape.CIRCLE) }
        assertEquals(EditorState.MAX_UNDO, s.undoStack.size)
    }

    @Test
    fun addControlCreatesUniqueSelectedValidControl() {
        var s = state()
        for (kind in NewControlKind.values()) s = s.addControl(kind, 20f / 9f)
        s = s.addControl(NewControlKind.BUTTON, 20f / 9f)
        val ids = s.profile.controls.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("button_2", s.selectedId)
        val added = s.profile.controls.drop(Fixtures.padPlus().controls.size)
        assertTrue(added[0] is ButtonControl)
        assertTrue(added[1] is StickControl)
        assertTrue(added[2] is ModifierControl)
        assertTrue(added[3] is SystemControl)
        // Prawa gałka jest zajęta przez kamerę pad_right_stick, a lewa przez „move”, więc druga gałka da konflikt:
        // walidator go zgłosi i zapis będzie zablokowany.
        assertTrue(s.errors().any { it.contains(ProfileValidator.ERR_STICK_AXIS_CONFLICT) })
        val withoutStick = s.select(added[1].id).deleteSelected()
        assertEquals(emptyList<String>(), withoutStick.errors())
        // Przycisk ma kształt koła: w px bok w ≈ h (proporcja 20:9).
        val r = added[0].rect
        assertEquals(r.h, r.w * 20f / 9f, 0.002f)
    }

    @Test
    fun addStickInKeyboardModeHasKbmKeys() {
        val s = EditorState(Fixtures.keyboardMouse()).addControl(NewControlKind.STICK, 2f)
        val stick = s.selected as StickControl
        assertEquals(ControlFactory.DEFAULT_KBM_KEYS, stick.kbmKeys)
        val withButtons = s.addControl(NewControlKind.BUTTON, 2f).addControl(NewControlKind.MODIFIER, 2f)
        assertEquals(emptyList<String>(), withButtons.errors())
    }

    @Test
    fun duplicateCopiesWithNewIdAndNoChord() {
        val base = Fixtures.padPlus()
        val withChord = base.copy(
            controls = base.controls.map {
                if (it is ButtonControl && it.id == "a") it.copy(chord = Output.Pad(PadButton.LB)) else it
            },
        )
        val s = EditorState(withChord).select("a").duplicateSelected()
        val dup = s.selected as ButtonControl
        assertEquals("button_1", dup.id)
        assertNull(dup.chord)
        assertEquals(Output.Pad(PadButton.A), dup.output)
        assertNotEquals(s.control("a").rect, dup.rect)
        // Oryginał z akordem nadal jest błędem; removeChord go usuwa.
        assertTrue(s.errors().any { it.contains(ProfileValidator.ERR_CHORD_UNSUPPORTED) })
        assertEquals(emptyList<String>(), s.removeChord("a").errors())
    }

    @Test
    fun deleteRemovesAndClearsSelection() {
        val s = state().select("x").deleteSelected()
        assertNull(s.selectedId)
        assertTrue(s.profile.controls.none { it.id == "x" })
        assertTrue(s.undo().profile.controls.any { it.id == "x" })
    }

    @Test
    fun undoDropsSelectionOfRemovedControl() {
        val s = state().addControl(NewControlKind.BUTTON, 2f)
        assertEquals("button_1", s.selectedId)
        assertNull(s.undo().selectedId)
    }

    @Test
    fun mirrorUsesHandednessTransform() {
        val s = state().mirror()
        assertEquals(HandednessTransform.mirror(Fixtures.padPlus()), s.profile)
        assertEquals(Fixtures.padPlus(), s.mirror().profile)
    }

    @Test
    fun setOutputAcceptsOnlyCatalogOutputs() {
        var s = state()
        s = s.setOutput("a", Output.App("menu"))
        s = s.setOutput("a", Output.MouseWheel)
        s = s.setOutput("a", Output.Stick(PadStick.RIGHT))
        s = s.setOutput("a", Output.Key("NOT_A_KEY"))
        assertEquals(Output.Pad(PadButton.A), (s.control("a") as ButtonControl).output)
        s = s.setOutput("a", Output.Key("F1"))
        assertEquals(Output.Key("F1"), (s.control("a") as ButtonControl).output)
        s = s.setOutput("lt", Output.Pad(PadButton.A))
        assertEquals(Output.Trigger(PadTrigger.LT), (s.control("lt") as ModifierControl).output)
        s = s.setOutput("lt", Output.Pad(PadButton.RB))
        assertEquals(Output.Pad(PadButton.RB), (s.control("lt") as ModifierControl).output)
        s = s.setOutput("menu", Output.Pad(PadButton.A))
        assertEquals(Output.App("menu"), (s.control("menu") as SystemControl).output)
    }

    @Test
    fun keyboardModeOffersNoPadOutputs() {
        val kbm = EditorState(Fixtures.keyboardMouse())
        val s = kbm.setOutput("one", Output.Pad(PadButton.A)).setOutput("shift", Output.Trigger(PadTrigger.LT))
        assertSame(kbm.profile, s.profile)
        for (o in OutputCatalog.buttonOptions(ProfileMode.KEYBOARD_MOUSE).flatMap { it.outputs }) {
            assertFalse(o is Output.Pad || o is Output.Trigger)
        }
    }

    @Test
    fun latchOnlyForModifier() {
        val s = state().setBehavior("a", Behavior.LATCH).setBehavior("lt", Behavior.LATCH)
        assertEquals(Behavior.HOLD, (s.control("a") as ButtonControl).behavior)
        assertEquals(Behavior.LATCH, (s.control("lt") as ModifierControl).behavior)
        assertEquals(emptyList<String>(), s.errors())
    }

    @Test
    fun stickProperties() {
        var s = state().setFloating("move", false).setSprintLock("move", true)
        s = s.sliderDeadzone("move", 0.9f).endGesture()
        val stick = s.control("move") as StickControl
        assertFalse(stick.floating)
        assertTrue(stick.sprintLock)
        assertEquals(ProfileValidator.DEADZONE_MAX, stick.deadzone, eps)
        assertEquals(emptyList<String>(), s.errors())
    }

    @Test
    fun opacityAndSizeSliders() {
        var s = state().sliderOpacity(0f).endGesture()
        assertEquals(ProfileValidator.OPACITY_MIN, s.profile.global.opacity, eps)
        s = s.sliderSize("a", 0.3f).sliderSize("a", 0.25f).endGesture()
        assertEquals(0.25f, s.control("a").rect.h, eps)
        assertEquals(2, s.undoStack.size)
        assertEquals(emptyList<String>(), s.errors())
    }

    @Test
    fun blankNameBlocksSave() {
        val s = state().setName(" ")
        assertTrue(EditorIo.saveErrors(s.profile).isNotEmpty())
        assertTrue(EditorIo.encodeForSave(s.profile).isFailure)
        val ok = s.setName("Mój układ")
        val json = EditorIo.encodeForSave(ok.profile).getOrThrow()
        assertEquals(ok.profile, ProfileCodec.decode(json).getOrThrow())
    }

    @Test
    fun decodeForEditingOpensInvalidProfileButRejectsForbiddenTypes() {
        val invalid = ProfileCodec.encode(Fixtures.padPlus().copy(name = ""))
        assertTrue(EditorIo.decodeForEditing(invalid).isSuccess)
        val macro = invalid.replace("\"type\": \"button\"", "\"type\": \"macro\"")
        assertTrue(EditorIo.decodeForEditing(macro).isFailure)
    }

    @Test
    fun markSavedClearsDirty() {
        val s = state().setShape("a", Shape.RECT)
        assertTrue(s.dirty)
        assertFalse(s.markSaved().dirty)
    }
}
