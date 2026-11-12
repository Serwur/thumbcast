package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Ustawienia Thumbcast zmieniane także z menu w grze, więc mają własny
// odczyt/zapis zamiast pól w upstreamowym PreferenceConfiguration (ten czyta wszystko raz, przy starcie).
// Klucze są te same, co w kategorii „Sterowanie dotykowe” w preferences.xml.

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import app.thumbcast.android.stats.StatsToggles;

public final class ThumbcastSettings {

    /** ListPreference: wartości tekstowe "60" … "130" (procent rozmiaru kontrolek). */
    public static final String KEY_CONTROL_SCALE = "list_thumbcast_control_scale";
    public static final String KEY_HIDE_ON_GAMEPAD = "checkbox_thumbcast_hide_on_gamepad";

    /**
     * 100% = rozmiar z presetu. Od 11.11.2026 presety mają rozmiar, który właściciel uznał za normę
     * na telefonie (wcześniejsze 85%), więc domyślnie nic nie jest skalowane.
     */
    public static final int DEFAULT_SCALE_PERCENT = 100;
    public static final boolean DEFAULT_HIDE_ON_GAMEPAD = true;

    /** Wartości z menu w grze i z ekranu ustawień (thumbcast_control_scale_values). */
    static final int[] SCALE_PERCENTS = {60, 70, 85, 100, 115, 130};

    /** Jednorazowa migracja: zapisane 85% (stara norma) to po zmniejszeniu presetów 100%. */
    private static final String KEY_SCALE_REBASED = "thumbcast_control_scale_rebased";
    private static final String OLD_NORM_PERCENT = "85";

    private final SharedPreferences prefs;

    public ThumbcastSettings(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        migrateScale();
    }

    private void migrateScale() {
        if (prefs.getBoolean(KEY_SCALE_REBASED, false)) {
            return;
        }
        SharedPreferences.Editor e = prefs.edit().putBoolean(KEY_SCALE_REBASED, true);
        if (OLD_NORM_PERCENT.equals(prefs.getString(KEY_CONTROL_SCALE, null))) {
            e.putString(KEY_CONTROL_SCALE, String.valueOf(DEFAULT_SCALE_PERCENT));
        }
        e.apply();
    }

    public int getControlScalePercent() {
        String raw = prefs.getString(KEY_CONTROL_SCALE, null);
        if (raw == null) {
            return DEFAULT_SCALE_PERCENT;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < SCALE_PERCENTS[0] || v > SCALE_PERCENTS[SCALE_PERCENTS.length - 1]) {
                return DEFAULT_SCALE_PERCENT;
            }
            return v;
        } catch (NumberFormatException e) {
            return DEFAULT_SCALE_PERCENT;
        }
    }

    public float getControlScale() {
        return getControlScalePercent() / 100f;
    }

    public void setControlScalePercent(int percent) {
        prefs.edit().putString(KEY_CONTROL_SCALE, String.valueOf(percent)).apply();
    }

    public boolean isHideOnGamepad() {
        return prefs.getBoolean(KEY_HIDE_ON_GAMEPAD, DEFAULT_HIDE_ON_GAMEPAD);
    }

    public void setHideOnGamepad(boolean hide) {
        prefs.edit().putBoolean(KEY_HIDE_ON_GAMEPAD, hide).apply();
    }

    // ---------- Nakładka statystyk (12.11.2026): menu w grze, StatsToggles ----------

    /**
     * „Statystyki” = upstreamowy klucz „Pokaż statystyki wydajności” (PreferenceConfiguration.enablePerfOverlay),
     * więc pole wyboru w ustawieniach i przełącznik w menu w grze to ten sam stan (wł. = linia podstawowa).
     */
    public static final String KEY_PERF_OVERLAY = "checkbox_enable_perf_overlay";
    /** „Szczegółowe statystyki” (tylko tryb Thumbcast), domyślnie wył. */
    public static final String KEY_PERF_OVERLAY_ADVANCED = "checkbox_thumbcast_perf_advanced";

    public StatsToggles getStatsToggles() {
        return new StatsToggles(prefs.getBoolean(KEY_PERF_OVERLAY, false),
                prefs.getBoolean(KEY_PERF_OVERLAY_ADVANCED, false));
    }

    public void setStatsToggles(StatsToggles toggles) {
        prefs.edit().putBoolean(KEY_PERF_OVERLAY, toggles.getEnabled())
                .putBoolean(KEY_PERF_OVERLAY_ADVANCED, toggles.getAdvanced()).apply();
    }

    // ---------- Gesty myszy (12.11.2026): przełącznik na profil, MouseGesturesTransform ----------

    /**
     * Klucz = prefiks + nazwa pliku profilu (wartość list_thumbcast_profile), więc działa także dla presetów
     * tylko do odczytu. Brak wpisu = zachowanie zapisane w profilu (MouseGesturesTransform.isEnabled).
     */
    static final String KEY_MOUSE_GESTURES_PREFIX = "thumbcast_mouse_gestures:";

    /** Zapisany wybór dla pliku profilu albo null (profil działa tak, jak go zapisano). */
    public Boolean getMouseGestures(String profileFile) {
        String key = KEY_MOUSE_GESTURES_PREFIX + profileFile;
        if (profileFile == null || !prefs.contains(key)) {
            return null;
        }
        return prefs.getBoolean(key, false);
    }

    /** {@code value} = MouseGesturesTransform.toStore(...): null usuwa wpis (wartość równa domyślnej profilu). */
    public void setMouseGestures(String profileFile, Boolean value) {
        if (profileFile == null) {
            return;
        }
        String key = KEY_MOUSE_GESTURES_PREFIX + profileFile;
        if (value == null) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    /** Przeniesienie wyboru na kopię profilu (Duplikuj, edycja presetu). */
    public void copyMouseGestures(String fromFile, String toFile) {
        setMouseGestures(toFile, getMouseGestures(fromFile));
    }
}
