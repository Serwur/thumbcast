package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Cała logika Thumbcast w aktywności Game: Game trzyma jedno pole
// (`thumbcast`, null = tryb legacy) i woła kilka jednolinijkowych haków (patches/Game.java.patch.md).
//
// Sesja odpowiada za: wczytanie profilu (błąd = null, Game tworzy wtedy VirtualController), rozmiar
// kontrolek (ScaleTransform), overlay nad StreamView, prostokąt wideo, HostPadReporterImpl, akcje
// aplikacji (Command: mapa akcja -> Runnable), menu w grze (☰, gest od górnej krawędzi, systemowy Back;
// widok ThumbcastMenuView nad overlayem, pozycje w ThumbcastMenuModel),
// ukrywanie kontrolek przy podłączonym padzie, PiP i sprzątanie.

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;

import java.util.Collections;
import java.util.HashMap;
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
    /** Rodzic StreamView: overlay i menu są jego ostatnimi dziećmi. */
    private final FrameLayout overlayParent;
    /** Tworzony przy pierwszym otwarciu; pokazany = dodany do {@link #overlayParent}. */
    private ThumbcastMenuView menuView;
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
            hideMenu();
        } else {
            showMenu();
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
    }

    /** stopConnection: zerowy stan na hosta przed zamknięciem połączenia. */
    public void onStopConnection() {
        overlay.releaseAll();
    }

    /** onDestroy: bez zaległych retransmisji, listenerów i menu. */
    public void onDestroy() {
        destroyed = true;
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
        overlay.releaseAll();
        if (menuView == null) {
            menuView = new ThumbcastMenuView(activity, menuListener);
        }
        if (menuView.getParent() == null) {
            // Ostatnie dziecko: rysowane nad overlayem i pierwsze w kolejce dotyku.
            overlayParent.addView(menuView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            menuView.scrollToTop();
        }
        refreshMenu();
    }

    /** Zamknięcie bez akcji (Wróć do gry, tap w tło, Back, PiP, onDestroy). */
    private void hideMenu() {
        if (isMenuShown()) {
            overlayParent.removeView(menuView);
        }
    }

    private void refreshMenu() {
        if (isMenuShown()) {
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
    };
}
