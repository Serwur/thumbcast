package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Cała logika Thumbcast w aktywności Game: Game trzyma jedno pole
// (`thumbcast`, null = tryb legacy) i woła kilka jednolinijkowych haków (patches/Game.java.patch.md).
//
// Sesja odpowiada za: wczytanie profilu (błąd = null, Game tworzy wtedy VirtualController), rozmiar
// kontrolek (ScaleTransform), overlay nad StreamView, prostokąt wideo, HostPadReporterImpl, akcje
// aplikacji (Command: mapa akcja -> Runnable), menu w grze (☰, gest od górnej krawędzi, systemowy Back),
// ukrywanie kontrolek przy podłączonym padzie, PiP i sprzątanie.

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
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
import app.thumbcast.android.overlay.ThumbcastOverlay;
import app.thumbcast.model.AppActions;
import app.thumbcast.model.Profile;
import app.thumbcast.model.ScaleTransform;

public final class ThumbcastSession {

    /** Zwrotne wywołania do aktywności strumienia. */
    public interface Host {
        void toggleKeyboard();

        /** Koniec strumienia tak jak w upstreamie po Back: finish() -> onStop() -> stopConnection(). */
        void disconnect();
    }

    /** Klawiatura ekranowa po zamknięciu menu: okno aktywności musi najpierw odzyskać fokus. */
    private static final long KEYBOARD_AFTER_DIALOG_MS = 200;
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private final Activity activity;
    private final Host host;
    private final ThumbcastSettings settings;
    private final ThumbcastOverlay overlay;
    private final HostPadReporterImpl padReporter;
    /** Profil z pliku z wyłączonym autoHideOnGamepad (ukrywaniem przy padzie steruje sesja), bez skalowania. */
    private final Profile baseProfile;
    private final Map<String, Runnable> commands;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final InputManager inputManager;

