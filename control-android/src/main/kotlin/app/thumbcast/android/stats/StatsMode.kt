package app.thumbcast.android.stats

/** Co pokazuje nakładka statystyk w trybie Thumbcast. */
enum class StatsMode {
    OFF,

    /** Jedna linia: FPS i szacowane opóźnienie. */
    BASIC,

    /** Linia podstawowa i szczegóły (rozdzielczość, kodek, dekoder, klatki, sieć, host, dekodowanie). */
    ADVANCED,
}

/**
 * Dwa przełączniki z menu w grze, zapisywane globalnie w preferencjach:
 * - [enabled] = „Statystyki” (ten sam klucz co upstreamowe „Pokaż statystyki wydajności”, `checkbox_enable_perf_overlay`),
 * - [advanced] = „Szczegółowe statystyki” (domyślnie wył.).
 *
 * Nakładkę pokazuje tylko [enabled]; [advanced] dodaje szczegóły. Włączenie szczegółów przy wyłączonych
 * statystykach włącza też statystyki (inaczej przełącznik nic by nie zmienił na ekranie). Wyłączenie statystyk
 * zostawia wybór szczegółów na następny raz. Czysty JVM: testy w `StatsTogglesTest`.
 */
data class StatsToggles(val enabled: Boolean, val advanced: Boolean) {

    val mode: StatsMode
        get() = when {
            !enabled -> StatsMode.OFF
            advanced -> StatsMode.ADVANCED
            else -> StatsMode.BASIC
        }

    /** Szczegóły widoczne teraz na ekranie (etykieta w menu: wł. tylko, gdy nakładka je pokazuje). */
    val advancedShown: Boolean
        get() = enabled && advanced

    /** Przełącznik „Statystyki”. */
    fun toggleEnabled(): StatsToggles = copy(enabled = !enabled)

    /** Przełącznik „Szczegółowe statystyki”: wł. = statystyki też wł.; wył. = tylko bez szczegółów. */
    fun toggleAdvanced(): StatsToggles =
        if (advancedShown) copy(advanced = false) else StatsToggles(enabled = true, advanced = true)
}
