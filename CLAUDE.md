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

## Dokumenty i decyzje
- `docs/PLAN.md`: plan techniczny (wygenerowany 8.11.2026, kopia źródłowa, nie poprawiać treści).
- `docs/DECISIONS.md` (10.11.2026) **ma pierwszeństwo przed PLAN.md**. Skrót: host = Sunshine (GPLv3) lub forki Apollo/Vibepollo; pad w Windows = ViGEmBus 1.17+ (darmowy, zarchiwizowany) albo płatny LizardByte Virtual HID Driver; klient wysyła standardowe pakiety pada niezależnie od sterownika hosta; własnego streamingu ani sterownika nie robimy; zdalnie przez Tailscale/ZeroTier (nie port forwarding, nie tunele HTTP); tryb Klawiatura+Mysz to plan awaryjny dla hostów bez pada.
- Nazwa repo ma być `thumbcast`; do zmiany w GitHub Settings przez właściciela repo nazywa się `Serwur/wow-portable`. Agent nie zmienia ustawień repo.
- Otwarte decyzje: fork vs submoduł, `applicationId`, landscape lock (lista w DECISIONS.md, pkt h).
- Konfiguracja hosta i testy: `docs/host-setup.md`, `docs/test-plan.md` (hosty H1 Virtual HID, H2 ViGEmBus, H3 bez pada, H4 macOS; scenariusz T-10 dla trybu bez pada).

## Uwagi
- Kod powstał offline, bez kompilatora. Pierwszy build wymaga poprawek. Start: `URUCHOMIENIE.md`, `docs/NEXT-STEPS.md`.
- `onTick(nowMs)` w `InputEngine` jest poza kontraktem, ale używany przez nakładkę.
- `LICENSE` zawiera pełny tekst GPLv3 (kopia z Debiana, `/usr/share/common-licenses/GPL-3`, 35149 B, SHA-256 `3972dc97...6986`; z gnu.org nie pobrano, bo proxy blokuje; porównać z oficjalnym `gpl-3.0.txt` przy okazji); nagłówek projektu i SPDX są w `NOTICE`.
- Lint presetów: `.github/scripts/check_presets.py` sprawdza pola `type`/`behavior` i klucze JSON, nie dowolne napisy (etykieta „Delay” jest dozwolona). Uruchamiany w jobie `lint`.
- `gradle/libs.versions.toml` ma aliasy `androidx-test-runner` (1.7.0) i `androidx-test-ext-junit` (1.3.0). Zależności `androidTestImplementation` w `control-android/build.gradle.kts` nadal zakomentowane (plik należy do modułu Android); odkomentować po pierwszym buildzie.
- `:control-android` nie używa `kotlinx.serialization` bezpośrednio (tylko `ProfileCodec` z `:control-model`), więc plugin `kotlin-serialization` jest tam zbędny.

## Upstream zweryfikowany (moonlight-android v12.2, b48494cb96bf, 10.11.2026)
- W trybie Thumbcast `PreferenceConfiguration` musi wymuszać `onscreenController=true` (bit 0 maski pada, landscape, rumble). Patrz `patches/preferences.patch.md`.
- `KeyboardTranslator.translate(int, int)` ma 2 parametry. `applicationId` ustawia się we flavorze `nonRoot`; flavor `root` do usunięcia (maxSdk 25 vs minSdk 26).
- Patche to opisy kroków z kotwicami z v12.2, nie gotowe pliki `.patch`. Po nałożeniu zrób własny `git diff v12.2`.
- Nic z `patches/` nie było stosowane ani kompilowane.
