package app.thumbcast.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thumbcast.editor.R
import app.thumbcast.editor.state.ControlFactory
import app.thumbcast.editor.state.EditorState

/**
 * Ekran edytora: płótno na całym ekranie i wysuwany panel po jednej stronie (można go ukryć albo przenieść
 * na drugą stronę, żeby odsłonić kontrolki pod spodem).
 *
 * @param onSave zapis i zamknięcie; zwraca listę błędów, gdy profil nie przeszedł walidacji (wtedy nic nie zapisano).
 * @param onCancel zamknięcie bez zapisu.
 */
@Composable
fun EditorScreen(store: EditorStore, onSave: () -> List<String>, onCancel: () -> Unit) {
    val state: EditorState = store.state
    var panelOpen by rememberSaveable { mutableStateOf(true) }
    var panelOnRight by rememberSaveable { mutableStateOf(true) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var shownErrors by remember { mutableStateOf<List<String>?>(null) }
    var aspect by remember { mutableFloatStateOf(ControlFactory.DEFAULT_ASPECT) }
    val errors: List<String> = remember(state.profile) { state.errors() }

    val requestClose: () -> Unit = {
        if (store.state.dirty) confirmDiscard = true else onCancel()
    }
    BackHandler(onBack = requestClose)

    Box(Modifier.fillMaxSize()) {
        EditorCanvas(
            store,
            Modifier.fillMaxSize().onSizeChanged {
                if (it.width > 0 && it.height > 0) aspect = it.width.toFloat() / it.height.toFloat()
            },
        )
        val side: Alignment = if (panelOnRight) Alignment.CenterEnd else Alignment.CenterStart
        if (panelOpen) {
            PropertiesPanel(
                store = store,
                errors = errors,
                aspect = aspect,
                onSave = {
                    val failed: List<String> = onSave()
                    if (failed.isNotEmpty()) shownErrors = failed
                },
                onClose = requestClose,
                onHide = { panelOpen = false },
                onSwapSide = { panelOnRight = !panelOnRight },
                onShowErrors = { shownErrors = errors },
                modifier = Modifier
                    .align(side)
                    .fillMaxHeight()
                    .width(PANEL_WIDTH)
                    .windowInsetsPadding(WindowInsets.displayCutout),
            )
        } else {
            FilledTonalButton(
                onClick = { panelOpen = true },
                modifier = Modifier
                    .align(if (panelOnRight) Alignment.TopEnd else Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(8.dp),
            ) {
                Text(stringResource(R.string.editor_panel_show))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onCancel()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }

    val list: List<String>? = shownErrors
    if (list != null) {
        AlertDialog(
            onDismissRequest = { shownErrors = null },
            title = { Text(stringResource(R.string.editor_errors_title)) },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text(stringResource(R.string.editor_no_problems))
                    for (e in list) {
                        Text("• $e", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shownErrors = null }) { Text(stringResource(R.string.editor_ok)) }
            },
        )
    }
}

private val PANEL_WIDTH = 300.dp
