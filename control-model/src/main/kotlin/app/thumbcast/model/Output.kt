package app.thumbcast.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dokładnie jedno wyjście na kontrolkę (polityka 1 dotyk = 1 wejście).
 *
 * Postać JSON: obiekt z DOKŁADNIE jednym kluczem:
 * - `{"pad": "A"}` (przycisk), `{"pad": "LT"}` (trigger), `{"pad": "left_stick"}` (gałka); tak zapisuje encoder,
 * - aliasy przyjmowane przy odczycie: `{"trigger": "LT"}`, `{"stick": "left"}` / `{"stick": "left_stick"}`,
 * - `{"key": "M"}` (kod z [KeyCodes]),
 * - `{"mouse": "left" | "right" | "middle" | "wheel" | "look"}`,
 * - `{"app": "menu" | "soft_keyboard" | "toggle_overlay"}` (akcja lokalna, nic nie idzie do hosta).
 * Tablice (np. `{"pad": ["LB","RB"]}`) i każdy inny klucz są odrzucane.
 */
// Serializer: OutputSerializer (podpinany na polach przez @Serializable(with = ...), bo adnotacja na interfejsie
// nie jest wspierana we wszystkich wersjach pluginu).
sealed interface Output {
    data class Pad(val button: PadButton) : Output
    data class Trigger(val trigger: PadTrigger) : Output
    data class Stick(val stick: PadStick) : Output            // oś analogowa
    data class Key(val code: String) : Output                 // kod ze słownika KeyCodes
    data class Mouse(val button: MouseBtn) : Output           // przycisk w punkcie dotyku lub bieżącej pozycji
    object MouseWheel : Output
    object MouseLook : Output                                 // ruch względny (kamera w trybie KBM)
    data class App(val action: String) : Output               // patrz AppActions
}

/** Dozwolone akcje lokalne aplikacji (Output.App). */
object AppActions {
    const val MENU = "menu"
    const val SOFT_KEYBOARD = "soft_keyboard"
    const val TOGGLE_OVERLAY = "toggle_overlay"
    val ALL: Set<String> = setOf(MENU, SOFT_KEYBOARD, TOGGLE_OVERLAY)
}

/** Konwersja Output <-> JsonObject, używana przez serializery. */
object OutputJson {
    const val KEY_PAD = "pad"
    const val KEY_TRIGGER = "trigger"
    const val KEY_STICK = "stick"
    const val KEY_KEY = "key"
    const val KEY_MOUSE = "mouse"
    const val KEY_APP = "app"

    fun toJson(output: Output): JsonObject {
        val pair: Pair<String, String> = when (output) {
            is Output.Pad -> KEY_PAD to output.button.name
            is Output.Trigger -> KEY_PAD to output.trigger.name
            is Output.Stick -> KEY_PAD to stickName(output.stick)
            is Output.Key -> KEY_KEY to output.code
            is Output.Mouse -> KEY_MOUSE to output.button.name.lowercase()
            Output.MouseWheel -> KEY_MOUSE to "wheel"
            Output.MouseLook -> KEY_MOUSE to "look"
            is Output.App -> KEY_APP to output.action
        }
        return JsonObject(mapOf(pair.first to JsonPrimitive(pair.second)))
    }

    fun fromJson(obj: JsonObject): Output {
        if (obj.size != 1) {
            throw SerializationException(
                "output musi mieć dokładnie jedno pole (pad/trigger/stick/key/mouse/app), jest ${obj.size}: ${obj.keys}"
            )
        }
        val entry = obj.entries.first()
        val key = entry.key
        val element = entry.value
        if (element !is JsonPrimitive || !element.isString) {
            throw SerializationException("output.$key musi być pojedynczym napisem (bez tablic i obiektów)")
        }
        val value = element.content
        return when (key) {
            KEY_PAD -> parsePad(value)
            KEY_TRIGGER -> Output.Trigger(parseTrigger(value))
            KEY_STICK -> Output.Stick(parseStick(value))
            KEY_KEY -> Output.Key(value)
            KEY_MOUSE -> parseMouse(value)
            KEY_APP -> Output.App(value)
            else -> throw SerializationException("nieznany typ wyjścia: \"$key\"")
        }
    }

    private fun stickName(s: PadStick): String = when (s) {
        PadStick.LEFT -> "left_stick"
        PadStick.RIGHT -> "right_stick"
    }

    private fun parsePad(value: String): Output {
        if (value == "left_stick") return Output.Stick(PadStick.LEFT)
        if (value == "right_stick") return Output.Stick(PadStick.RIGHT)
        for (t in PadTrigger.values()) {
            if (t.name == value) return Output.Trigger(t)
        }
        for (b in PadButton.values()) {
            if (b.name == value) return Output.Pad(b)
        }
        throw SerializationException("nieznany przycisk pada: \"$value\"")
    }

    private fun parseTrigger(value: String): PadTrigger {
        for (t in PadTrigger.values()) {
            if (t.name == value) return t
        }
        throw SerializationException("nieznany trigger: \"$value\"")
    }

    private fun parseStick(value: String): PadStick = when (value) {
        "left", "left_stick" -> PadStick.LEFT
        "right", "right_stick" -> PadStick.RIGHT
        else -> throw SerializationException("nieznana gałka: \"$value\"")
    }

    private fun parseMouse(value: String): Output = when (value) {
        "left" -> Output.Mouse(MouseBtn.LEFT)
        "right" -> Output.Mouse(MouseBtn.RIGHT)
        "middle" -> Output.Mouse(MouseBtn.MIDDLE)
        "wheel" -> Output.MouseWheel
        "look" -> Output.MouseLook
        else -> throw SerializationException("nieznane wyjście myszy: \"$value\"")
    }
}

/** Serializer Output wyłącznie dla JSON (kodek Thumbcast nie używa innych formatów). */
object OutputSerializer : KSerializer<Output> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("app.thumbcast.model.Output")

    override fun deserialize(decoder: Decoder): Output {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("Output obsługuje tylko format JSON")
        val element = input.decodeJsonElement()
        val obj = element as? JsonObject
            ?: throw SerializationException("output musi być obiektem JSON")
        return OutputJson.fromJson(obj)
    }

    override fun serialize(encoder: Encoder, value: Output) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("Output obsługuje tylko format JSON")
        output.encodeJsonElement(OutputJson.toJson(value))
    }
}

/** Serializer dla pola SystemControl.output: to samo co Output, ale wymaga Output.App. */
object AppOutputSerializer : KSerializer<Output.App> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("app.thumbcast.model.Output.App")

    override fun deserialize(decoder: Decoder): Output.App {
        val out = OutputSerializer.deserialize(decoder)
        return out as? Output.App
            ?: throw SerializationException("kontrolka typu system musi mieć wyjście {\"app\": ...}")
    }

    override fun serialize(encoder: Encoder, value: Output.App) {
        OutputSerializer.serialize(encoder, value)
    }
}
