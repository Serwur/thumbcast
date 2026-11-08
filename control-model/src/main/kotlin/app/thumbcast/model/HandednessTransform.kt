package app.thumbcast.model

/**
 * Lustro lewo/prawo dla leworęcznych (plan 5.4): `x -> 1 - x - w` dla każdego rect,
 * zamiana stref gestów lewa/prawa i przełączenie `handedness`. Wyjścia się nie zmieniają
 * (lewa gałka zostaje ruchem, tylko trafia na prawą stronę ekranu).
 *
 * Wynik jest zaokrąglany do siatki 1e-4, dzięki czemu podwójne lustro jest tożsamością
 * dla współrzędnych zapisanych z dokładnością do 4 miejsc po przecinku (tak zapisuje edytor i presety).
 */
object HandednessTransform {

    private const val GRID = 10000.0

    fun mirror(p: Profile): Profile = p.copy(
        handedness = if (p.handedness == Handedness.RIGHT) Handedness.LEFT else Handedness.RIGHT,
        gestures = p.gestures.copy(
            cameraDrag = p.gestures.cameraDrag.copy(zone = mirrorZone(p.gestures.cameraDrag.zone)),
        ),
        controls = p.controls.map { mirrorControl(it) },
    )

    /** Zwraca profil w żądanej orientacji (bez zmian, jeśli już ją ma). */
    fun toHandedness(p: Profile, target: Handedness): Profile = if (p.handedness == target) p else mirror(p)

    fun mirrorRect(r: Rect): Rect {
        val mirroredX = 1.0 - r.x.toDouble() - r.w.toDouble()
        return r.copy(x = snap(mirroredX))
    }

    fun mirrorZone(z: GestureZone): GestureZone = when (z) {
        GestureZone.RIGHT_FREE -> GestureZone.LEFT_FREE
        GestureZone.LEFT_FREE -> GestureZone.RIGHT_FREE
    }

    private fun mirrorControl(c: Control): Control = when (c) {
        is StickControl -> c.copy(rect = mirrorRect(c.rect))
        is ButtonControl -> c.copy(rect = mirrorRect(c.rect))
        is ModifierControl -> c.copy(rect = mirrorRect(c.rect))
        is SystemControl -> c.copy(rect = mirrorRect(c.rect))
    }

    private fun snap(v: Double): Float {
        val rounded = Math.round(v * GRID).toDouble() / GRID
        val clamped = if (rounded < 0.0) 0.0 else if (rounded > 1.0) 1.0 else rounded
        return clamped.toFloat()
    }
}
