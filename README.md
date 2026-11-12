# Thumbcast

> **Start: [URUCHOMIENIE.md](URUCHOMIENIE.md)**. Plan: [docs/PLAN.md](docs/PLAN.md), decyzje (mają pierwszeństwo): [docs/DECISIONS.md](docs/DECISIONS.md).

> Repozytorium: https://github.com/Serwur/thumbcast (dawniej `wow-portable`; GitHub przekierowuje stare linki).

> **Status: pre-alpha.** Projekt nie jest zatwierdzony, wspierany ani sponsorowany przez Blizzard Entertainment ani przez projekt Moonlight. Na tym etapie repo zawiera szkielet modułów, dokumentację i CI. Aplikacji nie da się jeszcze zainstalować.

Thumbcast to klient streamingu gier z własnego PC na telefon z Androidem z konfigurowalnym sterowaniem dotykowym. Jest przeznaczony dla gier z natywną obsługą pada, w tym dla MMO z trybem gamepada (np. World of Warcraft: Forever; nazwa gry pojawia się tu tylko jako opis zgodności). Obraz idzie z Twojego komputera, a telefon wysyła dotyk jako wirtualny pad Xbox, klawiaturę lub mysz.

Bazuje na [Moonlight](https://github.com/moonlight-stream/moonlight-android) (GPLv3). Nie jest oficjalnym projektem Moonlight.

## Zasada nadrzędna: 1 dotyk = 1 wejście

Każda kontrolka wysyła dokładnie jedno wejście. W aplikacji nie ma makr, sekwencji, turbo, opóźnień ani analizy obrazu. Zasady są egzekwowane w kodzie i w CI. Szczegóły i uczciwy opis ryzyka: [FAIR_PLAY.md](FAIR_PLAY.md).

## Architektura

```
PC (Windows / macOS)                         Telefon (Android)
┌───────────────────────────┐               ┌───────────────────────────────────┐
│ Gra                       │               │ Thumbcast (fork moonlight-android)│
│   ▲ pad / klawiatura / mysz               │                                   │
│ Sunshine (LizardByte)     │  wideo/audio  │  nvstream + dekoder (bez zmian)   │
│   libvirtualhid / ViGEm / │ ────────────► │  ThumbcastOverlay (1 View)        │
│   Win32                   │ ◄──────────── │   → InputEngine + ActionPolicy    │
└───────────────────────────┘    wejście    │   → InputSink (pad / klaw. / mysz)│
            LAN albo Tailscale (direct)     └───────────────────────────────────┘
```

- **Host:** [Sunshine](https://github.com/SunshineStream/Sunshine) (GPLv3) albo jego forki Apollo i Vibepollo, zgodne z GameStream. Pad wirtualny w Windows: darmowy ViGEmBus 1.17+ (projekt zarchiwizowany) albo płatny Virtual HID Driver od LizardByte. Konfiguracja: [docs/host-setup.md](docs/host-setup.md), uzasadnienie: [docs/DECISIONS.md](docs/DECISIONS.md).
- **Klient:** fork upstreamu `moonlight-stream/moonlight-android`. Warstwa streamingu zostaje bez zmian. Wejście dotykowe zastępuje nowa warstwa w Kotlinie:

| Moduł | Typ | Rola |
|---|---|---|
| `:control-model` | Kotlin/JVM | Profil sterowania (JSON, schemat v1), walidacja, migracje. Schemat nie zna makr ani sekwencji. |
| `:control-engine` | Kotlin/JVM | Silnik wejścia: śledzenie wskaźników, rozpoznawanie gestów, `ActionPolicy`, mapowanie wyjść. Testy właściwości. |
| `:control-android` | biblioteka Androida | `ThumbcastOverlay` (jeden View, do 10 palców), adaptery do upstreamu. |
| `presets/` | JSON | Profile wbudowane: „Pad – PlayStation” (domyślny od 12.11.2026; użytkownik może ustawić inny w bibliotece) i „Pad – Xbox” (pełny standardowy pad: dwie gałki, D-pad, romb, LB/RB, LT/RT, View/Menu, L3/R3, Guide; te same wyjścia, inne etykiety; wolna strefa wyłączona, `freeZone = none`: dotyk poza kontrolkami nic nie wysyła, gest z górnej krawędzi otwiera menu; przełącznik „Gesty myszy” w bibliotece i w menu w grze włącza tam mysz bezpośrednią: tap = klik w punkcie, dwa palce = prawy klik, przeciągnięcie = przewijanie), „MMO – Pad+”, „MMO – Klawiatura”, „Pulpit – Touchpad” (pulpit, sama mysz, gładzik), „Big Picture” (dwie gałki, pełny układ pada, mysz bezpośrednia w wolnej strefie, `freeZone = direct`). |
| `patches/` | diff | Minimalne hooki w `Game.java` upstreamu. |
| `:app` | upstream | Dochodzi po forku: [docs/NEXT-STEPS.md](docs/NEXT-STEPS.md). |

Tryby sterowania:
- **Pad+** (domyślny): ruch, kamera i umiejętności jako wirtualny pad Xbox, a tap, szczypanie i klik w interfejs jako mysz. Wymaga sterownika pada po stronie PC (ViGEmBus albo Virtual HID Driver).
- **Klawiatura+Mysz**: działa na każdym hoście, także na macOS i na Windows bez sterownika pada (plan awaryjny i tryb domyślny dla hostów bez pada).

## Budowanie

Wymagania: JDK 17, Android Studio ze wsparciem AGP 9.5, Android SDK (compileSdk 37), NDK 29.0.14206865 (dla `:app` z upstreamu).

```bash
git clone --recurse-submodules <adres-repo>
cd thumbcast
./gradlew :control-model:test :control-engine:test
```

Do czasu forka repo nie zawiera `gradlew`. Użyj Gradle 9.7.1 z systemu (`gradle :control-model:test`) albo wygeneruj wrapper: `gradle wrapper --gradle-version 9.7.1`. Pełny build APK będzie możliwy po wpięciu `:app` (instrukcja w [docs/NEXT-STEPS.md](docs/NEXT-STEPS.md)).

Testy właściwości polityki z pełną liczbą iteracji (tak jak w CI):

```bash
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
```

## Dokumentacja

- [docs/PLAN.md](docs/PLAN.md): plan techniczny (wygenerowany 8.11.2026)
- [docs/DECISIONS.md](docs/DECISIONS.md): decyzje projektowe z 10.11.2026 (pierwszeństwo przed planem)
- [FAIR_PLAY.md](FAIR_PLAY.md): zasady i ryzyko
- [CONTRIBUTING.md](CONTRIBUTING.md): jak współtworzyć
- [SECURITY.md](SECURITY.md): zgłaszanie podatności
- [docs/host-setup.md](docs/host-setup.md): konfiguracja PC (Sunshine, sterowniki pada, ustawienia gry)
- [docs/tailscale.md](docs/tailscale.md): granie poza domem
- [docs/test-plan.md](docs/test-plan.md): plan testów (w tym T-03)
- [docs/distribution.md](docs/distribution.md): GitHub, F-Droid, Google Play
- [docs/naming.md](docs/naming.md): nazwy i znaki towarowe
- [docs/NEXT-STEPS.md](docs/NEXT-STEPS.md): fork upstreamu i pierwszy test

## Licencja

GPL-3.0-or-later, tak jak upstream. Szczegóły w [LICENSE](LICENSE) i [NOTICE](NOTICE).

World of Warcraft i Blizzard są znakami towarowymi Blizzard Entertainment, Inc. Moonlight jest projektem jego autorów. Xbox jest znakiem towarowym Microsoft. Nazwy są użyte wyłącznie do opisu zgodności.
