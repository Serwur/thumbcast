package app.thumbcast.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.thumbcast.editor.state.EditorState
import app.thumbcast.ui.ThumbcastTheme

/**
 * Jedyny zmienny element UI: referencja do niezmiennego [EditorState] w stanie Compose. Odczyt [state]
 * zawsze daje najnowszy stan (także w środku gestu, przed rekompozycją).
 */
class EditorStore(initial: EditorState) {
    var state: EditorState by mutableStateOf(initial)
        private set

    fun update(f: (EditorState) -> EditorState) {
        state = f(state)
    }
}

/** Motyw Thumbcast z :control-ui (ciemny, paleta D1); edytor działa nad ciemnym płótnem niezależnie od motywu systemu. */
@Composable
fun EditorTheme(content: @Composable () -> Unit) {
    ThumbcastTheme(content = content)
}
