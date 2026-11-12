package app.thumbcast.android.library

import android.content.Context
import app.thumbcast.android.ThumbcastProfiles
import app.thumbcast.model.MouseGesturesTransform
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.ProfileValidationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID

/**
 * Biblioteka profili: presety wbudowane (tylko odczyt) i profile użytkownika w [userDir]
 * (`filesDir/profiles/<id>.thumbcast.json`). Logika bez API Androida, testowana w JVM.
 *
 * Kolejność wyszukiwania pliku jest ta sama co w `ThumbcastProfiles`: najpierw katalog użytkownika,
 * potem presety. Wartość preferencji `list_thumbcast_profile` to nazwa pliku ([ProfileEntry.fileName]).
 *
 * Każdy zapis przechodzi przez `ProfileCodec` (encode, potem decode z pełną walidacją), więc na dysk
 * trafia tylko profil, który `ThumbcastProfiles.load` wczyta. Zapis jest atomowy (plik tymczasowy + rename).
 */
class ProfileRepository(
    private val userDir: File,
    private val presets: PresetSource,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    // ---------- lista ----------

    /** Presety wbudowane; domyślny pierwszy, reszta wg nazwy. Pomija presety przesłonięte plikiem użytkownika. */
    fun listPresets(): List<ProfileEntry> {
        return presetNames()
            .filter { !userFile(it).isFile }
            .map { name -> entryFor(name, builtIn = true) { presets.read(name) } }
            .sortedWith(compareBy<ProfileEntry>({ it.fileName != DEFAULT_PRESET }, { it.name.lowercase(Locale.ROOT) }))
    }

    /** Profile użytkownika wg nazwy. Pliki uszkodzone też są na liście (z [ProfileEntry.error]), żeby dało się je usunąć. */
    fun listUser(): List<ProfileEntry> {
        val files = userDir.listFiles()?.filter { it.isFile && it.name.endsWith(EXTENSION) } ?: emptyList()
        return files
            .map { f -> entryFor(f.name, builtIn = false) { f.readText(Charsets.UTF_8) } }
            .sortedWith(compareBy<ProfileEntry>({ it.name.lowercase(Locale.ROOT) }, { it.fileName }))
    }

    fun listAll(): List<ProfileEntry> = listPresets() + listUser()

    /** Czy nazwa pliku wskazuje preset (a nie plik użytkownika, który go przesłania). */
    fun isBuiltIn(fileName: String): Boolean = !userFile(fileName).isFile && presetExists(fileName)

    fun exists(fileName: String): Boolean = userFile(fileName).isFile || presetExists(fileName)

    /** Wpis dla pliku albo null, gdy go nie ma. */
    fun entry(fileName: String): ProfileEntry? {
        if (!isSafeFileName(fileName)) return null
        val f = userFile(fileName)
        if (f.isFile) return entryFor(fileName, builtIn = false) { f.readText(Charsets.UTF_8) }
        if (presetExists(fileName)) return entryFor(fileName, builtIn = true) { presets.read(fileName) }
        return null
    }


    // ---------- odczyt ----------

    @Throws(ProfileLibraryException::class)
    fun load(fileName: String): Profile = decode(readText(fileName))

    /** Tekst do eksportu i udostępniania (`ProfileCodec.encode`, UTF-8). */
    @Throws(ProfileLibraryException::class)
    fun exportText(fileName: String): String = ProfileCodec.encode(load(fileName))

    // ---------- zapis ----------

    /** Zapis profilu użytkownika do `<id>.thumbcast.json`. Zwraca nazwę pliku. */
    @Throws(ProfileLibraryException::class)
    fun save(profile: Profile): String {
        if (!isSafeId(profile.id)) {
            throw ProfileLibraryException(Reason.INVALID_ID, "niedozwolone id profilu: ${profile.id}")
        }
        val fileName = fileNameFor(profile.id)
        if (presetExists(fileName)) {
            throw ProfileLibraryException(Reason.BUILT_IN_READ_ONLY, "id zajęte przez preset: $fileName")
        }
        writeValidated(fileName, profile)
        return fileName
    }

    /**
     * Nadpisanie istniejącego profilu użytkownika (np. wynik edytora). Id zostaje stare, żeby nazwa pliku
     * zgadzała się z id. Dla presetu: [Reason.BUILT_IN_READ_ONLY] (najpierw [duplicate]).
     */
    @Throws(ProfileLibraryException::class)
    fun update(fileName: String, profile: Profile): String {
        val existing = load(fileName)
        if (isBuiltIn(fileName)) throw readOnly()
        writeValidated(fileName, profile.copy(id = existing.id))
        return fileName
    }

    /** Jak [update], ale z tekstu JSON (wynik edytora); dekodowanie z limitem rozmiaru, migracjami i walidacją. */
    @Throws(ProfileLibraryException::class)
    fun updateFromText(fileName: String, json: String): String = update(fileName, decodeImport(json))

    /** Kopia (preset albo profil użytkownika) z nowym id i nazwą `<nazwa><suffix>` (unikalną). Zwraca nazwę pliku kopii. */
    @JvmOverloads
    @Throws(ProfileLibraryException::class)
    fun duplicate(fileName: String, nameSuffix: String = DEFAULT_COPY_SUFFIX): String {
        val source = load(fileName)
        return save(source.copy(id = freshId(), name = uniqueName(source.name + nameSuffix)))
    }

    @Throws(ProfileLibraryException::class)
    fun rename(fileName: String, newName: String): String {
        val name = newName.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            throw ProfileLibraryException(Reason.INVALID_NAME, "nazwa musi mieć od 1 do $MAX_NAME_LENGTH znaków")
        }
        val existing = load(fileName)
        if (isBuiltIn(fileName)) throw readOnly()
        writeValidated(fileName, existing.copy(name = name))
        return fileName
    }

    /** Usuwa profil użytkownika. Preset: [Reason.BUILT_IN_READ_ONLY]. */
    @Throws(ProfileLibraryException::class)
    fun delete(fileName: String) {
        if (!isSafeFileName(fileName)) throw notFound(fileName)
        val f = userFile(fileName)
        if (!f.isFile) throw if (presetExists(fileName)) readOnly() else notFound(fileName)
        if (!f.delete()) throw ProfileLibraryException(Reason.IO, "nie udało się usunąć $fileName")
    }

    // ---------- import ----------

    /**
     * Pierwszy krok importu (bez zapisu): limit rozmiaru, dekodowanie z migracjami i walidacją, nowe id,
     * gdy obecne koliduje z istniejącym profilem albo nie nadaje się na nazwę pliku. UI pokazuje
     * [ImportPreview] do potwierdzenia, potem woła [commitImport].
     */
    @Throws(ProfileLibraryException::class)
    fun prepareImport(text: String): ImportPreview {
        val decoded = decodeImport(text)
        val taken = takenIds()
        val needsNewId = !isSafeId(decoded.id) || decoded.id in taken
        val profile = if (needsNewId) decoded.copy(id = freshId(taken)) else decoded
        return ImportPreview(profile, originalId = decoded.id, idChanged = needsNewId)
    }

    /** Zapis po potwierdzeniu. Zwraca nazwę pliku nowego profilu. */
    @Throws(ProfileLibraryException::class)
    fun commitImport(preview: ImportPreview): String {
        // Między podglądem a potwierdzeniem mogło przybyć profili; przy kolizji losujemy id jeszcze raz.
        val taken = takenIds()
        val profile = if (preview.profile.id in taken) preview.profile.copy(id = freshId(taken)) else preview.profile
        return save(profile)
    }

    /** Import w jednym kroku (bez ekranu potwierdzenia). */
    @Throws(ProfileLibraryException::class)
    fun importText(text: String): String = commitImport(prepareImport(text))

    // ---------- pomocnicze ----------

    /** Proponowana nazwa pliku eksportu: `<nazwa>.thumbcast.json`, bez znaków niedozwolonych w nazwach plików. */
    fun exportFileName(profile: Profile): String = suggestedFileName(profile.name)

    private fun decodeImport(text: String): Profile {
        val size = utf8Size(text)
        if (size > MAX_IMPORT_BYTES) throw tooLarge(size.toLong())
        return decode(text.removePrefix(BOM))
    }

    private fun decode(text: String): Profile {
        val r = ProfileCodec.decode(text)
        val e = r.exceptionOrNull() ?: return r.getOrThrow()
        val details = if (e is ProfileValidationException) e.errors else listOf(e.message ?: e.toString())
        throw ProfileLibraryException(Reason.INVALID_PROFILE, e.message ?: "profil niepoprawny", details, e)
    }

    private fun writeValidated(fileName: String, profile: Profile) {
        val text = try {
            ProfileCodec.encode(profile)
        } catch (e: Exception) {
            throw ProfileLibraryException(Reason.INVALID_PROFILE, e.message ?: "błąd kodowania", cause = e)
        }
        // Walidujemy dokładnie to, co trafi na dysk (round-trip przez parser ścisły).
        decode(text)
        try {
            writeAtomically(userFile(fileName), text)
        } catch (e: IOException) {
            throw ProfileLibraryException(Reason.IO, e.message ?: "błąd zapisu", cause = e)
        }
    }

    private fun readOnly() = ProfileLibraryException(Reason.BUILT_IN_READ_ONLY, "preset jest tylko do odczytu")

    private fun writeAtomically(target: File, text: String) {
        if (!userDir.isDirectory && !userDir.mkdirs()) throw IOException("nie można utworzyć $userDir")
        val tmp = File(userDir, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun readText(fileName: String): String {
        if (!isSafeFileName(fileName)) throw notFound(fileName)
        val f = userFile(fileName)
        try {
            if (f.isFile) return f.readText(Charsets.UTF_8)
            if (presetExists(fileName)) return presets.read(fileName)
        } catch (e: IOException) {
            throw ProfileLibraryException(Reason.IO, e.message ?: "błąd odczytu", cause = e)
        }
        throw notFound(fileName)
    }

    private fun entryFor(fileName: String, builtIn: Boolean, read: () -> String): ProfileEntry {
        return try {
            val p = decode(read())
            ProfileEntry(fileName, p.id, p.name, p.mode, p.controls.size, builtIn, null,
                mouseGesturesDefault = MouseGesturesTransform.isEnabled(p),
                mouseGesturesStyle = MouseGesturesTransform.style(p),
                kind = ProfileKind.of(p))
        } catch (e: Exception) {
            // ProfileLibraryException (zły profil) albo IOException (odczyt).
            val msg = (e as? ProfileLibraryException)?.details?.firstOrNull() ?: e.message ?: e.toString()
            ProfileEntry(fileName, null, fileName.removeSuffix(EXTENSION), null, 0, builtIn, msg)
        }
    }

    private fun presetNames(): List<String> = try {
        presets.list().filter { it.endsWith(EXTENSION) }
    } catch (e: IOException) {
        emptyList()
    }

    private fun presetExists(fileName: String): Boolean = fileName in presetNames()

    private fun userFile(fileName: String): File = File(userDir, fileName)

    /** Id zajęte przez presety (także przesłonięte) i profile użytkownika oraz nazwy ich plików. */
    private fun takenIds(): Set<String> {
        val ids = HashSet<String>()
        for (name in presetNames()) {
            ids.add(name.removeSuffix(EXTENSION))
            entryFor(name, builtIn = true) { presets.read(name) }.id?.let { ids.add(it) }
        }
        for (e in listUser()) {
            ids.add(e.fileName.removeSuffix(EXTENSION))
            e.id?.let { ids.add(it) }
        }
        return ids
    }

    private fun freshId(taken: Set<String> = takenIds()): String {
        repeat(16) {
            val id = newId()
            if (isSafeId(id) && id !in taken && !userFile(fileNameFor(id)).exists()) return id
        }
        throw IllegalStateException("nie udało się wylosować unikalnego id")
    }

    private fun uniqueName(base: String): String {
        val trimmed = base.trim().take(MAX_NAME_LENGTH)
        val names = listAll().map { it.name }.toSet()
        if (trimmed !in names) return trimmed
        var n = 2
        while ("$trimmed $n" in names) n++
        return "$trimmed $n"
    }

    private fun notFound(fileName: String) = ProfileLibraryException(Reason.NOT_FOUND, "brak profilu $fileName")

    enum class Reason {
        /** Plik większy niż [MAX_IMPORT_BYTES]. */
        TOO_LARGE,

        /** Bajty nie są poprawnym UTF-8. */
        NOT_UTF8,

        /** Zły JSON, nieznane pole lub typ (np. `macro`), błąd migracji albo reguły walidatora; szczegóły w `details`. */
        INVALID_PROFILE,
        NOT_FOUND,
        BUILT_IN_READ_ONLY,
        INVALID_NAME,
        INVALID_ID,
        IO,
    }

    companion object {
        const val PROFILES_DIR: String = "profiles"
        const val ASSET_PRESETS_DIR: String = "presets"
        const val EXTENSION: String = ".thumbcast.json"
        const val DEFAULT_PRESET: String = ThumbcastProfiles.DEFAULT_PRESET
        const val MIME_TYPE: String = "application/json"

        /** Limit importu: 256 KB (największy preset ma kilka KB). */
        const val MAX_IMPORT_BYTES: Int = 256 * 1024
        const val MAX_NAME_LENGTH: Int = 80
        const val DEFAULT_COPY_SUFFIX: String = " (copy)"

        private val BOM: String = 0xFEFF.toChar().toString()
        private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

        /** Repozytorium dla aplikacji: `filesDir/profiles` i `assets/presets`. */
        @JvmStatic
        fun create(context: Context): ProfileRepository =
            ProfileRepository(File(context.filesDir, PROFILES_DIR), AssetPresetSource(context.assets))

        @JvmStatic
        fun fileNameFor(id: String): String = id + EXTENSION

        @JvmStatic
        fun isSafeId(id: String): Boolean = SAFE_ID.matches(id) && !id.contains("..")

        /** Nazwa pliku bez katalogów i bez `..`, z rozszerzeniem `.thumbcast.json`. */
        @JvmStatic
        fun isSafeFileName(fileName: String): Boolean =
            fileName.endsWith(EXTENSION) && !fileName.contains('/') && !fileName.contains('\\') &&
                !fileName.startsWith(".") && !fileName.contains("..")

        @JvmStatic
        fun suggestedFileName(displayName: String): String {
            val sb = StringBuilder()
            for (ch in displayName.trim()) {
                when {
                    Character.isLetterOrDigit(ch) || ch == '_' || ch == '+' -> sb.append(ch)
                    sb.isNotEmpty() && sb.last() != '-' -> sb.append('-')
                }
            }
            val base = sb.toString().trim('-').take(60).trim('-')
            return (base.ifEmpty { "profile" }) + EXTENSION
        }

        /**
         * Czyta strumień (plik z SAF, udostępniony URI) najwyżej do [limit] bajtów i dekoduje ściśle jako UTF-8.
         * Większy plik: [Reason.TOO_LARGE]; złe bajty: [Reason.NOT_UTF8].
         */
        @JvmStatic
        @JvmOverloads
        @Throws(ProfileLibraryException::class)
        fun readLimited(input: InputStream, limit: Int = MAX_IMPORT_BYTES): String {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) throw tooLarge(total, orMore = true)
                    out.write(buf, 0, n)
                }
            } catch (e: IOException) {
                throw ProfileLibraryException(Reason.IO, e.message ?: "błąd odczytu", cause = e)
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString()
            } catch (e: CharacterCodingException) {
                throw ProfileLibraryException(Reason.NOT_UTF8, "plik nie jest tekstem UTF-8", cause = e)
            }
        }

        private fun tooLarge(size: Long, orMore: Boolean = false) = ProfileLibraryException(
            Reason.TOO_LARGE,
            "plik ma ${if (orMore) "ponad " else ""}$size B, limit to $MAX_IMPORT_BYTES B",
        )

        private fun utf8Size(text: String): Int {
            var n = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                n += when {
                    c.code < 0x80 -> 1
                    c.code < 0x800 -> 2
                    Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                        i++
                        4
                    }
                    else -> 3
                }
                if (n > MAX_IMPORT_BYTES) return n
                i++
            }
            return n
        }
    }
}

