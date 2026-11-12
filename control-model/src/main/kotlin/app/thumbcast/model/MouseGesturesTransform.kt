package app.thumbcast.model

/**
 * Przełącznik użytkownika „Gesty myszy” (12.11.2026): dotyk w wolnej strefie (poza kontrolkami) steruje myszą
 * hosta albo nie. Działa na każdym profilu, także na presetach tylko do odczytu: wartość trzyma telefon osobno
 * dla każdego pliku profilu (nie plik profilu), a ta funkcja daje profil efektywny dla silnika i rysowania.
 *
 * Znaczenie zależy od `gestures.freeZone` profilu bazowego:
 *
 * | freeZone bazowy | domyślnie | wł.                                                               | wył.                                       |
 * |-----------------|-----------|-------------------------------------------------------------------|--------------------------------------------|
 * | `none` (pady)   | wył.      | `direct`: tap = kursor w punkcie + LPM, tap 2 palcami = PPM w punkcie, przeciągnięcie = przewijanie w punkcie | bez zmian (tylko menu z górnej krawędzi)   |
 * | `direct`        | wł.       | bez zmian                                                         | `none` (tylko menu z górnej krawędzi)      |
 * | `touchpad`      | wł.       | bez zmian (gładzik: kursor, kliki w miejscu kursora)              | `none` (tylko menu z górnej krawędzi)      |
 * | `camera` (MMO)  | wł., gdy profil ma tap, long-press albo pinch | tap = LPM w punkcie, tap 2 palcami i przytrzymanie = PPM w punkcie, szczypanie = kółko | tap, long-press i pinch wyłączone |
 *
 * Do 12.11.2026 (pierwsza wersja) pady po włączeniu dostawały `touchpad`; właściciel wybrał mysz bezpośrednią
 * (`direct`), bo tap w punkcie i przewijanie jednym palcem są bardziej intuicyjne niż kursor względny.
 * W `camera` tap dwoma palcami (PPM) zależy od `tapTarget.enabled`, więc wł./wył. obejmuje go bez nowego pola.
 *
 * Kamera przeciąganiem (`camera`) zostaje w obu stanach: to rdzeń profili MMO i nie jest „myszą” w sensie kliku.
 * Edge swipe (menu) zostaje zawsze. Brak zapisanej wartości (`null`) albo wartość równa domyślnej zwraca
 * profil bez zmian, więc nikt nie widzi zmiany zachowania, dopóki sam nie przełączy.
 *
 * Zmieniane są wyłącznie pola gestów (`freeZone`, `enabled` gestów, ewentualnie `longPress.minMs`). Kontrolek,
 * wyjść i trybu profilu funkcja nie dotyka, więc reguła „jedna gałka = jedno źródło” jest spełniona (kamera nigdy
 * nie jest dokładana), a każdy gest i tak przechodzi przez ActionPolicy (1 dotyk = 1 wejście, bez timerów).
 * Wynik przechodzi walidator; gdyby nie przeszedł (profil bazowy już niepoprawny), zwracany jest profil bazowy.
 */
object MouseGesturesTransform {

    /** Jak wyglądają gesty myszy w danym profilu po włączeniu (do opisu w UI). */
    enum class Style {
        /** Gładzik: przeciągnięcie rusza kursorem, kliki w miejscu kursora (bazowo `touchpad`). */
        TOUCHPAD,

        /** Mysz bezpośrednia: kliki w punkcie dotyku, przeciągnięcie = przewijanie (bazowo `none` albo `direct`). */
        DIRECT,

        /** Kliki w punkcie dotyku, szczypanie = kółko, przeciągnięcie = kamera (bazowo `camera`). */
        AT_POINT,
    }

