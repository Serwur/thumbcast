# Patch: jeden wpis PC dla LAN i Tailscale/ZeroTier (adresy połączenia)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Pewny stan: `0001-thumbcast.diff`.
Logika bez Androida: `control-android/src/main/kotlin/app/thumbcast/android/net/HostAddresses.kt`
(testy `HostAddressesTest`, `:control-android:testDebugUnitTest`).

## Przyczyna (upstream v12.2, numery linii sprzed zmiany)

1. **Sunshine zgłasza jako `LocalIP` adres, na który przyszło zapytanie.** `src/nvhttp.cpp`, `serverinfo`:
   `auto local_endpoint = request->local_endpoint();` i `tree.put("root.LocalIP", net::addr_to_normalized_string(local_endpoint.address()));`
   (sprawdzone 12.11.2026 na `master`). Zapytanie przez Tailscale zwraca więc `100.x.y.z`. `ExternalIP` Sunshine wysyła tylko
   przy ustawionym `external_ip`.
2. **Klient wpisuje `LocalIP` do `localAddress` przy każdym odpytaniu.** `NvHTTP.java:377`
   (`details.localAddress = makeTuple(getXmlString(serverInfo, "LocalIP", false), baseUrlHttp.port());`),
   potem `ComputerManagerService.pollComputer` `:707-708` (`details.update(polledDetails)`) i `ComputerDetails.update` `:119-121`
   nadpisuje `localAddress` każdym adresem poza `127.*`. `runPoll` `:134-136` zapisuje to do bazy
   (`existingComputer.update(details); dbManager.updateComputer(existingComputer);`). Po pierwszej udanej odpowiedzi przez
   Tailscale adres LAN znika z wpisu na stałe.
3. **Dodanie PC drugi raz innym adresem nadpisuje poprzedni adres ręczny.** `AddComputerManually.java:146` ustawia
   `manualAddress`, `addComputerBlocking` `:474-495` łączy po `uuid` (duplikatu nie ma), ale `ComputerDetails.update` `:131-133`
   zastępuje `manualAddress`. Dodanie IP Tailscale kasuje ręczny adres LAN, a ponowne dodanie IP LAN kasuje adres Tailscale.
4. Skutek: po sesji przez Tailscale wpis ma `local` = `manual` = `100.x`, `remote` = publiczny IP z STUN (bez przekierowania portów
   nieosiągalny), w LAN bez Tailscale żaden adres nie odpowiada (`parallelPollPc` `:627-699` próbuje tylko tych czterech pól),
   PC jest offline. Naprawić to może tylko mDNS (jeśli zadziała) albo ręczne dodanie IP LAN (które kasuje adres Tailscale).
   Usunięcie PC (`removeComputer` → `dbManager.deleteComputer`) usuwa przypięty certyfikat (`ServerCert`), stąd ponowne parowanie.
   Samo parowanie nie zależy od adresu: `NvHTTP` `:158-168` przyjmuje dowolną nazwę hosta dla przypiętego certyfikatu.
5. Pomniejsze: po zmianie sieci `onAvailable` (`:743-755`) zeruje tylko `state`; `activeAddress` (z niego startuje stream,
   `ServerHelper.java:29-34`, `:56-58`) zostaje stary do następnego udanego odpytania.

Uwaga do dokumentacji: Sunshine (`src/network.cpp`, `lan_ips_v4`) traktuje `100.64.0.0/10` jako LAN, więc przez Tailscale
obowiązuje `lan_encryption_mode`, a web UI z `origin_web_ui_allowed = lan` jest dostępne przez tailnet.

