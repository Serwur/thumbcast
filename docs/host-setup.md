# Konfiguracja hosta (PC)

Thumbcast łączy się z dowolnym hostem zgodnym z GameStream: Sunshine (LizardByte, GPLv3) albo jego forkami Apollo i Vibepollo ([DECISIONS.md](DECISIONS.md), pkt a). Dokumentacja prowadzi przez **Sunshine**. Stan opisu: listopad 2026 (Sunshine stable v2026.914.233613, pre-release v2026.1018). Oznaczenie **[?]** to rzecz niepotwierdzona, do sprawdzenia testem.

## 1. Instalacja Sunshine

1. Pobierz Sunshine **v2026.914 lub nowszy** ze strony wydań: https://github.com/LizardByte/Sunshine/releases
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
- Sunshine, wydania: https://github.com/LizardByte/Sunshine/releases (v2026.906.222525: migracja na libvirtualhid)
- libvirtualhid, licencje i platformy: https://docs.lizardbyte.dev/projects/libvirtualhid
- ViGEmBus (archiwum): https://github.com/nefarius/ViGEmBus
- Virtual HID Driver, ceny i licencja: https://app.lizardbyte.dev/2026-08-16-introducing-libvirtualhid-and-virtual-hid-driver/
- Poradnik pada w grze: https://www.warcrafttavern.com/forever/guides/controller/ ; https://worldofwarcraft.blizzard.com/news/24316974/
