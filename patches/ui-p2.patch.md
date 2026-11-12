# Patch: ekran startowy (pakiet P2)

Dotyczy upstreamu v12.2 (`b48494cb96bf`). Nałożone 12.11.2026 w `app/` (bloki `THUMBCAST`). Specyfikacja: `docs/ui-audit.md`
(U1 hak, U2, U3, U8–U11, U13, U15–U18; D1, D5–D8). Pewny stan po scaleniu wszystkich pakietów: nowy `git diff v12.2 -- app/`
(`0001-thumbcast.diff` odświeża orkiestrator).

Zasada: upstreamowe układy zostają nietknięte. Kod upstreamu w bloku THUMBCAST ładuje nowy układ `thumbcast_*` z tymi samymi
identyfikatorami, których szuka (`findViewById`). Logika jest w nowych klasach `com.limelight.thumbcast.*`, więc bloki
w plikach upstreamu to głównie jednolinijkowe wywołania. Zastąpione linie upstreamu są w bloku jako komentarz `Upstream: ...`.

## Zmiany w plikach upstreamu

| Plik | Kotwica (v12.2) | Zmiana |
|---|---|---|
| `PcView.java` | pole `private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;` | pod nim pole `thumbcastEmptyState` (`ThumbcastLauncherUi.EmptyState`) |
| | `initializeViews()`: `setContentView(R.layout.activity_pc_view);` | → `R.layout.thumbcast_activity_pc_view` (1 zmieniona linia) |
| | koniec `initializeViews()`, przed `pcGridAdapter.notifyDataSetChanged();` | `thumbcastEmptyState.bind(this)` |
| | `onCreate`: po `inForeground = true;` | `ThumbcastOnboardingHook.onCreate(this, savedInstanceState)` (U1) |
| | `onDestroy()` po `super.onDestroy();` | `thumbcastEmptyState.release()`, `ThumbcastPairingDialog.dismiss(this)`; pod metodą nowa `onActivityResult` (wynik kreatora → `ThumbcastOnboardingHook.onActivityResult`) |
| | koniec `onCreateContextMenu` (po bloku `THUMBCAST_ADDRESSES_ID`) | U11: `menu.removeItem(GAMESTREAM_EOL_ID)`, nagłówek „nazwa · stan”, `ThumbcastLauncherUi.place(...)` dla istniejących ID (nowe etykiety i kolejność, grupy: akcje / diagnostyka / usuwanie), linie między grupami (API 28+). Obsługa w `onContextItemSelected` bez zmian. |
| | `doPair`: `Toast.makeText(... R.string.pairing ...)` | → `ThumbcastPairingDialog.showConnecting` (U18, 1 zmieniona linia) |
| | `doPair`: `Dialog.displayDialog(... pair_pairing_title ...)` (3 linie) | → `ThumbcastPairingDialog.showPin(PcView.this, computer.name, pinStr)` (U10) |
| | `doPair`: po `Dialog.closeDialogs();` | `ThumbcastPairingDialog.dismiss(PcView.this)` |
| | `doPair`, `runOnUiThread`: `Toast.makeText(PcView.this, toastMessage, ...)` | → `ThumbcastPairingDialog.showFailure` (dialog z „Pokaż poradnik”, U18, 1 zmieniona linia) |
| | `getAdapterFragmentLayoutId()`: `return R.layout.pc_grid_view;` | → `R.layout.thumbcast_pc_grid_view` (1 zmieniona linia) |
| `AppView.java` | `onCreate`: `setContentView(R.layout.activity_app_view);` | → `R.layout.thumbcast_activity_app_view` + `ThumbcastLauncherUi.bindBackButton` (U15) |
| | koniec `onCreateContextMenu` (po pozycji `CREATE_SHORTCUT_ID`) | `ThumbcastLauncherUi.place(...)`: „Wróć do gry”, „Zamknij grę na PC”, „Ustawienia gry” zaraz po akcjach gry, „Ukryj na liście”, diagnostyka w osobnej grupie |
| `grid/PcGridAdapter.java` | `getLayoutIdForPreferences`: `return R.layout.pc_grid_item;` | → `R.layout.thumbcast_pc_grid_item` (1 zmieniona linia) |
| | początek `populateView(...)` | `if (ThumbcastLauncherUi.populatePcCard(...)) return;` (U9; dla innego układu działa kod upstreamu) |
| `grid/AppGridAdapter.java` | `getLayoutIdForPreferences`: `app_grid_item_small` / `app_grid_item` | → `thumbcast_app_grid_item_small` / `thumbcast_app_grid_item` (2 zmienione linie, U15) |
| | koniec `populateView(...)` (po `parentView.setAlpha`) | `ThumbcastLauncherUi.bindMoreButton(...)` (⋮ na kafelku) |
| `utils/HelpLauncher.java` | `launchSetupGuide`, `launchTroubleshooting` | na początku: `ThumbcastLauncherUi.openGuide(context, SECTION_INSTALL / SECTION_TROUBLESHOOT)`; wiki upstreamu tylko, gdy poradnik się nie otworzy (U2) |
| `preferences/AddComputerManually.java` | `onCreate`: `setContentView(R.layout.activity_add_computer_manually);` | → `R.layout.thumbcast_activity_add_computer_manually` + `ThumbcastLauncherUi.bindAddComputerScreen` (U13) |

