package app.thumbcast.android.library

import app.thumbcast.model.FreeZoneMode
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode

/**
 * Rodzaj profilu do ikony na liście biblioteki (docs/profiles.md, „Biblioteka w aplikacji”). Liczony z pliku profilu
 * (tryb i `gestures.freeZone`), więc działa też dla profili użytkownika i importów, bez tagów.
 *
 * | Rodzaj | Kiedy | Przykład | Ikona w :app |
 * |---|---|---|---|
 * | [TOUCH] | wolna strefa `direct` | Big Picture | `thumbcast_ic_touch_app` |
 * | [DESKTOP] | wolna strefa `touchpad` | Pulpit – Touchpad | `thumbcast_ic_mouse` |
 * | [KEYBOARD] | tryb `keyboard_mouse` (inna wolna strefa) | MMO – Klawiatura | `thumbcast_ic_keyboard` |
 * | [GAMEPAD_PLUS] | tryb `pad_plus`, wolna strefa `camera` | MMO – Pad+ | `thumbcast_ic_gamepad` |
 * | [GAMEPAD] | tryb `pad_plus`, wolna strefa `none` | Pad – Xbox, Pad – PlayStation | `thumbcast_ic_sports_esports` |
 *
 * Ikona opisuje profil zapisany w pliku; przełącznik „Gesty myszy” (zapis na telefonie) jej nie zmienia.
 */
enum class ProfileKind {
    GAMEPAD,
    GAMEPAD_PLUS,
    KEYBOARD,
    DESKTOP,
    TOUCH,
    ;

    companion object {
        @JvmStatic
        fun of(profile: Profile): ProfileKind = when {
            profile.gestures.freeZone == FreeZoneMode.DIRECT -> TOUCH
            profile.gestures.freeZone == FreeZoneMode.TOUCHPAD -> DESKTOP
            profile.mode == ProfileMode.KEYBOARD_MOUSE -> KEYBOARD
            profile.gestures.freeZone == FreeZoneMode.CAMERA -> GAMEPAD_PLUS
            else -> GAMEPAD
        }
    }
}
