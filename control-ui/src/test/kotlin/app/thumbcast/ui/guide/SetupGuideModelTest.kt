package app.thumbcast.ui.guide

import app.thumbcast.ui.UiContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideModelTest {

    @Test
    fun checklistCoversAllSectionsExceptTroubleshooting() {
        assertEquals(
            listOf("install", "gamepad", "pair", "apps", "remote"),
            SetupGuideModel.CHECKABLE_SECTIONS,
        )
        assertFalse(UiContract.SECTION_TROUBLESHOOT in SetupGuideModel.CHECKABLE_SECTIONS)
    }

    @Test
    fun sanitizeDropsUnknownAndNull() {
        assertEquals(emptySet<String>(), SetupGuideModel.sanitizeDone(null))
        assertEquals(
            setOf("install", "remote"),
            SetupGuideModel.sanitizeDone(setOf("install", "wiki", "remote", "troubleshoot", "")),
        )
    }

    @Test
    fun setDoneAndCount() {
        var done = emptySet<String>()
        done = SetupGuideModel.setDone(done, "pair", true)
        done = SetupGuideModel.setDone(done, "install", true)
        assertEquals(2, SetupGuideModel.doneCount(done))
        done = SetupGuideModel.setDone(done, "pair", false)
        assertEquals(setOf("install"), done)
        // Nieznana i nieodhaczalna sekcja nie zmienia zbioru.
        assertEquals(done, SetupGuideModel.setDone(done, "troubleshoot", true))
        assertEquals(done, SetupGuideModel.setDone(done, "foo", true))
    }

    @Test
    fun initialSectionPrefersIntentThenFirstUnfinished() {
        assertEquals("pair", SetupGuideModel.initialSection("pair", emptySet()))
        assertEquals("troubleshoot", SetupGuideModel.initialSection(" Troubleshoot ", emptySet()))
        // Nieznana sekcja z intencji = jak brak sekcji.
        assertEquals("install", SetupGuideModel.initialSection("wiki", emptySet()))
        assertEquals("install", SetupGuideModel.initialSection(null, emptySet()))
        assertEquals("pair", SetupGuideModel.initialSection(null, setOf("install", "gamepad")))
        assertNull(SetupGuideModel.initialSection(null, SetupGuideModel.CHECKABLE_SECTIONS.toSet()))
        // Sekcja z intencji wygrywa także wtedy, gdy jest już odhaczona.
        assertEquals("install", SetupGuideModel.initialSection("install", setOf("install")))
    }

    @Test
    fun indexAndToggle() {
        assertEquals(0, SetupGuideModel.indexOf("install"))
        assertEquals(5, SetupGuideModel.indexOf("troubleshoot"))
        assertEquals(-1, SetupGuideModel.indexOf(null))
        assertEquals(-1, SetupGuideModel.indexOf("x"))
        val open = SetupGuideModel.toggleExpanded(setOf("install"), "pair")
        assertEquals(setOf("install", "pair"), open)
        assertEquals(setOf("pair"), SetupGuideModel.toggleExpanded(open, "install"))
    }

    @Test
    fun inlineCodeParsing() {
        assertEquals(
            listOf(
                TextSegment("Open ", false),
                TextSegment("https://localhost:47990", true),
                TextSegment(" on the PC.", false),
            ),
            parseInlineCode("Open `https://localhost:47990` on the PC."),
        )
        assertEquals(listOf(TextSegment("100.x.y.z", true)), parseInlineCode("`100.x.y.z`"))
        assertEquals(listOf(TextSegment("plain", false)), parseInlineCode("plain"))
        assertEquals(emptyList<TextSegment>(), parseInlineCode(""))
        // Pusty kod jest pomijany, niezamknięty znak zostaje tekstem.
        assertEquals(listOf(TextSegment("a", false), TextSegment("b", false)), parseInlineCode("a``b"))
        assertEquals(listOf(TextSegment("a `b", false)), parseInlineCode("a `b"))
        assertEquals(
            listOf(TextSegment("x", true), TextSegment(" and ", false), TextSegment("y", true), TextSegment(" `z", false)),
            parseInlineCode("`x` and `y` `z"),
        )
        assertEquals("Open https://localhost:47990 now", plainText("Open `https://localhost:47990` now"))
    }

    @Test
    fun shareTextHasUrlOnOwnLine() {
        val text = GuideLinks.shareText(" Download Sunshine ", GuideLinks.SUNSHINE_RELEASES)
        assertEquals("Download Sunshine\nhttps://github.com/SunshineStream/Sunshine/releases", text)
        assertTrue(GuideLinks.VIGEMBUS_RELEASES.startsWith("https://"))
        assertTrue(GuideLinks.TAILSCALE_DOWNLOAD.startsWith("https://"))
    }
}
