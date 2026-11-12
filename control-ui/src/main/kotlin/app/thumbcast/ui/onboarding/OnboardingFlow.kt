package app.thumbcast.ui.onboarding

import app.thumbcast.ui.OnboardingResult
import app.thumbcast.ui.UiContract

/** Kroki kreatora (docs/ui-audit.md, sekcja 3) w kolejności wyświetlania. */
enum class OnboardingStep {
    WELCOME,
    DEVICE,
    PREPARE_PC,
    CONNECT,
    ;

    /** Numer kroku dla użytkownika (1..[COUNT]). */
    val number: Int get() = ordinal + 1

    val isFirst: Boolean get() = ordinal == 0
    val isLast: Boolean get() = ordinal == entries.size - 1

    companion object {
        val COUNT: Int = entries.size
    }
}

/**
 * Kafel kroku 2 „Czym grasz najczęściej?” i jego wynik (tabela w sekcji 3 audytu).
 * Kolejność wpisów = kolejność kafli na ekranie.
 */
enum class OnboardingChoice(val presetFile: String, val hideOnGamepad: Boolean) {
    TOUCH_XBOX(UiContract.PRESET_GAMEPAD_XBOX, hideOnGamepad = false),
    TOUCH_PLAYSTATION(UiContract.PRESET_GAMEPAD_PLAYSTATION, hideOnGamepad = false),
    PAD_XBOX(UiContract.PRESET_GAMEPAD_XBOX, hideOnGamepad = true),
    PAD_PLAYSTATION(UiContract.PRESET_GAMEPAD_PLAYSTATION, hideOnGamepad = true),
    MOUSE_KEYBOARD(UiContract.PRESET_MMO_KEYBOARD, hideOnGamepad = false),
    ;

    fun toResult(): OnboardingResult = OnboardingResult(presetFile, hideOnGamepad)

    companion object {
        /** Domyślnie zaznaczony kafel: „Pad PS4 / PS5”. */
        val DEFAULT: OnboardingChoice = PAD_PLAYSTATION

        /** Odczyt z zapisanego stanu (nazwa wpisu); nieznana wartość = [DEFAULT]. */
        fun fromName(name: String?): OnboardingChoice = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Co aktywność ma zrobić po akcji użytkownika. */
sealed interface OnboardingOutcome {
    /** Zostań w kreatorze, pokaż [state]. */
    data class Show(val state: OnboardingState) : OnboardingOutcome

    /** Zamknij z `RESULT_OK` i tym wynikiem. */
    data class Finish(val result: OnboardingResult) : OnboardingOutcome

    /** Zamknij z `RESULT_CANCELED` (:app zostawia ustawienia domyślne). */
    data object Cancel : OnboardingOutcome
}

/**
 * Stan kreatora: bieżący krok i zaznaczony kafel. Czysta logika (testy JVM: OnboardingFlowTest).
 *
 * Semantyka „Pomiń” (decyzja P1, opis w docs/ui.md):
 * - na kroku 1 = [OnboardingOutcome.Cancel] (użytkownik niczego nie wybrał, :app zostawia ustawienia domyślne);
 * - na kroku 2 i dalej = [OnboardingOutcome.Finish] z bieżącym zaznaczeniem (domyślnie „Pad PS4 / PS5”),
 *   bo kafel jest już widoczny jako wybrany.
 * Wstecz z kroku 1 = [OnboardingOutcome.Cancel], z dalszych kroków = poprzedni krok (wybór zostaje).
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val choice: OnboardingChoice = OnboardingChoice.DEFAULT,
) {
    /** Przycisk główny: następny krok, a na ostatnim zakończenie z wynikiem. */
    fun next(): OnboardingOutcome =
        if (step.isLast) OnboardingOutcome.Finish(choice.toResult())
        else OnboardingOutcome.Show(copy(step = OnboardingStep.entries[step.ordinal + 1]))

    /** Wstecz (przycisk albo gest systemowy). */
    fun back(): OnboardingOutcome =
        if (step.isFirst) OnboardingOutcome.Cancel
        else OnboardingOutcome.Show(copy(step = OnboardingStep.entries[step.ordinal - 1]))

    /** „Pomiń”. */
    fun skip(): OnboardingOutcome =
        if (step.isFirst) OnboardingOutcome.Cancel else OnboardingOutcome.Finish(choice.toResult())

    fun select(newChoice: OnboardingChoice): OnboardingState = copy(choice = newChoice)

    /** Postęp 0..1 dla paska (krok 1 = 1/4). */
    val progress: Float get() = step.number.toFloat() / OnboardingStep.COUNT

    companion object {
        /** Odtworzenie z zapisanego stanu (`rememberSaveable`): indeks kroku poza zakresem = krok 1. */
        fun restore(stepIndex: Int, choiceName: String?): OnboardingState =
            OnboardingState(
                step = OnboardingStep.entries.getOrElse(stepIndex) { OnboardingStep.WELCOME },
                choice = OnboardingChoice.fromName(choiceName),
            )
    }
}
