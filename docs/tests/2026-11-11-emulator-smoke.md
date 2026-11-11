# Smoke test na emulatorze, 11.11.2026

Zastępstwo za krok 4 z `docs/HANDOFF.md` do czasu, aż będzie telefon i host z Sunshine. Na tej maszynie nie ma hosta ani fizycznego telefonu, więc streamingu i nakładki w sesji (`Game`) nie testowano.

## Środowisko

- Emulator: AVD `Medium_Phone_API_37.0`, Android 17 (API 37), obraz x86_64, 1080x2400. Start z `-no-snapshot-save -no-audio -no-boot-anim`.
- Build: `:app:assembleNonRootDebug` z commita `e8945c1b`, JDK z Android Studio (jbr). Wynik: BUILD SUCCESSFUL, APK 27,3 MB.
- APK: pakiet `io.github.serwur.thumbcast.debug`, etykieta „Thumbcast (Debug)”. W APK są biblioteki `libmoonlight-core.so` dla arm64-v8a, armeabi-v7a, x86 i x86_64 oraz `assets/presets/mmo-keyboard.thumbcast.json` i `assets/presets/mmo-pad-plus.thumbcast.json`.
- Sterowanie UI: `adb shell input`, `uiautomator dump`, zrzuty `adb exec-out screencap -p` (poza repo).

## Wyniki

| # | Sprawdzenie | Wynik |
|---|---|---|
| 1 | Instalacja APK (`adb install -r`) | OK („Success”) |
| 2 | Start aplikacji, brak crasha | OK |
| 3 | Kategoria „Touch controls” w ustawieniach i jej pozycje | OK |
| 4 | Lista profili: „MMO – Pad+”, „MMO – Keyboard” | OK |
| 5 | Przełączenie warstwy na legacy: OSC włączony, profil wyszarzony | OK |
| 6 | Powrót z legacy do Thumbcast: „Enable vibration” aktywne | BŁĄD (UI, patrz niżej) |
| 7 | Polskie napisy przy locale pl-PL | OK |
| 8 | Dodanie PC po IP 10.0.2.2 bez hosta | OK (komunikat błędu, brak crasha) |
| 9 | `:control-android:connectedDebugAndroidTest` | OK, 1/1 test |

### 1–2. Instalacja i start

Po starcie widać ekran główny upstreamu: ikony ustawień, pomocy i „+”, kręcący się wskaźnik i tekst „Searching for host PCs on your local network…”. Proces żyje (`pidof` zwraca PID), bufor `logcat -b crash` pusty, brak wpisów `FATAL EXCEPTION` dla pakietu.

W logu jest `SQLiteCantOpenDatabaseException` dla `databases/computers.db` z `LegacyDatabaseReader.migrateAllComputers`. To zachowanie upstreamu przy pierwszym uruchomieniu (brak starej bazy do migracji), wyjątek jest złapany i nie wpływa na działanie.

### 3–4. Kategoria „Touch controls”

Kategoria jest między „Input Settings” a „On-screen Controls Settings”. Domyślne wartości po świeżej instalacji:

- „Control layer” = „Thumbcast (multitouch, profiles)”
- „Control profile” = „MMO – Pad+”
- „Input debug overlay” niezaznaczone, opis „Shows active pointers, sent pad flags and touch-to-send latency”
- W „On-screen Controls Settings”: „Show on-screen controls” wyszarzone i niezaznaczone, „Enable vibration” aktywne i zaznaczone, pozostałe opcje OSC wyszarzone.

Okno wyboru profilu ma dwie pozycje: „MMO – Pad+” (zaznaczona) i „MMO – Keyboard”. Wybór „MMO – Keyboard” zapisuje się w preferencjach (`list_thumbcast_profile` = `mmo-keyboard.thumbcast.json`, sprawdzone przez `run-as ... cat shared_prefs/...`).

### 5. Przełączenie na legacy

Po wybraniu „Classic on-screen controller”: „Control profile” wyszarzony, „Show on-screen controls” aktywne. „Enable vibration” zależy wtedy znowu od OSC (wyszarzone przy odznaczonym OSC, aktywne po zaznaczeniu). Zgodnie z logiką w `StreamSettings`.

### 6. Błąd: powrót z legacy do Thumbcast zostawia wyszarzone „Enable vibration”

Kroki: w ustawieniach przełączyć warstwę na „Classic on-screen controller”, potem z powrotem na „Thumbcast”, bez wychodzenia z ekranu ustawień.

