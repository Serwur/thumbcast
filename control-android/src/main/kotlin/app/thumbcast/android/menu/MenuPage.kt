package app.thumbcast.android.menu

/**
 * Strony menu w grze (`ThumbcastSession` w `:app`). Czysty JVM: testy w `MenuPageTest`.
 *
 * - [MAIN]: strona główna (Rozłącz, Zakończ sesję, przełączniki, rozmiar, Wróć do gry).
 * - [PROFILES]: lista profili do zmiany w trakcie gry.
 * - [END_SESSION]: potwierdzenie „Zakończ sesję” (zamknięcie aplikacji na PC, niezapisane postępy przepadają).
 */
enum class MenuPage {
    MAIN,
    PROFILES,
    END_SESSION,
    ;

    /** B, Back, Escape: z podstrony na stronę główną; ze strony głównej null = zamknij menu (powrót do gry). */
    fun back(): MenuPage? = if (this == MAIN) null else MAIN

}
