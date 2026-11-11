package app.thumbcast.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thumbcast.android.overlay.ControlLayout
import app.thumbcast.editor.R
import app.thumbcast.editor.state.EditorState
import app.thumbcast.editor.state.NewControlKind
import app.thumbcast.editor.state.OutputCatalog
import app.thumbcast.editor.state.OutputGroup
import app.thumbcast.editor.state.OutputOptions
import app.thumbcast.editor.state.kind
import app.thumbcast.editor.state.output
import app.thumbcast.editor.state.shapeOrNull
import app.thumbcast.model.AppActions
import app.thumbcast.model.Behavior
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Control
import app.thumbcast.model.ModifierControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadStick
import app.thumbcast.model.ProfileValidator
import app.thumbcast.model.Shape
import app.thumbcast.model.StickControl
import kotlin.math.roundToInt

/** Panel właściwości: akcje, ustawienia układu, dodawanie kontrolek i właściwości zaznaczonej kontrolki. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertiesPanel(
    store: EditorStore,
    errors: List<String>,
    aspect: Float,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onHide: () -> Unit,
    onSwapSide: () -> Unit,
    onShowErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: EditorState = store.state
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { store.update { it.undo() } }, enabled = state.canUndo) {
                    Text(stringResource(R.string.editor_undo))
                }
                TextButton(onClick = { store.update { it.redo() } }, enabled = state.canRedo) {
                    Text(stringResource(R.string.editor_redo))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSwapSide) { Text("⇄") }
                TextButton(onClick = onHide) { Text("✕") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = errors.isEmpty()) { Text(stringResource(R.string.editor_save)) }
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.editor_close)) }
            }
            TextButton(onClick = onShowErrors, enabled = errors.isNotEmpty()) {
                Text(
                    if (errors.isEmpty()) {
                        stringResource(R.string.editor_no_problems)
                    } else {
                        pluralStringResource(R.plurals.editor_problems, errors.size, errors.size)
                    },
                    color = if (errors.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()
            LayoutSection(store)

            HorizontalDivider()
            SectionTitle(stringResource(R.string.editor_section_add))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (kind in NewControlKind.values()) {
                    OutlinedButton(onClick = { store.update { it.addControl(kind, aspect) } }) {
                        Text(kindName(kind))
                    }
                }
            }

            HorizontalDivider()
            val selected: Control? = state.selected
            if (selected == null) {
                Text(
                    stringResource(R.string.editor_nothing_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectedSection(store, state, selected)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LayoutSection(store: EditorStore) {
    val state: EditorState = store.state
    SectionTitle(stringResource(R.string.editor_section_layout))
    OutlinedTextField(
        value = state.profile.name,
        onValueChange = { v -> store.update { it.setName(v) } },
        label = { Text(stringResource(R.string.editor_profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val opacity: Float = state.profile.global.opacity
    LabeledSlider(
        label = stringResource(R.string.editor_opacity, (opacity * 100f).roundToInt()),
        value = opacity,
        range = ProfileValidator.OPACITY_MIN..1f,
        onChange = { v -> store.update { it.sliderOpacity(v) } },
        onFinished = { store.update { it.endGesture() } },
    )
    SwitchRow(stringResource(R.string.editor_snap), state.snapToGrid) { store.update { it.toggleSnap() } }
    OutlinedButton(onClick = { store.update { it.mirror() } }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.editor_mirror))
    }
}

@Composable
private fun SelectedSection(store: EditorStore, state: EditorState, c: Control) {
    val id: String = c.id
    SectionTitle(stringResource(R.string.editor_section_selected, kindName(c.kind()) + " · " + id))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { store.update { it.duplicateSelected() } }) { Text(stringResource(R.string.editor_duplicate)) }
        OutlinedButton(onClick = { store.update { it.deleteSelected() } }) {
            Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
        }
    }
    OutlinedTextField(
        value = c.label ?: "",
        onValueChange = { v -> store.update { it.setLabel(id, v) } },
        label = { Text(stringResource(R.string.editor_label)) },
        placeholder = { Text(stringResource(R.string.editor_label_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledSlider(
        label = stringResource(R.string.editor_size, (c.rect.h * 100f).roundToInt()),
        value = c.rect.h,
        range = SIZE_MIN..1f,
        onChange = { v -> store.update { it.sliderSize(id, v) } },
        onFinished = { store.update { it.endGesture() } },
    )

    OutputPicker(store, state, c)

    val shape: Shape? = c.shapeOrNull()
    if (shape != null) {
        SubTitle(stringResource(R.string.editor_shape))
        ChipRow(
            options = Shape.values().toList(),
            selected = shape,
            label = { shapeName(it) },
            onSelect = { s -> store.update { it.setShape(id, s) } },
        )
    }
    if (c is ModifierControl) {
        SubTitle(stringResource(R.string.editor_behavior))
        ChipRow(
            options = Behavior.values().toList(),
            selected = c.behavior,
            label = {
                if (it == Behavior.HOLD) stringResource(R.string.editor_behavior_hold) else stringResource(R.string.editor_behavior_latch)
            },
            onSelect = { b -> store.update { it.setBehavior(id, b) } },
        )
    }
    if (c is StickControl) {
        SwitchRow(stringResource(R.string.editor_floating), c.floating) { v -> store.update { it.setFloating(id, v) } }
        val maxDz: Float = minOf(ProfileValidator.DEADZONE_MAX, ProfileValidator.DEADZONE_SUM_MAX - c.outerDeadzone)
        LabeledSlider(
            label = stringResource(R.string.editor_deadzone, (c.deadzone * 100f).roundToInt()),
            value = c.deadzone.coerceIn(0f, maxOf(0f, maxDz)),
            range = 0f..maxOf(0.01f, maxDz),
            onChange = { v -> store.update { it.sliderDeadzone(id, v) } },
            onFinished = { store.update { it.endGesture() } },
        )
        SwitchRow(stringResource(R.string.editor_sprint_lock), c.sprintLock) { v -> store.update { it.setSprintLock(id, v) } }
    }
    if (c is ButtonControl && c.chord != null) {
        Text(
            stringResource(R.string.editor_chord_warning),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { store.update { it.removeChord(id) } }) { Text(stringResource(R.string.editor_chord_remove)) }
    }
}

/** Wybór wyjścia: najpierw grupa (pad, triggery, klawisze, mysz…), potem wartość z grupy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutputPicker(store: EditorStore, state: EditorState, c: Control) {
    val options: List<OutputOptions> = OutputCatalog.optionsFor(c, state.profile.mode)
    if (options.isEmpty()) return
    val current: Output = c.output()
    val currentGroup: OutputGroup = OutputCatalog.groupOf(current)
    var group by remember(c.id, currentGroup) {
        mutableStateOf(if (options.any { it.group == currentGroup }) currentGroup else options.first().group)
    }
    SubTitle(stringResource(R.string.editor_output))
    if (options.size > 1) {
        ChipRow(
            options = options.map { it.group },
            selected = group,
            label = { groupName(it) },
            onSelect = { group = it },
        )
    }
    val outputs: List<Output> = options.firstOrNull { it.group == group }?.outputs ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in outputs) {
            FilterChip(
                selected = o == current,
                onClick = { store.update { it.setOutput(c.id, o) } },
                label = { Text(outputName(o)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in options) {
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range), onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun kindName(kind: NewControlKind): String = when (kind) {
    NewControlKind.BUTTON -> stringResource(R.string.editor_kind_button)
    NewControlKind.STICK -> stringResource(R.string.editor_kind_stick)
    NewControlKind.MODIFIER -> stringResource(R.string.editor_kind_modifier)
    NewControlKind.SYSTEM -> stringResource(R.string.editor_kind_system)
}

@Composable
private fun shapeName(shape: Shape): String = when (shape) {
    Shape.CIRCLE -> stringResource(R.string.editor_shape_circle)
    Shape.ROUNDED_RECT -> stringResource(R.string.editor_shape_rounded)
    Shape.RECT -> stringResource(R.string.editor_shape_rect)
}

@Composable
private fun groupName(group: OutputGroup): String = when (group) {
    OutputGroup.PAD -> stringResource(R.string.editor_group_pad)
    OutputGroup.TRIGGER -> stringResource(R.string.editor_group_trigger)
    OutputGroup.KEY -> stringResource(R.string.editor_group_key)
    OutputGroup.MOUSE -> stringResource(R.string.editor_group_mouse)
    OutputGroup.STICK -> stringResource(R.string.editor_group_stick)
    OutputGroup.APP -> stringResource(R.string.editor_group_app)
}

/** Nazwa wyjścia: gałki i akcje aplikacji z zasobów, reszta jak etykieta zastępcza w grze. */
@Composable
private fun outputName(o: Output): String = when {
    o is Output.Stick && o.stick == PadStick.LEFT -> stringResource(R.string.editor_stick_left)
    o is Output.Stick -> stringResource(R.string.editor_stick_right)
    o is Output.App && o.action == AppActions.MENU -> stringResource(R.string.editor_app_menu)
    o is Output.App && o.action == AppActions.SOFT_KEYBOARD -> stringResource(R.string.editor_app_soft_keyboard)
    o is Output.App && o.action == AppActions.TOGGLE_OVERLAY -> stringResource(R.string.editor_app_toggle_overlay)
    else -> ControlLayout.labelOf(o)
}

/** Najmniejsza wysokość na suwaku rozmiaru (szczypanie i uchwyt pozwalają zejść do 2%). */
private const val SIZE_MIN = 0.04f
