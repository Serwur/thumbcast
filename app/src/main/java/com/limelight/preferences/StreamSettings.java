package com.limelight.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
// THUMBCAST:
import android.content.ActivityNotFoundException;
import android.graphics.Insets;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.thumbcast.ProfileLibraryActivity;
import com.limelight.thumbcast.ThumbcastSettings;

import java.util.ArrayList;
import java.util.List;

import app.thumbcast.ui.OnboardingResult;
import app.thumbcast.ui.UiContract;
// THUMBCAST END
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;

import java.lang.reflect.Method;
import java.util.Arrays;

public class StreamSettings extends Activity {
    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    // THUMBCAST: ekran „Zaawansowane” był otwarty, gdy reloadSettings() podmienił fragment (np. po zmianie
    // „Pokaż wszystkie wartości FPS”); nowy fragment otwiera go ponownie.
    boolean thumbcastReopenAdvanced;
    // THUMBCAST END

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);

        UiHelper.notifyNewRootView(this);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false);
        }

        private void addNativeFrameRateEntry(float framerate) {
            int frameRateRounded = Math.round(framerate);
            if (frameRateRounded == 0) {
                return;
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Integer.toString(frameRateRounded);
            String fpsName = getResources().getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        // THUMBCAST: podsumowanie = nazwa profilu domyślnego (odświeżane po powrocie z biblioteki).
        @Override
        public void onResume() {
            super.onResume();
            Preference thumbcastProfile = findPreference("list_thumbcast_profile");
            if (thumbcastProfile != null) {
                thumbcastProfile.setSummary(getString(R.string.thumbcast_profile_summary,
                        ProfileLibraryActivity.defaultProfileName(getActivity())));
            }
        }

        private static final int THUMBCAST_REQ_ONBOARDING = 0x7c01;
        private static final String THUMBCAST_ADVANCED_KEY = "screen_thumbcast_advanced";

        /** Wynik kreatora uruchomionego z Pomoc → „Uruchom konfigurację od nowa” (te same zapisy co hak w PcView). */
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != THUMBCAST_REQ_ONBOARDING || getActivity() == null) {
                return;
            }
            OnboardingResult result = UiContract.parseOnboardingResult(resultCode, data);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putInt(UiContract.PREF_ONBOARDING_VERSION, UiContract.CURRENT_ONBOARDING_VERSION);
            if (result != null) {
                editor.putString(ProfileLibraryActivity.PREF_PROFILE, result.getPresetFile());
            }
            editor.apply();
            if (result == null) {
                return;
            }
            new ThumbcastSettings(getActivity()).setHideOnGamepad(result.isHideOnGamepad());
            Preference hide = findPreference(ThumbcastSettings.KEY_HIDE_ON_GAMEPAD);
            if (hide instanceof CheckBoxPreference) {
                ((CheckBoxPreference) hide).setChecked(result.isHideOnGamepad());
            }
            Toast.makeText(getActivity(), getString(R.string.thumbcast_set_onboarding_saved,
                    ProfileLibraryActivity.defaultProfileName(getActivity())), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            StreamSettings activity = (StreamSettings) getActivity();
            if (activity != null && activity.thumbcastReopenAdvanced) {
                activity.thumbcastReopenAdvanced = false;
                View view = getView();
                if (view != null) {
                    view.post(this::thumbcastOpenAdvanced);
                }
            }
        }

        @Override
        public void onDestroy() {
            // Przed super.onDestroy(): PreferenceManager zamyka wtedy okna zagnieżdżonych ekranów.
            Preference advanced = findPreference(THUMBCAST_ADVANCED_KEY);
            StreamSettings activity = (StreamSettings) getActivity();
            if (activity != null && !activity.isFinishing() && advanced instanceof PreferenceScreen) {
                android.app.Dialog dialog = ((PreferenceScreen) advanced).getDialog();
                activity.thumbcastReopenAdvanced = dialog != null && dialog.isShowing();
            }
            super.onDestroy();
        }

        /** Otwiera „Zaawansowane” tak jak dotknięcie wiersza (PreferenceScreen.onItemClick). */
        private void thumbcastOpenAdvanced() {
            PreferenceScreen root = getPreferenceScreen();
            Preference advanced = findPreference(THUMBCAST_ADVANCED_KEY);
            if (root == null || advanced == null || getActivity() == null) {
                return;
            }
            ListAdapter adapter = root.getRootAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i) == advanced) {
                    root.onItemClick(null, null, i, 0);
                    return;
                }
            }
        }

        /** Pasek z tytułem i strzałką wstecz jako nagłówek listy (bez paska akcji w motywie). */
        private static void thumbcastAddHeader(ListView list, CharSequence title, View.OnClickListener onBack) {
            View header = LayoutInflater.from(list.getContext())
                    .inflate(R.layout.thumbcast_settings_header, list, false);
            ((TextView) header.findViewById(R.id.thumbcast_settings_header_title)).setText(title);
            ImageButton back = header.findViewById(R.id.thumbcast_settings_header_back);
            back.setOnClickListener(onBack);
            list.addHeaderView(header, null, false);
        }

        /**
         * Okno zagnieżdżonego ekranu (PreferenceScreen.showDialog): nagłówek z tytułem i odstępy od pasków
         * systemowych (od Androida 15 okna rysują się pod nimi).
         */
        private static void thumbcastDecorateNestedScreen(PreferenceScreen screen) {
            final android.app.Dialog dialog = screen.getDialog();
            if (dialog == null) {
                return;
            }
            ListView list = dialog.findViewById(android.R.id.list);
            if (list != null && list.getHeaderViewsCount() == 0) {
                thumbcastAddHeader(list, screen.getTitle(), v -> dialog.dismiss());
            }
            View content = dialog.findViewById(android.R.id.content);
            if (content != null) {
                content.setOnApplyWindowInsetsListener((v, insets) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout());
                        v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else {
                        v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                    }
                    return insets;
                });
                content.requestApplyInsets();
            }
        }

        private void thumbcastStart(Intent intent, int requestCode) {
            try {
                if (requestCode != 0) {
                    startActivityForResult(intent, requestCode);
                } else {
                    startActivity(intent);
                }
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.thumbcast_set_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        }
        // THUMBCAST END

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            // THUMBCAST: tytuł ekranu i strzałka wstecz (ta sama ścieżka co systemowy Back).
            ListView list = view.findViewById(android.R.id.list);
            if (list != null) {
                thumbcastAddHeader(list, getString(R.string.thumbcast_label_settings), v -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                });
            }
            // THUMBCAST END
            return view;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // THUMBCAST: własny układ (docs/ui-audit.md, D3; te same klucze), było R.xml.preferences.
            addPreferencesFromResource(R.xml.thumbcast_preferences);
            // THUMBCAST END
            PreferenceScreen screen = getPreferenceScreen();

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_onscreen_controls");
                screen.removePreference(category);
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // Hide USB driver options on devices without USB host support
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getActivity().getPackageManager().hasSystemFeature("android.software.picture_in_picture") ||
                    getActivity().getPackageManager().hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = (PreferenceCategory) findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl() ) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
            }

            // THUMBCAST: ustawienia OSC legacy mają sens tylko w trybie legacy, profil tylko w trybie thumbcast.
            // Układ z thumbcast_preferences.xml: stare przyciski ekranowe stoją w category_onscreen_controls
            // (w „Zaawansowane”) za list_control_layer i checkbox_thumbcast_debug i są widoczne tylko w trybie legacy;
            // checkbox_vibrate_osc jest w „Sterowanie” (category_thumbcast). Upstreamowe usuwanie powyżej szuka ich
            // w starych miejscach (screen / kategoria OSC) i tutaj nic nie robi, więc te przypadki obsługujemy sami.
            final ListPreference controlLayer = (ListPreference) findPreference("list_control_layer");
            final Preference thumbcastProfile = findPreference("list_thumbcast_profile");
            final Preference oscPref = findPreference("checkbox_show_onscreen_controls");
            Preference vibrateOsc = findPreference("checkbox_vibrate_osc");
            final PreferenceCategory oscCategory = (PreferenceCategory) findPreference("category_onscreen_controls");
            final List<Preference> legacyOscPrefs = new ArrayList<>();
            if (oscCategory != null) {
                for (int i = 0; i < oscCategory.getPreferenceCount(); i++) {
                    Preference p = oscCategory.getPreference(i);
                    if (!"list_control_layer".equals(p.getKey()) && !"checkbox_thumbcast_debug".equals(p.getKey())) {
                        legacyOscPrefs.add(p);
                    }
                }
            }
            // Bez ekranu dotykowego (upstream usuwał całą kategorię OSC): stare przyciski znikają na stałe.
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                for (int i = legacyOscPrefs.size() - 1; i >= 0; i--) {
                    oscCategory.removePreference(legacyOscPrefs.get(i));
                }
                legacyOscPrefs.clear();
            }
            // Bez wibracji (upstream usuwał checkbox_vibrate_osc z kategorii OSC).
            if (vibrateOsc != null &&
                    !((Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                PreferenceCategory controls = (PreferenceCategory) findPreference("category_thumbcast");
                vibrateOsc.setDependency(null);
                if (controls != null) {
                    controls.removePreference(vibrateOsc);
                }
                vibrateOsc = null;
            }
            final Preference vibrateOscPref = vibrateOsc;
            final boolean legacyOscAvailable = !legacyOscPrefs.isEmpty();
            // Kolejność: dodawanie od początku (najpierw checkbox_show_onscreen_controls, od którego zależą pozostałe),
            // usuwanie od końca (zależne przed nim), żeby zależności rejestrowały się i wyrejestrowywały poprawnie.
            final boolean[] legacyOscShown = {true};
            final java.util.function.Consumer<Boolean> showLegacyOsc = show -> {
                if (!legacyOscAvailable || legacyOscShown[0] == show) {
                    return;
                }
                if (show) {
                    for (Preference p : legacyOscPrefs) {
                        oscCategory.addPreference(p);
                    }
                } else {
                    for (int i = legacyOscPrefs.size() - 1; i >= 0; i--) {
                        oscCategory.removePreference(legacyOscPrefs.get(i));
                    }
                }
                legacyOscShown[0] = show;
            };
            if (controlLayer != null) {
                Preference.OnPreferenceChangeListener layerListener = (pref, value) -> {
                    boolean tc = "thumbcast".equals(value);
                    if (!tc) showLegacyOsc.accept(true);
                    if (thumbcastProfile != null) thumbcastProfile.setEnabled(tc);
                    if (oscPref != null) oscPref.setEnabled(!tc);
                    // Wibracja telefonu przy rumble (vibrateOsc) działa też w trybie Thumbcast, więc nie może zależeć
                    // od checkbox_show_onscreen_controls (w tym trybie wyszarzonego i zwykle równego false).
                    if (vibrateOscPref != null) {
                        vibrateOscPref.setDependency(tc || !legacyOscAvailable ? null : "checkbox_show_onscreen_controls");
                        // setDependency(null) tylko wyrejestrowuje zależność i nie zeruje flagi "zależność niespełniona"
                        // ustawionej chwilę wcześniej przez oscPref.setEnabled(false), więc odblokowujemy ręcznie
                        // (błąd znaleziony w smoke teście na emulatorze 11.11.2026).
                        if ((tc || !legacyOscAvailable) && oscPref != null) vibrateOscPref.onDependencyChanged(oscPref, false);
                    }
                    if (tc) showLegacyOsc.accept(false);
                    return true;
                };
                layerListener.onPreferenceChange(controlLayer, controlLayer.getValue());
                controlLayer.setOnPreferenceChangeListener(layerListener);
            }
            if (thumbcastProfile != null) {
                // Wybór profilu w bibliotece (ta sama preferencja, wartość = nazwa pliku).
                thumbcastProfile.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), ProfileLibraryActivity.class));
                    return true;
                });
            }
            // Pomoc: poradnik (:control-ui, UiContract) i ponowny kreator (wynik w onActivityResult).
            Preference guide = findPreference("thumbcast_help_setup_guide");
            if (guide != null) {
                guide.setOnPreferenceClickListener(pref -> {
                    thumbcastStart(UiContract.setupGuideIntent(getActivity(), null), 0);
                    return true;
                });
            }
            Preference troubleshoot = findPreference("thumbcast_help_troubleshoot");
            if (troubleshoot != null) {
                troubleshoot.setOnPreferenceClickListener(pref -> {
                    thumbcastStart(UiContract.setupGuideIntent(getActivity(), UiContract.SECTION_TROUBLESHOOT), 0);
                    return true;
                });
            }
            Preference onboarding = findPreference("thumbcast_help_onboarding");
            if (onboarding != null) {
                onboarding.setOnPreferenceClickListener(pref -> {
                    thumbcastStart(UiContract.onboardingIntent(getActivity()), THUMBCAST_REQ_ONBOARDING);
                    return true;
                });
            }
            // „Zaawansowane”: okno zagnieżdżonego ekranu dostaje nagłówek i odstępy od pasków systemowych.
            // PreferenceScreen najpierw pokazuje okno (onClick), potem woła ten listener; false = dalej domyślnie.
            Preference advanced = findPreference(THUMBCAST_ADVANCED_KEY);
            if (advanced instanceof PreferenceScreen) {
                advanced.setOnPreferenceClickListener(pref -> {
                    thumbcastDecorateNestedScreen((PreferenceScreen) pref);
                    return false;
                });
            }
            // THUMBCAST END

            Display display = getActivity().getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    final Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Ensure the activity is still open when this timeout expires
                            StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                            if (settingsActivity != null) {
                                settingsActivity.reloadSettings();
                            }
                        }
                    }, 500);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_advanced_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });
        }
    }
}
