package app.thumbcast.android.net

/**
 * Adresy jednego sparowanego PC: LAN i alternatywne (Tailscale, ZeroTier, publiczne, IPv6).
 *
 * Czysta logika bez Androida i bez DNS (testy JVM). `:app` mapuje ją na pola `ComputerDetails`
 * (`localAddress`, `manualAddress` + `extraAddresses`, `remoteAddress`, `ipv6Address`), patrz
 * `patches/host-addresses.patch.md`.
 *
 * Problem z upstreamu: Sunshine wpisuje do `LocalIP` w `serverinfo` adres, na który przyszło zapytanie
 * (lokalny koniec gniazda). Zapytanie przez Tailscale zwraca więc `100.x.y.z`, a upstream nadpisywał
 * nim zapamiętany adres LAN (`ComputerDetails.update`). Tu adres LAN zmienia się tylko według reguł
 * z [decideLocal], a adresy ręczne się sumują zamiast nadpisywać.
 */
data class Endpoint(val host: String, val port: Int) {
    override fun toString(): String = if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

/** Klasa adresu z samego zapisu (bez rozwiązywania nazw). */
enum class AddressKind {
    /** 127.0.0.0/8, ::1, `localhost`. */
    LOOPBACK,

    /** RFC 1918, 169.254.0.0/16, IPv6 ULA (poza Tailscale) i link-local, nazwy `.local`. */
    LAN,

    /** 100.64.0.0/10 (CGNAT, Tailscale), `fd7a:115c:a1e0::/48` (Tailscale IPv6), nazwy `.ts.net`. */
    VPN,

    /** Adres publiczny albo dowolna inna nazwa hosta (np. MagicDNS bez sufiksu, DNS w LAN). */
    OTHER,
}

/** Grupa w kolejności łączenia: najpierw LAN, potem alternatywne. */
enum class AddressGroup { LAN, ALTERNATIVE }

/** Skąd adres jest w książce (do opisu w UI). */
enum class AddressSource {
    /** Adres LAN zgłoszony przez host (`LocalIP`) albo znaleziony przez mDNS. */
    LOCAL,

    /** Dodany ręcznie (albo zapamiętany adres VPN z mDNS). */
    MANUAL,

    /** Publiczny adres z STUN albo `ExternalIP`. */
    REMOTE,

    /** Globalny IPv6 z mDNS. */
    IPV6,
}

/**
 * Stan adresów PC. [manual] to lista w kolejności dodania; pierwszy element trafia do upstreamowego
 * `manualAddress`, reszta do `extraAddresses`.
 */
data class AddressBook(
    val local: Endpoint? = null,
    val manual: List<Endpoint> = emptyList(),
    val remote: Endpoint? = null,
    val ipv6: Endpoint? = null,
)

/** Wiersz listy adresów w dialogu „Adresy połączenia”. */
data class AddressEntry(
    val endpoint: Endpoint,
    val group: AddressGroup,
    val source: AddressSource,
    val active: Boolean,
)

object HostAddresses {
    const val DEFAULT_HTTP_PORT = 47989

    // ---------------------------------------------------------------- klasyfikacja

    @JvmStatic
    fun classify(host: String): AddressKind {
        val h = normalizeHost(host)
        if (h.isEmpty()) return AddressKind.OTHER
        parseIpv4(h)?.let { return classifyIpv4(it) }
        if (h.contains(':')) {
            val v6 = parseIpv6(h) ?: return AddressKind.OTHER
            return classifyIpv6(v6)
        }
        return when {
            h == "localhost" -> AddressKind.LOOPBACK
            h.endsWith(".local") -> AddressKind.LAN
            h.endsWith(".ts.net") -> AddressKind.VPN
            else -> AddressKind.OTHER
        }
    }

    @JvmStatic
    fun groupOf(host: String): AddressGroup = when (classify(host)) {
        AddressKind.LAN, AddressKind.LOOPBACK -> AddressGroup.LAN
        AddressKind.VPN, AddressKind.OTHER -> AddressGroup.ALTERNATIVE
    }

    private fun normalizeHost(host: String): String {
        var h = host.trim().lowercase()
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        val zone = h.indexOf('%')
        if (zone >= 0) h = h.substring(0, zone)
        if (h.endsWith(".")) h = h.substring(0, h.length - 1)
        return h
    }

    private fun classifyIpv4(a: IntArray): AddressKind = when {
        a[0] == 127 -> AddressKind.LOOPBACK
        a[0] == 10 -> AddressKind.LAN
        a[0] == 172 && a[1] in 16..31 -> AddressKind.LAN
        a[0] == 192 && a[1] == 168 -> AddressKind.LAN
        a[0] == 169 && a[1] == 254 -> AddressKind.LAN
        a[0] == 100 && a[1] in 64..127 -> AddressKind.VPN
        else -> AddressKind.OTHER
    }

