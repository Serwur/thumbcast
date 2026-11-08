## Opis

<!-- Co zmienia ten PR i dlaczego. Link do issue: Closes #... -->

## Rodzaj zmiany

- [ ] Poprawka błędu
- [ ] Nowa funkcja
- [ ] Dokumentacja / CI
- [ ] Zmiana w kodzie upstreamu (`:app` / `patches/`)

## Checklista fair-play (wymagana)

Zasady: [FAIR_PLAY.md](../FAIR_PLAY.md). PR, który nie spełnia którejkolwiek z nich, zostanie zamknięty.

- [ ] Każdy dotyk daje co najwyżej jedno wejście logiczne (1 dotyk = 1 wejście).
- [ ] Brak makr, sekwencji, combo, turbo, auto-repeat po stronie aplikacji.
- [ ] Brak timerów, opóźnień i wątków generujących naciśnięcia bez dotyku.
- [ ] Brak analizy obrazu strumienia (odczyt pikseli, OCR, CV).
- [ ] Brak czytania pamięci/plików gry i wymagań addonów.
- [ ] Brak nowych typów wyjść w schemacie profilu poza dozwolonymi (jeśli są: uzasadnienie poniżej).
- [ ] Zmiany wejścia są objęte testami właściwości polityki (`*Policy*` w `:control-engine`).
- [ ] Brak znaków towarowych gier i nazwy „Moonlight” w nazwach, zasobach, ikonach i metadanych.
- [ ] Brak Firebase / Crashlytics / Play Services.

## Testy

- [ ] `./gradlew :control-model:test :control-engine:test` lokalnie
- [ ] Testy polityki z `-Pthumbcast.policy.iterations=100000`
- [ ] Scenariusze z `docs/test-plan.md` (jeśli zmiana działa w grze): <!-- np. T-01, T-03 + wynik -->

## Licencja

- [ ] Commity mają `Signed-off-by:` (DCO, `git commit -s`); wkład na GPL-3.0-or-later.
