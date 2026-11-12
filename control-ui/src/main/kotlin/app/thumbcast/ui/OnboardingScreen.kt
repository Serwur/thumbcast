package app.thumbcast.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.thumbcast.ui.onboarding.OnboardingChoice
import app.thumbcast.ui.onboarding.OnboardingState
import app.thumbcast.ui.onboarding.OnboardingStep

/** Akcje ekranu kreatora; logika kroków jest w [OnboardingState], aktywność tylko przekłada wynik. */
internal class OnboardingActions(
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onSkip: () -> Unit,
    val onSelect: (OnboardingChoice) -> Unit,
    /** Poradnik otwarty na sekcji ([UiContract.SECTION_INSTALL], [UiContract.SECTION_GAMEPAD]). */
    val onOpenGuide: (String) -> Unit,
    val onShareSunshine: () -> Unit,
    val onShareDriver: () -> Unit,
)

/** Kreator pierwszego uruchomienia (docs/ui-audit.md, sekcja 3): pasek postępu, „Pomiń”, treść kroku, przyciski. */
@Composable
internal fun OnboardingScreen(
    state: OnboardingState,
    actions: OnboardingActions,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { OnboardingTopBar(state.step, state.progress, actions.onSkip) },
        bottomBar = { OnboardingBottomBar(state.step, actions.onBack, actions.onNext) },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "onboardingStep",
        ) { step ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = ThumbcastDimens.SpaceLarge, vertical = ThumbcastDimens.SpaceMedium),
                    verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
                ) {
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.DEVICE -> DeviceStep(state.choice, actions.onSelect)
                        OnboardingStep.PREPARE_PC -> PreparePcStep(
                            onOpenGuide = { actions.onOpenGuide(UiContract.SECTION_INSTALL) },
                            onShareLink = actions.onShareSunshine,
                        )
                        OnboardingStep.GAMEPAD_DRIVER -> DriverStep(
                            onOpenGuide = { actions.onOpenGuide(UiContract.SECTION_GAMEPAD) },
                            onShareLink = actions.onShareDriver,
                        )
                        OnboardingStep.CONNECT -> ConnectStep()
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(step: OnboardingStep, progress: Float, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(start = ThumbcastDimens.SpaceLarge, end = ThumbcastDimens.SpaceSmall, top = ThumbcastDimens.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ui_onboarding_step_of, step.number, OnboardingStep.COUNT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Zmiana kroku jest ogłaszana przez czytnik ekranu.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = ThumbcastDimens.TouchTarget)) {
                Text(stringResource(R.string.ui_onboarding_skip))
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = ThumbcastDimens.SpaceLarge, vertical = ThumbcastDimens.SpaceSmall),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun OnboardingBottomBar(step: OnboardingStep, onBack: () -> Unit, onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = ThumbcastDimens.SpaceMedium, vertical = ThumbcastDimens.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall),
        ) {
            if (!step.isFirst) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = ThumbcastDimens.TouchTarget)) {
                    Icon(UiIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
                    Text(stringResource(R.string.ui_onboarding_back))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, modifier = Modifier.heightIn(min = ThumbcastDimens.TouchTarget)) {
                Text(stringResource(primaryLabel(step)))
            }
        }
    }
}

@StringRes
private fun primaryLabel(step: OnboardingStep): Int = when (step) {
    OnboardingStep.WELCOME -> R.string.ui_onboarding_start
    OnboardingStep.DEVICE, OnboardingStep.PREPARE_PC, OnboardingStep.GAMEPAD_DRIVER -> R.string.ui_onboarding_next
    OnboardingStep.CONNECT -> R.string.ui_onboarding_finish
}

// ----- Kroki -----

