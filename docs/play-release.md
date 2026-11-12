# Wydanie w Google Play (12.11.2026)

Stan repo: wersja `0.1.0` (`versionCode` 1000, schemat `major * 1000000 + minor * 1000 + patch`), tylko języki
angielski i polski (`localeFilters` w `app/build.gradle`, `xml/thumbcast_locales_config.xml`, lista języków
w ustawieniach), AAB z `:app:bundleNonRootRelease` w `release.yml`. Właściciel przetestował aplikację na swoim
telefonie (12.11.2026).

Sprawdzone lokalnie 12.11.2026 (testowy klucz): AAB podpisany (`jarsigner -verify`: jar verified), biblioteki
64-bitowe (`arm64-v8a`, `x86_64`) wyrównane do 16 KB, w APK tylko konfiguracje językowe domyślna (EN) i `pl`.

## 1. Repo (raz)

1. Klucz: `release.jks` trzymaj poza repo, z kopią zapasową. Ten sam klucz podpisuje APK na GitHubie i jest
   kluczem w Play (punkt 3), więc wersje z obu źródeł mają ten sam podpis.
2. Sekrety Actions (`THUMBCAST_KEYSTORE_BASE64`, `THUMBCAST_KEYSTORE_PASSWORD`, `THUMBCAST_KEY_ALIAS`,
   `THUMBCAST_KEY_PASSWORD`), opis w nagłówku `.github/workflows/release.yml`.
3. GitHub Pages: Settings > Pages > Source: GitHub Actions (workflow `pages.yml`, katalog `site/`). Adresy:
   `https://serwur.github.io/thumbcast/` i `https://serwur.github.io/thumbcast/pl/`. Polityka prywatności dla Play:
   `https://serwur.github.io/thumbcast/#privacy`.
4. Wydanie: tag `v0.1.0` (musi równać się `versionName`). `release.yml` buduje APK i AAB, liczy sumy SHA-256
   i tworzy GitHub Release. AAB z wydania wgrywasz do Play.

## 2. Konto w Play Console

- Opłata 25 USD jednorazowo, weryfikacja tożsamości.
- Typ konta: dla siebie albo organizacja (JDG z numerem D-U-N-S). Konto osobiste założone po 13.11.2023 musi przed
  wydaniem produkcyjnym przeprowadzić test zamknięty z co najmniej 12 testerami przez 14 dni (stan wiedzy
  z 12.11.2026, sprawdź w Play Console). Typu konta nie da się później zmienić bez przenoszenia aplikacji.
- W UE (DSA) Play pyta o status przedsiębiorcy; przedsiębiorca ma publiczny adres, e-mail i telefon.

## 3. Aplikacja w Play Console

- Nazwa: Thumbcast. Pakiet: `io.github.serwur.thumbcast` (nie da się go zmienić po pierwszym wgraniu).
- Podpis (Play App Signing): „Use my own key”, czyli wgraj klucz z `release.jks` (narzędzie PEPK z instrukcji
  w Play Console), żeby podpis w Play był taki sam jak na GitHubie. Z kluczem wygenerowanym przez Google wersje
  z Play i z GitHuba się wykluczają (trzeba odinstalować przy zmianie źródła).
- Najpierw ścieżka testów wewnętrznych (do 100 osób, bez recenzji), potem zamknięta, potem produkcja.

## 4. Formularze

- Polityka prywatności: adres z punktu 1.3.
- Bezpieczeństwo danych (Data safety): aplikacja nie zbiera i nie udostępnia danych z listy Google; dane zostają
  na telefonie; brak konta. Połączenia: własny PC użytkownika i dwa serwery pomocnicze projektu Moonlight (STUN
  `stun.moonlight-stream.org`, test połączenia `android.conntest.moonlight-stream.org`), opisane w polityce.
- Reklamy: nie. Grupa docelowa: 18+ (albo 13+), nie dzieci. Ocena treści: kwestionariusz IARC (aplikacja
  narzędziowa, bez treści generowanych przez użytkowników, bez czatu).
- Dostęp do aplikacji: bez logowania, ale wymaga PC z Sunshine; w uwagach dla recenzji opisz to i podlinkuj
  stronę z punktu 1.3 (najlepiej z krótkim filmem).
- Kategoria: Narzędzia albo Rozrywka.

## 5. Strona w sklepie

- Grafiki: `store/icon-512.png` (ikona), `store/feature-graphic-pl.png` i `-en.png` (1024x500); generuje je
  `python -I tools/render_store_assets.py`. `store/icon-1024.png` do innych zastosowań.
- Zrzuty ekranu (min. 2 na telefon): ekrany Thumbcast (kreator, lista PC, edytor układu, menu w grze) na tle
  pulpitu, Steam Big Picture albo gry open source. Bez zrzutów z gier Blizzarda i bez nazw gier w tekstach
  (ryzyko odrzucenia za własność intelektualną i polityka „game cheating apps”, `docs/PLAN.md` R7).
- Opis: krótki (do 80 znaków) i pełny (do 4000), neutralny: klient streamingu gier z PC ze sterowaniem dotykowym.
  Wolno napisać, że bazuje na Moonlight (GPLv3). Treść pomocnicza: `site/index.html`, `site/pl/index.html`.
