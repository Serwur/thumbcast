# Nazewnictwo i znaki towarowe

## Zasady

1. **Bez znaków towarowych gry i wydawcy** w nazwie aplikacji, `applicationId`, nazwach pakietów, zasobach, ikonach i grafikach: „WoW”, „Warcraft”, „Azeroth”, „Blizzard”, „Hearth-” (Hearthstone), ikony klas, fonty gry.
2. **Bez „Moonlight”** w nazwie i zasobach, żeby nie mylić z oficjalnym projektem. Wolno napisać w opisie „bazuje na Moonlight (GPLv3)”.
   Audyt tekstów, ikon i baneru upstreamu oraz nadpisania w `app/src/nonRoot/res/`: [`docs/branding.md`](branding.md).
3. Gra może pojawić się w README i dokumentacji wyłącznie jako opis zgodności (nominative use): „zgodne z grami z natywną obsługą pada, np. …”. Nigdy w nazwie, ikonie, tytule ani krótkim opisie w sklepie.
4. Presety nazywamy gatunkowo: „MMO – Pad+”, „MMO – Klawiatura”, „Akcja 3D”.
5. Etykiety przycisków: neutralne („Skok”, „Akcja”, „Mapa”) albo nazwy przycisków pada (A, B, LT). Glyphy Xbox rysujemy sami jako proste litery w kółku, bez logotypów.
6. Identyfikatory w kodzie: pakiet `app.thumbcast` (moduły), docelowe `applicationId` `io.github.<login>.thumbcast` [?] (do decyzji przy forku).

Strażnik w CI (job `lint`) sprawdza zakazane zależności i typy w presetach. Rozważyć dodanie sprawdzenia zakazanych słów w `res/values*/strings.xml` i `fastlane/` po forku.

## Propozycje nazw (z planu, sekcja 11)

| Nazwa | Uwagi |
|---|---|
| **Thumbcast** (robocza) | kciuk + stream; krótka, bez skojarzeń z grą |
| Kciukomat | polska, zabawna; gorsza globalnie |
| Tactyl Stream | dotyk i taktyka |
| Overthumb | |
| PadLoom | „tkany” układ przycisków, nawiązanie do edytora |
| Runepad [?] | „rune” kojarzy się z fantasy MMO; większe ryzyko kolizji |

## Kontrola przed wyborem nazwy

Nie wykonano offline. Do zrobienia przed pierwszym publicznym wydaniem:

| Rejestr | Gdzie | Thumbcast | Uwagi |
|---|---|---|---|
| EUIPO (UE) | https://euipo.europa.eu/eSearch/ (klasy 9, 41, 42) | ☐ | |
| USPTO (USA) | https://tmsearch.uspto.gov/ | ☐ | |
| UPRP (Polska) | https://ewyszukiwarka.pue.uprp.gov.pl/ | ☐ | |
| WIPO Global Brand Database | https://branddb.wipo.int/ | ☐ | |
| Google Play | wyszukiwarka sklepu | ☐ | |
| F-Droid | https://f-droid.org/packages/ | ☐ | |
| GitHub | repozytoria i organizacje o tej nazwie | ☐ | |
| Domeny | `thumbcast.app`, `.dev`, `.org` | ☐ | opcjonalnie |

Wynik kontroli zapisz w tej tabeli z datą. Jeśli „Thumbcast” koliduje, wybierz następną nazwę z listy i zmień: `rootProject.name`, nazwę aplikacji, `applicationId` (przed pierwszym wydaniem, bo potem zmiana = nowa aplikacja), metadane fastlane.
