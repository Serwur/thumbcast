package app.thumbcast.android.library

import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * Źródło presetów wbudowanych (tylko do odczytu). W aplikacji: `assets/presets/` ([AssetPresetSource]);
 * w testach JVM: zwykły katalog ([DirectoryPresetSource]) albo implementacja w pamięci.
 */
interface PresetSource {
    /** Nazwy plików `*.thumbcast.json` (bez katalogu). */
    fun list(): List<String>

    /** Treść pliku w UTF-8; [IOException], gdy pliku nie ma. */
    @Throws(IOException::class)
    fun read(fileName: String): String
}

/** Presety z `assets/<dir>/` aplikacji (kopiuje je tam zadanie `copyThumbcastPresets` w :app). */
class AssetPresetSource(
    private val assets: AssetManager,
    private val dir: String = ProfileRepository.ASSET_PRESETS_DIR,
) : PresetSource {

    override fun list(): List<String> =
        (assets.list(dir) ?: emptyArray()).filter { it.endsWith(ProfileRepository.EXTENSION) }

    override fun read(fileName: String): String =
        assets.open("$dir/$fileName").use { String(it.readBytes(), Charsets.UTF_8) }
}

/** Presety z katalogu na dysku (testy JVM, narzędzia). */
class DirectoryPresetSource(private val dir: File) : PresetSource {

    override fun list(): List<String> =
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(ProfileRepository.EXTENSION) }?.map { it.name }
            ?: emptyList()

    override fun read(fileName: String): String {
        val f = File(dir, fileName)
        if (!f.isFile) throw IOException("brak presetu $fileName")
        return f.readText(Charsets.UTF_8)
    }
}
