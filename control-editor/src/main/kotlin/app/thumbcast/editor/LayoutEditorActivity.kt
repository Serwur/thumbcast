package app.thumbcast.editor

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.thumbcast.editor.state.EditorIo
import app.thumbcast.editor.state.EditorState
import app.thumbcast.editor.ui.EditorScreen
import app.thumbcast.editor.ui.EditorStore
import app.thumbcast.editor.ui.EditorTheme
import app.thumbcast.model.Profile

/**
 * Edytor układu kontrolek (pełny ekran, poziomo). Kontrakt:
 * - wejście: [EXTRA_PROFILE_JSON] (String, JSON profilu; może mieć błędy walidatora, nie może mieć typów zakazanych),
 * - wynik: [RESULT_OK] z [EXTRA_PROFILE_JSON] = JSON po edycji, zwalidowany przez ProfileCodec/ProfileValidator,
 *   albo [RESULT_CANCELED] (zamknięcie bez zapisu, także po porzuceniu zmian).
 *
 * Logika jest w [EditorState] (czysty Kotlin), ta klasa tylko łączy ją z oknem.
 */
class LayoutEditorActivity : ComponentActivity() {

    private var store: EditorStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enterImmersive()

        val initial: EditorState = restore(savedInstanceState) ?: fromIntent() ?: return
        val s = EditorStore(initial)
        store = s
        setContent {
            EditorTheme {
                EditorScreen(
                    store = s,
                    onSave = ::save,
                    onCancel = ::cancel,
                )
            }
        }
    }

    private fun fromIntent(): EditorState? {
        val json: String? = intent.getStringExtra(EXTRA_PROFILE_JSON)
        if (json == null) {
            failAndFinish(getString(R.string.editor_missing_profile))
            return null
        }
        val profile: Profile = EditorIo.decodeForEditing(json).getOrElse { e ->
            failAndFinish(getString(R.string.editor_load_failed, e.message ?: e.toString()))
            return null
        }
        return EditorState(profile)
    }

    private fun restore(b: Bundle?): EditorState? {
        if (b == null) return null
        val current: Profile = b.getString(KEY_CURRENT)?.let { EditorIo.decodeForEditing(it).getOrNull() } ?: return null
        val saved: Profile = b.getString(KEY_SAVED)?.let { EditorIo.decodeForEditing(it).getOrNull() } ?: current
        return EditorState(profile = current, saved = saved, selectedId = b.getString(KEY_SELECTED))
            .let { if (b.getBoolean(KEY_SNAP, true)) it else it.toggleSnap() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val s: EditorState = store?.state ?: return
        // Historia cofania nie przetrwa śmierci procesu; zostaje bieżący i ostatnio zapisany profil.
        outState.putString(KEY_CURRENT, EditorIo.encodeDraft(s.profile))
        outState.putString(KEY_SAVED, EditorIo.encodeDraft(s.saved))
        outState.putString(KEY_SELECTED, s.selectedId)
        outState.putBoolean(KEY_SNAP, s.snapToGrid)
    }

    /** Zapis: tylko profil bez błędów; wynik wraca do wywołującego, edytor się zamyka. */
    private fun save(): List<String> {
        val s: EditorStore = store ?: return emptyList()
        val json: String = EditorIo.encodeForSave(s.state.profile).getOrElse { return EditorIo.saveErrors(s.state.profile) }
        s.update { it.markSaved() }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PROFILE_JSON, json))
        finish()
        return emptyList()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun failAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    /** Pełny ekran immersive: paski systemowe ukryte, wracają na chwilę po przesunięciu od krawędzi. */
    private fun enterImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        /** Wejście i wynik: JSON profilu (String). */
        const val EXTRA_PROFILE_JSON: String = app.thumbcast.android.EditorContract.EXTRA_PROFILE_JSON

        private const val KEY_CURRENT = "app.thumbcast.editor.state.CURRENT"
        private const val KEY_SAVED = "app.thumbcast.editor.state.SAVED"
        private const val KEY_SELECTED = "app.thumbcast.editor.state.SELECTED"
        private const val KEY_SNAP = "app.thumbcast.editor.state.SNAP"

        /** Intent do uruchomienia edytora przez `registerForActivityResult(StartActivityForResult())`. */
        @JvmStatic
        fun intent(context: Context, profileJson: String): Intent =
            Intent(context, LayoutEditorActivity::class.java).putExtra(EXTRA_PROFILE_JSON, profileJson)
    }
}
