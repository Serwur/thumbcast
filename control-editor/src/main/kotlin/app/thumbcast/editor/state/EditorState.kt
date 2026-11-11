package app.thumbcast.editor.state

import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.HandednessTransform
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl

/**
 * Niezmienny stan edytora układu: profil, zaznaczenie, siatka i historia cofania. Każda operacja zwraca
 * nowy stan (UI trzyma tylko referencję). Bez Androida, testowane w JVM.
 *
 * Historia:
 * - zwykła edycja ([edit]) odkłada poprzedni profil na stos cofania i czyści stos ponawiania;
 * - edycje z tym samym [EditKey] pod rząd (np. pisanie etykiety) dają jeden krok cofania;
 * - gest (przeciąganie, szczypanie, suwak) to jeden krok: [beginGesture], dowolnie wiele [gesture],
 *   [endGesture]. Przekształcenie gestu liczy się zawsze od profilu z początku gestu ([gestureBase]),
 *   więc przyciąganie do siatki nie kumuluje błędów.
 */
data class EditorState(
    val profile: Profile,
    /** Ostatnio zapisany (albo wczytany) profil; różnica = niezapisane zmiany. */
    val saved: Profile = profile,
    val selectedId: String? = null,
    val snapToGrid: Boolean = true,
    val undoStack: List<Profile> = emptyList(),
    val redoStack: List<Profile> = emptyList(),
    /** Profil sprzed gestu (trafia na stos cofania przy [endGesture]); null = brak gestu. */
    val gestureStart: Profile? = null,
    /** Profil, od którego liczy się bieżące przekształcenie gestu. */
    val gestureBase: Profile? = null,
    val lastEditKey: EditKey? = null,
) {
    val dirty: Boolean
        get() = profile != saved

    val canUndo: Boolean
        get() = undoStack.isNotEmpty() || (gestureStart != null && gestureStart != profile)

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val selected: Control?
        get() = selectedId?.let { id -> profile.controls.firstOrNull { it.id == id } }

    /** Błędy walidatora modelu dla bieżącego profilu (pusta lista = można zapisać). */
    fun errors(): List<String> = ProfileValidator.validate(profile)

    // ---------- zaznaczenie i siatka ----------

    fun select(id: String?): EditorState {
        val valid: String? = if (id != null && profile.controls.any { it.id == id }) id else null
        return if (valid == selectedId) this else copy(selectedId = valid, lastEditKey = null)
    }

    fun toggleSnap(): EditorState = copy(snapToGrid = !snapToGrid)

    fun markSaved(): EditorState = copy(saved = profile)

    // ---------- historia ----------

    fun edit(key: EditKey? = null, transform: (Profile) -> Profile): EditorState {
        val base: EditorState = if (gestureStart != null) endGesture() else this
        val next: Profile = transform(base.profile)
        if (next == base.profile) return base
        val coalesce: Boolean = key != null && key == base.lastEditKey && base.undoStack.isNotEmpty()
        return base.copy(
            profile = next,
            undoStack = if (coalesce) base.undoStack else push(base.undoStack, base.profile),
            redoStack = emptyList(),
            lastEditKey = key,
            selectedId = keepSelection(next, base.selectedId),
        )
    }

    fun beginGesture(): EditorState =
        if (gestureStart != null) this else copy(gestureStart = profile, gestureBase = profile, lastEditKey = null)

    /** Bieżący profil jako nowa baza gestu (np. przejście z przeciągania na szczypanie), bez kroku cofania. */
    fun rebaseGesture(): EditorState = if (gestureStart == null) beginGesture() else copy(gestureBase = profile)

    fun gesture(transform: (Profile) -> Profile): EditorState {
        val started: EditorState = beginGesture()
        val base: Profile = started.gestureBase ?: started.profile
        val next: Profile = transform(base)
        return if (next == started.profile) started else started.copy(profile = next)
    }

    fun endGesture(): EditorState {
        val start: Profile = gestureStart ?: return this
        val ended: EditorState = copy(gestureStart = null, gestureBase = null)
        if (start == profile) return ended
        return ended.copy(undoStack = push(undoStack, start), redoStack = emptyList(), lastEditKey = null)
    }

    fun undo(): EditorState {
        val s: EditorState = endGesture()
        val prev: Profile = s.undoStack.lastOrNull() ?: return s
        return s.copy(
            profile = prev,
            undoStack = s.undoStack.dropLast(1),
            redoStack = s.redoStack + s.profile,
            lastEditKey = null,
            selectedId = keepSelection(prev, s.selectedId),
        )
    }

    fun redo(): EditorState {
        val s: EditorState = endGesture()
        val next: Profile = s.redoStack.lastOrNull() ?: return s
        return s.copy(
            profile = next,
            undoStack = push(s.undoStack, s.profile),
            redoStack = s.redoStack.dropLast(1),
            lastEditKey = null,
            selectedId = keepSelection(next, s.selectedId),
        )
    }

    // ---------- gesty na płótnie (wartości znormalizowane 0..1, liczone od początku gestu) ----------

    fun dragMove(id: String, dx: Float, dy: Float): EditorState =
        gesture { p -> p.updateControl(id) { c -> c.withRect(EditorGeometry.move(c.rect, dx, dy, snapToGrid)) } }

    fun dragResize(id: String, dx: Float, dy: Float): EditorState =
        gesture { p -> p.updateControl(id) { c -> c.withRect(EditorGeometry.resizeCorner(c.rect, dx, dy, snapToGrid)) } }

    fun pinchScale(id: String, factor: Float): EditorState =
        gesture { p -> p.updateControl(id) { c -> c.withRect(EditorGeometry.scaleUniform(c.rect, factor, snapToGrid)) } }

    // ---------- suwaki (gest: jeden krok cofania na całe przesunięcie suwaka) ----------

    fun sliderSize(id: String, height: Float): EditorState =
        gesture { p -> p.updateControl(id) { c -> c.withRect(EditorGeometry.withHeight(c.rect, height, snapToGrid)) } }

    fun sliderOpacity(opacity: Float): EditorState =
        gesture { p ->
            val v: Float = EditorGeometry.round4(opacity.coerceIn(ProfileValidator.OPACITY_MIN, 1f))
            p.copy(global = p.global.copy(opacity = v))
        }

    fun sliderDeadzone(id: String, deadzone: Float): EditorState =
        gesture { p ->
            p.updateControl(id) { c ->
                if (c !is StickControl) return@updateControl c
                val max: Float = minOf(ProfileValidator.DEADZONE_MAX, ProfileValidator.DEADZONE_SUM_MAX - c.outerDeadzone)
                c.copy(deadzone = EditorGeometry.round4(deadzone.coerceIn(0f, maxOf(0f, max))))
            }
        }

    // ---------- właściwości ----------

    fun setName(name: String): EditorState = edit(EditKey.Name) { it.copy(name = name) }

    /** Pusta etykieta = brak (`label` null), renderer pokaże wtedy nazwę wyjścia. */
    fun setLabel(id: String, label: String): EditorState =
        edit(EditKey.Label(id)) { p -> p.updateControl(id) { it.withLabel(label.ifEmpty { null }) } }

    /** Ustawia wyjście tylko z listy [OutputCatalog] dla typu kontrolki i trybu profilu. */
    fun setOutput(id: String, output: Output): EditorState = edit { p ->
        p.updateControl(id) { c -> if (OutputCatalog.isAllowed(c, p.mode, output)) c.withOutput(output) else c }
    }

    fun setShape(id: String, shape: Shape): EditorState = edit { p -> p.updateControl(id) { it.withShape(shape) } }

    /** hold/latch tylko dla modyfikatora (walidator zabrania latch na zwykłym przycisku). */
    fun setBehavior(id: String, behavior: Behavior): EditorState = edit { p ->
        p.updateControl(id) { c -> if (c is ModifierControl) c.copy(behavior = behavior) else c }
    }

    fun setFloating(id: String, floating: Boolean): EditorState = edit { p ->
        p.updateControl(id) { c -> if (c is StickControl) c.copy(floating = floating) else c }
    }

    fun setSprintLock(id: String, sprintLock: Boolean): EditorState = edit { p ->
        p.updateControl(id) { c -> if (c is StickControl) c.copy(sprintLock = sprintLock) else c }
    }

    /** Usuwa akord z przycisku wczytanego z pliku (akordy nie są obsługiwane, FAIR_PLAY.md). */
    fun removeChord(id: String): EditorState = edit { p ->
        p.updateControl(id) { c -> if (c is ButtonControl) c.copy(chord = null) else c }
    }

    // ---------- kontrolki ----------

    fun addControl(kind: NewControlKind, aspect: Float): EditorState {
        val created: Control = ControlFactory.create(kind, profile, aspect)
        return edit { it.copy(controls = it.controls + created) }.copy(selectedId = created.id)
    }

    fun duplicateSelected(): EditorState {
        val src: Control = selected ?: return this
        val dup: Control = ControlFactory.duplicate(src, profile)
        return edit { it.copy(controls = it.controls + dup) }.copy(selectedId = dup.id)
    }

    fun deleteSelected(): EditorState {
        val id: String = selectedId ?: return this
        return edit { p -> p.copy(controls = p.controls.filterNot { it.id == id }) }.copy(selectedId = null)
    }

    /** Lustro lewo/prawo całego układu (HandednessTransform z :control-model). */
    fun mirror(): EditorState = edit { HandednessTransform.mirror(it) }

    companion object {
        /** Najwięcej kroków cofania. */
        const val MAX_UNDO: Int = 100

        private fun push(stack: List<Profile>, p: Profile): List<Profile> {
            val next: List<Profile> = stack + p
            return if (next.size > MAX_UNDO) next.drop(next.size - MAX_UNDO) else next
        }

        private fun keepSelection(p: Profile, id: String?): String? =
            if (id != null && p.controls.any { it.id == id }) id else null
    }
}

/** Klucz łączenia kolejnych edycji w jeden krok cofania. */
sealed interface EditKey {
    data object Name : EditKey
    data class Label(val id: String) : EditKey
}

/** Profil z kontrolką [id] przekształconą przez [f] (pozostałe bez zmian). */
fun Profile.updateControl(id: String, f: (Control) -> Control): Profile {
    var changed = false
    val list: List<Control> = controls.map { c ->
        if (c.id != id) {
            c
        } else {
            val n: Control = f(c)
            if (n != c) changed = true
            n
        }
    }
    return if (changed) copy(controls = list) else this
}
