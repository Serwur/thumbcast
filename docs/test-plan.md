# Plan testów i metryki

Źródło: plan projektu, sekcja 9. Wyniki zapisuj w `docs/tests/RRRR-MM-DD-<id>.md` (kopia odpowiedniej tabeli), a podsumowanie w tabeli zbiorczej na końcu tego pliku.

## 1. Sprzęt testowy

**Telefony (minimum)**

| ID | Urządzenie | Cel |
|---|---|---|
| D1 | flagowiec 120 Hz (np. Pixel 9/10 lub Galaxy S2x) | opóźnienia, multitouch |
| D2 | średniak z Androidem 12–13 (np. Galaxy A5x) | typowy użytkownik |
| D3 | starszy z API 26–28 | granica minSdk |
| D4 | z wycięciem i agresywnym gestem „wstecz” (Xiaomi/HyperOS) | gesty systemowe, insety |
| D5 | tablet 11" | inne proporcje |

**Pady:** Xbox Wireless (BT), DualSense (BT), tani generyk.

**Hosty**

| ID | Konfiguracja |
|---|---|
| H1 | Windows 11 + Sunshine stable + Virtual HID Driver z licencją (`gamepad_driver = virtualhid`, `gamepad = xseries`) |
| H2 | Windows 11 + Sunshine stable + ViGEmBus 1.17+, `gamepad_driver = vigembus`, `gamepad = x360` (darmowa ścieżka) |
| H3 | Windows 11 + Sunshine stable bez sterownika pada (tryb bez pada, tylko KBM) |
| H4 | macOS + Sunshine (KBM; pady tylko w pre-release [?]) |

Ścieżki pada opisuje [host-setup.md](host-setup.md), decyzje [DECISIONS.md](DECISIONS.md) (pkt b, c, f). Klient wysyła te same pakiety pada niezależnie od sterownika, więc **T-01, T-02 i T-04 wykonaj na H1 i H2** i porównaj wyniki (różnica = problem sterownika, nie aplikacji). Przed testami w grze na H1/H2 potwierdź, że pad jest widoczny w Windows (`joy.cpl`). Na **H3 i H4 wykonuj scenariusze w profilu „MMO – Klawiatura”**: ruch (WASD, 8 kierunków) zamiast T-01 analogowego, kamera przez RMB-look, T-03 nie dotyczy (brak trybu pada), a Q2 (obrót kamery przez Raw Input bez sterownika) sprawdza krok B7. Aplikacja na hoście bez sterownika pada ma proponować tryb KBM (diagnostyka hosta, E3).

**Sieci:** N1 LAN Wi-Fi 5 GHz; N2 Tailscale direct przez LTE/5G; N3 Tailscale przez DERP (test negatywny).

## 2. Pomiary opóźnień

- **Event→send** w aplikacji: czas od `MotionEvent.eventTime` do wywołania `InputSink`, logowany i zbierany w histogram (overlay „debug wejścia”). Cel E1: p95 < 4 ms.
- **Statystyki strumienia** z upstreamu (`PerfOverlayListener`): dekodowanie, sieć, utracone klatki.
- **Touch-to-photon**: kamera 240 kl./s nagrywa jednocześnie palec i ekran telefonu. Postać skacze (A), a mierzymy liczbę klatek od kontaktu palca do ruchu na ekranie (1 klatka = 4,17 ms). Min. 20 prób na konfigurację. Cel: N1 ≤ 60 ms p50, N2 ≤ 100 ms p50.

## 3. Scenariusze w grze

Gra: MMO z natywnym Gamepad UI (WoW: Forever). W grze włączone: Enable Gamepad UI (Alpha), Show Input Prompts ([host-setup.md](host-setup.md)).

