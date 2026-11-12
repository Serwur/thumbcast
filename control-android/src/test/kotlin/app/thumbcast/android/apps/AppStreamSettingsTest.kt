package app.thumbcast.android.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ustawienia aplikacji hosta: klucze, dopasowanie, zapis i odczyt, nadpisanie strumienia, wybór profilu. */
class AppStreamSettingsTest {

    private val pad = "gamepad-playstation.thumbcast.json"
    private val mmo = "mmo-pad.thumbcast.json"
    private val r720 = StreamResolution(1280, 720)

    // ---------- klucze ----------

    @Test
    fun hostKeyIsTrimmedLowercaseUuid() {
        assertEquals("0a1b-ffee", HostAppSettings.hostKey("  0A1B-FFEE "))
        assertNull(HostAppSettings.hostKey(null))
        assertNull(HostAppSettings.hostKey("   "))
    }

    @Test
    fun resolutionParsing() {
        assertEquals(r720, StreamResolution.parse("1280x720"))
        assertEquals(StreamResolution(2400, 1080), StreamResolution.parse(" 2400X1080 "))
        assertNull(StreamResolution.parse("720p"))
        assertNull(StreamResolution.parse("1280x"))
        assertNull(StreamResolution.parse("1280x720x60"))
        assertNull(StreamResolution.parse("0x720"))
        assertNull(StreamResolution.parse("99999x720"))
        assertNull(StreamResolution.parse(null))
        assertEquals("1280x720", r720.toString())
    }

    // ---------- dopasowanie ----------

    @Test
    fun findPrefersIdAndNameThenNameThenId() {
        val s = HostAppSettings(
            listOf(
                AppSettingsEntry(100, "Desktop", AppStreamSettings(profileFile = pad)),
                AppSettingsEntry(200, "Game", AppStreamSettings(profileFile = mmo)),
            ),
        )
        // 1. id + nazwa (wielkość liter bez znaczenia)
        assertEquals(200, s.find(200, "game")?.appId)
        // 2. ta sama nazwa, nowe id (Sunshine: zmiana obrazka aplikacji zmienia id)
        assertEquals(200, s.find(999, "Game")?.appId)
        // 3. to samo id, nazwa zastępcza z wznowienia ("app") albo brak nazwy
        assertEquals(100, s.find(100, "app")?.appId)
        assertEquals(200, s.find(200, null)?.appId)
        assertEquals(200, s.find(200, "  ")?.appId)
        // nic wspólnego
        assertNull(s.find(300, "Other"))
        assertSame(AppStreamSettings.NONE, s.settingsFor(300, "Other"))
    }

    @Test
    fun nameMatchWinsOverIdMatchOfAnotherApp() {
        val s = HostAppSettings(
            listOf(
                AppSettingsEntry(1, "A", AppStreamSettings(fps = 30)),
                AppSettingsEntry(2, "B", AppStreamSettings(fps = 60)),
            ),
        )
        assertEquals(60, s.find(1, "B")?.settings?.fps)
    }

    // ---------- zmiana ----------

    @Test
    fun withReplacesSameIdAndSameNameAndRemovesEmpty() {
        var s = HostAppSettings.EMPTY
        s = s.with(10, "Game", AppStreamSettings(profileFile = pad))
        s = s.with(11, "Other", AppStreamSettings(fps = 30))
        // ta sama gra z nowym id (zmieniony obrazek): stary wpis znika
        s = s.with(12, " Game ", AppStreamSettings(profileFile = mmo, resolution = r720))
        assertEquals(2, s.entries.size)
        val game = s.find(12, "Game")!!
        assertEquals("Game", game.appName)
        assertEquals(mmo, game.settings.profileFile)
        assertNull(s.entries.firstOrNull { it.appId == 10 })
        // wszystko globalne = usunięcie wpisu
        s = s.with(11, "Other", AppStreamSettings.NONE)
        assertEquals(listOf(12), s.entries.map { it.appId })
    }

    @Test
    fun withDropsInvalidValues() {
        val s = HostAppSettings.EMPTY.with(
            1, "X",
            AppStreamSettings(profileFile = "../evil.thumbcast.json", resolution = StreamResolution(10, 10), fps = 5),
        )
        assertTrue(s.entries.isEmpty())
    }

    // ---------- zapis i odczyt ----------

    @Test
    fun encodeDecodeRoundTrip() {
        val s = HostAppSettings.EMPTY
            .with(-5, "Zażółć \"gęślą\" jaźń", AppStreamSettings(profileFile = pad))
            .with(2147483647, "Big", AppStreamSettings(resolution = r720, fps = 60))
            .with(7, "", AppStreamSettings(fps = 30))
        val decoded = HostAppSettings.decode(s.encode())
        assertEquals(s.entries, decoded.entries)
    }

