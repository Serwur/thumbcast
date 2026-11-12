package app.thumbcast.android.menu

/**
 * Strony menu w grze (`ThumbcastSession` w `:app`). Czysty JVM: testy w `MenuPageTest`.
 *
 * - [MAIN]: strona główna (od 12.11.2026: „Wróć do gry”, szybkie akcje, sekcje Sterowanie i Obraz, na dole Wyjście).
 * - [PROFILES]: lista profili do zmiany w trakcie gry (sekcje „Wbudowane” i „Moje”).
 * - [END_SESSION]: potwierdzenie „Zamknij grę na PC” (dawniej „Zakończ sesję”: zamknięcie aplikacji na PC,
 *   niezapisane postępy przepadają).
 */
enum class MenuPage {
    MAIN,
    PROFILES,
    END_SESSION,
    ;

    /** B, Back, Escape: z podstrony na stronę główną; ze strony głównej null = zamknij menu (powrót do gry). */
    fun back(): MenuPage? = if (this == MAIN) null else MAIN

}
