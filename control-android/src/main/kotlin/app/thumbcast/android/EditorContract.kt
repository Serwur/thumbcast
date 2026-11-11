package app.thumbcast.android

/**
 * Kontrakt między biblioteką profili (:app) a edytorem układu (osobny moduł, np. :control-editor).
 * Opis: docs/profiles.md, sekcja „Kontrakt edytora”.
 *
 * - Uruchomienie: `Intent().setClassName(context, ACTIVITY_CLASS)` + `startActivityForResult`
 *   (albo ActivityResult API).
 * - Wejście: [EXTRA_PROFILE_JSON] (String) = JSON profilu (`ProfileCodec.encode`).
 * - Wynik: `RESULT_OK` z [EXTRA_PROFILE_JSON] = zwalidowany JSON po edycji; każdy inny wynik = anulowano.
 * - Biblioteka zapisuje wynik przez `ProfileRepository` (ponowna walidacja). Edytor nie pisze plików profili.
 * - Edytor dostaje zawsze profil użytkownika: preset wbudowany biblioteka najpierw kopiuje.
 */
object EditorContract {
    const val ACTIVITY_CLASS: String = "app.thumbcast.editor.LayoutEditorActivity"
    const val EXTRA_PROFILE_JSON: String = "app.thumbcast.editor.extra.PROFILE_JSON"
}
