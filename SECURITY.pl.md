# Bezpieczeństwo

<p><a href="SECURITY.md">English</a> · <b>Polski</b></p>

## Wspierane wersje

Projekt jest we wczesnym dostępie. Poprawki bezpieczeństwa trafiają tylko do najnowszego wydania i gałęzi `main`.

## Zgłaszanie podatności

Nie zgłaszaj podatności w publicznych issue. Użyj prywatnego zgłoszenia na GitHub: zakładka **Security → Report a vulnerability** w repozytorium (GitHub Private Vulnerability Reporting). Opisz:
- wersję aplikacji (lub commit), urządzenie i wersję Androida;
- wersję Sunshine i system hosta, jeśli dotyczy;
- kroki odtworzenia i wpływ.

Postaramy się odpowiedzieć w ciągu 7 dni. Po wydaniu poprawki opublikujemy advisory z podziękowaniem dla zgłaszającego (jeśli sobie tego życzy).

Podatności w warstwie streamingu, która pochodzi z upstreamu (`nvstream`, `moonlight-common-c`), zgłoś też do projektu Moonlight. Podatności hosta zgłoś do projektu Sunshine.

## Model zagrożeń (skrót)

- Aplikacja łączy się wyłącznie z hostem, którego sparowałeś PIN-em (parowanie i szyfrowanie z upstreamu).
- Nie zbiera danych i nie ma telemetrii. Logi crashy zostają na urządzeniu, a eksportujesz je ręcznie.
- Importowane profile (`.thumbcast.json`) są niezaufanymi danymi: parser odrzuca nieznane pola i typy, a walidator sprawdza zakresy. Błąd pozwalający profilowi ominąć walidację (np. wprowadzić sekwencję wejść) traktujemy jako podatność.
- Nie wystawiaj portów Sunshine do internetu. Do grania poza domem użyj VPN ([docs/tailscale.md](docs/tailscale.md)).

## Podpis wydań

APK z GitHub Releases są podpisane kluczem projektu, a obok leży plik `SHA256SUMS.txt`. Sprawdzenie:

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs thumbcast-*.apk
```

Odcisk certyfikatu podamy w README po pierwszym wydaniu.
