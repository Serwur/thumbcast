# Handoff (11.11.2026, aktualizacja po krokach 1–3)

Dla nowej sesji (Claude Code na PC z Android Studio i internetem). Najpierw przeczytaj `CLAUDE.md`, potem ten plik, `docs/DECISIONS.md` i `docs/NEXT-STEPS.md`.

## Cel
Android + PC: granie w WoW Forever (i inne gry z obsługą pada) przez streaming z własnego PC, z dotykowym sterowaniem: wirtualny joystick, przyciski skilli, gesty, opcjonalnie pad Bluetooth. Docelowo aplikacja do udostępnienia innym.

## Architektura (skrót)
Host: Sunshine (lub Apollo/Vibepollo). Pad w Windows: ViGEmBus 1.17+ albo płatny Virtual HID Driver. Klient: fork `moonlight-stream/moonlight-android` (v12.2, GPLv3) z naszą warstwą dotyku (`control-model`, `control-engine`, `control-android`, `patches/`). Zasada: 1 dotyk = 1 wejście, bez makr (`FAIR_PLAY.md`).

## Stan
- **Krok 1 zrobiony (11.11.2026, scalony do lokalnego `main`, nie wypchnięty).** Historia upstreamu v12.2 scalona z repo (DECISIONS.md pkt j), remote `upstream` dodany, submoduł `moonlight-common-c` zainicjowany. Usunięte grafiki i metadane marki upstreamu. `:app` ma `applicationId` `io.github.serwur.thumbcast`, `minSdk 26`, bez flavoru `root` i sufiksu `.unofficial`, podpis release z właściwości `thumbcastKeystore*`, zależność od `:control-android`.
- Sprawdzone buildem: czysty upstream v12.2 (`assembleNonRootDebug`), a po scaleniu `:app:assembleNonRootDebug` i `:app:assembleNonRootRelease` (R8, APK niepodpisany w `app/build/outputs/apk/nonRoot/release/`). Kod główny `:control-model`, `:control-engine`, `:control-android` kompiluje się bez poprawek (1 ostrzeżenie: `HAPTIC_FEEDBACK_ENABLED` deprecated w `OverlayHaptics.kt:36`). **Testy nie były uruchamiane ani kompilowane.**
- **Krok 2 zrobiony (11.11.2026, gałąź `thumbcast/hooks`, nie wypchnięta).** Fasady Java, preferencje (z wymuszeniem `onscreenController=true`), presety w `assets/presets/` i hooki w `Game.java` nałożone wg `patches/APPLY.md`; różnice względem opisów są na początku tego pliku. Diff: `patches/0001-thumbcast.diff`. `:app:assembleNonRootDebug` i `Release` przechodzą. Na urządzeniu nie uruchamiane.
- **Krok 3 zrobiony (11.11.2026), bez poprawek w kodzie.** `:control-model:test` 86/86, `:control-engine:test` 62/62, `*Policy*` z `-Pthumbcast.policy.iterations=100000` zielone (property test ok. 24 s wobec ok. 1 s przy 1000, więc właściwość dociera do testów), `:control-android:testDebugUnitTest` 32/32, `:control-android:lint` bez uwag. `connectedDebugAndroidTest` nie uruchamiany (brak urządzenia).
- **CI (11.11.2026).** `ci.yml` zielony na `main` (`jvm-tests`, `policy-tests`, `lint`); wszystkie workflowy na `./gradlew`. Dodany job `app-build`: `:app:assembleNonRootDebug` (APK debug jako artefakt) i `:app:lintNonRootDebug` (lokalnie 0 błędów). `app-build`, `release.yml` i `emulator-tests.yml` nie były jeszcze uruchomione na GitHub; ich polecenia Gradle przechodzą lokalnie (release z testowym keystore). Szczegóły: `docs/NEXT-STEPS.md`, sekcja 6, „CI”.
- Założenia o upstreamie zweryfikowane z moonlight-android v12.2 (patrz `CLAUDE.md`), ale tylko ze streszczeń kodu. Patche to opisy kroków z kotwicami, nie pliki `.patch`.
- Zatwierdzone decyzje: `docs/DECISIONS.md` (pkt i): wymuszamy `onscreenController=true`, usuwamy flavor `root` i sufiks `.unofficial`.

