package app.thumbcast.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource

/**
 * Kreator pierwszego uruchomienia (docs/ui-audit.md, sekcja 3). Kontrakt wyniku: [UiContract]
 * (RESULT_OK z [UiContract.EXTRA_PRESET_FILE] i [UiContract.EXTRA_HIDE_ON_GAMEPAD] albo RESULT_CANCELED).
 *
 * P0: zaślepka, zawsze RESULT_CANCELED. Treść kreatora dodaje pakiet P1.
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Zamknięcie (przycisk, gest wstecz) bez wyboru = pominięcie kreatora.
        setResult(RESULT_CANCELED)
        setContent {
            ThumbcastTheme {
                StubScreen(
                    title = stringResource(R.string.ui_onboarding_title),
                    detail = null,
                    onClose = ::finish,
                )
            }
        }
    }
}
