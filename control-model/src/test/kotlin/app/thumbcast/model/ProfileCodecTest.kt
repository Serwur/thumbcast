package app.thumbcast.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ProfileCodecTest {

    private fun assertRejected(json: String, reason: String) {
        val r = ProfileCodec.decode(json)
        assertTrue(r.isFailure, "oczekiwano odrzucenia: $reason")
    }

    @Test
    fun presetsExist() {
        val names = TestFixtures.presetFiles().map { it.name }
        assertTrue("mmo-pad-plus.thumbcast.json" in names, "brak presetu Pad+: $names")
        assertTrue("mmo-keyboard.thumbcast.json" in names, "brak presetu Klawiatura: $names")
    }

    @Test
    fun presetsDecodeAndPassValidator() {
        for (f in TestFixtures.presetFiles()) {
            val r = ProfileCodec.decode(f.readText(Charsets.UTF_8))
            r.exceptionOrNull()?.let { fail("${f.name}: ${it.message}") }
        }
    }

    @Test
    fun presetsRoundTrip() {
        for (f in TestFixtures.presetFiles()) {
            val first = ProfileCodec.decode(f.readText(Charsets.UTF_8)).getOrThrow()
            val encoded = ProfileCodec.encode(first)
            val second = ProfileCodec.decode(encoded).getOrThrow()
            assertEquals(first, second, "${f.name}: round-trip zmienił profil")
            // Stabilność: drugie kodowanie daje identyczny tekst.
            assertEquals(encoded, ProfileCodec.encode(second))
        }
    }

    @Test
    fun presetModesAreAsExpected() {
        val pad = ProfileCodec.decode(TestFixtures.readPreset("mmo-pad-plus.thumbcast.json")).getOrThrow()
        val kbm = ProfileCodec.decode(TestFixtures.readPreset("mmo-keyboard.thumbcast.json")).getOrThrow()
        assertEquals(ProfileMode.PAD_PLUS, pad.mode)
        assertEquals(ProfileMode.KEYBOARD_MOUSE, kbm.mode)
        val kbmStick = kbm.controls.filterIsInstance<StickControl>().single()
        assertEquals(StickKeys("W", "S", "A", "D"), kbmStick.kbmKeys)
        assertEquals(0.35f, kbmStick.kbmThreshold)
        assertEquals(12, kbm.controls.count { it.id.startsWith("slot_") })
    }

    @Test
    fun encodeIsPrettyPrinted() {
        val text = ProfileCodec.encode(TestFixtures.padProfile())
        assertTrue(text.contains("\n  \"schema\": 1"), "oczekiwano wcięć 2 spacje")
        assertTrue(text.contains("\"type\": \"button\""))
        assertTrue(text.contains("\"mode\": \"pad_plus\""))
    }

    @Test
    fun minimalProfileUsesDefaults() {
        val p = ProfileCodec.decode(TestFixtures.MINIMAL_JSON).getOrThrow()
        assertEquals(Handedness.RIGHT, p.handedness)
        assertEquals(GlobalSettings(), p.global)
        val a = p.controls.single() as ButtonControl
        assertEquals(Output.Pad(PadButton.A), a.output)
        assertEquals(Behavior.HOLD, a.behavior)
        assertEquals(null, a.chord)
        // Profil bez pola freeZone (wszystkie sprzed 11.11.2026) ma kamerę jak dotąd.
        assertEquals(FreeZoneMode.CAMERA, p.gestures.freeZone)
        assertEquals(Touchpad(), p.gestures.touchpad)
    }

    @Test
    fun touchpadFreeZoneDecodesAndRoundTrips() {
        val json = TestFixtures.MINIMAL_JSON.replace(
            "\"mode\": \"pad_plus\",",
            "\"mode\": \"pad_plus\", \"gestures\": { \"freeZone\": \"touchpad\", " +
                "\"touchpad\": { \"sensitivity\": 1.4, \"scrollPxPerNotch\": 30 } },",
        )
        val p = ProfileCodec.decode(json).getOrThrow()
        assertEquals(FreeZoneMode.TOUCHPAD, p.gestures.freeZone)
        assertEquals(Touchpad(1.4f, 30f), p.gestures.touchpad)
        val text = ProfileCodec.encode(p)
        assertTrue(text.contains("\"freeZone\": \"touchpad\""), text)
        assertEquals(p, ProfileCodec.decode(text).getOrThrow())
    }

    @Test
    fun rejectsUnknownFreeZone() = assertRejected(
        TestFixtures.MINIMAL_JSON.replace("\"mode\": \"pad_plus\",", "\"mode\": \"pad_plus\", \"gestures\": { \"freeZone\": \"both\" },"),
        "nieznana wartość freeZone",
    )

    @Test
    fun rejectsUnknownTouchpadField() = assertRejected(
        TestFixtures.MINIMAL_JSON.replace(
            "\"mode\": \"pad_plus\",",
            "\"mode\": \"pad_plus\", \"gestures\": { \"freeZone\": \"touchpad\", \"touchpad\": { \"autoClick\": true } },",
        ),
        "nieznane pole touchpada",
    )

    @Test
    fun outputVariantsRoundTrip() {
        val outputs = listOf(
            Output.Pad(PadButton.START), Output.Trigger(PadTrigger.RT), Output.Stick(PadStick.RIGHT),
            Output.Key("F5"), Output.Mouse(MouseBtn.MIDDLE), Output.MouseWheel, Output.MouseLook,
            Output.App(AppActions.TOGGLE_OVERLAY),
        )
        for (o in outputs) {
            assertEquals(o, OutputJson.fromJson(OutputJson.toJson(o)))
            assertEquals(1, OutputJson.toJson(o).size)
        }
    }

    @Test
    fun outputAliasesTriggerAndStickAreAccepted() {
        val trig = TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ \"trigger\": \"RT\" }")
            .replace("\"type\": \"button\"", "\"type\": \"modifier\"")
        val p = ProfileCodec.decode(trig).getOrThrow()
        assertEquals(Output.Trigger(PadTrigger.RT), (p.controls.single() as ModifierControl).output)
    }

    @Test
    fun rejectsUnknownTopLevelField() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"name\": \"Minimalny\",", "\"name\": \"Minimalny\", \"extra\": 1,"), "nieznane pole")

    @Test
    fun rejectsUnknownControlField() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"label\": \"Skok\",", "\"label\": \"Skok\", \"color\": \"red\","), "nieznane pole kontrolki")

    @Test
    fun rejectsSequenceType() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"type\": \"button\"", "\"type\": \"sequence\""), "sequence")

    @Test
    fun rejectsMacroType() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"type\": \"button\"", "\"type\": \"macro\""), "macro")

    @Test
    fun rejectsTurboField() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"label\": \"Skok\",", "\"label\": \"Skok\", \"turbo\": true,"), "turbo")

    @Test
    fun rejectsOtherForbiddenNames() {
        for (name in listOf("repeat", "delay", "toggle_auto")) {
            assertRejected(TestFixtures.MINIMAL_JSON.replace("\"type\": \"button\"", "\"type\": \"$name\""), name)
        }
    }

    @Test
    fun rejectsUnknownOutputKind() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ \"sequence\": \"A\" }"), "nieznany typ wyjścia")

    @Test
    fun rejectsTwoOutputs() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ \"pad\": \"A\", \"key\": \"M\" }"), "dwa wyjścia")

    @Test
    fun rejectsEmptyOutput() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ }"), "puste wyjście")

    @Test
    fun rejectsOutputArray() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ \"pad\": [\"LB\", \"RB\"] }"), "tablica w wyjściu")

    @Test
    fun rejectsUnknownPadButton() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("{ \"pad\": \"A\" }", "{ \"pad\": \"Z\" }"), "nieznany przycisk")

    @Test
    fun rejectsUnknownMode() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"pad_plus\"", "\"auto\""), "nieznany tryb")

    @Test
    fun rejectsNewerSchema() =
        assertRejected(TestFixtures.MINIMAL_JSON.replace("\"schema\": 1", "\"schema\": 2"), "schemat z przyszłości")

    @Test
    fun rejectsInvalidJson() = assertRejected("{ \"schema\": 1, ", "uszkodzony JSON")

    @Test
    fun decodeRunsValidator() {
        val bad = TestFixtures.MINIMAL_JSON.replace("\"x\": 0.8", "\"x\": 0.95")
        val r = ProfileCodec.decode(bad)
        assertTrue(r.exceptionOrNull() is ProfileValidationException)
        assertTrue(ProfileCodec.decodeUnvalidated(bad).isSuccess)
    }
}
