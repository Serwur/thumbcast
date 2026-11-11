package app.thumbcast.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/** Każda reguła walidatora: przypadek dobry i zły. */
class ProfileValidatorTest {

    private val f = TestFixtures

    private fun assertValid(p: Profile) {
        val errors = ProfileValidator.validate(p)
        assertTrue(errors.isEmpty(), "oczekiwano braku błędów, są: $errors")
    }

    private fun assertInvalid(p: Profile, fragment: String) {
        val errors = ProfileValidator.validate(p)
        assertTrue(errors.any { it.contains(fragment) }, "oczekiwano błędu zawierającego \"$fragment\", są: $errors")
    }

    private fun padWith(vararg extra: Control): Profile = f.padProfile(listOf(f.stick, f.buttonA, f.modifierLt, f.systemMenu) + extra)

    // --- profile bazowe ---
    @Test fun basePadProfileIsValid() = assertValid(f.padProfile())
    @Test fun baseKbmProfileIsValid() = assertValid(f.kbmProfile())

    // --- schema / nagłówek ---
    @Test fun schemaGood() = assertValid(f.padProfile().copy(schema = 1))
    @Test fun schemaBad() = assertInvalid(f.padProfile().copy(schema = 7), "schema")
    @Test fun emptyControlsBad() = assertInvalid(f.padProfile(emptyList()), "co najmniej jedną")

    // --- dokładnie jedno wyjście (surowy JSON) ---
    @Test fun rawSingleOutputGood() {
        val tree = Json.parseToJsonElement("""{"controls":[{"type":"button","output":{"pad":"A"}}]}""")
        assertTrue(ProfileValidator.validateRaw(tree).isEmpty())
    }
    @Test fun rawDoubleOutputBad() {
        val tree = Json.parseToJsonElement("""{"controls":[{"type":"button","output":{"pad":"A","key":"M"}}]}""")
        assertTrue(ProfileValidator.validateRaw(tree).any { it.contains("dokładnie jedno") })
    }

    // --- brak makr / sekwencji / turbo / delay ---
    @Test fun rawForbiddenTypesBad() {
        for (name in ProfileValidator.FORBIDDEN_TYPES) {
            val asType = Json.parseToJsonElement("""{"controls":[{"type":"$name"}]}""")
            assertTrue(ProfileValidator.validateRaw(asType).any { it.contains("zakazany typ") }, name)
            val asField = Json.parseToJsonElement("""{"controls":[{"type":"button","$name":true}]}""")
            assertTrue(ProfileValidator.validateRaw(asField).any { it.contains("zakazane pole") }, name)
        }
    }
    @Test fun rawAllowedTypesGood() {
        val tree = Json.parseToJsonElement("""{"controls":[{"type":"stick"},{"type":"button"},{"type":"modifier"},{"type":"system"}]}""")
        assertTrue(ProfileValidator.validateRaw(tree).isEmpty())
    }

    // --- LATCH tylko dla modyfikatorów ---
    @Test fun latchOnModifierGood() = assertValid(padWith(f.modifierLt.copy(id = "rt", output = Output.Trigger(PadTrigger.RT), behavior = Behavior.LATCH)))
    @Test fun latchOnButtonBad() = assertInvalid(padWith(f.buttonA.copy(id = "b", output = Output.Pad(PadButton.B), behavior = Behavior.LATCH)), "latch")
    @Test fun modifierWithNonModifierOutputBad() = assertInvalid(padWith(f.modifierLt.copy(id = "m", output = Output.Pad(PadButton.A))), "modyfikator musi")
    @Test fun keyboardModifierGood() = assertValid(f.kbmProfile())

    // --- akordy: nieobsługiwane (decyzja 11.11.2026, FAIR_PLAY.md), także przy allowChords = true ---
    private val chordFragment = ProfileValidator.ERR_CHORD_UNSUPPORTED
    @Test fun chordRejectedByDefault() = assertInvalid(padWith(f.buttonA.copy(id = "c", output = Output.Pad(PadButton.Y), chord = Output.Pad(PadButton.LB))), chordFragment)
    @Test fun chordPadRejectedEvenWhenAllowed() {
        val p = padWith(f.buttonA.copy(id = "c", output = Output.Pad(PadButton.Y), chord = Output.Pad(PadButton.LB)))
        assertInvalid(p.copy(global = p.global.copy(allowChords = true)), chordFragment)
    }
    @Test fun chordKeyRejectedEvenWhenAllowed() {
        val base = f.kbmProfile()
        val chordBtn = ButtonControl("s1", Rect(0.6f, 0.2f, 0.07f, 0.15f), "Shift+1", Output.Key("1"), Behavior.HOLD, Shape.ROUNDED_RECT, null, Output.Key("SHIFT"))
        assertInvalid(base.copy(global = base.global.copy(allowChords = true), controls = base.controls + chordBtn), chordFragment)
    }
    @Test fun allowChordsFlagWithoutChordsGood() {
        // Pole zostaje w schemacie (bez migracji); sama flaga niczego nie włącza.
        val p = f.padProfile()
        assertValid(p.copy(global = p.global.copy(allowChords = true)))
    }
    @Test fun separateLatchModifierInsteadOfChordGood() = assertValid(padWith(
        f.modifierLt.copy(id = "lb", output = Output.Pad(PadButton.LB), behavior = Behavior.LATCH),
        f.buttonA.copy(id = "y", output = Output.Pad(PadButton.Y)),
    ))

