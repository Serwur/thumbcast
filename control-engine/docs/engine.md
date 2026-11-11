# Silnik wejścia `:control-engine`

Czysty moduł Kotlin/JVM bez zależności od Androida. Zamienia zdarzenia dotyku (`TouchEvent`) na wywołania `InputSink`: stan pada, klawisze, mysz i lokalne akcje aplikacji. Najważniejsza reguła jest twarda: **1 dotyk = 1 wejście** (plan, sekcja 6.2). Silnik nie zawiera makr, sekwencji, turbo, opóźnień ani timerów, które same generowałyby naciśnięcia.

Pakiet: `app.thumbcast.engine`. Zależność: `api(project(":control-model"))`.

## Przepływ

```
TouchEvent (overlay Androida)        tick czasu (onTick, tylko gdy dotyka palec)
        │                                      │
        ▼                                      ▼
  InputEngine.onTouch ──────────► ActionPolicy.beginTouchCycle(e, synthetic)
        │                                      │  (otwiera cykl: rodzaj, zdarzenie, wątek, lista palców)
        ▼
  PointerTracker  ── DOWN: HitTest.hit(x, y) ──►  właściciel palca = kontrolka | gest   („capture on down”)
        │
        ├─ kontrolka ─► StickRecognizer | ButtonRecognizer   (slide-to-press: zwolnij → przenieś → wciśnij)
        └─ gest ──────► GestureArbiter ─► Tap | LongPress | CameraDrag | Pinch | EdgeSwipe
                                   │
                                   ▼
                        Intent(pointerId, bindingId, kind, effect)
                                   │
                                   ▼
                      ActionPolicy.emit  ── odrzuca ──► PolicyViolation (log `violations`)
                                   │ przepuszcza
                                   ▼
                      OutputMapper (agregacja pada, deduplikacja klawiszy i myszy)
                                   │
                                   ▼
                      InputSink.padState / key / mouse* / app
                                   │
                ActionPolicy.endCycle(): flush osi (najwyżej 1 padState osi na cykl), koniec cyklu
```

`InputEngine` nie ma referencji do `OutputMapper`. Mapper jest prywatnym polem `ActionPolicy`, więc do sinka nie ma innej drogi niż przez politykę.

## Komponenty

