package app.thumbcast.android

import android.content.Context
import app.thumbcast.android.host.AppActionHandler
import app.thumbcast.android.host.HostConnection
import app.thumbcast.android.host.HostPadReporter
import app.thumbcast.android.host.KeyTranslator
import app.thumbcast.android.host.WindowsVkKeyTranslator
import app.thumbcast.android.overlay.EngineBridge
import app.thumbcast.android.overlay.ThumbcastOverlay
import app.thumbcast.android.sink.CompositeInputSink
import app.thumbcast.android.sink.KeyboardSink
import app.thumbcast.android.sink.MouseSink
import app.thumbcast.android.sink.PadSink
import app.thumbcast.engine.InputEngine
import app.thumbcast.engine.Viewport
import app.thumbcast.model.AppActions
import app.thumbcast.model.Profile

/**
 * Jedyny punkt wejścia dla `Game` (upstream). Składa adaptery, silnik i overlay.
 *
 * Akcja "toggle_overlay" jest obsługiwana lokalnie (ukrycie kontrolek, gesty zostają), a potem
 * i tak przekazywana do [AppActionHandler], żeby Game mogło np. zaktualizować menu.
 * Pozostałe akcje ("menu", "soft_keyboard") obsługuje wyłącznie Game.
 */
object ThumbcastOverlayFactory {

    @JvmStatic
    fun create(
        context: Context,
        profile: Profile,
        hostConnection: HostConnection,
        padReporter: HostPadReporter,
        appActionHandler: AppActionHandler,
    ): ThumbcastOverlay = create(context, profile, hostConnection, padReporter, appActionHandler, WindowsVkKeyTranslator)

    @JvmStatic
    fun create(
        context: Context,
        profile: Profile,
        hostConnection: HostConnection,
        padReporter: HostPadReporter,
        appActionHandler: AppActionHandler,
        keyTranslator: KeyTranslator,
    ): ThumbcastOverlay {
        val holder: OverlayHolder = OverlayHolder()
        val routedActions: AppActionHandler = AppActionHandler { action ->
            if (action == AppActions.TOGGLE_OVERLAY) {
                val o: ThumbcastOverlay? = holder.overlay
                if (o != null) {
                    o.toggleControlsHidden()
                }
            }
            appActionHandler.onAppAction(action)
        }
        val sink: CompositeInputSink = CompositeInputSink(
            PadSink(padReporter),
            KeyboardSink(hostConnection, keyTranslator),
            MouseSink(hostConnection),
            routedActions,
        )
        val engineFactory: ThumbcastOverlay.EngineFactory = ThumbcastOverlay.EngineFactory { p, s, viewport ->
            defaultEngine(p, s, viewport)
        }
        // ZAŁOŻENIE (CONTRACT-NOTES, ANDROID): InputEngine.onTick(nowMs: Long). Kontrakt go nie wymienia,
        // ale ActionPolicy w :control-engine opisuje cykl TICK wywoływany przez InputEngine.onTick.
        // Jedyne miejsce w module, które go woła.
        val tickHandler: ThumbcastOverlay.TickHandler = ThumbcastOverlay.TickHandler { engine, nowMs ->
            engine.onTick(nowMs)
        }
        val overlay: ThumbcastOverlay = ThumbcastOverlay(context, profile, sink, engineFactory, tickHandler)
        holder.overlay = overlay
        return overlay
    }

    private fun defaultEngine(profile: Profile, sink: CompositeInputSink, viewport: Viewport): InputEngine =
        InputEngine(profile, sink, EngineBridge.uptimeClock, viewport)

    private class OverlayHolder {
        var overlay: ThumbcastOverlay? = null
    }
}
