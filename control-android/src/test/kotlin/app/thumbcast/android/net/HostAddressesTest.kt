package app.thumbcast.android.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adresy PC: LAN + Tailscale/ZeroTier w jednym wpisie (HANDOFF 4f). */
class HostAddressesTest {

    private val p = HostAddresses.DEFAULT_HTTP_PORT
    private val lan = Endpoint("192.168.1.10", p)
    private val lanNew = Endpoint("192.168.1.11", p)
    private val tailscale = Endpoint("100.101.102.103", p)
    private val zerotier = Endpoint("10.147.17.5", p)
    private val stun = Endpoint("203.0.113.7", p)
    private val v6 = Endpoint("2001:db8::10", p)

    // ------------------------------------------------------------ klasyfikacja

    @Test
    fun classifiesIpv4Ranges() {
        assertEquals(AddressKind.LAN, HostAddresses.classify("192.168.0.1"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("10.0.0.5"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("172.16.0.1"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("172.31.255.255"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("172.32.0.1"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("169.254.3.4"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("100.64.0.1"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("100.127.255.254"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("100.128.0.1"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("100.63.255.255"))
        assertEquals(AddressKind.LOOPBACK, HostAddresses.classify("127.0.0.1"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("8.8.8.8"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("192.168.1.300"))
    }

    @Test
    fun classifiesIpv6AndNames() {
        assertEquals(AddressKind.LOOPBACK, HostAddresses.classify("::1"))
        assertEquals(AddressKind.LOOPBACK, HostAddresses.classify("[::1]"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("fd7a:115c:a1e0::1234:5678"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("fd12:3456::1"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("fe80::1%wlan0"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("2001:db8::1"))
        assertEquals(AddressKind.LAN, HostAddresses.classify("::ffff:192.168.1.2"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("::ffff:100.100.1.1"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("gaming-pc.tail1234.ts.net"))
        assertEquals(AddressKind.VPN, HostAddresses.classify("gaming-pc.tail1234.ts.net."))
        assertEquals(AddressKind.LAN, HostAddresses.classify("gaming-pc.local"))
        assertEquals(AddressKind.LOOPBACK, HostAddresses.classify("localhost"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("gaming-pc"))
        assertEquals(AddressKind.OTHER, HostAddresses.classify("1:2:3"))
        assertEquals(AddressGroup.LAN, HostAddresses.groupOf("10.1.2.3"))
        assertEquals(AddressGroup.ALTERNATIVE, HostAddresses.groupOf("100.80.1.1"))
        assertEquals(AddressGroup.ALTERNATIVE, HostAddresses.groupOf("example.org"))
    }

    // ------------------------------------------------------------ parsowanie

    @Test
    fun parsesUserInput() {
        assertEquals(Endpoint("100.80.1.2", p), HostAddresses.parseEndpoint(" 100.80.1.2 "))
        assertEquals(Endpoint("100.80.1.2", 48000), HostAddresses.parseEndpoint("100.80.1.2:48000"))
        assertEquals(Endpoint("pc.tail1.ts.net", p), HostAddresses.parseEndpoint("pc.tail1.ts.net"))
        assertEquals(Endpoint("fd7a:115c:a1e0::1", p), HostAddresses.parseEndpoint("fd7a:115c:a1e0::1"))
        assertEquals(Endpoint("fd7a:115c:a1e0::1", 47990), HostAddresses.parseEndpoint("[fd7a:115c:a1e0::1]:47990"))
        assertEquals(Endpoint("::1", p), HostAddresses.parseEndpoint("[::1]"))
        assertNull(HostAddresses.parseEndpoint(""))
        assertNull(HostAddresses.parseEndpoint(null))
        assertNull(HostAddresses.parseEndpoint("my pc"))
        assertNull(HostAddresses.parseEndpoint("http://pc"))
        assertNull(HostAddresses.parseEndpoint("pc:0"))
        assertNull(HostAddresses.parseEndpoint("pc:70000"))
        assertNull(HostAddresses.parseEndpoint("[::1"))
        assertNull(HostAddresses.parseEndpoint("[zz::1]"))
        assertNull(HostAddresses.parseEndpoint("1:2:3"))
    }

    // ------------------------------------------------------------ odpowiedź przez VPN nie kasuje LAN

    @Test
    fun replyOverTailscaleDoesNotOverwriteLan() {
        // Zgłoszenie z problemu: PC sparowany w LAN, dodany potem adres Tailscale.
        val book = AddressBook(local = lan, manual = listOf(tailscale), remote = stun)
        // Sunshine zwraca LocalIP = adres, na który przyszło zapytanie, czyli 100.x.
        val after = HostAddresses.applyPollReply(book, reportedLocal = tailscale, via = tailscale)
        assertEquals(lan, after.local)
        assertEquals(listOf(tailscale), after.manual)
    }

    @Test
    fun replyOverZeroTierManualAddressDoesNotOverwriteLan() {
        // ZeroTier często daje prywatne 10.x/172.x, więc klasa LAN; chroni go to, że jest ręczny.
        val book = AddressBook(local = lan, manual = listOf(zerotier))
        val after = HostAddresses.applyPollReply(book, reportedLocal = zerotier, via = zerotier)
        assertEquals(lan, after.local)
    }

    @Test
    fun replyOverLanDoesNotDropAlternatives() {
        val book = AddressBook(local = lan, manual = listOf(tailscale))
        val after = HostAddresses.applyPollReply(book, reportedLocal = lan, via = lan)
        assertEquals(lan, after.local)
        assertEquals(listOf(tailscale), after.manual)
    }

    @Test
    fun lanReplyLearnsNewDhcpAddress() {
        val book = AddressBook(local = lan, manual = listOf(tailscale))
        // Odkrycie przez mDNS pod nowym adresem (DHCP) daje wpis z nowym slotem LAN.
        val merged = HostAddresses.merge(book, AddressBook(local = lanNew))
        assertEquals(lanNew, merged.local)
        assertEquals(listOf(tailscale), merged.manual)
        // Odpowiedź przez adres publiczny (przekierowanie portów) zgłasza adres LAN za NAT-em.
        val viaPublic = HostAddresses.applyPollReply(book.copy(remote = stun), reportedLocal = lanNew, via = stun)
        assertEquals(lanNew, viaPublic.local)
    }

    @Test
    fun manualLanAddressFillsEmptySlot() {
        val book = AddressBook(manual = listOf(lan))
        val after = HostAddresses.applyPollReply(book, reportedLocal = lan, via = lan)
        assertEquals(lan, after.local)
        assertEquals(listOf(lan), after.manual)
    }

    @Test
    fun publicReportDoesNotReplacePrivateLan() {
        val book = AddressBook(local = lan, remote = stun)
        val after = HostAddresses.applyPollReply(book, reportedLocal = stun, via = stun)
        assertEquals(lan, after.local)
        // Bez znanego adresu prywatnego zachowanie upstreamu (LAN z adresami publicznymi).
        val noLan = HostAddresses.applyPollReply(AddressBook(remote = stun), reportedLocal = stun, via = stun)
        assertEquals(stun, noLan.local)
    }

    @Test
    fun loopbackAndVpnNeverEnterLanSlot() {
        assertNull(HostAddresses.decideLocal(null, Endpoint("127.0.0.1", p), viaManual = false))
        assertNull(HostAddresses.decideLocal(null, tailscale, viaManual = false))
        assertEquals(lan, HostAddresses.decideLocal(lan, tailscale, viaManual = false))
    }

    // ------------------------------------------------------------ scalanie (ten sam uniqueid)

    @Test
    fun addingTailscaleAddressMergesIntoExistingEntry() {
        val existing = AddressBook(local = lan, manual = emptyList(), remote = stun)
        val added = AddressBook(manual = listOf(tailscale))
        val merged = HostAddresses.merge(existing, added)
        assertEquals(lan, merged.local)
        assertEquals(listOf(tailscale), merged.manual)
        assertEquals(stun, merged.remote)
    }

    @Test
    fun addingLanAgainKeepsTailscale() {
        val existing = AddressBook(local = null, manual = listOf(tailscale))
        val added = AddressBook(local = lan, manual = listOf(lan))
        val merged = HostAddresses.merge(existing, added)
        assertEquals(lan, merged.local)
        assertEquals(listOf(tailscale, lan), merged.manual)
    }

    @Test
    fun mergeIsIdempotentAndKeepsOrder() {
        val a = AddressBook(local = lan, manual = listOf(tailscale, zerotier))
        assertEquals(a, HostAddresses.merge(a, a))
        assertEquals(a, HostAddresses.merge(AddressBook(), a))
    }

    @Test
    fun mdnsVpnAddressIsRememberedAsAlternative() {
        val existing = AddressBook(local = lan)
        val fromMdns = AddressBook(local = tailscale)
        val merged = HostAddresses.merge(existing, fromMdns)
        assertEquals(lan, merged.local)
        assertEquals(listOf(tailscale), merged.manual)
    }

    // ------------------------------------------------------------ migracja

    @Test
    fun migrationMovesPollutedTailscaleOutOfLanSlot() {
        // Wpis zapisany przez starą wersję po połączeniu przez Tailscale.
        val old = AddressBook(local = tailscale, manual = listOf(tailscale), remote = stun)
        val migrated = HostAddresses.normalize(old)
        assertNull(migrated.local)
        assertEquals(listOf(tailscale), migrated.manual)
        assertEquals(stun, migrated.remote)

        val oldNoManual = AddressBook(local = tailscale, manual = listOf(lan))
        assertEquals(AddressBook(local = null, manual = listOf(lan, tailscale)), HostAddresses.normalize(oldNoManual))

        val clean = AddressBook(local = lan, manual = listOf(tailscale))
        assertEquals(clean, HostAddresses.normalize(clean))
    }

    // ------------------------------------------------------------ kolejność

    @Test
    fun pollOrderPrefersLanThenAlternatives() {
        val book = AddressBook(local = lan, manual = listOf(tailscale, zerotier), remote = stun, ipv6 = v6)
        assertEquals(listOf(lan, zerotier, tailscale, stun, v6), HostAddresses.pollOrder(book))
    }

    @Test
    fun pollOrderPutsManualLanBeforeVpnEvenIfAddedLater() {
        val book = AddressBook(manual = listOf(tailscale, lan))
        assertEquals(listOf(lan, tailscale), HostAddresses.pollOrder(book))
    }

    @Test
    fun pollOrderDeduplicates() {
        val book = AddressBook(local = lan, manual = listOf(lan, tailscale), remote = tailscale)
        assertEquals(listOf(lan, tailscale), HostAddresses.pollOrder(book))
    }

    @Test
    fun pollOrderOfPollutedEntryPutsVpnLast() {
        val book = AddressBook(local = tailscale, manual = listOf(lan))
        assertEquals(listOf(lan, tailscale), HostAddresses.pollOrder(book))
    }

    // ------------------------------------------------------------ edycja w UI

    @Test
    fun entriesMarkActiveAndSource() {
        val book = AddressBook(local = lan, manual = listOf(tailscale), remote = stun)
        val e = HostAddresses.entries(book, tailscale)
        assertEquals(3, e.size)
        assertEquals(AddressEntry(lan, AddressGroup.LAN, AddressSource.LOCAL, false), e[0])
        assertEquals(AddressEntry(tailscale, AddressGroup.ALTERNATIVE, AddressSource.MANUAL, true), e[1])
        assertEquals(AddressEntry(stun, AddressGroup.ALTERNATIVE, AddressSource.REMOTE, false), e[2])
    }

    @Test
    fun addAndRemove() {
        var book = AddressBook(local = lan)
        book = HostAddresses.addManual(book, tailscale)
        assertEquals(listOf(tailscale), book.manual)
        // Duplikat (także adres LAN) nic nie zmienia.
        assertEquals(book, HostAddresses.addManual(book, tailscale))
        assertEquals(book, HostAddresses.addManual(book, lan))
        book = HostAddresses.remove(book, tailscale)
        assertTrue(book.manual.isEmpty())
        assertEquals(lan, book.local)
        book = HostAddresses.remove(AddressBook(local = lan, manual = listOf(lan), remote = stun), lan)
        assertEquals(AddressBook(remote = stun), book)
    }
}
