package app.thumbcast.android.stats

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Wzorce tekstów nakładki statystyk (składnia `String.format`). Aplikacja podaje je z zasobów (EN + PL),
 * testy wpisują je wprost. Numerowane argumenty jak w zasobach Androida (`%1$d`).
 */
data class StatsLabels(
    /** `%1$d` = FPS wyświetlane, `%2$d` = szacowane opóźnienie w ms. */
    val basic: String,
    /** `%1$d` = FPS wyświetlane; opóźnienie nieznane. */
    val basicNoDelay: String,
    /** `%1$d`×`%2$d`, `%3$s` = kodek, `%4$.1f` = FPS strumienia. */
    val video: String,
    /** `%1$s` = dekoder. */
    val decoder: String,
    /** `%1$.1f` = odebrane FPS, `%2$.1f` = wyświetlone FPS. */
    val frames: String,
    /** `%1$.2f` = procent klatek utraconych w sieci. */
    val drops: String,
    /** `%1$d` = RTT, `%2$d` = wariancja RTT. */
    val network: String,
    val networkUnknown: String,
    /** `%1$.1f`/`%2$.1f`/`%3$.1f` = min/maks/średni czas hosta. */
    val host: String,
    val hostUnknown: String,
    /** `%1$.2f` = dekodowanie, `%2$.1f` = czas klienta. */
    val decode: String,
    val decodeUnknown: String,
    /** `%1$d` = RTT, `%2$.1f` = host, `%3$.1f` = klient (składniki [StreamStats.latencyEstimateMs]). */
    val breakdown: String,
)

/**
 * Tekst nakładki statystyk: [basic] = jedna krótka linia („60 FPS · 23 ms”, od 12.11.2026; bez opóźnienia
 * „60 FPS”), [advanced] = ta sama
 * linia i szczegóły w kolejnych liniach (dane upstreamowej nakładki wydajności, w krótszej formie).
 */
class StatsFormatter(private val labels: StatsLabels, private val locale: Locale) {

    fun basic(s: StreamStats): String {
        val fps: Int = s.renderedFps.roundToInt()
        val latency: Int? = s.latencyEstimateMs
        return if (latency != null) fmt(labels.basic, fps, latency) else fmt(labels.basicNoDelay, fps)
    }

    fun advanced(s: StreamStats): String {
        val lines: ArrayList<String> = ArrayList(9)
        lines.add(basic(s))
        lines.add(fmt(labels.video, s.width, s.height, s.codec, s.streamFps))
        lines.add(fmt(labels.decoder, s.decoder))
        lines.add(fmt(labels.frames, s.receivedFps, s.renderedFps))
        lines.add(fmt(labels.drops, s.networkDropPercent))
        val rtt: Int? = s.rttMs
        lines.add(if (rtt != null) fmt(labels.network, rtt, s.rttVarianceMs ?: 0) else labels.networkUnknown)
        val hostAvg: Float? = s.hostLatencyAvgMs
        lines.add(
            if (hostAvg != null) fmt(labels.host, s.hostLatencyMinMs ?: 0f, s.hostLatencyMaxMs ?: 0f, hostAvg)
            else labels.hostUnknown,
        )
        val decode: Float? = s.decodeTimeMs
        val client: Float? = s.clientTimeMs
        lines.add(if (decode != null && client != null) fmt(labels.decode, decode, client) else labels.decodeUnknown)
        if (s.latencyEstimateMs != null && rtt != null && client != null) {
            lines.add(fmt(labels.breakdown, rtt, hostAvg ?: 0f, client))
        }
        return lines.joinToString("\n")
    }

    /** Tekst dla trybu nakładki; [StatsMode.OFF] = pusty. */
    fun format(mode: StatsMode, s: StreamStats): String = when (mode) {
        StatsMode.OFF -> ""
        StatsMode.BASIC -> basic(s)
        StatsMode.ADVANCED -> advanced(s)
    }

    private fun fmt(pattern: String, vararg args: Any): String = String.format(locale, pattern, *args)
}
