package app.thumbcast.android.apps

import app.thumbcast.android.library.ProfileRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Ustawienia strumienia jednej aplikacji hosta (np. „Steam Big Picture”, gra z Battle.net), zapisane na telefonie.
 * Każde pole `null` = ustawienie globalne. Logika bez API Androida (testy JVM w `AppStreamSettingsTest`).
 *
 * Nic tu nie generuje wejść: to tylko wybór profilu sterowania, rozdzielczości i FPS na start strumienia.
 */
data class AppStreamSettings(
    /** Nazwa pliku profilu z biblioteki (jak wartość `list_thumbcast_profile`); null = profil globalny. */
    val profileFile: String? = null,
    /** Rozdzielczość strumienia; null = globalna. */
    val resolution: StreamResolution? = null,
    /** Liczba klatek na sekundę; null = globalna. */
    val fps: Int? = null,
) {
    val isEmpty: Boolean get() = profileFile == null && resolution == null && fps == null

    /** Kopia bez wartości spoza dozwolonych zakresów (zastępowanych ustawieniem globalnym). */
    fun sanitized(): AppStreamSettings = AppStreamSettings(
        profileFile = profileFile?.takeIf { isValidProfileFile(it) },
        resolution = resolution?.takeIf { it.isValid },
        fps = fps?.takeIf { isValidFps(it) },
    )

    companion object {
        @JvmField
        val NONE: AppStreamSettings = AppStreamSettings()

        const val MIN_FPS: Int = 10
        const val MAX_FPS: Int = 480

        @JvmStatic
        fun isValidFps(fps: Int): Boolean = fps in MIN_FPS..MAX_FPS

        @JvmStatic
        fun isValidProfileFile(fileName: String): Boolean =
            fileName.isNotBlank() && ProfileRepository.isSafeFileName(fileName)
    }
}

/** Rozdzielczość strumienia w pikselach (szerokość × wysokość), jak wartości `list_resolution` upstreamu. */
data class StreamResolution(val width: Int, val height: Int) {
    val isValid: Boolean get() = width in MIN_SIDE..MAX_SIDE && height in MIN_SIDE..MAX_SIDE

    /** Zapis jak w preferencjach upstreamu: `1280x720`. */
    override fun toString(): String = "${width}x$height"

    companion object {
        const val MIN_SIDE: Int = 64
        const val MAX_SIDE: Int = 8192

        /** `1280x720` (także `1280X720`, spacje dookoła) albo null, gdy napis jest niepoprawny lub poza zakresem. */
        @JvmStatic
        fun parse(text: String?): StreamResolution? {
            val parts = text?.trim()?.lowercase(Locale.ROOT)?.split('x') ?: return null
            if (parts.size != 2) return null
            val w = parts[0].trim().toIntOrNull() ?: return null
            val h = parts[1].trim().toIntOrNull() ?: return null
            return StreamResolution(w, h).takeIf { it.isValid }
        }
    }
}

/** Wpis jednej aplikacji: id z listy aplikacji hosta, nazwa (do dopasowania, gdy id się zmieni) i ustawienia. */
data class AppSettingsEntry(val appId: Int, val appName: String, val settings: AppStreamSettings)

/**
 * Ustawienia aplikacji jednego hosta (PC). Klucz po stronie telefonu: [hostKey] (uuid PC z `serverinfo`,
 * jak `HiddenApps` w upstreamie) + id aplikacji, z nazwą aplikacji jako zapasowym kluczem.
 *
 * Dopasowanie ([find]), w tej kolejności:
 * 1. to samo id i ta sama nazwa (bez rozróżniania wielkości liter);
 * 2. ta sama nazwa, inne id: Sunshine liczy id z nazwy i obrazka aplikacji (CRC32), więc zmiana obrazka zmienia id;
 * 3. to samo id, inna albo nieznana nazwa: wznowienie z ekranu PC i skrót „wznów” podają nazwę zastępczą
 *    (`"app"`) albo null. Zmiana nazwy w Sunshine zmienia id, więc ten przypadek to praktycznie zawsze ta sama aplikacja.
 */
class HostAppSettings(entries: List<AppSettingsEntry>) {

    val entries: List<AppSettingsEntry> = entries.toList()