/** Tytuł kroku z dużą ikoną nad nim (ikona dekoracyjna, tytuł jest nagłówkiem). */
@Composable
private fun StepTitle(icon: ImageVector, @StringRes title: Int, @StringRes subtitle: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall)) {
        Box(
            modifier = Modifier
                .padding(bottom = ThumbcastDimens.SpaceXSmall)
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    StepTitle(UiIcons.VideogameAsset, R.string.ui_onboarding_welcome_title, R.string.ui_onboarding_welcome_subtitle)
    Spacer(Modifier.size(ThumbcastDimens.SpaceXSmall))
    Benefit(UiIcons.TouchApp, R.string.ui_onboarding_benefit_controls_title, R.string.ui_onboarding_benefit_controls_body)
    Benefit(UiIcons.Computer, R.string.ui_onboarding_benefit_pc_title, R.string.ui_onboarding_benefit_pc_body)
    Benefit(UiIcons.Lock, R.string.ui_onboarding_benefit_secure_title, R.string.ui_onboarding_benefit_secure_body)
    Benefit(UiIcons.VpnKey, R.string.ui_onboarding_benefit_remote_title, R.string.ui_onboarding_benefit_remote_body)
    NoteRow(UiIcons.DesktopWindows, stringResource(R.string.ui_onboarding_welcome_note))
}

@Composable
private fun Benefit(icon: ImageVector, @StringRes title: Int, @StringRes body: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ikona 24 dp w kółku 40 dp z tłem akcentu (dekoracyjna: obok zawsze jest tekst). */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private class ChoiceUi(val choice: OnboardingChoice, val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

private val CHOICES: List<ChoiceUi> by lazy {
    OnboardingChoice.entries.map {
        when (it) {
            OnboardingChoice.TOUCH_XBOX ->
                ChoiceUi(it, UiIcons.TouchApp, R.string.ui_choice_touch_xbox, R.string.ui_choice_touch_xbox_body)
            OnboardingChoice.TOUCH_PLAYSTATION ->
                ChoiceUi(it, UiIcons.TouchApp, R.string.ui_choice_touch_playstation, R.string.ui_choice_touch_playstation_body)
            OnboardingChoice.PAD_XBOX ->
                ChoiceUi(it, UiIcons.SportsEsports, R.string.ui_choice_pad_xbox, R.string.ui_choice_pad_body)
            OnboardingChoice.PAD_PLAYSTATION ->
                ChoiceUi(it, UiIcons.Gamepad, R.string.ui_choice_pad_playstation, R.string.ui_choice_pad_body)
            OnboardingChoice.MOUSE_KEYBOARD ->
                ChoiceUi(it, UiIcons.Keyboard, R.string.ui_choice_mouse_keyboard, R.string.ui_choice_mouse_keyboard_body)
        }
    }
}

@Composable
private fun DeviceStep(selected: OnboardingChoice, onSelect: (OnboardingChoice) -> Unit) {
    StepTitle(UiIcons.Gamepad, R.string.ui_onboarding_device_title, R.string.ui_onboarding_device_subtitle)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Na tablecie i w poziomie dwie kolumny kafli, na telefonie w pionie jedna.
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
        ) {
            for (row in CHOICES.chunked(columns)) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
                ) {
                    for (item in row) {
                        ChoiceTile(
                            item = item,
                            selected = item.choice == selected,
                            onClick = { onSelect(item.choice) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    NoteRow(UiIcons.Settings, stringResource(R.string.ui_onboarding_device_note))
}

@Composable
private fun ChoiceTile(item: ChoiceUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(ThumbcastDimens.SpaceMedium),
            horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(item.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            // Stan zaznaczenia widać nie tylko po kolorze: kółko wyboru (obsługuje je kafel, nie samo kółko).
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** Ikony kroków 3–5, w kolejności pozycji `string-array` (ta sama liczba pozycji w PL i EN). */
private val PREPARE_ICONS: List<ImageVector> by lazy {
    listOf(
        UiIcons.Share, UiIcons.Language, UiIcons.SwipeDown, UiIcons.Download,
        UiIcons.InstallDesktop, UiIcons.Link, UiIcons.VerifiedUser, UiIcons.Person,
    )
}

private val DRIVER_ICONS: List<ImageVector> by lazy {
    listOf(UiIcons.Share, UiIcons.Warning, UiIcons.Download, UiIcons.InstallDesktop, UiIcons.RestartAlt, UiIcons.Settings)
}

private val CONNECT_ICONS: List<ImageVector> by lazy {
    listOf(UiIcons.Wifi, UiIcons.Computer, UiIcons.Pin)
}

@Composable
private fun PreparePcStep(onOpenGuide: () -> Unit, onShareLink: () -> Unit) {
    StepTitle(UiIcons.InstallDesktop, R.string.ui_onboarding_prepare_title, R.string.ui_onboarding_prepare_subtitle)
    LinkButtons(onShareLink, onOpenGuide)
    IconSteps(stringArrayResource(R.array.ui_onboarding_prepare_points), PREPARE_ICONS)
    NoteRow(UiIcons.Info, stringResource(R.string.ui_onboarding_send_link_hint))
}

/** Sterownik pada: krok obowiązkowy w kreatorze, niezależnie od wybranego sterowania. */
@Composable
private fun DriverStep(onOpenGuide: () -> Unit, onShareLink: () -> Unit) {
    StepTitle(UiIcons.SportsEsports, R.string.ui_onboarding_driver_title, R.string.ui_onboarding_driver_subtitle)
    LinkButtons(onShareLink, onOpenGuide)
    IconSteps(stringArrayResource(R.array.ui_onboarding_driver_points), DRIVER_ICONS)
    NoteRow(UiIcons.Payments, stringResource(R.string.ui_onboarding_driver_paid_note))
}

/** „Wyślij sobie link” (share sheet) i „Pokaż pełny poradnik” nad krokami, żeby były widoczne bez przewijania. */
@Composable
private fun LinkButtons(onShareLink: () -> Unit, onOpenGuide: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceXSmall)) {
        FilledTonalButton(
            onClick = onShareLink,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ThumbcastDimens.TouchTarget),
        ) {
            Icon(UiIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
            Text(stringResource(R.string.ui_onboarding_send_link))
        }
        OutlinedButton(
            onClick = onOpenGuide,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ThumbcastDimens.TouchTarget),
        ) {
            Icon(UiIcons.Help, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
            Text(stringResource(R.string.ui_onboarding_show_guide))
        }
    }
}

@Composable
private fun ConnectStep() {
    StepTitle(UiIcons.Wifi, R.string.ui_onboarding_connect_title, R.string.ui_onboarding_connect_subtitle)
    IconSteps(stringArrayResource(R.array.ui_onboarding_connect_points), CONNECT_ICONS)
    NoteRow(UiIcons.AddCircle, stringResource(R.string.ui_onboarding_connect_note))
    NoteRow(UiIcons.Help, stringResource(R.string.ui_onboarding_connect_help))
}