    // --- unikalne id ---
    @Test fun uniqueIdsGood() = assertValid(padWith(f.buttonA.copy(id = "b", output = Output.Pad(PadButton.B))))
    @Test fun duplicateIdsBad() = assertInvalid(padWith(f.buttonA.copy(output = Output.Pad(PadButton.B))), "zduplikowane id")
    @Test fun blankIdBad() = assertInvalid(padWith(f.buttonA.copy(id = " ", output = Output.Pad(PadButton.B))), "id nie może")

    // --- rect ---
    @Test fun rectEdgeGood() = assertValid(padWith(f.buttonA.copy(id = "e", rect = Rect(0.9f, 0.9f, 0.1f, 0.1f))))
    @Test fun rectNegativeBad() = assertInvalid(padWith(f.buttonA.copy(id = "e", rect = Rect(-0.1f, 0.5f, 0.1f, 0.1f))), "rect")
    @Test fun rectOutOfBoundsBad() = assertInvalid(padWith(f.buttonA.copy(id = "e", rect = Rect(0.95f, 0.5f, 0.1f, 0.1f))), "poza ekran")
    @Test fun rectZeroSizeBad() = assertInvalid(padWith(f.buttonA.copy(id = "e", rect = Rect(0.5f, 0.5f, 0f, 0.1f))), "w i h")
    @Test fun rectNaNBad() = assertInvalid(padWith(f.buttonA.copy(id = "e", rect = Rect(Float.NaN, 0.5f, 0.1f, 0.1f))), "skończone")