Wywołujący `HelpLauncher` (sprawdzone): `PcView` (ikona pomocy → poradnik od sekcji `install`), `utils/Dialog.java` (przycisk
„Pomoc” w dialogach błędów → sekcja `troubleshoot`; pasuje, bez zmian w `Dialog.java`), `WebLauncherPreference` (tylko
`launchUrl`, bez zmian). `launchGameStreamEolFaq` nie ma już wywołań w menu (pozycja EOL ukryta), metoda zostaje.

Bez zmian (świadomie): upstreamowe `activity_pc_view.xml` (+ `layout-land`), `pc_grid_item.xml`, `pc_grid_view.xml`,
`app_grid_item*.xml`, `activity_app_view.xml`, `activity_add_computer_manually.xml`, `utils/Dialog.java`,
`ThumbcastAddressBook.java`. Po zmianie układów lint zgłasza je jako nieużywane (ostrzeżenia `UnusedResources`).

## Zmiany w plikach Thumbcast (nie upstream)

- `thumbcast/HostAddressesDialog.java` (U16): lista z nagłówkami „W domu (LAN)” / „Poza domem (Tailscale, ZeroTier)”,
  plakietka „Używany”, osobny przycisk kosza z opisem; tap w wiersz niczego nie usuwa. Dodawanie i potwierdzenie usunięcia
  bez zmian.
- `thumbcast/ThumbcastAppSettingsDialog.java` (U17): tytuł „Ustawienia gry: …”, etykiety D7 („Rozdzielczość obrazu”,
  „Płynność (FPS)”), style `ThumbcastText` (notka 14 sp w kolorze drugorzędnym, bez przezroczystości; ostrzeżenie
  z ikoną w kolorze ostrzeżenia), „Przywróć globalne”. Zapis bez zmian.

## Nowe pliki

- `thumbcast/ThumbcastLauncherUi.java`: poradnik (`openGuide`), pusty stan (`EmptyState`, 5 s), karta PC (`populatePcCard`,
  `statusText`), przycisk ⋮ (`bindMoreButton` → `showContextMenu()` elementu siatki), menu (`place`, `enableGroupDividers`),
  paski (`bindBackButton`, `bindAddComputerScreen`).
- `thumbcast/ThumbcastPairingDialog.java`: postęp „Łączę się z komputerem…”, dialog PIN (kroki, PIN 44 sp monospace,
  „Pokaż poradnik” → sekcja `pair`, dialog zostaje), błąd parowania z poradnikiem.
- `thumbcast/ThumbcastOnboardingHook.java` (U1, D8): opis w następnej sekcji.
- Układy: `thumbcast_activity_pc_view.xml` (jeden dla pionu i poziomu), `thumbcast_pc_grid_view.xml`, `thumbcast_pc_grid_item.xml`,
  `thumbcast_app_grid_item.xml`, `thumbcast_app_grid_item_small.xml`, `thumbcast_activity_app_view.xml`,
  `thumbcast_activity_add_computer_manually.xml`, `thumbcast_pc_pair_dialog.xml`, `thumbcast_pc_progress_dialog.xml`,
  `thumbcast_pc_address_row.xml`, `thumbcast_pc_address_header.xml`.
- Drawable: `thumbcast_bg_pc_grid_selector.xml` (obrys fokusu karty), `thumbcast_bg_pc_more.xml` (kółko pod ⋮ na okładce).
  Nowych ikon nie było trzeba (tylko wspólne `thumbcast_ic_*` z P0).
- Teksty: `values/thumbcast_strings_launcher.xml` (EN) i `values-pl/thumbcast_strings_launcher.xml` (PL), prefiks
  `thumbcast_launcher_`.

## Kreator pierwszego uruchomienia (hak U1)

- `PcView.onCreate` → `ThumbcastOnboardingHook.onCreate`: tylko gdy `savedInstanceState == null` i
  `UiContract.shouldShowOnboarding(zapisana wersja, false)`. Liczba PC: odczyt tylko do odczytu z `computers4.db`
  (`SELECT COUNT(*) FROM Computers`) w osobnym wątku; nazwy bazy i tabeli skopiowane z prywatnych stałych
  `ComputerDatabaseManager` (przy rebase sprawdzić, czy upstream ich nie zmienił). Brak pliku lub tabeli = brak PC.
- Pusta lista → `startActivityForResult(UiContract.onboardingIntent, REQUEST_ONBOARDING)`; są PC → tylko zapis wersji.
- Wynik (`onActivityResult` w `PcView`) → `applyResult`: przy `RESULT_OK` preset do `list_thumbcast_profile` (ten sam zapis
  co „Ustaw jako domyślny” w `ProfileLibraryActivity`, tylko gdy plik istnieje w `ProfileRepository`) i
  `checkbox_thumbcast_hide_on_gamepad`; wersja `CURRENT_ONBOARDING_VERSION` zawsze, także po „Pomiń”.
- `ThumbcastOnboardingHook.applyResult(Context, int, Intent)` jest publiczne: ponowne uruchomienie z Ustawień (P3) może
  użyć tej samej obsługi wyniku.

## Sprawdzenie

`./gradlew :app:assembleNonRootDebug :app:assembleNonRootRelease :app:lintNonRootDebug :control-android:testDebugUnitTest`.
Na urządzeniu jeszcze nie sprawdzone: wygląd kart i pustego stanu w pionie i poziomie, ⋮ na kartach i kafelkach (menu
z poprawnym elementem), kreator przy pierwszym uruchomieniu, dialog PIN z hostem Sunshine, przejście do poradnika i powrót
do dialogu PIN, dialog adresów, nawigacja padem/pilotem po siatce kart (obrys fokusu).
