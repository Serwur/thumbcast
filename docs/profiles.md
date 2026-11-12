# Profile sterowania: format wymiany i biblioteka

Stan: 12.11.2026 (profil domyślny i gesty myszy). Kod: `control-android/.../library/ProfileRepository.kt` (logika, testy JVM w `ProfileRepositoryTest`), `app/.../thumbcast/ProfileLibraryActivity.java` (ekran), `ProfileShareProvider.java` (udostępnianie pliku).

## Format pliku

- Rozszerzenie **`.thumbcast.json`**, kodowanie **UTF-8** (BOM na początku jest tolerowany przy imporcie), typ MIME `application/json`.
- Treść: jeden obiekt JSON profilu wg schematu z `control-model` (`Profile.kt`, przykład w PLAN.md, sekcja 5.3). Pole `schema` jest obowiązkowe; starsze wersje schematu przechodzą migracje (`ProfileMigrations`).
- Eksport i udostępnianie zapisują profil przez `ProfileCodec.encode` (wcięcie 2 spacje, pola domyślne jawnie). Nazwa proponowanego pliku: `<nazwa profilu>.thumbcast.json` bez znaków niedozwolonych w nazwach plików (np. `MMO-Pad+.thumbcast.json`).
- Limit importu: **256 KB** (presety mają kilka KB).

## Walidacja przy imporcie

Każdy import (plik, schowek, udostępniony plik lub tekst, wynik edytora) przechodzi `ProfileCodec.decode`: ścisłe parsowanie, migracje, walidator. Dopiero potem ekran potwierdzenia (nazwa, liczba kontrolek, tryb) i zapis. Walidator odrzuca m.in.:

- typy i pola `sequence`, `macro`, `repeat`, `turbo`, `delay`, `toggle_auto` w dowolnym miejscu pliku (także jako klucz);
- nieznane pola i nieznane wartości wyliczeń (literówka w nazwie pola też jest błędem);
- `output` z więcej niż jednym wyjściem, każde pole `chord` (akordy nie są obsługiwane, `FAIR_PLAY.md`);
- dwa źródła jednej gałki pada (np. gałka `right_stick` przy kamerze `pad_right_stick`);
- wartości poza zakresem (prostokąty poza 0..1, martwe strefy, krzywe, przezroczystość, okna czasowe gestów), puste `id`/`name`, profil bez kontrolek, nieznana przyszła wersja `schema`.

Błąd pokazuje listę problemów z walidatora (komunikaty po polsku, ze ścieżką, np. `controls[3] (id=a): ...`). Import nie zmienia istniejących profili.

## Biblioteka w aplikacji

Ustawienia → Sterowanie dotykowe → Profile sterowania otwiera bibliotekę (podsumowanie „Domyślny: <nazwa>”).

