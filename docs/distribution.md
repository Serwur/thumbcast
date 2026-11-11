# Dystrybucja

Kolejność kanałów: **GitHub Releases → F-Droid → Google Play**. Stan wiedzy: listopad 2026.

## 0. Wymagania wspólne

- Własne, unikalne `applicationId`, np. `io.github.<login>.thumbcast`. Nie `com.limelight` i nie domena, której nie kontrolujemy (F-Droid odrzuca ID podszywające się pod cudzą domenę).
- Nazwa i ikona bez „WoW”, „Blizzard”, „Moonlight” ([naming.md](naming.md)).
- APK instaluje się obok oryginalnego Moonlight (inne `applicationId`, inne authorities providerów).
- Brak Firebase, Crashlytics i Play Services (sprawdza to job `lint` w CI).
- Tagi wydań odpowiadają źródłom (GPLv3).

## 1. Android Developer Verification

Od **30.09.2026** Android wymaga weryfikacji deweloperów dla aplikacji instalowanych spoza Play (sideload) w Brazylii, Indonezji, Singapurze i Tajlandii, a **globalnie od 2027**.

- Rejestrujemy aplikację (nazwę pakietu i klucz podpisu) w **Android Developer Console** od początku projektu. Jest osobna ścieżka dla aplikacji open source.
- Konto „limited distribution”: do 20 urządzeń, bez opłaty i bez dokumentu tożsamości. Wystarczy dla testerów bety.
- „Advanced flow” pozwala power userom instalować niezweryfikowane aplikacje.
- F-Droid podpisuje APK własnym kluczem, więc trzeba ustalić, jak weryfikacja obejmie build F-Droid [?] (F-Droid opublikował list otwarty przeciw weryfikacji: https://f-droid.org/is/2026/02/24/open-letter-opposing-developer-verification.html). Do sprawdzenia przed E3.

Źródło: https://developer.android.com/developer-verification

## 2. GitHub Releases (E0–E3)

- Workflow `.github/workflows/release.yml` (tag `v*`): testy JVM i polityki, podpisany APK z sekretów, `SHA256SUMS.txt`, wydanie jako pre-release.
- Changelog w opisie wydania. Odcisk certyfikatu podpisu w README.
- Opcjonalnie Obtainium (aktualizacje z GitHub Releases).
- Klucz podpisu: w sejfie offline + kopia zapasowa; w GitHub tylko jako sekret base64. Utrata klucza = brak aktualizacji dla obecnych użytkowników.

## 3. F-Droid (E3)

- Wymogi: budowalne ze źródeł, brak niewolnych zależności, brak GMS. Moonlight jest w F-Droid jako `com.limelight` (GPL-3.0-or-later), co potwierdza wykonalność builda z NDK i submodułem `moonlight-common-c`.
- Metadane w układzie fastlane: `fastlane/metadata/android/<locale>/` (`title.txt`, `short_description.txt`, `full_description.txt`, później `changelogs/<versionCode>.txt` i `images/`).
- Zgłoszenie: merge request do `fdroiddata` z plikiem `metadata/<applicationId>.yml` (Builds, `subdir: app`, `submodules: true`, wersja NDK).
- Reproducible builds [?]: rozważyć, żeby F-Droid mógł publikować APK podpisany naszym kluczem.
- Anti-features: spodziewamy się braku. Uwaga: F-Droid może oznaczyć „NonFreeNet”, jeśli opis sugeruje wymóg zamkniętej usługi. Sunshine i Tailscale (klient open source) nie powinny tego wywołać [?].

Źródło: https://f-droid.org/docs/Inclusion_Policy/

## 4. Google Play (E4)

- targetSdk 36 (wymóg Play od 31.08.2026).
- **Data safety:** brak zbierania i udostępniania danych. Jedyne połączenia sieciowe idą do własnego hosta użytkownika. Tailscale działa po stronie systemu, poza aplikacją.
- Brak samoaktualizacji poza Play. W wariancie Play nie ma mechanizmu pobierania APK.
- Foreground service tylko jeśli konieczny (upstream go nie wymaga do streamingu [?]).
- Tor: test wewnętrzny → zamknięty → produkcyjny.

### Uwagi o listingu (polityka Device and Network Abuse)

Polityka zabrania m.in. „game cheating apps that affect the gameplay of other apps” i używania usług niezgodnie z ich warunkami. Dlatego:
- pozycjonujemy aplikację jako **uniwersalny klient streamingu z konfigurowalnym sterowaniem dotykowym**;
- presety nazywamy gatunkowo („MMO”, „Akcja 3D”), bez nazwy konkretnej gry;
- **nazwy gry nie ma** w tytule, krótkim opisie, ikonie ani grafice promocyjnej;
- zrzuty ekranu: pulpit, gry open source (np. Veloren, SuperTuxKart) albo edytor układu na szachownicy; **bez zrzutów z gier Blizzarda**;
- w pełnym opisie wolno napisać „bazuje na Moonlight (GPLv3)” i wymienić zasadę 1 dotyk = 1 wejście;
- link do FAIR_PLAY.md i informacja „niezatwierdzone przez żadnego wydawcę gier”.

Źródła: https://support.google.com/googleplay/android-developer/answer/9888379 ; https://developer.android.com/google/play/requirements/target-sdk

## 5. Checklista przed wydaniem

- [ ] Plan testów: scenariusze z [test-plan.md](test-plan.md) zaliczone na min. 2 telefonach.
- [ ] `policy-tests` zielony na tagu.
- [x] LICENSE zawiera pełny tekst GPLv3, zgodny z gnu.org (sprawdzone 11.11.2026).
- [ ] NOTICE: aktualne biblioteki i licencje.
- [ ] Metadane fastlane PL i EN bez znaków towarowych Blizzarda.
- [ ] `SHA256SUMS.txt` i podpis zweryfikowane (`apksigner verify`).
- [ ] Aplikacja zarejestrowana w Android Developer Console.
