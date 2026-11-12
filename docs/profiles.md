# Profile sterowania: format wymiany i biblioteka

Stan: 11.11.2026. Kod: `control-android/.../library/ProfileRepository.kt` (logika, testy JVM w `ProfileRepositoryTest`), `app/.../thumbcast/ProfileLibraryActivity.java` (ekran), `ProfileShareProvider.java` (udostępnianie pliku).

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

Ustawienia → Sterowanie dotykowe → Profil sterowania otwiera bibliotekę (podsumowanie = nazwa aktywnego profilu).

- **Presety wbudowane**: wszystkie `assets/presets/*.thumbcast.json` (kopiowane z `presets/` przez `copyThumbcastPresets`), nazwa z pola `name`. Nowy preset w `presets/` pojawia się automatycznie, bez tablic w zasobach (`thumbcast_profile_names/values` nie są już używane). Presety są tylko do odczytu.
- **Moje profile**: `filesDir/profiles/<id>.thumbcast.json`. Nazwa pliku pochodzi z `id`, nie z nazwy wyświetlanej. Zapis atomowy (plik tymczasowy + rename) i zawsze po walidacji.
- **Wybór**: zapisuje nazwę pliku w preferencji `list_thumbcast_profile` (ten sam klucz co dawniej). `ThumbcastProfiles` wczytuje ją bez zmian: najpierw `filesDir/profiles/`, potem assety.
- **Zmiana w trakcie gry** (12.11.2026): menu w grze → „Profil: <nazwa>” pokazuje tę samą listę (`ProfileRepository.listAll()`, bez plików uszkodzonych, aktywny z ✓). Wybór zeruje stan na hoście, przełącza profil na żywo (`ThumbcastProfiles` + `ThumbcastOverlay.setProfile`, bez wychodzenia ze strumienia) i zapisuje go w `list_thumbcast_profile`, więc biblioteka i następna sesja widzą ten sam profil. Import, edycja i usuwanie są tylko w bibliotece. Wyjątek: gdy sesja wystartowała z profilem przypisanym do aplikacji (niżej), wybór w grze działa tylko do końca tej sesji i niczego nie zapisuje.
- **Profil dla aplikacji hosta** (12.11.2026): lista aplikacji PC → przytrzymaj aplikację → „Ustawienia Thumbcast dla aplikacji” → profil z biblioteki (albo „Profil domyślny” = `list_thumbcast_profile`), opcjonalnie rozdzielczość i FPS. Zapis w SharedPreferences `ThumbcastAppSettings` (klucz = uuid PC, wpisy z id i nazwą aplikacji), nie w pliku profilu, więc eksport profilu go nie zawiera. Przy starcie tej aplikacji sesja wczytuje przypisany plik przez `ThumbcastProfiles`; gdy go nie ma albo nie przechodzi walidacji (np. profil usunięty z biblioteki), używa profilu globalnego i pokazuje komunikat. Usunięcie albo zmiana nazwy profilu w bibliotece nie zmienia przypisań (nazwa pliku zostaje ta sama przy zmianie nazwy). Opis i konfiguracja hosta: [host-apps.md](host-apps.md).
- **Akcje**: Użyj, Edytuj układ, Duplikuj (nowe id UUID, nazwa z dopiskiem „(kopia)”, unikalna), Zmień nazwę, Eksportuj do pliku (SAF `ACTION_CREATE_DOCUMENT`), Udostępnij (`ACTION_SEND`, `application/json`, plik przez `ProfileShareProvider`), Usuń (z potwierdzeniem; usunięcie aktywnego wraca do domyślnego `gamepad-xbox.thumbcast.json`).
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
3. Aplikacja pokazuje nazwę, liczbę kontrolek i tryb; profil zapisuje się dopiero po **Importuj** (albo **Importuj i użyj**).

Profil to tylko układ i mapowanie wejść. Walidator gwarantuje zasadę 1 dotyk = 1 wejście także w cudzych profilach, ale nie ocenia, czy dany układ jest dozwolony w konkretnej grze (`FAIR_PLAY.md`). W polu `tags` warto podać grę i wariant (np. `"game": "mmo-generic"`).