Oczekiwane: „Enable vibration” aktywne (komentarz w kodzie: wibracja działa też w trybie Thumbcast). Faktycznie: wyszarzone (`enabled=false` w `uiautomator dump`). Występuje zarówno przy zaznaczonym, jak i odznaczonym OSC. Po wyjściu z ustawień i ponownym wejściu opcja jest znowu aktywna, więc to tylko stan UI; zapisana wartość `checkbox_vibrate_osc` się nie zmienia. Brak crasha.

Prawdopodobna przyczyna (blok THUMBCAST w `StreamSettings.java`, `android.preference`): `oscPref.setEnabled(false)` powiadamia zależne preferencje i ustawia w `vibrateOscPref` „zależność niespełniona”. Następne `setDependency(null)` tylko wyrejestrowuje zależność i nie przywraca tej flagi. Zmiana kolejności nie wystarczy (przy OSC odznaczonym w legacy flaga jest już fałszywa). Propozycja: po `setDependency(null)` wywołać `vibrateOscPref.onDependencyChanged(oscPref, false)`. Kodu nie zmieniałem.

### 7. Polskie napisy

Locale aplikacji ustawione przez `cmd locale set-app-locales io.github.serwur.thumbcast.debug --locales pl-PL`. W ustawieniach widać: „Sterowanie dotykowe”, „Warstwa sterowania” = „Thumbcast (multitouch, profile)”, „Profil sterowania” = „MMO – Klawiatura”, „Nakładka debug wejścia” z opisem „Pokazuje aktywne wskaźniki, wysłane flagi pada i opóźnienie dotyk→wysyłka”. Ekran główny też po polsku. Uwaga poboczna: „Show Guide Button” i jego opis zostają po angielsku, to brak tłumaczenia w upstreamie, nie w warstwie Thumbcast.

### 8. Dodanie PC po IP bez hosta

„+” → „Add PC Manually”, wpisane `10.0.2.2` (adres hosta widziany z emulatora, Sunshine nie działa), OK. W logu `LimeLog: Starting parallel poll for null (null, null, 10.0.2.2:47989, null)`, po kilku sekundach okno „Connection Error”: „Unable to connect to the specified computer. Make sure the required ports are allowed through the firewall.” z przyciskami HELP i OK. Po zamknięciu okna proces działa dalej (ten sam PID), bufor crash pusty.

### 9. Test instrumentacyjny

`./gradlew :control-android:connectedDebugAndroidTest` (ANDROID_SERIAL=emulator-5554): BUILD SUCCESSFUL, 1 test, 0 błędów, 0 pominiętych. `app.thumbcast.android.MultiTouchInstrumentedTest.twoFingersStickAndButtonAtOnce` przeszedł w 0,081 s, za pierwszym razem i bez zmian w teście ani w konfiguracji (aliasy `androidx-test-runner` i `androidx-test-ext-junit` już działały).

## Zrzuty ekranu (opis, pliki nie są w repo)

1. Ekran główny po starcie: ciemne tło, ikony ustawień, pomocy i „+” u góry, wskaźnik ładowania i tekst o szukaniu hostów.
2. Ustawienia, sekcja „Touch controls” w domyślnym stanie: nagłówek w kolorze akcentu, trzy pozycje Thumbcast, poniżej wyszarzone opcje OSC poza „Enable vibration”.
3. Okno „Control profile” z dwoma pozycjami radio.
4. Stan legacy: profil wyszarzony, „Show on-screen controls” aktywne.
5. Stan po powrocie do Thumbcast: „Enable vibration” wyszarzone (błąd z pkt 6).
6. Ustawienia po polsku, sekcja „Sterowanie dotykowe”.
7. Okno „Connection Error” nad ekranem „Add PC Manually” z wpisanym 10.0.2.2.

## Czego nie sprawdzono

- Parowanie z Sunshine, stream, wejście pada po stronie hosta (krok 4 HANDOFF w pełnej wersji, scenariusze z `docs/test-plan.md` na hostach H1–H4).
- Nakładka `ThumbcastOverlay` w aktywności `Game`: rysowanie, multitouch na prawdziwym ekranie, retransmisja stanu pada w `HostPadReporterImpl`, wymuszone `onscreenController=true`, landscape, rumble, PiP.
- Wczytywanie presetów z assetów w czasie sesji i komunikat `thumbcast_profile_error` przy uszkodzonym profilu.
- Nakładka debug wejścia (działa tylko w sesji).
- Wydajność i opóźnienie dotyk→wysyłka na prawdziwym telefonie (emulator x86_64 nie jest miarodajny).
- Build release i podpisywanie.

Po teście dane aplikacji na emulatorze wyczyszczono (`pm clear`), APK zostało zainstalowane.