/**
 * Wiersz listy w bibliotece. Przy uszkodzonym pliku `id` i `mode` są null, a `error` ma pierwszy błąd.
 * [mouseGesturesDefault]: czy profil sam z siebie ma gesty myszy (wartość przełącznika „Gesty myszy” bez wpisu
 * użytkownika); [mouseGesturesStyle]: jak wyglądają gesty myszy po włączeniu (mysz bezpośrednia, gładzik albo kliki
 * w punkcie przy kamerze), do opisu przełącznika. Patrz `MouseGesturesTransform`.
 */
data class ProfileEntry(
    val fileName: String,
    val id: String?,
    val name: String,
    val mode: ProfileMode?,
    val controlCount: Int,
    val builtIn: Boolean,
    val error: String?,
    val mouseGesturesDefault: Boolean = false,
    val mouseGesturesStyle: MouseGesturesTransform.Style = MouseGesturesTransform.Style.DIRECT,
    /** Rodzaj profilu do ikony na liście ([ProfileKind]); null przy uszkodzonym pliku. */
    val kind: ProfileKind? = null,
) {
    val isValid: Boolean get() = error == null
}

/** Wynik pierwszego kroku importu (do ekranu potwierdzenia). */
data class ImportPreview(
    val profile: Profile,
    /** Id z pliku; różne od `profile.id`, gdy [idChanged]. */
    val originalId: String,
    val idChanged: Boolean,
) {
    val name: String get() = profile.name
    val controlCount: Int get() = profile.controls.size
    val mode: ProfileMode get() = profile.mode
}

/** Błąd biblioteki z kategorią dla UI i szczegółami (np. lista błędów walidatora, po polsku). */
class ProfileLibraryException(
    val reason: ProfileRepository.Reason,
    message: String,
    val details: List<String> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)
