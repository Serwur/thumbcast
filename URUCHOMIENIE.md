# Jak to uruchomić (od zera do pierwszego testu w grze)

Repo ma docelowo nazywać się `thumbcast` (do czasu zmiany w GitHub Settings: `Serwur/wow-portable`). Plan: `docs/PLAN.md`. Decyzje, które mają pierwszeństwo przed planem: `docs/DECISIONS.md`.

Stan projektu: **szkielet napisany offline. Nic nie było kompilowane ani testowane.** Spodziewaj się poprawek przy pierwszym buildzie. Aplikacja nie jest jeszcze gotowa do użycia, to baza do dalszej pracy.

## Co jest w repo
- `control-model/`: profil sterowania (JSON), walidacja, migracje. `presets/`: gotowe układy Pad+ i klawiatura+mysz.
- `control-engine/`: silnik wejścia (joystick, przyciski, gesty) i `ActionPolicy` (zasada 1 dotyk = 1 wejście).
- `control-android/`: nakładka na obraz streamu i adaptery do hosta.
- `patches/`: zmiany do wprowadzenia w forku Moonlight (`APPLY.md`).
- `docs/`: plan testów, konfiguracja hosta, Tailscale, dystrybucja.

Repo **nie zawiera** kodu Moonlight. Aplikacja powstaje przez scalenie tych modułów z forkiem `moonlight-stream/moonlight-android`.

## Krok 1. PC z grą (host)
1. Zainstaluj Sunshine (LizardByte) na PC z WoW Forever, wg `docs/host-setup.md`.
2. Pad wirtualny w Sunshine wymaga sterownika. Darmowa ścieżka: **ViGEmBus 1.17+** (web UI Sunshine → Troubleshooting → zainstaluj; potem `gamepad_driver = vigembus`, `gamepad = x360`; projekt zarchiwizowany w XI 2023). Płatna ścieżka: LizardByte Virtual HID Driver (14,99 USD/rok albo 49,99 USD jednorazowo, 5 maszyn). Szczegóły: `docs/host-setup.md`, `docs/DECISIONS.md`.
3. Bez żadnego sterownika pada używaj trybu Klawiatura+Mysz: klawiatura i mysz działają bez niego.

## Krok 2. Środowisko na komputerze z internetem
Android Studio (zgodne z AGP 9.4+), SDK platforma 37, NDK 29.0.14206865 + CMake, JDK 17, Git.

## Krok 3. Fork Moonlight i scalenie
Pełna procedura z tabelą konfliktów: **`docs/NEXT-STEPS.md`**, sekcje 1 i 2. W skrócie:
```bash
git clone --recurse-submodules <twój fork moonlight-android> thumbcast && cd thumbcast
# skopiuj do korzenia: control-model control-engine control-android presets patches docs gradle/libs.versions.toml itd.
# usuń settings.gradle i build.gradle (Groovy), zostają wersje .kts z tego repo; odkomentuj include(":app")
```

## Krok 4. Patche do Moonlight
Wg `patches/APPLY.md`: hook w `Game.java`, preferencja `control_layer`, nowy `applicationId`, implementacje fasad.
**Sprawdź stałe w `HostProtocol` z kodem `moonlight-common-c`**, bo były pisane z pamięci.

## Krok 5. Build i testy
```bash
./gradlew :control-model:test :control-engine:test
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
./gradlew :app:assembleNonRootDebug     # nazwa wariantu do potwierdzenia
```
Zainstaluj APK na telefonie, sparuj z Sunshine w LAN.

## Krok 6. Test T-03 w grze
Procedura w `docs/test-plan.md` (sekcja 4): czy ruch myszy przełącza HUD WoW Forever z trybu pada. Od wyniku zależy, czy tryb hybrydowy ma sens.

## Poza domem
`docs/tailscale.md`.