| Komponent | Plik | Odpowiedzialność |
|---|---|---|
| Typy z kontraktu | `Types.kt` | `TouchAction`, `TouchPointer`, `TouchEvent`, `InputSink`, `Clock`, `RectPx`, `Viewport` (+ opcjonalne `safeArea`). |
| `InputEngine` | `InputEngine.kt` | Fasada: `onTouch`, `onTick`, `releaseAll`, `setProfile`, `setViewport`, `violations`. Kieruje zdarzenia do rozpoznawaczy wewnątrz cyklu polityki. |
| `ProfileAdapter` | `ProfileAdapter.kt` | `Profile` → `EngineConfig`. To jedyne miejsce, które czyta szczegółowe pola modelu. |
| `EngineConfig`, `EngineTuning` | `EngineConfig.kt` | Jawnie typowana konfiguracja i stałe strojenia (promień gałki, hit slop, prędkość pełnego wychylenia kamery itd.). |
| `PointerTracker` | `PointerTracker.kt` | `pointerId` → właściciel, do 10 palców. Właściciel ustalany przy DOWN i trzymany do UP/CANCEL. Jedyny transfer to slide-to-press. |
| `HitTest` | `HitTest.kt` | `rect` 0..1 → px względem `safeArea` (albo całego ekranu), lustro `x → 1 - x - w`, z-order (późniejsza kontrolka na liście leży wyżej), strefa aktywacji większa od grafiki (`max(8 px, 20% krótszego boku)`); grafika ma pierwszeństwo przed strefą, w strefie wygrywa najbliższa grafika. |
| `StickRecognizer` + `StickMath` | `StickRecognizer.kt` | Gałka pływająca (środek w miejscu DOWN, promień 15% krótszego boku) albo stała (środek i promień z `rect`). Radialna martwa strefa ze skalowaniem „rescaled radial deadzone” i zewnętrzną strefą nasycenia, krzywa linear/power. Wyjście to gałka pada albo, w KBM, klawisze `kbmKeys` z progiem i 8 sektorami po 45° (zawsze najpierw zwolnienia, potem wciśnięcia). `sprintLock`: histereza nasycenia (niżej). |
| `ButtonRecognizer` | `ButtonRecognizer.kt` | HOLD (stan = palec, wielu palców na jednym przycisku = wspólny stan), LATCH (DOWN przełącza, UP nic nie robi), wyjścia jednorazowe (`App`, `MouseWheel` = 1 ząbek) przy DOWN. |
| `GestureArbiter` | `GestureRecognizers.kt` | Palec wolnej strefy ma stan `PENDING → CAMERA / LONG_PRESS / PINCH / CONSUMED`. Jeden palec dostaje najwyżej jeden gest. |
| `TapRecognizer` | `GestureRecognizers.kt` | UP w czasie ≤ `maxMs` i ruch ≤ `maxMovePx` daje `mousePosition` (przeliczone przez `videoRect` → `videoRefWidth × videoRefHeight`; dotyk w czarnym pasie letterboxu nic nie daje), potem LMB down i up. |
| `LongPressRecognizer` | `GestureRecognizers.kt` | Palec bez ruchu ≥ `minMs`: `mousePosition` i RMB down, RMB up przy UP palca. Rozpoznanie zachodzi tylko w cyklu zdarzenia: MOVE, tick aktywnego palca albo UP po progu (wtedy down i up w jednym cyklu). Long-press zastępuje tap, nigdy nie występują oba. |
| `CameraDragRecognizer` + `CameraMath` | `GestureRecognizers.kt` | Start po przekroczeniu `maxMovePx` w strefie kamery (wolna połowa ekranu; leworęczność zamienia strony). PAD_PLUS: prędkość px/ms, EMA (α = 2/(N+1), N = 2), martwa strefa `deadzonePx` na ramkę 16,67 ms, krzywa, `sensitivity` i `maxDeflection` dają prawą gałkę. Po zatrzymaniu wychylenie wygasa liniowo do 0 w `decayMs` (ticki). KBM / `mouse_look`: RMB down na starcie, `mouseMoveRelative(delta × sensitivity)` z akumulacją reszt ułamkowych, RMB up na końcu. |
| `PinchRecognizer` | `GestureRecognizers.kt` | Drugi palec wolnej strefy, gdy pierwszy jest PENDING albo CAMERA (kamera jest wtedy najpierw zwalniana), uruchamia szczypanie. Zmiana odległości / `pxPerNotch` daje `mouseWheel(n × 120)` z kotwicą (bez dryfu). Rozsuwanie to wartości dodatnie. Bezpiecznik: najwyżej 10 ząbków na zdarzenie. |
| `EdgeSwipeRecognizer` | `GestureRecognizers.kt` | DOWN w pasku przy górnej krawędzi (5% wysokości, min. 16 px) i ruch w dół ≥ 8% wysokości daje `app("menu")`, raz na palec. |
| `ActionPolicy` | `ActionPolicy.kt` | Egzekwuje zasadę 1 dotyk = 1 wejście, loguje naruszenia, jest właścicielem `OutputMapper`. |
| `OutputMapper` | `OutputMapper.kt` | Stan pada → `padState(flagi, osie ±32767, triggery 0..255 jako Byte)`. Przyciski i triggery wysyła od razu, osie zbiera i wysyła raz na cykl, zawsze tylko przy zmianie. Klawisze i przyciski myszy deduplikuje. Flagi = `ControllerPacket` (A=0x1000 …). |

## `sprintLock`: histereza nasycenia (decyzja 11.11.2026)

`StickSpec.sprintLock` (z `StickControl.sprintLock`). Wektor surowy `v` liczony jest od bieżącego środka gałki (pływającej: punkt DOWN, stałej: środek `rect`), `|v| = 1` na obrysie.

- **Wejście:** `|v|` dochodzi do nasycenia, czyli punktu, w którym zwykła krzywa daje 100% (`StickMath.saturated`, ta sama arytmetyka co `StickMath.shape`: `|v| ≥ 1 - outerDeadzone`).
- **W stanie sprint:** moduł wyjścia = 1 (pełny zakres osi jak przy nasyceniu), kierunek = bieżący kąt palca (`StickMath.full`), także gdy kciuk cofnie się do obrysu. W KBM to samo wyjście idzie do 8 sektorów, więc klawisze kierunku zostają wciśnięte poniżej `kbmThreshold`.
- **Wyjście:** `|v| ≤ deadzone` (wyjście 0, dalej zwykła krzywa) albo UP, CANCEL, `releaseAll` (zero jak dotąd; `onUp`/`reset` czyszczą stan).

Zgodność z 1 dotyk = 1 wejście: stan należy do aktywnego palca gałki i ginie razem z nim. Zmienia się wyłącznie w `update`, czyli w cyklu DOWN/MOVE tego palca; tick nie dotyka gałek. Każda zmiana wyjścia jest intencją `HOLD` tego samego wiązania (`control:<id>`) z przyczyną w zdarzeniu dotyku, więc reguły R1–R9 działają bez nowych wyjątków. `sprintLock = false` wywołuje dokładnie to samo `StickMath.shape` co wcześniej.

