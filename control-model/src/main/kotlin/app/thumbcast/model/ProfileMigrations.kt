package app.thumbcast.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Migracje schematu profilu na poziomie drzewa JSON (schema n -> n+1).
 * Na razie istnieje tylko schemat 1, więc lista kroków jest pusta. Dodając schemat 2:
 * 1) podnieś ProfileCodec.CURRENT_SCHEMA, 2) dopisz krok `1 to { obj -> ... }`, który ustawia "schema": 2,
 * 3) dopisz test migracji z zamrożonym plikiem v1.
 */
object ProfileMigrations {

    /** Krok migracji: przyjmuje obiekt w schemacie `from`, zwraca obiekt w schemacie `from + 1`. */
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    /** Najstarszy schemat, który umiemy wczytać. */
    const val OLDEST_SUPPORTED = 1

    /**
     * Doprowadza obiekt profilu ze schematu `from` do `to` (domyślnie bieżącego).
     * Dla `from == to` zwraca obiekt bez zmian.
     */
    fun migrate(obj: JsonObject, from: Int, to: Int = ProfileCodec.CURRENT_SCHEMA): Result<JsonObject> {
        if (from > to) {
            return Result.failure(IllegalArgumentException("schemat $from jest nowszy niż obsługiwany ($to)"))
        }
        if (from < OLDEST_SUPPORTED) {
            return Result.failure(IllegalArgumentException("schemat $from nie jest obsługiwany (minimum $OLDEST_SUPPORTED)"))
        }
        var current = obj
        var version = from
        while (version < to) {
            val step = steps[version]
                ?: return Result.failure(IllegalStateException("brak migracji ze schematu $version do ${version + 1}"))
            current = step(current)
            version += 1
            val written = (current["schema"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (written != version) {
                return Result.failure(IllegalStateException("migracja do $version nie ustawiła pola schema"))
            }
        }
        return Result.success(current)
    }
}
