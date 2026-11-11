package app.thumbcast.android.overlay

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.GlobalSettings
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** EffectiveProfile: baza -> przekształcenia -> ukrycie kontrolek. */
class EffectiveProfileTest {

    private fun profile(autoHide: Boolean = true): Profile = Profile(
        schema = 1,
        id = "t",
        name = "t",
        mode = ProfileMode.PAD_PLUS,
        global = GlobalSettings(autoHideOnGamepad = autoHide),
        controls = listOf(
            StickControl(id = "move", rect = Rect(0f, 0.5f, 0.3f, 0.5f)),
            ButtonControl(id = "a", rect = Rect(0.8f, 0.7f, 0.1f, 0.2f), output = Output.Pad(PadButton.A)),
            SystemControl(id = "menu", rect = Rect(0f, 0f, 0.05f, 0.08f), output = Output.App("menu")),
        ),
    )

    /** Przesunięcie wszystkich rect w prawo (zastępstwo przyszłego ScaleTransform). */
    private fun shift(dx: Float): ProfileStep = ProfileStep { p ->
        p.copy(
            controls = p.controls.map { c ->
                val r = c.rect.copy(x = c.rect.x + dx)
                when (c) {
                    is StickControl -> c.copy(rect = r)
                    is ButtonControl -> c.copy(rect = r)
                    is ModifierControl -> c.copy(rect = r)
                    is SystemControl -> c.copy(rect = r)
                }
            },
        )
    }

    @Test
    fun visibleWithoutTransformsIsBase() {
        val base = profile()
        assertSame(base, EffectiveProfile.build(base, hidden = false))
    }

    @Test
    fun hiddenKeepsOnlySystemControlsAndGestures() {
        val base = profile()
        val p = EffectiveProfile.build(base, hidden = true)
        assertEquals(listOf("menu"), p.controls.map { it.id })
        assertEquals(base.gestures, p.gestures)
        assertEquals(base.global, p.global)
    }

    @Test
    fun transformsRunInOrderBeforeHiding() {
        val base = profile()
        val log = ArrayList<String>()
        val first = ProfileStep { p -> log.add("1"); p }
        val second = ProfileStep { p -> log.add("2"); p }
        EffectiveProfile.build(base, hidden = false, transforms = listOf(first, second))
        assertEquals(listOf("1", "2"), log)

        val hidden = EffectiveProfile.build(base, hidden = true, transforms = listOf(shift(0.1f)))
        assertEquals(listOf("menu"), hidden.controls.map { it.id })
        assertEquals(0.1f, hidden.controls[0].rect.x, 1e-6f)
    }

    @Test
    fun controlsHiddenRule() {
        val auto = profile(autoHide = true)
        val manual = profile(autoHide = false)
        assertFalse(EffectiveProfile.controlsHidden(auto, hiddenByUser = false, gamepadPresent = false))
        assertTrue(EffectiveProfile.controlsHidden(auto, hiddenByUser = false, gamepadPresent = true))
        assertFalse(EffectiveProfile.controlsHidden(manual, hiddenByUser = false, gamepadPresent = true))
        assertTrue(EffectiveProfile.controlsHidden(manual, hiddenByUser = true, gamepadPresent = false))
    }

    @Test
    fun controlLayoutDelegatesToHideControls() {
        assertEquals(EffectiveProfile.hideControls(profile()), ControlLayout.withControlsHidden(profile()))
    }
}