- **Presety wbudowane**: wszystkie `assets/presets/*.thumbcast.json` (kopiowane z `presets/` przez `copyThumbcastPresets`), nazwa z pola `name`. Nowy preset w `presets/` pojawia się automatycznie, bez tablic w zasobach (`thumbcast_profile_names/values` nie są już używane). Presety są tylko do odczytu.
- **Moje profile**: `filesDir/profiles/<id>.thumbcast.json`. Nazwa pliku pochodzi z `id`, nie z nazwy wyświetlanej. Zapis atomowy (plik tymczasowy + rename) i zawsze po walidacji.
- **Profil domyślny** (12.11.2026): profil, z którym startuje każdy strumień, chyba że aplikacja hosta ma własny profil (niżej). Na liście ma znacznik „★ Domyślny” (i zaznaczone kółko), a pod tytułem ekranu jest jedno zdanie wyjaśnienia. Opcje profilu → **Ustaw jako domyślny** zapisuje nazwę pliku w preferencji `list_thumbcast_profile` (ten sam klucz co dawniej). Domyślnym może być każdy preset i każdy profil użytkownika. `ThumbcastProfiles` wczytuje plik bez zmian: najpierw `filesDir/profiles/`, potem assety.
- **Profil domyślny z aplikacji**: „Pad – PlayStation” (`ThumbcastProfiles.DEFAULT_PRESET` = `gamepad-playstation.thumbcast.json`, ten sam w `PreferenceConfiguration`, `ProfileRepository`; do 11.11.2026 „Pad – Xbox”). Preferencja nie jest zapisywana przy pierwszym uruchomieniu (`Preference` bez `defaultValue`, `persistent="false"`), więc kto nigdy nie wybrał profilu, dostaje aktualny profil z aplikacji, a kto wybrał, zostaje przy swoim. Wyjątek: wersje z 11.11.2026 sprzed biblioteki mogły zapisać `mmo-pad-plus`/`gamepad-xbox` same; tego nie da się odróżnić od wyboru, więc nie migrujemy (wystarczy „Ustaw jako domyślny”).
- **Brak profilu domyślnego**: usunięcie go w bibliotece kasuje wybór (preferencja usunięta) i pokazuje komunikat z nazwą profilu z aplikacji. Plik zniknął poza biblioteką albo nie przechodzi walidacji: biblioteka pokazuje ★ przy profilu z aplikacji, a sesja (`AppStreamOverrides.selectProfile` z `shippedDefault`) startuje z profilem z aplikacji i komunikatem; preferencja zostaje bez zmian (plik mógł być niepoprawny tylko chwilowo).
- **Zmiana w trakcie gry** (12.11.2026): menu w grze → „Profil: <nazwa>” pokazuje tę samą listę pod tytułem „Profil domyślny” (`ProfileRepository.listAll()`, bez plików uszkodzonych, aktywny z ✓, domyślny z ★). Wybór zeruje stan na hoście, przełącza profil na żywo (`ThumbcastProfiles` + `ThumbcastOverlay.setProfile`, bez wychodzenia ze strumienia) i ustawia go jako domyślny (`list_thumbcast_profile`, komunikat „Profil domyślny: <nazwa>”), więc biblioteka i następna sesja widzą ten sam profil. Import, edycja i usuwanie są tylko w bibliotece. Wyjątek: gdy sesja wystartowała z profilem przypisanym do aplikacji (niżej), strona ma tytuł „Profil w tej sesji”, a wybór działa tylko do końca tej sesji i niczego nie zapisuje.
- **Profil dla aplikacji hosta** (12.11.2026): lista aplikacji PC → przytrzymaj aplikację → „Ustawienia Thumbcast dla aplikacji” → profil z biblioteki (albo „Profil domyślny” = `list_thumbcast_profile`), opcjonalnie rozdzielczość i FPS. Zapis w SharedPreferences `ThumbcastAppSettings` (klucz = uuid PC, wpisy z id i nazwą aplikacji), nie w pliku profilu, więc eksport profilu go nie zawiera. Przy starcie tej aplikacji sesja wczytuje przypisany plik przez `ThumbcastProfiles`; gdy go nie ma albo nie przechodzi walidacji (np. profil usunięty z biblioteki), używa profilu globalnego i pokazuje komunikat. Usunięcie albo zmiana nazwy profilu w bibliotece nie zmienia przypisań (nazwa pliku zostaje ta sama przy zmianie nazwy). Opis i konfiguracja hosta: [host-apps.md](host-apps.md).
- **Gesty myszy** (12.11.2026): przełącznik na każdy profil, także preset tylko do odczytu. Opcje profilu w bibliotece → „Gesty myszy: wł./wył.” albo menu w grze → „Gesty myszy: wł./wył.” (dla aktywnego profilu, działa od razu, bez wychodzenia ze strumienia). Po przełączeniu komunikat mówi, co robią gesty w tym profilu; włączone widać na liście („· gesty myszy”). Zapis na telefonie, nie w pliku: domyślne SharedPreferences, klucz `thumbcast_mouse_gestures:<nazwa pliku profilu>`, wpis tylko wtedy, gdy wybór różni się od zachowania zapisanego w profilu (`MouseGesturesTransform.toStore`). Eksport i udostępnianie go nie zawierają. Duplikat (także kopia przy edycji presetu) przejmuje wybór, usunięcie profilu go kasuje. Działanie (`MouseGesturesTransform`, `:control-model`):

  | Wolna strefa profilu | Domyślnie | Wł. | Wył. |
  |---|---|---|---|
  | `none` (Pad – Xbox, Pad – PlayStation) | wył. | mysz bezpośrednia (`direct`): tap = kursor w punkcie dotyku i LPM, tap dwoma palcami = PPM tam, gdzie stoi kursor (bez jego ruchu), przeciągnięcie jednym palcem = przewijanie w punkcie dotyku (góra/dół i na boki, treść idzie za palcem) | tylko menu z górnej krawędzi |
  | `direct` (Big Picture) | wł. | bez zmian | jak `none`: tylko menu z górnej krawędzi |
  | `touchpad` (Pulpit – Touchpad) | wł. | bez zmian (gładzik: przeciągnięcie = kursor, tap = LPM, dwa palce = PPM i przewijanie, przytrzymanie i ruch = przeciąganie) | jak `none`: tylko menu z górnej krawędzi |
  | `camera` (MMO – Pad+, MMO – Klawiatura) | wł., gdy profil ma tap, long-press albo pinch | tap = LPM w punkcie, tap dwoma palcami albo przytrzymanie = PPM w punkcie, szczypanie = kółko | tap (także dwoma palcami), long-press i pinch wyłączone |

  Do pierwszej wersji (12.11.2026) pady po włączeniu dostawały gładzik (`touchpad`); właściciel wybrał mysz bezpośrednią, a „Big Picture” przeszedł z `touchpad` na `direct` (w Steam Big Picture tap w kafelek i przewijanie jednym palcem są prostsze niż kursor względny).

  Kamera przeciąganiem w profilach MMO zostaje w obu stanach, a gest menu z górnej krawędzi zawsze. Kontrolki, ich wyjścia i tryb profilu się nie zmieniają.
