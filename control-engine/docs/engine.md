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
        └─ gest ──────► FreeZoneGestures (jeden na profil, `gestures.freeZone`)
                          ├─ camera:   GestureArbiter ─► Tap | tap 2 palcami RMB | LongPress | CameraDrag | Pinch | EdgeSwipe
                          ├─ touchpad: TouchpadGestures ─► kursor | tap LMB | tap 2 palcami RMB | przytrzymanie+LMB | kółko V/H | EdgeSwipe
                          ├─ direct:   DirectMouseGestures ─► tap = pozycja+LMB | tap 2 palcami = pozycja+RMB | przeciągnięcie = pozycja+kółko V i H | EdgeSwipe
                          └─ none:     EdgeSwipeOnlyGestures ─► tylko EdgeSwipe (reszta dotyku nic nie wysyła)
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
| `InputEngine` | `InputEngine.kt` | Fasada: `onTouch`, `onTick`, `releaseAll`, `setProfile`, `setViewport`, `violations`. Kieruje zdarzenia do rozpoznawaczy wewnątrz cyklu polityki. W MOVE najpierw aktualizuje pozycje wszystkich palców zdarzenia, a dopiero potem woła rozpoznawacze (gesty dwóch palców widzą spójny stan). |
| `ProfileAdapter` | `ProfileAdapter.kt` | `Profile` → `EngineConfig`. To jedyne miejsce, które czyta szczegółowe pola modelu. |
| `EngineConfig`, `EngineTuning` | `EngineConfig.kt` | Jawnie typowana konfiguracja i stałe strojenia (promień gałki, hit slop, prędkość pełnego wychylenia kamery itd.). |
| `PointerTracker` | `PointerTracker.kt` | `pointerId` → właściciel, do 10 palców. Właściciel ustalany przy DOWN i trzymany do UP/CANCEL. Jedyny transfer to slide-to-press. |
| `HitTest` | `HitTest.kt` | `rect` 0..1 → px względem `safeArea` (albo całego ekranu), lustro `x → 1 - x - w`, z-order (późniejsza kontrolka na liście leży wyżej), strefa aktywacji większa od grafiki (`max(8 px, 20% krótszego boku)`); grafika ma pierwszeństwo przed strefą, w strefie wygrywa najbliższa grafika. |
| `StickRecognizer` + `StickMath` | `StickRecognizer.kt` | Gałka pływająca (środek w miejscu DOWN, promień 15% krótszego boku) albo stała (środek i promień z `rect`). Radialna martwa strefa ze skalowaniem „rescaled radial deadzone” i zewnętrzną strefą nasycenia, krzywa linear/power. Wyjście to gałka pada albo, w KBM, klawisze `kbmKeys` z progiem i 8 sektorami po 45° (zawsze najpierw zwolnienia, potem wciśnięcia). `sprintLock`: histereza nasycenia (niżej). |
| `ButtonRecognizer` | `ButtonRecognizer.kt` | HOLD (stan = palec, wielu palców na jednym przycisku = wspólny stan), LATCH (DOWN przełącza, UP nic nie robi), wyjścia jednorazowe (`App`, `MouseWheel` = 1 ząbek) przy DOWN. |
| `FreeZoneGestures` | `FreeZoneGestures.kt` | Interfejs właściciela wolnej strefy (`onDown`, `onMove`, `onUp`, `reset`) i fabryka `create` według `EngineConfig.freeZone`. Jedna implementacja na profil, więc jedno przeciągnięcie nigdy nie daje naraz kamery i myszy. |
| `GestureArbiter` | `GestureArbiter.kt` | `freeZone = camera` (tabela Pad+). Palec wolnej strefy ma stan `PENDING → CAMERA / LONG_PRESS / PINCH / TWO_TAP / CONSUMED`. Jeden palec dostaje najwyżej jeden gest. Tap dwoma palcami (12.11.2026) opisany niżej. |
| `TouchpadGestures` | `TouchpadGestures.kt` | `freeZone = touchpad`, cała wolna strefa (bez podziału na połowy). Stan palca `PENDING → POINTER / DRAG / TWO → SCROLL / CONSUMED`. Szczegóły niżej. |
| `EdgeSwipeOnlyGestures` | `EdgeSwipeOnlyGestures.kt` | `freeZone = none` (12.11.2026): wolna strefa nic nie wysyła; jedynym gestem jest edge swipe z góry (gdy włączony). Szczegóły niżej. |
| `DirectMouseGestures` | `DirectMouseGestures.kt` | `freeZone = direct` (12.11.2026): mysz bezpośrednia na całej wolnej strefie. Stan palca `PENDING → SCROLL / TWO / CONSUMED`. Szczegóły niżej. |
| `TapRecognizer` | `TapRecognizer.kt`, `VideoMapper.kt` | UP w czasie ≤ `maxMs` i ruch ≤ `maxMovePx` daje `mousePosition` (przeliczone przez `videoRect` → `videoRefWidth × videoRefHeight`; dotyk w czarnym pasie letterboxu nic nie daje), potem LMB down i up. |
| `LongPressRecognizer` | `LongPressRecognizer.kt` | Palec bez ruchu ≥ `minMs`: `mousePosition` i RMB down, RMB up przy UP palca. Rozpoznanie zachodzi tylko w cyklu zdarzenia: MOVE, tick aktywnego palca albo UP po progu (wtedy down i up w jednym cyklu). Long-press zastępuje tap, nigdy nie występują oba. |
| `CameraDragRecognizer` (interfejs) = `StickCameraDrag` / `MouseLookCameraDrag`, `CameraMath`, `RelativeAccumulator` | `CameraDragRecognizer.kt`, `RelativeAccumulator.kt` | Start po przekroczeniu `maxMovePx` w strefie kamery (wolna połowa ekranu; leworęczność zamienia strony). `StickCameraDrag` (PAD_PLUS, `pad_right_stick`): prędkość px/ms, EMA (α = 2/(N+1), N = 2), martwa strefa `deadzonePx` na ramkę 16,67 ms, krzywa, `sensitivity` i `maxDeflection` dają prawą gałkę. Po zatrzymaniu wychylenie wygasa liniowo do 0 w `decayMs` (ticki). `MouseLookCameraDrag` (KBM / `mouse_look`): RMB down na starcie, `mouseMoveRelative(delta × sensitivity)` z akumulacją reszt ułamkowych (`RelativeAccumulator`), RMB up na końcu. |
| `PinchRecognizer` | `PinchRecognizer.kt` | Drugi palec wolnej strefy, gdy pierwszy jest PENDING albo CAMERA (kamera jest wtedy najpierw zwalniana), uruchamia szczypanie. Zmiana odległości / `pxPerNotch` daje `mouseWheel(n × 120)` z kotwicą (bez dryfu). Rozsuwanie to wartości dodatnie. Bezpiecznik: najwyżej 10 ząbków na zdarzenie. |
| `EdgeSwipeRecognizer` | `EdgeSwipeRecognizer.kt` | DOWN w pasku przy górnej krawędzi (5% wysokości, min. 16 px) i ruch w dół ≥ 8% wysokości daje `app("menu")`, raz na palec. |
| `ActionPolicy` | `ActionPolicy.kt` | Egzekwuje zasadę 1 dotyk = 1 wejście, loguje naruszenia, jest właścicielem `OutputMapper`. |
| `OutputMapper` | `OutputMapper.kt` | Stan pada → `padState(flagi, osie ±32767, triggery 0..255 jako Byte)`. Przyciski i triggery wysyła od razu, osie zbiera i wysyła raz na cykl, zawsze tylko przy zmianie. Klawisze i przyciski myszy deduplikuje. Flagi = `ControllerPacket` (A=0x1000 …). |

## `sprintLock`: histereza nasycenia (decyzja 11.11.2026)

`StickSpec.sprintLock` (z `StickControl.sprintLock`). Wektor surowy `v` liczony jest od bieżącego środka gałki (pływającej: punkt DOWN, stałej: środek `rect`), `|v| = 1` na obrysie.

- **Wejście:** `|v|` dochodzi do nasycenia, czyli punktu, w którym zwykła krzywa daje 100% (`StickMath.saturated`, ta sama arytmetyka co `StickMath.shape`: `|v| ≥ 1 - outerDeadzone`).
- **W stanie sprint:** moduł wyjścia = 1 (pełny zakres osi jak przy nasyceniu), kierunek = bieżący kąt palca (`StickMath.full`), także gdy kciuk cofnie się do obrysu. W KBM to samo wyjście idzie do 8 sektorów, więc klawisze kierunku zostają wciśnięte poniżej `kbmThreshold`.
- **Wyjście:** `|v| ≤ deadzone` (wyjście 0, dalej zwykła krzywa) albo UP, CANCEL, `releaseAll` (zero jak dotąd; `onUp`/`reset` czyszczą stan).

Zgodność z 1 dotyk = 1 wejście: stan należy do aktywnego palca gałki i ginie razem z nim. Zmienia się wyłącznie w `update`, czyli w cyklu DOWN/MOVE tego palca; tick nie dotyka gałek. Każda zmiana wyjścia jest intencją `HOLD` tego samego wiązania (`control:<id>`) z przyczyną w zdarzeniu dotyku, więc reguły R1–R9 działają bez nowych wyjątków. `sprintLock = false` wywołuje dokładnie to samo `StickMath.shape` co wcześniej.

## Stan wizualny (`visualState`)

`InputEngine.visualState(): EngineVisualState` (plik `VisualState.kt`) to migawka do rysowania, tylko do odczytu: palce (`PointerVisual`: pozycja, punkt DOWN, kontrolka-właściciel albo `null` dla gestu), przyciski HOLD trzymane palcem (`heldIds`), zatrzaśnięte LATCH (`latchedIds`) i wszystkie gałki (`StickVisual`: środek, promień i surowe wychylenie palca obcięte do koła jednostkowego, w osiach ekranu). Dla aktywnej gałki to te same wartości, z których rozpoznawacz liczy wyjście; dla nieaktywnej środek `rect` i promień, który przyjmie `onDown`. Overlay nie ma własnego hit testu, więc wygląd i haptyka idą za strefą aktywacji, slide-to-press i promieniem silnika. Odczyt nie otwiera cyklu polityki i niczego nie emituje.

## Wolna strefa: kamera, touchpad, mysz bezpośrednia albo nic (11.11.2026, `none` i `direct` od 12.11.2026)

`gestures.freeZone` wybiera jedną implementację `FreeZoneGestures` na cały profil (wzorzec strategii, bez reguł konfigurowalnych w `ActionPolicy`). `camera` (domyślnie) to `GestureArbiter` jak dotąd. `touchpad` to `TouchpadGestures`, czyli gładzik laptopa na całej wolnej części ekranu:

| Gest | Wiązanie, rodzaj | Wyjście |
|---|---|---|
| 1 palec, ruch > `tap.maxMovePx` | `gesture:pointer`, HOLD | `MouseMove` od punktu DOWN; `touchpad.sensitivity`, reszty ułamkowe w `RelativeAccumulator` |
| 1 palec, tap (≤ `maxMovePx`, ≤ `tap.maxMs`) | `gesture:tap`, ONE_SHOT | LMB down + up w cyklu UP, w miejscu kursora (bez `mousePosition`) |
| 2 palce, tap (oba ≤ `maxMovePx`, pierwszy DOWN → ostatni UP ≤ `tap.maxMs` + `twoFingerTapExtraMs` = 100 ms) | `gesture:tap2`, ONE_SHOT | RMB down + up w cyklu UP drugiego palca, przypisane temu palcowi |
| 1 palec bez ruchu ≥ `longPress.minMs` | `gesture:drag`, ONE_SHOT_TIMED (LMB), HOLD (ruch) | LMB down (MOVE albo tick trzymanego palca), dalej `MouseMove`, LMB up przy UP. Bez ticków: down + up przy UP |
| 2 palce, ruch środka > `maxMovePx` | `gesture:scroll`, HOLD | oś blokowana na starcie (większa składowa ruchu środka); `Wheel` (palce w dół = dodatnie) albo `HWheel` (palce w lewo = dodatnie); `touchpad.scrollPxPerNotch` px na 120 jednostek, reszty akumulowane, bezpiecznik 10 ząbków na zdarzenie |
| edge swipe z góry | `gesture:edge`, ONE_SHOT | `app("menu")`, jak w Pad+ |
| pinch | | wyłączony |

Wybór wariantu przeciągania: przytrzymanie, a potem ruch. Wariant „tap, a potem przytrzymanie” wymagałby wstrzymania kliknięcia z tapu do czasu, aż wiadomo, czy przyjdzie drugi dotyk, czyli timera wysyłającego klik po fakcie (zakazane). Przytrzymanie rozpoznawane jest tak samo jak long-press w Pad+: tylko w cyklu MOVE, ticku trzymanego palca (R7, `ONE_SHOT_TIMED`) albo przy UP.

`direct` (12.11.2026) to `DirectMouseGestures`, mysz bezpośrednia: kursor idzie tam, gdzie palec dotknął (`MouseAt` przez `VideoMapper`, jak tap w Pad+), więc nie ma kursora względnego ani przytrzymania:

| Gest | Wiązanie, rodzaj | Wyjście |
|---|---|---|
| 1 palec, tap (≤ `maxMovePx`, ≤ `tap.maxMs`, `tap.enabled`) | `gesture:tap`, ONE_SHOT | `MouseAt` w punkcie DOWN, LMB down + up w cyklu UP |
| 2 palce, tap (oba ≤ `maxMovePx`, pierwszy DOWN → ostatni UP ≤ `tap.maxMs` + 100 ms, `tap.enabled`) | `gesture:tap2`, ONE_SHOT | `MouseAt` w punkcie DOWN pierwszego palca, RMB down + up w cyklu UP ostatniego palca, przypisane temu palcowi |
| 1 palec, ruch > `maxMovePx` | `gesture:scroll`, HOLD | `MouseAt` w punkcie DOWN (raz), potem `Wheel` (palec w dół = dodatnie) i `HWheel` (palec w lewo = dodatnie) z każdego MOVE, obie osie naraz, bez blokady osi; `touchpad.scrollPxPerNotch` px na 120 jednostek, reszty akumulowane osobno na oś, bezpiecznik 10 ząbków na zdarzenie i oś |
| 2 palce, ruch któregoś > `maxMovePx` | | nic (oba palce `CONSUMED`) |
| 1 palec trzymany bez ruchu | | nic (long-press nie działa; tick jest ignorowany) |
| edge swipe z góry | `gesture:edge`, ONE_SHOT | `app("menu")`, jak w pozostałych trybach |

Decyzje: RMB w punkcie pierwszego palca, a nie w środku pary, bo pierwszy palec zwykle wskazuje cel. Przeciągnięcie dwoma palcami nic nie robi, bo jeden palec już przewija, a drugi sposób przewijania tylko zwiększa ryzyko przypadkowych wejść. Bez blokady osi, bo właściciel chciał przewijania w pionie i w poziomie jednym gestem (jak mapa na telefonie). Punkt DOWN poza obrazem (letterbox): tap i przewijanie nic nie dają, jak `TapRecognizer`. Palec dołożony do przewijającego palca i trzeci palec nic nie robią (`CONSUMED`, trzeci unieważnia też tap dwoma palcami). Palce na kontrolkach nie należą do wolnej strefy, więc nie tworzą par (tap przy trzymanej gałce to zwykły LMB).

Zgodność z 1 dotyk = 1 wejście: palec daje najwyżej jedną krawędź (LMB z tapu, RMB z tapu dwoma palcami tylko dla ostatniego palca pary, menu z edge swipe). Tap nie przewija (przewijanie zaczyna się dopiero po przekroczeniu progu ruchu, a palec po ruchu nie da już tapu), przeciągnięcie nie klika, a tap dwoma palcami nie daje LMB (palce pary nie są pojedynczymi palcami). `MouseAt` i kółka nie mają trwałego stanu (jak przewijanie touchpada i pinch) i powstają wyłącznie w cyklu MOVE (przewijanie) albo UP (kliki). Tick nic nie robi, więc żadne wejście nie powstaje z samego czasu.