    private int scalePercent;
    private boolean hideOnGamepad;
    private boolean gamepadConnected;
    /** Wybór z menu / akcji toggle_overlay; null = reguła automatyczna (pad + ustawienie). */
    private Boolean manualHidden;
    private AlertDialog dialog;
    private Runnable afterDialog;
    private long afterDialogDelayMs;
    private boolean destroyed;

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
        Profile profile = ThumbcastProfiles.loadOrNull(activity, prefConfig.thumbcastProfileFile);
        if (profile == null) {
            Toast.makeText(activity, activity.getString(R.string.thumbcast_profile_error,
                    String.valueOf(ThumbcastProfiles.getLastError())), Toast.LENGTH_LONG).show();
            return null;
        }
        return new ThumbcastSession(activity, host, prefConfig, conn, controllerHandler, keyboardTranslator,
                streamView, profile);
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
                             KeyboardTranslator keyboardTranslator, View streamView, Profile profile) {
        this.activity = activity;
        this.host = host;
        this.settings = new ThumbcastSettings(activity);
        this.scalePercent = settings.getControlScalePercent();
        this.hideOnGamepad = settings.isHideOnGamepad();
        this.baseProfile = ThumbcastOverlayFactory.withAutoHideOnGamepad(profile, false);
        this.commands = buildCommands();

        padReporter = new HostPadReporterImpl(controllerHandler);
        overlay = ThumbcastOverlayFactory.create(
                activity,
                effectiveProfile(),
                new HostConnectionImpl(conn),
                padReporter,
                this::onAppAction,
                new ThumbcastKeyTranslator(keyboardTranslator));
        FrameLayout parent = (FrameLayout) streamView.getParent();
        // Ostatnie dziecko FrameLayout jest rysowane na wierzchu i pierwsze dostaje dotyk.
        parent.addView(overlay, new FrameLayout.LayoutParams(
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

    /** Systemowy Back (przycisk lub gest): zamiast wyjścia otwiera menu. true = obsłużone. */
    public boolean onBackPressed() {
        if (destroyed) {
            return false;
        }
        showMenu();
        return true;
    }

    /** PiP: overlay i menu znikają, wszystkie wejścia zwolnione. */
    public void onPictureInPictureChanged(boolean inPip) {
        if (inPip) {
            overlay.releaseAll();
            dismissDialog();
            overlay.setVisibility(View.GONE);
        } else {
            overlay.setVisibility(View.VISIBLE);
        }
    }

    /** stopConnection: zerowy stan na hosta przed zamknięciem połączenia. */
    public void onStopConnection() {
        overlay.releaseAll();
    }

    /** onDestroy: bez zaległych retransmisji, listenerów i okien. */
    public void onDestroy() {
        destroyed = true;
        padReporter.cancelPending();
        mainHandler.removeCallbacksAndMessages(null);
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(deviceListener);
        }
        dismissDialog();
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
        return ScaleTransform.INSTANCE.scale(baseProfile, scalePercent / 100f);
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
    // Menu w grze
    // =====================================================================================

    private void showMenu() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        // Palce schodzą z overlaya na okno dialogu: nic nie może zostać wciśnięte na hoście.
        overlay.releaseAll();
        dismissDialog();

        final List<CharSequence> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        addItem(labels, actions, delays, activity.getString(R.string.thumbcast_menu_disconnect), host::disconnect, 0);
        addItem(labels, actions, delays, activity.getString(overlay.getControlsHidden()
                ? R.string.thumbcast_menu_show_controls : R.string.thumbcast_menu_hide_controls),
                this::toggleControls, 0);
        addItem(labels, actions, delays, activity.getString(R.string.thumbcast_menu_keyboard),
                host::toggleKeyboard, KEYBOARD_AFTER_DIALOG_MS);
        addItem(labels, actions, delays, activity.getString(R.string.thumbcast_menu_control_size, scalePercent),
                this::showScaleDialog, 0);
        addItem(labels, actions, delays, activity.getString(hideOnGamepad
                ? R.string.thumbcast_menu_hide_on_gamepad_on : R.string.thumbcast_menu_hide_on_gamepad_off),
                () -> setHideOnGamepad(!hideOnGamepad), 0);

        AlertDialog d = new AlertDialog.Builder(activity, DIALOG_THEME)
                .setTitle(R.string.thumbcast_menu_title)
                .setItems(labels.toArray(new CharSequence[0]), (di, which) -> {
                    afterDialog = actions.get(which);
                    afterDialogDelayMs = delays.get(which);
                })
                .setNegativeButton(R.string.thumbcast_menu_resume, null)
                .create();
        show(d);
    }

    private static void addItem(List<CharSequence> labels, List<Runnable> actions, List<Long> delays,
                                CharSequence label, Runnable action, long delayMs) {
        labels.add(label);
        actions.add(action);
        delays.add(delayMs);
    }

    private void showScaleDialog() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        final int[] values = ThumbcastSettings.SCALE_PERCENTS;
        CharSequence[] labels = new CharSequence[values.length];
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = activity.getString(R.string.thumbcast_percent, values[i]);
            if (values[i] == scalePercent) {
                checked = i;
            }
        }
        AlertDialog d = new AlertDialog.Builder(activity, DIALOG_THEME)
                .setTitle(R.string.title_thumbcast_control_scale)
                .setSingleChoiceItems(labels, checked, (di, which) -> {
                    final int percent = values[which];
                    afterDialog = () -> applyScale(percent);
                    afterDialogDelayMs = 0;
                    di.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        show(d);
    }

    /**
     * Pokazuje dialog bez zrywania trybu immersive (okno bez fokusu w chwili show() dostaje flagi UI
     * aktywności) i wykonuje wybraną akcję dopiero po zamknięciu, gdy fokus wraca do strumienia.
     */
    @SuppressWarnings("deprecation")
    private void show(AlertDialog d) {
        d.setOnDismissListener(di -> {
            if (dialog == di) {
                dialog = null;
            }
            Runnable action = afterDialog;
            afterDialog = null;
            if (action != null && !destroyed) {
                mainHandler.postDelayed(() -> {
                    if (!destroyed) {
                        action.run();
                    }
                }, afterDialogDelayMs);
            }
        });
        afterDialog = null;
        dialog = d;
        Window w = d.getWindow();
        if (w != null) {
            w.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        d.show();
        if (w != null) {
            w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    /** Zamknięcie bez wykonywania wybranej akcji (PiP, onDestroy, ponowne otwarcie menu). */
    private void dismissDialog() {
        AlertDialog d = dialog;
        dialog = null;
        afterDialog = null;
        if (d != null && d.isShowing()) {
            d.dismiss();
        }
    }
}
