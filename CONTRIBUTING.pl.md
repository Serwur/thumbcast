# Współtworzenie Thumbcast

<p><a href="CONTRIBUTING.md">English</a> · <b>Polski</b></p>

Dziękujemy za chęć pomocy. Przeczytaj najpierw [FAIR_PLAY.md](FAIR_PLAY.pl.md), bo zasada 1 dotyk = 1 wejście ogranicza zakres akceptowanych zmian.

## Czego nie przyjmujemy

PR-y dodające którąkolwiek z poniższych funkcji zostaną zamknięte bez dyskusji o implementacji:

- makra, sekwencje, „combo”, rotacje i inne łańcuchy wejść z jednego dotyku;
- turbo, auto-fire, auto-repeat, przełączniki „trzymaj za mnie” działające bez palca (poza `latch` dla modyfikatorów);
- opóźnienia, timery i harmonogramy generujące naciśnięcia;
- analizę obrazu strumienia (OCR, CV, odczyt pikseli, reakcje na stan gry);
- czytanie pamięci lub plików gry, integrację przez addony;
- input broadcasting, multiboxing, wiele sesji naraz;
- nowe typy wyjść w schemacie profilu poza: jeden przycisk pada, jedna oś, jeden klawisz, jeden przycisk myszy, kółko, ruch myszy, akcja lokalna aplikacji;
- zależności Firebase, Crashlytics, Google Play Services (wymóg F-Droid);
- nazwy, ikony, grafiki i zrzuty z gier Blizzarda oraz nazwę „Moonlight” w zasobach ([docs/naming.md](docs/naming.md)).

Zmiany w `ActionPolicy`, w walidatorze profilu albo w testach polityki wymagają przeglądu przez opiekuna i uzasadnienia w opisie PR.

## Jak zgłosić zmianę

1. Otwórz issue (szablony w `.github/ISSUE_TEMPLATE/`), jeśli zmiana jest większa niż poprawka.
2. Fork, gałąź `feature/<krótki-opis>` albo `fix/<krótki-opis>`.
3. Commity podpisane DCO: `git commit -s` (linia `Signed-off-by:`). Nie wymagamy CLA, bo kod upstreamu na GPLv3 i tak nie może zmienić licencji.
4. PR z wypełnioną checklistą fair-play (szablon PR).

## Styl

- Nowy kod: Kotlin (JVM target 17). Kod upstreamu (`:app`, Java): zmieniaj minimalnie, żeby nie utrudniać rebase. Hooki w `Game.java` trzymaj w `patches/` i w granicach ok. 50 linii.
- Identyfikatory po angielsku. Komentarze i dokumentacja deweloperska po polsku. Dokumenty publiczne (README, FAIR_PLAY, CONTRIBUTING, SECURITY) mają wersję angielską (`*.md`, domyślną) i polską (`*.pl.md`); zmieniaj obie.
- `kotlin.code.style=official`, formatowanie według `.editorconfig`. ktlint i detekt dojdą do CI (nowe moduły).
- Bez refleksji i magii w ścieżce wejścia. W ścieżce krytycznej (dotyk → wysyłka) bez alokacji per zdarzenie, jeśli to możliwe.
- Granice modułów i wspólne typy są opisane w `CONTRACT.md`. Zmiany sygnatur notuj w `CONTRACT-NOTES.md`.

## Testy

- `./gradlew :control-model:test :control-engine:test` przed każdym PR.
- Każda nowa funkcja wejścia (gest, typ kontrolki, tryb) potrzebuje:
  - testu jednostkowego z `FakeInputSink`;
  - rozszerzenia testów właściwości polityki (nazwa klasy zawiera `Policy`, moduł `:control-engine`), tak żeby nowe zachowanie było objęte losowymi strumieniami zdarzeń.
- Testy właściwości z pełną liczbą iteracji: `-Pthumbcast.policy.iterations=100000`.
- Nowe presety w `presets/` muszą przechodzić `ProfileValidator` (test w `:control-model`).
- Zmiany w zachowaniu w grze opisz wynikiem odpowiedniego scenariusza z [docs/test-plan.md](docs/test-plan.md).

## Licencja wkładu

Wysyłając PR, zgadzasz się na udostępnienie wkładu na GPL-3.0-or-later. Grafiki: CC BY-SA 4.0 albo GPL.
