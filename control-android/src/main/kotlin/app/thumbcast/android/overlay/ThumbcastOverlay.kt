package app.thumbcast.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import app.thumbcast.android.sink.CompositeInputSink
import app.thumbcast.engine.InputEngine
import app.thumbcast.engine.TouchAction
import app.thumbcast.engine.TouchEvent
import app.thumbcast.engine.TouchPointer
import app.thumbcast.engine.Viewport
import app.thumbcast.model.Profile

/**
 * Jeden `View` nad strumieniem: rysuje wszystkie kontrolki profilu na Canvas i przekazuje dotyk
 * do silnika (`InputEngine`). Nie zawiera żadnej logiki wyjścia: o tym, co i kiedy trafia do hosta,
 * decyduje silnik (ActionPolicy). Stan rysowania (wciśnięcia, pozycja gałki) jest tylko wizualny.
 *
 * Cykl życia:
 * - rozmiar znany (`onSizeChanged`) -> tworzymy silnik i liczymy `Viewport`;
 * - `ACTION_CANCEL`, `onWindowFocusChanged(false)`, `onDetachedFromWindow` -> `releaseAll()`;
 * - podłączenie pada BT (InputDeviceListener) -> ukrycie kontrolek (gesty zostają), jeśli profil
 *   ma `global.autoHideOnGamepad`.
 *
 * Wszystkie metody wołać na wątku UI.
 *
 * Widok tworzony tylko z kodu (fabryka), nie z XML, stąd brak konstruktorów (Context, AttributeSet).
 */