## Zmiany

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `nvstream/http/ComputerDetails.java` | `public AddressTuple ipv6Address;` | nowe pole `List<AddressTuple> extraAddresses` (kolejne adresy ręczne/alternatywne) |
| | `// We can get IPv4 loopback addresses with GS IPv6 Forwarder` (blok `localAddress`) | zastąpione `ThumbcastAddressBook.mergeInto(this, details)` |
| | `if (details.manualAddress != null) {` (blok w `update`) | usunięte (łączy `mergeInto`) |
| | `str.append("Manual Address: ")` | dopisane `Extra Addresses` |
| `computers/ComputerManagerService.java` | `details.update(polledDetails);` w `pollComputer` | przed nim `ThumbcastAddressBook.applyPollReply(details, polledDetails)` |
| | `private ComputerDetails parallelPollPc(` | ciało zastąpione pętlą po `ThumbcastAddressBook.pollOrder(details)` (wszystkie adresy, LAN pierwszy) |
| | `LimeLog.info("Starting parallel poll for "` | log `extraAddresses` |
| | `LimeLog.info("Resetting PC state for new available network");` | dodatkowo `activeAddress = null` |
| | `public void invalidateStateForComputer(String uuid)` (binder) | przed nim `addAddress`, `removeAddress` |
| | `public boolean onUnbind(Intent intent)` | przed nim `editAddresses` (zmiana, zapis do bazy, powiadomienie listenera) |
| `computers/ComputerDatabaseManager.java` | `String IPv6 = "ipv6";` | klucz `thumbcastExtra` (tablica JSON w kolumnie `Addresses`) |
| | `addresses.put(AddressFields.IPv6, ...)` | zapis `extraAddresses` |
| | `details.ipv6Address = tupleFromJson(...)` | odczyt `extraAddresses` i `ThumbcastAddressBook.migrate(details)` |
| `nvstream/wol/WakeOnLanSender.java` | `for (ComputerDetails.AddressTuple address : new ComputerDetails.AddressTuple[] {` | lista z czterech pól + `extraAddresses` (broadcast dla każdego adresu jak w upstreamie) |
| `PcView.java` | `GAMESTREAM_EOL_ID = 11;`, `VIEW_DETAILS_ID, 7`, `case GAMESTREAM_EOL_ID:` | pozycja menu „Adresy połączenia” (`THUMBCAST_ADDRESSES_ID = 100`) → `HostAddressesDialog.show` |
| `thumbcast/ThumbcastAddressBook.java` (nowy) | | most pól `ComputerDetails` ↔ `HostAddresses` |
| `thumbcast/HostAddressesDialog.java` (nowy) | | dialog: lista w kolejności łączenia, ✓ = używany, tap = usuń, „Dodaj adres” |
| `res/values{,-pl}/thumbcast_strings.xml` | | `thumbcast_pcview_menu_addresses`, `thumbcast_addresses_*` |

## Reguły (`HostAddresses`)

- Klasy: LAN = 10/8, 172.16/12, 192.168/16, 169.254/16, IPv6 ULA (poza Tailscale) i link-local, nazwy `.local`;
  VPN = 100.64/10, `fd7a:115c:a1e0::/48`, nazwy `.ts.net`; reszta (publiczne, inne nazwy) = alternatywne. Bez DNS.
- `localAddress` (slot LAN) nigdy nie zawiera adresu VPN ani loopback. Odpowiedź przez adres ręczny (Tailscale, ZeroTier, nazwa)
  nie zmienia znanego adresu LAN, wypełnia tylko pusty slot adresem RFC 1918 (ZeroTier w 10.x nie nadpisze LAN). Odpowiedź przez
  LAN/mDNS/publiczny/IPv6: zachowanie upstreamu (nowy adres prywatny zastępuje stary, np. DHCP), ale adres publiczny nie zastępuje
  znanego prywatnego.
- Lista ręczna (`manualAddress` + `extraAddresses`) to suma przy każdym łączeniu, w kolejności dodania. Adres VPN zgłoszony przez mDNS
  trafia na nią jako alternatywny. Usuwa tylko użytkownik (dialog).
- Kolejność odpytywania: grupa LAN (slot LAN, ręczne adresy LAN), potem alternatywne (ręczne VPN/publiczne, `remote`, `ipv6`).
  Wszystkie równolegle, wygrywa pierwszy w kolejności, który odpowie; alternatywny dopiero po timeoucie LAN (3 s, 5 s dla aktywnego).
  Lista aplikacji, parowanie i stream używają `activeAddress`, czyli adresu z ostatniego udanego odpytania.

## Migracja

Odczyt z bazy (`getComputerFromCursor`) przenosi adres VPN ze slotu LAN na listę alternatywnych (`HostAddresses.normalize`).
Certyfikat, MAC, nazwa i `uuid` bez zmian, więc parowanie zostaje. Utraconego wcześniej adresu LAN nie da się odtworzyć:
wraca po odkryciu przez mDNS albo po dodaniu IP LAN (menu PC → Adresy połączenia albo „Dodaj PC”, które scala wpis po `uuid`).
Starsza wersja aplikacji ignoruje klucz `thumbcastExtra` (traci tylko dodatkowe adresy).
