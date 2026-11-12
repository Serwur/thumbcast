package app.thumbcast.ui.onboarding

import app.thumbcast.ui.OnboardingResult
import app.thumbcast.ui.UiContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun fiveStepsInSpecOrder() {
        assertEquals(5, OnboardingStep.COUNT)
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.DEVICE, OnboardingStep.PREPARE_PC,
                OnboardingStep.GAMEPAD_DRIVER, OnboardingStep.CONNECT,
            ),
            OnboardingStep.entries,
        )
        assertEquals(1, OnboardingStep.WELCOME.number)
        assertEquals(5, OnboardingStep.CONNECT.number)
    }

    @Test
    fun tileMappingMatchesSpecTable() {
        val expected = mapOf(
            OnboardingChoice.TOUCH_XBOX to OnboardingResult(UiContract.PRESET_GAMEPAD_XBOX, false),
            OnboardingChoice.TOUCH_PLAYSTATION to OnboardingResult(UiContract.PRESET_GAMEPAD_PLAYSTATION, false),
            OnboardingChoice.PAD_XBOX to OnboardingResult(UiContract.PRESET_GAMEPAD_XBOX, true),
            OnboardingChoice.PAD_PLAYSTATION to OnboardingResult(UiContract.PRESET_GAMEPAD_PLAYSTATION, true),
            OnboardingChoice.MOUSE_KEYBOARD to OnboardingResult(UiContract.PRESET_MMO_KEYBOARD, false),
        )
        assertEquals(expected.keys.toList(), OnboardingChoice.entries)
        for ((choice, result) in expected) {
            assertEquals(choice.name, result, choice.toResult())
            assertTrue(UiContract.isValidPresetFile(choice.presetFile))
        }
    }

    @Test
    fun defaultSelectionIsPadPlayStation() {
        assertEquals(OnboardingChoice.PAD_PLAYSTATION, OnboardingChoice.DEFAULT)
        assertEquals(OnboardingChoice.PAD_PLAYSTATION, OnboardingState().choice)
        assertEquals(OnboardingStep.WELCOME, OnboardingState().step)
    }

    @Test
    fun nextWalksAllStepsAndFinishesWithSelection() {
        var state = OnboardingState().select(OnboardingChoice.MOUSE_KEYBOARD)
        for (expected in listOf(
            OnboardingStep.DEVICE, OnboardingStep.PREPARE_PC, OnboardingStep.GAMEPAD_DRIVER, OnboardingStep.CONNECT,
        )) {
            val outcome = state.next()
            state = (outcome as OnboardingOutcome.Show).state
            assertEquals(expected, state.step)
            assertEquals(OnboardingChoice.MOUSE_KEYBOARD, state.choice)
        }
        assertEquals(
            OnboardingOutcome.Finish(OnboardingResult(UiContract.PRESET_MMO_KEYBOARD, false)),
            state.next(),
        )
    }

    @Test
    fun backGoesToPreviousStepAndCancelsOnFirst() {
        assertEquals(OnboardingOutcome.Cancel, OnboardingState().back())
        val onConnect = OnboardingState(OnboardingStep.CONNECT, OnboardingChoice.TOUCH_XBOX)
        assertEquals(
            OnboardingOutcome.Show(OnboardingState(OnboardingStep.GAMEPAD_DRIVER, OnboardingChoice.TOUCH_XBOX)),
            onConnect.back(),
        )
        assertEquals(
            OnboardingOutcome.Show(OnboardingState(OnboardingStep.WELCOME, OnboardingChoice.TOUCH_XBOX)),
            OnboardingState(OnboardingStep.DEVICE, OnboardingChoice.TOUCH_XBOX).back(),
        )
    }

    @Test
    fun skipCancelsOnFirstStepAndReturnsSelectionLater() {
        assertEquals(OnboardingOutcome.Cancel, OnboardingState().skip())
        for (step in OnboardingStep.entries.drop(1)) {
            assertEquals(
                OnboardingOutcome.Finish(OnboardingResult(UiContract.PRESET_GAMEPAD_PLAYSTATION, true)),
                OnboardingState(step).skip(),
            )
            assertEquals(
                OnboardingOutcome.Finish(OnboardingResult(UiContract.PRESET_GAMEPAD_XBOX, false)),
                OnboardingState(step, OnboardingChoice.TOUCH_XBOX).skip(),
            )
        }
    }

    @Test
    fun progressGrowsPerStep() {
        assertEquals(0.2f, OnboardingState(OnboardingStep.WELCOME).progress, 0.0001f)
        assertEquals(1f, OnboardingState(OnboardingStep.CONNECT).progress, 0.0001f)
    }

    @Test
    fun driverStepIsShownForEveryChoice() {
        // Sterownik pada jest częścią kreatora zawsze, także przy sterowaniu myszą i klawiaturą.
        for (choice in OnboardingChoice.entries) {
            var state = OnboardingState().select(choice)
            val seen = mutableListOf(state.step)
            while (true) {
                val outcome = state.next()
                if (outcome !is OnboardingOutcome.Show) break
                state = outcome.state
                seen += state.step
            }
            assertEquals(choice.name, OnboardingStep.entries, seen)
        }
    }

    @Test
    fun restoreFromSavedState() {
        assertEquals(
            OnboardingState(OnboardingStep.PREPARE_PC, OnboardingChoice.TOUCH_PLAYSTATION),
            OnboardingState.restore(2, "TOUCH_PLAYSTATION"),
        )
        assertEquals(OnboardingState(), OnboardingState.restore(-1, null))
        assertEquals(OnboardingState(), OnboardingState.restore(99, "NOPE"))
    }
}
