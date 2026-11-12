package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Cała logika Thumbcast w aktywności Game: Game trzyma jedno pole
// (`thumbcast`, null = tryb legacy) i woła kilka jednolinijkowych haków (patches/Game.java.patch.md).
//
// Sesja odpowiada za: wczytanie profilu (błąd = null, Game tworzy wtedy VirtualController), rozmiar
// kontrolek (ScaleTransform), overlay nad StreamView, prostokąt wideo, HostPadReporterImpl, akcje
// aplikacji (Command: mapa akcja -> Runnable), menu w grze (☰, gest od górnej krawędzi, systemowy Back;
// widok ThumbcastMenuView nad overlayem, pozycje w ThumbcastMenuModel),
// ukrywanie kontrolek przy podłączonym padzie, PiP i sprzątanie.
//
// Otwarte menu (12.11.2026) zatrzymuje wejście fizyczne: Game pyta sesję o każdy klawisz (routeKeyEvent)
// i zdarzenie ruchu (consumeMotionEvent), zanim cokolwiek wyśle do hosta. Klawisze i przyciski pada nawigują
// po menu (MenuInputRouter z :control-android), osie pada też, a do hosta nic nie trafia. Przy otwarciu menu
// stan nakładki i padów fizycznych jest zerowany (przyciski puszczone, gałki na środku).
//
// Zmiana profilu w trakcie gry (12.11.2026): strona menu z profilami z biblioteki (ProfileRepository, presety
// i profile użytkownika). Wybór zeruje stan na hoście, przebudowuje silnik i overlay (setProfile, jak przy
// starcie: ten sam plik wczytany przez ThumbcastProfiles, ukrywanie przy padzie i skala z sesji) i zapisuje
// nazwę pliku w preferencji list_thumbcast_profile (jak biblioteka profili).
//
// Profil aplikacji (12.11.2026, ThumbcastAppSettings): gdy uruchamiana aplikacja hosta ma przypisany profil,
// sesja startuje z nim (brak pliku albo błąd walidacji = profil globalny i komunikat). Zmiana profilu w grze
// działa wtedy tylko w tej sesji: nie zmienia ani przypisania aplikacji, ani profilu globalnego.
//
// Gesty myszy (12.11.2026): przełącznik w menu dla aktywnego profilu (zapis w ThumbcastSettings, klucz = plik
// profilu), profil efektywny = MouseGesturesTransform(profil z pliku), potem ScaleTransform. Zmiana na żywo.
// Profil domyślny (list_thumbcast_profile) usunięty poza biblioteką: profil dostarczany z aplikacją i komunikat.
//
// Nakładka statystyk (12.11.2026): przełączniki „Statystyki” i „Szczegółowe statystyki” w menu w grze (StatsToggles,
// zapis globalny w ThumbcastSettings; „Statystyki” = upstreamowy checkbox_enable_perf_overlay). Dane liczbowe
// z MediaCodecDecoderRenderer (setThumbcastStatsListener), tekst StatsFormatter, widok ThumbcastStatsView w prawym
// górnym rogu, z dala od kontrolek. Upstreamowy TextView performanceOverlay w trybie Thumbcast jest ukryty.
//
// Wyjście (12.11.2026): „Rozłącz (gra działa dalej)” = jak dotąd (finish, aplikacja na PC zostaje) i „Zakończ sesję…”
// (strona potwierdzenia): finish, a po zatrzymaniu strumienia /cancel (ThumbcastHostQuit, jak „Zakończenie sesji”
// z listy PC), więc Sunshine zamyka aplikację i przywraca rozdzielczość ekranu.

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Locale;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.thumbcast.android.ThumbcastOverlayFactory;
import app.thumbcast.android.ThumbcastProfiles;
import app.thumbcast.android.apps.AppStreamOverrides;
import app.thumbcast.android.apps.ProfileSelection;
import app.thumbcast.android.library.ProfileEntry;
import app.thumbcast.android.library.ProfileRepository;
import app.thumbcast.android.menu.MenuCommand;
import app.thumbcast.android.menu.MenuInputRouter;
import app.thumbcast.android.menu.MenuKeyResult;
import app.thumbcast.android.menu.MenuPage;
import app.thumbcast.android.overlay.PxRect;
import app.thumbcast.android.overlay.ThumbcastOverlay;
import app.thumbcast.android.stats.StatsFormatter;
import app.thumbcast.android.stats.StatsLabels;
import app.thumbcast.android.stats.StatsMode;
import app.thumbcast.android.stats.StatsToggles;
import app.thumbcast.android.stats.StreamStats;
import app.thumbcast.android.stats.StreamStatsListener;
import app.thumbcast.model.AppActions;
import app.thumbcast.model.MouseGesturesTransform;
import app.thumbcast.model.Profile;
import app.thumbcast.model.ScaleTransform;

