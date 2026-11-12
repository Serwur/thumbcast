# Konfiguracja hosta (PC)

Thumbcast łączy się z dowolnym hostem zgodnym z GameStream: Sunshine (LizardByte, GPLv3) albo jego forkami Apollo i Vibepollo ([DECISIONS.md](DECISIONS.md), pkt a). Dokumentacja prowadzi przez **Sunshine**. Stan opisu: listopad 2026 (Sunshine stable v2026.914.233613, pre-release v2026.1018). Oznaczenie **[?]** to rzecz niepotwierdzona, do sprawdzenia testem.

## 1. Instalacja Sunshine

1. Pobierz Sunshine **v2026.914 lub nowszy** ze strony wydań: https://github.com/SunshineStream/Sunshine/releases
   (repo przeniesione z `LizardByte/Sunshine`, stary adres przekierowuje; to samo repo, sprawdzone 11.11.2026).
   - Windows na zwykłym PC (x86-64): **`Sunshine-Windows-AMD64-installer.msi`**. `-lite.zip` to wersja przenośna bez instalatora i usługi,
     `-debuginfo.7z` to symbole debugowania (niepotrzebne), `ARM64` tylko dla Windows na ARM; pozostałe pliki są dla Linuksa, macOS i FreeBSD.
   - Sprawdź sumę przed instalacją (PowerShell): `Get-FileHash .\Sunshine-Windows-AMD64-installer.msi`. Dla v2026.914.233613 SHA-256 to
     `1d7fed8beecd5889dc7ff14cf9f42d6d38f37c3066c13c6c2a5f4e91847e0ccf` (pole `digest` zasobu w API GitHuba).
   - Instalator zakłada usługę Windows i reguły zapory. **Nie używaj obrazu Dockera na Windows:** kontener Linuksa (WSL2) nie przechwyci
     pulpitu Windows ani gry, nie użyje enkodera GPU dla ekranu Windows i nie da grze wirtualnego pada. Obrazy Dockera Sunshine są dla hostów z Linuksem.
2. Zainstaluj i otwórz web UI (domyślnie `https://localhost:47990`). Ustaw login i hasło administratora.
3. Sparuj telefon w domu, w tej samej sieci LAN: w Thumbcast wybierz PC, a PIN wpisz w web UI Sunshine (zakładka PIN).

## 2. Sterownik pada (Windows): `gamepad_driver`

Tryb **Pad+** wymaga wirtualnego pada po stronie PC. Od Sunshine v2026.906 wejście obsługuje biblioteka **libvirtualhid**. Na Windows są dwie ścieżki (decyzja: [DECISIONS.md](DECISIONS.md), pkt b). Klient wysyła standardowe pakiety pada i nie zależy od wybranego sterownika (pkt c).

| | (a) Virtual HID Driver (LizardByte) | (b) ViGEmBus |
|---|---|---|
| Koszt | płatny: 14,99 USD/rok albo 49,99 USD jednorazowo; licencja na 5 maszyn | darmowy (licencja BSD-3-Clause) |
| Licencja sterownika | source-available; kontrybutorzy LizardByte mogą poprosić o darmową licencję (bez gwarancji) | BSD-3-Clause |
| Status | aktywnie rozwijany; wymaga internetu przy tworzeniu pada (sprawdzenie licencji) | wersja **1.17+**; **EOL**, repo zarchiwizowane 2.11.2023, brak aktualizacji |
| Emulowane pady | X360, Xbox One, Xbox Series, DS4, DualSense, Switch Pro, generic | tylko X360 i DS4 |
| Instalacja | licencja w Sunshine | ręcznie: web UI Sunshine → Troubleshooting (od v2026.516 Sunshine nie instaluje go sam) |
| Ustawienie | `gamepad_driver = virtualhid` (wyłącza fallback ViGEm) | `gamepad_driver = vigembus` |

Wartość domyślna `gamepad_driver = all` próbuje dostępnych sterowników.

**Którą ścieżkę wybrać.** Bez budżetu i bez wymagań co do wsparcia: **ViGEmBus** (krok „Ścieżka ViGEmBus” poniżej). Chcesz wspieranego, rozwijanego sterownika albo pada innego niż X360/DS4: Virtual HID Driver. Nie chcesz żadnego: tryb bez pada (sekcja 2a).

### Ścieżka ViGEmBus (krok hosta)

1. Web UI Sunshine → Troubleshooting → zainstaluj ViGEmBus (wersja 1.17 lub nowsza) i uruchom ponownie PC, jeśli instalator o to prosi.
2. Web UI → Configuration → Input: `gamepad_driver = vigembus`, `gamepad = x360`, `controller = enabled`. Zapisz i zrestartuj Sunshine.
3. Sprawdź: w aplikacji Thumbcast (ekran diagnostyki, E3) albo ręcznie przez `joy.cpl` powinien pojawić się pad Xbox 360.