## Następne kroki (w kolejności)
1. ~~Fork `moonlight-stream/moonlight-android`, scalenie z tym repo wg `docs/NEXT-STEPS.md` (sekcje 1–2). Upewnij się, że czysty upstream się buduje (`./gradlew assembleNonRootDebug`).~~ Zrobione 11.11.2026 (patrz Stan).
2. ~~Zastosuj `patches/APPLY.md`, po nałożeniu zrób własny `git diff v12.2`.~~ Zrobione 11.11.2026 (patrz Stan).
3. ~~`./gradlew :control-model:test :control-engine:test`, popraw błędy kompilacji. Potem testy `*Policy*` z `-Pthumbcast.policy.iterations=100000`.~~ Zrobione 11.11.2026 (patrz Stan).
4. Zbuduj APK, sparuj z Sunshine w LAN, sprawdź stream pulpitu.
5. Test T-03 w grze (`docs/test-plan.md`, sekcja 4): czy ruch myszy przełącza HUD gry z trybu pada. Od niego zależy tryb hybrydowy.
6. Strojenie stałych na prawdziwym telefonie: promień pływającego joysticka (15% krótszego boku), czułość kamery (1,5 px/ms), tolerancja trafienia (max(8 px, 20%)).

## Otwarte decyzje
Landscape lock (DECISIONS.md pkt h). Fork vs submoduł i `applicationId` rozstrzygnięte 11.11.2026 (pkt j). `LICENSE` zgodny z gnu.org (11.11.2026).

## Znane ryzyka
- Płatny Virtual HID Driver: 14,99 USD/rok lub 49,99 USD jednorazowo (5 maszyn, internet przy tworzeniu pada). ViGEmBus jest zarchiwizowany od XI 2023 i daje tylko Xbox 360/DS4.
- Sprawdzone 11.11.2026, testy charakteryzujące w `control-engine/.../KnownRisksTest.kt` i 2 testy w `ProfileValidatorTest` (asercje „do decyzji” odwrócić razem z poprawką):
  - **Gałka RIGHT + kamera `pad_right_stick` (do decyzji):** walidator to przepuszcza, tak samo jak dwie gałki na jednej osi. Silnik nadpisuje oś ostatnim zapisem. Po UP palca kamery zostaje jej wychylenie, dopóki palec gałki się nie ruszy. Wygaszanie kamery po nadpisaniu osi daje naruszenie R7. Dokumenty tego nie rozstrzygają. Opcje: (A) walidator odrzuca dwa źródła jednej osi (rekomendacja; gałka RIGHT działa z kamerą `mouse_look`); (B) wyłączna własność osi, pierwszy palec wygrywa; (C) przy zwolnieniu przywracać wartość pozostałego właściciela.
  - **`chord` (do decyzji):** przy `allowChords = true` walidator przepuszcza akord, a silnik po cichu wysyła tylko `output`. Wdrożenie oznacza 2 wyjścia z jednego dotyku, czyli wyjątek od zasady („szara strefa” w FAIR_PLAY). Wymaga osobnej reguły w ActionPolicy i zmiany właściwości „krawędzi ≤ DOWN”. Opcje: (A) wdrożyć według FAIR_PLAY; (B) do czasu wdrożenia walidator odrzuca `chord` także przy `allowChords = true`; (C) usunąć `chord`/`allowChords` ze schematu (migracja).
  - **`sprintLock` (no-op, bez ryzyka):** definicję z PLAN 5.4 (poza obrysem 100%, tylko dopóki palec dotyka) silnik spełnia bez flagi. Zatrzask po puszczeniu palca (auto-bieg) łamałby zasadę, więc odpada. Do wyboru: zostawić jako no-op, nadać znaczenie histerezy (100% aż do powrotu w martwą strefę, nadal tylko z palcem) albo usunąć pole (migracja).
  - **Long-press bez ticków (nie dotyczy wysyłanej ścieżki):** `ThumbcastOverlayFactory` (używana w `Game.java`) podaje `TickHandler`, a overlay woła `onTick` co klatkę, dopóki dotyka palec. Long-press odpala w cyklu TICK najpóźniej klatkę po `minMs`, z palcem na ekranie. Ograniczenie dotyczy tylko wywołań bez ticków (`tickHandler = null`, przyszły tryb testu edytora).
- Zachowanie Sunshine/Apollo wobec pakietów pada nr 0 przy `multiController=false` nieprzetestowane.

## Środowisko
- Sesja z 10.11.2026: cloud, bez kompilatora i bez sieci w powłoce. Serwer pośredniczący blokował zmiany ustawień repo i gnu.org.
- Sesja z 11.11.2026 (krok 1): Windows 11, Android Studio 2026.2.2. Build działa z `JAVA_HOME` = JBR Android Studio (`C:\Program Files\Android\Android Studio\jbr`, JDK 25); systemowa Java to 1.8 i się nie nadaje. `local.properties` (poza gitem): `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk` (ukośniki zwykłe, bo `\U` to sekwencja ucieczki). NDK 29.0.14206865 AGP doinstalował sam przy pierwszym buildzie.
