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

## Szybki start

Około 20 minut, jednorazowo. Potrzebujesz **PC z Windows** z grą, **telefonu z Androidem** (8.0 lub nowszy) i obu urządzeń w **tym samym Wi-Fi**.

> Thumbcast jest we wczesnym dostępie. Plik aplikacji (APK) jest na [stronie wydań](https://github.com/Serwur/thumbcast/releases/latest). Wersja w Google Play jest w przygotowaniu.

### Część 1. Na PC (raz)

1. **Zainstaluj Sunshine**
   Sunshine to darmowy program, który wysyła obraz gry na telefon.
   - [Pobierz Sunshine](https://github.com/SunshineStream/Sunshine/releases/latest) i uruchom plik `Sunshine-Windows-AMD64-installer.msi` (w sekcji *Assets*).
   - Przeklikaj instalator. Jeśli Windows zapyta o zaporę, wybierz **Zezwól**.

2. **Ustaw hasło w Sunshine**
   - Otwórz w przeglądarce `https://localhost:47990`.
   - Przeglądarka pokaże, że połączenie nie jest prywatne. To normalne. Kliknij *Zaawansowane*, potem *Przejdź dalej*.
   - Wybierz nazwę użytkownika i hasło i zapisz je sobie.

3. **Zainstaluj sterownik pada**
   Dzięki niemu gry widzą telefon jako zwykły pad Xbox.
   - [Pobierz ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest), uruchom instalator `.exe` i zrestartuj PC, jeśli o to poprosi.

4. **Wybierz pada w Sunshine**
   - Na stronie Sunshine otwórz **Configuration → Input**.
   - Ustaw emulowany typ pada na **Xbox 360** (`gamepad = x360`), zapisz i zrestartuj Sunshine, gdy o to poprosi.
   - Brak sterownika pada? Nadal możesz grać sterowaniem myszą i klawiaturą.

### Część 2. Na telefonie

5. **Zainstaluj Thumbcast**
   - Pobierz APK ze [strony wydań](https://github.com/Serwur/thumbcast/releases/latest) na telefonie i otwórz go.
   - Android poprosi o zgodę na instalację z przeglądarki. Zezwól na tę jedną instalację.

6. **Znajdź swój PC**
   - Otwórz Thumbcast. Kreator przeprowadzi Cię przez te same kroki.
   - PC pojawi się na ekranie głównym. Jeśli nie, sprawdź, czy oba urządzenia są w tym samym Wi-Fi i czy Sunshine działa, albo dotknij **Dodaj PC** i wpisz adres IP komputera.

7. **Sparuj (raz)**
   - Dotknij swojego PC. Telefon pokaże **PIN**.
   - Na PC otwórz stronę Sunshine, wejdź w zakładkę **PIN**, wpisz PIN, nadaj telefonowi dowolną nazwę i naciśnij **Send**.

8. **Graj**
   - Dotknij **Desktop** albo **Steam Big Picture** i uruchom grę.
   - Domyślne sterowanie to pad w stylu PlayStation na ekranie. Menu **☰** w grze zmienia profil, jakość i inne ustawienia.
   - W ustawieniach samej gry wybierz okno pełnoekranowe (bez ramki) i włącz obsługę pada, jeśli gra ma taką opcję.

### Część 3. Gra poza domem przez Tailscale (opcjonalnie)

9. **Najpierw zrób części 1 i 2 w domu**
   Sparuj telefon z PC w domowym Wi-Fi. Robisz to tylko raz.

10. **Zainstaluj Tailscale na PC**
   Tailscale to darmowa prywatna sieć między Twoimi urządzeniami. Nie trzeba nic otwierać w routerze.
   - [Pobierz Tailscale](https://tailscale.com/download) dla Windows, zainstaluj i **zaloguj się** (wystarczy konto Google, Microsoft lub Apple).
   - Zostaw go włączonego. Startuje razem z Windows.

11. **Zainstaluj Tailscale na telefonie**
   - Zainstaluj [Tailscale z Google Play](https://play.google.com/store/apps/details?id=com.tailscale.ipn) i zaloguj się **tym samym kontem** co na PC.
   - Włącz go (przełącznik u góry).

12. **Skopiuj adres PC w Tailscale**
   - W aplikacji Tailscale na telefonie dotknij swojego PC. Skopiuj jego adres, wygląda jak `100.x.y.z`.

13. **Dodaj adres w Thumbcast**
   - Na liście PC **przytrzymaj swój PC → Adresy połączenia → Dodaj adres** i wklej adres.
   - Nie parujesz od nowa. W domu Thumbcast użyje Wi-Fi, a poza domem Tailscale.

14. **Wskazówki**
   - Tailscale musi być włączony na obu urządzeniach.
   - Na danych komórkowych ustaw 720p lub 1080p przy 60 kl./s i bitrate 10–20 Mb/s (ustawienia Thumbcast).
   - Nie przekierowuj portów w routerze.

## Dodawanie własnych gier

Każda pozycja z zakładki **Applications** w Sunshine pojawia się w Thumbcast jako gra. Żeby dodać grę: otwórz stronę Sunshine (`https://localhost:47990`) → **Applications → Add New**, uzupełnij pola z poniższej listy, naciśnij **Save** i otwórz PC w Thumbcast od nowa.

**Gra ze Steam**

- **Application Name**: nazwa gry.
- **Detached Commands**: `steam://rungameid/<id gry>`. Id gry to liczba w adresie jej strony w sklepie Steam.

**Gra z Battle.net**

- **Detached Commands**: `"C:\Program Files (x86)\Battle.net\Battle.net.exe" --exec="launch <kod>"`.
- Kody gier: `WoW` dla World of Warcraft; `WoWF` dla WoW: Forever (jeszcze niesprawdzone). Battle.net musi być zalogowany.

**Dowolna inna gra (plik .exe)**

- **Command**: pełna ścieżka do pliku `.exe` gry.

**Ustawienia dla jednej gry**

- W Thumbcast **przytrzymaj grę → Ustawienia Thumbcast dla aplikacji**. Możesz wybrać profil sterowania, rozdzielczość i liczbę klatek tylko dla tej gry.

**Zamykanie gry z telefonu**

Gry uruchamiane przez Steam albo Battle.net nie zamykają się same. W aplikacji w Sunshine otwórz **Command Preparations → Add** i wpisz `taskkill /IM <plik gry>.exe` w polu **Undo**. Nazwę pliku znajdziesz w Menedżerze zadań → Szczegóły.
Wtedy **Zamknij grę na PC** w menu Thumbcast zamknie też grę.

## Coś nie działa?

- **PC nie ma na liście**: to samo Wi-Fi? Czy Sunshine działa (ikona przy zegarze)? Spróbuj **Dodaj PC** z adresem IP komputera.
- **Gra nie widzi pada**: zainstaluj ViGEmBus, ustaw pada Xbox 360 w Sunshine i zrestartuj PC. Albo przełącz się na profil myszy i klawiatury.
- **Nie działa poza domem**: sprawdź, czy Tailscale jest włączony na PC i na telefonie oraz czy adres jest dodany.
- Szczegóły dla zaawansowanych: [host-setup.md](docs/host-setup.md), [host-apps.md](docs/host-apps.md), [tailscale.md](docs/tailscale.md). Błędy: [GitHub issues](https://github.com/Serwur/thumbcast/issues).

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
