package app.thumbcast.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reguły schematu profilu (plan sekcje 5.3 i 6.2). Pusta lista = profil poprawny.
 * Komunikaty są po polsku i zaczynają się od ścieżki, np. "controls[3] (id=a): ...".
 *
 * Część reguł wynika z samej konstrukcji typów i parsera (dokładnie jedno wyjście, brak typów makro),
 * ale [validateRaw] sprawdza je także na surowym drzewie JSON, żeby komunikat był czytelny.
 */
object ProfileValidator {

    /** Nazwy, które nie mogą wystąpić ani jako `type`, ani jako klucz pola, w żadnym miejscu profilu. */
    val FORBIDDEN_TYPES: Set<String> = setOf("sequence", "macro", "repeat", "turbo", "delay", "toggle_auto")

    const val DEADZONE_MAX = 0.5f
    const val OUTER_DEADZONE_MAX = 0.5f
    const val DEADZONE_SUM_MAX = 0.9f
    const val KBM_THRESHOLD_MIN = 0.1f
    const val KBM_THRESHOLD_MAX = 0.9f
    const val POWER_EXPONENT_MIN = 1.0f
    const val POWER_EXPONENT_MAX = 3.0f
    const val OPACITY_MIN = 0.1f
    private const val EPS = 1e-4f

    /** Modyfikatory pada dozwolone w ModifierControl i jako `chord`. */
    val PAD_MODIFIER_BUTTONS: Set<PadButton> = setOf(PadButton.LB, PadButton.RB)

    fun validate(p: Profile): List<String> {
        val errors = ArrayList<String>()
        checkHeader(p, errors)
        checkGlobal(p.global, errors)
        checkGestures(p, errors)
        checkUniqueIds(p.controls, errors)
        p.controls.forEachIndexed { index, c ->
            val path = "controls[$index] (id=${c.id})"
            checkRect(path, c.rect, errors)
            if (c.id.isBlank()) errors.add("$path: id nie może być puste")
            when (c) {
                is StickControl -> checkStick(path, c, p.mode, errors)
                is ButtonControl -> checkButton(path, c, p, errors)
                is ModifierControl -> checkModifier(path, c, p.mode, errors)
                is SystemControl -> checkApp(path, c.output, errors)
            }
        }
        return errors
    }

    /**
     * Kontrola surowego JSON przed dekodowaniem typowanym: zakazane typy/klucze i liczba pól w `output`/`chord`.
     */
    fun validateRaw(root: JsonElement): List<String> {
        val errors = ArrayList<String>()
        walk(root, "$", errors)
        return errors
    }

    // ---------- reguły szczegółowe ----------

    private fun checkHeader(p: Profile, errors: MutableList<String>) {
        if (p.schema != ProfileCodec.CURRENT_SCHEMA) {
            errors.add("schema: oczekiwano ${ProfileCodec.CURRENT_SCHEMA}, jest ${p.schema}")
        }
        if (p.id.isBlank()) errors.add("id: nie może być puste")
        if (p.name.isBlank()) errors.add("name: nie może być puste")
        if (p.controls.isEmpty()) errors.add("controls: profil musi mieć co najmniej jedną kontrolkę")
    }

    private fun checkGlobal(g: GlobalSettings, errors: MutableList<String>) {
        if (!inRange(g.opacity, OPACITY_MIN, 1f)) errors.add("global.opacity: poza zakresem $OPACITY_MIN..1")
        if (!inRange(g.haptics.strength, 0f, 1f)) errors.add("global.haptics.strength: poza zakresem 0..1")
    }

    private fun checkGestures(p: Profile, errors: MutableList<String>) {
        val g = p.gestures
        val cam = g.cameraDrag
        if (p.mode == ProfileMode.KEYBOARD_MOUSE && cam.output != CameraOutput.MOUSE_LOOK) {
            errors.add("gestures.cameraDrag.output: w trybie keyboard_mouse dozwolone tylko mouse_look")
        }
        if (!(cam.sensitivity > 0f && cam.sensitivity <= 10f)) errors.add("gestures.cameraDrag.sensitivity: poza zakresem (0..10]")
        if (!inRange(cam.deadzonePx, 0f, 64f)) errors.add("gestures.cameraDrag.deadzonePx: poza zakresem 0..64")
        if (!(cam.maxDeflection > 0f && cam.maxDeflection <= 1f)) errors.add("gestures.cameraDrag.maxDeflection: poza zakresem (0..1]")
        if (cam.decayMs < 0 || cam.decayMs > 500) errors.add("gestures.cameraDrag.decayMs: poza zakresem 0..500")
        checkCurve("gestures.cameraDrag.curve", cam.curve, errors)

        if (g.tapTarget.maxMovePx < 0f) errors.add("gestures.tapTarget.maxMovePx: nie może być ujemne")
        if (g.tapTarget.maxMs <= 0) errors.add("gestures.tapTarget.maxMs: musi być dodatnie")
        if (g.longPress.minMs <= 0) errors.add("gestures.longPress.minMs: musi być dodatnie")
        // Long-press zastępuje tap (jedno wejście, nigdy oba), więc okna czasowe nie mogą się nakładać.
        if (g.tapTarget.enabled && g.longPress.enabled && g.longPress.minMs <= g.tapTarget.maxMs) {
            errors.add("gestures.longPress.minMs: musi być większe niż tapTarget.maxMs (tap i long-press się wykluczają)")
        }
        if (!(g.pinchZoom.pxPerNotch > 0f)) errors.add("gestures.pinchZoom.pxPerNotch: musi być dodatnie")
    }