| ID | Scenariusz | Kryterium zaliczenia |
|---|---|---|
| T-01 | Ruch analogowy: chód i bieg (progi Back Pedal / Stick Run), cofanie, strafe. | Płynne przejście chód→bieg; po oderwaniu palca postać staje w ≤ 100 ms także przy utracie pakietów (brak „zawieszonej gałki”). |
| T-02 | Kamera: obrót o 360° jednym przeciągnięciem, małe korekty, brak dryfu. | 360° w jednym geście przy domyślnej czułości; po zatrzymaniu palca kamera stoi (wygaszenie w `decayMs`). |
| **T-03** | **Hybryda: tap-target i pinch-zoom przy włączonym Gamepad UI.** Czy HUD przełącza się na tryb KBM i czy wraca do pada po użyciu gałki? | Procedura w sekcji 4. Wynik decyduje: Pad+ albo Pad-czysty. |
| T-04 | Walka: 3 umiejętności z dwóch zestawów (trzymane LT/RT) w rotacji, rzucanie w ruchu. | Każdy dotyk = dokładnie 1 użycie; modyfikator trzymany równolegle z ruchem i kamerą (3 palce). |
| T-05 | Interakcje: loot (long-press i X), rozmowa z NPC, sprzedaż u vendora, plecak (LB+RB+B). | Wszystkie bez dotykania PC; long-press daje prawy klik, a nie tap i prawy klik. |
| T-06 | Czat: klawiatura ekranowa i `sendUtf8Text` z polskimi znakami („Zażółć gęślą jaźń”). | Tekst w czacie gry bez zniekształceń. |
| T-07 | Pad BT: podłączenie w trakcie sesji (auto-hide), rozłączenie (powrót overlaya), pad i dotyk jednocześnie. | Overlay chowa się w ≤ 1 s; gesty myszy działają dalej; brak podwójnych wejść. |
| T-08 | Odporność: połączenie telefoniczne, obrót ekranu, przejście Wi-Fi→LTE (reconnect upstreamu), blokada ekranu. | Po każdym zdarzeniu wszystkie wejścia zwolnione (0 „stuck input”). |
| T-09 | Polityka: fuzzing wejścia (testy właściwości w CI, 10⁵ sekwencji) i ręczny przegląd presetów. | 0 naruszeń; check `policy-tests` zielony; presety bez akordów i zakazanych typów. |
| T-10 | Tryb bez pada (host H3/H4, profil „MMO – Klawiatura”): ruch WASD, kamera RMB-look, tap, pinch, czat. | Wszystkie akcje działają bez sterownika pada; RMB zwolniony po końcu gestu (0 „stuck input”); wynik Q2 zapisany. |

## 4. Procedura T-03: czy mysz przełącza HUD gry z trybu pada

**Cel.** Ustalić, czy wejście myszy (ruch, klik, kółko) wysłane w trakcie gry padem przełącza interfejs gry z trybu pada (glyphy Xbox, radialne menu, soft-targeting) na tryb klawiatury i myszy, i czy powrót do pada następuje płynnie. Od wyniku zależy domyślny tryb (plan, sekcja 4.2; ryzyko R3).

**Dlaczego to ważne.** Tryb Pad+ miesza pad (ruch, kamera, umiejętności) z myszą (tap na cel, szczypanie = kółko, long-press = prawy klik). Jeśli każde dotknięcie świata gry przełącza HUD, interfejs będzie migotał, a soft-target może się gubić.

### 4.1 Warunki wstępne

- Host H1 lub H2 (pad działa), sieć N1.
- W grze: Enable Gamepad UI (Alpha) i Show Input Prompts włączone; postać w spokojnym miejscu (miasto), bez walki.
- Nagrywanie ekranu telefonu (systemowe) albo kamera skierowana na ekran PC.
- Faza A nie wymaga Thumbcast: wystarczy oryginalny klient Moonlight z OSC (plan, sekcja 13, krok 5). Faza B powtarza test na Thumbcast.

### 4.2 Faza A: upstream (Moonlight OSC + tryb trackpad)

Ustawienia klienta: OSC włączony, „Touchscreen as trackpad” (`checkbox_touchscreen_trackpad`) **włączony** w krokach A1–A6, a **wyłączony** (dotyk jako bezwzględna pozycja myszy) w kroku A7.

| Krok | Akcja | Co obserwować |
|---|---|---|
| A0 | Przejdź kilka kroków lewą gałką OSC, obróć kamerę prawą gałką. | Punkt odniesienia: HUD w trybie pada (glyphy Xbox na paskach akcji). |
| A1 | Bez dotykania gałek przesuń palcem po wolnej części ekranu (ruch myszy, bez kliku). | Czy glyphy zmieniają się na klawiszowe? Czy pojawia się kursor? |
| A2 | Stuknij raz w wolną część ekranu (lewy klik). | Jak wyżej + czy klik zaznacza cel / wchodzi w interakcję. |
| A3 | Stuknij w NPC albo gracza (lewy klik na jednostce). | Czy cel się zaznacza? Czy soft-target pada zostaje, czy znika? |
| A4 | Przewiń dwoma palcami (kółko). | Czy kamera robi zoom? Czy HUD się przełącza? |
| A5 | Zaraz po A1–A4 dotknij lewej gałki i przejdź kilka kroków. | Czy HUD wraca do trybu pada? Po jakim czasie? Czy jest migotanie? |
| A6 | 10 razy naprzemiennie: stuknięcie w świat → ruch gałką (ok. 1 s odstępu). | Liczba przełączeń HUD; czy gra „gubi” wejście pada tuż po kliku. |
| A7 | Powtórz A2–A3 z wyłączonym trybem trackpad (pozycja bezwzględna). | Czy ruch kursora do punktu (bez względnego ruchu) też przełącza HUD? |

### 4.3 Faza B: Thumbcast (profil „MMO – Pad+”)

Wykonuj po wpięciu overlaya (etap E1). Tap = `sendMousePosition` + lewy klik w punkcie; pinch = `sendMouseHighResScroll`; long-press = prawy klik w punkcie.

