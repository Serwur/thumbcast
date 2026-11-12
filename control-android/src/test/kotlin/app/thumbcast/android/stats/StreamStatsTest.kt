package app.thumbcast.android.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Liczenie próbki z liczników upstreamu, szacowane opóźnienie i teksty nakładki. */
class StreamStatsTest {

    private fun rtt(ms: Int, variance: Int): Long = (ms.toLong() shl 32) or variance.toLong()

    /** 120 klatek w oknie, 2 utracone, dekodowanie 5 ms/klatkę, klient 7 ms/klatkę, host 4,8 ms śr. */
    private fun sample(rttInfo: Long = rtt(12, 3), hostFrames: Int = 120, framesReceived: Int = 120): StreamStats =
        StreamStats.fromCounters(
            width = 1920, height = 1080, codec = "HEVC", decoder = "c2.qti.hevc.decoder",
            streamFps = 60.9f, receivedFps = 59.9f, renderedFps = 59.6f,
            totalFrames = 122, framesReceived = framesReceived, framesLost = 2,
            decoderTimeMs = 5L * framesReceived, totalTimeMs = 7L * framesReceived,
            hostMinTenths = 41, hostMaxTenths = 60, hostTotalTenths = 48L * hostFrames, hostFrames = hostFrames,
            rttInfo = rttInfo,
        )

    private val labels = StatsLabels(
        basic = "%1\$d FPS · delay ~%2\$d ms",
        basicNoDelay = "%1\$d FPS · delay –",
        video = "%1\$dx%2\$d %3\$s · %4\$.1f FPS",
        decoder = "Decoder: %1\$s",
        frames = "Received %1\$.1f / shown %2\$.1f FPS",
        drops = "Network loss: %1\$.2f%%",
        network = "Network RTT: %1\$d ms (±%2\$d ms)",
        networkUnknown = "Network RTT: –",
        host = "Host min/max/avg: %1\$.1f/%2\$.1f/%3\$.1f ms",
        hostUnknown = "Host: no data",
        decode = "Decoding %1\$.2f ms · client %2\$.1f ms",
        decodeUnknown = "Decoding: –",
        breakdown = "≈ network %1\$d + host %2\$.1f + client %3\$.1f ms",
    )

    private val formatter = StatsFormatter(labels, Locale.US)

    @Test
    fun countersAreAveragedPerReceivedFrame() {
        val s = sample()
        assertEquals(12, s.rttMs)
        assertEquals(3, s.rttVarianceMs)
        assertEquals(5f, s.decodeTimeMs!!, 1e-4f)
        assertEquals(7f, s.clientTimeMs!!, 1e-4f)
        assertEquals(4.1f, s.hostLatencyMinMs!!, 1e-4f)
        assertEquals(6.0f, s.hostLatencyMaxMs!!, 1e-4f)
        assertEquals(4.8f, s.hostLatencyAvgMs!!, 1e-4f)
        assertEquals(2 * 100f / 122, s.networkDropPercent, 1e-4f)
    }

    @Test
    fun latencyIsRttPlusHostPlusClient() {
        // 12 + 4,8 + 7 = 23,8 -> 24
        assertEquals(24, sample().latencyEstimateMs)
    }

    @Test
    fun latencyWithoutHostDataCountsHostAsZero() {
        val s = sample(hostFrames = 0)
        assertNull(s.hostLatencyAvgMs)
        assertEquals(19, s.latencyEstimateMs)
    }

    @Test
    fun latencyUnknownWithoutRttOrFrames() {
        assertNull(sample(rttInfo = -1L).rttMs)
        assertNull(sample(rttInfo = -1L).latencyEstimateMs)
        val noFrames = sample(framesReceived = 0)
        assertNull(noFrames.clientTimeMs)
        assertNull(noFrames.decodeTimeMs)
        assertNull(noFrames.latencyEstimateMs)
    }

    @Test
    fun largeRttDoesNotGoNegative() {
        val s = sample(rttInfo = rtt(0x7FFF_FFFF, 0x7FFF_FFFF))
        assertEquals(Int.MAX_VALUE, s.rttMs)
        assertEquals(Int.MAX_VALUE, s.rttVarianceMs)
    }

    @Test
    fun nanFpsBecomesZero() {
        val s = StreamStats.fromCounters(
            1280, 720, "H.264", "dec", Float.NaN, Float.POSITIVE_INFINITY, -1f,
            0, 0, 0, 0L, 0L, 0, 0, 0L, 0, -1L,
        )
        assertEquals(0f, s.streamFps)
        assertEquals(0f, s.receivedFps)
        assertEquals(0f, s.renderedFps)
        assertEquals(0f, s.networkDropPercent)
        assertEquals("0 FPS · delay –", formatter.basic(s))
    }

    @Test
    fun basicLineIsOneCompactLine() {
        assertEquals("60 FPS · delay ~24 ms", formatter.basic(sample()))
        assertEquals("60 FPS · delay –", formatter.basic(sample(rttInfo = -1L)))
    }

    @Test
    fun advancedListsDetailsAndBreakdown() {
        val expected = listOf(
            "60 FPS · delay ~24 ms",
            "1920x1080 HEVC · 60.9 FPS",
            "Decoder: c2.qti.hevc.decoder",
            "Received 59.9 / shown 59.6 FPS",
            "Network loss: 1.64%",
            "Network RTT: 12 ms (±3 ms)",
            "Host min/max/avg: 4.1/6.0/4.8 ms",
            "Decoding 5.00 ms · client 7.0 ms",
            "≈ network 12 + host 4.8 + client 7.0 ms",
        ).joinToString("\n")
        assertEquals(expected, formatter.advanced(sample()))
    }

    @Test
    fun advancedWithMissingDataUsesPlaceholdersAndNoBreakdown() {
        val text = formatter.advanced(sample(rttInfo = -1L, hostFrames = 0, framesReceived = 0))
        val lines = text.split("\n")
        assertEquals("Network RTT: –", lines[5])
        assertEquals("Host: no data", lines[6])
        assertEquals("Decoding: –", lines[7])
        assertEquals(8, lines.size)
    }

    @Test
    fun localeControlsDecimalSeparator() {
        val pl = StatsFormatter(labels, Locale.forLanguageTag("pl-PL"))
        assertEquals("Received 59,9 / shown 59,6 FPS", pl.advanced(sample()).split("\n")[3])
    }

    @Test
    fun formatFollowsMode() {
        val s = sample()
        assertEquals("", formatter.format(StatsMode.OFF, s))
        assertEquals(formatter.basic(s), formatter.format(StatsMode.BASIC, s))
        assertEquals(formatter.advanced(s), formatter.format(StatsMode.ADVANCED, s))
    }
}
