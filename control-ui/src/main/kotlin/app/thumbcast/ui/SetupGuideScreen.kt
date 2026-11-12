package app.thumbcast.ui

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.thumbcast.ui.guide.GuideLinks
import app.thumbcast.ui.guide.SetupGuideModel

/** Strona pobierania w sekcji poradnika: przycisk „Wyślij link na PC” (share sheet) i „Otwórz stronę”. */
internal class GuideLinkUi(@StringRes val label: Int, val url: String, @StringRes val shareHint: Int)

/** Uwaga pod krokami sekcji: ikona i tekst. */
internal class GuideNoteUi(val icon: ImageVector, @StringRes val text: Int)

/**
 * Treść jednej sekcji poradnika (docs/ui-audit.md, sekcja 4). Kroki są w `string-array`, kod w znakach `` ` ``.
 * [stepIcons] idą w kolejności pozycji `string-array` (ta sama liczba pozycji w PL i EN).
 */
internal class GuideSectionUi(
    val id: String,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val intro: Int?,
    @ArrayRes val steps: Int,
    val stepIcons: List<ImageVector>,
    val links: List<GuideLinkUi> = emptyList(),
    /** Sekcje, do których prowadzą przyciski „Zobacz: …” (np. z „Nie działa?” do „Sterownik pada”). */
    val jumps: List<String> = emptyList(),
    val notes: List<GuideNoteUi> = emptyList(),
)

internal val GUIDE_SECTIONS_UI: List<GuideSectionUi> by lazy {
    listOf(
        GuideSectionUi(
            id = UiContract.SECTION_INSTALL,
            icon = UiIcons.Download,
            title = R.string.ui_guide_install_title,
            intro = R.string.ui_guide_install_intro,
            steps = R.array.ui_guide_install_steps,
            stepIcons = listOf(
                UiIcons.Language, UiIcons.SwipeDown, UiIcons.Download, UiIcons.InstallDesktop,
                UiIcons.Link, UiIcons.VerifiedUser, UiIcons.Person,
            ),
            links = listOf(
                GuideLinkUi(R.string.ui_guide_link_sunshine, GuideLinks.SUNSHINE_RELEASES, R.string.ui_share_sunshine_hint),
            ),
            notes = listOf(
                GuideNoteUi(UiIcons.Computer, R.string.ui_guide_install_note_arm),
                GuideNoteUi(UiIcons.Info, R.string.ui_guide_install_note),
            ),
        ),
        GuideSectionUi(
            id = UiContract.SECTION_GAMEPAD,
            icon = UiIcons.SportsEsports,
            title = R.string.ui_guide_gamepad_title,
            intro = R.string.ui_guide_gamepad_intro,
            steps = R.array.ui_guide_gamepad_steps,
            stepIcons = listOf(
                UiIcons.Language, UiIcons.Warning, UiIcons.Download, UiIcons.InstallDesktop,
                UiIcons.RestartAlt, UiIcons.Settings,
            ),
            links = listOf(
                GuideLinkUi(R.string.ui_guide_link_vigembus, GuideLinks.VIGEMBUS_RELEASES, R.string.ui_share_vigembus_hint),
            ),
            notes = listOf(
                GuideNoteUi(UiIcons.Payments, R.string.ui_guide_gamepad_paid_note),
                GuideNoteUi(UiIcons.Keyboard, R.string.ui_guide_gamepad_keyboard_note),
            ),
        ),
        GuideSectionUi(
            id = UiContract.SECTION_PAIR,
            icon = UiIcons.Pin,
            title = R.string.ui_guide_pair_title,
            intro = null,
            steps = R.array.ui_guide_pair_steps,
            stepIcons = listOf(UiIcons.Wifi, UiIcons.Smartphone, UiIcons.Pin, UiIcons.CheckCircle, UiIcons.AddCircle),
        ),
        GuideSectionUi(
            id = UiContract.SECTION_APPS,
            icon = UiIcons.PlayArrow,
            title = R.string.ui_guide_apps_title,
            intro = null,
            steps = R.array.ui_guide_apps_steps,
            stepIcons = listOf(UiIcons.Apps, UiIcons.VideogameAsset, UiIcons.PlayArrow, UiIcons.Smartphone, UiIcons.DesktopWindows),
        ),
        GuideSectionUi(
            id = UiContract.SECTION_REMOTE,
            icon = UiIcons.TravelExplore,
            title = R.string.ui_guide_remote_title,
            intro = R.string.ui_guide_remote_intro,
            steps = R.array.ui_guide_remote_steps,
            stepIcons = listOf(UiIcons.Download, UiIcons.Wifi, UiIcons.AddCircle, UiIcons.Router, UiIcons.Warning),
            links = listOf(
                GuideLinkUi(R.string.ui_guide_link_tailscale, GuideLinks.TAILSCALE_DOWNLOAD, R.string.ui_share_tailscale_hint),
            ),
        ),
        GuideSectionUi(
            id = UiContract.SECTION_TROUBLESHOOT,
            icon = UiIcons.Help,
            title = R.string.ui_guide_troubleshoot_title,
            intro = null,
            steps = R.array.ui_guide_troubleshoot_steps,
            stepIcons = listOf(UiIcons.WifiOff, UiIcons.SportsEsports, UiIcons.Speed, UiIcons.VpnKey),
            jumps = listOf(UiContract.SECTION_GAMEPAD, UiContract.SECTION_REMOTE),
        ),
    ).also { list -> check(list.map { it.id } == SetupGuideModel.SECTIONS) }
}

private fun sectionUi(id: String): GuideSectionUi = GUIDE_SECTIONS_UI.first { it.id == id }

