package app.thumbcast.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource

/**
 * Poradnik „Jak podłączyć PC” (docs/ui-audit.md, sekcja 4), offline. Wejście: opcjonalna sekcja startowa
 * [UiContract.EXTRA_GUIDE_SECTION] (odczyt przez [UiContract.guideSectionFrom]). Bez wyniku.
 *
 * P0: zaślepka pokazująca tylko sekcję startową. Treść poradnika dodaje pakiet P1.
 */
class SetupGuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val section: String? = UiContract.guideSectionFrom(intent)
        setContent {
            ThumbcastTheme {
                StubScreen(
                    title = stringResource(R.string.ui_setup_guide_title),
                    detail = section?.let { stringResource(R.string.ui_stub_section, it) },
                    onClose = ::finish,
                )
            }
        }
    }
}