    private fun checkUniqueIds(controls: List<Control>, errors: MutableList<String>) {
        val seen = HashSet<String>()
        val dups = LinkedHashSet<String>()
        for (c in controls) {
            if (!seen.add(c.id)) dups.add(c.id)
        }
        for (d in dups) errors.add("controls: zduplikowane id \"$d\"")
    }

    private fun checkRect(path: String, r: Rect, errors: MutableList<String>) {
        val values = listOf(r.x, r.y, r.w, r.h)
        if (values.any { it.isNaN() || it.isInfinite() }) {
            errors.add("$path.rect: wartości muszą być skończone")
            return
        }
        if (r.x < 0f || r.y < 0f || r.x > 1f || r.y > 1f) errors.add("$path.rect: x i y muszą być w zakresie 0..1")
        if (r.w <= 0f || r.h <= 0f || r.w > 1f || r.h > 1f) errors.add("$path.rect: w i h muszą być w zakresie (0..1]")
        if (r.x + r.w > 1f + EPS || r.y + r.h > 1f + EPS) errors.add("$path.rect: prostokąt wychodzi poza ekran (x+w lub y+h > 1)")
    }

    private fun checkCurve(path: String, c: Curve, errors: MutableList<String>) {
        when (c.type) {
            CurveType.LINEAR -> if (c.exponent != 1f) errors.add("$path: krzywa linear musi mieć exponent = 1")
            CurveType.POWER -> if (!inRange(c.exponent, POWER_EXPONENT_MIN, POWER_EXPONENT_MAX)) {
                errors.add("$path: exponent poza zakresem $POWER_EXPONENT_MIN..$POWER_EXPONENT_MAX")
            }
        }
    }

    private fun checkStick(path: String, c: StickControl, mode: ProfileMode, errors: MutableList<String>) {
        if (c.output !is Output.Stick) errors.add("$path.output: joystick musi mieć wyjście gałki (pad: left_stick/right_stick)")
        if (!inRange(c.deadzone, 0f, DEADZONE_MAX)) errors.add("$path.deadzone: poza zakresem 0..$DEADZONE_MAX")
        if (!inRange(c.outerDeadzone, 0f, OUTER_DEADZONE_MAX)) errors.add("$path.outerDeadzone: poza zakresem 0..$OUTER_DEADZONE_MAX")
        if (c.deadzone + c.outerDeadzone > DEADZONE_SUM_MAX) errors.add("$path: deadzone + outerDeadzone > $DEADZONE_SUM_MAX")
        checkCurve("$path.curve", c.curve, errors)
        if (!inRange(c.kbmThreshold, KBM_THRESHOLD_MIN, KBM_THRESHOLD_MAX)) {
            errors.add("$path.kbmThreshold: poza zakresem $KBM_THRESHOLD_MIN..$KBM_THRESHOLD_MAX")
        }
        val keys = c.kbmKeys
        if (mode == ProfileMode.KEYBOARD_MOUSE && keys == null) {
            errors.add("$path.kbmKeys: w trybie keyboard_mouse joystick musi mieć kbmKeys")
        }
        if (keys != null) {
            val list = listOf(keys.up, keys.down, keys.left, keys.right)
            for (k in list) {
                if (!KeyCodes.isValid(k)) errors.add("$path.kbmKeys: nieznany kod klawisza \"$k\"")
            }
            if (list.toSet().size != 4) errors.add("$path.kbmKeys: cztery kierunki muszą mieć różne klawisze")
        }
    }

    private fun checkButton(path: String, c: ButtonControl, p: Profile, errors: MutableList<String>) {
        if (c.behavior == Behavior.LATCH) errors.add("$path.behavior: latch dozwolone tylko dla kontrolek typu modifier")
        checkDigitalOutput("$path.output", c.output, p.mode, errors)
        val chord = c.chord ?: return
        if (!p.global.allowChords) {
            errors.add("$path.chord: akordy są wyłączone (global.allowChords = false)")
            return
        }
        checkChord(path, c.output, chord, p.mode, errors)
    }

