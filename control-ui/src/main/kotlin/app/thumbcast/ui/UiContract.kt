package app.thumbcast.ui

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Kontrakt modułu :control-ui dla :app (docs/ui-audit.md, D8; opis w docs/ui.md). Wołany z Javy, więc stałe są
 * `const`, a funkcje `@JvmStatic`.
 *
 * Kreator ([OnboardingActivity]):
 * - uruchomienie: `startActivityForResult(UiContract.onboardingIntent(ctx), kod)`;
 * - wynik: [Activity.RESULT_OK] z [EXTRA_PRESET_FILE] (nazwa pliku presetu, ta sama wartość co preferencja
 *   `list_thumbcast_profile`) i [EXTRA_HIDE_ON_GAMEPAD]; [Activity.RESULT_CANCELED] = pominięto lub zamknięto.
 *   Odczyt: [parseOnboardingResult]. Preferencje zapisuje :app, nie kreator.
 * - kiedy pokazać: [shouldShowOnboarding] z wartością [PREF_ONBOARDING_VERSION] z domyślnych SharedPreferences
 *   aplikacji. :app zapisuje [CURRENT_ONBOARDING_VERSION] po każdym powrocie z kreatora (OK i CANCELED) oraz od
 *   razu, gdy kreator nie jest potrzebny, bo lista PC nie jest pusta.
 *
 * Poradnik ([SetupGuideActivity]): `startActivity(UiContract.setupGuideIntent(ctx, UiContract.SECTION_PAIR))`;
 * sekcja jest opcjonalna (`null` = od początku), nieznana jest ignorowana.
 */
object UiContract {

    // ----- Nazwy klas (np. dla Intent.setClassName albo testów) -----

    const val ONBOARDING_ACTIVITY: String = "app.thumbcast.ui.OnboardingActivity"
    const val SETUP_GUIDE_ACTIVITY: String = "app.thumbcast.ui.SetupGuideActivity"

    // ----- Wynik kreatora -----

    /** String: nazwa pliku presetu, np. [PRESET_GAMEPAD_PLAYSTATION]. Tylko przy RESULT_OK. */
    const val EXTRA_PRESET_FILE: String = "app.thumbcast.ui.extra.PRESET_FILE"

    /** Boolean: chowaj przyciski, gdy gram padem. Tylko przy RESULT_OK. */
    const val EXTRA_HIDE_ON_GAMEPAD: String = "app.thumbcast.ui.extra.HIDE_ON_GAMEPAD"

    /** Presety z katalogu `presets/` (assets/presets w APK), których używa kreator. */
    const val PRESET_GAMEPAD_XBOX: String = "gamepad-xbox.thumbcast.json"
    const val PRESET_GAMEPAD_PLAYSTATION: String = "gamepad-playstation.thumbcast.json"
    const val PRESET_MMO_KEYBOARD: String = "mmo-keyboard.thumbcast.json"

    private const val PRESET_SUFFIX = ".thumbcast.json"

    // ----- Wejście poradnika -----

    /** String (opcjonalny): sekcja, od której poradnik się otwiera; jedna z [GUIDE_SECTIONS]. */
    const val EXTRA_GUIDE_SECTION: String = "app.thumbcast.ui.extra.GUIDE_SECTION"

    const val SECTION_INSTALL: String = "install"
    const val SECTION_GAMEPAD: String = "gamepad"
    const val SECTION_PAIR: String = "pair"
    const val SECTION_APPS: String = "apps"
    const val SECTION_REMOTE: String = "remote"
    const val SECTION_TROUBLESHOOT: String = "troubleshoot"

    /** Sekcje poradnika w kolejności wyświetlania. */
    @JvmField
    val GUIDE_SECTIONS: List<String> = listOf(
        SECTION_INSTALL, SECTION_GAMEPAD, SECTION_PAIR, SECTION_APPS, SECTION_REMOTE, SECTION_TROUBLESHOOT,
    )

    // ----- Wersja kreatora -----

    /** Int w domyślnych SharedPreferences aplikacji: wersja kreatora, którą użytkownik już widział (brak = 0). */
    const val PREF_ONBOARDING_VERSION: String = "thumbcast_onboarding_version"

    /** Bieżąca wersja kreatora. Podnieść, gdy nowa wersja ma się pokazać także dotychczasowym użytkownikom bez PC. */
    const val CURRENT_ONBOARDING_VERSION: Int = 1

    // ----- Czysta logika (testy JVM: UiContractTest) -----

    /** Kreator pokazuje się tylko przy pustej liście PC i gdy zapisana wersja jest starsza od bieżącej. */
    @JvmStatic
    fun shouldShowOnboarding(storedVersion: Int, hasPcs: Boolean): Boolean =
        !hasPcs && storedVersion < CURRENT_ONBOARDING_VERSION

    /** Sekcja poradnika albo `null`, gdy brak lub nieznana. */
    @JvmStatic
    fun normalizeGuideSection(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { it in GUIDE_SECTIONS }

    /** Sama nazwa pliku presetu (bez ścieżki) z rozszerzeniem `.thumbcast.json`. */
    @JvmStatic
    fun isValidPresetFile(name: String?): Boolean =
        name != null &&
            name.length > PRESET_SUFFIX.length &&
            name.endsWith(PRESET_SUFFIX) &&
            !name.contains('/') && !name.contains('\\') &&
            !name.startsWith(".")

    // ----- Intencje -----

    @JvmStatic
    fun onboardingIntent(context: Context): Intent = Intent(context, OnboardingActivity::class.java)

    @JvmStatic
    fun setupGuideIntent(context: Context, section: String?): Intent {
        val intent = Intent(context, SetupGuideActivity::class.java)
        normalizeGuideSection(section)?.let { intent.putExtra(EXTRA_GUIDE_SECTION, it) }
        return intent
    }

    /** Sekcja startowa z intencji poradnika (dla [SetupGuideActivity]). */
    @JvmStatic
    fun guideSectionFrom(intent: Intent?): String? = normalizeGuideSection(intent?.getStringExtra(EXTRA_GUIDE_SECTION))

    /** Dane wyniku kreatora do `setResult(RESULT_OK, ...)` (dla [OnboardingActivity]). */
    @JvmStatic
    fun onboardingResultData(result: OnboardingResult): Intent =
        Intent()
            .putExtra(EXTRA_PRESET_FILE, result.presetFile)
            .putExtra(EXTRA_HIDE_ON_GAMEPAD, result.hideOnGamepad)

    /** Wynik kreatora albo `null` (anulowano, brak danych lub zła nazwa presetu). */
    @JvmStatic
    fun parseOnboardingResult(resultCode: Int, data: Intent?): OnboardingResult? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val preset = data.getStringExtra(EXTRA_PRESET_FILE)
        if (!isValidPresetFile(preset)) return null
        return OnboardingResult(preset!!, data.getBooleanExtra(EXTRA_HIDE_ON_GAMEPAD, false))
    }
}

/** Wybór z kreatora: preset (nazwa pliku, jak `list_thumbcast_profile`) i „Chowaj przyciski, gdy gram padem”. */
data class OnboardingResult(
    val presetFile: String,
    @get:JvmName("isHideOnGamepad") val hideOnGamepad: Boolean,
)
