package app.thumbcast.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Przyciski pada (nazwy jak flagi ControllerPacket z upstreamu). W JSON zapisywane wielkimi literami, np. "A", "LB".
enum class PadButton { A, B, X, Y, UP, DOWN, LEFT, RIGHT, LB, RB, START, BACK, L3, R3, GUIDE }

// Triggery analogowe. W JSON: "LT", "RT".
enum class PadTrigger { LT, RT }

// Gałki analogowe. W JSON: "left_stick", "right_stick".
enum class PadStick { LEFT, RIGHT }

// Przyciski myszy. W JSON: "left", "right", "middle".
enum class MouseBtn { LEFT, RIGHT, MIDDLE }

// Zachowanie przycisku. LATCH (dotknięcie wciska, kolejne zwalnia) wolno tylko w ModifierControl.
@Serializable
enum class Behavior {
    @SerialName("hold") HOLD,
    @SerialName("latch") LATCH,
}

@Serializable
enum class ProfileMode {
    @SerialName("pad_plus") PAD_PLUS,
    @SerialName("keyboard_mouse") KEYBOARD_MOUSE,
}

@Serializable
enum class Handedness {
    @SerialName("right") RIGHT,
    @SerialName("left") LEFT,
}

// Kształt grafiki kontrolki (strefa dotyku to zawsze rect).
@Serializable
enum class Shape {
    @SerialName("circle") CIRCLE,
    @SerialName("rounded_rect") ROUNDED_RECT,
    @SerialName("rect") RECT,
}

@Serializable
enum class CurveType {
    @SerialName("linear") LINEAR,
    @SerialName("power") POWER,
}

// Efekt haptyczny przy naciśnięciu / zwolnieniu.
@Serializable
enum class HapticEffect {
    @SerialName("none") NONE,
    @SerialName("tick") TICK,
    @SerialName("click") CLICK,
}

// Strefa gestu kamery: wolna (niezajęta przez kontrolki) część prawej albo lewej połowy ekranu.
@Serializable
enum class GestureZone {
    @SerialName("right_free") RIGHT_FREE,
    @SerialName("left_free") LEFT_FREE,
}

// Właściciel wolnej strefy (część ekranu bez kontrolek). Jeden na profil: jedno przeciągnięcie nigdy nie daje
// jednocześnie kamery i ruchu myszy.
// CAMERA: tabela gestów Pad+ (kamera z cameraDrag, tap, long-press, pinch, edge swipe).
// TOUCHPAD: tabela gestów touchpada (ruch kursora, kliknięcia w miejscu kursora, przewijanie dwoma palcami).
// NONE (12.11.2026): wolna strefa nic nie wysyła do hosta; działa tylko edgeSwipeTop (menu), jeśli włączony.
// DIRECT (12.11.2026): mysz bezpośrednia: tap = kursor w punkcie dotyku + lewy klik, tap dwoma palcami = prawy klik
// w punkcie pierwszego palca, przeciągnięcie jednym palcem = przewijanie (pion i poziom) w punkcie dotyku.
@Serializable
enum class FreeZoneMode {
    @SerialName("camera") CAMERA,
    @SerialName("touchpad") TOUCHPAD,
    @SerialName("none") NONE,
    @SerialName("direct") DIRECT,
}

// Wyjście gestu przeciągania kamery.
// PAD_RIGHT_STICK: prędkość palca -> wychylenie prawej gałki (tylko PAD_PLUS).
// MOUSE_LOOK: prawy przycisk myszy trzymany przez cały gest + ruch względny (RMB-look).
@Serializable
enum class CameraOutput {
    @SerialName("pad_right_stick") PAD_RIGHT_STICK,
    @SerialName("mouse_look") MOUSE_LOOK,
}

@Serializable
enum class TapOutput {
    @SerialName("mouse_left_at_point") MOUSE_LEFT_AT_POINT,
}

@Serializable
enum class LongPressOutput {
    @SerialName("mouse_right_at_point") MOUSE_RIGHT_AT_POINT,
}

@Serializable
enum class PinchOutput {
    @SerialName("mouse_wheel") MOUSE_WHEEL,
}

@Serializable
enum class EdgeSwipeOutput {
    @SerialName("app_menu") APP_MENU,
}
