package app.thumbcast.android.overlay

import app.thumbcast.model.Control
import app.thumbcast.model.Profile
import app.thumbcast.model.SystemControl

/** Krok łańcucha profilu efektywnego: czysta funkcja `Profile -> Profile` (bez stanu, bez Androida). */
fun interface ProfileStep {
    fun apply(profile: Profile): Profile
}

/**
 * Profil, który faktycznie dostają silnik i rysowanie. Czysty łańcuch funkcji:
 *
 * ```
 * profil bazowy ──► transforms (w kolejności) ──► ukrycie kontrolek (gdy hidden)
 * ```
 *
 * `transforms` to miejsce na przekształcenia geometrii, np. skalowanie kontrolek
 * (`ScaleTransform` z `:control-model`, podpinane przez `ThumbcastOverlay.setProfileTransforms`).
 * Ukrycie jest zawsze ostatnie: filtruje kontrolki już po przekształceniach.
 */
object EffectiveProfile {

    fun build(base: Profile, hidden: Boolean, transforms: List<ProfileStep> = emptyList()): Profile {
        var p: Profile = base
        for (t in transforms) p = t.apply(p)
        return if (hidden) hideControls(p) else p
    }

    /** Czy kontrolki są ukryte: ręcznie (akcja `toggle_overlay`) albo przez pad przy `global.autoHideOnGamepad`. */
    fun controlsHidden(base: Profile, hiddenByUser: Boolean, gamepadPresent: Boolean): Boolean =
        hiddenByUser || (gamepadPresent && base.global.autoHideOnGamepad)

    /**
     * Profil z ukrytymi kontrolkami: zostają tylko kontrolki systemowe (menu, klawiatura),
     * a gesty z `gestures` działają dalej (plan 4.2).
     */
    fun hideControls(profile: Profile): Profile {
        val kept: ArrayList<Control> = ArrayList()
        for (c in profile.controls) {
            if (c is SystemControl) kept.add(c)
        }
        return profile.copy(controls = kept)
    }
}
