# Granie poza domem przez Tailscale

Tailscale tworzy prywatną sieć (WireGuard) między PC a telefonem. Dzięki temu nie trzeba otwierać portów Sunshine do internetu.

## Konfiguracja

1. Zainstaluj Tailscale na PC (jako usługę startującą z systemem) i na telefonie. Zaloguj oba urządzenia na to samo konto (tailnet).
2. **Sparuj Thumbcast z PC w domu, w LAN** (zalecenie dokumentacji Moonlight).
3. W Thumbcast dodaj adres Tailscale (`100.x.y.z` albo nazwa MagicDNS PC) do **tego samego** PC: przytrzymaj PC → „Adresy połączenia” → „Dodaj adres” (albo „Dodaj PC”, które scali wpis). Bez usuwania PC i bez ponownego parowania; w domu nadal wybierany jest adres LAN. Szczegóły: [host-setup.md](host-setup.md), sekcja 3a.
4. Ustawienia strumienia na LTE/5G: 720p lub 1080p przy 60 kl./s, bitrate 10–20 Mb/s, HEVC lub AV1, jeśli dekoder telefonu je obsługuje.

## Połączenie musi być „direct”

Przekaźniki DERP są wolne i mają niską przepustowość. Dokumentacja Moonlight podaje, że stream przez sam relay zawodzi.

Sprawdzenie (na PC lub telefonie z dostępem do CLI):

```bash
tailscale status          # typ połączenia z telefonem
tailscale ping <nazwa-pc> # "via DERP(...)" = relay, "via <ip>:<port>" = direct
```

Jak zwiększyć szansę na direct:
- zezwól na ruch UDP wychodzący i powroty w zaporze PC;
- włącz UPnP/NAT-PMP na routerze **albo** przekieruj UDP 41641 na PC (to port Tailscale, nie Sunshine);
- sieć komórkowa za CGNAT zwykle pozwala na direct, jeśli NAT w domu jest „łatwy”;
- przy „hard NAT” po obu stronach rozważ peer relay w tailnecie, np. domowy serwer z Linuksem jako relay.

## Czego nie robić

- Nie przekierowuj portów Sunshine (TCP 47984/47989/48010, UDP 47998–48010) w routerze.
- Nie włączaj `upnp` w Sunshine (domyślnie wyłączone).
- Zostaw `origin_web_ui_allowed = lan`.

## Otwarte kwestie [?]

- ~~Czy Sunshine traktuje adresy `100.64.0.0/10` jako LAN czy WAN?~~ Jako **LAN** (`src/network.cpp`, lista `lan_ips_v4`, sprawdzone w kodzie 12.11.2026, nie testem): przez Tailscale obowiązuje `lan_encryption_mode`, a web UI z `origin_web_ui_allowed = lan` jest dostępne z tailnetu. Tunel WireGuard szyfruje ruch tak czy inaczej.
- MTU tunelu WireGuard może wymagać mniejszych pakietów wideo (ustawienie rozmiaru pakietu w kliencie). Do sprawdzenia przy pierwszych testach na LTE.
- Wykrywanie relay w aplikacji jest możliwe tylko heurystycznie (RTT i jitter), bo bez integracji z aplikacją Tailscale nie mamy jej API na Androidzie. Planowany ekran „Poza domem” pokaże ping i jitter oraz komunikat „prawdopodobnie relay, obniż bitrate”.

## Pomiar

Wyniki testu (typ połączenia, RTT, touch-to-photon) zapisuj w `docs/tests/` według [test-plan.md](test-plan.md). Cel: Tailscale direct na LTE/5G ≤ 100 ms p50 touch-to-photon.

## Źródła

- Tailscale, typy połączeń: https://tailscale.com/kb/1257/connection-types
- Moonlight Setup Guide (porty, VPN, parowanie): https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide
