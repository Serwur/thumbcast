# Schemat profilu Thumbcast, wersja 1

Plik profilu ma rozszerzenie `.thumbcast.json` i kodowanie UTF-8. Odczytuje go `ProfileCodec.decode`, który:

1. odrzuca każde nieznane pole (`ignoreUnknownKeys = false`) i każdy nieznany typ,
2. odrzuca w dowolnym miejscu pliku nazwy `sequence`, `macro`, `repeat`, `turbo`, `delay` i `toggle_auto`, zarówno jako wartość `type`, jak i jako klucz pola,
3. migruje schemat do bieżącego (`ProfileMigrations`, na razie tylko v1),
4. uruchamia `ProfileValidator.validate`. Jeśli walidator zwróci jakikolwiek błąd, wynikiem jest `Result.failure(ProfileValidationException)`.

`ProfileCodec.encode` zapisuje czytelny JSON z wcięciem 2 spacji, z wartościami domyślnymi i bez pól równych `null`.

Wszystkie współrzędne są znormalizowane do zakresu 0..1 względem bezpiecznego obszaru ekranu w orientacji poziomej. Punkt (0,0) to lewy górny róg.

## Poziom główny

| Pole | Typ | Wymagane | Opis |
|---|---|---|---|
| `schema` | int | tak | Wersja schematu. Obecnie musi wynosić `1`. Nowszy schemat jest odrzucany. |
| `id` | string | tak | Identyfikator profilu, np. UUID. Nie może być pusty. |
| `name` | string | tak | Nazwa widoczna dla użytkownika. Nie może być pusta. |
| `tags` | obiekt string→string | nie (`{}`) | Dowolne tagi, np. `game`, `class`, `character`. |
| `mode` | `"pad_plus"` \| `"keyboard_mouse"` | tak | Tryb profilu (plan, sekcja 4.2). |
| `reference` | obiekt | nie | `aspect` (domyślnie `"20:9"`) i `unit` (domyślnie `"dp-normalized"`). Pole informacyjne, używane przy skalowaniu z kotwicami. |
| `handedness` | `"right"` \| `"left"` | nie (`"right"`) | Orientacja układu. Lustro robi `HandednessTransform`. |
| `global` | obiekt | nie | Ustawienia globalne, opisane niżej. |
| `gestures` | obiekt | nie | Gesty na wolnej strefie ekranu, opisane niżej. |
| `controls` | tablica | tak, co najmniej 1 | Kontrolki. Każda ma pole `type`. |

## `global`

| Pole | Typ | Domyślnie | Zakres / opis |
|---|---|---|---|
| `opacity` | float | 0.55 | 0.1..1 |
| `haptics.enabled` | bool | true | |
| `haptics.strength` | float | 0.6 | 0..1 |
| `haptics.onPress` | `"none"` \| `"tick"` \| `"click"` | `"tick"` | |
| `haptics.onRelease` | jw. | `"none"` | |
| `slideToPress` | bool | false | Przesunięcie palca na inny przycisk najpierw zwalnia poprzedni, potem wciska nowy. Nigdy nie trzyma dwóch naraz. |
| `autoHideOnGamepad` | bool | true | Po podłączeniu pada BT overlay chowa joystick i przyciski. |
| `allowChords` | bool | **false** | Nic nie włącza. Od 11.11.2026 akordy nie są obsługiwane (FAIR_PLAY.md). Pole zostaje bez migracji. |

## `gestures`

| Gest | Pola (domyślne) | Reguły |
|---|---|---|
| `cameraDrag` | `zone` (`"right_free"` / `"left_free"`), `output` (`"pad_right_stick"` / `"mouse_look"`), `sensitivity` (1.2), `curve` ({power, 1.6}), `deadzonePx` (4), `invertY` (false), `maxDeflection` (1.0), `decayMs` (60) | W trybie `keyboard_mouse` `output` musi mieć wartość `"mouse_look"` (RMB trzymany przez cały gest + ruch względny). `sensitivity` w (0..10], `deadzonePx` 0..64, `maxDeflection` w (0..1], `decayMs` 0..500. |
| `tapTarget` | `enabled` (true), `output` (`"mouse_left_at_point"`), `maxMovePx` (12), `maxMs` (220) | |
| `longPress` | `enabled` (true), `output` (`"mouse_right_at_point"`), `minMs` (450) | Gdy włączone są oba gesty, `minMs` musi być większe od `tapTarget.maxMs`. Long-press daje jedno wejście **zamiast** tapu. |
| `pinchZoom` | `enabled` (true), `output` (`"mouse_wheel"`), `pxPerNotch` (40) | `pxPerNotch` > 0 |
| `edgeSwipeTop` | `enabled` (true), `output` (`"app_menu"`) | Akcja lokalna, nic nie trafia do hosta. |
| `freeZone` | `"camera"` (domyślnie) \| `"touchpad"` \| `"direct"` \| `"none"` | Właściciel wolnej strefy (od 11.11.2026, `"none"` i `"direct"` od 12.11.2026). Jeden na profil, więc jedno przeciągnięcie nigdy nie daje naraz kamery i ruchu myszy. Profile bez tego pola mają `"camera"`, czyli działają jak dotąd. |
| `touchpad` | `sensitivity` (1.0), `scrollPxPerNotch` (24) | `sensitivity` w (0..10] (tylko `freeZone = "touchpad"`): piksele myszy na piksel palca. `scrollPxPerNotch` 4..400: droga palców na 1 ząbek kółka (120 jednostek): środka dwóch palców przy `"touchpad"`, jednego palca przy `"direct"`. |

### Wolna strefa: `camera`, `touchpad`, `direct` i `none`

`"camera"` to tabela gestów Pad+ z tabeli wyżej: `cameraDrag` w strefie `zone` (połowa ekranu), tap i long-press w punkcie dotyku, pinch, edge swipe. Od 12.11.2026 przy `tapTarget.enabled` dochodzi tap dwoma palcami: drugi palec dotyka, gdy pierwszy jeszcze nic nie wysłał, oba bez ruchu (≤ `maxMovePx`), od pierwszego DOWN do ostatniego UP ≤ `maxMs` + 100 ms, bez kółka z pinchu → RMB w punkcie pierwszego palca (pozycja + wciśnięcie i zwolnienie przy podniesieniu ostatniego palca). Pinch i long-press działają jak dotąd; para palców w ruchu bez pinchu nic nie daje.

`"touchpad"` obejmuje całą wolną część ekranu (bez podziału na połowy) i ma stałą tabelę gestów. Pola `cameraDrag` i `pinchZoom` są wtedy ignorowane. Z `tapTarget` i `longPress` liczą się `enabled`, `maxMovePx`, `maxMs` i `minMs`, a pole `output` nie ma znaczenia (klik idzie w miejscu kursora):

| Gest | Wyjście |
|---|---|
| 1 palec, przeciągnięcie (ruch > `tapTarget.maxMovePx`) | ruch względny myszy (`touchpad.sensitivity`, reszty ułamkowe są akumulowane) |
| 1 palec, tap (≤ `maxMovePx`, ≤ `maxMs`) | LMB w miejscu kursora: wciśnięcie i zwolnienie przy podniesieniu palca |
| 2 palce, tap (oba bez ruchu, od pierwszego DOWN do ostatniego UP ≤ `maxMs` + 100 ms) | RMB w miejscu kursora przy podniesieniu drugiego palca |
| 1 palec, przytrzymanie ≥ `longPress.minMs` bez ruchu, potem przeciągnięcie | LMB trzymany przez cały dotyk (przeciąganie okien, zaznaczanie), ruch myszy jak wyżej, LMB puszczany przy podniesieniu palca |
| 2 palce, przeciągnięcie | kółko: oś (pionowa albo pozioma) wybierana raz, na starcie gestu, według większej składowej ruchu; potem tylko ta oś. Kierunek jak na telefonie: palce w dół = treść w dół (kółko w górę), palce w lewo = treść w lewo (kółko w prawo) |
| pinch | wyłączony |
| `edgeSwipeTop` | `app_menu`, bez zmian |

Trzeci palec w wolnej strefie nic nie robi i unieważnia tap dwoma palcami. Palec, który dotknie wolnej strefy, gdy inny trzyma LMB (przeciąganie), nic nie robi.

`"direct"` (12.11.2026, mysz bezpośrednia): cała wolna część ekranu, kursor idzie tam, gdzie palec dotknął (pozycja bezwzględna w układzie wideo, jak `mouse_left_at_point`). Pola `cameraDrag`, `longPress`, `pinchZoom` i `touchpad.sensitivity` są ignorowane; z `tapTarget` liczą się `enabled` (tap i tap dwoma palcami), `maxMovePx` i `maxMs`, a z `touchpad` tylko `scrollPxPerNotch`.

| Gest | Wyjście |
|---|---|
| 1 palec, tap (≤ `maxMovePx`, ≤ `maxMs`) | pozycja w punkcie dotyku + LMB (wciśnięcie i zwolnienie przy podniesieniu palca) |
| 2 palce, tap (oba bez ruchu, od pierwszego DOWN do ostatniego UP ≤ `maxMs` + 100 ms) | pozycja w punkcie **pierwszego** palca + RMB przy podniesieniu ostatniego palca (pierwszy palec zwykle wskazuje cel; środek pary leżałby obok niego) |
| 1 palec, przeciągnięcie (ruch > `maxMovePx`) | pozycja w punkcie DOWN, potem kółko pionowe i poziome naraz z ruchu palca (bez blokady osi), `scrollPxPerNotch` px na 120 jednostek, reszty ułamkowe akumulowane. Kierunek jak na telefonie: palec w dół = treść w dół (kółko w górę), palec w lewo = treść w lewo (kółko w prawo). Bez kliku |
| 2 palce, przeciągnięcie | nic (jeden palec już przewija; drugi sposób przewijania zwiększałby ryzyko przypadkowych wejść) |
| 1 palec trzymany bez ruchu dłużej niż `maxMs` | nic (bez long-pressu i przeciągania z LMB) |
| pinch | wyłączony |
| `edgeSwipeTop` | `app_menu`, bez zmian |

Palec, który dotknie wolnej strefy, gdy inny palec wolnej strefy przewija, nic nie robi; trzeci palec unieważnia tap dwoma palcami. Palce na kontrolkach (gałki, przyciski) nie liczą się do par. Punkt DOWN poza obrazem (czarne pasy): tap i przewijanie nic nie dają. Kamery nie ma, więc prawa gałka jest wolna dla kontrolki. Używa go preset „Big Picture” (od 12.11.2026, wcześniej `touchpad`) i przełącznik „Gesty myszy” na presetach pada.

`"none"` (12.11.2026): dotyk poza kontrolkami nic nie wysyła do hosta (bez kamery, myszy, klików, kółka). Pola `cameraDrag`, `tapTarget`, `longPress`, `pinchZoom` i `touchpad` są ignorowane. Działa tylko `edgeSwipeTop` (`app_menu`), jeśli jest włączony, rozpoznawany tak samo jak w pozostałych trybach. Kamery nie ma, więc prawa gałka jest wolna dla kontrolki. Używają go presety „Pad – Xbox” i „Pad – PlayStation” (pełny pad na ekranie, przeciągnięcie w pustym miejscu nie rusza kursorem na hoście).

### Przełącznik „Gesty myszy” (poza plikiem profilu, 12.11.2026)

Użytkownik może włączyć albo wyłączyć mysz w wolnej strefie dowolnego profilu, także presetu tylko do odczytu. Wybór nie trafia do pliku (schemat bez zmian, eksport go nie zawiera): telefon trzyma go osobno dla każdej nazwy pliku profilu, a `MouseGesturesTransform.apply(profil, wybór)` daje profil efektywny dla silnika. Wartość domyślna (`MouseGesturesTransform.isEnabled`) wynika z pliku, więc bez wpisu nic się nie zmienia:

