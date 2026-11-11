# CONTRACT-NOTES: uwagi do kontraktu (dopisywane przez agentów)
## SCAFFOLD

Autor: agent SCAFFOLD, 2026-11-07. Pliki: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `.github/`, `docs/`, `fastlane/`, dokumenty w katalogu głównym.

### Moduły w `settings.gradle.kts`
`:control-model`, `:control-engine`, `:control-android`. Zakomentowane: `:app` (po forku), `:control-editor` (E2).

### Aliasy version catalog (`gradle/libs.versions.toml`)

Pluginy (`alias(libs.plugins.<...>)`; w głównym `build.gradle.kts` zadeklarowane z `apply false`, więc w modułach używać `alias(...)` bez wersji):
| Alias w kodzie | Id | Wersja |
|---|---|---|
| `libs.plugins.kotlin.jvm` | `org.jetbrains.kotlin.jvm` | kotlin 2.4.21 |
| `libs.plugins.kotlin.serialization` | `org.jetbrains.kotlin.plugin.serialization` | kotlin 2.4.21 |
| `libs.plugins.kotlin.compose` | `org.jetbrains.kotlin.plugin.compose` | kotlin 2.4.21 (na przyszłość) |
| `libs.plugins.android.library` | `com.android.library` | agp 9.4.0 |
| `libs.plugins.android.application` | `com.android.application` | agp 9.4.0 |

Brak aliasu `kotlin-android`: AGP 9 ma wbudowany Kotlin, nie stosować `org.jetbrains.kotlin.android` w `:control-android`.

Biblioteki (`libs.<...>`):
| Alias w kodzie | Moduł | Wersja |
|---|---|---|
| `libs.kotlinx.serialization.json` | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 (DO ZWERYFIKOWANIA z Kotlin 2.4.21) |
| `libs.kotlin.test` | `org.jetbrains.kotlin:kotlin-test` | kotlin |
| `libs.kotlin.test.junit5` | `org.jetbrains.kotlin:kotlin-test-junit5` | kotlin |
| `libs.kotest.property` | `io.kotest:kotest-property` | 6.2.5 (DO ZWERYFIKOWANIA) |
| `libs.junit.bom` | `org.junit:junit-bom` | 6.1.3 |
| `libs.junit.jupiter` | `org.junit.jupiter:junit-jupiter` | 6.1.3 |
| `libs.junit.platform.launcher` | `org.junit.platform:junit-platform-launcher` | 6.1.3 (testRuntimeOnly, wymagany przez Gradle 9) |
| `libs.junit4` | `junit:junit` | 4.13.2 (runner Robolectric) |
| `libs.androidx.core` | `androidx.core:core` | 1.19.1 |
| `libs.androidx.core.ktx` | `androidx.core:core-ktx` | 1.19.1 (od 1.19 pusty artefakt zgodności) |
| `libs.androidx.annotation` | `androidx.annotation:annotation` | 1.11.0 |
| `libs.robolectric` | `org.robolectric:robolectric` | 4.17 |
| `libs.androidx.compose.bom` | `androidx.compose:compose-bom` | 2026.09.00 (na przyszłość) |
| `libs.androidx.compose.foundation` / `.material3` / `.ui.test.junit4` | Compose (wersja z BOM) | na przyszłość |

Bundle: `libs.bundles.jvm.test` = kotlin-test, kotlin-test-junit5, kotest-property, junit-jupiter.

