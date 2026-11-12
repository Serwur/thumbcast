package app.thumbcast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiContractTest {

    @Test
    fun onboardingShownOnlyForNewUserWithoutPcs() {
        assertTrue(UiContract.shouldShowOnboarding(storedVersion = 0, hasPcs = false))
        // Aktualizacja aplikacji z istniejącymi PC: bez kreatora (:app tylko zapisuje wersję).
        assertFalse(UiContract.shouldShowOnboarding(storedVersion = 0, hasPcs = true))
        // Kreator już widziany (także pominięty).
        assertFalse(UiContract.shouldShowOnboarding(UiContract.CURRENT_ONBOARDING_VERSION, hasPcs = false))
        assertFalse(UiContract.shouldShowOnboarding(UiContract.CURRENT_ONBOARDING_VERSION + 1, hasPcs = false))
        assertTrue(UiContract.shouldShowOnboarding(UiContract.CURRENT_ONBOARDING_VERSION - 1, hasPcs = false))
    }

    @Test
    fun guideSectionsMatchSpec() {
        assertEquals(
            listOf("install", "gamepad", "pair", "apps", "remote", "troubleshoot"),
            UiContract.GUIDE_SECTIONS,
        )
    }

    @Test
    fun guideSectionNormalization() {
        assertEquals("pair", UiContract.normalizeGuideSection("pair"))
        assertEquals("remote", UiContract.normalizeGuideSection(" Remote "))
        assertNull(UiContract.normalizeGuideSection(null))
        assertNull(UiContract.normalizeGuideSection(""))
        assertNull(UiContract.normalizeGuideSection("wiki"))
    }

    @Test
    fun presetFileValidation() {
        assertTrue(UiContract.isValidPresetFile(UiContract.PRESET_GAMEPAD_XBOX))
        assertTrue(UiContract.isValidPresetFile(UiContract.PRESET_GAMEPAD_PLAYSTATION))
        assertTrue(UiContract.isValidPresetFile(UiContract.PRESET_MMO_KEYBOARD))
        assertFalse(UiContract.isValidPresetFile(null))
        assertFalse(UiContract.isValidPresetFile(""))
        assertFalse(UiContract.isValidPresetFile(".thumbcast.json"))
        assertFalse(UiContract.isValidPresetFile("gamepad-xbox"))
        assertFalse(UiContract.isValidPresetFile("gamepad-xbox.json"))
        assertFalse(UiContract.isValidPresetFile("../gamepad-xbox.thumbcast.json"))
        assertFalse(UiContract.isValidPresetFile("dir\\x.thumbcast.json"))
    }

    @Test
    fun wizardPresetsExistInPresetsDir() {
        val dir = java.io.File(System.getProperty("thumbcast.presetsDir") ?: return)
        for (name in listOf(
            UiContract.PRESET_GAMEPAD_XBOX, UiContract.PRESET_GAMEPAD_PLAYSTATION, UiContract.PRESET_MMO_KEYBOARD,
        )) {
            assertTrue("brak presetu $name", java.io.File(dir, name).isFile)
        }
    }
}