| Krok | Akcja | Co obserwować |
|---|---|---|
| B1 | Tap na świecie gry (wolna prawa strefa). | Jak A2. |
| B2 | Tap na NPC. | Jak A3. |
| B3 | Szczypanie (zoom in, zoom out). | Czy zoom działa; czy HUD się przełącza. |
| B4 | Long-press na NPC / zwłokach (loot). | Czy interakcja działa; czy HUD się przełącza. |
| B5 | Drag kamery (prawa gałka) zaraz po B1–B4. | Czy HUD wraca do pada. |
| B6 | Walka z mobem: ruch lewą gałką + tap na cel + umiejętności X/Y przez 60 s. | Czy przełączanie przeszkadza w walce (subiektywnie 1–5) + liczba przełączeń z nagrania. |
| B7 | Sprawdzenie Q2: ten sam test na hoście H3 (bez sterownika pada) w trybie KBM, RMB-look. | Czy obrót kamery myszą działa bez licencji Virtual HID. |

### 4.4 Tabela wyników T-03 (do wypełnienia)

Data: ____  Tester: ____  Telefon: ____  Host: ____  Wersja Sunshine: ____  Wersja gry: ____  Sterownik pada: ____

| Krok | HUD przełączył się na KBM? (tak / nie / chwilowo) | Wrócił do pada po gałce? (tak / nie / po … s) | Migotanie? (tak / nie) | Akcja zadziałała? (tak / nie) | Uwagi |
|---|---|---|---|---|---|
| A0 | | | | | |
| A1 | | | | | |
| A2 | | | | | |
| A3 | | | | | |
| A4 | | | | | |
| A5 | | | | | |
| A6 | liczba przełączeń: __ / 10 | | | | |
| A7 | | | | | |
| B1 | | | | | |
| B2 | | | | | |
| B3 | | | | | |
| B4 | | | | | |
| B5 | | | | | |
| B6 | liczba przełączeń: __ ; ocena 1–5: __ | | | | |
| B7 | | | | | |

### 4.5 Interpretacja i decyzja

| Wynik | Decyzja |
|---|---|
| Mysz **nie** przełącza HUD (A1–A4 i B1–B4: „nie”) | **Pad+** jako domyślny tryb bez zmian. |
| Przełącza tylko ruch myszy (A1, A7), a klik w miejscu nie | Pad+ z tapem bez ruchu kursora przed klikiem, jeśli możliwe [?]; inaczej jak niżej. |
| Przełącza, ale HUD wraca natychmiast po gałce i nie przeszkadza w walce (B6 ≥ 4) | Pad+ z ostrzeżeniem w onboardingu; tap-target i pinch włączone. |
| Przełącza i przeszkadza (migotanie, gubienie soft-targetu, B6 ≤ 3) | Wariant **Pad-czysty**: tap wyłączony (cele przez LB/RB), zoom przez natywny skrót gry, klik w UI przez wirtualny kursor pada gry. Gesty myszy domyślnie wyłączone w presecie. |

Wynik zapisz w `docs/tests/RRRR-MM-DD-T-03.md` i zaktualizuj presety (`presets/`) oraz sekcję 4.2 planu.

## 5. Metryki jakości

| Metryka | Cel |
|---|---|
| Event→send p50 / p95 | p95 < 4 ms (E1) |
| Touch-to-photon p50 | LAN ≤ 60 ms; Tailscale direct ≤ 100 ms |
| „Ghost input” (wejście bez dotyku) | 0 |
| „Stuck input” po CANCEL | 0 |
| Crash-free sessions | ≥ 99 % (E2), ≥ 99,5 % (1.0) |
| Czas stworzenia własnego układu | < 5 min bez instrukcji (E2) |
| SUS edytora | ≥ 75 |
| Zużycie baterii ponad samo dekodowanie | < 15 %/h |
| Naruszenia polityki w testach właściwości | 0 w 10⁵ losowych sekwencji |

## 6. Testy automatyczne

| Poziom | Narzędzie | Gdzie | Kiedy |
|---|---|---|---|
| Jednostkowe JVM | kotlin.test, JUnit Jupiter | `:control-model`, `:control-engine` | każdy PR (`jvm-tests`) |
| Właściwości polityki | Kotest property, 10⁵ iteracji | `:control-engine`, klasy `*Policy*` | każdy PR (`policy-tests`, wymagany) |
| Logika View | Robolectric | `:control-android` | każdy PR (po wpięciu) |
| Instrumentacja | Espresso, emulator API 26/34/36 | `:control-android`, `:app` | ręcznie (`emulator-tests.yml`), docelowo PR |
| W grze | ręcznie, ta lista | urządzenia D1–D5 | przed każdym wydaniem |

## 7. Tabela zbiorcza wyników

| Data | Scenariusz | Telefon | Host | Sieć | Wynik (OK / FAIL / częściowo) | Plik z wynikami |
|---|---|---|---|---|---|---|
| | T-01 | | | | | |
| | T-02 | | | | | |
| | T-03 | | | | | |
| | T-04 | | | | | |
| | T-05 | | | | | |
| | T-06 | | | | | |
| | T-07 | | | | | |
| | T-08 | | | | | |
| | T-09 | | | | | |
| | T-10 | | | | | |