Zalecane ustawienia (web UI → Configuration → Input):
- `controller = enabled`
- `gamepad = x360` (ViGEm) albo `xseries` (Virtual HID). **Emuluj pad Xbox**: nie wiadomo, czy gra uznaje wirtualnego DS4/DualSense za „XInput-compatible” [?].
- `keyboard = enabled`, `mouse = enabled`
- `high_resolution_scrolling = enabled` (domyślnie), bo szczypanie wysyła kółko w krokach 120.
- `key_repeat_delay` zostaw domyślne (500 ms). Auto-repeat trzymanego klawisza robi host, tak jak przy fizycznej klawiaturze.

## 2a. Tryb bez pada (Klawiatura+Mysz)

Bez sterownika pada (ani ViGEmBus, ani Virtual HID Driver) używaj trybu **Klawiatura+Mysz** (profil „MMO – Klawiatura”). To plan awaryjny i tryb domyślny dla takich hostów ([DECISIONS.md](DECISIONS.md), pkt f). Klawiatura i mysz działają na Windows bez licencji (fallback Win32); LizardByte podaje, że na żadnej platformie nie wymagają one licencji Virtual HID Driver.

Zastrzeżenie [?]: dokumentacja libvirtualhid wspomina licencję dla myszy „widocznych dla Raw Input”. Jeśli gra obraca kamerę przez Raw Input, obrót przy RMB w trybie KBM może nie działać bez sterownika. Do sprawdzenia testem (Q2 w planie, krok B7 w [test-plan.md](test-plan.md), host H3).

Test w aplikacji (ekran diagnostyki, etap E3): Thumbcast wyśle przycisk A, a Ty potwierdzisz, że pad widać w Windows: Ustawienia → Bluetooth i urządzenia → Urządzenia (albo `joy.cpl`).

## 3. Bezpieczeństwo sieci

- `upnp = disabled` (domyślnie). Nie przekierowuj portów Sunshine w routerze (TCP 47984/47989/48010, UDP 47998–48010).
- `origin_web_ui_allowed = lan` (domyślnie).
- `lan_encryption_mode = 0`, `wan_encryption_mode = 1` (domyślnie).
- Poza domem użyj Tailscale (albo ZeroTier): [tailscale.md](tailscale.md). Tunele HTTP (Cloudflare Tunnel, ngrok) się nie nadają, bo strumień to UDP ([DECISIONS.md](DECISIONS.md), pkt e).

## 3a. Jeden PC w domu (LAN) i poza domem (Tailscale/ZeroTier)

Od 12.11.2026 jeden wpis PC w Thumbcast trzyma kilka adresów: adres LAN i adresy alternatywne (Tailscale, ZeroTier, inne). Parowanie jest jedno, bo zależy od certyfikatu PC, nie od adresu. Na urządzeniu jeszcze niesprawdzone (HANDOFF 4f).

Jak to działa:
- **LAN ma pierwszeństwo.** Aplikacja pyta wszystkie znane adresy równolegle, ale wybiera adres LAN (192.168.x, 10.x, 172.16–31.x), jeśli odpowie. Adres alternatywny jest używany dopiero, gdy LAN nie odpowie w ok. 3 s (poza domem). W domu z włączonym Tailscale stream idzie przez LAN.
- Stream, lista aplikacji i parowanie używają adresu, który właśnie odpowiedział. Po zmianie sieci (Wi-Fi ↔ LTE, włączenie VPN) aplikacja najpierw odpytuje PC od nowa.
- Odpowiedź przez Tailscale nie kasuje adresu LAN (Sunshine zgłasza wtedy jako „LocalIP” adres `100.x`; wcześniej nadpisywał on adres LAN i trzeba było usuwać PC i parować od nowa).
- Wake-on-LAN idzie na wszystkie znane adresy i na broadcast.

Co robi użytkownik (raz):
1. Sparuj PC w domu, w LAN (sekcja 1, krok 3).
2. Dodaj adres Tailscale tego PC (`100.x.y.z` albo nazwa MagicDNS, np. `pc.tailnet.ts.net`). Dwie drogi, obie bez parowania:
   - przytrzymaj PC na liście → **Adresy połączenia** → **Dodaj adres**;
   - albo „Dodaj PC” z adresem Tailscale: aplikacja rozpozna ten sam PC (po `uniqueid` z Sunshine) i dopisze adres do istniejącego wpisu.
3. Sprawdź w **Adresy połączenia**: grupa „LAN” i „Alternatywny”, ✓ przy adresie używanym w tej chwili. Tam też usuniesz stary adres (np. po zmianie IP w Tailscale). Adres LAN zgłoszony przez PC wraca sam po następnym połączeniu w LAN.

