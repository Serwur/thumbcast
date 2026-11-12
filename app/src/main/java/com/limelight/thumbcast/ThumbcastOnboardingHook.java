package com.limelight.thumbcast;

// NOWY PLIK (moduł :app, pakiet P2, docs/ui-audit.md U1 i D8). Hak kreatora pierwszego uruchomienia w PcView:
// - onCreate: gdy zapisana wersja kreatora jest starsza od bieżącej, sprawdza (poza wątkiem UI), czy w bazie są PC.
//   Pusta baza → kreator (:control-ui) na wynik; PC są (aktualizacja aplikacji) → tylko zapis wersji.
// - wynik: preset jako profil domyślny (list_thumbcast_profile, ten sam zapis co „Ustaw jako domyślny” w bibliotece
//   profili), „Chowaj przyciski, gdy gram padem” (checkbox_thumbcast_hide_on_gamepad) i wersja kreatora (także po
//   „Pomiń”). Kreator sam niczego nie zapisuje (UiContract).
// applyResult jest publiczne dla ponownego uruchomienia kreatora z Ustawień (ta sama obsługa wyniku).

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.limelight.LimeLog;

import java.io.File;

import app.thumbcast.android.library.ProfileRepository;
import app.thumbcast.ui.OnboardingResult;
import app.thumbcast.ui.UiContract;

public final class ThumbcastOnboardingHook {

    /** Kod żądania kreatora w PcView (PcView nie używa innych startActivityForResult). */
    public static final int REQUEST_ONBOARDING = 0x5443;

    // Baza PC upstreamu (ComputerDatabaseManager, stałe prywatne): tylko odczyt liczby wierszy.
    private static final String COMPUTER_DB_NAME = "computers4.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";

    private ThumbcastOnboardingHook() {
    }

    /**
     * Woła PcView.onCreate. Po odtworzeniu aktywności ({@code savedInstanceState != null}) nic nie robi: kreator, jeśli
     * był otwarty, dalej leży na stosie, a jego wynik trafi do {@link #onActivityResult}.
     */
    public static void onCreate(final Activity activity, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final int stored = prefs.getInt(UiContract.PREF_ONBOARDING_VERSION, 0);
        if (!UiContract.shouldShowOnboarding(stored, false)) {
            return;
        }
        final Context appContext = activity.getApplicationContext();
        Thread check = new Thread(() -> {
            final boolean hasPcs = hasSavedComputers(appContext);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (!UiContract.shouldShowOnboarding(stored, hasPcs)) {
                    storeVersion(prefs);
                    return;
                }
                try {
                    activity.startActivityForResult(UiContract.onboardingIntent(activity), REQUEST_ONBOARDING);
                } catch (ActivityNotFoundException e) {
                    LimeLog.warning("Thumbcast: onboarding unavailable: " + e);
                    storeVersion(prefs);
                }
            });
        });
        check.setName("Thumbcast onboarding check");
        check.start();
    }

    /** Woła PcView.onActivityResult; true = wynik kreatora obsłużony. */
    public static boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_ONBOARDING) {
            return false;
        }
        applyResult(activity, resultCode, data);
        return true;
    }

    /**
     * Zapisuje wybór z kreatora (przy RESULT_OK z poprawnym presetem) i bieżącą wersję kreatora (zawsze).
     * Preset, którego nie ma w bibliotece, jest pomijany: zostaje dotychczasowy profil domyślny.
     */
    public static void applyResult(Context context, int resultCode, Intent data) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        OnboardingResult result = UiContract.parseOnboardingResult(resultCode, data);
        if (result != null) {
            if (ProfileRepository.create(context).exists(result.getPresetFile())) {
                editor.putString(ProfileLibraryActivity.PREF_PROFILE, result.getPresetFile());
            } else {
                LimeLog.warning("Thumbcast: onboarding preset missing: " + result.getPresetFile());
            }
            editor.putBoolean(ThumbcastSettings.KEY_HIDE_ON_GAMEPAD, result.isHideOnGamepad());
        }
        editor.putInt(UiContract.PREF_ONBOARDING_VERSION, UiContract.CURRENT_ONBOARDING_VERSION);
        editor.apply();
    }

    private static void storeVersion(SharedPreferences prefs) {
        prefs.edit().putInt(UiContract.PREF_ONBOARDING_VERSION, UiContract.CURRENT_ONBOARDING_VERSION).apply();
    }

    /**
     * Czy w bazie PC jest choć jeden wpis. Tylko odczyt, bez tworzenia bazy (robi to ComputerManagerService). Brak
     * pliku albo tabeli = brak PC. Błąd odczytu (np. baza chwilowo zablokowana) też = brak PC: najwyżej kreator
     * pokaże się raz osobie, która ma już PC, i da się go pominąć.
     */
    static boolean hasSavedComputers(Context context) {
        File db = context.getDatabasePath(COMPUTER_DB_NAME);
        if (!db.isFile()) {
            return false;
        }
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + COMPUTER_TABLE_NAME, null)) {
            return cursor.moveToFirst() && cursor.getLong(0) > 0;
        } catch (RuntimeException e) {
            LimeLog.warning("Thumbcast: computer database check failed: " + e);
            return false;
        }
    }
}