    /** Czy profil ma gesty myszy sam z siebie (wartość domyślna przełącznika). */
    @JvmStatic
    fun isEnabled(p: Profile): Boolean {
        val g = p.gestures
        return when (g.freeZone) {
            FreeZoneMode.TOUCHPAD, FreeZoneMode.DIRECT -> true
            FreeZoneMode.CAMERA -> g.tapTarget.enabled || g.longPress.enabled || g.pinchZoom.enabled
            FreeZoneMode.NONE -> false
        }
    }

    @JvmStatic
    fun style(p: Profile): Style = when (p.gestures.freeZone) {
        FreeZoneMode.CAMERA -> Style.AT_POINT
        FreeZoneMode.TOUCHPAD -> Style.TOUCHPAD
        FreeZoneMode.NONE, FreeZoneMode.DIRECT -> Style.DIRECT
    }

    /** Stan przełącznika: zapisany wybór albo domyślny z profilu. */
    @JvmStatic
    fun effective(p: Profile, stored: Boolean?): Boolean = stored ?: isEnabled(p)

    /**
     * Co zapisać po przełączeniu na [enabled]: `null` (usuń wpis), gdy to wartość domyślna profilu. Dzięki temu
     * profil bez wpisu zawsze działa tak, jak go zapisano (także po zmianie presetu w nowej wersji aplikacji).
     */
    @JvmStatic
    fun toStore(p: Profile, enabled: Boolean): Boolean? = if (enabled == isEnabled(p)) null else enabled

    /** Profil efektywny. [enabled] = null albo równe [isEnabled]: ten sam obiekt bez zmian. */
    @JvmStatic
    fun apply(p: Profile, enabled: Boolean?): Profile {
        if (enabled == null || enabled == isEnabled(p)) return p
        val result = if (enabled) enable(p) else disable(p)
        return if (ProfileValidator.validate(result).isEmpty()) result else p
    }

    private fun enable(p: Profile): Profile {
        val g = p.gestures
        val tap = g.tapTarget.copy(enabled = true)
        val longPress = g.longPress.copy(enabled = true, minMs = longPressMinMs(g.longPress.minMs, tap.maxMs))
        return when (g.freeZone) {
            // `direct` nie używa long-pressu; `enabled` zostaje jak w pliku, a próg tylko spełnia regułę walidatora.
            FreeZoneMode.NONE -> p.copy(
                gestures = g.copy(
                    freeZone = FreeZoneMode.DIRECT,
                    tapTarget = tap,
                    longPress = if (g.longPress.enabled) {
                        g.longPress.copy(minMs = longPressMinMs(g.longPress.minMs, tap.maxMs))
                    } else {
                        g.longPress
                    },
                ),
            )
            FreeZoneMode.CAMERA -> p.copy(
                gestures = g.copy(tapTarget = tap, longPress = longPress, pinchZoom = g.pinchZoom.copy(enabled = true)),
            )
            FreeZoneMode.TOUCHPAD, FreeZoneMode.DIRECT -> p
        }
    }

    private fun disable(p: Profile): Profile {
        val g = p.gestures
        return when (g.freeZone) {
            FreeZoneMode.TOUCHPAD, FreeZoneMode.DIRECT -> p.copy(gestures = g.copy(freeZone = FreeZoneMode.NONE))
            FreeZoneMode.CAMERA -> p.copy(
                gestures = g.copy(
                    tapTarget = g.tapTarget.copy(enabled = false),
                    longPress = g.longPress.copy(enabled = false),
                    pinchZoom = g.pinchZoom.copy(enabled = false),
                ),
            )
            FreeZoneMode.NONE -> p
        }
    }

    /**
     * Tap i long-press muszą się wykluczać (walidator: `minMs > tapTarget.maxMs`). Profil z wyłączonymi gestami
     * mógł mieć dowolny `minMs`; wtedy bierzemy domyślne 450 ms albo tuż ponad `maxMs` tapu.
     */
    private fun longPressMinMs(minMs: Int, tapMaxMs: Int): Int =
        if (minMs > tapMaxMs) minMs else maxOf(LongPress().minMs, tapMaxMs + 1)
}
