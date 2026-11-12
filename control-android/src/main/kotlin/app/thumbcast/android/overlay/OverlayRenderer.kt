package app.thumbcast.android.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.thumbcast.model.Shape
import app.thumbcast.model.SystemControl

/**
 * Rysowanie kontrolek na Canvas z [OverlaySnapshot]. Bez stanu poza farbami: nie zna silnika, profilu
 * ani dotyku, więc może go użyć każdy widok (overlay strumienia, edytor układu, podgląd presetu).
 *
 * @param density `DisplayMetrics.density` (grubość linii, rozmiary tekstu w dp).
 */
class OverlayRenderer(private val density: Float) {

    private val fillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val debugPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 12f * density
        textAlign = Paint.Align.LEFT
    }
    private val debugBgPaint: Paint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }
    /** Przyciski systemowe (G15): pigułka i ikona rysowana liniami, nie glif czcionki. */
    private val systemFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val systemStrokePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val iconPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val iconFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val iconPath: Path = Path()
    private val pillRect: RectF = RectF()
    private val debugPointerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 64, 64)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    /** Rysuje migawkę na widoku [width] x [height] (wymiary potrzebne tylko nakładce debug). */
    fun draw(canvas: Canvas, snapshot: OverlaySnapshot, width: Int, height: Int) {
        val alpha: Int = snapshot.alpha
        for (item in snapshot.items) {
            when (item) {
                is StickItem -> drawStick(canvas, item, alpha)
                is ButtonItem -> drawButton(canvas, item, alpha)
            }
        }
        val dbg: DebugOverlay? = snapshot.debug
        if (dbg != null) drawDebug(canvas, dbg, width, height)
    }

    private fun drawStick(canvas: Canvas, s: StickItem, alpha: Int) {
        val c: LaidOutControl = s.control
        val b: PxRect = c.bounds
        if (c.floating && !s.active) {
            // Pływająca w spoczynku: delikatny zarys strefy (podstawa pojawi się tam, gdzie wyląduje kciuk).
            strokePaint.color = Color.WHITE
            strokePaint.alpha = alpha / 3
            canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, 12f * density, 12f * density, strokePaint)
        }
        fillPaint.color = Color.BLACK
        fillPaint.alpha = alpha / 2
        canvas.drawCircle(s.centerX, s.centerY, s.radius, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.alpha = alpha
        canvas.drawCircle(s.centerX, s.centerY, s.radius, strokePaint)

        fillPaint.color = Color.WHITE
        fillPaint.alpha = if (s.active) alpha else alpha * 2 / 3
        canvas.drawCircle(s.knobX, s.knobY, s.radius * KNOB_FRACTION, fillPaint)
        val ts: Float = textSizeFor(b, density)
        drawLabel(canvas, c.text, s.centerX, s.centerY + s.radius + ts * 1.1f, ts, alpha)
    }

    private fun drawButton(canvas: Canvas, item: ButtonItem, alpha: Int) {
        val c: LaidOutControl = item.control
        if (c.kind == LaidOutControl.Kind.SYSTEM && systemIconOf(c) != null) {
            drawSystem(canvas, item, alpha)
            return
        }
        val b: PxRect = c.bounds
        val pressed: Boolean = item.pressed
        fillPaint.color = if (pressed) Color.WHITE else Color.BLACK
        fillPaint.alpha = if (pressed) alpha * 3 / 4 else alpha / 2
        strokePaint.color = Color.WHITE
        strokePaint.alpha = alpha
        when (c.shape) {
            Shape.CIRCLE -> {
                val r: Float = minOf(b.width, b.height) * 0.5f
                canvas.drawCircle(b.centerX, b.centerY, r, fillPaint)
                canvas.drawCircle(b.centerX, b.centerY, r, strokePaint)
            }
            Shape.ROUNDED_RECT -> {
                val r: Float = minOf(b.width, b.height) * 0.25f
                canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, fillPaint)
                canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, strokePaint)
            }
            Shape.RECT -> {
                canvas.drawRect(b.left, b.top, b.right, b.bottom, fillPaint)
                canvas.drawRect(b.left, b.top, b.right, b.bottom, strokePaint)
            }
        }
        val ts: Float = textSizeFor(b, density)
        drawLabel(canvas, c.text, b.centerX, b.centerY + ts * 0.35f, ts, alpha)
    }

    /**
     * Przycisk systemowy (menu, klawiatura, ukrycie przycisków): pigułka mniejsza od obszaru dotyku
     * (`SystemTouchTargets` daje min. 48 dp) i ikona z linii w środku. Kolory jak paleta menu w grze
     * (`thumbcast_menu_surface`, `thumbcast_accent` po wciśnięciu).
     */
    private fun drawSystem(canvas: Canvas, item: ButtonItem, alpha: Int) {
        val b: PxRect = item.control.bounds
        val inset: Float = (minOf(b.width, b.height) * 0.12f).coerceIn(2f * density, 6f * density)
        pillRect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val radius: Float = minOf(pillRect.width(), pillRect.height()) / 2f
        if (item.pressed) {
            systemFillPaint.color = SYSTEM_PRESSED_COLOR
            systemFillPaint.alpha = alpha
        } else {
            systemFillPaint.color = SYSTEM_FILL_COLOR
            systemFillPaint.alpha = alpha * 4 / 5
        }
        canvas.drawRoundRect(pillRect, radius, radius, systemFillPaint)
        systemStrokePaint.color = Color.WHITE
        systemStrokePaint.alpha = alpha * 3 / 10
        canvas.drawRoundRect(pillRect, radius, radius, systemStrokePaint)
        val size: Float = (minOf(pillRect.width(), pillRect.height()) * 0.55f).coerceIn(14f * density, 24f * density)
        drawSystemIcon(canvas, systemIconOf(item.control) ?: return, pillRect.centerX(), pillRect.centerY(), size, alpha)
    }

    /** Ikona [icon] o boku [s] w środku ([cx], [cy]), linie zaokrąglone; bez czcionek i zasobów. */
    private fun drawSystemIcon(canvas: Canvas, icon: SystemIcon, cx: Float, cy: Float, s: Float, alpha: Int) {
        iconPaint.alpha = alpha
        iconFillPaint.alpha = alpha
        iconPaint.strokeWidth = maxOf(1.5f * density, s * 0.1f)
        val h: Float = s / 2f
        when (icon) {
            SystemIcon.MENU -> {
                // Trzy poziome kreski.
                val x0: Float = cx - h * 0.8f
                val x1: Float = cx + h * 0.8f
                for (k in -1..1) {
                    val y: Float = cy + k * h * 0.6f
                    canvas.drawLine(x0, y, x1, y, iconPaint)
                }
            }
            SystemIcon.KEYBOARD -> {
                // Obrys klawiatury, dwa rzędy klawiszy (kropki) i spacja.
                iconPaint.strokeWidth = maxOf(1.2f * density, s * 0.08f)
                val left: Float = cx - h
                val right: Float = cx + h
                val top: Float = cy - h * 0.7f
                val bottom: Float = cy + h * 0.7f
                canvas.drawRoundRect(left, top, right, bottom, s * 0.12f, s * 0.12f, iconPaint)
                val dot: Float = maxOf(1f * density, s * 0.055f)
                val stepX: Float = (right - left) / 5f
                for (row in 0..1) {
                    val y: Float = top + (bottom - top) * (0.3f + row * 0.22f)
                    for (col in 1..4) canvas.drawCircle(left + stepX * col, y, dot, iconFillPaint)
                }
                val spaceY: Float = top + (bottom - top) * 0.76f
                canvas.drawLine(cx - h * 0.45f, spaceY, cx + h * 0.45f, spaceY, iconPaint)
            }
            SystemIcon.VISIBILITY -> {
                // Oko: dwa łuki i źrenica.
                iconPath.reset()
                iconPath.moveTo(cx - h, cy)
                iconPath.quadTo(cx, cy - h * 1.25f, cx + h, cy)
                iconPath.quadTo(cx, cy + h * 1.25f, cx - h, cy)
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
                canvas.drawCircle(cx, cy, h * 0.3f, iconFillPaint)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, size: Float, alpha: Int) {
        if (text.isEmpty()) return
        textPaint.textSize = size
        textPaint.alpha = alpha
        canvas.drawText(text, x, y, textPaint)
    }

    private fun drawDebug(canvas: Canvas, dbg: DebugOverlay, width: Int, height: Int) {
        val lineH: Float = debugPaint.textSize * 1.3f
        var maxW = 0f
        for (l in dbg.lines) {
            val w: Float = debugPaint.measureText(l)
            if (w > maxW) maxW = w
        }
        val pad: Float = 6f * density
        val top: Float = height * 0.15f
        val left: Float = width * 0.30f
        canvas.drawRect(left, top, left + maxW + pad * 2f, top + lineH * dbg.lines.size + pad * 2f, debugBgPaint)
        var y: Float = top + pad + debugPaint.textSize
        for (l in dbg.lines) {
            canvas.drawText(l, left + pad, y, debugPaint)
            y += lineH
        }
        for (p in dbg.pointers) {
            canvas.drawCircle(p.x, p.y, 28f * density, debugPointerPaint)
            canvas.drawText(p.id.toString(), p.x + 30f * density, p.y, debugPaint)
        }
    }

    /** Ikony przycisków systemowych rysowane liniami. */
    enum class SystemIcon { MENU, KEYBOARD, VISIBILITY }

    companion object {
        /** Wypełnienie pigułki przycisku systemowego (`thumbcast_menu_surface` bez krycia) i po wciśnięciu (akcent). */
        private const val SYSTEM_FILL_COLOR: Int = 0xFF181B22.toInt()
        private const val SYSTEM_PRESSED_COLOR: Int = 0xFF5B8CFF.toInt()

        /** Ikona dla akcji aplikacji kontrolki systemowej; null = nieznana akcja (rysowana jak zwykły przycisk z etykietą). */
        fun systemIconOf(c: LaidOutControl): SystemIcon? {
            val sc: SystemControl = c.control as? SystemControl ?: return null
            return when (sc.output.action) {
                "menu" -> SystemIcon.MENU
                "soft_keyboard" -> SystemIcon.KEYBOARD
                "toggle_overlay" -> SystemIcon.VISIBILITY
                else -> null
            }
        }

        /** Promień gałki jako ułamek promienia podstawy (podstawa = promień silnika, pełne wychylenie). */
        const val KNOB_FRACTION: Float = 0.4f

        /** Rozmiar etykiety: 32% krótszego boku, w granicach 9..18 dp. */
        fun textSizeFor(bounds: PxRect, density: Float): Float {
            val base: Float = minOf(bounds.width, bounds.height) * 0.32f
            val maxPx: Float = 18f * density
            val minPx: Float = 9f * density
            return if (base > maxPx) maxPx else if (base < minPx) minPx else base
        }
    }
}
