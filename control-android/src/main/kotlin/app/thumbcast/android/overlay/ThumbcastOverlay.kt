package app.thumbcast.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.thumbcast.android.sink.CompositeInputSink
import app.thumbcast.engine.EngineVisualState
import app.thumbcast.engine.InputEngine
import app.thumbcast.engine.TouchAction
import app.thumbcast.engine.TouchEvent
import app.thumbcast.engine.Viewport
import app.thumbcast.model.Profile

/**
 * Jeden `View` nad strumieniem: klej między Androidem a silnikiem. Przekazuje dotyk do `InputEngine`,
 * planuje impuls czasu, trzyma cykl życia i wykluczenia gestów. O wyjściu decyduje silnik (ActionPolicy),
 * o wyglądzie też: rysowanie idzie z `InputEngine.visualState()` przez [OverlaySnapshotBuilder] do
 * [OverlayRenderer]. Geometria wideo: [VideoGeometry]; profil efektywny: [EffectiveProfile];
 * pad fizyczny: [GamepadPresenceMonitor].
 *
 * Cykl życia:
 * - rozmiar znany (`onSizeChanged`) -> tworzymy silnik i liczymy `Viewport`;
 * - `ACTION_CANCEL`, `onWindowFocusChanged(false)`, `onDetachedFromWindow` -> `releaseAll()`;
 * - podłączenie pada BT -> ukrycie kontrolek (gesty zostają), jeśli profil ma `global.autoHideOnGamepad`.
 *
 * Wszystkie metody wołać na wątku UI. Widok tworzony tylko z kodu (fabryka), nie z XML.
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

    private var baseProfile: Profile = initialProfile
    private var profileTransforms: List<ProfileStep> = emptyList()
    private var engine: InputEngine? = null
    private var controlLayout: ControlLayout = ControlLayout(emptyList())
    private var video: VideoGeometry = VideoGeometry()
    private var controlsHiddenByUser: Boolean = false

    private val translator: MotionTranslator = MotionTranslator(MotionTranslator.MAX_POINTERS)
    private val motionSource: MotionEventSource = MotionEventSource()
    private val haptics: OverlayHaptics = OverlayHaptics(context)
    private val latency: LatencyStats = LatencyStats(256)
    private val density: Float = context.resources.displayMetrics.density
    private val renderer: OverlayRenderer = OverlayRenderer(density)
    private val gamepads: GamepadPresenceMonitor = GamepadPresenceMonitor(context) { _ ->
        if (baseProfile.global.autoHideOnGamepad) applyEffectiveProfile()
    }
    private val ticker: FrameTicker = FrameTicker(
        this,
        { tickHandler != null && engine != null && translator.trackedPointerIds.isNotEmpty() },
    ) {
        val e: InputEngine? = engine
        if (e != null) tickHandler?.onTick(e, SystemClock.uptimeMillis())
        if (debugEnabled) invalidate()
    }

    // Czas bieżącego zdarzenia w trakcie dispatchu (do pomiaru opóźnienia event -> send).
    private var dispatchEventTime: Long = -1L

    /** Tryb debug: nakładka z aktywnymi wskaźnikami, ostatnimi flagami pada i opóźnieniem. */
    var debugEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
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
                if (dispatchEventTime >= 0L) latency.add(SystemClock.uptimeMillis() - dispatchEventTime)
                if (debugEnabled) invalidate()
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
        haptics.setConfig(profile.global.haptics)
        applyEffectiveProfile()
    }

    val profile: Profile
        get() = baseProfile

    /**
     * Przekształcenia profilu bazowego przed ukryciem kontrolek (np. skalowanie kontrolek), w kolejności.
     * Patrz [EffectiveProfile]. Zwalnia wszystkie wejścia, jak zmiana profilu.
     */
    fun setProfileTransforms(transforms: List<ProfileStep>) {
        profileTransforms = ArrayList(transforms)
        applyEffectiveProfile()
    }

    /**
     * Prostokąt wideo (po letterboxie) w układzie współrzędnych TEGO widoku.
     * `refWidth/refHeight` to rozdzielczość odniesienia dla `sendMousePosition`; 0 = rozmiar prostokąta.
     */
    fun setVideoRect(left: Int, top: Int, right: Int, bottom: Int, refWidth: Int, refHeight: Int) {
        video = video.withVideoRect(VideoRect(left, top, right, bottom), refWidth, refHeight)
        updateViewport()
    }

    /** Wariant bez rozdzielczości odniesienia (= rozmiar prostokąta). */
    fun setVideoRect(left: Int, top: Int, right: Int, bottom: Int) {
        setVideoRect(left, top, right, bottom, 0, 0)
    }

    /** Gdy Game zna tylko rozdzielczość strumienia: wycentrowany letterbox. Jawny [setVideoRect] ma pierwszeństwo. */
    fun setStreamResolution(streamW: Int, streamH: Int) {
        video = video.withStreamResolution(streamW, streamH)
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

    /** Kontrolki ukryte: ręcznie albo przez podłączony pad (`global.autoHideOnGamepad`). */
    val controlsHidden: Boolean
        get() = EffectiveProfile.controlsHidden(baseProfile, controlsHiddenByUser, gamepads.isPresent)

    /** To samo co [controlsHidden] (wygodniejsze z Javy i w warunkach). */
    fun isControlsHidden(): Boolean = controlsHidden

    /**
     * Prostokąty rysowanych teraz kontrolek w px tego widoku (profil efektywny: skala, ukrycie przy padzie).
     * Używa tego nakładka statystyk, żeby nie zasłaniać kontrolek (`stats/StatsPlacement`).
     */
    fun controlBounds(): List<PxRect> = controlLayout.controls.map { it.bounds }

    /** Zwolnienie wszystkich wejść: zerowy stan pada, puszczone klawisze i przyciski myszy. */
    fun releaseAll() {
        engine?.releaseAll()
        // Bezpiecznik po stronie adapterów (silnik powinien już wszystko zwolnić).
        sink.releaseAll()
        translator.reset()
        ticker.cancel()
        invalidate()
    }

    // =====================================================================================
    // Dotyk i impuls czasu
    // =====================================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action: Int = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN && !MotionActions.isFingerLike(event.getToolType(0))) {
            // Mysz / rysik: oddajemy gest niżej (backgroundTouchView upstreamu, gałąź pointer w Game).
            return false
        }
        // Pełna częstotliwość próbkowania ekranu, bez łączenia zdarzeń do ramki (plan 2.2/5.2).
        if (action == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(event)
        // Rozmiar nieznany: silnik jeszcze nie istnieje. Konsumujemy, żeby nic nie trafiło do upstreamu.
        val e: InputEngine = engine ?: return true
        motionSource.event = event
        val events: List<TouchEvent> = translator.translate(motionSource)
        motionSource.event = null

        dispatchEventTime = event.eventTime
        try {
            for (te in events) dispatch(e, te)
        } finally {
            dispatchEventTime = -1L
        }
        if (action == MotionEvent.ACTION_CANCEL) releaseAll()
        if (action == MotionEvent.ACTION_UP) performClick()
        if (events.isNotEmpty()) invalidate()
        ticker.scheduleIfNeeded()
        return true
    }

    /** Jedno zdarzenie do silnika; haptyka z przejścia stanu silnika (bez własnego hit testu). */
    private fun dispatch(e: InputEngine, te: TouchEvent) {
        if (te.action == TouchAction.CANCEL) {
            e.onTouch(te)
            return
        }
        val before: EngineVisualState = e.visualState()
        e.onTouch(te)
        val fb: TouchFeedback.Result = TouchFeedback.between(before, e.visualState())
        repeat(fb.presses) { haptics.onPress() }
        repeat(fb.releases) { haptics.onRelease() }
    }

    override fun performClick(): Boolean {
        // Overlay nie ma akcji kliknięcia; nadpisane dla dostępności (lint ClickableViewAccessibility).
        return super.performClick()
    }

    // =====================================================================================
    // Cykl życia
    // =====================================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (engine == null) engine = engineFactory.create(effectiveProfile(), sink, video.viewport(w, h))
        relayout()
        updateViewport()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) updateGestureExclusion()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        haptics.refreshSystemSetting()
        gamepads.start()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        gamepads.stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) releaseAll() else haptics.refreshSystemSetting()
    }

    // =====================================================================================
    // Profil efektywny, układ, viewport, wykluczenia gestów, rysowanie
    // =====================================================================================

    private fun effectiveProfile(): Profile = EffectiveProfile.build(baseProfile, controlsHidden, profileTransforms)

    private fun applyEffectiveProfile() {
        releaseAll()
        engine?.setProfile(effectiveProfile())
        relayout()
    }

    private fun relayout() {
        controlLayout = ControlLayout.of(effectiveProfile(), width, height)
        updateGestureExclusion()
        invalidate()
    }

    private fun updateViewport() {
        val e: InputEngine = engine ?: return
        if (width <= 0 || height <= 0) return
        e.setViewport(video.viewport(width, height))
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rects: ArrayList<Rect> = ArrayList()
        for (r in GestureExclusion.forSticks(controlLayout, width.toFloat(), density)) {
            rects.add(Rect(r.left.toInt(), r.top.toInt(), Math.ceil(r.right.toDouble()).toInt(), Math.ceil(r.bottom.toDouble()).toInt()))
        }
        setSystemGestureExclusionRects(rects)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas, snapshot(), width, height)
    }

    /** Co narysować teraz: układ + stan silnika (+ dane debug). Mała migawka na klatkę, tylko po invalidate(). */
    private fun snapshot(): OverlaySnapshot {
        val e: InputEngine? = engine
        val state: EngineVisualState = e?.visualState() ?: EngineVisualState.EMPTY
        val debug: DebugInfo? = if (!debugEnabled) null else DebugInfo(
            translator.trackedPointerIds, sink.lastPadButtons, latency.last, latency.percentile(95), latency.size,
            e?.violations?.size, controlsHidden, gamepads.isPresent,
        )
        return OverlaySnapshotBuilder.build(controlLayout, state, baseProfile.global.opacity, debug)
    }
}
