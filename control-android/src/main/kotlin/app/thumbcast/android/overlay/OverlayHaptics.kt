package app.thumbcast.android.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import app.thumbcast.model.HapticEffect
import app.thumbcast.model.Haptics

/**
 * Haptyka przycisków overlaya (plan 5.4).
 *
 * - API 29+: `VibrationEffect.createPredefined(EFFECT_TICK)` (albo `EFFECT_CLICK`, gdy profil ma "click");
 * - API 26–28: `VibrationEffect.createOneShot(10 ms, DEFAULT_AMPLITUDE)`; jeśli wibrator ma kontrolę
 *   amplitudy, amplituda = `strength` z profilu (1..255).
 * - Szacunek dla ustawień: profil (`haptics.enabled`, `onPress`, `onRelease`) ORAZ systemowe
 *   `Settings.System.HAPTIC_FEEDBACK_ENABLED` (sprawdzane przy każdym [refreshSystemSetting]).
 *
 * Wibracja to wyłącznie lokalne potwierdzenie dotyku; nie ma wpływu na to, co idzie do hosta.
 */
class OverlayHaptics(private val context: Context) {

    private val vibrator: Vibrator? = findVibrator(context)
    private var config: Haptics = Haptics()
    private var systemEnabled: Boolean = true

    fun setConfig(h: Haptics) {
        config = h
    }

    /** Odczyt ustawienia systemowego; wołane przy przypięciu do okna i po odzyskaniu fokusu. */
    fun refreshSystemSetting() {
        systemEnabled = try {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        } catch (e: SecurityException) {
            true
        }
    }

    fun onPress() {
        play(config.onPress)
    }

    fun onRelease() {
        play(config.onRelease)
    }

    private fun play(effect: HapticEffect) {
        if (!config.enabled || !systemEnabled || effect == HapticEffect.NONE) {
            return
        }
        val v: Vibrator = vibrator ?: return
        if (!v.hasVibrator()) {
            return
        }
        val ve: VibrationEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id: Int = if (effect == HapticEffect.CLICK) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
            VibrationEffect.createPredefined(id)
        } else {
            val amplitude: Int = if (v.hasAmplitudeControl()) {
                val a: Int = Math.round(config.strength * 255f)
                if (a < 1) 1 else if (a > 255) 255 else a
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            val ms: Long = if (effect == HapticEffect.CLICK) 20L else 10L
            VibrationEffect.createOneShot(ms, amplitude)
        }
        v.vibrate(ve)
    }

    companion object {
        private fun findVibrator(context: Context): Vibrator? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm: VibratorManager? = context.getSystemService(VibratorManager::class.java)
                return vm?.defaultVibrator
            }
            @Suppress("DEPRECATION")
            val legacy: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            return legacy
        }
    }
}
