package app.thumbcast.android.library

import app.thumbcast.android.library.ProfileRepository.Reason
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class ProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var userDir: File
    private lateinit var presets: MemoryPresets
    private lateinit var repo: ProfileRepository
    private var idCounter = 0

    /** Prawdziwy preset z repo (poprawny wg walidatora) jako baza danych testowych. */
    private val padPlusText: String by lazy { File(presetsDir(), PAD_PLUS).readText(Charsets.UTF_8) }
    private val keyboardText: String by lazy { File(presetsDir(), KEYBOARD).readText(Charsets.UTF_8) }

    @Before
    fun setUp() {
        userDir = File(tmp.root, "profiles") // celowo nie istnieje: repozytorium tworzy go przy zapisie
        presets = MemoryPresets(mapOf(PAD_PLUS to padPlusText, KEYBOARD to keyboardText))
        repo = ProfileRepository(userDir, presets) { "test-id-${++idCounter}" }
    }

    // ---------- lista ----------

    @Test
    fun listPresets_namesFromJson_sortedWithoutDefault() {
        // Domyślnego presetu (gamepad-playstation) nie ma w tym zestawie, więc kolejność wg nazwy.
        // Kolejność „domyślny pierwszy” sprawdza realPresets_allListedAndValid.
        val list = repo.listPresets()
        assertEquals(listOf(KEYBOARD, PAD_PLUS), list.map { it.fileName })
        assertEquals("MMO – Klawiatura", list[0].name)
        assertEquals("MMO – Pad+", list[1].name)
        assertTrue(list.all { it.builtIn && it.isValid })
        assertEquals(ProfileMode.KEYBOARD_MOUSE, list[0].mode)
        assertTrue(list[0].controlCount > 0)
    }

    @Test
    fun listUser_emptyWhenDirMissing() {
        assertTrue(repo.listUser().isEmpty())
        assertEquals(2, repo.listAll().size)
    }

    @Test
    fun realPresets_allListedAndValid() {
        val real = ProfileRepository(File(tmp.root, "u"), DirectoryPresetSource(presetsDir()))
        val files = presetsDir().listFiles()!!.filter { it.name.endsWith(ProfileRepository.EXTENSION) }
        val list = real.listPresets()
        assertEquals(files.size, list.size)
        assertEquals(ProfileRepository.DEFAULT_PRESET, list.first().fileName)
        assertEquals("gamepad-playstation.thumbcast.json", list.first().fileName)
        list.forEach { assertTrue("${it.fileName}: ${it.error}", it.isValid) }
        // Przełącznik „Gesty myszy”: domyślnie wył. tylko w presetach pada (wolna strefa none).
        val byFile = list.associateBy { it.fileName }
        assertFalse(byFile.getValue("gamepad-playstation.thumbcast.json").mouseGesturesDefault)
        assertFalse(byFile.getValue("gamepad-xbox.thumbcast.json").mouseGesturesDefault)
        assertTrue(byFile.getValue("gamepad-xbox.thumbcast.json").mouseGesturesTouchpad)
        assertTrue(byFile.getValue("mmo-pad-plus.thumbcast.json").mouseGesturesDefault)
        assertFalse(byFile.getValue("mmo-pad-plus.thumbcast.json").mouseGesturesTouchpad)
        assertTrue(byFile.getValue("big-picture.thumbcast.json").mouseGesturesDefault)
    }

    @Test
    fun listUser_includesBrokenFileWithError() {
        userDir.mkdirs()
        File(userDir, "broken.thumbcast.json").writeText("{ nie json", Charsets.UTF_8)
        val list = repo.listUser()
        assertEquals(1, list.size)
        assertFalse(list[0].isValid)
        assertNull(list[0].id)
        assertEquals("broken", list[0].name)
        repo.delete("broken.thumbcast.json")
    }

    // ---------- zapis i odczyt ----------

    @Test
    fun save_writesIdFileAtomically_andLoadRoundTrips() {
        val p = ProfileCodec.decode(padPlusText).getOrThrow().copy(id = "abc-1", name = "Mój")
        val fileName = repo.save(p)
        assertEquals("abc-1.thumbcast.json", fileName)
        assertEquals(listOf(fileName), userDir.list()!!.toList()) // bez plików tymczasowych
        assertEquals(p, repo.load(fileName))
        assertFalse(repo.isBuiltIn(fileName))
        assertEquals("Mój", repo.entry(fileName)!!.name)

        // Nadpisanie tego samego pliku (rename na istniejący cel musi działać także na Windows).
        repo.save(p.copy(name = "Mój 2"))
        assertEquals("Mój 2", repo.load(fileName).name)
        assertEquals(1, userDir.list()!!.size)
    }

    @Test
    fun save_rejectsInvalidProfile_withoutWritingFile() {
        val p = ProfileCodec.decode(padPlusText).getOrThrow().copy(id = "x1", name = "  ")
        val e = failure { repo.save(p) }
        assertEquals(Reason.INVALID_PROFILE, e.reason)
        assertTrue(e.details.any { it.startsWith("name") })
        assertFalse(File(userDir, "x1.thumbcast.json").exists())
    }

    @Test
    fun save_rejectsUnsafeId() {
        val p = ProfileCodec.decode(padPlusText).getOrThrow().copy(id = "../evil")
        assertEquals(Reason.INVALID_ID, failure { repo.save(p) }.reason)
        assertFalse(File(tmp.root, "evil.thumbcast.json").exists())
    }

    @Test
    fun load_missingOrTraversal_isNotFound() {
        assertEquals(Reason.NOT_FOUND, failure { repo.load("nope.thumbcast.json") }.reason)
        assertEquals(Reason.NOT_FOUND, failure { repo.load("../$PAD_PLUS") }.reason)
        assertEquals(Reason.NOT_FOUND, failure { repo.delete("../$PAD_PLUS") }.reason)
    }

    @Test
    fun userFileShadowsPreset_likeThumbcastProfiles() {
        userDir.mkdirs()
        File(userDir, PAD_PLUS).writeText(keyboardText, Charsets.UTF_8)
        assertFalse(repo.isBuiltIn(PAD_PLUS))
        assertEquals(ProfileMode.KEYBOARD_MOUSE, repo.load(PAD_PLUS).mode)
        assertEquals(listOf(KEYBOARD), repo.listPresets().map { it.fileName })
    }

    // ---------- duplikat, nazwa, usuwanie ----------

    @Test
    fun duplicatePreset_newIdAndSuffixedName_uniqueOnRepeat() {
        val f1 = repo.duplicate(PAD_PLUS, " (kopia)")
        val p1 = repo.load(f1)
        assertEquals("test-id-1", p1.id)
        assertEquals("test-id-1.thumbcast.json", f1)
        assertEquals("MMO – Pad+ (kopia)", p1.name)
        assertFalse(repo.entry(f1)!!.builtIn)
        assertEquals(ProfileCodec.decode(padPlusText).getOrThrow().controls, p1.controls)

        val p2 = repo.load(repo.duplicate(PAD_PLUS, " (kopia)"))
        assertEquals("MMO – Pad+ (kopia) 2", p2.name)
        assertNotEquals(p1.id, p2.id)
    }

    @Test
    fun duplicate_withDefaultUuidGenerator() {
        val uuidRepo = ProfileRepository(userDir, presets)
        val p = uuidRepo.load(uuidRepo.duplicate(PAD_PLUS))
        assertTrue(p.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertTrue(p.name.endsWith(ProfileRepository.DEFAULT_COPY_SUFFIX))
    }

    @Test
    fun rename_userProfile_keepsFileAndId() {
        val f = repo.duplicate(KEYBOARD)
        assertEquals(f, repo.rename(f, "  Łucznik  "))
        val p = repo.load(f)
        assertEquals("Łucznik", p.name)
        assertEquals("test-id-1", p.id)
    }

    @Test
    fun rename_rejectsPresetAndBlankAndTooLong() {
        assertEquals(Reason.BUILT_IN_READ_ONLY, failure { repo.rename(PAD_PLUS, "X") }.reason)
        val f = repo.duplicate(KEYBOARD)
        assertEquals(Reason.INVALID_NAME, failure { repo.rename(f, "   ") }.reason)
        assertEquals(Reason.INVALID_NAME, failure { repo.rename(f, "a".repeat(ProfileRepository.MAX_NAME_LENGTH + 1)) }.reason)
    }

    @Test
    fun delete_userProfile_butNotPreset() {
        val f = repo.duplicate(PAD_PLUS)
        repo.delete(f)
        assertFalse(repo.exists(f))
        assertEquals(Reason.BUILT_IN_READ_ONLY, failure { repo.delete(PAD_PLUS) }.reason)
        assertTrue(repo.exists(PAD_PLUS))
    }

    // ---------- edytor ----------

    @Test
    fun updateFromText_keepsIdAndFile() {
        val f = repo.duplicate(PAD_PLUS)
        val edited = ProfileCodec.encode(repo.load(f).copy(id = "other", name = "Po edycji"))
        assertEquals(f, repo.updateFromText(f, edited))
        val p = repo.load(f)
        assertEquals("Po edycji", p.name)
        assertEquals("test-id-1", p.id)
        assertEquals(1, userDir.list()!!.size)
    }

    @Test
    fun update_presetIsReadOnly_andInvalidEditRejected() {
        assertEquals(Reason.BUILT_IN_READ_ONLY, failure { repo.updateFromText(PAD_PLUS, padPlusText) }.reason)
        val f = repo.duplicate(PAD_PLUS)
        val macro = padPlusText.replaceFirst("\"type\": \"button\"", "\"type\": \"macro\"")
        assertEquals(Reason.INVALID_PROFILE, failure { repo.updateFromText(f, macro) }.reason)
        repo.load(f)
    }

    // ---------- import i eksport ----------

    @Test
    fun import_rejectsForbiddenMacroType() {
        val macro = padPlusText.replaceFirst("\"type\": \"button\"", "\"type\": \"macro\"")
        assertNotEquals(padPlusText, macro)
        val e = failure { repo.prepareImport(macro) }
        assertEquals(Reason.INVALID_PROFILE, e.reason)
        assertTrue(e.details.toString(), e.details.any { it.contains("macro") })
        assertTrue(repo.listUser().isEmpty())
    }

    @Test
    fun import_rejectsForbiddenKey() {
        val turbo = padPlusText.replaceFirst("\"floating\": true", "\"floating\": true, \"turbo\": true")
        assertNotEquals(padPlusText, turbo)
        val e = failure { repo.importText(turbo) }
        assertEquals(Reason.INVALID_PROFILE, e.reason)
        assertTrue(e.details.any { it.contains("turbo") })
    }

    @Test
    fun import_rejectsTooLargeText() {
        val big = padPlusText.trimEnd().removeSuffix("}") +
            ", \"padding\": \"" + "x".repeat(ProfileRepository.MAX_IMPORT_BYTES) + "\" }"
        assertEquals(Reason.TOO_LARGE, failure { repo.prepareImport(big) }.reason)
        assertTrue(repo.listUser().isEmpty())
    }

    @Test
    fun import_rejectsGarbageAndValidationErrors() {
        assertEquals(Reason.INVALID_PROFILE, failure { repo.prepareImport("hello") }.reason)
        assertEquals(Reason.INVALID_PROFILE, failure { repo.prepareImport("[]") }.reason)
        val chord = padPlusText.replaceFirst(
            "\"output\": { \"pad\": \"Y\" },",
            "\"output\": { \"pad\": \"Y\" }, \"chord\": { \"pad\": [\"LB\", \"Y\"] },",
        )
        assertNotEquals(padPlusText, chord)
        val e = failure { repo.prepareImport(chord) }
        assertTrue(e.details.toString(), e.details.isNotEmpty())
    }

    @Test
    fun import_collidingId_getsNewId() {
        val preview = repo.prepareImport(padPlusText)
        assertTrue(preview.idChanged)
        assertEquals("0c6e5a3e-7d1b-4f0e-8a51-3f2b9d6c1a01", preview.originalId)
        assertEquals("test-id-1", preview.profile.id)
        assertEquals("MMO – Pad+", preview.name)
        assertEquals(ProfileMode.PAD_PLUS, preview.mode)
        assertTrue(preview.controlCount > 0)
        assertTrue(repo.listUser().isEmpty()) // podgląd nic nie zapisuje

        val f = repo.commitImport(preview)
        assertEquals("test-id-1.thumbcast.json", f)
        assertEquals(1, repo.listUser().size)
    }

    @Test
    fun import_freshId_kept_andSecondImportOfSameFileGetsNewId() {
        val text = padPlusText.replace("0c6e5a3e-7d1b-4f0e-8a51-3f2b9d6c1a01", "shared-123")
        val preview = repo.prepareImport(text)
        assertFalse(preview.idChanged)
        assertEquals("shared-123.thumbcast.json", repo.commitImport(preview))

        val again = repo.prepareImport(text)
        assertTrue(again.idChanged)
        repo.commitImport(again)
        assertEquals(2, repo.listUser().size)
    }

    @Test
    fun import_unsafeId_getsNewId() {
        val text = padPlusText.replace("0c6e5a3e-7d1b-4f0e-8a51-3f2b9d6c1a01", "../../evil")
        val preview = repo.prepareImport(text)
        assertTrue(preview.idChanged)
        assertEquals("test-id-1.thumbcast.json", repo.commitImport(preview))
        assertEquals(listOf("test-id-1.thumbcast.json"), userDir.list()!!.toList())
    }

    @Test
    fun import_acceptsUtf8Bom() {
        assertTrue(repo.importText(0xFEFF.toChar() + padPlusText).endsWith(ProfileRepository.EXTENSION))
    }

    @Test
    fun export_roundTripsThroughImport() {
        val text = repo.exportText(KEYBOARD)
        val exported = ProfileCodec.decode(text).getOrThrow()
        assertEquals(repo.load(KEYBOARD), exported)
        val imported = repo.load(repo.importText(text))
        assertEquals(exported.copy(id = imported.id), imported)
    }

    @Test
    fun exportFileName_sanitized() {
        assertEquals("MMO-Pad+.thumbcast.json", ProfileRepository.suggestedFileName("MMO – Pad+"))
        assertEquals("Łucznik-PvP.thumbcast.json", ProfileRepository.suggestedFileName(" Łucznik / PvP "))
        assertEquals("a-b.thumbcast.json", ProfileRepository.suggestedFileName("a:*?\"<>|b"))
        assertEquals("profile.thumbcast.json", ProfileRepository.suggestedFileName("///"))
        val p = repo.load(PAD_PLUS)
        assertEquals("MMO-Pad+.thumbcast.json", repo.exportFileName(p))
    }

    @Test
    fun readLimited_okTooLargeAndNotUtf8() {
        assertEquals("zażółć", ProfileRepository.readLimited(ByteArrayInputStream("zażółć".toByteArray())))
        try {
            ProfileRepository.readLimited(ByteArrayInputStream(ByteArray(ProfileRepository.MAX_IMPORT_BYTES + 1) { 'a'.code.toByte() }))
            fail("oczekiwano TOO_LARGE")
        } catch (e: ProfileLibraryException) {
            assertEquals(Reason.TOO_LARGE, e.reason)
        }
        assertEquals(
            ProfileRepository.MAX_IMPORT_BYTES,
            ProfileRepository.readLimited(ByteArrayInputStream(ByteArray(ProfileRepository.MAX_IMPORT_BYTES) { 'a'.code.toByte() })).length,
        )
        try {
            ProfileRepository.readLimited(ByteArrayInputStream(byteArrayOf(0x7b, 0xC3.toByte(), 0x28)))
            fail("oczekiwano NOT_UTF8")
        } catch (e: ProfileLibraryException) {
            assertEquals(Reason.NOT_UTF8, e.reason)
        }
    }

    @Test
    fun safeNames() {
        assertTrue(ProfileRepository.isSafeId("0c6e5a3e-7d1b-4f0e-8a51-3f2b9d6c1a01"))
        assertFalse(ProfileRepository.isSafeId(".hidden"))
        assertFalse(ProfileRepository.isSafeId("a/b"))
        assertFalse(ProfileRepository.isSafeId("a..b"))
        assertFalse(ProfileRepository.isSafeId(""))
        assertFalse(ProfileRepository.isSafeFileName("x.json"))
        assertTrue(ProfileRepository.isSafeFileName(PAD_PLUS))
        assertNotNull(repo.entry(PAD_PLUS))
        assertNull(repo.entry("../$PAD_PLUS"))
    }

    // ---------- pomocnicze ----------

    private fun failure(block: () -> Any?): ProfileLibraryException {
        try {
            val r = block()
            fail("oczekiwano ProfileLibraryException, jest $r")
            throw AssertionError()
        } catch (e: ProfileLibraryException) {
            return e
        }
    }

    private class MemoryPresets(private val files: Map<String, String>) : PresetSource {
        override fun list(): List<String> = files.keys.toList()
        override fun read(fileName: String): String = files[fileName] ?: throw IOException("brak $fileName")
    }

    private companion object {
        const val PAD_PLUS = "mmo-pad-plus.thumbcast.json"
        const val KEYBOARD = "mmo-keyboard.thumbcast.json"

        fun presetsDir(): File {
            System.getProperty("thumbcast.presetsDir")?.let { return File(it) }
            val sibling = File("../presets")
            return if (sibling.isDirectory) sibling else File("presets")
        }
    }
}
