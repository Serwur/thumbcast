package com.limelight.thumbcast;

import com.limelight.nvstream.http.ComputerDetails;

import java.util.ArrayList;
import java.util.List;

import app.thumbcast.android.net.AddressBook;
import app.thumbcast.android.net.AddressEntry;
import app.thumbcast.android.net.Endpoint;
import app.thumbcast.android.net.HostAddresses;

/**
 * Most między polami adresów {@link ComputerDetails} a czystą logiką {@link HostAddresses}
 * (:control-android, testy JVM). Mapowanie:
 * <ul>
 *   <li>{@code localAddress} = slot LAN (nigdy adres VPN),</li>
 *   <li>{@code manualAddress} + {@code extraAddresses} = lista ręczna (alternatywne i ręczne LAN) w kolejności dodania,</li>
 *   <li>{@code remoteAddress}, {@code ipv6Address} jak w upstreamie.</li>
 * </ul>
 * Opis: patches/host-addresses.patch.md.
 */
public final class ThumbcastAddressBook {
    private ThumbcastAddressBook() {}

    private static Endpoint toEndpoint(ComputerDetails.AddressTuple t) {
        return t == null ? null : new Endpoint(t.address, t.port);
    }

    private static ComputerDetails.AddressTuple toTuple(Endpoint e) {
        return e == null ? null : new ComputerDetails.AddressTuple(e.getHost(), e.getPort());
    }

    public static AddressBook toBook(ComputerDetails d) {
        List<Endpoint> manual = new ArrayList<>();
        if (d.manualAddress != null) {
            manual.add(toEndpoint(d.manualAddress));
        }
        if (d.extraAddresses != null) {
            for (ComputerDetails.AddressTuple t : d.extraAddresses) {
                Endpoint e = toEndpoint(t);
                if (e != null && !manual.contains(e)) {
                    manual.add(e);
                }
            }
        }
        return new AddressBook(toEndpoint(d.localAddress), manual,
                toEndpoint(d.remoteAddress), toEndpoint(d.ipv6Address));
    }

    /** Zapisuje slot LAN i listę ręczną (remote i ipv6 zostają bez zmian). */
    private static void writeLocalAndManual(ComputerDetails d, AddressBook b) {
        ComputerDetails.AddressTuple local = toTuple(b.getLocal());
        List<ComputerDetails.AddressTuple> manual = new ArrayList<>();
        for (Endpoint e : b.getManual()) {
            manual.add(toTuple(e));
        }
        d.localAddress = local;
        d.manualAddress = manual.isEmpty() ? null : manual.get(0);
        d.extraAddresses = manual.size() > 1 ? new ArrayList<>(manual.subList(1, manual.size())) : new ArrayList<>();
    }

    /** Pełny zapis (także remote i ipv6), np. po usunięciu adresu w dialogu. */
    private static void writeAll(ComputerDetails d, AddressBook b) {
        ComputerDetails.AddressTuple remote = toTuple(b.getRemote());
        ComputerDetails.AddressTuple ipv6 = toTuple(b.getIpv6());
        writeLocalAndManual(d, b);
        d.remoteAddress = remote;
        d.ipv6Address = ipv6;
    }

    /** Migracja wpisu z bazy (adres VPN zapisany przez starszą wersję w slocie LAN). */
    public static void migrate(ComputerDetails d) {
        writeLocalAndManual(d, HostAddresses.normalize(toBook(d)));
    }

    /**
     * Wywoływane w {@code ComputerDetails.update()} zamiast nadpisywania localAddress i manualAddress.
     */
    public static void mergeInto(ComputerDetails target, ComputerDetails incoming) {
        AddressBook incomingBook = toBook(incoming);
        // Upstream: loopback IPv4 z GS IPv6 Forwarder nie jest adresem LAN (decideLocal też go odrzuca).
        writeLocalAndManual(target, HostAddresses.merge(toBook(target), incomingBook));
    }

    /**
     * Przed {@code details.update(polled)}: decyduje o slocie LAN na podstawie {@code LocalIP} i adresu,
     * który odpowiedział. Ustawia {@code polled.localAddress} na wynik, żeby update() go nie nadpisał.
     */
    public static void applyPollReply(ComputerDetails details, ComputerDetails polled) {
        AddressBook after = HostAddresses.applyPollReply(toBook(details),
                toEndpoint(polled.localAddress), toEndpoint(polled.activeAddress));
        writeLocalAndManual(details, after);
        polled.localAddress = details.localAddress;
    }

    /** Kolejność odpytywania: LAN, potem alternatywne. */
    public static List<ComputerDetails.AddressTuple> pollOrder(ComputerDetails d) {
        List<ComputerDetails.AddressTuple> out = new ArrayList<>();
        for (Endpoint e : HostAddresses.pollOrder(toBook(d))) {
            out.add(toTuple(e));
        }
        return out;
    }

    public static List<AddressEntry> entries(ComputerDetails d) {
        return HostAddresses.entries(toBook(d), toEndpoint(d.activeAddress));
    }

    /** @return true, jeśli adres został dodany (false: już był). */
    public static boolean addManual(ComputerDetails d, ComputerDetails.AddressTuple t) {
        AddressBook before = HostAddresses.normalize(toBook(d));
        AddressBook after = HostAddresses.addManual(before, toEndpoint(t));
        if (after.equals(before)) {
            return false;
        }
        writeAll(d, after);
        return true;
    }

    /** @return true, jeśli coś usunięto. */
    public static boolean remove(ComputerDetails d, ComputerDetails.AddressTuple t) {
        AddressBook before = HostAddresses.normalize(toBook(d));
        AddressBook after = HostAddresses.remove(before, toEndpoint(t));
        if (after.equals(before)) {
            return false;
        }
        writeAll(d, after);
        if (t.equals(d.activeAddress)) {
            d.activeAddress = null;
        }
        return true;
    }
}
