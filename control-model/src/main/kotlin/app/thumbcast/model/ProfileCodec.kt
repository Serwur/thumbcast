package app.thumbcast.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Kodek plików `.thumbcast.json` (UTF-8).
 * decode: parsowanie ścisłe (nieznane pola i typy = błąd) -> migracja schematu -> walidacja (ProfileValidator).
 * encode: czytelny JSON (pretty print, wcięcie 2 spacje), pola null pomijane.
 */
object ProfileCodec {
    const val CURRENT_SCHEMA = 1

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = false
        classDiscriminator = "type"
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    /** Pełny odczyt: odrzuca nieznane pola i typy oraz profile łamiące reguły walidatora. */
    fun decode(json: String): Result<Profile> {
        val parsed = decodeUnvalidated(json)
        val profile = parsed.getOrElse { return Result.failure(it) }
        val errors = ProfileValidator.validate(profile)
        if (errors.isNotEmpty()) {
            return Result.failure(ProfileValidationException(errors))
        }
        return Result.success(profile)
    }

    /**
     * Odczyt bez walidacji reguł (nadal ścisły względem pól i typów). Przydatny w edytorze,
     * żeby pokazać użytkownikowi listę błędów zamiast odrzucać plik w całości.
     */
    fun decodeUnvalidated(text: String): Result<Profile> = runCatching {
        val element = this.json.parseToJsonElement(text)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("profil musi być obiektem JSON")
        // Najpierw czytelne komunikaty o zakazanych typach i wielokrotnych wyjściach;
        // typowany parser i tak by je odrzucił, ale z mniej zrozumiałym błędem.
        val rawErrors = ProfileValidator.validateRaw(obj)
        if (rawErrors.isNotEmpty()) throw ProfileValidationException(rawErrors)
        val schema = (obj["schema"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("brak lub niepoprawne pole schema")
        val migrated = ProfileMigrations.migrate(obj, schema).getOrThrow()
        this.json.decodeFromJsonElement(Profile.serializer(), migrated)
    }

    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)
}

class ProfileValidationException(val errors: List<String>) :
    IllegalArgumentException("profil niepoprawny: " + errors.joinToString("; "))
