package app.thumbcast.ui.guide

import app.thumbcast.ui.UiContract

/** Strony pobierania używane w kreatorze i poradniku (źródło: docs/host-setup.md, docs/tailscale.md). */
object GuideLinks {
    /**
     * Najnowsze stabilne wydanie Sunshine (repo przeniesione z LizardByte/Sunshine; stary adres przekierowuje).
     * `/releases/latest` otwiera od razu stronę jednego wydania z listą plików (Assets), bez wersji testowych.
     * Nazwy plików w tekstach (`Sunshine-Windows-AMD64-installer.msi`, `ViGEmBus_1.22.0_x64_x86_arm64.exe`)
     * sprawdzone 12.11.2026 dla Sunshine v2026.914.233613 i ViGEmBus v1.22.0 (ostatnie wydanie, repo zarchiwizowane).
     */
    const val SUNSHINE_RELEASES: String = "https://github.com/SunshineStream/Sunshine/releases/latest"

    /** ViGEmBus, sterownik pada wymagany do grania padem. */
    const val VIGEMBUS_RELEASES: String = "https://github.com/nefarius/ViGEmBus/releases/latest"

    const val TAILSCALE_DOWNLOAD: String = "https://tailscale.com/download"

    /** Treść do udostępnienia: jedna linia podpowiedzi i adres w osobnej linii (łatwy do kliknięcia na PC). */
    fun shareText(hint: String, url: String): String = hint.trim() + "\n" + url
}

/**
 * Czysta logika poradnika „Jak podłączyć PC” (testy JVM: SetupGuideModelTest). Sekcje i ich kolejność pochodzą
 * z [UiContract.GUIDE_SECTIONS].
 */
object SetupGuideModel {

    val SECTIONS: List<String> get() = UiContract.GUIDE_SECTIONS

    /**
     * Sekcje z polem „Zrobione” na liście kontrolnej. „Nie działa?” nie ma czego odhaczać, więc jej tu nie ma.
     */
    val CHECKABLE_SECTIONS: List<String> = SECTIONS.filter { it != UiContract.SECTION_TROUBLESHOOT }

    /** Odczyt zapisanej listy kontrolnej: tylko znane sekcje z polem „Zrobione”. */
    fun sanitizeDone(stored: Set<String>?): Set<String> =
        stored.orEmpty().filterTo(LinkedHashSet()) { it in CHECKABLE_SECTIONS }

    /** Zaznaczenie lub odznaczenie sekcji; nieznana sekcja nie zmienia zbioru. */
    fun setDone(done: Set<String>, section: String, checked: Boolean): Set<String> {
        if (section !in CHECKABLE_SECTIONS) return done
        return if (checked) done + section else done - section
    }

    /** Liczba zrobionych sekcji (do napisu „Zrobione: 2 z 5”). */
    fun doneCount(done: Set<String>): Int = CHECKABLE_SECTIONS.count { it in done }

    /**
     * Sekcja rozwinięta na starcie:
     * - poprawna sekcja z intencji ([UiContract.EXTRA_GUIDE_SECTION]) ma pierwszeństwo;
     * - bez niej pierwsza nieodhaczona sekcja z listy kontrolnej (nowy użytkownik: „Zainstaluj Sunshine”);
     * - wszystko odhaczone: żadna (`null`), użytkownik widzi przegląd.
     */
    fun initialSection(requested: String?, done: Set<String>): String? =
        UiContract.normalizeGuideSection(requested)
            ?: CHECKABLE_SECTIONS.firstOrNull { it !in done }

    /** Pozycja sekcji na liście (0..5) albo -1 dla nieznanej. */
    fun indexOf(section: String?): Int = if (section == null) -1 else SECTIONS.indexOf(section)

    /** Rozwinięcie lub zwinięcie sekcji (kilka sekcji może być otwartych naraz). */
    fun toggleExpanded(expanded: Set<String>, section: String): Set<String> =
        if (section in expanded) expanded - section else expanded + section
}

/** Fragment tekstu kroku: zwykły tekst albo kod (adres, nazwa pliku) pisany czcionką o stałej szerokości. */
data class TextSegment(val text: String, val isCode: Boolean)

/**
 * Podział tekstu z zasobów na fragmenty: to, co jest między znakami `` ` ``, to kod (np. `` `https://localhost:47990` ``).
 * Niezamknięty znak `` ` `` zostaje zwykłym tekstem. Puste fragmenty są pomijane.
 */
fun parseInlineCode(text: String): List<TextSegment> {
    val out = ArrayList<TextSegment>()
    var pos = 0
    while (pos < text.length) {
        val open = text.indexOf('`', pos)
        if (open < 0) break
        val close = text.indexOf('`', open + 1)
        if (close < 0) break
        if (open > pos) out += TextSegment(text.substring(pos, open), isCode = false)
        if (close > open + 1) out += TextSegment(text.substring(open + 1, close), isCode = true)
        pos = close + 1
    }
    if (pos < text.length) out += TextSegment(text.substring(pos), isCode = false)
    return out
}

/** Tekst bez znaczników `` ` `` (np. do opisu dla czytnika ekranu albo do udostępnienia). */
fun plainText(text: String): String = parseInlineCode(text).joinToString("") { it.text }
