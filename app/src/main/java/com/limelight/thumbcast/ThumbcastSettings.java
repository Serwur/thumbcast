package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Ustawienia Thumbcast zmieniane także z menu w grze, więc mają własny
// odczyt/zapis zamiast pól w upstreamowym PreferenceConfiguration (ten czyta wszystko raz, przy starcie).
// Klucze są te same, co w kategorii „Sterowanie dotykowe” w preferences.xml.

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class ThumbcastSettings {

    /** ListPreference: wartości tekstowe "60" … "130" (procent rozmiaru kontrolek). */
    public static final String KEY_CONTROL_SCALE = "list_thumbcast_control_scale";
    public static final String KEY_HIDE_ON_GAMEPAD = "checkbox_thumbcast_hide_on_gamepad";

    /** Telefon: przyciski i gałka z presetów są za duże przy 100% (test właściciela 11.11.2026). */
    public static final int DEFAULT_SCALE_PERCENT = 85;
    public static final boolean DEFAULT_HIDE_ON_GAMEPAD = true;

    /** Wartości z menu w grze i z ekranu ustawień (thumbcast_control_scale_values). */
    static final int[] SCALE_PERCENTS = {60, 70, 85, 100, 115, 130};

    private final SharedPreferences prefs;

    public ThumbcastSettings(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
}
