package app.thumbcast.android.apps

/**
 * Parametry strumienia po nałożeniu ustawień aplikacji na ustawienia globalne. Używane tylko dla jednego
 * strumienia: preferencje globalne się nie zmieniają.
 */
data class StreamParams(val width: Int, val height: Int, val fps: Int)

/**
 * Wynik wyboru profilu na start strumienia.
 *
 * @property fileName plik, który się wczytał (albo ostatni próbowany, gdy [value] jest null)
 * @property value wczytany profil; null = nie wczytał się ani profil aplikacji, ani globalny
 * @property fromApp true, gdy działa profil przypisany do aplikacji (zmiana profilu w grze dotyczy wtedy tylko tej sesji)
 * @property appProfileFailed true, gdy aplikacja miała profil, ale go brak albo jest niepoprawny (użyto globalnego)
 * @property defaultProfileFailed true, gdy profilu domyślnego (globalnego) brak albo jest niepoprawny i użyto
 *   profilu dostarczanego z aplikacją (np. plik usunięty poza biblioteką)
 */
class ProfileSelection<T : Any>(
    val fileName: String,
    val value: T?,
    val fromApp: Boolean,
    val appProfileFailed: Boolean,
    val defaultProfileFailed: Boolean = false,
)

object AppStreamOverrides {

    /** Rozdzielczość i FPS aplikacji zastępują globalne; brak (null) albo wartość spoza zakresu = globalne. */
    @JvmStatic
    fun applyStream(global: StreamParams, settings: AppStreamSettings): StreamParams {
        val clean = settings.sanitized()
        val res = clean.resolution
        return StreamParams(
            width = res?.width ?: global.width,
            height = res?.height ?: global.height,
            fps = clean.fps ?: global.fps,
        )
    }

    /**
     * Profil sterowania na start strumienia: najpierw profil aplikacji ([appFile]), a gdy go nie ma w bibliotece
     * albo nie przechodzi walidacji (np. usunięty), profil domyślny ([globalFile], preferencja
     * `list_thumbcast_profile`). Gdy i tego brak, profil dostarczany z aplikacją ([shippedDefault], o ile podany
     * i inny niż [globalFile]) z [ProfileSelection.defaultProfileFailed]. [load] zwraca null przy błędzie.
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Any> selectProfile(
        appFile: String?,
        globalFile: String,
        load: (String) -> T?,
        shippedDefault: String? = null,
    ): ProfileSelection<T> {
        val app = appFile?.takeIf { AppStreamSettings.isValidProfileFile(it) }
        if (app != null) {
            val loaded = load(app)
            if (loaded != null) {
                return ProfileSelection(app, loaded, fromApp = true, appProfileFailed = false)
            }
        }
        val failed = appFile != null && appFile.isNotBlank()
        val global = load(globalFile)
        if (global == null && shippedDefault != null && shippedDefault != globalFile) {
            val shipped = load(shippedDefault)
            if (shipped != null) {
                return ProfileSelection(shippedDefault, shipped, fromApp = false, appProfileFailed = failed,
                    defaultProfileFailed = true)
            }
        }
        return ProfileSelection(globalFile, global, fromApp = false, appProfileFailed = failed)
    }
}
