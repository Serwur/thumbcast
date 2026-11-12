package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Dialog „Ustawienia Thumbcast dla aplikacji” z menu kontekstowego aplikacji w AppView:
// profil sterowania (biblioteka albo domyślny), rozdzielczość i FPS (te same opcje co w ustawieniach upstreamu albo
// ustawienie globalne). Zapis przez ThumbcastAppSettings; nic nie zmienia ustawień globalnych.

import android.app.Activity;
import android.app.AlertDialog;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

import app.thumbcast.android.apps.AppStreamSettings;
import app.thumbcast.android.apps.StreamResolution;
import app.thumbcast.android.library.ProfileEntry;
import app.thumbcast.android.library.ProfileRepository;

public final class ThumbcastAppSettingsDialog {

    private ThumbcastAppSettingsDialog() {
    }

    /** Pozycja listy: etykieta i wartość (null = ustawienie globalne). */
    private static final class Choice<T> {
        final String label;
        final T value;

        Choice(String label, T value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static void show(final Activity activity, final String pcUuid, final int appId, final String appName) {
        final AppStreamSettings current = ThumbcastAppSettings.get(activity, pcUuid, appId, appName);
        PreferenceConfiguration global = PreferenceConfiguration.readPreferences(activity);

        // ---- profil ----
        ProfileRepository repo = ProfileRepository.create(activity);
        String globalFile = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(ProfileLibraryActivity.PREF_PROFILE, ProfileRepository.DEFAULT_PRESET);
        String globalName = globalFile;
        List<Choice<String>> profiles = new ArrayList<>();
        int profileIndex = 0;
        boolean mappedProfileMissing = current.getProfileFile() != null;
        try {
            ProfileEntry globalEntry = repo.entry(globalFile);
            if (globalEntry != null && globalEntry.isValid()) {
                globalName = globalEntry.getName();
            }
            profiles.add(new Choice<String>(activity.getString(R.string.thumbcast_app_settings_default_profile, globalName), null));
            for (ProfileEntry e : repo.listAll()) {
                if (!e.isValid()) {
                    continue;
                }
                if (e.getFileName().equals(current.getProfileFile())) {
                    profileIndex = profiles.size();
                    mappedProfileMissing = false;
                }
                profiles.add(new Choice<>(e.getName(), e.getFileName()));
            }
        } catch (RuntimeException e) {
            LimeLog.warning("Thumbcast: profile list failed: " + e);
            if (profiles.isEmpty()) {
                profiles.add(new Choice<String>(activity.getString(R.string.thumbcast_app_settings_default_profile, globalName), null));
            }
        }

        // ---- rozdzielczość (wartości jak list_resolution upstreamu) ----
        String[] resNames = activity.getResources().getStringArray(R.array.resolution_names);
        String[] resValues = activity.getResources().getStringArray(R.array.resolution_values);
        List<Choice<StreamResolution>> resolutions = new ArrayList<>();
        resolutions.add(new Choice<StreamResolution>(activity.getString(R.string.thumbcast_app_settings_global_resolution,
                global.width + "x" + global.height), null));
        int resIndex = 0;
        for (int i = 0; i < Math.min(resNames.length, resValues.length); i++) {
            StreamResolution r = StreamResolution.parse(resValues[i]);
            if (r == null) {
                continue;
            }
            if (r.equals(current.getResolution())) {
                resIndex = resolutions.size();
            }
            resolutions.add(new Choice<>(resNames[i] + " (" + r + ")", r));
        }
        if (current.getResolution() != null && resIndex == 0) {
            // Wartość spoza listy (np. zapisana ręcznie): pokazujemy ją, żeby zapis jej nie zgubił.
            resIndex = resolutions.size();
            resolutions.add(new Choice<>(current.getResolution().toString(), current.getResolution()));
        }

        // ---- FPS (wartości jak list_fps upstreamu) ----
        String[] fpsNames = activity.getResources().getStringArray(R.array.fps_names);
        String[] fpsValues = activity.getResources().getStringArray(R.array.fps_values);
        List<Choice<Integer>> fpsChoices = new ArrayList<>();
        fpsChoices.add(new Choice<Integer>(activity.getString(R.string.thumbcast_app_settings_global_fps, global.fps), null));
        int fpsIndex = 0;
        for (int i = 0; i < Math.min(fpsNames.length, fpsValues.length); i++) {
            int fps;
            try {
                fps = Integer.parseInt(fpsValues[i]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (current.getFps() != null && current.getFps() == fps) {
                fpsIndex = fpsChoices.size();
            }
            fpsChoices.add(new Choice<>(fpsNames[i], fps));
        }
        if (current.getFps() != null && fpsIndex == 0) {
            fpsIndex = fpsChoices.size();
            fpsChoices.add(new Choice<>(current.getFps() + " FPS", current.getFps()));
        }

        // ---- widok ----
        int pad = dp(activity, 20);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad / 2, pad, 0);

        if (mappedProfileMissing) {
            addText(activity, column, activity.getString(R.string.thumbcast_app_settings_missing,
                    current.getProfileFile()), true);
        }
        final Spinner profileSpinner = addSpinner(activity, column, R.string.thumbcast_app_settings_profile, profiles, profileIndex);
        final Spinner resSpinner = addSpinner(activity, column, R.string.thumbcast_app_settings_resolution, resolutions, resIndex);
        final Spinner fpsSpinner = addSpinner(activity, column, R.string.thumbcast_app_settings_fps, fpsChoices, fpsIndex);
        addText(activity, column, activity.getString(R.string.thumbcast_app_settings_note), false);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(column);

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.thumbcast_app_settings_title, appName))
                .setView(scroll)
                .setPositiveButton(R.string.thumbcast_app_settings_save, (d, w) -> {
                    AppStreamSettings chosen = new AppStreamSettings(
                            ThumbcastAppSettingsDialog.<String>selected(profileSpinner),
                            ThumbcastAppSettingsDialog.<StreamResolution>selected(resSpinner),
                            ThumbcastAppSettingsDialog.<Integer>selected(fpsSpinner));
                    ThumbcastAppSettings.save(activity, pcUuid, appId, appName, chosen);
                    Toast.makeText(activity, activity.getString(R.string.thumbcast_app_settings_saved, appName),
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.thumbcast_app_settings_reset, (d, w) -> {
                    ThumbcastAppSettings.save(activity, pcUuid, appId, appName, AppStreamSettings.NONE);
                    Toast.makeText(activity, activity.getString(R.string.thumbcast_app_settings_cleared, appName),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("unchecked")
    private static <T> T selected(Spinner spinner) {
        Object item = spinner.getSelectedItem();
        return item instanceof Choice ? ((Choice<T>) item).value : null;
    }

    private static <T> Spinner addSpinner(Activity activity, LinearLayout column, int labelRes,
                                          List<Choice<T>> items, int selection) {
        TextView label = new TextView(activity);
        label.setText(labelRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setPadding(0, dp(activity, 12), 0, 0);
        column.addView(label);

        Spinner spinner = new Spinner(activity);
        ArrayAdapter<Choice<T>> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selection, items.size() - 1)));
        spinner.setMinimumHeight(dp(activity, 48));
        column.addView(spinner);
        return spinner;
    }

    private static void addText(Activity activity, LinearLayout column, String text, boolean warning) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, warning ? 14 : 12);
        view.setPadding(0, dp(activity, 12), 0, warning ? 0 : dp(activity, 8));
        if (!warning) {
            view.setAlpha(0.75f);
        }
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        column.addView(view);
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}