public final class ThumbcastSession {

    /** {@link #routeKeyEvent}: zwykła ścieżka Game (host). */
    public static final int KEY_PASS = 0;
    /** {@link #routeKeyEvent}: zjedzone przez menu (Game zwraca true). */
    public static final int KEY_CONSUMED = 1;
    /** {@link #routeKeyEvent}: do systemu, nie do hosta (Game zwraca false). */
    public static final int KEY_SYSTEM = 2;

    /** Zwrotne wywołania do aktywności strumienia. */
    public interface Host {
        void toggleKeyboard();

        /** Koniec strumienia tak jak w upstreamie po Back: finish() -> onStop() -> stopConnection(). */
        void disconnect();
    }

    private final Activity activity;
    private final Host host;
    private final ThumbcastSettings settings;
    private final PreferenceConfiguration prefConfig;
    private final ThumbcastOverlay overlay;
    private final HostPadReporterImpl padReporter;
    private final ControllerHandler controllerHandler;
    private final MenuInputRouter menuInput = new MenuInputRouter();
    /** Profil z pliku z wyłączonym autoHideOnGamepad (ukrywaniem przy padzie steruje sesja), bez skalowania. */
    private Profile baseProfile;
    /** Nazwa pliku aktywnego profilu (wartość preferencji list_thumbcast_profile albo profil aplikacji). */
    private String activeProfileFile;
    /** Zapisany przełącznik „Gesty myszy” aktywnego profilu; null = zachowanie z pliku profilu. */
    private Boolean mouseGestures;
    /** true = sesja wystartowała z profilem aplikacji: zmiana profilu w grze nie zapisuje preferencji globalnej. */
    private final boolean sessionOnlyProfile;
    private final Map<String, Runnable> commands;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final InputManager inputManager;

    private int scalePercent;
    private boolean hideOnGamepad;
    private boolean gamepadConnected;
    /** Wybór z menu / akcji toggle_overlay; null = reguła automatyczna (pad + ustawienie). */
    private Boolean manualHidden;
    /** Rodzic StreamView: overlay i menu są jego ostatnimi dziećmi. */
    private final FrameLayout overlayParent;
    /** Tworzony przy pierwszym otwarciu; pokazany = dodany do {@link #overlayParent}. */
    private ThumbcastMenuView menuView;
    /** Strona menu (główna, profile, potwierdzenie „Zakończ sesję”). */
    private MenuPage menuPage = MenuPage.MAIN;
    /** Lista profili wczytana przy otwarciu strony profili. */
    private List<ThumbcastMenuModel.ProfileChoice> profileChoices = Collections.emptyList();
    private boolean destroyed;