    private fun classifyIpv6(b: IntArray): AddressKind {
        // ::1
        if ((0 until 15).all { b[it] == 0 } && b[15] == 1) return AddressKind.LOOPBACK
        // ::ffff:a.b.c.d (IPv4 w IPv6)
        if ((0 until 10).all { b[it] == 0 } && b[10] == 0xff && b[11] == 0xff) {
            return classifyIpv4(intArrayOf(b[12], b[13], b[14], b[15]))
        }
        // Tailscale: fd7a:115c:a1e0::/48
        if (b[0] == 0xfd && b[1] == 0x7a && b[2] == 0x11 && b[3] == 0x5c && b[4] == 0xa1 && b[5] == 0xe0) {
            return AddressKind.VPN
        }
        // ULA fc00::/7
        if ((b[0] and 0xfe) == 0xfc) return AddressKind.LAN
        // link-local fe80::/10
        if (b[0] == 0xfe && (b[1] and 0xc0) == 0x80) return AddressKind.LAN
        return AddressKind.OTHER
    }

    private fun parseIpv4(s: String): IntArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0 until 4) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v
        }
        return out
    }

    /** Zwraca 16 bajtów albo null. Obsługuje `::` i końcówkę IPv4. */
    private fun parseIpv6(s: String): IntArray? {
        if (s.count { it == ':' } < 2) return null
        val doubleColon = s.indexOf("::")
        if (doubleColon >= 0 && s.indexOf("::", doubleColon + 1) >= 0) return null
        val head: List<String>
        val tail: List<String>
        if (doubleColon >= 0) {
            val left = s.substring(0, doubleColon)
            val right = s.substring(doubleColon + 2)
            head = if (left.isEmpty()) emptyList() else left.split(':')
            tail = if (right.isEmpty()) emptyList() else right.split(':')
        } else {
            head = s.split(':')
            tail = emptyList()
        }
        val headBytes = groupsToBytes(head, allowV4Tail = tail.isEmpty()) ?: return null
        val tailBytes = groupsToBytes(tail, allowV4Tail = true) ?: return null
        val total = headBytes.size + tailBytes.size
        if (doubleColon >= 0) {
            if (total > 14) return null
        } else if (total != 16) {
            return null
        }
        val out = IntArray(16)
        headBytes.forEachIndexed { i, v -> out[i] = v }
        tailBytes.forEachIndexed { i, v -> out[16 - tailBytes.size + i] = v }
        return out
    }

    private fun groupsToBytes(groups: List<String>, allowV4Tail: Boolean): List<Int>? {
        val out = ArrayList<Int>()
        groups.forEachIndexed { i, g ->
            if (i == groups.size - 1 && allowV4Tail && g.contains('.')) {
                val v4 = parseIpv4(g) ?: return null
                v4.forEach { out.add(it) }
                return out
            }
            if (g.isEmpty() || g.length > 4 || !g.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val v = g.toInt(16)
            out.add(v shr 8)
            out.add(v and 0xff)
        }
        return out
    }

    // ---------------------------------------------------------------- parsowanie wpisu użytkownika

    /**
     * `host`, `host:port`, `[v6]`, `[v6]:port` albo goły IPv6. Null przy złym wpisie.
     * Nie rozwiązuje nazw.
     */
    @JvmStatic
    @JvmOverloads
    fun parseEndpoint(input: String?, defaultPort: Int = DEFAULT_HTTP_PORT): Endpoint? {
        val s = input?.trim() ?: return null
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        val host: String
        var port = defaultPort
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            host = s.substring(1, end)
            val rest = s.substring(end + 1)
            if (rest.isNotEmpty()) {
                if (!rest.startsWith(":")) return null
                port = parsePort(rest.substring(1)) ?: return null
            }
            if (parseIpv6(normalizeHost(host)) == null) return null
        } else {
            val colons = s.count { it == ':' }
            when {
                colons == 0 -> host = s
                colons == 1 -> {
                    host = s.substringBefore(':')
                    port = parsePort(s.substringAfter(':')) ?: return null
                }
                else -> {
                    host = s
                    if (parseIpv6(normalizeHost(host)) == null) return null
                }
            }
        }
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it in ".-_:%" }) return null
        if (host.startsWith(".") || host.startsWith("-")) return null
        return Endpoint(host, port)
    }

    private fun parsePort(s: String): Int? {
        if (s.isEmpty() || s.length > 5 || !s.all { it in '0'..'9' }) return null
        val p = s.toInt()
        return if (p in 1..65535) p else null
    }

    // ---------------------------------------------------------------- reguły

    /**
     * Adres VPN nigdy nie siedzi w slocie LAN: przenosimy go na koniec listy alternatywnych.
     * Naprawia też wpisy zapisane przez starszą wersję (migracja przy odczycie z bazy).
     */
    @JvmStatic
    fun normalize(book: AddressBook): AddressBook {
        val local = book.local ?: return book
        if (classify(local.host) != AddressKind.VPN) return book
        val manual = if (local in book.manual) book.manual else book.manual + local
        return book.copy(local = null, manual = manual)
    }

    /**
     * Nowa wartość slotu LAN, gdy host zgłosił [reported] (`LocalIP`).
     *
     * - Loopback i VPN nigdy nie trafiają do slotu LAN.
     * - Odpowiedź przez adres ręczny (VPN, ZeroTier, nazwa) nie zmienia znanego adresu LAN; wypełnia
     *   tylko pusty slot adresem prywatnym (RFC 1918).
     * - W pozostałych przypadkach (odpowiedź przez adres LAN, mDNS, publiczny, IPv6) zachowanie upstreamu:
     *   zgłoszony adres prywatny zastępuje poprzedni (np. zmiana DHCP), ale adres publiczny nie zastępuje
     *   znanego adresu prywatnego.
     */
    @JvmStatic
    fun decideLocal(current: Endpoint?, reported: Endpoint?, viaManual: Boolean): Endpoint? {
        if (reported == null) return current
        val kind = classify(reported.host)
        if (kind == AddressKind.LOOPBACK || kind == AddressKind.VPN) return current
        if (viaManual) {
            return current ?: if (kind == AddressKind.LAN) reported else null
        }
        if (current == null) return reported
        if (kind == AddressKind.LAN) return reported
        if (classify(current.host) == AddressKind.LAN) return current
        return reported
    }

    /**
     * Wynik odpytania `serverinfo`: [reportedLocal] to `LocalIP` (z portem zapytania), [via] to adres,
     * który odpowiedział. Zmienia tylko slot LAN; lista ręczna zostaje.
     */
    @JvmStatic
    fun applyPollReply(book: AddressBook, reportedLocal: Endpoint?, via: Endpoint?): AddressBook {
        val b = normalize(book)
        val viaManual = via != null && via != b.local && via in b.manual
        return b.copy(local = decideLocal(b.local, reportedLocal, viaManual))
    }

    /**
     * Łączy dwa opisy tego samego PC (ten sam uniqueid): wpis z bazy z wpisem z odpytania, istniejący
     * wpis z nowo dodanym adresem. Lista ręczna to suma (najpierw [existing]); slot LAN wg [decideLocal].
     * `remote` i `ipv6` zostają z [existing] (`:app` łączy je po swojemu, jak upstream).
     */
    @JvmStatic
    fun merge(existing: AddressBook, incoming: AddressBook): AddressBook {
        val e = normalize(existing)
        val i = normalize(incoming)
        val manual = ArrayList(e.manual)
        for (m in i.manual) if (m !in manual) manual.add(m)
        val local = decideLocal(e.local, i.local, viaManual = false)
        return e.copy(local = local, manual = manual)
    }

    /**
     * Kolejność odpytywania i łączenia: najpierw grupa LAN, potem alternatywne; w grupie kolejność
     * upstreamu (local, ręczne w kolejności dodania, remote, ipv6). Bez duplikatów.
     */
    @JvmStatic
    fun pollOrder(book: AddressBook): List<Endpoint> = entries(book, null).map { it.endpoint }

    /** Wiersze do UI w kolejności łączenia. */
    @JvmStatic
    fun entries(book: AddressBook, active: Endpoint?): List<AddressEntry> {
        val b = normalize(book)
        val raw = ArrayList<Pair<Endpoint, AddressSource>>()
        b.local?.let { raw.add(it to AddressSource.LOCAL) }
        b.manual.forEach { raw.add(it to AddressSource.MANUAL) }
        b.remote?.let { raw.add(it to AddressSource.REMOTE) }
        b.ipv6?.let { raw.add(it to AddressSource.IPV6) }
        val seen = HashSet<Endpoint>()
        val unique = raw.filter { seen.add(it.first) }
        val withGroup = unique.map { (ep, src) ->
            // Slot LAN to zawsze grupa LAN (także publiczny adres w LAN bez RFC 1918, jak w upstreamie).
            val group = if (src == AddressSource.LOCAL) AddressGroup.LAN else groupOf(ep.host)
            AddressEntry(ep, group, src, ep == active)
        }
        return withGroup.filter { it.group == AddressGroup.LAN } +
            withGroup.filter { it.group == AddressGroup.ALTERNATIVE }
    }

    /** Dodaje adres alternatywny (ręczny). Bez zmian, jeśli adres już jest w książce. */
    @JvmStatic
    fun addManual(book: AddressBook, endpoint: Endpoint): AddressBook {
        val b = normalize(book)
        if (endpoint == b.local || endpoint in b.manual || endpoint == b.remote || endpoint == b.ipv6) return b
        return b.copy(manual = b.manual + endpoint)
    }

    /** Usuwa adres ze wszystkich slotów. Adres LAN zgłoszony przez host wróci po odpytaniu przez LAN. */
    @JvmStatic
    fun remove(book: AddressBook, endpoint: Endpoint): AddressBook {
        val b = normalize(book)
        return AddressBook(
            local = b.local?.takeIf { it != endpoint },
            manual = b.manual.filter { it != endpoint },
            remote = b.remote?.takeIf { it != endpoint },
            ipv6 = b.ipv6?.takeIf { it != endpoint },
        )
    }
}