/** Akcje ekranu poradnika. */
internal class SetupGuideActions(
    val onBack: () -> Unit,
    val onToggleExpanded: (String) -> Unit,
    val onJump: (String) -> Unit,
    val onDoneChange: (String, Boolean) -> Unit,
    val onOpenLink: (GuideLinkUi) -> Unit,
    val onShareLink: (GuideLinkUi) -> Unit,
)

/** Pozycja sekcji na liście (pierwsza pozycja to wstęp). */
internal fun guideListIndex(section: String): Int = 1 + SetupGuideModel.indexOf(section)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupGuideScreen(
    listState: LazyListState,
    expanded: Set<String>,
    done: Set<String>,
    actions: SetupGuideActions,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_setup_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(UiIcons.ArrowBack, contentDescription = stringResource(R.string.ui_navigate_up))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        // Odstępy boczne (wcięcia ekranu w poziomie) na każdej pozycji, bo treść jest wyśrodkowana.
        val itemModifier = Modifier
            .padding(padding.horizontalOnly())
            .padding(horizontal = ThumbcastDimens.SpaceMedium)
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + ThumbcastDimens.SpaceSmall,
                bottom = padding.calculateBottomPadding() + ThumbcastDimens.SpaceLarge,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium - ThumbcastDimens.SpaceXSmall),
        ) {
            item(key = "intro") {
                GuideIntro(done = done, modifier = itemModifier)
            }
            itemsIndexed(GUIDE_SECTIONS_UI, key = { _, s -> s.id }) { index, section ->
                GuideSectionCard(
                    number = index + 1,
                    section = section,
                    expanded = section.id in expanded,
                    done = section.id in done,
                    actions = actions,
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
private fun PaddingValues.horizontalOnly(): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(start = calculateStartPadding(dir), end = calculateEndPadding(dir))
}

@Composable
private fun GuideIntro(done: Set<String>, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall)) {
        Text(
            text = stringResource(R.string.ui_guide_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall),
        ) {
            Icon(
                UiIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    R.string.ui_guide_progress,
                    SetupGuideModel.doneCount(done),
                    SetupGuideModel.CHECKABLE_SECTIONS.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun GuideSectionCard(
    number: Int,
    section: GuideSectionUi,
    expanded: Boolean,
    done: Boolean,
    actions: SetupGuideActions,
    modifier: Modifier,
) {
    val checkable = section.id in SetupGuideModel.CHECKABLE_SECTIONS
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SectionHeader(number, section, expanded, done && checkable) { actions.onToggleExpanded(section.id) }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(
                    start = ThumbcastDimens.SpaceMedium,
                    end = ThumbcastDimens.SpaceMedium,
                    bottom = ThumbcastDimens.SpaceMedium,
                ),
                verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
            ) {
                if (section.intro != null) {
                    InlineCodeText(
                        text = stringResource(section.intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconSteps(stringArrayResource(section.steps), section.stepIcons)
                for (link in section.links) {
                    LinkBlock(link, actions)
                }
                for (target in section.jumps) {
                    TextButton(
                        onClick = { actions.onJump(target) },
                        modifier = Modifier.heightIn(min = ThumbcastDimens.TouchTarget),
                    ) {
                        Icon(sectionUi(target).icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
                        Text(stringResource(R.string.ui_guide_jump_to, stringResource(sectionUi(target).title)))
                        Spacer(Modifier.size(ThumbcastDimens.SpaceXSmall))
                        Icon(UiIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                for (note in section.notes) {
                    NoteRow(note.icon, stringResource(note.text))
                }
                if (checkable) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DoneCheckbox(done) { actions.onDoneChange(section.id, it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    number: Int,
    section: GuideSectionUi,
    expanded: Boolean,
    done: Boolean,
    onClick: () -> Unit,
) {
    val expandLabel = stringResource(if (expanded) R.string.ui_guide_collapse else R.string.ui_guide_expand)
    val stateLabel = stringResource(if (expanded) R.string.ui_guide_state_expanded else R.string.ui_guide_state_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = expandLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateLabel
            }
            .padding(horizontal = ThumbcastDimens.SpaceMedium, vertical = ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
    ) {
        // Ikona sekcji; po odhaczeniu znak ✓ na zielonym tle (plus napis „Zrobione”, nie tylko kolor).
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (done) UiIcons.Check else section.icon,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ui_guide_section_title, number, stringResource(section.title)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (done) {
                Text(
                    text = stringResource(R.string.ui_guide_done_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Icon(
            imageVector = UiIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded) -90f else 90f),
        )
    }
}

@Composable
private fun LinkBlock(link: GuideLinkUi, actions: SetupGuideActions) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(ThumbcastDimens.SpaceMedium - ThumbcastDimens.SpaceXSmall),
            verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall),
        ) {
            Text(stringResource(link.label), style = MaterialTheme.typography.titleSmall)
            // Adres można zaznaczyć i skopiować (np. żeby przepisać go na PC).
            SelectionContainer {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { actions.onShareLink(link) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ThumbcastDimens.TouchTarget),
            ) {
                Icon(UiIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
                Text(stringResource(R.string.ui_guide_send_link_to_pc))
            }
            TextButton(
                onClick = { actions.onOpenLink(link) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ThumbcastDimens.TouchTarget),
            ) {
                Icon(UiIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(ThumbcastDimens.SpaceSmall))
                Text(stringResource(R.string.ui_guide_open_page))
            }
        }
    }
}

@Composable
private fun DoneCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ThumbcastDimens.TouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(R.string.ui_guide_mark_done), style = MaterialTheme.typography.bodyLarge)
    }
}
