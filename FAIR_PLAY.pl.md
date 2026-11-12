# Zasady fair play

<p><a href="FAIR_PLAY.md">English</a> · <b>Polski</b></p>

Thumbcast to kontroler, a nie bot. Ten dokument opisuje, co aplikacja robi, czego nie robi i jakie ryzyko ponosisz, grając z nią w gry z regulaminem zabraniającym automatyzacji.

**Thumbcast nie jest zatwierdzony przez Blizzard Entertainment ani przez żadnego innego wydawcę gier.** Nie ma oficjalnej zgody na to ani na żadne inne narzędzie do mapowania wejścia.

## Zasada: 1 dotyk = 1 wejście

Każdy dotyk ekranu daje co najwyżej jedno wejście logiczne do gry: jeden przycisk pada, jedną oś, jeden klawisz, jeden przycisk myszy, kółko albo ruch myszy. Wejście trwa dokładnie tak długo, jak palec dotyka kontrolki.

W kodzie wymuszają to:
1. **`ActionPolicy`** (moduł `:control-engine`): jeden wskaźnik (palec) trzyma najwyżej jedno wejście naraz. Każda zmiana stanu wyjścia musi mieć przyczynę w zdarzeniu dotyku z tego samego cyklu obsługi. Zdarzenia bez przyczyny (z timera lub innego wątku) są odrzucane i logowane.
   - Wyjątek 1: upstream ponawia wysłanie **identycznego** stanu pada po 25, 50 i 75 ms. To obejście gubienia pakietów, a nie nowe naciśnięcia.
   - Wyjątek 2: wychylenie kamery wygasa do zera po zatrzymaniu palca.
2. **Schemat profilu** (moduł `:control-model`): każda kontrolka ma dokładnie jedno wyjście. Typy `sequence`, `macro`, `repeat`, `turbo`, `delay` i `toggle_auto` nie istnieją. Parser odrzuca nieznane pola i typy, więc import cudzego profilu nie doda automatyzacji.
3. **Testy właściwości w CI** (wymagany check `policy-tests`): dla 100 000 losowych strumieni dotyku liczba wciśnięć na wyjściu nie może przekroczyć liczby dotknięć z przypisanym wyjściem, a każde wciśnięcie musi mieć przyczynę. Naruszenie blokuje merge i release.

## Czego aplikacja nie robi (i nie będzie robić)

- Makra, sekwencje, „rotacje” rzucane jednym dotykiem.
- Turbo, auto-fire, auto-repeat po stronie aplikacji.
- Opóźnienia i timery generujące naciśnięcia.
- Analiza obrazu strumienia (OCR, wykrywanie obiektów, reakcje na stan gry). Aplikacja nie czyta pikseli.
- Czytanie pamięci lub plików gry, wymaganie addonów.
- Input broadcasting i multiboxing: jedna sesja, jeden host.
- Sterowanie analogowe „przez mysz” w trybie klawiatury (automatyczny obrót).

PR-y dodające którąkolwiek z tych funkcji będą zamykane ([CONTRIBUTING.md](CONTRIBUTING.pl.md)).

## Szara strefa: akordy

Akord to jeden dotyk, który wciska dwa wejścia naraz, np. Shift+1 albo LB+RB. Jest to odstępstwo od zasady 1 dotyk = 1 wejście.

**Od 11.11.2026 Thumbcast w ogóle nie obsługuje akordów.** Walidator profilu odrzuca każdy przycisk z polem `chord`, także przy `global.allowChords = true`, więc import cudzego profilu z akordem kończy się błędem. Pola `chord` i `allowChords` zostały w schemacie tylko po to, by nie robić migracji, i nic nie włączają.

Zamiast akordu użyj osobnych przycisków modyfikatorów w trybie `hold` (trzymaj) lub `latch` (dotknij, by wcisnąć, dotknij ponownie, by zwolnić). Shift+1 to wtedy dwa dotyki: modyfikator `SHIFT` i przycisk `1`. `latch` to nadal 1 dotyk = 1 zmiana stanu.

## Ryzyko: uczciwie

Stan wiedzy z listopada 2026 (źródła w planie projektu):

- **EULA Blizzarda** (wersja z 21.03.2024) zakazuje:
  - botów, czyli oprogramowania „that allows the automated control of a Game” (1.C.ii.2);
  - nieautoryzowanego oprogramowania, które „changes and/or facilitates the gameplay” (1.C.ii.4);
  - nieautoryzowanego streamingu z chmury osób trzecich (1.C.v). Thumbcast streamuje z Twojego własnego PC. Uważamy, że to nie jest „third-party cloud”, ale to nasza interpretacja, a nie stanowisko Blizzarda.
- **Input broadcasting** jest zakazany od 3.11.2020.
- **Nieformalna reguła wsparcia Blizzarda** (2021): „one key press = one action is the general rule of thumb”. 29.10.2026 wsparcie Blizzarda odmówiło oceniania konkretnych konfiguracji.
- **Historia:** w 2021 krążyły doniesienia o banach za narzędzia mapujące pad (WoWMapper, ConsolePort). Nie zostały potwierdzone, a zgłoszone bany cofnięto po apelacji.

Co z tego wynika:
- Remapowanie 1:1 jest w praktyce tolerowane, ale **nie jest gwarantowane**. Ryzyko konsekwencji dla konta jest niskie, ale **niezerowe**.
- Najbezpieczniejszy jest profil pada: wysyła natywne wejście pada, które gra oficjalnie obsługuje.
- Decyzja o użyciu aplikacji z daną grą i ryzyko z nią związane należą do Ciebie. Autorzy nie odpowiadają za decyzje wydawców gier.

Gdy wydawca zmieni stanowisko, zaktualizujemy ten dokument. Jeśli znasz nowsze oficjalne informacje, otwórz issue ze źródłem.
