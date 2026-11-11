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
import app.thumbcast.model.Profile

/**
 * Jedyny punkt wejścia dla `:app` (`com.limelight.thumbcast.ThumbcastSession`). Składa adaptery, silnik i overlay.
 *
 * Wszystkie akcje aplikacji ("menu", "soft_keyboard", "toggle_overlay") trafiają bez zmian do [AppActionHandler].
 * Ukrycie kontrolek robi wołający przez publiczne [ThumbcastOverlay.setControlsHidden], bo to on łączy
 * wybór użytkownika z ustawieniem „ukryj przy podłączonym padzie”.
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
        val sink: CompositeInputSink = CompositeInputSink(
            PadSink(padReporter),
            KeyboardSink(hostConnection, keyTranslator),
            MouseSink(hostConnection),
            appActionHandler,
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
        return ThumbcastOverlay(context, profile, sink, engineFactory, tickHandler)
    }

    /**
     * Kopia profilu z nadpisanym `global.autoHideOnGamepad`. Sesja w `:app` ustawia `false`, bo sama decyduje
     * o ukryciu przy padzie (ustawienie użytkownika ma pierwszeństwo przed polem profilu). Pomocnik dla Javy,
     * która nie ma `copy()` z argumentami nazwanymi.
     */
    @JvmStatic
    fun withAutoHideOnGamepad(profile: Profile, enabled: Boolean): Profile =
        if (profile.global.autoHideOnGamepad == enabled) profile
        else profile.copy(global = profile.global.copy(autoHideOnGamepad = enabled))

    private fun defaultEngine(profile: Profile, sink: CompositeInputSink, viewport: Viewport): InputEngine =
        InputEngine(profile, sink, EngineBridge.uptimeClock, viewport)
}
