package app.thumbcast.android

import android.content.Context
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import java.io.File

/**
 * Minimalne ładowanie profilu dla `Game` w MVP (bez edytora): plik z `filesDir/profiles/`
 * albo preset z assetów aplikacji. Wynik przechodzi pełną walidację `ProfileCodec.decode`.
 *
 * Presety z `presets/` (katalog główny repo) mają trafić do assetów aplikacji jako
 * `assets/presets/<nazwa>.thumbcast.json` (konfiguruje SCAFFOLD / :app; patrz patches/APPLY.md).
 */
object ThumbcastProfiles {

    const val PROFILES_DIR: String = "profiles"
    const val ASSET_PRESETS_DIR: String = "presets"
    /**
     * Profil domyślny dostarczany z aplikacją (od 12.11.2026 „Pad – PlayStation”, wcześniej „Pad – Xbox”).
     * Działa, dopóki użytkownik nie wybierze innego w bibliotece (preferencja `list_thumbcast_profile` niezapisana),
     * i jest zapasowym, gdy wybrany profil domyślny zniknie.
     */
    const val DEFAULT_PRESET: String = "gamepad-playstation.thumbcast.json"

    /** Wynik jako `Result`, żeby Game mogło pokazać błąd walidacji i wrócić do trybu legacy. */
    @JvmStatic
    fun load(context: Context, fileName: String): Result<Profile> {
        val text: String = try {
            readText(context, fileName)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return ProfileCodec.decode(text)
    }

    /** Wygodny wariant dla Javy: profil albo null (błąd jest w `lastError`). */
    @JvmStatic
    fun loadOrNull(context: Context, fileName: String): Profile? {
        val r: Result<Profile> = load(context, fileName)
        lastError = r.exceptionOrNull()
        return r.getOrNull()
    }

    @JvmStatic
    var lastError: Throwable? = null
        private set

    private fun readText(context: Context, fileName: String): String {
        val dir: File = File(context.filesDir, PROFILES_DIR)
        val f: File = File(dir, fileName)
        if (f.isFile) {
            return f.readText(Charsets.UTF_8)
        }
        val bytes: ByteArray = context.assets.open("$ASSET_PRESETS_DIR/$fileName").use { input -> input.readBytes() }
        return String(bytes, Charsets.UTF_8)
    }
}
