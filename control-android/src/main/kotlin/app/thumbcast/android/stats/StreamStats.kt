package app.thumbcast.android.stats

/**
 * Jedna próbka statystyk strumienia (okno ok. 2 s: poprzednia i bieżąca sekunda, jak upstreamowa nakładka
 * wydajności). Liczby pochodzą z liczników upstreamu (`MediaCodecDecoderRenderer`, `VideoStats`,
 * `MoonBridge.getEstimatedRttInfo`); ta klasa tylko je dzieli i nazywa, niczego sama nie mierzy.
 *
 * Wartości null = brak pomiaru (np. brak odebranych klatek, host nie podaje czasu przetwarzania, RTT jeszcze
 * nieznane). Czysty JVM: testy w `StreamStatsTest`.
 */
data class StreamStats(
    val width: Int,
    val height: Int,
    /** Kodek, np. „HEVC”, „H.264 HDR”. */
    val codec: String,
    /** Nazwa dekodera MediaCodec, np. „c2.qti.hevc.decoder”. */
    val decoder: String,
    /** Klatki strumienia na sekundę (odebrane + utracone w sieci), upstream `totalFps`. */
    val streamFps: Float,
    /** Klatki odebrane z sieci na sekundę. */
    val receivedFps: Float,
    /** Klatki wyświetlone na sekundę (to widzi gracz). */
    val renderedFps: Float,
    /** Procent klatek utraconych w sieci. */
    val networkDropPercent: Float,
    /** Średni czas obiegu sieci (RTT) połączenia sterującego w ms. */
    val rttMs: Int?,
    /** Wariancja RTT w ms. */
    val rttVarianceMs: Int?,
    /** Czas przetwarzania klatki na hoście (przechwycenie → zakodowanie), zgłaszany przez Sunshine. */
    val hostLatencyMinMs: Float?,
    val hostLatencyMaxMs: Float?,
    val hostLatencyAvgMs: Float?,
    /** Średni czas dekodowania: klatka przekazana do dekodera → klatka zdekodowana (upstream „decoding time”). */
    val decodeTimeMs: Float?,
    /**
     * Średni czas po stronie telefonu: pierwszy pakiet klatki odebrany → klatka zdekodowana (składanie klatki
     * + kolejka + dekodowanie; upstream „end-to-end client latency” z komunikatu po zakończeniu strumienia).
     * Zawiera [decodeTimeMs].
     */
    val clientTimeMs: Float?,
) {

    /**
     * Szacowane opóźnienie w ms = RTT sieci + średni czas hosta + średni czas klienta, zaokrąglone; null, gdy
     * RTT albo czas klienta nieznany. RTT, bo wejście idzie z telefonu do PC, a obraz wraca z PC do telefonu
     * (po jednej drodze w każdą stronę). Czas hosta, gdy go brak (inny host niż Sunshine), liczy się jako 0.
     *
     * Nie obejmuje: czasu renderowania klatki przez grę i czekania na przechwycenie, kolejki wyświetlania
     * i odświeżania ekranu telefonu ani próbkowania dotyku. Prawdziwe opóźnienie jest więc większe, stąd „~”.
     */
    val latencyEstimateMs: Int?
        get() {
            val rtt: Int = rttMs ?: return null
            val client: Float = clientTimeMs ?: return null
            return Math.round(rtt + (hostLatencyAvgMs ?: 0f) + client)
        }

    companion object {

        /**
         * Próbka z surowych liczników upstreamu (suma dwóch okien `VideoStats`).
         *
         * @param hostMinTenths, hostMaxTenths, hostTotalTenths czasy hosta w dziesiątych częściach ms (jak w
         *   `VideoStats`); [hostFrames] = liczba klatek, dla których host podał czas (0 = brak danych).
         * @param rttInfo `MoonBridge.getEstimatedRttInfo()`: RTT w górnych 32 bitach, wariancja w dolnych,
         *   -1 = brak pomiaru.
         */
        @JvmStatic
        fun fromCounters(
            width: Int,
            height: Int,
            codec: String,
            decoder: String,
            streamFps: Float,
            receivedFps: Float,
            renderedFps: Float,
            totalFrames: Int,
            framesReceived: Int,
            framesLost: Int,
            decoderTimeMs: Long,
            totalTimeMs: Long,
            hostMinTenths: Int,
            hostMaxTenths: Int,
            hostTotalTenths: Long,
            hostFrames: Int,
            rttInfo: Long,
        ): StreamStats {
            val hasHost: Boolean = hostFrames > 0
            val hasFrames: Boolean = framesReceived > 0
            val hasRtt: Boolean = rttInfo >= 0L
            return StreamStats(
                width = width,
                height = height,
                codec = codec,
                decoder = decoder,
                streamFps = finite(streamFps),
                receivedFps = finite(receivedFps),
                renderedFps = finite(renderedFps),
                networkDropPercent = if (totalFrames > 0) framesLost * 100f / totalFrames else 0f,
                rttMs = if (hasRtt) (rttInfo ushr 32).toInt() else null,
                rttVarianceMs = if (hasRtt) (rttInfo and 0xFFFFFFFFL).toInt() else null,
                hostLatencyMinMs = if (hasHost) hostMinTenths / 10f else null,
                hostLatencyMaxMs = if (hasHost) hostMaxTenths / 10f else null,
                hostLatencyAvgMs = if (hasHost) hostTotalTenths / 10f / hostFrames else null,
                decodeTimeMs = if (hasFrames) decoderTimeMs.toFloat() / framesReceived else null,
                clientTimeMs = if (hasFrames) totalTimeMs.toFloat() / framesReceived else null,
            )
        }

        private fun finite(v: Float): Float = if (v.isNaN() || v.isInfinite() || v < 0f) 0f else v
    }
}

/**
 * Odbiorca próbek statystyk. `MediaCodecDecoderRenderer` (blok THUMBCAST) woła go mniej więcej raz na sekundę
 * z wątku odbioru wideo, tylko gdy słuchacz jest ustawiony (nakładka włączona).
 */
fun interface StreamStatsListener {
    fun onStreamStats(stats: StreamStats)
}
