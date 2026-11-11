# Handoff (11.11.2026, aktualizacja po kroku 1)

Dla nowej sesji (Claude Code na PC z Android Studio i internetem). Najpierw przeczytaj `CLAUDE.md`, potem ten plik, `docs/DECISIONS.md` i `docs/NEXT-STEPS.md`.

## Cel
Android + PC: granie w WoW Forever (i inne gry z obsługą pada) przez streaming z własnego PC, z dotykowym sterowaniem: wirtualny joystick, przyciski skilli, gesty, opcjonalnie pad Bluetooth. Docelowo aplikacja do udostępnienia innym.

## Architektura (skrót)
Host: Sunshine (lub Apollo/Vibepollo). Pad w Windows: ViGEmBus 1.17+ albo płatny Virtual HID Driver. Klient: fork `moonlight-stream/moonlight-android` (v12.2, GPLv3) z naszą warstwą dotyku (`control-model`, `control-engine`, `control-android`, `patches/`). Zasada: 1 dotyk = 1 wejście, bez makr (`FAIR_PLAY.md`).

## Stan
- **Krok 1 zrobiony (11.11.2026, gałąź `thumbcast/merge-upstream-v12.2`, nie scalona do `main`, nie wypchnięta).** Historia upstreamu v12.2 scalona z repo (DECISIONS.md pkt j), remote `upstream` dodany, submoduł `moonlight-common-c` zainicjowany. Usunięte grafiki i metadane marki upstreamu. `:app` ma `applicationId` `io.github.serwur.thumbcast`, `minSdk 26`, bez flavoru `root` i sufiksu `.unofficial`, podpis release z właściwości `thumbcastKeystore*`, zależność od `:control-android`.
- Sprawdzone buildem: czysty upstream v12.2 (`assembleNonRootDebug`), a po scaleniu `:app:assembleNonRootDebug` i `:app:assembleNonRootRelease` (R8, APK niepodpisany w `app/build/outputs/apk/nonRoot/release/`). Kod główny `:control-model`, `:control-engine`, `:control-android` kompiluje się bez poprawek (1 ostrzeżenie: `HAPTIC_FEEDBACK_ENABLED` deprecated w `OverlayHaptics.kt:36`). **Testy nie były uruchamiane ani kompilowane, patche nie stosowane.**
- Założenia o upstreamie zweryfikowane z moonlight-android v12.2 (patrz `CLAUDE.md`), ale tylko ze streszczeń kodu. Patche to opisy kroków z kotwicami, nie pliki `.patch`.
- Zatwierdzone decyzje: `docs/DECISIONS.md` (pkt i): wymuszamy `onscreenController=true`, usuwamy flavor `root` i sufiks `.unofficial`.

## Następne kroki (w kolejności)
1. ~~Fork `moonlight-stream/moonlight-android`, scalenie z tym repo wg `docs/NEXT-STEPS.md` (sekcje 1–2). Upewnij się, że czysty upstream się buduje (`./gradlew assembleNonRootDebug`).~~ Zrobione 11.11.2026 (patrz Stan). Do zrobienia przez właściciela: przegląd gałęzi, scalenie do `main`, push.
2. Zastosuj `patches/APPLY.md`, po nałożeniu zrób własny `git diff v12.2`.
3. `./gradlew :control-model:test :control-engine:test`, popraw błędy kompilacji. Potem testy `*Policy*` z `-Pthumbcast.policy.iterations=100000`.
4. Zbuduj APK, sparuj z Sunshine w LAN, sprawdź stream pulpitu.
5. Test T-03 w grze (`docs/test-plan.md`, sekcja 4): czy ruch myszy przełącza HUD gry z trybu pada. Od niego zależy tryb hybrydowy.
6. Strojenie stałych na prawdziwym telefonie: promień pływającego joysticka (15% krótszego boku), czułość kamery (1,5 px/ms), tolerancja trafienia (max(8 px, 20%)).

## Otwarte decyzje
Landscape lock (DECISIONS.md pkt h). Fork vs submoduł i `applicationId` rozstrzygnięte 11.11.2026 (pkt j). Porównaj `LICENSE` z oficjalnym tekstem GPLv3 (kopia z Debiana).

## Znane ryzyka
- Płatny Virtual HID Driver: 14,99 USD/rok lub 49,99 USD jednorazowo (5 maszyn, internet przy tworzeniu pada). ViGEmBus jest zarchiwizowany od XI 2023 i daje tylko Xbox 360/DS4.
- Nierozstrzygnięte: stick RIGHT razem z kamerą na prawej gałce (walidator), `chord`/`sprintLock` ignorowane przez silnik, long-press bez ticków działa dopiero przy puszczeniu palca.
- Zachowanie Sunshine/Apollo wobec pakietów pada nr 0 przy `multiController=false` nieprzetestowane.

## Środowisko
- Sesja z 10.11.2026: cloud, bez kompilatora i bez sieci w powłoce. Serwer pośredniczący blokował zmiany ustawień repo i gnu.org.
- Sesja z 11.11.2026 (krok 1): Windows 11, Android Studio 2026.2.2. Build działa z `JAVA_HOME` = JBR Android Studio (`C:\Program Files\Android\Android Studio\jbr`, JDK 25); systemowa Java to 1.8 i się nie nadaje. `local.properties` (poza gitem): `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk` (ukośniki zwykłe, bo `\U` to sekwencja ucieczki). NDK 29.0.14206865 AGP doinstalował sam przy pierwszym buildzie.
