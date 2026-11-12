package app.thumbcast.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import app.thumbcast.ui.guide.GuideLinks
import app.thumbcast.ui.guide.SetupGuideModel
import kotlinx.coroutines.launch

/**
 * Poradnik „Jak podłączyć PC” (docs/ui-audit.md, sekcja 4), offline. Wejście: opcjonalna sekcja startowa
 * [UiContract.EXTRA_GUIDE_SECTION] (odczyt przez [UiContract.guideSectionFrom]): lista przewija się do niej i ją
 * rozwija. Bez wyniku.
 *
 * Lista kontrolna („Zrobione” przy sekcjach) jest tylko pomocą dla użytkownika: zapis we własnych
 * SharedPreferences modułu ([PREFS_NAME]), nic poza poradnikiem jej nie czyta.
 */
class SetupGuideActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val requested: String? = UiContract.guideSectionFrom(intent)
        setContent {
            ThumbcastTheme {
                var done by remember { mutableStateOf(SetupGuideModel.sanitizeDone(prefs.getStringSet(KEY_DONE, null))) }
                val initial = remember { SetupGuideModel.initialSection(requested, done) }
                var expanded by rememberSaveable(stateSaver = StringSetSaver) {
                    mutableStateOf(setOfNotNull(initial))
                }
                // Sekcja z intencji: lista startuje od niej (stan przewinięcia przetrwa obrót ekranu).
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = if (requested != null) guideListIndex(requested) else 0,
                )
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val linkFailed = stringResource(R.string.ui_link_no_app)

                SetupGuideScreen(
                    listState = listState,
                    expanded = expanded,
                    done = done,
                    snackbarHostState = snackbarHostState,
                    actions = SetupGuideActions(
                        onBack = ::finish,
                        onToggleExpanded = { expanded = SetupGuideModel.toggleExpanded(expanded, it) },
                        onJump = { target ->
                            expanded = expanded + target
                            scope.launch { listState.animateScrollToItem(guideListIndex(target)) }
                        },
                        onDoneChange = { section, checked ->
                            done = SetupGuideModel.setDone(done, section, checked)
                            prefs.edit { putStringSet(KEY_DONE, HashSet(done)) }
                        },
                        onOpenLink = { link ->
                            if (!LinkActions.open(this, link.url)) {
                                scope.launch { snackbarHostState.showSnackbar(linkFailed) }
                            }
                        },
                        onShareLink = { link ->
                            val ok = LinkActions.share(
                                context = this,
                                chooserTitle = getString(R.string.ui_share_chooser_title),
                                subject = getString(link.label),
                                text = GuideLinks.shareText(getString(link.shareHint), link.url),
                                url = link.url,
                            )
                            if (!ok) scope.launch { snackbarHostState.showSnackbar(linkFailed) }
                        },
                    ),
                )
            }
        }
    }

    private companion object {
        /** Własny plik preferencji modułu (nie domyślne SharedPreferences aplikacji). */
        const val PREFS_NAME = "thumbcast_ui_guide"
        const val KEY_DONE = "done_sections"

        val StringSetSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        )
    }
}