Przykład dla modułu JVM:
```kotlin
plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.jvm.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

### Konwencje, na których polega CI (prośba do ENGINE i ANDROID)
- Główny `build.gradle.kts` ustawia `useJUnitPlatform()` dla modułów z pluginem `org.jetbrains.kotlin.jvm` i przekazuje do testów właściwość systemową `thumbcast.policy.iterations` (lokalnie 1000, w CI 100000). Testy właściwości polityki powinny czytać liczbę iteracji przez `System.getProperty("thumbcast.policy.iterations")?.toIntOrNull() ?: 1000`.
- **Wymagany check `policy-tests`** uruchamia `:control-engine:test --tests '*Policy*'`. Klasy testów polityki w `:control-engine` muszą mieć w nazwie `Policy` (np. `ActionPolicyPropertyTest`). Job failuje, gdy nie wykona się żaden test lub gdy jest jakakolwiek porażka.
- Job `lint` uruchamia `:control-android:lint` i odrzuca w `presets/` JSON-y, w których pole `"type"` (lub `"behavior"`) ma wartość `"sequence"`, `"macro"`, `"repeat"`, `"turbo"`, `"delay"` albo `"toggle_auto"`, lub w których któraś z tych nazw jest kluczem obiektu (skrypt `.github/scripts/check_presets.py`, zmiana z 10.11.2026). Etykiety i inne napisy nie są sprawdzane, więc `"label": "Delay"` jest dozwolone.
- `emulator-tests.yml` (ręczny) uruchamia `:control-android:connectedDebugAndroidTest`.
- `release.yml` oczekuje, że `:app` czyta podpis z właściwości Gradle `thumbcastKeystoreFile`, `thumbcastKeystorePassword`, `thumbcastKeyAlias`, `thumbcastKeyPassword` (opis w `docs/NEXT-STEPS.md`).

## MODEL

Autor: agent MODEL, 2026-11-07. Pliki: `control-model/**`, `presets/*.thumbcast.json`. Nazwy typów i sygnatury z kontraktu zostały zachowane. Poniżej uzupełnienia i doprecyzowania, na których mogą polegać ENGINE i ANDROID.

### Uzupełnienia typów (zgodnie z przykładem z planu 5.3)
- `Profile` ma dodatkowe pole `reference: Reference(aspect, unit)`. Kontrakt wymienia je w opisie JSON, ale nie w sygnaturze. Pola `tags`, `reference`, `handedness`, `global` i `gestures` mają wartości domyślne.
- `GlobalSettings(opacity, haptics: Haptics(enabled, strength, onPress, onRelease), slideToPress, autoHideOnGamepad, allowChords = false)`.
- `CameraDrag(zone: GestureZone, output: CameraOutput, sensitivity, curve, deadzonePx, invertY, maxDeflection, decayMs)`, `TapTarget(enabled, output, maxMovePx, maxMs)`, `LongPress(enabled, output, minMs)`, `PinchZoom(enabled, output, pxPerNotch)`, `EdgeSwipe(enabled, output)`. Pola `output` gestów to osobne enumy: `CameraOutput { PAD_RIGHT_STICK, MOUSE_LOOK }`, `TapOutput`, `LongPressOutput`, `PinchOutput`, `EdgeSwipeOutput`.
- Nowe enumy: `Shape { CIRCLE, ROUNDED_RECT, RECT }`, `CurveType { LINEAR, POWER }`, `HapticEffect`, `GestureZone { RIGHT_FREE, LEFT_FREE }`.
- `StickControl` ma też pola `sprintLock` (z przykładu) i `icon`. Wszystkie pola poza `id`/`rect` mają wartości domyślne, a `label` domyślnie wynosi `null`.
- `ButtonControl` ma też `icon` i opcjonalne `chord: Output?`, domyślnie `null`. **Od 11.11.2026 walidator odrzuca każdy akord, także przy `global.allowChords == true`** (komunikat z fragmentem `ProfileValidator.ERR_CHORD_UNSUPPORTED`, patrz FAIR_PLAY.md). Pola `chord` i `allowChords` zostają w schemacie bez migracji.
- Od 11.11.2026 w trybie `PAD_PLUS` jedna gałka pada może mieć tylko jedno źródło. Walidator odrzuca dwie kontrolki `stick` z tym samym `output` oraz `stick` z `right_stick` przy `gestures.cameraDrag.output = PAD_RIGHT_STICK` (komunikat z fragmentem `ProfileValidator.ERR_STICK_AXIS_CONFLICT`). W `KEYBOARD_MOUSE` reguła nie obowiązuje.
- Od 11.11.2026 `GesturesConfig` ma pola `freeZone: FreeZoneMode = CAMERA` (`"camera"` | `"touchpad"`) i `touchpad: Touchpad(sensitivity = 1.0, scrollPxPerNotch = 24)`, dodane bez podnoszenia schematu (wartości domyślne). Przy `TOUCHPAD` kamera nie istnieje, więc `cameraDrag.output = PAD_RIGHT_STICK` nie zajmuje prawej gałki (walidator dopuszcza wtedy kontrolkę `stick` z `right_stick`). Walidator: `touchpad.sensitivity` w (0..10], `touchpad.scrollPxPerNotch` 4..400. Tabela gestów touchpada: `control-model/docs/profile-schema.md`.
- `ModifierControl(id, rect, label, output, behavior, shape, icon)`: wyjście musi być modyfikatorem (`LT`/`RT`/`LB`/`RB` albo klawisz `SHIFT`/`CTRL`/`ALT`).
- `SystemControl(id, rect, label, output: Output.App, shape, icon)`: pole `output` ma typ zawężony do `Output.App`.
- `AppActions.ALL = {"menu", "soft_keyboard", "toggle_overlay"}`. `KeyCodes.ALL` to `A`–`Z`, `0`–`9`, `F1`–`F12`, `SHIFT`, `CTRL`, `ALT`, `SPACE`, `ESC`, `TAB`, `ENTER`, `-`, `=` oraz strzałki `UP`, `DOWN`, `LEFT`, `RIGHT`. Mapowanie tych nazw na kody VK hosta należy do ANDROID.

### Kodowanie JSON wyjść
- Encoder zapisuje `Trigger` i `Stick` pod kluczem `pad` (`{"pad":"LT"}`, `{"pad":"left_stick"}`), tak jak w przykładzie z planu. Decoder przyjmuje dodatkowo aliasy `{"trigger":"LT"}` i `{"stick":"left"}`.
- `mouse` przyjmuje wartości `left`, `right`, `middle`, `wheel` i `look`. `MouseWheel` i `MouseLook` walidator dopuszcza **tylko w gestach** (pinchZoom, cameraDrag), nie w kontrolkach.
- Serializer `Output` jest podpięty na polach (`@Serializable(with = OutputSerializer::class)`), a nie na samym interfejsie. Nazwa interfejsu się nie zmienia.

### Semantyka, o której musi wiedzieć ENGINE
- W trybie `KEYBOARD_MOUSE` joystick ma nadal `output = Output.Stick(LEFT)`, ale walidator wymaga `kbmKeys`. Silnik w tym trybie powinien emitować klawisze z `kbmKeys` według `kbmThreshold` (8 kierunków) zamiast osi.
- W trybie `KEYBOARD_MOUSE` `gestures.cameraDrag.output` musi mieć wartość `MOUSE_LOOK`. W `PAD_PLUS` dopuszczalne są obie wartości.
- Gdy oba gesty są włączone, zachodzi `longPress.minMs > tapTarget.maxMs`, więc okna czasowe tapu i long-pressu się nie nakładają.
- `ProfileCodec.decode` uruchamia też `ProfileValidator.validate`, więc profil z `decode` jest zawsze poprawny. Dodatkowo istnieje `ProfileCodec.decodeUnvalidated`, przeznaczony dla edytora. Błąd walidacji to `ProfileValidationException(errors)`.
- Dostępne są też `ProfileValidator.validateRaw(JsonElement)` (zakazane typy i pola, liczba kluczy w `output`/`chord`) oraz `HandednessTransform.mirror` / `toHandedness`.

### Do zweryfikowania przy pierwszym buildzie
- Test właściwości używa `runBlocking` z kotlinx-coroutines, dociąganego przechodnio przez `kotest-property`. Jeśli coroutines nie przyjdą jako zależność `api`, trzeba dodać jawny alias (SCAFFOLD).

## ANDROID

Autor: agent ANDROID, 2026-11-07. Pliki: `control-android/**`, `patches/**`. Typy z kontraktu używane bez zmian.

### Założenia o typach ENGINE (sprawdzone na `control-engine/src/.../Types.kt` z 7.11, o ile plik już istniał)
- `RectPx(left, top, right, bottom: Int)`, prawa/dolna krawędź wyłączna: **zgodne** z `Types.kt` ENGINE. Konstruowany tylko w `control-android/.../overlay/EngineBridge.kt`.
- `Viewport(widthPx, heightPx, videoRect, videoRefWidth, videoRefHeight)`: wołamy 5-argumentowo, `safeArea` zostaje `null` (overlay nie liczy jeszcze WindowInsets).
- `InputEngine(profile, sink, clock, viewport)`, `onTouch`, `releaseAll()`, `setProfile`, `setViewport`, `violations` (używamy tylko `.size`): jak w kontrakcie. W chwili pisania klasa `InputEngine` jeszcze nie istniała w repo.
- **Poza kontraktem:** `InputEngine.onTick(nowMs: Long)`. ActionPolicy ENGINE opisuje cykl TICK „wywoływany przez InputEngine.onTick”. Overlay woła go raz na klatkę (`postOnAnimation`) tylko, gdy dotyka jakiś palec. Jedyne wywołanie: `ThumbcastOverlayFactory` (lambda `TickHandler`). Jeśli ENGINE nazwie to inaczej albo nie będzie potrzebne, zmienić tę jedną linię.
- `TouchEvent.actionPointerId = -1` dla `MOVE` i `CANCEL` (stała `MotionTranslator.NO_POINTER`). W `UP` lista `pointers` zawiera jeszcze podnoszony wskaźnik (z ostatnią pozycją), jak w `MotionEvent`.
- Overlay do silnika wysyła tylko palce (`TOOL_TYPE_FINGER`/`UNKNOWN`), najwyżej 10 naraz. `DOWN` przy zgubionym `UP` poprzedza syntetyczny `CANCEL`.
- `TouchEvent.timeMs` = `MotionEvent.getEventTime()` (baza `SystemClock.uptimeMillis()`), `Clock` w tej samej bazie (`EngineBridge.uptimeClock`).
- Ukrycie kontrolek (pad BT, `toggle_overlay`) = `engine.setProfile(profil bez stick/button/modifier)`; zostają kontrolki `system` i gesty. Przed tym `releaseAll()`.
- Wizualny hit test overlaya zakłada: `rect` znormalizowany względem całego overlaya, późniejsza kontrolka na liście jest na wierzchu. Jeśli silnik liczy inaczej (np. względem `safeArea`), rozjedzie się tylko podświetlenie.

### Założenia o MODEL
- `KeyCodes.ALL` mapowany w `AndroidKeyCodes` (test pokrycia w `SinksTest`). `AppActions.TOGGLE_OVERLAY` jest obsługiwany lokalnie w fabryce, a potem przekazywany do callbacku Game.
- `GlobalSettings.opacity`, `haptics` (`enabled/strength/onPress/onRelease`), `autoHideOnGamepad`; `Shape`, `HapticEffect.CLICK` (API 29+: `EFFECT_CLICK`).

### Prośby
- **SCAFFOLD:** dodać aliasy `androidx-test-runner` (`androidx.test:runner`) i `androidx-test-ext-junit` (`androidx.test.ext:junit`). Do tego czasu zależności `androidTest` w `control-android/build.gradle.kts` są zakomentowane. Testy JVM używają `libs.junit4` (runner JUnit 4 AGP), a nie JUnit Platform.
- **ENGINE:** czy silnik sam retransmituje stan pada (`ActionPolicy.retransmit`, cykl RETRANSMIT)? `patches/.../HostPadReporterImpl.java` powtarza ten sam stan po 25/50/75 ms, tak jak upstreamowy `VirtualController`. Jeśli silnik też to robi, jedno z nich trzeba wyłączyć (`new HostPadReporterImpl(handler, false)`).
- **ENGINE:** `EmitCauseListener` jako opcjonalny interfejs sinka: `CompositeInputSink` go jeszcze nie implementuje; nakładka debug mierzy opóźnienie we własnym `SendListener`. Można dodać później.

## ENGINE

Autor: agent ENGINE, 2026-11-07. Pliki: `control-engine/**`. Typy z kontraktu (`TouchAction`, `TouchPointer`, `TouchEvent`, `InputSink`, `Clock`, `Viewport`, `RectPx`, `InputEngine`, `PolicyViolation`) zachowane; typy modelu używane wprost z `app.thumbcast.model` (bez kopii).

### Doprecyzowania typów (kontrakt ich nie rozpisuje)
- `RectPx(left: Int, top: Int, right: Int, bottom: Int)`; prawa i dolna krawędź wyłączne (jak `android.graphics.Rect`); `width`, `height`, `contains(x, y)`. Zgodne z założeniem ANDROID.
- `Viewport(widthPx, heightPx, videoRect, videoRefWidth, videoRefHeight, safeArea: RectPx? = null)`. Szóste pole to rozszerzenie z wartością domyślną, więc wywołania 5-argumentowe się kompilują. Gdy `safeArea == null`, `rect` kontrolek liczony jest względem całego overlaya (tak samo jak wizualny hit test ANDROID). Gdy ANDROID zacznie podawać `safeArea`, powinien liczyć podświetlenie względem tego samego prostokąta.
- `PolicyViolation(timeMs, cycle: CycleKind?, pointerId, bindingId, effect: String, reason: String)` w `ActionPolicy.kt`.

### Poza kontraktem (dodatki ENGINE)
- `InputEngine.onTick(nowMs: Long)` oraz `onTick()` (z `Clock`). To tick czasu traktowany jak syntetyczny MOVE aktywnych palców. Służy wyłącznie do rozpoznania long-pressu trzymanego palca i do wygaszania kamery. **Bez aktywnego palca nic nie robi.** Overlay ANDROID woła go raz na klatkę, i tak ma zostać.
- Opcjonalny interfejs `EmitCauseListener` (`onEmitCause(cause: EmitCause?)`). Jeśli `InputSink` go implementuje, przed każdym wywołaniem metody sinka dostaje przyczynę (cykl, akcja dotyku, `timeMs`, `pointerId`, wiązanie). Używa go `FakeInputSink`, przyda się też overlayowi debug. Nie jest wymagany.
- `InputEngine.violationCount`, `InputEngine.padSnapshot()`, `InputEngine.activeProfile`, konstruktor `InputEngine(config: EngineConfig, profile, sink, clock, viewport)` (testy, przyszły „tryb testu” edytora).

### Zmiana kontraktu 11.11.2026: kółko poziome
- `InputSink.mouseHScroll(highResDelta: Int)`: nowa metoda abstrakcyjna (bez domyślnej implementacji, żeby żaden sink nie gubił po cichu przewijania). 120 = 1 ząbek, dodatnie = w prawo. `mouseWheel` bez zmian (dodatnie = w górę).
- W silniku efekt `HWheel(delta)` (obok `Wheel`). `OutputMapper` wysyła go tylko przy `delta != 0`. Dla `ActionPolicy` jest to wciśnięcie bez trwałego stanu, tak jak `Wheel`: wymaga przyczyny, palca w zdarzeniu i żywego palca, a w cyklu TICK jest odrzucane (R7).
- ANDROID: `HostConnection.sendMouseHighResHScroll(scrollAmount: Short)` = upstreamowe `NvConnection.sendMouseHighResHScroll(short)` (`public void`, sprawdzone w `app/src/main/java/com/limelight/nvstream/NvConnection.java`). `MouseSink.mouseHScroll` obcina do zakresu `Short` i pomija zero; `CompositeInputSink.mouseHScroll` deleguje do `MouseSink`. `HostConnectionImpl` (`:app`) deleguje 1:1.

### Semantyka, na której mogą polegać inni
- **Retransmisji stanu pada silnik NIE robi** (odpowiedź na pytanie ANDROID). `padState` jest wysyłany tylko przy zmianie, a retransmisję 25/50/75 ms zostawiamy w `HostPadReporterImpl` (upstream). Nie wyłączać jej.
- `releaseAll()` wysyła zerowy `padState` (o ile pad był wcześniej użyty w tej instancji silnika), puszcza wszystkie klawisze i przyciski myszy, czyści LATCH. `setProfile` zawsze robi `releaseAll`. `setViewport` z inną geometrią robi `releaseAll` tylko wtedy, gdy dotyka jakiś palec (LATCH bez palców przeżywa obrót).
- `TouchEvent` z `actionPointerId = -1` dla MOVE/CANCEL: zgodne. UP bez wcześniejszego DOWN jest ignorowany. Podwójny DOWN tego samego id kończy poprzedni palec (bez tapu i long-pressu), a potem zaczyna nowy.
- Leworęczność: `HandednessTransform.mirror` (MODEL) zapisuje odbite `rect` i strefę kamery w profilu, więc silnik **nie odbija profilu drugi raz** (`EngineConfig.leftHanded = false` z `ProfileAdapter`). Pole `leftHanded` służy tylko do odbicia w czasie działania (testy, podgląd).
- `ButtonControl.chord` jest ignorowany przez silnik: przycisk daje wyłącznie `output`. Od 11.11.2026 walidator (MODEL) odrzuca każdy akord, więc `ProfileCodec.decode` takiego profilu nie zwróci. Akordów nie wdrażamy (FAIR_PLAY.md).
- `StickControl.sprintLock` (od 11.11.2026) to histereza nasycenia: po dojściu do `|v| ≥ 1 - outerDeadzone` wychylenie zostaje 100% z kierunkiem za palcem, aż palec wróci w martwą strefę (`|v| ≤ deadzone`) albo zostanie podniesiony (UP/CANCEL/`releaseAll` → 0). Stan żyje tylko z palcem, zmienia się tylko w cyklu dotyku tego palca. `false` = zachowanie jak dotąd. Szczegóły: `control-engine/docs/engine.md`.
- Przyciski `MouseWheel` (1 ząbek) i `App` dają jedno zdarzenie przy DOWN; `MouseLook`/`Stick` na przycisku są ignorowane.

### Do zweryfikowania przy pierwszym buildzie
- Testy właściwości używają `kotlinx.coroutines.runBlocking` (przechodnio z `kotest-property`), tak samo jak MODEL.
- Nazwy klas testów polityki: `ActionPolicyTest`, `ActionPolicyPropertyTest` (pasują do `--tests '*Policy*'`). Liczba iteracji pochodzi z `thumbcast.policy.iterations`.