    fun find(appId: Int, appName: String?): AppSettingsEntry? {
        val name = normalizeName(appName)
        if (name.isNotEmpty()) {
            entries.firstOrNull { it.appId == appId && normalizeName(it.appName) == name }?.let { return it }
            entries.firstOrNull { normalizeName(it.appName) == name }?.let { return it }
        }
        return entries.firstOrNull { it.appId == appId }
    }

    /** Ustawienia dla aplikacji albo [AppStreamSettings.NONE]. */
    fun settingsFor(appId: Int, appName: String?): AppStreamSettings = find(appId, appName)?.settings ?: AppStreamSettings.NONE

    /**
     * Nowa lista z ustawieniami aplikacji. Usuwa wpisy o tym samym id albo tej samej nazwie (stare id po zmianie
     * obrazka), a puste ustawienia (wszystko globalne) usuwają aplikację z listy.
     */
    fun with(appId: Int, appName: String?, settings: AppStreamSettings): HostAppSettings {
        val name = normalizeName(appName)
        val kept = entries.filterNot { it.appId == appId || (name.isNotEmpty() && normalizeName(it.appName) == name) }
        val clean = settings.sanitized()
        return HostAppSettings(if (clean.isEmpty) kept else kept + AppSettingsEntry(appId, appName?.trim() ?: "", clean))
    }

    fun encode(): String {
        val root = buildJsonObject {
            put(KEY_VERSION, FORMAT_VERSION)
            put(KEY_APPS, buildJsonArray {
                for (e in entries) {
                    add(buildJsonObject {
                        put(KEY_APP_ID, e.appId)
                        put(KEY_APP_NAME, e.appName)
                        e.settings.profileFile?.let { put(KEY_PROFILE, it) }
                        e.settings.resolution?.let {
                            put(KEY_WIDTH, it.width)
                            put(KEY_HEIGHT, it.height)
                        }
                        e.settings.fps?.let { put(KEY_FPS, it) }
                    })
                }
            })
        }
        return root.toString()
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
        private const val KEY_VERSION = "version"
        private const val KEY_APPS = "apps"
        private const val KEY_APP_ID = "appId"
        private const val KEY_APP_NAME = "appName"
        private const val KEY_PROFILE = "profile"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_FPS = "fps"

        @JvmField
        val EMPTY: HostAppSettings = HostAppSettings(emptyList())

        /** Klucz hosta: uuid PC bez spacji, małymi literami (upstream porównuje uuid z `equalsIgnoreCase`). */
        @JvmStatic
        fun hostKey(pcUuid: String?): String? = pcUuid?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        /**
         * Odczyt tolerancyjny: brak lub uszkodzony tekst = pusta lista, wpis bez `appId` jest pomijany,
         * pole spoza zakresu = ustawienie globalne. Nieznane pola są ignorowane (nowsza wersja aplikacji).
         */
        @JvmStatic
        fun decode(text: String?): HostAppSettings {
            if (text.isNullOrBlank()) return EMPTY
            val root = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                return EMPTY
            }
            val apps = ((root as? JsonObject)?.get(KEY_APPS) as? JsonArray) ?: return EMPTY
            val out = ArrayList<AppSettingsEntry>()
            for (item in apps) {
                val o = item as? JsonObject ?: continue
                val appId = o.int(KEY_APP_ID) ?: continue
                val appName = o.string(KEY_APP_NAME) ?: ""
                val w = o.int(KEY_WIDTH)
                val h = o.int(KEY_HEIGHT)
                val settings = AppStreamSettings(
                    profileFile = o.string(KEY_PROFILE),
                    resolution = if (w != null && h != null) StreamResolution(w, h) else null,
                    fps = o.int(KEY_FPS),
                ).sanitized()
                if (!settings.isEmpty) out += AppSettingsEntry(appId, appName, settings)
            }
            return HostAppSettings(out)
        }

        private fun normalizeName(name: String?): String = name?.trim()?.lowercase(Locale.ROOT) ?: ""

        private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

        private fun JsonObject.int(key: String): Int? {
            val p = primitive(key) ?: return null
            return if (p.isString) null else p.intOrNull
        }

        private fun JsonObject.string(key: String): String? {
            val p = primitive(key) ?: return null
            return if (p.isString) p.content else null
        }
    }
}