## Jak ActionPolicy egzekwuje 1 dotyk = 1 wejście

Każde wyjście jest **intencją** z trzema atrybutami: palec (`pointerId`), logiczne wejście (`bindingId`, np. `control:a`, `gesture:camera`) i rodzaj (`HOLD`, `LATCH`, `ONE_SHOT`, `ONE_SHOT_TIMED`). Polityka prowadzi rejestr *właścicieli* każdego trwałego stanu (przycisk pada, trigger, gałka, klawisz, przycisk myszy) oraz stan każdego palca.

Reguły (numeracja jak w komentarzu klasy):

1. **R1 – przyczyna.** Wyjście musi powstać w otwartym cyklu (`onTouch`, `onTick`, `releaseAll`) i na tym samym wątku. Wywołanie z timera, z korutyny albo z innego wątku po zakończeniu cyklu jest odrzucane („brak przyczyny”).
2. **R2 – palec w zdarzeniu.** Wciśnięcie musi dotyczyć palca, który jest w bieżącym `TouchEvent`.
3. **R3 – żywy palec.** Wciśnięcie wymaga palca po DOWN, przed UP/CANCEL. W cyklu UP dozwolone jest tylko wejście jednorazowe (tap albo long-press rozpoznany przy UP). W cyklu CANCEL nie ma żadnych wciśnięć.
4. **R4 – jeden palec, jedno wejście.** Palec może zmienić wiązanie tylko wtedy, gdy poprzednie już niczego nie trzyma. Slide-to-press musi więc najpierw zwolnić stary przycisk.
5. **R5 – gest jednorazowy raz.** Tap, long-press, edge swipe i przycisk App dają najwyżej jedną krawędź wciśnięcia na cykl życia palca i wykluczają się nawzajem.
6. **R6 – LATCH tylko przy DOWN** tego samego palca. Stan LATCH należy do kontrolki, nie do palca, więc przeżywa UP i nie blokuje palca.
7. **R7 – czas sam nie naciska.** W cyklu TICK dozwolone są tylko long-press (`ONE_SHOT_TIMED`, rozpoznanie gestu trzymanego palca), zwolnienia i wygaszanie gałki (moduł nie rośnie, znak się nie zmienia). Bez dotykającego palca `onTick` w ogóle nie otwiera cyklu.
8. **R8 – zwalnia właściciel.** Palec nie może zwolnić cudzego wejścia. Stan wspólny (dwa palce na A) schodzi na wyjściu dopiero przy ostatnim właścicielu.
9. **R9 – nic nie zostaje po palcu.** Przy UP polityka wymusza zwolnienie wszystkiego, co palec jeszcze trzyma (poza LATCH), i zapisuje to jako naruszenie (błąd rozpoznawacza).

`CANCEL` i `releaseAll()` czyszczą rejestr i wysyłają zerowy stan: puszczają klawisze i przyciski myszy, wysyłają zerowy `padState` (jeśli pad był używany) i resetują LATCH. Przyczyną jest odpowiednio zdarzenie CANCEL albo cykl `RELEASE_ALL`.

Wyjątki z planu 6.2:
- *Wygaszanie kamery do zera* jest dozwolone w cyklu TICK (R7).
- *Retransmisja identycznego stanu* (25/50/75 ms) zostaje w adapterze upstreamu (`HostPadReporterImpl` w `:control-android`). Silnik jej nie powtarza, bo nie zmienia ona stanu.

Naruszenia nie trafiają do sinka. Lądują w `InputEngine.violations` (ostatnie 1000) i liczniku `violationCount`. W poprawnym silniku lista jest pusta, a polityka działa jako siatka bezpieczeństwa i detektor błędów. Testy właściwości wymagają, żeby była pusta.

Każde wywołanie sinka może dostać przyczynę (`EmitCause`: id cyklu, rodzaj, akcja, czas zdarzenia, palec, wiązanie, rodzaj wiązania), jeśli sink implementuje `EmitCauseListener`. Tak działa `FakeInputSink` w testach; overlay debug może z tego korzystać.

## Testy (`src/test`)

