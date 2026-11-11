package app.thumbcast.editor.state

import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileCodec
import app.thumbcast.model.ProfileValidationException
import app.thumbcast.model.ProfileValidator

/** Wejście i wyjście edytora (JSON profilu). Czysty Kotlin, bez Androida. */
object EditorIo {

    /**
     * Odczyt do edycji: ścisły parser (zakazane typy i pola, wiele wyjść = błąd), ale bez reguł walidatora,
     * żeby dało się otworzyć i poprawić profil z błędami (lista błędów jest w panelu).
     */
    fun decodeForEditing(json: String): Result<Profile> = ProfileCodec.decodeUnvalidated(json)

    /**
     * Błędy blokujące zapis: reguły walidatora, a gdy ich nie ma, pełny cykl encode -> decode
     * (ten sam kodek, którym aplikacja czyta profil). Pusta lista = można zapisać.
     */
    fun saveErrors(profile: Profile): List<String> {
        val errors: List<String> = ProfileValidator.validate(profile)
        if (errors.isNotEmpty()) return errors
        val decoded: Result<Profile> = ProfileCodec.decode(ProfileCodec.encode(profile))
        val failure: Throwable = decoded.exceptionOrNull() ?: return emptyList()
        return if (failure is ProfileValidationException) failure.errors else listOf(failure.message ?: failure.toString())
    }

    /** JSON do zapisu albo błędy, jeśli profil ich nie przechodzi. */
    fun encodeForSave(profile: Profile): Result<String> {
        val errors: List<String> = saveErrors(profile)
        if (errors.isNotEmpty()) return Result.failure(ProfileValidationException(errors))
        return Result.success(ProfileCodec.encode(profile))
    }

    /** JSON bez walidacji (stan roboczy w Bundle przy odtwarzaniu aktywności). */
    fun encodeDraft(profile: Profile): String = ProfileCodec.encode(profile)
}
