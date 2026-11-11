package app.thumbcast.editor.state

import app.thumbcast.model.AppActions
import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.CameraDrag
import app.thumbcast.model.CameraOutput
import app.thumbcast.model.GesturesConfig
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.PadStick
import app.thumbcast.model.PadTrigger
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import app.thumbcast.model.StickKeys
import app.thumbcast.model.SystemControl

object Fixtures {

    fun padPlus(): Profile = Profile(
        schema = ProfileCodec.CURRENT_SCHEMA,
        id = "test-pad",
        name = "Test Pad+",
        mode = ProfileMode.PAD_PLUS,
        controls = listOf(
            StickControl("move", Rect(0f, 0.4f, 0.35f, 0.6f), "Move", Output.Stick(PadStick.LEFT)),
            ButtonControl("a", Rect(0.82f, 0.79f, 0.072f, 0.162f), null, Output.Pad(PadButton.A)),
            ButtonControl("x", Rect(0.74f, 0.65f, 0.072f, 0.162f), "Act", Output.Pad(PadButton.X), shape = Shape.CIRCLE),
            ModifierControl("lt", Rect(0.035f, 0.256f, 0.09f, 0.108f), "LT", Output.Trigger(PadTrigger.LT), Behavior.HOLD),
            SystemControl("menu", Rect(0.02f, 0.02f, 0.06f, 0.09f), null, Output.App(AppActions.MENU)),
        ),
    )

    fun keyboardMouse(): Profile = Profile(
        schema = ProfileCodec.CURRENT_SCHEMA,
        id = "test-kbm",
        name = "Test KBM",
        mode = ProfileMode.KEYBOARD_MOUSE,
        gestures = GesturesConfig(cameraDrag = CameraDrag(output = CameraOutput.MOUSE_LOOK)),
        controls = listOf(
            StickControl(
                "move", Rect(0f, 0.4f, 0.35f, 0.6f), null, Output.Stick(PadStick.LEFT),
                kbmKeys = StickKeys("W", "S", "A", "D"),
            ),
            ButtonControl("one", Rect(0.8f, 0.8f, 0.07f, 0.16f), null, Output.Key("1")),
            ModifierControl("shift", Rect(0.6f, 0.8f, 0.09f, 0.11f), null, Output.Key("SHIFT"), Behavior.LATCH),
        ),
    )
}