    // --- martwe strefy i krzywe ---
    @Test fun deadzoneGood() = assertValid(f.padProfile(listOf(f.stick.copy(deadzone = 0.5f, outerDeadzone = 0.0f))))
    @Test fun deadzoneTooBigBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(deadzone = 0.6f))), "deadzone")
    @Test fun deadzoneNegativeBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(outerDeadzone = -0.01f))), "outerDeadzone")
    @Test fun powerCurveGood() = assertValid(f.padProfile(listOf(f.stick.copy(curve = Curve(CurveType.POWER, 2.0f)))))
    @Test fun powerCurveBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(curve = Curve(CurveType.POWER, 5.0f)))), "exponent")
    @Test fun linearCurveWithExponentBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(curve = Curve(CurveType.LINEAR, 2.0f)))), "linear")
    @Test fun kbmThresholdBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(kbmThreshold = 0.95f))), "kbmThreshold")

    // --- tryby PAD_PLUS / KEYBOARD_MOUSE ---
    @Test fun padPlusAllowsStickPadTriggerAndKey() = assertValid(padWith(
        f.buttonA.copy(id = "map", output = Output.Key("M")),
        f.modifierLt.copy(id = "rt", output = Output.Trigger(PadTrigger.RT)),
    ))
    @Test fun kbmStickWithoutKeysBad() = assertInvalid(f.kbmProfile(listOf(f.stick, f.systemMenu)), "kbmKeys")
    @Test fun kbmStickWithKeysGood() = assertValid(f.kbmProfile(listOf(f.stick.copy(kbmKeys = StickKeys("UP", "DOWN", "LEFT", "RIGHT")), f.systemMenu)))
    @Test fun kbmKeysUnknownCodeBad() = assertInvalid(f.kbmProfile(listOf(f.stick.copy(kbmKeys = StickKeys("W", "S", "A", "LMB")), f.systemMenu)), "nieznany kod")
    @Test fun kbmKeysDuplicateBad() = assertInvalid(f.kbmProfile(listOf(f.stick.copy(kbmKeys = StickKeys("W", "W", "A", "D")), f.systemMenu)), "różne klawisze")
    @Test fun kbmPadButtonBad() {
        val base = f.kbmProfile()
        assertInvalid(base.copy(controls = base.controls + f.buttonA), "trybie keyboard_mouse")
    }
    @Test fun kbmCameraMustBeMouseLookBad() {
        val base = f.kbmProfile()
        assertInvalid(base.copy(gestures = GesturesConfig()), "mouse_look")
    }
    @Test fun stickWithButtonOutputBad() = assertInvalid(f.padProfile(listOf(f.stick.copy(output = Output.Pad(PadButton.A)))), "wyjście gałki")
    @Test fun buttonWithStickOutputBad() = assertInvalid(padWith(f.buttonA.copy(id = "s", output = Output.Stick(PadStick.RIGHT))), "kontrolce typu stick")
    @Test fun buttonWithMouseWheelBad() = assertInvalid(padWith(f.buttonA.copy(id = "w", output = Output.MouseWheel)), "pinchZoom")
    @Test fun buttonWithMouseLeftGood() = assertValid(padWith(f.buttonA.copy(id = "lmb", output = Output.Mouse(MouseBtn.LEFT))))

    // --- jedna gałka pada = jedno źródło (decyzja 11.11.2026, opcja A z HANDOFF.md) ---
    private val axisFragment = ProfileValidator.ERR_STICK_AXIS_CONFLICT
    private val lookStick = f.stick.copy(id = "look", rect = Rect(0.6f, 0.4f, 0.2f, 0.3f), output = Output.Stick(PadStick.RIGHT), floating = false)
    @Test fun stickRightWithPadRightStickCameraRejected() {
        val p = padWith(lookStick)
        assertTrue(p.gestures.cameraDrag.output == CameraOutput.PAD_RIGHT_STICK)
        assertInvalid(p, axisFragment)
        assertInvalid(p, "mouse_look")
    }
    @Test fun stickRightWithMouseLookCameraGood() {
        val p = padWith(lookStick)
        assertValid(p.copy(gestures = p.gestures.copy(cameraDrag = p.gestures.cameraDrag.copy(output = CameraOutput.MOUSE_LOOK))))
    }
    @Test fun twoSticksOnSameAxisRejected() = assertInvalid(padWith(f.stick.copy(id = "move2", rect = Rect(0.6f, 0.4f, 0.2f, 0.3f))), axisFragment)
    @Test fun twoRightSticksWithMouseLookCameraRejected() {
        val p = padWith(lookStick, lookStick.copy(id = "look2", rect = Rect(0.4f, 0.1f, 0.2f, 0.2f)))
        val errors = ProfileValidator.validate(p.copy(gestures = p.gestures.copy(cameraDrag = p.gestures.cameraDrag.copy(output = CameraOutput.MOUSE_LOOK))))
        assertTrue(errors.size == 1 && errors[0].contains("id=look2") && errors[0].contains(axisFragment), "oczekiwano jednego błędu dla look2, są: $errors")
    }
    @Test fun kbmTwoSticksAreKeysNotAxesGood() {
        // W keyboard_mouse joystick wysyła klawisze z kbmKeys, więc output = left_stick to tylko rola, a nie oś pada.
        val second = f.stick.copy(id = "move2", rect = Rect(0.6f, 0.4f, 0.2f, 0.3f), kbmKeys = StickKeys("UP", "DOWN", "LEFT", "RIGHT"))
        assertValid(f.kbmProfile(listOf(f.stick.copy(kbmKeys = StickKeys("W", "S", "A", "D")), second, f.systemMenu)))
    }

    // --- kody klawiszy ---
    @Test fun keyCodeGood() {
        for (code in listOf("A", "Z", "0", "9", "F1", "F12", "SPACE", "ESC", "TAB", "ENTER", "-", "=", "UP")) {
            assertValid(padWith(f.buttonA.copy(id = "k", output = Output.Key(code))))
        }
    }
    @Test fun keyCodeBad() {
        for (code in listOf("a", "F13", "WIN", "", "SHIFT+1")) {
            assertInvalid(padWith(f.buttonA.copy(id = "k", output = Output.Key(code))), "nieznany kod")
        }
    }

    // --- App.action ---
    @Test fun appActionsGood() {
        for (a in AppActions.ALL) assertValid(padWith(f.systemMenu.copy(id = "sys_$a", output = Output.App(a))))
    }
    @Test fun appActionBad() = assertInvalid(padWith(f.systemMenu.copy(id = "sys_x", output = Output.App("run_script"))), "niedozwolona akcja")
    @Test fun appOutputOnButtonBad() = assertInvalid(padWith(f.buttonA.copy(id = "ab", output = Output.App(AppActions.MENU))), "typu system")

    // --- gesty ---
    @Test fun longPressAfterTapGood() = assertValid(f.padProfile().copy(gestures = GesturesConfig(longPress = LongPress(minMs = 221))))
    @Test fun longPressOverlapsTapBad() = assertInvalid(f.padProfile().copy(gestures = GesturesConfig(longPress = LongPress(minMs = 200))), "longPress")
    @Test fun opacityBad() = assertInvalid(f.padProfile().copy(global = GlobalSettings(opacity = 0f)), "opacity")
}
