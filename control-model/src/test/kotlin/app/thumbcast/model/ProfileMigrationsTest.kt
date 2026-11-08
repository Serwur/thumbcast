package app.thumbcast.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileMigrationsTest {

    private fun presetTree(): JsonObject =
        ProfileCodec.json.parseToJsonElement(TestFixtures.readPreset("mmo-pad-plus.thumbcast.json")) as JsonObject

    @Test
    fun v1ToV1IsIdentity() {
        val tree = presetTree()
        val migrated = ProfileMigrations.migrate(tree, 1, 1).getOrThrow()
        assertEquals(tree, migrated)
    }

    @Test
    fun currentSchemaIsOne() = assertEquals(1, ProfileCodec.CURRENT_SCHEMA)

    @Test
    fun newerSchemaIsRejected() {
        val tree = JsonObject(presetTree() + ("schema" to JsonPrimitive(2)))
        assertTrue(ProfileMigrations.migrate(tree, 2).isFailure)
    }

    @Test
    fun tooOldSchemaIsRejected() {
        assertTrue(ProfileMigrations.migrate(presetTree(), 0).isFailure)
    }

    @Test
    fun decodedAfterMigrationEqualsDirectDecode() {
        val text = TestFixtures.readPreset("mmo-pad-plus.thumbcast.json")
        val migrated = ProfileMigrations.migrate(presetTree(), 1).getOrThrow()
        val viaMigration = ProfileCodec.json.decodeFromJsonElement(Profile.serializer(), migrated)
        assertEquals(ProfileCodec.decode(text).getOrThrow(), viaMigration)
    }
}