- **Akcje**: Ustaw jako domyślny, Gesty myszy, Edytuj układ, Duplikuj (nowe id UUID, nazwa z dopiskiem „(kopia)”, unikalna), Zmień nazwę, Eksportuj do pliku (SAF `ACTION_CREATE_DOCUMENT`), Udostępnij (`ACTION_SEND`, `application/json`, plik przez `ProfileShareProvider`), Usuń (z potwierdzeniem; usunięcie profilu domyślnego wraca do profilu z aplikacji, „Pad – PlayStation”).
- **Import**: z pliku (SAF `ACTION_OPEN_DOCUMENT`), ze schowka, z intencji `ACTION_SEND` (`application/json`, plik albo tekst) i `ACTION_VIEW` (`content://`, `application/json` albo ścieżka `*.thumbcast.json`). Przyjmujemy tylko URI `content://`. Gdy `id` z pliku jest zajęte (np. ponowny import własnego eksportu, import presetu) albo nie nadaje się na nazwę pliku, profil dostaje nowe id i trafia na listę jako nowa pozycja.
- Importu z adresu URL (sieć) nie ma.

`ProfileShareProvider` to minimalny odpowiednik `androidx.core.content.FileProvider` (moduł `:app` nie ma zależności AndroidX): tylko odczyt, tylko `cacheDir/shared_profiles/`, bez eksportu, dostęp przez `FLAG_GRANT_READ_URI_PERMISSION`. Authority: `<applicationId>.profiles`.

## Kontrakt edytora

Stałe w `app.thumbcast.android.EditorContract` (`:control-android`):

| Element | Wartość |
|---|---|
| Aktywność | `app.thumbcast.editor.LayoutEditorActivity`, uruchamiana przez `Intent().setClassName(context, ACTIVITY_CLASS)` |
| Wejście | extra `app.thumbcast.editor.extra.PROFILE_JSON` (String): JSON profilu z `ProfileCodec.encode` |
| Wynik | `RESULT_OK` + to samo extra: zwalidowany JSON po edycji; inny wynik = anulowano |

- Biblioteka uruchamia edytor przez `startActivityForResult` i zapisuje wynik przez `ProfileRepository.updateFromText` (ponowna walidacja; plik i `id` zostają stare). Edytor nie zapisuje plików profili sam.
- Edycja presetu wbudowanego najpierw tworzy kopię użytkownika; edytor dostaje kopię. Jeśli preset był aktywny, po zapisie aktywna staje się kopia.
- Gdy aktywności nie ma w APK (`resolveActivity == null` albo `ActivityNotFoundException`), przycisk pokazuje komunikat i nie tworzy kopii.

## Jak dzielić się profilem

1. Biblioteka → profil → **Udostępnij** (komunikator, poczta, dysk) albo **Eksportuj do pliku**.
2. Odbiorca otwiera plik `.thumbcast.json` w Thumbcast albo wybiera Thumbcast w menu udostępniania; może też skopiować tekst JSON (np. z forum) i użyć **Wklej ze schowka**.
3. Aplikacja pokazuje nazwę, liczbę kontrolek i tryb; profil zapisuje się dopiero po **Importuj** (albo **Importuj jako domyślny**).

Profil to tylko układ i mapowanie wejść. Walidator gwarantuje zasadę 1 dotyk = 1 wejście także w cudzych profilach, ale nie ocenia, czy dany układ jest dozwolony w konkretnej grze (`FAIR_PLAY.md`). W polu `tags` warto podać grę i wariant (np. `"game": "mmo-generic"`).
