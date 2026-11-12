package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Ustawienia Thumbcast dla aplikacji hosta (Sunshine „Applications”): profil sterowania,
// rozdzielczość i FPS dla strumieni jednej aplikacji. Zapis na telefonie, w osobnym pliku SharedPreferences
// (jak upstreamowy „HiddenApps”): klucz = uuid PC (HostAppSettings.hostKey), wartość = JSON z wpisami aplikacji
// (id z listy aplikacji + nazwa jako klucz zapasowy). Logika bez Androida: :control-android, pakiet apps.
//
// Na start strumienia (Game.onCreate, zaraz po readPreferences) applyToStream nadpisuje tylko obiekt
// PreferenceConfiguration tego strumienia: width/height/fps (trafiają do StreamConfiguration i do hosta w /launch)
// i thumbcastAppProfileFile (ThumbcastSession). Preferencje globalne się nie zmieniają.

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.preferences.PreferenceConfiguration;

import app.thumbcast.android.apps.AppStreamOverrides;
import app.thumbcast.android.apps.AppStreamSettings;
import app.thumbcast.android.apps.HostAppSettings;
import app.thumbcast.android.apps.StreamParams;

public final class ThumbcastAppSettings {

    /** Plik SharedPreferences (data/shared_prefs/ThumbcastAppSettings.xml). */
    public static final String PREFS_FILE = "ThumbcastAppSettings";

    private ThumbcastAppSettings() {
    }

    /** Ustawienia wszystkich aplikacji jednego PC (pusta lista, gdy brak albo uszkodzone). */
    public static HostAppSettings load(Context context, String pcUuid) {
        String key = HostAppSettings.hostKey(pcUuid);
        if (key == null) {
            return HostAppSettings.EMPTY;
        }
        return HostAppSettings.decode(prefs(context).getString(key, null));
    }

    /** Ustawienia jednej aplikacji ({@link AppStreamSettings#NONE}, gdy nic nie przypisano). */
    public static AppStreamSettings get(Context context, String pcUuid, int appId, String appName) {
        return load(context, pcUuid).settingsFor(appId, appName);
    }

    /** Zapis; ustawienia puste (wszystko globalne) usuwają aplikację z listy. */
    public static void save(Context context, String pcUuid, int appId, String appName, AppStreamSettings settings) {
        String key = HostAppSettings.hostKey(pcUuid);
        if (key == null) {
            return;
        }
        HostAppSettings updated = load(context, pcUuid).with(appId, appName, settings);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (updated.getEntries().isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, updated.encode());
        }
        editor.apply();
    }

    /**
     * Hak z Game.onCreate (po readPreferences): nakłada ustawienia uruchamianej aplikacji na konfigurację tego
     * strumienia. Rozdzielczość i FPS trafiają do hosta w żądaniu /launch (pole mode); host zmienia ekran tylko
     * wtedy, gdy ma to skonfigurowane (docs/host-apps.md).
     */
    public static void applyToStream(Context context, PreferenceConfiguration prefConfig, Intent intent) {
        int appId = intent.getIntExtra(Game.EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        if (appId == StreamConfiguration.INVALID_APP_ID) {
            return;
        }
        AppStreamSettings settings = get(context, intent.getStringExtra(Game.EXTRA_PC_UUID), appId,
                intent.getStringExtra(Game.EXTRA_APP_NAME));
        if (settings.isEmpty()) {
            return;
        }
        StreamParams global = new StreamParams(prefConfig.width, prefConfig.height, prefConfig.fps);
        StreamParams stream = AppStreamOverrides.applyStream(global, settings);
        prefConfig.width = stream.getWidth();
        prefConfig.height = stream.getHeight();
        prefConfig.fps = stream.getFps();
        prefConfig.thumbcastAppProfileFile = settings.getProfileFile();
        LimeLog.info("Thumbcast: app settings for app " + appId + ": " + global + " -> " + stream
                + ", profile " + settings.getProfileFile());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }
}