ZeroTier: adres ZeroTier bywa prywatny (np. `10.147.x.x`), więc lista pokaże go w grupie „LAN”. Nie nadpisze on jednak adresu domowego (adresy dodane ręcznie tylko się dopisują), a kolejność wśród adresów LAN to: adres zgłoszony przez PC, potem ręczne.

Aktualizacja ze starszej wersji: wpis, który po sesji przez Tailscale miał zapisany `100.x` jako adres LAN, zostaje poprawiony przy starcie (adres trafia do alternatywnych, parowanie zostaje). Jeśli adres LAN zginął wcześniej, wróci po wykryciu PC w sieci (mDNS) albo po jednorazowym dodaniu IP LAN jak w kroku 2.

Scenariusz testu (telefon + PC z Sunshine i Tailscale):
1. W domu (Wi-Fi, Tailscale na telefonie wyłączony): PC online, „Adresy połączenia” → ✓ przy adresie LAN. Uruchom stream pulpitu, wyjdź.
2. Dodaj adres Tailscale (krok 2 wyżej). PC dalej jeden na liście, dalej sparowany, ✓ dalej przy LAN.
3. Wyłącz Wi-Fi, włącz LTE i Tailscale. Po kilku sekundach PC online, ✓ przy `100.x`. Uruchom stream.
4. Wróć na Wi-Fi i wyłącz Tailscale. PC online bez usuwania i parowania, ✓ przy LAN. Uruchom stream.
5. Powtórz 4 z włączonym Tailscale: ✓ ma być przy LAN.
6. Opcjonalnie: uśpij PC, „Wyślij Wake-on-LAN” w domu.

## 4. Ustawienia w grze (MMO z trybem pada, np. WoW: Forever)

Tryb pada w grze ma status „Alpha”, więc nazwy opcji mogą się zmieniać.

1. Options → Gameplay → Gamepad → **Enable Gamepad UI (Alpha)**. Da się to włączyć też na ekranie logowania.
2. Włącz **Show Input Prompts** (glyphy przycisków Xbox, zgodne z etykietami Thumbcast).
3. Rozważ **Compact Action Bar**.
4. Progi **Back Pedal** i **Stick Run** (domyślnie 0.6 i 0.5): na dotykowym joysticku zalecamy ok. **0.3**.
5. Ustaw czułość kamery i inwersję według uznania. Follow Camera według preferencji.

Przydatne skróty gry (nie są funkcją Thumbcast):
- zoom: LB+RB + prawa gałka przód/tył (w Pad+ zamiast tego szczypanie = kółko myszy);
- LB+RB+B: plecak; LB+RB+Up: quest tracker; LB+RB+Down: czat; LB+RB+L3: centrowanie kamery.

Czat wymaga klawiatury: w Thumbcast użyj przycisku ⌨ (klawiatura ekranowa).

## 5. macOS jako host

- libvirtualhid na macOS obsługuje tylko **klawiaturę i mysz** (sekcja 2a). Pady pojawiły się dopiero w pre-release Sunshine v2026.1017 [?].
- **Na Macu używaj trybu Klawiatura+Mysz** (profil „MMO – Klawiatura”).
- Sunshine wymaga zgody w Ustawienia systemowe → Prywatność i ochrona → **Dostępność** (Accessibility) oraz **Nagrywanie ekranu**.

## 6. Alternatywa: Apollo i Vibepollo

Apollo (fork Sunshine, GPLv3, tylko Windows) tworzy wirtualny ekran dopasowany do rozdzielczości telefonu (np. 2400×1080, 20:9). Thumbcast powinien działać z Apollo jak z każdym hostem GameStream, ale dokumentacja i testy obejmują Sunshine. Obsługa sterownika pada w Apollo nie jest jasno opisana [?]. Vibepollo (kolejny fork Sunshine) jest wspierany tak samo „best effort”, bez osobnych testów.

## Źródła

- Sunshine, konfiguracja: https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html
- Sunshine, wydania: https://github.com/SunshineStream/Sunshine/releases (v2026.906.222525: migracja na libvirtualhid)
- libvirtualhid, licencje i platformy: https://docs.lizardbyte.dev/projects/libvirtualhid
- ViGEmBus (archiwum): https://github.com/nefarius/ViGEmBus
- Virtual HID Driver, ceny i licencja: https://app.lizardbyte.dev/2026-08-16-introducing-libvirtualhid-and-virtual-hid-driver/
- Poradnik pada w grze: https://www.warcrafttavern.com/forever/guides/controller/ ; https://worldofwarcraft.blizzard.com/news/24316974/