    private fun checkModifier(path: String, c: ModifierControl, mode: ProfileMode, errors: MutableList<String>) {
        if (!isModifierOutput(c.output)) {
            errors.add("$path.output: modyfikator musi mieć wyjście LT/RT/LB/RB albo klawisz SHIFT/CTRL/ALT")
            return
        }
        checkDigitalOutput("$path.output", c.output, mode, errors)
    }

    /** Wyjście przycisku: jeden przycisk pada, trigger, klawisz albo przycisk myszy; zgodne z trybem. */
    private fun checkDigitalOutput(path: String, o: Output, mode: ProfileMode, errors: MutableList<String>) {
        when (o) {
            is Output.Pad, is Output.Trigger -> if (mode == ProfileMode.KEYBOARD_MOUSE) {
                errors.add("$path: wyjście pada niedozwolone w trybie keyboard_mouse")
            }
            is Output.Key -> if (!KeyCodes.isValid(o.code)) errors.add("$path: nieznany kod klawisza \"${o.code}\"")
            is Output.Mouse -> Unit
            is Output.Stick -> errors.add("$path: oś gałki dozwolona tylko w kontrolce typu stick")
            Output.MouseWheel -> errors.add("$path: kółko myszy dostępne tylko jako gest pinchZoom")
            Output.MouseLook -> errors.add("$path: ruch myszy dostępny tylko jako gest cameraDrag")
            is Output.App -> errors.add("$path: akcje aplikacji tylko w kontrolce typu system")
        }
    }

    private fun checkApp(path: String, o: Output.App, errors: MutableList<String>) {
        if (o.action !in AppActions.ALL) {
            errors.add("$path.output.app: niedozwolona akcja \"${o.action}\" (dozwolone: ${AppActions.ALL.sorted()})")
        }
    }

    /**
     * Akord: dokładnie 1 modyfikator (chord) + 1 przycisk (output), to samo urządzenie, różne wejścia.
     * Pad: chord = LB/RB/LT/RT, output = przycisk pada. Klawiatura: chord = SHIFT/CTRL/ALT, output = klawisz niebędący modyfikatorem.
     */
    private fun checkChord(path: String, output: Output, chord: Output, mode: ProfileMode, errors: MutableList<String>) {
        if (!isModifierOutput(chord)) {
            errors.add("$path.chord: akord musi być modyfikatorem (LB/RB/LT/RT albo SHIFT/CTRL/ALT)")
            return
        }
        if (chord == output) {
            errors.add("$path.chord: modyfikator akordu musi różnić się od wyjścia")
            return
        }
        val padChord = chord is Output.Pad || chord is Output.Trigger
        if (padChord) {
            if (mode == ProfileMode.KEYBOARD_MOUSE) errors.add("$path.chord: akord pada niedozwolony w trybie keyboard_mouse")
            if (output !is Output.Pad) errors.add("$path.chord: akord pada wymaga wyjścia będącego przyciskiem pada")
        } else {
            val ok = output is Output.Key && !KeyCodes.isModifier(output.code)
            if (!ok) errors.add("$path.chord: akord klawiatury wymaga wyjścia będącego zwykłym klawiszem")
        }
    }

    private fun isModifierOutput(o: Output): Boolean = when (o) {
        is Output.Trigger -> true
        is Output.Pad -> o.button in PAD_MODIFIER_BUTTONS
        is Output.Key -> KeyCodes.isModifier(o.code)
        else -> false
    }

    private fun inRange(v: Float, min: Float, max: Float): Boolean = !v.isNaN() && v >= min && v <= max

    // ---------- surowy JSON ----------

    private fun walk(e: JsonElement, path: String, errors: MutableList<String>) {
        when (e) {
            is JsonObject -> {
                for ((k, v) in e) {
                    val childPath = "$path.$k"
                    if (k.lowercase() in FORBIDDEN_TYPES) errors.add("$childPath: zakazane pole \"$k\" (brak makr i automatyzacji)")
                    if (k == "type" && v is JsonPrimitive && v.isString && v.content.lowercase() in FORBIDDEN_TYPES) {
                        errors.add("$childPath: zakazany typ \"${v.content}\" (brak makr i automatyzacji)")
                    }
                    if ((k == "output" || k == "chord") && v is JsonObject && v.size != 1) {
                        errors.add("$childPath: musi mieć dokładnie jedno pole wyjścia, jest ${v.size}")
                    }
                    walk(v, childPath, errors)
                }
            }
            is JsonArray -> e.forEachIndexed { i, child -> walk(child, "$path[$i]", errors) }
            else -> Unit
        }
    }
}
