# Decyzje projektowe

Data: 10.11.2026. Ten dokument ma pierwszeństwo przed [PLAN.md](PLAN.md) (plan wygenerowany 8.11.2026). Oznaczenie **[?]** to rzecz niepotwierdzona.

## (a) Host

Host to **Sunshine** (LizardByte, GPLv3) albo jego forki **Apollo** i **Vibepollo**. Thumbcast mówi protokołem GameStream, więc działa z każdym z nich. Dokumentacja i testy obejmują Sunshine ([host-setup.md](host-setup.md)); forki są wspierane „best effort”.

## (b) Pad wirtualny w Windows

Dwie ścieżki, obie wspierane:

| | ViGEmBus | Virtual HID Driver (LizardByte) |
|---|---|---|
| Koszt | darmowy | 14,99 USD/rok albo 49,99 USD jednorazowo |
| Licencja sterownika | BSD-3-Clause | source-available (płatna licencja na maszynę) |
| Status | wersja 1.17+; projekt zarchiwizowany w XI 2023 (EOL, bez aktualizacji) | rozwijany |
| Pady | Xbox 360 i DS4 | m.in. X360, Xbox One, Xbox Series, DS4, DualSense |
| Ograniczenia | instalacja ręczna | licencja na 5 maszyn; wymaga internetu przy tworzeniu pada |

- Kontrybutorzy projektów LizardByte mogą poprosić o darmową licencję Virtual HID Driver (decyzja indywidualna, bez gwarancji).
- Klawiatura i mysz nie wymagają licencji na żadnej platformie, więc tryb Klawiatura+Mysz działa zawsze (ale patrz Q2 w [test-plan.md](test-plan.md)).
- Rekomendacja domyślna dla użytkownika bez budżetu: ViGEmBus (`gamepad_driver = vigembus`, `gamepad = x360`). Dla kogoś, kto chce wspieranego sterownika: Virtual HID Driver.
- Źródło (ceny, limity, licencja): https://app.lizardbyte.dev/2026-08-16-introducing-libvirtualhid-and-virtual-hid-driver/ (sprawdzone 10.11.2026).

## (c) Warstwa pada w kliencie jest niezależna od sterownika hosta

Klient wysyła standardowe pakiety pada (stan przycisków, osie, wyzwalacze). Nie zna sterownika hosta i go nie wykrywa. Co host zrobi z pakietem (ViGEmBus, Virtual HID Driver, inny), to jego sprawa. Dzięki temu zmiana sterownika nie wymaga zmian w aplikacji.

## (d) Nie robimy własnego streamingu ani własnego sterownika

- **Streaming:** Sunshine + Moonlight to dojrzałe, otwarte rozwiązanie. Własny stos wideo/sieci to miesiące pracy bez zysku dla sterowania dotykowego, które jest sednem projektu. Warstwa streamingu zostaje bez zmian, co ułatwia rebase upstreamu.
- **Sterownik pada:** sterownik jądra Windows trzeba podpisać (certyfikat EV, program atestacji Microsoftu), co kosztuje i wymaga firmy. Do tego utrzymanie i odpowiedzialność za stabilność systemu użytkownika. Istnieją już dwa sterowniki (ViGEmBus i Virtual HID Driver), więc własny nic nie dodaje.

## (e) Dostęp zdalny (poza domem)

| Metoda | Ocena |
|---|---|
| Przekierowanie portów / UPnP | Wymaga publicznego IP, nie działa za CGNAT, wystawia host do internetu. **Nie zalecamy.** |
| IPv6 | Działa, jeśli operator i router dają routowalny adres i zaporę da się otworzyć. Nadal wystawia host. |
| Własny VPS z relayem | Możliwe (np. WireGuard przez VPS); wymaga własnej administracji, dodaje opóźnienie. |
| **Tailscale / ZeroTier** | **Zalecane** ([tailscale.md](tailscale.md)). Prywatna sieć, bez otwierania portów, połączenie bezpośrednie (direct) po NAT traversal. |
| Tunele HTTP (Cloudflare Tunnel, ngrok) | **Nie nadają się:** strumień Sunshine to UDP, a te tunele przenoszą HTTP/TCP. |

## (f) Tryb Klawiatura+Mysz jako plan awaryjny

Tryb Klawiatura+Mysz (profil „MMO – Klawiatura”) jest planem awaryjnym i trybem domyślnym dla hostów bez sterownika pada: Windows bez ViGEmBus i bez licencji, macOS (pady tylko w pre-release Sunshine [?]). Onboarding i diagnostyka hosta (E3) mają wykryć brak pada i zaproponować ten tryb. Domyślny tryb dla hosta z działającym padem pozostaje Pad+, z zastrzeżeniem wyniku testu T-03.

## (g) Nazwa repozytorium

Repo ma nazywać się **`thumbcast`**. Właściciel zmienia nazwę w GitHub Settings (proxy sesji blokuje zmiany ustawień repo, więc nie robimy tego z poziomu agenta). Do czasu zmiany repo nazywa się **Serwur/wow-portable**. Po zmianie: zaktualizować zdalny adres `origin` w lokalnych klonach (GitHub przekierowuje stare URL-e, ale nie należy na tym polegać) oraz linki w README.

## (h) Otwarte decyzje

- **Fork vs submoduł:** repo = fork upstreamu z modułami obok `app/` (rekomendacja z [NEXT-STEPS.md](NEXT-STEPS.md)), czy upstream jako submoduł git. Do rozstrzygnięcia przy pierwszej sesji z siecią.
- **`applicationId`:** `io.github.<login>.thumbcast` [?]. Ustalić przed pierwszym wydaniem (późniejsza zmiana = nowa aplikacja) i po kontroli znaków towarowych ([naming.md](naming.md)).
- **Landscape lock:** czy wymuszać orientację poziomą w sesji streamu (upstream robi to przy włączonym OSC), czy obsługiwać też portret.
