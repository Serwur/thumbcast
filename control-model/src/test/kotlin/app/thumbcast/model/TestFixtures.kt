package app.thumbcast.model

import java.io.File
import java.io.FileFilter

/** Wspólne dane testowe: minimalne poprawne profile i dostęp do presetów. */
object TestFixtures {

    fun presetsDir(): File {
        val prop = System.getProperty("thumbcast.presetsDir")
        if (prop != null) return File(prop)
        // Uruchomienie z IDE bez Gradle: katalog roboczy to zwykle control-model/.
        val sibling = File("../presets")
        return if (sibling.isDirectory) sibling else File("presets")
    }

    fun presetFiles(): List<File> {
        val files = presetsDir().listFiles(FileFilter { f -> f.name.endsWith(".thumbcast.json") })
        return files?.sortedBy { it.name } ?: emptyList()
    }

    fun readPreset(name: String): String = File(presetsDir(), name).readText(Charsets.UTF_8)

    val stick = StickControl(
        id = "move",
        rect = Rect(0.0f, 0.4f, 0.35f, 0.6f),
        label = "Ruch",
        output = Output.Stick(PadStick.LEFT),
        floating = true,
        deadzone = 0.12f,
        outerDeadzone = 0.04f,
        curve = Curve(CurveType.LINEAR),
        kbmKeys = null,
        kbmThreshold = 0.35f,
    )

    val buttonA = ButtonControl(
        id = "a",
        rect = Rect(0.82f, 0.78f, 0.08f, 0.18f),
        label = "Skok",
        output = Output.Pad(PadButton.A),
        behavior = Behavior.HOLD,
        shape = Shape.CIRCLE,
    )

    val modifierLt = ModifierControl(
        id = "lt",
        rect = Rect(0.03f, 0.25f, 0.1f, 0.12f),
        label = "LT",
        output = Output.Trigger(PadTrigger.LT),
        behavior = Behavior.HOLD,
    )

    val systemMenu = SystemControl(
        id = "app_menu",
        rect = Rect(0.02f, 0.02f, 0.06f, 0.09f),
        label = "☰",
        output = Output.App(AppActions.MENU),
    )

    /** Poprawny profil Pad+. */
    fun padProfile(controls: List<Control> = listOf(stick, buttonA, modifierLt, systemMenu)): Profile = Profile(
        schema = ProfileCodec.CURRENT_SCHEMA,
        id = "test-pad",
        name = "Test Pad+",
        tags = mapOf("game" to "test"),
        mode = ProfileMode.PAD_PLUS,
        handedness = Handedness.RIGHT,
        global = GlobalSettings(),
        gestures = GesturesConfig(),
        controls = controls,
    )

    /** Poprawny profil klawiatura+mysz. */
    fun kbmProfile(controls: List<Control>? = null): Profile = Profile(
        schema = ProfileCodec.CURRENT_SCHEMA,
        id = "test-kbm",
        name = "Test KBM",
        tags = emptyMap(),
        mode = ProfileMode.KEYBOARD_MOUSE,
        handedness = Handedness.RIGHT,
        global = GlobalSettings(),
        gestures = GesturesConfig(cameraDrag = CameraDrag(output = CameraOutput.MOUSE_LOOK, curve = Curve(CurveType.LINEAR))),
        controls = controls ?: listOf(
            stick.copy(kbmKeys = StickKeys("W", "S", "A", "D")),
            ButtonControl("one", Rect(0.55f, 0.45f, 0.07f, 0.15f), "1", Output.Key("1"), Behavior.HOLD, Shape.ROUNDED_RECT),
            ModifierControl("shift", Rect(0.03f, 0.25f, 0.1f, 0.12f), "Shift", Output.Key("SHIFT"), Behavior.LATCH),
            systemMenu,
        ),
    )

    /** Minimalny poprawny profil jako tekst JSON, do testów parsera. */
    const val MINIMAL_JSON = """
{
  "schema": 1,
  "id": "min",
  "name": "Minimalny",
  "mode": "pad_plus",
  "controls": [
    { "id": "a", "type": "button", "label": "Skok", "output": { "pad": "A" },
      "rect": { "x": 0.8, "y": 0.7, "w": 0.1, "h": 0.2 } }
  ]
}
"""
}
