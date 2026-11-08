package app.thumbcast.android.overlay

/**
 * Bufor kołowy opóźnień `event.eventTime -> send` (ms) dla nakładki debug.
 * Kryterium E1 z planu: p95 < 4 ms. Czysty JVM, testowalny.
 */
class LatencyStats(private val capacity: Int = 256) {

    private val samples: LongArray = LongArray(capacity)
    private var count: Int = 0
    private var next: Int = 0

    var last: Long = -1L
        private set

    fun add(ms: Long) {
        val v: Long = if (ms < 0L) 0L else ms
        samples[next] = v
        next = (next + 1) % capacity
        if (count < capacity) count++
        last = v
    }

    val size: Int
        get() = count

    /** Percentyl (0..100) metodą „nearest rank”; -1, gdy brak próbek. */
    fun percentile(p: Int): Long {
        if (count == 0) return -1L
        val copy: LongArray = samples.copyOf(count)
        copy.sort()
        var rank: Int = Math.ceil(p / 100.0 * count).toInt()
        if (rank < 1) rank = 1
        if (rank > count) rank = count
        return copy[rank - 1]
    }

    fun clear() {
        count = 0
        next = 0
        last = -1L
    }
}