- `Fakes.kt`: `FakeClock`, `FakeInputSink`, czyli rejestr wywołań z przyczyną, śledzenie stanu, krawędzie wciśnięcia i maksimum jednocześnie wciśniętych wejść.
- `TestSupport.kt`: `testProfile()` budowany inline (bez presetów z dysku), stałe punktów i `Rig` (silnik, sink i zegar).
- `StickRecognizerTest`: martwe strefy, brak skoku, nasycenie, krzywa power, 8 kierunków, gałka pływająca, capture on down, WASD w KBM, `sprintLock` (wejście tylko przy nasyceniu, 100% z kierunkiem za palcem po cofnięciu, wyjście w martwej strefie i ponowne wejście, UP/CANCEL/`releaseAll` → 0 bez przenoszenia na kolejny dotyk, brak wyjścia na tickach, gałka stała, `false` bez zmian, KBM).
- `ButtonAndHitTestTest`: HOLD, flagi, LATCH (także po zmianie viewportu), wspólny stan, slide-to-press (X → 0 → A), App, klawisz w Pad+, strefa aktywacji, z-order, `safeArea`, lustro (w silniku i z `HandednessTransform` bez podwójnego odbicia).
- `GestureRecognizersTest`: tap (układ wideo, letterbox), tap kontra long-press kontra drag, long-press na ticku i przy UP, brak wyjścia bez palca, kamera Pad+ (kierunek, wygaszanie, monotoniczność krzywej), RMB-look z resztami ułamkowymi, pinch (ząbki, kotwica, przerwanie kamery), edge swipe, kilka palców naraz z CANCEL.
- `ActionPolicyTest`: każda reguła R1–R9 wywołana bezpośrednio.
- `KnownRisksTest`: ryzyka z `docs/HANDOFF.md`. Gałkę RIGHT razem z kamerą `pad_right_stick` i każdy `chord` walidator od 11.11.2026 odrzuca. Testy sprawdzają to odrzucenie i opisują zachowanie silnika na takim profilu niewalidowanym. Poza tym rozstrzygnięty `sprintLock` (histereza) i long-press przy tickach co klatkę.
- `ActionPolicyPropertyTest` (Kotest `checkAll`, liczba iteracji z `thumbcast.policy.iterations`). Strumienie są poprawne albo chaotyczne: CANCEL, podwójny DOWN, UP bez DOWN, nieznane palce w MOVE, cofający się czas, `releaseAll` i zmiana viewportu w trakcie. Sprawdzane są tryby Pad+, Pad+ ze slide-to-press i KBM; mniej więcej połowa strumieni (wybór z ziarna) ma gałkę z `sprintLock = true`. Właściwości:
  - każda krawędź ma przyczynę z tego samego zdarzenia i żywego palca;
  - jeden dotyk daje najwyżej jedną krawędź, więc liczba krawędzi ≤ liczba DOWN;
  - tick nie zmienia stanu poza wygaszaniem;
  - żaden palec nie trzyma dwóch wiązań, a jednoczesnych wejść pada jest najwyżej tyle, ile palców;
  - nie ma naruszeń;
  - po `releaseAll()` stan jest zerowy;
  - zegar i ticki bez dotyku nie dają żadnego wyjścia.

  Ziarno jest w komunikacie błędu.

Klasy testów polityki mają w nazwie `Policy`, więc obejmuje je job CI `policy-tests` (`--tests '*Policy*'`).

## Znane ograniczenia

- **Kod nie był kompilowany ani uruchamiany** (brak sieci i `kotlinc` w środowisku tworzenia). Sprawdzono go tylko skryptem statycznym: nawiasy, importy, typy modelu.
- Akordy (`ButtonControl.chord`) są ignorowane. Przycisk daje tylko `output`. Akordów nie będzie: od 11.11.2026 odrzuca je walidator (FAIR_PLAY.md).
- W KBM jeden palec na gałce może trzymać dwa klawisze naraz (np. W+D dla skosu). To jedno logiczne wejście (`control:move`) i jedyny wyjątek od „jedna krawędź na dotyk”: wynika z 8 kierunków WASD (plan 4.2).
- Wygaszanie kamery postępuje tylko przy tickach albo kolejnych zdarzeniach. Jeśli overlay przestanie wołać `onTick` przy nieruchomym palcu, gałka zostaje wychylona do następnego zdarzenia albo UP (ograniczenie bezpieczne: zero przy UP/CANCEL jest zawsze wysyłane).
- Long-press bez ticków jest rozpoznawany dopiero przy UP (RMB down i up w jednym cyklu), czyli bez możliwości trzymania RMB.
- Strefa kamery to połowa ekranu po stronie `zone`. Palec, który ruszył po drugiej stronie, jest „zużyty” (nic nie robi do UP).
- Edge swipe obsługuje tylko górną krawędź, a jego pasek liczony jest od górnej krawędzi overlaya, nie od `safeArea`.
- Stałe w `EngineTuning` (promień gałki 15%, 1,5 px/ms dla pełnej kamery, hit slop) są wstępne i do strojenia na urządzeniach (plan 9, T-01/T-02).
- `setViewport` z inną geometrią podczas dotyku robi `releaseAll` zamiast przeliczać pozycje palców.
- Polityka zakłada jeden wątek (UI). Wywołania z innego wątku są odrzucane, a nie synchronizowane.