Tap dwoma palcami w `camera` (12.11.2026, `GestureArbiter`, gdy `tap.enabled`): drugi palec wolnej strefy dotyka, gdy pierwszy jest jeszcze w `PENDING` bez ruchu. Przy włączonym pinchu para i tak jest pinchem, a tap dwoma palcami jest śledzony obok (unieważnia go ruch któregoś palca > `maxMovePx`, kółko z pinchu, trzeci palec albo przekroczenie czasu); bez pinchu para ma stan `TWO_TAP` (ruch = nic). Po podniesieniu pierwszego palca drugi czeka w `TWO_TAP`. Na ostatnim UP: `MouseAt` w punkcie pierwszego palca i RMB down + up (`gesture:tap2`, ONE_SHOT). Kamera przeciąganiem, tap LMB i long-press RMB bez zmian; palec kamery nie tworzy pary do RMB. Tap dwoma palcami i long-press nie kolidują: palce pary nie są już w `PENDING`, więc nie rozpoznają long-pressu.

`none` to `EdgeSwipeOnlyGestures`: palec wolnej strefy nie daje kamery, myszy, klików, pinchu ani kółka, niezależnie od pól `cameraDrag`, `tapTarget`, `longPress`, `pinchZoom` i `touchpad`. Palec, który dotknął paska przy górnej krawędzi (gdy `edgeSwipeTop.enabled`), po wyjściu poza `tap.maxMovePx` w dół otwiera menu (`gesture:edge`, ONE_SHOT, raz na palec, tylko w cyklu MOVE); ruch w bok albo w górę kończy kandydata. Tick niczego nie robi. Presety „Pad – Xbox” i „Pad – PlayStation” używają tego trybu (dawniej touchpad z wyłączonym tapem, w którym przeciągnięcie nadal ruszało kursorem hosta).

Zgodność z 1 dotyk = 1 wejście: każdy palec daje najwyżej jedną krawędź (LMB z tapu albo przytrzymania, RMB z tapu dwoma palcami tylko dla drugiego palca, menu z edge swipe). Ruch kursora i kółko nie mają trwałego stanu (jak RMB-look i pinch) i powstają wyłącznie w cyklu MOVE. Trzeci palec w wolnej strefie i palec dołożony w trakcie przeciągania nic nie robią (`CONSUMED`). Dołożenie drugiego palca do palca przesuwającego kursor zatrzymuje kursor i zaczyna przewijanie (bez tapu). Po CANCEL i `releaseAll` stan jest zapominany (`reset`), a polityka wysyła zerowy stan.

### Przełącznik „Gesty myszy” (12.11.2026)

Silnik nie ma osobnego trybu: przełącznik użytkownika zamienia profil przed `setProfile` (`MouseGesturesTransform` z `:control-model`, opis w `control-model/docs/profile-schema.md`). Pady (`none`) po włączeniu dostają `DirectMouseGestures` (do pierwszej wersji z 12.11.2026 był to `TouchpadGestures`), `direct` i touchpad po wyłączeniu `EdgeSwipeOnlyGestures`, touchpad po włączeniu zostaje gładzikiem, a kamera (`GestureArbiter`) tylko włącza albo wyłącza tap (z nim tap dwoma palcami), long-press i pinch. Wszystko idzie przez te same rozpoznawacze i `ActionPolicy`, bez nowych wyjątków i timerów. Sesja (`ThumbcastSession`) składa profil efektywny: plik → `MouseGesturesTransform` → `ScaleTransform` → ukrycie kontrolek. `MouseSink` wysyła mysz niezależnie od trybu profilu, więc kursor i kliki działają też w Pad+.

## Jak ActionPolicy egzekwuje 1 dotyk = 1 wejście

Każde wyjście jest **intencją** z trzema atrybutami: palec (`pointerId`), logiczne wejście (`bindingId`, np. `control:a`, `gesture:camera`) i rodzaj (`HOLD`, `LATCH`, `ONE_SHOT`, `ONE_SHOT_TIMED`). Polityka prowadzi rejestr *właścicieli* każdego trwałego stanu (przycisk pada, trigger, gałka, klawisz, przycisk myszy) oraz stan każdego palca.

Reguły (numeracja jak w komentarzu klasy):