| `freeZone` w pliku | domyślnie | wł. | wył. |
|---|---|---|---|
| `none` | wył. | `freeZone = direct`, `tapTarget.enabled` = true | bez zmian |
| `direct` | wł. | bez zmian | `freeZone = none` |
| `touchpad` | wł. | bez zmian (gładzik zostaje gładzikiem) | `freeZone = none` |
| `camera` | wł., gdy `tapTarget`, `longPress` albo `pinchZoom` jest włączony | `tapTarget`, `longPress`, `pinchZoom` włączone (tap = LMB w punkcie, tap dwoma palcami i long-press = RMB w punkcie, pinch = kółko) | te trzy wyłączone (kamera zostaje) |

Pierwsza wersja (12.11.2026) zamieniała `none` na `touchpad`; właściciel wybrał `direct` jako bardziej intuicyjny. Gdy włączenie tapu złamałoby regułę `longPress.minMs > tapTarget.maxMs` (przy włączonym long-pressie), `minMs` dostaje max(450, `maxMs` + 1). Zmieniają się tylko pola `gestures` (`cameraDrag` i `edgeSwipeTop` nigdy), kamera nie jest dokładana, więc reguła jednej gałki zostaje spełniona. Wynik przechodzi `ProfileValidator` (testy właściwości w `MouseGesturesTransformTest` i `ActionPolicyPropertyTest`); gdyby nie przeszedł, zwracany jest profil z pliku. Pole `touchpad.scrollPxPerNotch` ma znaczenie także w profilu z `none` (przewijanie po włączeniu gestów myszy); `touchpad.sensitivity` w presetach pada (1.3) zostało z pierwszej wersji i działa tylko po ręcznej zmianie na `touchpad`.

`curve`: `{ "type": "linear" }` (wtedy `exponent` musi wynosić 1, co jest wartością domyślną) albo `{ "type": "power", "exponent": 1.0..3.0 }`.

## Wyjście (`output`)

Obiekt z **dokładnie jednym** kluczem, którego wartością jest pojedynczy napis. Tablice, obiekty, puste `{}` i dwa klucze naraz są odrzucane.

| Klucz | Wartości | Typ w modelu |
|---|---|---|
| `pad` | `A B X Y UP DOWN LEFT RIGHT LB RB START BACK L3 R3 GUIDE` | `Output.Pad` |
| `pad` | `LT RT` | `Output.Trigger` |
| `pad` | `left_stick right_stick` | `Output.Stick` |
| `trigger` | `LT RT` (alias przy odczycie, zapis jako `pad`) | `Output.Trigger` |
| `stick` | `left right left_stick right_stick` (alias przy odczycie, zapis jako `pad`) | `Output.Stick` |
| `key` | kod z `KeyCodes` | `Output.Key` |
| `mouse` | `left right middle` | `Output.Mouse` |
| `mouse` | `wheel`, `look` | `Output.MouseWheel`, `Output.MouseLook` (tylko gesty; walidator nie pozwala ich użyć w przyciskach) |
| `app` | `menu soft_keyboard toggle_overlay` | `Output.App` |

**Kody klawiszy (`KeyCodes`)**: `A`–`Z`, `0`–`9`, `F1`–`F12`, `SPACE`, `ESC`, `TAB`, `ENTER`, `SHIFT`, `CTRL`, `ALT`, `-`, `=`, `UP`, `DOWN`, `LEFT`, `RIGHT`. Wielkość liter ma znaczenie, więc `"a"` jest błędem.

## Kontrolki (`controls[]`)

Pola wspólne dla wszystkich typów:

| Pole | Typ | Opis |
|---|---|---|
| `id` | string | Niepusty i unikalny w obrębie profilu. |
| `type` | `"stick"` \| `"button"` \| `"modifier"` \| `"system"` | Każda inna wartość jest odrzucana. |
| `rect` | `{x,y,w,h}` | x, y w 0..1; w, h w (0..1]; x+w ≤ 1 oraz y+h ≤ 1 (tolerancja 1e-4). Wartości muszą być skończone. |
| `label` | string? | Etykieta: neutralna, po polsku. |
| `icon` | string? | Nazwa ikony z neutralnego zestawu aplikacji. |

### `stick`

