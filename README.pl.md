<p align="center"><img src="store/icon-512.png" alt="" width="96"></p>

<h1 align="center">Thumbcast</h1>

<p align="center">Graj w gry z PC na telefonie z Androidem, dotykiem albo padem.</p>

<p align="center"><a href="README.md">English</a> · <b>Polski</b></p>

---

Thumbcast przesyła gry z Twojego PC z Windows na telefon z Androidem. Gra działa na PC, a telefon pokazuje obraz i wysyła Twoje sterowanie jako wirtualny pad Xbox, klawiaturę albo mysz. Jest przeznaczony dla gier z natywną obsługą pada, w tym dla MMO z trybem pada (np. World of Warcraft: Forever; nazwy gier opisują tu tylko zgodność).

Thumbcast bazuje na [Moonlight dla Androida](https://github.com/moonlight-stream/moonlight-android) (GPLv3). Nie jest oficjalnym projektem Moonlight.

**Stan: wczesny dostęp (0.1.0).** Działa od początku do końca na prawdziwym telefonie i PC. Strona i polityka prywatności: <https://serwur.github.io/thumbcast/pl/>.

## Funkcje

- Przyciski na ekranie w układzie Xbox albo PlayStation, sterowanie myszą i klawiaturą, gładzik do pulpitu i układ do Steam Big Picture.
- Prawdziwy pad podłączony do telefonu; przyciski na ekranie chowają się, gdy nim grasz.
- Edytor układu i profile sterowania, zapisywane dla każdej gry i aplikacji PC, z importem i eksportem.
- Menu w grze: jakość obrazu, statystyki, sterowanie, rozmiar przycisków i zamykanie gry.
- Kreator pierwszego uruchomienia i poradnik „Jak podłączyć PC” bez internetu, zawsze pod ręką.
- Gra poza domem przez Tailscale albo ZeroTier, bez przekierowania portów.
- Angielski i polski.

## Uczciwa gra: 1 dotyk = 1 wejście

Każda kontrolka wysyła dokładnie jedno wejście. Nie ma makr, sekwencji, turbo, timerów ani analizy obrazu. Zasady są egzekwowane w kodzie i w CI. Szczegóły i uczciwy opis ryzyka: [FAIR_PLAY.pl.md](FAIR_PLAY.pl.md).

## Co jest potrzebne

**Na PC (Windows):**
- [Sunshine](https://github.com/SunshineStream/Sunshine/releases/latest), darmowy program open source do streamingu (działają też forki Apollo i Vibepollo).
- [ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest), sterownik wymagany do grania padem. Są płatne zamienniki (LizardByte Virtual HID Driver), ale ViGEmBus wystarczy. Bez sterownika pada używaj sterowania myszą i klawiaturą.
- Opcjonalnie: [Tailscale](https://tailscale.com/download), żeby grać poza domem.

**Na telefonie:** Android 8.0 lub nowszy, w tym samym Wi-Fi co PC (albo Tailscale).

Kreator w aplikacji przeprowadzi Cię przez instalację i parowanie. Te same kroki są na [stronie](https://serwur.github.io/thumbcast/pl/).

## Jak to działa

```
PC (Windows / macOS)                         Telefon (Android)
┌───────────────────────────┐               ┌───────────────────────────────────┐
│ Gra                       │               │ Thumbcast (fork moonlight-android)│
│   ▲ pad / klawiatura / mysz               │                                   │
│ Sunshine                  │  wideo/audio  │  nvstream + dekoder (bez zmian)   │
│   ViGEmBus / Virtual HID  │ ────────────► │  ThumbcastOverlay (jeden View)    │
│   / Win32                 │ ◄──────────── │   → InputEngine + ActionPolicy    │
└───────────────────────────┘    wejście    │   → InputSink (pad / klaw. / mysz)│
            LAN albo Tailscale (direct)     └───────────────────────────────────┘
```

| Moduł | Typ | Rola |
|---|---|---|
| `:app` | aplikacja Androida | Klient Moonlight dla Androida v12.2 z hakami Thumbcast (bloki `THUMBCAST`, pełny diff w `patches/0001-thumbcast.diff`). |
| `:control-model` | Kotlin/JVM | Profil sterowania (schemat JSON v1), walidacja, migracje. Schemat nie zna makr ani sekwencji. |
| `:control-engine` | Kotlin/JVM | Silnik wejścia: śledzenie palców, gesty, `ActionPolicy`, mapowanie wyjść. Testy właściwości. |
| `:control-android` | biblioteka Androida | `ThumbcastOverlay`, biblioteka profili, menu i statystyki w grze, adresy PC, ustawienia aplikacji. |
| `:control-editor` | Compose | Edytor układu. |
| `:control-ui` | Compose | Motyw, kreator, poradnik „Jak podłączyć PC”. |
| `presets/` | JSON | Profile wbudowane: Pad – PlayStation (domyślny), Pad – Xbox, MMO – Pad+, MMO – Klawiatura, Pulpit – Touchpad, Big Picture. |

## Budowanie

Wymagania: JDK 17+ (wystarczy JetBrains Runtime z Android Studio), Android SDK z compileSdk 37, NDK 29.0.14206865.

```bash
git clone --recurse-submodules https://github.com/Serwur/thumbcast.git
cd thumbcast
./gradlew :app:assembleNonRootDebug
```

Testy (testy właściwości polityki w CI idą z pełną liczbą iteracji):

```bash
./gradlew :control-model:test :control-engine:test :control-android:testDebugUnitTest
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
```

Wydania buduje `.github/workflows/release.yml` z tagu `v*`: podpisany APK na GitHub i AAB do Google Play.

## Dokumentacja

Dokumenty publiczne są po angielsku i po polsku. Dokumentacja deweloperska w `docs/` i w modułach jest po polsku.

- [FAIR_PLAY.pl.md](FAIR_PLAY.pl.md): zasady i ryzyko
- [CONTRIBUTING.pl.md](CONTRIBUTING.pl.md): jak współtworzyć
- [SECURITY.pl.md](SECURITY.pl.md): zgłaszanie podatności
- [docs/host-setup.md](docs/host-setup.md), [docs/host-apps.md](docs/host-apps.md), [docs/tailscale.md](docs/tailscale.md): konfiguracja PC, gry w Sunshine, gra poza domem
- [docs/PLAN.md](docs/PLAN.md), [docs/DECISIONS.md](docs/DECISIONS.md), [docs/HANDOFF.md](docs/HANDOFF.md): plan, decyzje i stan prac

## Licencja

GPL-3.0-or-later, tak jak upstream. Szczegóły w [LICENSE](LICENSE) i [NOTICE](NOTICE).

World of Warcraft i Blizzard są znakami towarowymi Blizzard Entertainment, Inc. Xbox jest znakiem towarowym Microsoft, PlayStation znakiem towarowym Sony Interactive Entertainment. Moonlight należy do jego autorów. Nazwy służą tylko do opisu zgodności; Thumbcast nie jest z nimi powiązany ani przez nich wspierany.