1. **R1 – przyczyna.** Wyjście musi powstać w otwartym cyklu (`onTouch`, `onTick`, `releaseAll`) i na tym samym wątku. Wywołanie z timera, z korutyny albo z innego wątku po zakończeniu cyklu jest odrzucane („brak przyczyny”).
2. **R2 – palec w zdarzeniu.** Wciśnięcie musi dotyczyć palca, który jest w bieżącym `TouchEvent`.
3. **R3 – żywy palec.** Wciśnięcie wymaga palca po DOWN, przed UP/CANCEL. W cyklu UP dozwolone jest tylko wejście jednorazowe (tap albo long-press rozpoznany przy UP). W cyklu CANCEL nie ma żadnych wciśnięć.
4. **R4 – jeden palec, jedno wejście.** Palec może zmienić wiązanie tylko wtedy, gdy poprzednie już niczego nie trzyma. Slide-to-press musi więc najpierw zwolnić stary przycisk.
5. **R5 – gest jednorazowy raz.** Tap, long-press, edge swipe i przycisk App dają najwyżej jedną krawędź wciśnięcia na cykl życia palca i wykluczają się nawzajem.
6. **R6 – LATCH tylko przy DOWN** tego samego palca. Stan LATCH należy do kontrolki, nie do palca, więc przeżywa UP i nie blokuje palca.
7. **R7 – czas sam nie naciska.** W cyklu TICK dozwolone są tylko long-press i przytrzymanie touchpada (`ONE_SHOT_TIMED`, rozpoznanie gestu trzymanego palca), zwolnienia i wygaszanie gałki (moduł nie rośnie, znak się nie zmienia). Bez dotykającego palca `onTick` w ogóle nie otwiera cyklu.
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
- `TouchpadGesturesTest`: ruch kursora z resztami ułamkowymi (i kontrola, że ta sama ścieżka w Pad+ to gałka, a nie mysz), tap LMB w miejscu kursora, za wolny tap, przytrzymanie bez ticków, przytrzymanie na ticku i przeciąganie z LMB, palec już przesuwający kursor nie staje się przeciąganiem, tap dwoma palcami (RMB na ostatnim UP, za wolny, trzeci palec), rozsuwanie bez zoomu, przewijanie pionowe i poziome z blokadą osi, dwa palce w jednym MOVE, drugi palec dołożony do kursora, ticki przy dwóch palcach, edge swipe, CANCEL i `releaseAll` przy trzymanym LMB, prawa gałka-kontrolka razem z touchpadem, KBM.
- `NoFreeZoneTest` (`freeZone = none`): przeciągnięcie, tap, long-press (ticki i UP), dwa palce i pinch nic nie dają; edge swipe otwiera menu raz (MOVE), wyłączony albo w bok nic nie daje; kontrolki (prawa gałka, A) działają obok; KBM bez RMB-look; CANCEL i `releaseAll` czyszczą kandydata.
- `DirectMouseTest` (`freeZone = direct`): tap = pozycja w punkcie (układ wideo, letterbox, czarny pas nic nie daje) i LMB; przytrzymanie i ticki nic; przeciągnięcie = pozycja raz w punkcie DOWN, kółko pionowe i poziome z właściwym znakiem (treść za palcem), reszty ułamkowe, bezpiecznik ząbków, bez kliku; tap dwoma palcami = RMB w punkcie pierwszego palca (oba porządki podniesienia), za wolny nic; przeciągnięcie dwoma palcami nic; palec dołożony do przewijania i trzeci palec nic; tap i przewijanie przy trzymanych gałkach i przycisku; edge swipe (w dół menu, w bok przewijanie); tap wyłączony nadal przewija; CANCEL i `releaseAll`.
- `CameraTwoFingerTapTest` (`freeZone = camera`): tap dwoma palcami = RMB w punkcie pierwszego palca z pinchem i bez, oba porządki podniesienia; bez tapu nic; pinch dalej daje kółko i unieważnia RMB; para w ruchu bez pinchu nic; za wolna para nic, long-press dalej działa; palec kamery nie tworzy pary; nowy palec z id podniesionego palca pary zachowuje kamerę (regresja z 10^5 iteracji).
- `MouseGesturesSwitchTest`: profil pada z gestami myszy przewija i klika w punkcie (bez kursora względnego i kamery), tap dwoma palcami = PPM; bez nich nic nie wysyła; `direct` i touchpad wyłączone dają tylko menu; touchpad włączony zostaje gładzikiem; kamera bez gestów myszy nadal kręci prawą gałką, bez klików i pozycji myszy (także bez PPM z dwóch palców); z gestami klika w punkcie (tap LMB, dwa palce PPM).
- `HWheelTest`: `HWheel` → `mouseHScroll`, zero pomijane, R1/R3/R7 dla kółka poziomego.
- `ActionPolicyTest`: każda reguła R1–R9 wywołana bezpośrednio.
- `KnownRisksTest`: ryzyka z `docs/HANDOFF.md`. Gałkę RIGHT razem z kamerą `pad_right_stick` i każdy `chord` walidator od 11.11.2026 odrzuca. Testy sprawdzają to odrzucenie i opisują zachowanie silnika na takim profilu niewalidowanym. Poza tym rozstrzygnięty `sprintLock` (histereza) i long-press przy tickach co klatkę.
- `ActionPolicyPropertyTest` (Kotest `checkAll`, liczba iteracji z `thumbcast.policy.iterations`). Strumienie są poprawne albo chaotyczne: CANCEL, podwójny DOWN, UP bez DOWN, nieznane palce w MOVE, cofający się czas, `releaseAll` i zmiana viewportu w trakcie. Sprawdzane są tryby Pad+, Pad+ ze slide-to-press, KBM, wolna strefa `none` (chaos wielu palców; dodatkowo brak wyjścia myszy, prawa gałka tylko z kontrolki, z gestów tylko menu) profil po przełączniku „Gesty myszy” (`mouseGesturesSwitchChaoticStreams`: kamera z losowymi gestami, touchpad, direct albo none, Pad+ albo KBM, z prawą gałką albo bez, wł. albo wył.; wynik przechodzi walidator i ma żądany stan, a wynik `none` nie daje myszy), mysz bezpośrednia (`directValidStreams`, `directMultiFingerChaoticStreams`: Pad+ albo KBM, prawa gałka, losowa długość ząbka, tap wł./wył.; dodatkowo brak ruchu względnego, LMB tylko z `gesture:tap`, RMB tylko z `gesture:tap2`, kółka tylko z `gesture:scroll`) oraz touchpad (poprawne i chaotyczne strumienie; Pad+ albo KBM, z widoczną prawą gałką albo bez, losowa czułość i długość ząbka, dodatkowo pary palców w wolnej strefie i wspólny ruch kilku palców w jednym MOVE); mniej więcej połowa strumieni (wybór z ziarna) ma gałkę z `sprintLock = true`. Właściwości:
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
- W KBM jeden palec na gałce może trzymać dwa klawisze naraz (np. W+D dla skosu). To jedno logiczne wejście (wiązanie gałki, np. `control:move`) i jedyny wyjątek od „jedna krawędź na dotyk”: wynika z 8 kierunków WASD (plan 4.2).
- Wygaszanie kamery postępuje tylko przy tickach albo kolejnych zdarzeniach. Jeśli overlay przestanie wołać `onTick` przy nieruchomym palcu, gałka zostaje wychylona do następnego zdarzenia albo UP (ograniczenie bezpieczne: zero przy UP/CANCEL jest zawsze wysyłane).
- Long-press bez ticków jest rozpoznawany dopiero przy UP (RMB down i up w jednym cyklu), czyli bez możliwości trzymania RMB.
- Strefa kamery to połowa ekranu po stronie `zone`. Palec, który ruszył po drugiej stronie, jest „zużyty” (nic nie robi do UP).
- Edge swipe obsługuje tylko górną krawędź, a jego pasek liczony jest od górnej krawędzi overlaya, nie od `safeArea`.
- Stałe w `EngineTuning` (promień gałki 15%, 1,5 px/ms dla pełnej kamery, hit slop) są wstępne i do strojenia na urządzeniach (plan 9, T-01/T-02).
- `setViewport` z inną geometrią podczas dotyku robi `releaseAll` zamiast przeliczać pozycje palców.
- Polityka zakłada jeden wątek (UI). Wywołania z innego wątku są odrzucane, a nie synchronizowane.
