package app.thumbcast.android.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuPageTest {

    @Test
    fun backFromSubpagesReturnsToMain() {
        assertEquals(MenuPage.MAIN, MenuPage.PROFILES.back())
        assertEquals(MenuPage.MAIN, MenuPage.END_SESSION.back())
    }

    @Test
    fun backFromMainClosesMenu() {
        assertNull(MenuPage.MAIN.back())
    }

}
