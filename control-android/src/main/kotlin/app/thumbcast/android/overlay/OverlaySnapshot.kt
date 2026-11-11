package app.thumbcast.android.overlay

import app.thumbcast.engine.EngineVisualState
import app.thumbcast.engine.StickVisual

// Migawka „co narysować” dla OverlayRenderer: czyste dane, bez Androida. Budowana z układu kontrolek
// (geometria, etykiety) i stanu wizualnego silnika (wciśnięcia, gałki, palce). Ten sam renderer może
// rysować migawkę z innego źródła (edytor układu, podgląd presetu).

/** Element do narysowania, w kolejności z-order (później = wyżej). */
sealed interface OverlayItem {
    val control: LaidOutControl
}

data class ButtonItem(override val control: LaidOutControl, val pressed: Boolean) : OverlayItem

/**
 * Gałka: podstawa w ([centerX], [centerY]) o promieniu [radius] (promień silnika, czyli pełne wychylenie),
 * gałka w ([knobX], [knobY]). [active]: palec trzyma gałkę.
 */
data class StickItem(
    override val control: LaidOutControl,
    val active: Boolean,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val knobX: Float,
    val knobY: Float,
) : OverlayItem

data class DebugPointer(val id: Int, val x: Float, val y: Float)

data class DebugOverlay(val lines: List<String>, val pointers: List<DebugPointer>)

data class OverlaySnapshot(
    /** Krycie 0..255 z `global.opacity`. */
    val alpha: Int,
    val items: List<OverlayItem>,
    /** null = bez nakładki debug. */
    val debug: DebugOverlay?,
) {
    companion object {
        val EMPTY: OverlaySnapshot = OverlaySnapshot(255, emptyList(), null)
    }
}

/** Dane do tekstu nakładki debug (zebrane przez overlay). */
data class DebugInfo(
    val trackedPointerIds: Collection<Int>,
    val padButtons: Int,
    val latencyLastMs: Long,
    val latencyP95Ms: Long,
    val latencySamples: Int,
    /** null, gdy silnik jeszcze nie istnieje. */
    val policyViolations: Int?,
    val controlsHidden: Boolean,
    val gamepadPresent: Boolean,
)

object OverlaySnapshotBuilder {

    fun build(layout: ControlLayout, state: EngineVisualState, opacity: Float, debug: DebugInfo?): OverlaySnapshot {
        val items: ArrayList<OverlayItem> = ArrayList(layout.controls.size)
        for (c in layout.controls) {
            if (c.kind == LaidOutControl.Kind.STICK) {
                items.add(stickItem(c, state.sticks[c.control.id]))
            } else {
                items.add(ButtonItem(c, state.isPressed(c.control.id)))
            }
        }
        val dbg: DebugOverlay? = if (debug == null) null else debugOverlay(debug, state)
        return OverlaySnapshot(alphaOf(opacity), items, dbg)
    }

    fun alphaOf(opacity: Float): Int {
        val clamped: Float = if (opacity.isNaN() || opacity < 0f) 0f else if (opacity > 1f) 1f else opacity
        return Math.round(clamped * 255f)
    }

    /**
     * Geometria gałki z silnika. Bez stanu silnika (silnik jeszcze nie istnieje): spoczynek w środku strefy,
     * promień połowy krótszego boku (jak gałka stała w silniku).
     */
    fun stickItem(c: LaidOutControl, s: StickVisual?): StickItem {
        if (s == null) {
            val b: PxRect = c.bounds
            val r: Float = minOf(b.width, b.height) * 0.5f
            return StickItem(c, false, b.centerX, b.centerY, r, b.centerX, b.centerY)
        }
        return StickItem(
            c, s.active, s.centerX, s.centerY, s.radius,
            s.centerX + s.deflectionX * s.radius, s.centerY + s.deflectionY * s.radius,
        )
    }

    fun debugOverlay(info: DebugInfo, state: EngineVisualState): DebugOverlay {
        val pointers: ArrayList<DebugPointer> = ArrayList(state.pointers.size)
        for (p in state.pointers) pointers.add(DebugPointer(p.pointerId, p.x, p.y))
        return DebugOverlay(debugLines(info), pointers)
    }

    fun debugLines(info: DebugInfo): List<String> {
        val lines: ArrayList<String> = ArrayList()
        lines.add("ptr " + info.trackedPointerIds.size + " [" + info.trackedPointerIds.joinToString(",") + "]")
        lines.add("pad 0x" + Integer.toHexString(info.padButtons))
        lines.add("lat last " + info.latencyLastMs + " ms, p95 " + info.latencyP95Ms + " ms (n=" + info.latencySamples + ")")
        val v: Int? = info.policyViolations
        if (v != null) lines.add("policy violations $v")
        if (info.controlsHidden) {
            lines.add(if (info.gamepadPresent) "controls hidden (gamepad)" else "controls hidden")
        }
        return lines
    }
}

/**
 * Haptyka z przejść stanu silnika (przed i po jednym `TouchEvent`), zamiast z osobnego hit testu:
 * wibracja jest tam, gdzie silnik naprawdę wcisnął (także w strefie aktywacji i przy slide-to-press).
 */
object TouchFeedback {

    data class Result(val presses: Int, val releases: Int)

    /**
     * Dla każdego palca, którego właściciel się zmienił: nowy właściciel będący przyciskiem = wciśnięcie,
     * w przeciwnym razie utrata przycisku = zwolnienie (slide-to-press daje samo wciśnięcie). Gałki nie wibrują.
     */
    fun between(before: EngineVisualState, after: EngineVisualState): Result {
        var presses = 0
        var releases = 0
        val ids: LinkedHashSet<Int> = LinkedHashSet()
        for (p in before.pointers) ids.add(p.pointerId)
        for (p in after.pointers) ids.add(p.pointerId)
        for (id in ids) {
            val was: String? = before.controlOf(id)
            val now: String? = after.controlOf(id)
            if (was == now) continue
            if (isButton(now, after)) {
                presses++
            } else if (isButton(was, before)) {
                releases++
            }
        }
        return Result(presses, releases)
    }

    private fun isButton(controlId: String?, state: EngineVisualState): Boolean =
        controlId != null && !state.sticks.containsKey(controlId)
}