| Pole | Domyślnie | Reguły |
|---|---|---|
| `output` | `{ "pad": "left_stick" }` | Musi to być gałka (`Output.Stick`). W `pad_plus` jedna gałka pada ma jedno źródło: dwie kontrolki `stick` z tą samą gałką albo `right_stick` przy `gestures.freeZone = "camera"` i `gestures.cameraDrag.output = "pad_right_stick"` to błąd. Przy `freeZone = "touchpad"`, `"direct"` i `"none"` kamery nie ma, więc prawa gałka jest wolna dla kontrolki (np. presety Big Picture i Pad – Xbox). |
| `floating` | true | Środek joysticka wypada w miejscu, w którym kciuk dotknął strefy. |
| `deadzone` | 0.12 | 0..0.5 |
| `outerDeadzone` | 0.04 | 0..0.5; suma z `deadzone` ≤ 0.9 |
| `curve` | `{ "type": "linear" }` | jak wyżej |
| `kbmKeys` | brak | `{up,down,left,right}`: cztery różne kody z `KeyCodes`. **Wymagane w trybie `keyboard_mouse`.** |
| `kbmThreshold` | 0.35 | 0.1..0.9: próg wychylenia dla klawiszy kierunków (8 kierunków). |
| `sprintLock` | false | Histereza nasycenia (decyzja 11.11.2026). Gdy palec dojdzie do pełnego wychylenia (\|v\| ≥ 1 - `outerDeadzone`, liczone od bieżącego środka gałki, pływającej lub stałej), wychylenie zostaje 100%, a kierunek idzie za palcem, także gdy kciuk cofnie się do obrysu. Koniec: powrót palca w martwą strefę (\|v\| ≤ `deadzone`, potem zwykła krzywa), podniesienie palca, CANCEL albo `releaseAll`. Nic nie zostaje po palcu i nie ma timerów. W `keyboard_mouse` 100% oznacza trzymane klawisze `kbmKeys`, więc kierunek trzyma się poniżej `kbmThreshold`, aż do martwej strefy. `false`: bez zmian (zwykła krzywa w obie strony). |

### `button`

| Pole | Domyślnie | Reguły |
|---|---|---|
| `output` | wymagane | Przycisk pada, trigger, klawisz albo `mouse` left/right/middle. W trybie `keyboard_mouse` wyjścia pada są zabronione. Gałka, `wheel`, `look` i `app` są zabronione. |
| `behavior` | `"hold"` | Dozwolone tylko `"hold"`. Wartość `"latch"` jest błędem. |
| `shape` | `"circle"` | `"circle"` \| `"rounded_rect"` \| `"rect"` |
| `chord` | brak | **Nieobsługiwane od 11.11.2026.** Każda wartość jest błędem walidacji, także przy `global.allowChords = true` (FAIR_PLAY.md: 1 dotyk = 1 wejście). Zamiast akordu użyj osobnej kontrolki `modifier` w trybie `hold` albo `latch`. Pole zostaje w schemacie bez migracji. |

### `modifier`

To samo co `button`, z dwiema różnicami: `behavior` może mieć wartość `"latch"` (pierwsze dotknięcie wciska, następne zwalnia), a `output` musi być modyfikatorem: `LT`, `RT`, `LB`, `RB` albo klawiszem `SHIFT`, `CTRL`, `ALT`. Domyślny `shape` to `"rounded_rect"`. Pole `chord` nie istnieje.

### `system`

`output` musi być postaci `{ "app": ... }`, z akcją `menu`, `soft_keyboard` albo `toggle_overlay`. Kontrolka nic nie wysyła do hosta. Domyślny `shape` to `"rounded_rect"`.

## Tryby

- `pad_plus`: dozwolone wyjścia `pad`/`trigger`/`stick`, a także `key` i `mouse` w przyciskach (np. „Mapa” → `M`).
- `keyboard_mouse`: brak wyjść pada w przyciskach i modyfikatorach. Joystick musi mieć `kbmKeys`, a `cameraDrag.output` musi mieć wartość `mouse_look`.

## Leworęczność

`HandednessTransform.mirror` zamienia `x` na `1 - x - w` w każdym `rect` (wynik zaokrąglany do siatki 1e-4), zamienia `cameraDrag.zone` między `right_free` a `left_free` i przełącza `handedness`. Wyjścia zostają bez zmian. Dwukrotne lustro daje profil wyjściowy.

