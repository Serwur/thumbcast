package app.thumbcast.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.thumbcast.ui.guide.GuideLinks
import app.thumbcast.ui.onboarding.OnboardingChoice
import app.thumbcast.ui.onboarding.OnboardingOutcome
import app.thumbcast.ui.onboarding.OnboardingState
import kotlinx.coroutines.launch

/**
 * Kreator pierwszego uruchomienia (docs/ui-audit.md, sekcja 3; opis w docs/ui.md). Kontrakt wyniku: [UiContract]
 * (RESULT_OK z [UiContract.EXTRA_PRESET_FILE] i [UiContract.EXTRA_HIDE_ON_GAMEPAD] albo RESULT_CANCELED).
 *
 * Kreator niczego nie zapisuje; zapis robi :app po wyniku. Logika kroków i „Pomiń”: [OnboardingState].
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Zamknięcie bez wyboru (Wstecz na kroku 1, zamknięcie zadania) = pominięcie kreatora.
        setResult(RESULT_CANCELED)
        setContent {
            ThumbcastTheme {
                var stepIndex by rememberSaveable { mutableIntStateOf(0) }
                var choiceName by rememberSaveable { mutableStateOf(OnboardingChoice.DEFAULT.name) }
                val state = OnboardingState.restore(stepIndex, choiceName)
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                fun handle(outcome: OnboardingOutcome) {
                    when (outcome) {
                        is OnboardingOutcome.Show -> {
                            stepIndex = outcome.state.step.ordinal
                            choiceName = outcome.state.choice.name
                        }
                        is OnboardingOutcome.Finish -> finishWith(outcome)
                        OnboardingOutcome.Cancel -> finishWith(outcome)
                    }
                }

                // Gest wstecz: poprzedni krok. Na kroku 1 wyłączony, więc systemowe zamknięcie da RESULT_CANCELED.
                BackHandler(enabled = !state.step.isFirst) { handle(state.back()) }

                val linkFailed = stringResource(R.string.ui_link_no_app)
                OnboardingScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    actions = OnboardingActions(
                        onNext = { handle(state.next()) },
                        onBack = { handle(state.back()) },
                        onSkip = { handle(state.skip()) },
                        onSelect = { choiceName = it.name },
                        onOpenGuide = {
                            startActivity(UiContract.setupGuideIntent(this, UiContract.SECTION_INSTALL))
                        },
                        onShareLink = {
                            val ok = LinkActions.share(
                                context = this,
                                chooserTitle = getString(R.string.ui_share_chooser_title),
                                subject = getString(R.string.ui_share_sunshine_subject),
                                text = GuideLinks.shareText(
                                    getString(R.string.ui_share_sunshine_hint),
                                    GuideLinks.SUNSHINE_RELEASES,
                                ),
                                url = GuideLinks.SUNSHINE_RELEASES,
                            )
                            if (!ok) scope.launch { snackbarHostState.showSnackbar(linkFailed) }
                        },
                    ),
                )
            }
        }
    }

    private fun finishWith(outcome: OnboardingOutcome) {
        when (outcome) {
            is OnboardingOutcome.Finish -> setResult(RESULT_OK, UiContract.onboardingResultData(outcome.result))
            else -> setResult(RESULT_CANCELED)
        }
        finish()
    }
}
