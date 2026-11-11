package app.thumbcast.editor.state

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.KeyCodes
import app.thumbcast.model.Output
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import app.thumbcast.model.Behavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * FAIR_PLAY.md w edytorze: żadna sekwencja operacji nie tworzy akordu ani typu zakazanego, każda kontrolka
 * ma jedno wyjście z katalogu, a geometria zostaje na ekranie.
 */
class FairPlayEditorTest {

    @Test
    fun catalogKeysCoverAllKeyCodes() {
        assertEquals(KeyCodes.ALL, OutputCatalog.KEYS.toSet())
        assertEquals(KeyCodes.ALL.size, OutputCatalog.KEYS.size)
    }

    /** Każde wyjście z katalogu przechodzi walidator dla swojego typu kontrolki i trybu. */
    @Test
    fun everyCatalogOutputIsValid() {
        for (base in listOf(Fixtures.padPlus(), Fixtures.keyboardMouse())) {
            for (c in base.controls.filterNot { it is StickControl }) {
                for (o in OutputCatalog.optionsFor(c, base.mode).flatMap { it.outputs }) {
                    val s = EditorState(base).setOutput(c.id, o)
                    val errors = s.errors()
                    assertEquals("${base.mode} ${c.id} -> $o", emptyList<String>(), errors)
                }
            }
        }
    }

    @Test
    fun randomEditsNeverCreateChordsOrForbiddenTypes() {
        val rnd = Random(20261111)
        for (start in listOf(Fixtures.padPlus(), Fixtures.keyboardMouse())) {
            var s = EditorState(start)
            repeat(3000) {
                s = randomStep(s, rnd)
                checkInvariants(s.profile)
            }
        }
    }

    private fun randomStep(s: EditorState, rnd: Random): EditorState {
        val ids: List<String> = s.profile.controls.map { it.id }
        val id: String? = if (ids.isEmpty()) null else ids[rnd.nextInt(ids.size)]
        val f = { rnd.nextFloat() * 2.4f - 1.2f }
        return when (rnd.nextInt(18)) {
            0 -> s.select(id)
            1 -> if (id == null) s else s.beginGesture().dragMove(id, f(), f()).endGesture()
            2 -> if (id == null) s else s.beginGesture().dragResize(id, f(), f()).endGesture()
            3 -> if (id == null) s else s.beginGesture().pinchScale(id, rnd.nextFloat() * 4f).endGesture()
            4 -> s.addControl(NewControlKind.values()[rnd.nextInt(4)], 0.5f + rnd.nextFloat() * 2f)
            5 -> s.select(id).duplicateSelected()
            6 -> if (s.profile.controls.size > 1) s.select(id).deleteSelected() else s
            7 -> s.undo()
            8 -> s.redo()
            9 -> s.mirror()
            10 -> s.toggleSnap()
            11 -> if (id == null) s else {
                val c: Control = s.profile.controls.first { it.id == id }
                val all: List<Output> = OutputCatalog.optionsFor(c, s.profile.mode).flatMap { it.outputs }
                s.setOutput(id, all[rnd.nextInt(all.size)])
            }
            12 -> if (id == null) s else s.setShape(id, Shape.values()[rnd.nextInt(3)])
            13 -> if (id == null) s else s.setBehavior(id, Behavior.values()[rnd.nextInt(2)])
            14 -> if (id == null) s else s.sliderSize(id, rnd.nextFloat() * 1.2f).endGesture()
            15 -> s.sliderOpacity(rnd.nextFloat() * 1.2f).endGesture()
            16 -> if (id == null) s else s.setLabel(id, "L" + rnd.nextInt(100))
            else -> if (id == null) s else s.sliderDeadzone(id, rnd.nextFloat()).endGesture()
        }
    }

    private fun checkInvariants(p: Profile) {
        val ids = p.controls.map { it.id }
        assertEquals("unikalne id", ids.size, ids.toSet().size)
        for (c in p.controls) {
            if (c is ButtonControl) {
                assertNull("akord w ${c.id}", c.chord)
                assertTrue("latch w przycisku ${c.id}", c.behavior == Behavior.HOLD)
            }
            val r = c.rect
            assertTrue("rect ${c.id} $r", r.x >= 0f && r.y >= 0f && r.x + r.w <= 1.0001f && r.y + r.h <= 1.0001f)
            assertTrue("rozmiar ${c.id} $r", r.w > 0f && r.h > 0f)
            if (c !is StickControl && p.mode == ProfileMode.KEYBOARD_MOUSE) {
                val o = c.output()
                assertTrue("wyjście pada w KBM ${c.id}", o !is Output.Pad && o !is Output.Trigger)
            }
        }
        // Zapisany JSON nie zawiera typów ani kluczy zakazanych, a każde output ma jedno pole.
        val json = ProfileCodec.json.parseToJsonElement(ProfileCodec.encode(p))
        assertEquals(emptyList<String>(), ProfileValidator.validateRaw(json))
        // Jedyne błędy walidatora, jakie edytor może wytworzyć z poprawnego profilu: konflikt gałek.
        val other = ProfileValidator.validate(p).filterNot { it.contains(ProfileValidator.ERR_STICK_AXIS_CONFLICT) }
        assertEquals(emptyList<String>(), other)
    }
}