## Migracje

`ProfileMigrations.migrate(tree, from, to)` działa na drzewie JSON przed dekodowaniem typowanym. Każdy krok n → n+1 musi ustawić `"schema": n+1`. Schemat starszy niż 1 albo nowszy niż `CURRENT_SCHEMA` jest odrzucany.

Pola `gestures.freeZone` i `gestures.touchpad` (11.11.2026) oraz wartości `freeZone = "none"` i `"direct"` (12.11.2026) dodano bez podnoszenia schematu: mają wartości domyślne, więc każdy istniejący plik v1 wczytuje się bez zmian i bez migracji. Starsza wersja aplikacji odrzuci jednak profil, który je zawiera (nieznane pole).

## Przykład (fragment `presets/mmo-pad-plus.thumbcast.json`)

```json
{
  "schema": 1,
  "id": "0c6e5a3e-7d1b-4f0e-8a51-3f2b9d6c1a01",
  "name": "MMO – Pad+",
  "tags": { "game": "mmo-generic" },
  "mode": "pad_plus",
  "reference": { "aspect": "20:9", "unit": "dp-normalized" },
  "handedness": "right",
  "global": {
    "opacity": 0.55,
    "haptics": { "enabled": true, "strength": 0.6, "onPress": "tick", "onRelease": "none" },
    "slideToPress": false, "autoHideOnGamepad": true, "allowChords": false
  },
  "gestures": {
    "cameraDrag": { "zone": "right_free", "output": "pad_right_stick", "sensitivity": 1.2,
                    "curve": { "type": "power", "exponent": 1.6 }, "deadzonePx": 4,
                    "invertY": false, "maxDeflection": 1.0, "decayMs": 60 },
    "tapTarget":  { "enabled": true, "output": "mouse_left_at_point", "maxMovePx": 12, "maxMs": 220 },
    "longPress":  { "enabled": true, "output": "mouse_right_at_point", "minMs": 450 },
    "pinchZoom":  { "enabled": true, "output": "mouse_wheel", "pxPerNotch": 40 },
    "edgeSwipeTop": { "enabled": true, "output": "app_menu" }
  },
  "controls": [
    { "id": "move", "type": "stick", "label": "Ruch", "output": { "pad": "left_stick" },
      "rect": { "x": 0.0, "y": 0.40, "w": 0.35, "h": 0.60 }, "floating": true,
      "deadzone": 0.12, "outerDeadzone": 0.04, "curve": { "type": "linear" } },
    { "id": "lt", "type": "modifier", "label": "LT", "output": { "pad": "LT" }, "behavior": "hold",
      "rect": { "x": 0.035, "y": 0.256, "w": 0.09, "h": 0.108 } },
    { "id": "a", "type": "button", "label": "Skok", "icon": "jump", "output": { "pad": "A" },
      "rect": { "x": 0.824, "y": 0.789, "w": 0.072, "h": 0.162 }, "shape": "circle" },
    { "id": "chat", "type": "system", "label": "⌨", "output": { "app": "soft_keyboard" },
      "rect": { "x": 0.09, "y": 0.02, "w": 0.06, "h": 0.09 } }
  ]
}
```

## Przykłady odrzucane

```json
{ "id": "m", "type": "macro", ... }                          // zakazany typ
{ "id": "a", "type": "button", "turbo": true, ... }          // zakazane i nieznane pole
{ "output": { "pad": "A", "key": "M" } }                     // dwa wyjścia
{ "output": { "pad": ["LB", "RB"] } }                        // tablica w wyjściu
{ "type": "button", "behavior": "latch", ... }               // latch poza modyfikatorem
{ "type": "button", "chord": { "pad": "LB" } }               // akord (nieobsługiwany, także przy allowChords)
{ "type": "stick", "output": { "pad": "right_stick" } }      // w pad_plus przy freeZone camera i kamerze pad_right_stick
{ "type": "system", "output": { "app": "run_script" } }      // niedozwolona akcja aplikacji
```
