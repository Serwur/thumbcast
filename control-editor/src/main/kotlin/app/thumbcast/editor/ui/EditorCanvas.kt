package app.thumbcast.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.thumbcast.android.overlay.ControlLayout
import app.thumbcast.android.overlay.OverlayRenderer
import app.thumbcast.android.overlay.OverlaySnapshot
import app.thumbcast.android.overlay.OverlaySnapshotBuilder
import app.thumbcast.editor.state.EditorState
import app.thumbcast.engine.EngineVisualState
import app.thumbcast.model.Rect
import kotlin.math.hypot

private val CHECKER_DARK = Color(0xFF1B1D21)
private val CHECKER_LIGHT = Color(0xFF23262B)
private val GRID_LINE = Color(0x22FFFFFF)
private val CENTER_LINE = Color(0x44FFFFFF)
private val ZONE_OUTLINE = Color(0x55FFFFFF)
private val SELECTION = Color(0xFF4FC3F7)

/**
 * Płótno edytora: cały ekran = współrzędne profilu 0..1. Kontrolki rysuje ten sam [OverlayRenderer] co w grze
 * (stan silnika pusty, nic nie jest wciśnięte), a edytor dorysowuje strefy dotyku, zaznaczenie i uchwyt.
 *
 * Gesty: dotknięcie kontrolki zaznacza ją, przeciąganie przesuwa, przeciąganie uchwytu w prawym dolnym rogu
 * zmienia rozmiar, dwa palce skalują zaznaczoną kontrolkę, dotknięcie pustego miejsca czyści zaznaczenie.
 */
@Composable
fun EditorCanvas(store: EditorStore, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val renderer: OverlayRenderer = remember(density.density) { OverlayRenderer(density.density) }
    val handleTouchPx: Float = with(density) { HANDLE_TOUCH.toPx() }
    val handleDrawPx: Float = with(density) { HANDLE_DRAW.toPx() }
    val cellPx: Float = with(density) { CHECKER_CELL.toPx() }
    val strokePx: Float = with(density) { 2.dp.toPx() }
    val state: EditorState = store.state

    Canvas(modifier.pointerInput(store, handleTouchPx) { editorGestures(store, handleTouchPx) }) {
        drawChecker(cellPx)
        if (state.snapToGrid) drawGrid()
        val w: Int = size.width.toInt()
        val h: Int = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        val layout: ControlLayout = ControlLayout.of(state.profile, w, h)
        val snapshot: OverlaySnapshot = OverlaySnapshotBuilder.build(
            layout, EngineVisualState.EMPTY, state.profile.global.opacity, null,
        )
        // Strefa dotyku (rect) bywa większa od grafiki: zaznaczamy ją przerywaną linią.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f * strokePx, 3f * strokePx))
        for (c in layout.controls) {
            val b = c.bounds
            drawRect(ZONE_OUTLINE, Offset(b.left, b.top), Size(b.width, b.height), style = Stroke(strokePx / 2f, pathEffect = dash))
        }
        drawIntoCanvas { renderer.draw(it.nativeCanvas, snapshot, w, h) }
        val sel = state.selected
        if (sel != null) {
            val r: Rect = sel.rect
            val left = r.x * size.width
            val top = r.y * size.height
            val right = (r.x + r.w) * size.width
            val bottom = (r.y + r.h) * size.height
            drawRect(SELECTION, Offset(left, top), Size(right - left, bottom - top), style = Stroke(strokePx))
            drawCircle(SELECTION, handleDrawPx, Offset(right, bottom))
            drawCircle(Color.Black, handleDrawPx, Offset(right, bottom), style = Stroke(strokePx / 2f))
        }
    }
}

private val HANDLE_TOUCH = 28.dp
private val HANDLE_DRAW = 9.dp
private val CHECKER_CELL = 24.dp

private fun DrawScope.drawChecker(cell: Float) {
    drawRect(CHECKER_DARK)
    if (cell <= 0f) return
    var row = 0
    var y = 0f
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(CHECKER_LIGHT, Offset(x, y), Size(cell, cell))
            x += cell * 2f
        }
        y += cell
        row++
    }
}

/** Linie co 10% (siatka przyciągania ma krok 1%, gęstsze linie byłyby nieczytelne) i osie środka. */
private fun DrawScope.drawGrid() {
    for (i in 1..9) {
        val color = if (i == 5) CENTER_LINE else GRID_LINE
        val x = size.width * i / 10f
        val y = size.height * i / 10f
        drawLine(color, Offset(x, 0f), Offset(x, size.height))
        drawLine(color, Offset(0f, y), Offset(size.width, y))
    }
}

private fun onHandle(r: Rect, p: Offset, w: Float, h: Float, touchPx: Float): Boolean =
    hypot(p.x - (r.x + r.w) * w, p.y - (r.y + r.h) * h) <= touchPx

private suspend fun PointerInputScope.editorGestures(store: EditorStore, handleTouchPx: Float) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w: Float = size.width.toFloat()
        val h: Float = size.height.toFloat()
        if (w <= 0f || h <= 0f) return@awaitEachGesture
        val start: EditorState = store.state
        val sel = start.selected
        val resize: Boolean = sel != null && onHandle(sel.rect, down.position, w, h, handleTouchPx)
        val targetId: String? = if (resize) {
            sel.id
        } else {
            ControlLayout.of(start.profile, size.width, size.height).hitTest(down.position.x, down.position.y)?.control?.id
        }
        if (targetId != null) store.update { it.select(targetId) }
        store.update { it.beginGesture() }
        down.consume()

        val slop: Float = viewConfiguration.touchSlop
        var moved = false
        var pinching = false
        var scale = 1f
        while (true) {
            val event = awaitPointerEvent()
            val pressedCount: Int = event.changes.count { it.pressed }
            if (pressedCount == 0) break
            val selectedId: String? = store.state.selectedId
            if (pressedCount >= 2) {
                if (selectedId != null) {
                    if (!pinching) {
                        pinching = true
                        scale = 1f
                        store.update { it.rebaseGesture() }
                    }
                    scale *= event.calculateZoom()
                    store.update { it.pinchScale(selectedId, scale) }
                }
            } else if (!pinching && targetId != null) {
                val c = event.changes.firstOrNull { it.id == down.id && it.pressed }
                if (c != null) {
                    val dx: Float = c.position.x - down.position.x
                    val dy: Float = c.position.y - down.position.y
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        store.update {
                            if (resize) it.dragResize(targetId, dx / w, dy / h) else it.dragMove(targetId, dx / w, dy / h)
                        }
                    }
                }
            }
            for (c in event.changes) if (c.positionChanged()) c.consume()
        }
        if (targetId == null && !pinching) store.update { it.select(null) }
        store.update { it.endGesture() }
    }
}
