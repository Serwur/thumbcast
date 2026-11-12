package app.thumbcast.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MouseGesturesTransformTest {

    private fun preset(name: String): Profile = ProfileCodec.decode(TestFixtures.readPreset(name)).getOrThrow()

    private fun allPresets(): List<Profile> =
        TestFixtures.presetFiles().map { ProfileCodec.decode(it.readText(Charsets.UTF_8)).getOrThrow() }

    @Test
    fun defaultsMatchCurrentPresetBehaviour() {
        // Pady: bez myszy (wolna strefa none); MMO, Big Picture i Pulpit: z myszą.
        assertFalse(MouseGesturesTransform.isEnabled(preset("gamepad-xbox.thumbcast.json")))
        assertFalse(MouseGesturesTransform.isEnabled(preset("gamepad-playstation.thumbcast.json")))
        assertTrue(MouseGesturesTransform.isEnabled(preset("mmo-pad-plus.thumbcast.json")))
        assertTrue(MouseGesturesTransform.isEnabled(preset("mmo-keyboard.thumbcast.json")))
        assertTrue(MouseGesturesTransform.isEnabled(preset("big-picture.thumbcast.json")))
        assertTrue(MouseGesturesTransform.isEnabled(preset("desktop-touchpad.thumbcast.json")))
    }

    @Test
    fun nothingStoredOrDefaultValueKeepsProfileUnchanged() {
        for (p in allPresets()) {
            assertSame(p, MouseGesturesTransform.apply(p, null), p.name)
            assertSame(p, MouseGesturesTransform.apply(p, MouseGesturesTransform.isEnabled(p)), p.name)
            assertNull(MouseGesturesTransform.toStore(p, MouseGesturesTransform.isEnabled(p)), p.name)
            assertEquals(!MouseGesturesTransform.isEnabled(p), MouseGesturesTransform.toStore(p, !MouseGesturesTransform.isEnabled(p)))
        }
    }

    @Test
    fun padPresetOnBecomesDirectMouseWithClicks() {
        for (name in listOf("gamepad-playstation.thumbcast.json", "gamepad-xbox.thumbcast.json")) {
            val ps = preset(name)
            val on = MouseGesturesTransform.apply(ps, true)
            assertEquals(FreeZoneMode.DIRECT, on.gestures.freeZone, name)
            assertTrue(on.gestures.tapTarget.enabled, name)
            assertTrue(on.gestures.edgeSwipeTop.enabled, "menu z górnej krawędzi zostaje")
            assertEquals(ps.controls, on.controls)
            assertEquals(ps.mode, on.mode)
            assertEquals(MouseGesturesTransform.Style.DIRECT, MouseGesturesTransform.style(ps))
            assertEquals(MouseGesturesTransform.Style.DIRECT, MouseGesturesTransform.style(on))
            // Prawa gałka jest kontrolką, a kamery nie ma, więc walidator nie widzi dwóch źródeł gałki.
            assertEquals(emptyList(), ProfileValidator.validate(on), name)
            // Wył. (zapisany wybór = brak wpisu) daje plik bez zmian; wyłączenie samego `direct` wraca do none.
            assertSame(ps, MouseGesturesTransform.apply(ps, false), name)
            assertEquals(FreeZoneMode.NONE, MouseGesturesTransform.apply(on, false).gestures.freeZone, name)
        }
    }

    @Test
    fun bigPictureIsDirectAndDesktopStaysTouchpad() {
        val bp = preset("big-picture.thumbcast.json")
        assertEquals(FreeZoneMode.DIRECT, bp.gestures.freeZone)
        assertEquals(MouseGesturesTransform.Style.DIRECT, MouseGesturesTransform.style(bp))
        assertSame(bp, MouseGesturesTransform.apply(bp, true))
        val desktop = preset("desktop-touchpad.thumbcast.json")
        assertEquals(FreeZoneMode.TOUCHPAD, desktop.gestures.freeZone)
        assertEquals(MouseGesturesTransform.Style.TOUCHPAD, MouseGesturesTransform.style(desktop))
        assertSame(desktop, MouseGesturesTransform.apply(desktop, true), "Pulpit – Touchpad zostaje gładzikiem")
    }

    @Test
    fun touchpadAndDirectPresetsOffBecomeNone() {
        for (name in listOf("big-picture.thumbcast.json", "desktop-touchpad.thumbcast.json")) {
            val p = preset(name)
            val off = MouseGesturesTransform.apply(p, false)
            assertEquals(FreeZoneMode.NONE, off.gestures.freeZone, name)
            assertTrue(off.gestures.edgeSwipeTop.enabled, name)
            assertEquals(p.controls, off.controls, name)
            assertEquals(emptyList(), ProfileValidator.validate(off), name)
        }
    }

    @Test
    fun directProfileWithClashingDisabledLongPressStaysUntouched() {
        // Pad (none) z wyłączonym long-pressem i progiem niższym niż tap: włączenie nie musi go ruszać.
        val base = TestFixtures.padProfile().let {
            it.copy(gestures = it.gestures.copy(
                freeZone = FreeZoneMode.NONE,
                tapTarget = TapTarget(enabled = false, maxMs = 300),
                longPress = LongPress(enabled = false, minMs = 100),
            ))
        }
        val on = MouseGesturesTransform.apply(base, true)
        assertEquals(FreeZoneMode.DIRECT, on.gestures.freeZone)
        assertTrue(on.gestures.tapTarget.enabled)
        assertEquals(base.gestures.longPress, on.gestures.longPress)
        assertEquals(emptyList(), ProfileValidator.validate(on))
    }

    @Test
    fun mmoOffKeepsCameraAndDropsClicksAndPinch() {
        for (name in listOf("mmo-pad-plus.thumbcast.json", "mmo-keyboard.thumbcast.json")) {
            val p = preset(name)
            val off = MouseGesturesTransform.apply(p, false)
            assertEquals(FreeZoneMode.CAMERA, off.gestures.freeZone, name)
            assertEquals(p.gestures.cameraDrag, off.gestures.cameraDrag, name)
            assertFalse(off.gestures.tapTarget.enabled, name)
            assertFalse(off.gestures.longPress.enabled, name)
            assertFalse(off.gestures.pinchZoom.enabled, name)
            assertEquals(MouseGesturesTransform.Style.AT_POINT, MouseGesturesTransform.style(p))
            assertEquals(emptyList(), ProfileValidator.validate(off), name)
            // Z powrotem włączone (z profilu bazowego bez myszy) = tap, long-press i pinch.
            val on = MouseGesturesTransform.apply(off, true)
            assertTrue(on.gestures.tapTarget.enabled && on.gestures.longPress.enabled && on.gestures.pinchZoom.enabled)
            assertEquals(p.gestures, on.gestures, name)
        }
    }

    @Test
    fun enablingFixesLongPressThresholdThatWouldClashWithTap() {
        val base = TestFixtures.padProfile().let {
            it.copy(gestures = it.gestures.copy(
                tapTarget = TapTarget(enabled = false, maxMs = 300),
                longPress = LongPress(enabled = false, minMs = 100),
                pinchZoom = PinchZoom(enabled = false),
            ))
        }
        assertFalse(MouseGesturesTransform.isEnabled(base))
        val on = MouseGesturesTransform.apply(base, true)
        assertTrue(on.gestures.longPress.minMs > on.gestures.tapTarget.maxMs)
        assertEquals(450, on.gestures.longPress.minMs)
        assertEquals(emptyList(), ProfileValidator.validate(on))
    }

    @Test
    fun invalidBaseProfileIsReturnedUnchanged() {
        // Kamera na prawej gałce + kontrolka prawej gałki: profil już niepoprawny (np. decodeUnvalidated).
        val bad = TestFixtures.padProfile(listOf(TestFixtures.stick.copy(id = "r", output = Output.Stick(PadStick.RIGHT))))
            .let { it.copy(gestures = it.gestures.copy(tapTarget = it.gestures.tapTarget.copy(enabled = true))) }
        assertTrue(ProfileValidator.validate(bad).isNotEmpty())
        assertSame(bad, MouseGesturesTransform.apply(bad, false))
    }

    /**
     * Właściwości na losowych poprawnych profilach (wszystkie tryby wolnej strefy, Pad+ i KBM, różne gesty):
     * wynik przechodzi walidator, przełącznik ma żądany stan, transformacja jest idempotentna, zmienia tylko gesty,
     * nie dokłada kamery i zostawia edge swipe.
     */
    @Test
    fun randomProfilesStayValidAndHaveRequestedState(): Unit = runBlocking<Unit> {
        checkAll(2000, Arb.long()) { seed ->
            val p = randomProfile(Random(seed))
            assertEquals(emptyList(), ProfileValidator.validate(p), "seed=$seed: generator dał niepoprawny profil")
            for (want in listOf(true, false)) {
                val r = MouseGesturesTransform.apply(p, want)
                assertEquals(emptyList(), ProfileValidator.validate(r), "seed=$seed want=$want")
                assertEquals(want, MouseGesturesTransform.isEnabled(r), "seed=$seed want=$want")
                assertEquals(r, MouseGesturesTransform.apply(r, want), "seed=$seed idempotencja")
                assertEquals(p.copy(gestures = r.gestures), r, "seed=$seed: zmieniają się tylko gesty")
                assertEquals(p.gestures.edgeSwipeTop, r.gestures.edgeSwipeTop, "seed=$seed: menu")
                assertEquals(p.gestures.cameraDrag, r.gestures.cameraDrag, "seed=$seed: kamera")
                if (p.gestures.freeZone != FreeZoneMode.CAMERA) {
                    assertTrue(r.gestures.freeZone != FreeZoneMode.CAMERA, "seed=$seed: nie dokładamy kamery")
                }
                // Wł.: none i direct dają direct, touchpad zostaje touchpadem; wył.: none (poza kamerą).
                val expectedZone = when (p.gestures.freeZone) {
                    FreeZoneMode.CAMERA -> FreeZoneMode.CAMERA
                    FreeZoneMode.TOUCHPAD -> if (want) FreeZoneMode.TOUCHPAD else FreeZoneMode.NONE
                    FreeZoneMode.NONE, FreeZoneMode.DIRECT -> if (want) FreeZoneMode.DIRECT else FreeZoneMode.NONE
                }
                assertEquals(expectedZone, r.gestures.freeZone, "seed=$seed want=$want")
                if (want && r.gestures.freeZone == FreeZoneMode.DIRECT && p.gestures.freeZone == FreeZoneMode.NONE) {
                    assertTrue(r.gestures.tapTarget.enabled, "seed=$seed: direct z tapem")
                }
            }
        }
    }

    private fun randomProfile(r: Random): Profile {
        val kbm = r.nextInt(3) == 0
        val zone = FreeZoneMode.values()[r.nextInt(FreeZoneMode.values().size)]
        val base = if (kbm) TestFixtures.kbmProfile() else TestFixtures.padProfile()
        val rightStick = !kbm && zone != FreeZoneMode.CAMERA && r.nextBoolean()
        val controls = if (rightStick) {
            base.controls + TestFixtures.stick.copy(id = "look", rect = Rect(0.6f, 0.5f, 0.2f, 0.3f), output = Output.Stick(PadStick.RIGHT), floating = false)
        } else {
            base.controls
        }
        val tapMax = 100 + r.nextInt(400)
        val tapOn = r.nextBoolean()
        val longOn = r.nextBoolean()
        // Gdy oba włączone, minMs musi być > maxMs; przy wyłączonych dowolne (także kolidujące).
        val minMs = if (tapOn && longOn) tapMax + 1 + r.nextInt(500) else 1 + r.nextInt(900)
        val camOutput = if (kbm || r.nextBoolean()) CameraOutput.MOUSE_LOOK else CameraOutput.PAD_RIGHT_STICK
        return base.copy(
            controls = controls,
            gestures = base.gestures.copy(
                freeZone = zone,
                cameraDrag = base.gestures.cameraDrag.copy(output = camOutput),
                tapTarget = TapTarget(enabled = tapOn, maxMs = tapMax),
                longPress = LongPress(enabled = longOn, minMs = minMs),
                pinchZoom = PinchZoom(enabled = r.nextBoolean()),
                edgeSwipeTop = EdgeSwipe(enabled = r.nextBoolean()),
                touchpad = Touchpad(sensitivity = 0.5f + r.nextInt(30) / 10f),
            ),
        )
    }
}
