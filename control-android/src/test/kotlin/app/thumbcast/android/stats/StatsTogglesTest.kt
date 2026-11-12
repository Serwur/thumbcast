package app.thumbcast.android.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dwa przełączniki menu w grze: „Statystyki” i „Szczegółowe statystyki”. */
class StatsTogglesTest {

    @Test
    fun modeFollowsBothSwitches() {
        assertEquals(StatsMode.OFF, StatsToggles(enabled = false, advanced = false).mode)
        assertEquals(StatsMode.OFF, StatsToggles(enabled = false, advanced = true).mode)
        assertEquals(StatsMode.BASIC, StatsToggles(enabled = true, advanced = false).mode)
        assertEquals(StatsMode.ADVANCED, StatsToggles(enabled = true, advanced = true).mode)
    }

    @Test
    fun enablingStatsShowsBasicByDefault() {
        val t = StatsToggles(enabled = false, advanced = false).toggleEnabled()
        assertEquals(StatsMode.BASIC, t.mode)
    }

    @Test
    fun disablingStatsKeepsAdvancedChoiceForNextTime() {
        val off = StatsToggles(enabled = true, advanced = true).toggleEnabled()
        assertEquals(StatsMode.OFF, off.mode)
        assertTrue(off.advanced)
        assertFalse(off.advancedShown)
        assertEquals(StatsMode.ADVANCED, off.toggleEnabled().mode)
    }

    @Test
    fun advancedSwitchTurnsStatsOnWhenHidden() {
        assertEquals(StatsToggles(true, true), StatsToggles(enabled = false, advanced = false).toggleAdvanced())
        // Zapisane „szczegóły wł.” przy wyłączonych statystykach: menu pokazuje wył., przełączenie je pokazuje.
        assertEquals(StatsToggles(true, true), StatsToggles(enabled = false, advanced = true).toggleAdvanced())
    }

    @Test
    fun advancedSwitchOffKeepsBasicLine() {
        val t = StatsToggles(enabled = true, advanced = true).toggleAdvanced()
        assertEquals(StatsToggles(true, false), t)
        assertEquals(StatsMode.BASIC, t.mode)
    }

    @Test
    fun everySequenceEndsInConsistentState() {
        // Wszystkie ciągi do 6 przełączeń z każdego stanu: tryb zawsze zgodny z polami, szczegóły tylko przy wł.
        val starts = listOf(StatsToggles(false, false), StatsToggles(false, true), StatsToggles(true, false), StatsToggles(true, true))
        for (start in starts) {
            for (mask in 0 until (1 shl 6)) {
                var t = start
                for (i in 0 until 6) {
                    t = if ((mask shr i) and 1 == 0) t.toggleEnabled() else t.toggleAdvanced()
                    assertEquals(t.enabled && t.advanced, t.advancedShown)
                    assertEquals(t.mode == StatsMode.ADVANCED, t.advancedShown)
                    assertEquals(t.mode == StatsMode.OFF, !t.enabled)
                }
            }
        }
    }

    @Test
    fun segmentedChoiceMapsToTwoStoredFlags() {
        val off = StatsToggles(enabled = false, advanced = false)
        assertEquals(StatsToggles(true, false), off.withMode(StatsMode.BASIC))
        assertEquals(StatsToggles(true, true), off.withMode(StatsMode.ADVANCED))
        assertEquals(StatsToggles(true, false), StatsToggles(true, true).withMode(StatsMode.BASIC))
        // Wył. zostawia flagę szczegółów (ponowne włączenie w ustawieniach wraca do Szczegółowych).
        assertEquals(StatsToggles(false, true), StatsToggles(true, true).withMode(StatsMode.OFF))
        assertEquals(StatsToggles(false, false), StatsToggles(true, false).withMode(StatsMode.OFF))
    }

    @Test
    fun withModeRoundTripsEveryMode() {
        for (start in listOf(StatsToggles(false, false), StatsToggles(false, true), StatsToggles(true, false),
            StatsToggles(true, true))) {
            for (mode in StatsMode.values()) {
                assertEquals(mode, start.withMode(mode).mode)
            }
        }
    }
}
