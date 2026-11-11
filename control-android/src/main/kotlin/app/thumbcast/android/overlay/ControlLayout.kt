package app.thumbcast.android.overlay

import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.MouseBtn
import app.thumbcast.model.Output
import app.thumbcast.model.PadStick
import app.thumbcast.model.Profile
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import app.thumbcast.model.SystemControl

/**
 * Kontrolka przeliczona na px do rysowania (geometria grafiki, etykieta, kształt). Stan „wciśnięcia”
 * i właściciela palca bierze się z silnika (`InputEngine.visualState()`), nie z [ControlLayout.hitTest].
 */
data class LaidOutControl(
    val control: Control,
    val bounds: PxRect,
    val kind: Kind,
    val shape: Shape,
    val text: String,
    val floating: Boolean,
    val latch: Boolean,
) {
    enum class Kind { STICK, BUTTON, MODIFIER, SYSTEM }
}

/**
 * Układ kontrolek profilu w px.
 *
 * Założenie (zgodne z naszym rozumieniem kontraktu, do potwierdzenia z ENGINE): `rect` jest
 * znormalizowany względem całego widoku overlaya (`Viewport.widthPx/heightPx`), a przy nakładaniu
 * się kontrolek wygrywa ta, która jest PÓŹNIEJ na liście `controls` (rysowana na wierzchu).
 */
class ControlLayout(val controls: List<LaidOutControl>) {

    /** Kontrolka pod punktem (najwyższa w z-order) albo null. */
    fun hitTest(x: Float, y: Float): LaidOutControl? {
        var i: Int = controls.size - 1
        while (i >= 0) {
            val c: LaidOutControl = controls[i]
            if (c.bounds.contains(x, y)) {
                return c
            }
            i--
        }
        return null
    }

    fun byId(id: String): LaidOutControl? {
        for (c in controls) {
            if (c.control.id == id) return c
        }
        return null
    }

    companion object {
        fun of(profile: Profile, widthPx: Int, heightPx: Int): ControlLayout {
            val w: Float = widthPx.toFloat()
            val h: Float = heightPx.toFloat()
            val list: ArrayList<LaidOutControl> = ArrayList(profile.controls.size)
            for (c in profile.controls) {
                val r = c.rect
                val bounds = PxRect(r.x * w, r.y * h, (r.x + r.w) * w, (r.y + r.h) * h)
                list.add(layOut(c, bounds))
            }
            return ControlLayout(list)
        }

        private fun layOut(c: Control, bounds: PxRect): LaidOutControl = when (c) {
            is StickControl -> LaidOutControl(
                c, bounds, LaidOutControl.Kind.STICK, Shape.CIRCLE,
                c.label ?: labelOf(c.output), c.floating, false,
            )
            is ButtonControl -> LaidOutControl(
                c, bounds, LaidOutControl.Kind.BUTTON, c.shape,
                c.label ?: labelOf(c.output), false, false,
            )
            is ModifierControl -> LaidOutControl(
                c, bounds, LaidOutControl.Kind.MODIFIER, c.shape,
                c.label ?: labelOf(c.output), false, c.behavior == app.thumbcast.model.Behavior.LATCH,
            )
            is SystemControl -> LaidOutControl(
                c, bounds, LaidOutControl.Kind.SYSTEM, c.shape,
                c.label ?: labelOf(c.output), false, false,
            )
        }

        /** Krótka etykieta zastępcza, gdy profil nie podaje `label`. */
        fun labelOf(o: Output): String = when (o) {
            is Output.Pad -> o.button.name
            is Output.Trigger -> o.trigger.name
            is Output.Stick -> if (o.stick == PadStick.LEFT) "LS" else "RS"
            is Output.Key -> o.code
            is Output.Mouse -> when (o.button) {
                MouseBtn.LEFT -> "LMB"
                MouseBtn.RIGHT -> "RMB"
                MouseBtn.MIDDLE -> "MMB"
            }
            Output.MouseWheel -> "Wheel"
            Output.MouseLook -> "Look"
            is Output.App -> when (o.action) {
                "menu" -> "☰"
                "soft_keyboard" -> "⌨"
                "toggle_overlay" -> "◎"
                else -> o.action
            }
        }

        /**
         * Profil z ukrytymi kontrolkami (pad BT podłączony albo ręczne ukrycie): zostają tylko
         * kontrolki systemowe (menu, klawiatura), a gesty z `gestures` działają dalej (plan 4.2).
         */
        fun withControlsHidden(profile: Profile): Profile = EffectiveProfile.hideControls(profile)
    }
}
