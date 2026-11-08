# CLAUDE.md

Projekt Thumbcast: dotykowe sterowanie do streamingu gier z PC na Androida (Sunshine + fork Moonlight). Dokumenty po polsku, identyfikatory kodu po angielsku.

## Moduły
- `control-model` (JVM): profil JSON, walidacja, migracje. Zakazane typy: sequence, macro, repeat, turbo, delay, toggle_auto.
- `control-engine` (JVM): `InputEngine`, rozpoznawanie gestów, `ActionPolicy`. Wszystkie wyjścia idą przez politykę. Szczegóły: `control-engine/docs/engine.md`.
- `control-android`: `ThumbcastOverlay` i adaptery. Retransmisja stanu pada (25/50/75 ms) jest tylko w `HostPadReporterImpl`, silnik jej nie robi.
- `patches/`: zmiany do forka Moonlight, nie kompilują się w tym repo.

## Zasady
- 1 dotyk = 1 wejście. Bez makr, sekwencji, turbo, timerów generujących naciśnięcia (`FAIR_PLAY.md`).
- Nazwy, pakiety i zasoby bez „WoW", „Blizzard", „Moonlight".
- Kontrakt typów: `CONTRACT.md` i `CONTRACT-NOTES.md`.

## Uwagi
- Kod powstał offline, bez kompilatora. Pierwszy build wymaga poprawek. Start: `URUCHOMIENIE.md`, `docs/NEXT-STEPS.md`.
- `onTick(nowMs)` w `InputEngine` jest poza kontraktem, ale używany przez nakładkę.