@SuppressLint("ViewConstructor")
class ThumbcastOverlay(
    context: Context,
    initialProfile: Profile,
    private val sink: CompositeInputSink,
    private val engineFactory: EngineFactory,
    private val tickHandler: TickHandler?,
) : View(context) {

    /** Tworzenie silnika; wydzielone, żeby test instrumentacyjny mógł wstrzyknąć silnik z fałszywym zegarem. */
    fun interface EngineFactory {
        fun create(profile: Profile, sink: CompositeInputSink, viewport: Viewport): InputEngine
    }

    /**
     * Impuls czasu dla silnika (rozpoznanie long-press bez ruchu palca, wygaszanie kamery), wołany
     * raz na klatkę (`postOnAnimation`) WYŁĄCZNIE, gdy jakiś palec dotyka ekranu. Silnik (ActionPolicy R7)
     * dopuszcza w takim cyklu tylko long-press, zwolnienia i wygaszanie wychylenia; nic nowego nie
     * może powstać z samego czasu. null = brak impulsów.
     */
    fun interface TickHandler {
        fun onTick(engine: InputEngine, nowMs: Long)
    }

    private var tickScheduled: Boolean = false
    private val tickRunnable: Runnable = Runnable { runTick() }

    private var baseProfile: Profile = initialProfile
    private var engine: InputEngine? = null
    private var controlLayout: ControlLayout = ControlLayout(emptyList())

    private val translator: MotionTranslator = MotionTranslator(MotionTranslator.MAX_POINTERS)
    private val motionSource: MotionEventSource = MotionEventSource()
    private val haptics: OverlayHaptics = OverlayHaptics(context)
    private val latency: LatencyStats = LatencyStats(256)

    // Wideo: jawny prostokąt od Game albo wyliczony z rozdzielczości strumienia.
    private var explicitVideoRect: VideoRect? = null
    private var streamWidth: Int = 0
    private var streamHeight: Int = 0
    private var refWidthOverride: Int = 0
    private var refHeightOverride: Int = 0

    private var controlsHiddenByUser: Boolean = false
    private var gamepadConnected: Boolean = false

    /** Tryb debug: nakładka z aktywnymi wskaźnikami, ostatnimi flagami pada i opóźnieniem. */
    var debugEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // --- stan wizualny wskaźników (tylko rysowanie i haptyka) ---
    private class VisualPointer(val id: Int, val owner: LaidOutControl?, val downX: Float, val downY: Float) {
        var x: Float = downX
        var y: Float = downY
    }

    private val visualPointers: HashMap<Int, VisualPointer> = HashMap()

    /** Stan „zatrzaśnięty” modyfikatorów LATCH: przełączany przy każdym DOWN (wizualnie, jak silnik). */
    private val latchedIds: HashSet<String> = HashSet()

    // Czas bieżącego zdarzenia w trakcie dispatchu (do pomiaru opóźnienia event -> send).
    private var dispatchEventTime: Long = -1L

    private val density: Float = context.resources.displayMetrics.density

    // --- farby ---
    private val fillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
    }
    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val debugPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 12f * context.resources.displayMetrics.density
        textAlign = Paint.Align.LEFT
    }
    private val debugBgPaint: Paint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val debugPointerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 64, 64)
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
    }

    private val inputDeviceListener: InputManager.InputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshGamepadState()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshGamepadState()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refreshGamepadState()
        }
    }

    init {
        isFocusable = false
        isClickable = false
        // Overlay tylko rysuje; nie chcemy domyślnego tła ani dźwięków kliknięć.
        isSoundEffectsEnabled = false
        setWillNotDraw(false)
        haptics.setConfig(initialProfile.global.haptics)
        sink.listener = object : CompositeInputSink.SendListener {
            override fun onSent(kind: String, detail: String) {
                if (dispatchEventTime >= 0L) {
                    latency.add(SystemClock.uptimeMillis() - dispatchEventTime)
                }
                if (debugEnabled) {
                    invalidate()
                }
            }
        }
    }

    // =====================================================================================
    // API publiczne (używane przez Game przez fabrykę)
    // =====================================================================================

    /** Zmiana profilu w trakcie sesji (menu w grze). Najpierw zwalnia wszystko. */
    fun setProfile(profile: Profile) {
        releaseAll()
        baseProfile = profile
        latchedIds.clear()
        haptics.setConfig(profile.global.haptics)
        applyEffectiveProfile()
    }

    val profile: Profile
        get() = baseProfile

    /**
     * Prostokąt wideo (po letterboxie) w układzie współrzędnych TEGO widoku.
     * `refWidth/refHeight` to rozdzielczość odniesienia dla `sendMousePosition`; 0 = rozmiar prostokąta
     * (tak robi upstream dla dotyku absolutnego: podaje wymiary widoku wideo).
     */
    fun setVideoRect(left: Int, top: Int, right: Int, bottom: Int, refWidth: Int, refHeight: Int) {
        explicitVideoRect = VideoRect(left, top, right, bottom)
        refWidthOverride = refWidth
        refHeightOverride = refHeight
        updateViewport()
    }

    /** Wariant bez rozdzielczości odniesienia (= rozmiar prostokąta). */
    fun setVideoRect(left: Int, top: Int, right: Int, bottom: Int) {
        setVideoRect(left, top, right, bottom, 0, 0)
    }

    /**
     * Gdy Game zna tylko rozdzielczość strumienia: prostokąt liczymy jako wycentrowany
     * z zachowaniem proporcji (`ViewportMath.fitCentered`). Jawny [setVideoRect] ma pierwszeństwo.
     */
    fun setStreamResolution(streamW: Int, streamH: Int) {
        streamWidth = streamW
        streamHeight = streamH
        updateViewport()
    }

    /** Ręczne ukrycie kontrolek (akcja "toggle_overlay"). Gesty i kontrolki systemowe zostają. */
    fun setControlsHidden(hidden: Boolean) {
        if (controlsHiddenByUser == hidden) return
        controlsHiddenByUser = hidden
        applyEffectiveProfile()
    }

    fun toggleControlsHidden() {
        setControlsHidden(!controlsHiddenByUser)
    }

    val controlsHidden: Boolean
        get() = controlsHiddenByUser || (gamepadConnected && baseProfile.global.autoHideOnGamepad)

    /** Zwolnienie wszystkich wejść: zerowy stan pada, puszczone klawisze i przyciski myszy. */
    fun releaseAll() {
        val e: InputEngine? = engine
        if (e != null) {
            e.releaseAll()
        }
        // Bezpiecznik po stronie adapterów (silnik powinien już wszystko zwolnić).
        sink.releaseAll()
        translator.reset()
        visualPointers.clear()
        removeCallbacks(tickRunnable)
        tickScheduled = false
        invalidate()
    }

    // =====================================================================================
    // Dotyk
    // =====================================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action: Int = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN && !MotionActions.isFingerLike(event.getToolType(0))) {
            // Mysz / rysik: oddajemy gest niżej (backgroundTouchView upstreamu, gałąź pointer w Game).
            return false
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // Pełna częstotliwość próbkowania ekranu, bez łączenia zdarzeń do ramki (plan 2.2/5.2).
            requestUnbufferedDispatch(event)
        }
        val e: InputEngine? = engine
        if (e == null) {
            // Rozmiar nieznany: silnik jeszcze nie istnieje. Konsumujemy, żeby nic nie trafiło do upstreamu.
            return true
        }
        motionSource.event = event
        val events: List<TouchEvent> = translator.translate(motionSource)
        motionSource.event = null

        dispatchEventTime = event.eventTime
        try {
            for (te in events) {
                updateVisual(te)
                e.onTouch(te)
            }
        } finally {
            dispatchEventTime = -1L
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll()
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick()
        }
        if (events.isNotEmpty()) {
            invalidate()
        }
        scheduleTickIfNeeded()
        return true
    }

    private fun scheduleTickIfNeeded() {
        if (tickHandler == null || tickScheduled) return
        if (translator.trackedPointerIds.isEmpty()) return
        tickScheduled = true
        postOnAnimation(tickRunnable)
    }

    private fun runTick() {
        tickScheduled = false
        val e: InputEngine? = engine
        val h: TickHandler? = tickHandler
        if (e == null || h == null || translator.trackedPointerIds.isEmpty()) return
        h.onTick(e, SystemClock.uptimeMillis())
        if (debugEnabled) invalidate()
        scheduleTickIfNeeded()
    }

    override fun performClick(): Boolean {
        // Overlay nie ma akcji kliknięcia; nadpisane dla dostępności (lint ClickableViewAccessibility).
        return super.performClick()
    }

    private fun updateVisual(te: TouchEvent) {
        when (te.action) {
            TouchAction.DOWN -> {
                val p: TouchPointer? = findPointer(te.pointers, te.actionPointerId)
                if (p != null) {
                    val owner: LaidOutControl? = controlLayout.hitTest(p.x, p.y)
                    visualPointers[p.id] = VisualPointer(p.id, owner, p.x, p.y)
                    if (owner != null) {
                        if (owner.latch) {
                            val id: String = owner.control.id
                            if (!latchedIds.remove(id)) latchedIds.add(id)
                        }
                        if (owner.kind != LaidOutControl.Kind.STICK) {
                            haptics.onPress()
                        }
                    }
                }
            }
            TouchAction.MOVE -> {
                for (p in te.pointers) {
                    val vp: VisualPointer? = visualPointers[p.id]
                    if (vp != null) {
                        vp.x = p.x
                        vp.y = p.y
                    }
                }
            }
            TouchAction.UP -> {
                val vp: VisualPointer? = visualPointers.remove(te.actionPointerId)
                val owner: LaidOutControl? = vp?.owner
                if (owner != null && owner.kind != LaidOutControl.Kind.STICK) {
                    haptics.onRelease()
                }
            }
            TouchAction.CANCEL -> visualPointers.clear()
        }
    }

    private fun findPointer(pointers: List<TouchPointer>, id: Int): TouchPointer? {
        for (p in pointers) {
            if (p.id == id) return p
        }
        return null
    }

    // =====================================================================================
    // Cykl życia
    // =====================================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (engine == null) {
            engine = engineFactory.create(effectiveProfile(), sink, computeViewport())
        }
        relayout()
        updateViewport()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateGestureExclusion()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        haptics.refreshSystemSetting()
        val im: InputManager? = context.getSystemService(InputManager::class.java)
        if (im != null) {
            im.registerInputDeviceListener(inputDeviceListener, null)
        }
        refreshGamepadState()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        val im: InputManager? = context.getSystemService(InputManager::class.java)
        if (im != null) {
            im.unregisterInputDeviceListener(inputDeviceListener)
        }
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            releaseAll()
        } else {
            haptics.refreshSystemSetting()
        }
    }

    // =====================================================================================
    // Profil efektywny, układ, viewport, wykluczenia gestów
    // =====================================================================================

    private fun effectiveProfile(): Profile =
        if (controlsHidden) ControlLayout.withControlsHidden(baseProfile) else baseProfile

    private fun applyEffectiveProfile() {
        releaseAll()
        val p: Profile = effectiveProfile()
        val e: InputEngine? = engine
        if (e != null) {
            e.setProfile(p)
        }
        relayout()
    }

    private fun relayout() {
        controlLayout = ControlLayout.of(effectiveProfile(), width, height)
        updateGestureExclusion()
        invalidate()
    }

    private fun currentVideoRect(): VideoRect {
        val explicit: VideoRect? = explicitVideoRect
        if (explicit != null) return explicit
        if (streamWidth > 0 && streamHeight > 0) {
            return ViewportMath.fitCentered(width, height, streamWidth, streamHeight)
        }
        return VideoRect(0, 0, width, height)
    }

    private fun computeViewport(): Viewport {
        val video: VideoRect = currentVideoRect()
        val refW: Int = if (refWidthOverride > 0) refWidthOverride else maxOf(video.width, 1)
        val refH: Int = if (refHeightOverride > 0) refHeightOverride else maxOf(video.height, 1)
        return EngineBridge.viewport(width, height, video, refW, refH)
    }

    private fun updateViewport() {
        val e: InputEngine? = engine
        if (e == null || width <= 0 || height <= 0) return
        e.setViewport(computeViewport())
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val zones: ArrayList<PxRect> = ArrayList()
        for (c in controlLayout.controls) {
            if (c.kind == LaidOutControl.Kind.STICK) zones.add(c.bounds)
        }
        val clipped: List<PxRect> = GestureExclusion.compute(zones, width.toFloat(), density, EDGE_BAND_DP * density)
        val rects: ArrayList<Rect> = ArrayList(clipped.size)
        for (r in clipped) {
            rects.add(Rect(r.left.toInt(), r.top.toInt(), Math.ceil(r.right.toDouble()).toInt(), Math.ceil(r.bottom.toDouble()).toInt()))
        }
        setSystemGestureExclusionRects(rects)
    }

    // =====================================================================================
    // Pad BT
    // =====================================================================================

    private fun refreshGamepadState() {
        val now: Boolean = isPhysicalGamepadConnected()
        if (now == gamepadConnected) return
        gamepadConnected = now
        if (baseProfile.global.autoHideOnGamepad) {
            applyEffectiveProfile()
        }
    }

    private fun isPhysicalGamepadConnected(): Boolean {
        val ids: IntArray = InputDevice.getDeviceIds()
        for (id in ids) {
            val dev: InputDevice = InputDevice.getDevice(id) ?: continue
            if (dev.isVirtual) continue
            val sources: Int = dev.sources
            val isPad: Boolean = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isPad) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !dev.isExternal) continue
            return true
        }
        return false
    }

    // =====================================================================================
    // Rysowanie
    // =====================================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val alpha: Int = opacityAlpha()
        for (c in controlLayout.controls) {
            when (c.kind) {
                LaidOutControl.Kind.STICK -> drawStick(canvas, c, alpha)
                else -> drawButton(canvas, c, alpha)
            }
        }
        if (debugEnabled) {
            drawDebug(canvas)
        }
    }

    private fun opacityAlpha(): Int {
        val o: Float = baseProfile.global.opacity
        val clamped: Float = if (o < 0f) 0f else if (o > 1f) 1f else o
        return Math.round(clamped * 255f)
    }

    private fun pointerOwnedBy(c: LaidOutControl): VisualPointer? {
        for (vp in visualPointers.values) {
            // Porównanie po id, bo po relayout (zmiana rozmiaru) obiekty LaidOutControl są nowe.
            val owner: LaidOutControl? = vp.owner
            if (owner != null && owner.control.id == c.control.id) return vp
        }
        return null
    }

    private fun drawStick(canvas: Canvas, c: LaidOutControl, alpha: Int) {
        val b: PxRect = c.bounds
        val radius: Float = minOf(b.width, b.height) * 0.5f * STICK_BASE_FRACTION
        val knobRadius: Float = radius * 0.45f
        val vp: VisualPointer? = pointerOwnedBy(c)
        val cx: Float
        val cy: Float
        if (vp != null && c.floating) {
            cx = vp.downX
            cy = vp.downY
        } else {
            cx = b.centerX
            cy = b.centerY
        }
        if (c.floating && vp == null) {
            // Pływający w spoczynku: tylko delikatny zarys strefy i podpowiedź w środku.
            strokePaint.color = Color.WHITE
            strokePaint.alpha = alpha / 3
            canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, 12f * density, 12f * density, strokePaint)
        }
        fillPaint.color = Color.BLACK
        fillPaint.alpha = alpha / 2
        canvas.drawCircle(cx, cy, radius, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, strokePaint)

        var kx: Float = cx
        var ky: Float = cy
        if (vp != null) {
            val dx: Float = vp.x - cx
            val dy: Float = vp.y - cy
            val dist: Float = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist > radius && dist > 0f) {
                kx = cx + dx / dist * radius
                ky = cy + dy / dist * radius
            } else {
                kx = vp.x
                ky = vp.y
            }
        }
        fillPaint.color = Color.WHITE
        fillPaint.alpha = if (vp != null) alpha else alpha * 2 / 3
        canvas.drawCircle(kx, ky, knobRadius, fillPaint)
        drawLabel(canvas, c.text, cx, cy + radius + textSizeFor(c) * 1.1f, c, alpha)
    }

    private fun drawButton(canvas: Canvas, c: LaidOutControl, alpha: Int) {
        val b: PxRect = c.bounds
        val pressed: Boolean = pointerOwnedBy(c) != null || latchedIds.contains(c.control.id)
        fillPaint.color = if (pressed) Color.WHITE else Color.BLACK
        fillPaint.alpha = if (pressed) alpha * 3 / 4 else alpha / 2
        strokePaint.color = Color.WHITE
        strokePaint.alpha = alpha
        when (c.shape) {
            app.thumbcast.model.Shape.CIRCLE -> {
                val r: Float = minOf(b.width, b.height) * 0.5f
                canvas.drawCircle(b.centerX, b.centerY, r, fillPaint)
                canvas.drawCircle(b.centerX, b.centerY, r, strokePaint)
            }
            app.thumbcast.model.Shape.ROUNDED_RECT -> {
                val r: Float = minOf(b.width, b.height) * 0.25f
                canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, fillPaint)
                canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, strokePaint)
            }
            app.thumbcast.model.Shape.RECT -> {
                canvas.drawRect(b.left, b.top, b.right, b.bottom, fillPaint)
                canvas.drawRect(b.left, b.top, b.right, b.bottom, strokePaint)
            }
        }
        val ts: Float = textSizeFor(c)
        drawLabel(canvas, c.text, b.centerX, b.centerY + ts * 0.35f, c, alpha)
    }

    private fun textSizeFor(c: LaidOutControl): Float {
        val base: Float = minOf(c.bounds.width, c.bounds.height) * 0.32f
        val maxPx: Float = 18f * density
        val minPx: Float = 9f * density
        return if (base > maxPx) maxPx else if (base < minPx) minPx else base
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, c: LaidOutControl, alpha: Int) {
        if (text.isEmpty()) return
        textPaint.textSize = textSizeFor(c)
        textPaint.alpha = alpha
        canvas.drawText(text, x, y, textPaint)
    }

    private fun drawDebug(canvas: Canvas) {
        val lineH: Float = debugPaint.textSize * 1.3f
        val lines: ArrayList<String> = ArrayList()
        val ids: StringBuilder = StringBuilder()
        for (id in translator.trackedPointerIds) {
            if (ids.isNotEmpty()) ids.append(',')
            ids.append(id)
        }
        lines.add("ptr " + translator.trackedPointerIds.size + " [" + ids + "]")
        lines.add("pad 0x" + Integer.toHexString(sink.lastPadButtons))
        lines.add("lat last " + latency.last + " ms, p95 " + latency.percentile(95) + " ms (n=" + latency.size + ")")
        val e: InputEngine? = engine
        if (e != null) {
            lines.add("policy violations " + e.violations.size)
        }
        if (controlsHidden) {
            lines.add(if (gamepadConnected) "controls hidden (gamepad)" else "controls hidden")
        }
        var maxW: Float = 0f
        for (l in lines) {
            val w: Float = debugPaint.measureText(l)
            if (w > maxW) maxW = w
        }
        val pad: Float = 6f * density
        val top: Float = height * 0.15f
        val left: Float = width * 0.30f
        canvas.drawRect(left, top, left + maxW + pad * 2f, top + lineH * lines.size + pad * 2f, debugBgPaint)
        var y: Float = top + pad + debugPaint.textSize
        for (l in lines) {
            canvas.drawText(l, left + pad, y, debugPaint)
            y += lineH
        }
        for (vp in visualPointers.values) {
            canvas.drawCircle(vp.x, vp.y, 28f * density, debugPointerPaint)
            canvas.drawText(vp.id.toString(), vp.x + 30f * density, vp.y, debugPaint)
        }
    }

    companion object {
        /** Szerokość pasa przy krawędzi, w którym działa systemowy gest „wstecz”. */
        private const val EDGE_BAND_DP: Float = 32f

        /** Promień podstawy gałki jako ułamek połowy mniejszego wymiaru strefy. */
        private const val STICK_BASE_FRACTION: Float = 0.55f
    }
}