    /** Przełączniki nakładki statystyk (menu w grze, zapis w ThumbcastSettings). */
    private StatsToggles statsToggles;
    /** Źródło danych (attachPerfStats); null = nakładka statystyk niedostępna. */
    private MediaCodecDecoderRenderer statsSource;
    /** Tworzony przy pierwszym włączeniu; pod overlayem kontrolek. */
    private ThumbcastStatsView statsView;
    private StatsFormatter statsFormatter;
    /** Ostatnia próbka (przełączenie trybu pokazuje ją od razu, bez czekania sekundy). */
    private StreamStats lastStats;
    private boolean inPip;
    /** „Zakończ sesję”: po zatrzymaniu strumienia zamknąć aplikację na hoście (takeAfterStopTask / onDestroy). */
    private ThumbcastHostQuit pendingHostQuit;

    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            refreshGamepadState();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refreshGamepadState();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refreshGamepadState();
        }
    };

    /**
     * Tworzy sesję i dokłada overlay jako ostatnie dziecko rodzica StreamView (FrameLayout).
     * Zwraca null, gdy wybrano tryb legacy albo profil się nie wczytał (wtedy Toast z błędem):
     * Game tworzy wtedy VirtualController jak w upstreamie (onscreenController jest wymuszone).
     */
    public static ThumbcastSession createOrNull(Activity activity, Host host, PreferenceConfiguration prefConfig,
                                                NvConnection conn, ControllerHandler controllerHandler,
                                                KeyboardTranslator keyboardTranslator, View streamView) {
        if (prefConfig.controlLayer != PreferenceConfiguration.ControlLayer.THUMBCAST) {
            return null;
        }
        ProfileSelection<Profile> selection = AppStreamOverrides.selectProfile(prefConfig.thumbcastAppProfileFile,
                prefConfig.thumbcastProfileFile, name -> ThumbcastProfiles.loadOrNull(activity, name),
                ThumbcastProfiles.DEFAULT_PRESET);
        if (selection.getDefaultProfileFailed() && selection.getValue() != null) {
            // Preferencji nie zmieniamy: plik mógł być tylko chwilowo niepoprawny (np. po zmianie wersji).
            LimeLog.warning("Thumbcast: default profile " + prefConfig.thumbcastProfileFile + " failed, using "
                    + ThumbcastProfiles.DEFAULT_PRESET);
            Toast.makeText(activity, activity.getString(R.string.thumbcast_default_profile_missing,
                    selection.getValue().getName()), Toast.LENGTH_LONG).show();
        }
        if (selection.getAppProfileFailed()) {
            LimeLog.warning("Thumbcast: app profile " + prefConfig.thumbcastAppProfileFile + " failed: "
                    + ThumbcastProfiles.getLastError());
            Toast.makeText(activity, R.string.thumbcast_app_profile_missing, Toast.LENGTH_LONG).show();
        }
        Profile profile = selection.getValue();
        if (profile == null) {
            Toast.makeText(activity, activity.getString(R.string.thumbcast_profile_error,
                    String.valueOf(ThumbcastProfiles.getLastError())), Toast.LENGTH_LONG).show();
            return null;
        }
        return new ThumbcastSession(activity, host, prefConfig, conn, controllerHandler, keyboardTranslator,
                streamView, profile, selection.getFileName(), selection.getFromApp());
    }

    /** Host dla aktywności z upstreamowym `toggleKeyboard()` (GameGestures) i zakończeniem przez finish(). */
    public static Host activityHost(final Activity activity, final GameGestures gestures) {
        return new Host() {
            @Override
            public void toggleKeyboard() {
                gestures.toggleKeyboard();
            }

            @Override
            public void disconnect() {
                activity.finish();
            }
        };
    }

    private ThumbcastSession(Activity activity, Host host, final PreferenceConfiguration prefConfig,
                             NvConnection conn, ControllerHandler controllerHandler,
                             KeyboardTranslator keyboardTranslator, View streamView, Profile profile,
                             String profileFile, boolean sessionOnlyProfile) {
        this.activity = activity;
        this.host = host;
        this.settings = new ThumbcastSettings(activity);
        this.prefConfig = prefConfig;
        this.activeProfileFile = profileFile;
        this.mouseGestures = settings.getMouseGestures(profileFile);
        this.sessionOnlyProfile = sessionOnlyProfile;
        this.scalePercent = settings.getControlScalePercent();
        this.hideOnGamepad = settings.isHideOnGamepad();
        this.baseProfile = ThumbcastOverlayFactory.withAutoHideOnGamepad(profile, false);
        this.commands = buildCommands();

        this.controllerHandler = controllerHandler;
        padReporter = new HostPadReporterImpl(controllerHandler);
        overlay = ThumbcastOverlayFactory.create(
                activity,
                effectiveProfile(),
                new HostConnectionImpl(conn),
                padReporter,
                this::onAppAction,
                new ThumbcastKeyTranslator(keyboardTranslator));
        overlayParent = (FrameLayout) streamView.getParent();
        // Ostatnie dziecko FrameLayout jest rysowane na wierzchu i pierwsze dostaje dotyk.
        overlayParent.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setDebugEnabled(prefConfig.thumbcastDebugOverlay);
        overlay.setStreamResolution(prefConfig.width, prefConfig.height);
        // Prostokąt wideo = rozmiar StreamView po layoucie (onMeasure dopasowuje proporcje, layout_gravity=center
        // wyśrodkowuje). l/t/r/b są w układzie rodzica, tym samym co overlay (MATCH_PARENT, bez paddingu).
        streamView.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) ->
                overlay.setVideoRect(l, t, r, b, prefConfig.width, prefConfig.height));

        inputManager = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, mainHandler);
        }
        gamepadConnected = isPhysicalGamepadConnected();
        applyControlsHidden();
    }

    // =====================================================================================
    // Haki z Game (każdy to jedna linia w bloku THUMBCAST)
    // =====================================================================================

    /** handleMotionEvent: palce obsługuje overlay (do 10 wskaźników), touchContextMap pomijamy. */
    public boolean consumesFingerTouches() {
        return !destroyed;
    }

    /** Systemowy Back (przycisk lub gest): zamiast wyjścia otwiera menu, a przy otwartym je zamyka. true = obsłużone. */
    public boolean onBackPressed() {
        if (destroyed) {
            return false;
        }
        if (isMenuShown()) {
            onMenuBack();
        } else {
            showMenu();
        }
        return true;
    }

    /**
     * handleKeyDown / handleKeyUp / handleKeyMultiple, przed czymkolwiek innym: przy otwartym menu klawisze
     * i przyciski pada nawigują po menu i nie trafiają do hosta. Zwraca {@link #KEY_PASS} (zwykła ścieżka),
     * {@link #KEY_CONSUMED} albo {@link #KEY_SYSTEM} (głośność, zasilanie: obsługuje system).
     */
    public int routeKeyEvent(KeyEvent event) {
        if (destroyed) {
            return KEY_PASS;
        }
        MenuKeyResult r = menuInput.onKey(isMenuShown(), event.getAction(), event.getKeyCode(),
                event.getRepeatCount(), event.getDeviceId());
        if (r.getCommand() != MenuCommand.NONE) {
            onMenuCommand(r.getCommand());
        }
        switch (r.getRoute()) {
            case CONSUME:
                return KEY_CONSUMED;
            case SYSTEM:
                return KEY_SYSTEM;
            default:
                return KEY_PASS;
        }
    }

    /**
     * handleMotionEvent, przed czymkolwiek innym: przy otwartym menu żadne zdarzenie ruchu (pad, mysz, touchpad)
     * nie trafia do hosta. Krzyżak i lewa gałka pada przesuwają zaznaczenie. true = zjedzone.
     */
    public boolean consumeMotionEvent(MotionEvent event) {
        if (destroyed || !isMenuShown()) {
            return false;
        }
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            List<MenuCommand> commands = menuInput.onAxes(event.getDeviceId(),
                    event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                    event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y));
            for (MenuCommand command : commands) {
                onMenuCommand(command);
            }
        }
        return true;
    }

    /** PiP: overlay i menu znikają, wszystkie wejścia zwolnione. */
    public void onPictureInPictureChanged(boolean inPip) {
        if (inPip) {
            overlay.releaseAll();
            hideMenu();
            overlay.setVisibility(View.GONE);
        } else {
            overlay.setVisibility(View.VISIBLE);
        }
        this.inPip = inPip;
        applyStats();
    }

    /**
     * Po utworzeniu dekodera (Game.onCreate): nakładka statystyk Thumbcast zamiast upstreamowego tekstu. Upstreamowy
     * TextView jest chowany, a {@code prefConfig.enablePerfOverlay} zerowane tylko w obiekcie tego strumienia (renderer
     * nie składa wtedy upstreamowego tekstu, Game nie pokazuje widoku po PiP); zapisana preferencja bez zmian.
     */
    public void attachPerfStats(MediaCodecDecoderRenderer renderer, View upstreamPerfView) {
        if (renderer == null) {
            return;
        }
        statsSource = renderer;
        prefConfig.enablePerfOverlay = false;
        if (upstreamPerfView != null) {
            upstreamPerfView.setVisibility(View.GONE);
        }
        applyStats();
    }

    /**
     * Game.stopConnection: zadanie do wykonania w wątku zatrzymania po {@code conn.stop()} (null = brak). Po
     * „Zakończ sesję” to zamknięcie aplikacji na hoście; zadanie jest oddawane raz.
     */
    public Runnable takeAfterStopTask() {
        Runnable task = pendingHostQuit;
        pendingHostQuit = null;
        return task;
    }

    /** stopConnection: zerowy stan na hosta przed zamknięciem połączenia. */
    public void onStopConnection() {
        overlay.releaseAll();
    }

    /** onDestroy: bez zaległych retransmisji, listenerów i menu. */
    public void onDestroy() {
        destroyed = true;
        if (statsSource != null) {
            statsSource.setThumbcastStatsListener(null);
        }
        // „Zakończ sesję” bez zatrzymania strumienia (np. połączenie jeszcze się nie zestawiło): zamknij teraz.
        Runnable quit = takeAfterStopTask();
        if (quit != null) {
            new Thread(quit, "thumbcast-end-session").start();
        }
        padReporter.cancelPending();
        mainHandler.removeCallbacksAndMessages(null);
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(deviceListener);
        }
        hideMenu();
    }

    // =====================================================================================
    // Akcje aplikacji (Command)
    // =====================================================================================

    private Map<String, Runnable> buildCommands() {
        Map<String, Runnable> map = new HashMap<>();
        map.put(AppActions.MENU, this::showMenu);
        map.put(AppActions.SOFT_KEYBOARD, host::toggleKeyboard);
        map.put(AppActions.TOGGLE_OVERLAY, this::toggleControls);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Akcja z overlaya (przycisk systemowy albo gest). Silnik woła ją w trakcie obsługi dotyku, więc
     * wykonujemy ją po zakończeniu tego cyklu (releaseAll z menu nie może wejść w środek silnika).
     */
    private void onAppAction(String action) {
        final Runnable command = commands.get(action);
        if (command == null) {
            LimeLog.warning("Thumbcast: unknown app action " + action);
            return;
        }
        mainHandler.post(() -> {
            if (!destroyed) {
                command.run();
            }
        });
    }

    // =====================================================================================
    // Ukrywanie kontrolek
    // =====================================================================================

    private void toggleControls() {
        manualHidden = !overlay.getControlsHidden();
        applyControlsHidden();
    }

    private void applyControlsHidden() {
        boolean hidden = manualHidden != null ? manualHidden : (hideOnGamepad && gamepadConnected);
        overlay.setControlsHidden(hidden);
        // Ukryte lub pokazane kontrolki zmieniają miejsce wolne dla nakładki statystyk.
        showStats();
        // Pad podłączony/odłączony przy otwartym menu: etykieta Ukryj/Pokaż przyciski.
        refreshMenu();
    }

    private void refreshGamepadState() {
        boolean now = isPhysicalGamepadConnected();
        if (now == gamepadConnected) {
            return;
        }
        gamepadConnected = now;
        // Podłączenie/odłączenie pada przywraca regułę automatyczną.
        manualHidden = null;
        applyControlsHidden();
    }

    /** Fizyczny pad: nie wirtualny, źródło GAMEPAD lub JOYSTICK, od Androida 10 tylko zewnętrzny. */
    private static boolean isPhysicalGamepadConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null || dev.isVirtual()) {
                continue;
            }
            int sources = dev.getSources();
            boolean isPad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (!isPad) {
                continue;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !dev.isExternal()) {
                continue;
            }
            return true;
        }
        return false;
    }

    // =====================================================================================
    // Rozmiar kontrolek (na żywo: setProfile zwalnia wejścia i przelicza układ)
    // =====================================================================================

    private Profile effectiveProfile() {
        Profile withMouse = MouseGesturesTransform.apply(baseProfile, mouseGestures);
        return ScaleTransform.INSTANCE.scale(withMouse, scalePercent / 100f);
    }

    private boolean isMouseGestures() {
        return MouseGesturesTransform.effective(baseProfile, mouseGestures);
    }

    /** Przełącznik „Gesty myszy” aktywnego profilu: zapis dla jego pliku i nowy profil efektywny na żywo. */
    private void setMouseGestures(boolean enabled) {
        if (enabled == isMouseGestures()) {
            return;
        }
        mouseGestures = MouseGesturesTransform.toStore(baseProfile, enabled);
        settings.setMouseGestures(activeProfileFile, mouseGestures);
        overlay.setProfile(effectiveProfile());
        Toast.makeText(activity, mouseGesturesHint(activity, enabled, MouseGesturesTransform.style(baseProfile)),
                Toast.LENGTH_LONG).show();
    }

    /** Krótki opis, co robi przełącznik w danym profilu (menu w grze i biblioteka). */
    static String mouseGesturesHint(Context context, boolean enabled, MouseGesturesTransform.Style style) {
        switch (style) {
            case AT_POINT:
                return context.getString(enabled ? R.string.thumbcast_mouse_gestures_on_camera
                        : R.string.thumbcast_mouse_gestures_off_camera);
            case TOUCHPAD:
                return context.getString(enabled ? R.string.thumbcast_mouse_gestures_on_touchpad
                        : R.string.thumbcast_mouse_gestures_off_touchpad);
            default:
                return context.getString(enabled ? R.string.thumbcast_mouse_gestures_on_direct
                        : R.string.thumbcast_mouse_gestures_off_direct);
        }
    }

    private void applyScale(int percent) {
        if (percent == scalePercent) {
            return;
        }
        scalePercent = percent;
        settings.setControlScalePercent(percent);
        overlay.setProfile(effectiveProfile());
    }

    private void setHideOnGamepad(boolean hide) {
        hideOnGamepad = hide;
        settings.setHideOnGamepad(hide);
        manualHidden = null;
        applyControlsHidden();
    }

    // =====================================================================================
    // Wyjście: „Rozłącz” (Host.disconnect) i „Zakończ sesję” (rozłączenie + /cancel na hoście)
    // =====================================================================================

    /**
     * „Zakończ sesję” (po potwierdzeniu): zerowy stan na hoście, zatrzymanie strumienia tak jak przy „Rozłącz”
     * (finish → onStop → stopConnection), a w wątku zatrzymania po {@code conn.stop()} żądanie /cancel
     * ({@link #takeAfterStopTask}). Sunshine zamyka wtedy aplikację i przywraca konfigurację ekranu.
     */
    private void endSession() {
        if (destroyed || pendingHostQuit != null) {
            return;
        }
        releaseHostInput();
        pendingHostQuit = new ThumbcastHostQuit(activity, activity.getIntent());
        Toast.makeText(activity, R.string.thumbcast_end_session_progress, Toast.LENGTH_SHORT).show();
        host.disconnect();
    }

    // =====================================================================================
    // Nakładka statystyk (FPS, opóźnienie; szczegóły z upstreamowej nakładki wydajności)
    // =====================================================================================

    private StatsToggles currentStatsToggles() {
        if (statsToggles == null) {
            statsToggles = settings.getStatsToggles();
        }
        return statsToggles;
    }

    private void setStatsToggles(StatsToggles toggles) {
        statsToggles = toggles;
        settings.setStatsToggles(toggles);
        applyStats();
    }

    /** Próbki przychodzą z wątku odbioru wideo; widok zmieniamy na wątku UI. */
    private final StreamStatsListener statsListener = stats -> mainHandler.post(() -> onStreamStats(stats));

    /** Widok i słuchacz zgodnie z trybem: wyłączona nakładka (albo PiP) = renderer nic nie liczy. */
    private void applyStats() {
        if (statsSource == null) {
            return;
        }
        StatsMode mode = currentStatsToggles().getMode();
        boolean show = mode != StatsMode.OFF && !inPip && !destroyed;
        if (!show) {
            statsSource.setThumbcastStatsListener(null);
            if (statsView != null) {
                statsView.setVisibility(View.GONE);
            }
            return;
        }
        if (statsView == null) {
            statsView = new ThumbcastStatsView(activity);
            statsFormatter = new StatsFormatter(statsLabels(), Locale.getDefault());
            // Pod overlayem kontrolek (i pod menu): kontrolki są rysowane nad napisem, dotyk idzie do nich.
            overlayParent.addView(statsView, overlayParent.indexOfChild(overlay), ThumbcastStatsView.layoutParams());
            // Zmiana rozmiaru ekranu (obrót, okno): nowe położenie po layoucie.
            overlayParent.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                if (r - l != oldR - oldL || b - t != oldB - oldT) {
                    v.post(this::showStats);
                }
            });
        }
        statsView.setVisibility(View.VISIBLE);
        statsSource.setThumbcastStatsListener(statsListener);
        showStats();
    }

    private void onStreamStats(StreamStats stats) {
        if (destroyed) {
            return;
        }
        lastStats = stats;
        showStats();
    }

    /** Tekst dla bieżącego trybu (bez próbki: „– FPS”) i położenie z dala od kontrolek i komunikatów upstreamu. */
    private void showStats() {
        if (statsView == null || statsView.getVisibility() != View.VISIBLE || statsFormatter == null) {
            return;
        }
        StatsMode mode = currentStatsToggles().getMode();
        String text = lastStats != null ? statsFormatter.format(mode, lastStats)
                : activity.getString(R.string.thumbcast_stats_waiting);
        // Overlay leży na całym rodzicu (MATCH_PARENT, bez marginesów), więc jego px = px rodzica.
        List<PxRect> obstacles = new ArrayList<>(overlay.controlBounds());
        View notification = activity.findViewById(R.id.notificationOverlay);
        if (notification != null && notification.getVisibility() == View.VISIBLE) {
            obstacles.add(new PxRect(notification.getLeft(), notification.getTop(),
                    notification.getRight(), notification.getBottom()));
        }
        statsView.show(text, obstacles);
    }

    private StatsLabels statsLabels() {
        return new StatsLabels(
                activity.getString(R.string.thumbcast_stats_basic),
                activity.getString(R.string.thumbcast_stats_basic_no_delay),
                activity.getString(R.string.thumbcast_stats_video),
                activity.getString(R.string.thumbcast_stats_decoder),
                activity.getString(R.string.thumbcast_stats_frames),
                activity.getString(R.string.thumbcast_stats_drops),
                activity.getString(R.string.thumbcast_stats_network),
                activity.getString(R.string.thumbcast_stats_network_unknown),
                activity.getString(R.string.thumbcast_stats_host),
                activity.getString(R.string.thumbcast_stats_host_unknown),
                activity.getString(R.string.thumbcast_stats_decode),
                activity.getString(R.string.thumbcast_stats_decode_unknown),
                activity.getString(R.string.thumbcast_stats_breakdown));
    }

    // =====================================================================================
    // Menu w grze (widok nad overlayem w oknie aktywności, bez osobnego okna)
    // =====================================================================================

    private boolean isMenuShown() {
        return menuView != null && menuView.getParent() != null;
    }

    private void showMenu() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        // Nic nie może zostać wciśnięte na hoście. Palce, które jeszcze leżą na overlayu, po releaseAll
        // nie są śledzone (translator ignoruje ich MOVE), a nowe trafiają w menu, które konsumuje każdy dotyk.
        releaseHostInput();
        if (menuView == null) {
            menuView = new ThumbcastMenuView(activity, menuListener);
        }
        if (menuView.getParent() == null) {
            menuInput.resetAxes();
            setPhysicalInputBlocked(true);
            // Ostatnie dziecko: rysowane nad overlayem i pierwsze w kolejce dotyku.
            overlayParent.addView(menuView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            menuView.clearSelection();
            menuView.scrollToTop();
        }
        refreshMenu();
    }

    /**
     * Zerowy stan na hoście: nakładka (pad, klawisze, mysz; releaseAll jak przy ukryciu, PiP i utracie fokusu)
     * i pady fizyczne (Game nie przekazuje ich zdarzeń, dopóki menu jest otwarte, więc puszczenie przycisku
     * albo powrót gałki nie dotarłby do hosta). Klawisze klawiatury fizycznej wciśnięte przed menu puszcza ich
     * własne zdarzenie ACTION_UP, które MenuInputRouter przepuszcza do hosta.
     */
    private void releaseHostInput() {
        overlay.releaseAll();
        controllerHandler.releaseAllControllerInput();
    }

    /** Pady z własnego sterownika USB omijają Game: ControllerHandler blokuje je sam, dopóki menu jest otwarte. */
    private void setPhysicalInputBlocked(boolean blocked) {
        controllerHandler.setThumbcastInputBlocked(blocked);
    }

    /** Zamknięcie bez akcji (Wróć do gry, tap w tło, Back, PiP, onDestroy). */
    private void hideMenu() {
        if (isMenuShown()) {
            overlayParent.removeView(menuView);
        }
        menuPage = MenuPage.MAIN;
        menuInput.resetAxes();
        setPhysicalInputBlocked(false);
    }

    /** B, Back, Escape przy otwartym menu: z podstrony na stronę główną, ze strony głównej do gry. */
    private void onMenuBack() {
        MenuPage back = menuPage.back();
        if (back != null) {
            showPage(back);
        } else {
            hideMenu();
        }
    }

    private void showPage(MenuPage page) {
        if (page == MenuPage.PROFILES) {
            profileChoices = loadProfileChoices();
        }
        menuPage = page;
        refreshMenu();
        if (isMenuShown()) {
            menuView.scrollToTop();
        }
    }

    /** Profile z biblioteki (presety, potem profile użytkownika), bez plików uszkodzonych. */
    private List<ThumbcastMenuModel.ProfileChoice> loadProfileChoices() {
        List<ThumbcastMenuModel.ProfileChoice> out = new ArrayList<>();
        String defaultFile = ProfileLibraryActivity.defaultProfileFile(activity);
        try {
            for (ProfileEntry e : ProfileRepository.create(activity).listAll()) {
                if (e.isValid()) {
                    out.add(new ThumbcastMenuModel.ProfileChoice(e.getFileName(), e.getName(),
                            e.getFileName().equals(activeProfileFile), e.getFileName().equals(defaultFile)));
                }
            }
        } catch (RuntimeException e) {
            LimeLog.warning("Thumbcast: profile list failed: " + e);
        }
        return out;
    }

    /** Nazwa wyświetlana aktywnego profilu (pole name z pliku). */
    private String activeProfileName() {
        return baseProfile.getName();
    }

    /**
     * Zmiana profilu na żywo. Najpierw zerowy stan na hoście (nakładka i pady), potem nowy profil w overlayu
     * (setProfile: releaseAll, silnik z nowym profilem, układ i haptyka) z bieżącą skalą i regułą ukrywania.
     */
    private void switchProfile(String fileName) {
        if (destroyed || fileName == null || fileName.equals(activeProfileFile)) {
            return;
        }
        Profile loaded = ThumbcastProfiles.loadOrNull(activity, fileName);
        if (loaded == null) {
            Toast.makeText(activity, activity.getString(R.string.thumbcast_menu_profile_switch_error,
                    String.valueOf(ThumbcastProfiles.getLastError())), Toast.LENGTH_LONG).show();
            return;
        }
        releaseHostInput();
        baseProfile = ThumbcastOverlayFactory.withAutoHideOnGamepad(loaded, false);
        activeProfileFile = fileName;
        mouseGestures = settings.getMouseGestures(fileName);
        if (!sessionOnlyProfile) {
            prefConfig.thumbcastProfileFile = fileName;
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString(ProfileLibraryActivity.PREF_PROFILE, fileName).apply();
        }
        overlay.setProfile(effectiveProfile());
        applyControlsHidden();
        Toast.makeText(activity, activity.getString(sessionOnlyProfile
                ? R.string.thumbcast_menu_profile_session_only : R.string.thumbcast_library_default_set,
                loaded.getName()), Toast.LENGTH_SHORT).show();
    }

    /** Polecenie z klawiatury lub pada przy otwartym menu. */
    private void onMenuCommand(MenuCommand command) {
        if (!isMenuShown()) {
            return;
        }
        if (command == MenuCommand.BACK) {
            onMenuBack();
        } else {
            menuView.handleCommand(command);
        }
    }

    private void refreshMenu() {
        if (!isMenuShown()) {
            return;
        }
        if (menuPage == MenuPage.PROFILES) {
            // Wybór w grze zmienia profil domyślny, chyba że sesja ma profil przypisany do aplikacji.
            menuView.setTitle(menuString(sessionOnlyProfile ? R.string.thumbcast_menu_profiles_title_session
                    : R.string.thumbcast_menu_profiles_title));
            menuView.bind(ThumbcastMenuModel.buildProfiles(this::menuString, profileChoices, menuTarget));
        } else if (menuPage == MenuPage.END_SESSION) {
            menuView.setTitle(menuString(R.string.thumbcast_menu_end_session_title));
            menuView.bind(ThumbcastMenuModel.buildEndSession(this::menuString, menuTarget));
        } else {
            menuView.setTitle(menuString(R.string.thumbcast_menu_title));
            menuView.bind(ThumbcastMenuModel.build(this::menuString, menuTarget));
        }
    }

    private String menuString(int resId, Object... args) {
        return args.length == 0 ? activity.getString(resId) : activity.getString(resId, args);
    }

    private final ThumbcastMenuView.Listener menuListener = new ThumbcastMenuView.Listener() {
        @Override
        public void onMenuAction(ThumbcastMenuModel.Item item, Runnable action) {
            if (destroyed || !isMenuShown()) {
                return;
            }
            if (item.closesMenu) {
                hideMenu();
            }
            action.run();
            refreshMenu();
        }

        @Override
        public void onDismissRequested() {
            hideMenu();
        }
    };

    private final ThumbcastMenuModel.Target menuTarget = new ThumbcastMenuModel.Target() {
        @Override
        public void disconnect() {
            host.disconnect();
        }

        @Override
        public boolean areControlsHidden() {
            return overlay.getControlsHidden();
        }

        @Override
        public void toggleControls() {
            ThumbcastSession.this.toggleControls();
        }

        @Override
        public void toggleKeyboard() {
            // Okno aktywności nie traci fokusu (menu to widok, nie dialog), więc dawne opóźnienie 200 ms
            // nie jest potrzebne. Zostaje post: klawiatura po zdjęciu menu, poza obsługą kliknięcia.
            mainHandler.post(() -> {
                if (!destroyed) {
                    host.toggleKeyboard();
                }
            });
        }

        @Override
        public int getScalePercent() {
            return scalePercent;
        }

        @Override
        public void setScalePercent(int percent) {
            applyScale(percent);
        }

        @Override
        public boolean isHideOnGamepad() {
            return hideOnGamepad;
        }

        @Override
        public void setHideOnGamepad(boolean hide) {
            ThumbcastSession.this.setHideOnGamepad(hide);
        }

        @Override
        public String getActiveProfileName() {
            return activeProfileName();
        }

        @Override
        public void openProfiles() {
            showPage(MenuPage.PROFILES);
        }

        @Override
        public void showMainPage() {
            showPage(MenuPage.MAIN);
        }

        @Override
        public void openEndSession() {
            showPage(MenuPage.END_SESSION);
        }

        @Override
        public void endSession() {
            ThumbcastSession.this.endSession();
        }

        @Override
        public StatsToggles getStatsToggles() {
            return currentStatsToggles();
        }

        @Override
        public void setStatsToggles(StatsToggles toggles) {
            ThumbcastSession.this.setStatsToggles(toggles);
        }

        @Override
        public void switchProfile(String fileName) {
            ThumbcastSession.this.switchProfile(fileName);
        }

        @Override
        public boolean isMouseGestures() {
            return ThumbcastSession.this.isMouseGestures();
        }

        @Override
        public void setMouseGestures(boolean enabled) {
            ThumbcastSession.this.setMouseGestures(enabled);
        }
    };
}