    @Test
    fun decodeIsTolerant() {
        assertTrue(HostAppSettings.decode(null).entries.isEmpty())
        assertTrue(HostAppSettings.decode("").entries.isEmpty())
        assertTrue(HostAppSettings.decode("not json").entries.isEmpty())
        assertTrue(HostAppSettings.decode("[1,2]").entries.isEmpty())
        assertTrue(HostAppSettings.decode("{\"apps\":5}").entries.isEmpty())

        val text = """
            {"version": 99, "future": true, "apps": [
              {"appName": "no id", "fps": 60},
              {"appId": "12", "appName": "id as string", "fps": 60},
              {"appId": 1, "appName": "only width", "width": 1280, "fps": 60},
              {"appId": 2, "appName": "bad values", "width": 1, "height": 1, "fps": 9999, "profile": "a/b.thumbcast.json"},
              {"appId": 3, "appName": "ok", "width": 1920, "height": 1080, "profile": "x.thumbcast.json", "extra": [1]},
              7, null
            ]}
        """.trimIndent()
        val s = HostAppSettings.decode(text)
        assertEquals(listOf(1, 3), s.entries.map { it.appId })
        assertEquals(AppStreamSettings(fps = 60), s.entries[0].settings)
        assertEquals(
            AppStreamSettings(profileFile = "x.thumbcast.json", resolution = StreamResolution(1920, 1080)),
            s.entries[1].settings,
        )
    }

    // ---------- nadpisanie strumienia ----------

    @Test
    fun applyStreamOverridesOnlySetFields() {
        val global = StreamParams(1920, 1080, 60)
        assertEquals(global, AppStreamOverrides.applyStream(global, AppStreamSettings.NONE))
        assertEquals(StreamParams(1280, 720, 60), AppStreamOverrides.applyStream(global, AppStreamSettings(resolution = r720)))
        assertEquals(StreamParams(1920, 1080, 30), AppStreamOverrides.applyStream(global, AppStreamSettings(fps = 30)))
        assertEquals(
            StreamParams(1280, 720, 120),
            AppStreamOverrides.applyStream(global, AppStreamSettings(profileFile = pad, resolution = r720, fps = 120)),
        )
        // wartości spoza zakresu = globalne
        assertEquals(global, AppStreamOverrides.applyStream(global, AppStreamSettings(resolution = StreamResolution(0, 0), fps = 1)))
    }

    // ---------- wybór profilu ----------

    private val library = mapOf(pad to "PAD", mmo to "MMO")
    private val loads = ArrayList<String>()
    private val load: (String) -> String? = { name -> loads += name; library[name] }

    @Test
    fun appProfileIsUsedWhenLoadable() {
        val sel = AppStreamOverrides.selectProfile(mmo, pad, load)
        assertEquals(mmo, sel.fileName)
        assertEquals("MMO", sel.value)
        assertTrue(sel.fromApp)
        assertFalse(sel.appProfileFailed)
        assertEquals(listOf(mmo), loads)
    }

    @Test
    fun noAppProfileUsesGlobalWithoutWarning() {
        val sel = AppStreamOverrides.selectProfile(null, pad, load)
        assertEquals(pad, sel.fileName)
        assertEquals("PAD", sel.value)
        assertFalse(sel.fromApp)
        assertFalse(sel.appProfileFailed)
    }

    @Test
    fun missingOrInvalidAppProfileFallsBackToGlobal() {
        // profil usunięty z biblioteki
        val removed = AppStreamOverrides.selectProfile("deleted.thumbcast.json", pad, load)
        assertEquals(pad, removed.fileName)
        assertEquals("PAD", removed.value)
        assertFalse(removed.fromApp)
        assertTrue(removed.appProfileFailed)
        // niedozwolona nazwa pliku: nie próbujemy jej czytać
        loads.clear()
        val unsafe = AppStreamOverrides.selectProfile("../x.thumbcast.json", pad, load)
        assertEquals(listOf(pad), loads)
        assertTrue(unsafe.appProfileFailed)
    }

    @Test
    fun bothProfilesBrokenGivesNullValue() {
        val sel = AppStreamOverrides.selectProfile("gone.thumbcast.json", "also-gone.thumbcast.json", load)
        assertNull(sel.value)
        assertEquals("also-gone.thumbcast.json", sel.fileName)
        assertTrue(sel.appProfileFailed)
    }

    @Test
    fun missingDefaultProfileFallsBackToShippedDefaultWithFlag() {
        // Profil domyślny użytkownika usunięty poza biblioteką: profil dostarczany z aplikacją i komunikat.
        val sel = AppStreamOverrides.selectProfile(null, "deleted.thumbcast.json", load, pad)
        assertEquals(pad, sel.fileName)
        assertEquals("PAD", sel.value)
        assertTrue(sel.defaultProfileFailed)
        assertFalse(sel.appProfileFailed)
        // Profil aplikacji i domyślny usunięte: nadal profil dostarczany z aplikacją, oba komunikaty.
        val both = AppStreamOverrides.selectProfile("gone.thumbcast.json", "deleted.thumbcast.json", load, pad)
        assertEquals(pad, both.fileName)
        assertTrue(both.appProfileFailed)
        assertTrue(both.defaultProfileFailed)
    }

    @Test
    fun existingDefaultProfileDoesNotTouchShippedDefault() {
        val sel = AppStreamOverrides.selectProfile(null, mmo, load, pad)
        assertEquals(mmo, sel.fileName)
        assertFalse(sel.defaultProfileFailed)
        assertEquals(listOf(mmo), loads)
        // Domyślny = dostarczany i uszkodzony: bez drugiej próby, wartość null (sesja pokaże błąd).
        loads.clear()
        val broken = AppStreamOverrides.selectProfile(null, "x.thumbcast.json", load, "x.thumbcast.json")
        assertNull(broken.value)
        assertFalse(broken.defaultProfileFailed)
        assertEquals(listOf("x.thumbcast.json"), loads)
    }
}
