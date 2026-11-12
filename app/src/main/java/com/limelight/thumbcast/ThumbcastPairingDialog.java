package com.limelight.thumbcast;

// NOWY PLIK (moduł :app, pakiet P2, docs/ui-audit.md U10, U18). Dialogi parowania wołane z PcView.doPair (bloki
// THUMBCAST) zamiast toastu „Parowanie…”, upstreamowego dialogu z PIN-em i toastu z błędem:
// - „Łączę się z komputerem…” (postęp) do chwili, gdy PIN jest gotowy;
// - kroki w Sunshine, duży PIN i „Pokaż poradnik” (sekcja „Sparuj”), dialog zostaje otwarty przy przejściu do poradnika;
// - błąd parowania w dialogu z poradnikiem.
// Parowanie (PairingManager) działa jak w upstreamie: dialog tylko pokazuje stan, „Zamknij” go chowa.

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.limelight.R;

import java.lang.ref.WeakReference;

import app.thumbcast.ui.UiContract;

public final class ThumbcastPairingDialog {

    /** Otwarty dialog postępu albo PIN-u (tylko wątek UI). Słaba referencja, żeby nie trzymać aktywności. */
    private static WeakReference<AlertDialog> current = new WeakReference<>(null);

    private ThumbcastPairingDialog() {
    }

    /** Dialog postępu „Łączę się z komputerem…” (dowolny wątek). */
    @SuppressLint("InflateParams") // widok dialogu: rodzica nadaje AlertDialog
    public static void showConnecting(final Activity activity, final String pcName) {
        activity.runOnUiThread(() -> {
            dismissNow();
            if (!usable(activity)) {
                return;
            }
            View content = LayoutInflater.from(activity).inflate(R.layout.thumbcast_pc_progress_dialog, null);
            ((TextView) content.findViewById(R.id.thumbcast_progress_text)).setText(R.string.thumbcast_launcher_pair_connecting);
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.thumbcast_launcher_pair_title, pcName))
                    .setView(content)
                    .setCancelable(false)
                    .create();
            current = new WeakReference<>(dialog);
            dialog.show();
        });
    }

    /** Dialog z krokami i PIN-em (dowolny wątek); zastępuje dialog postępu. */
    @SuppressLint("InflateParams") // widok dialogu: rodzica nadaje AlertDialog
    public static void showPin(final Activity activity, final String pcName, final String pin) {
        activity.runOnUiThread(() -> {
            dismissNow();
            if (!usable(activity)) {
                return;
            }
            View content = LayoutInflater.from(activity).inflate(R.layout.thumbcast_pc_pair_dialog, null);
            TextView pinView = content.findViewById(R.id.thumbcast_pair_pin);
            pinView.setText(pin);
            pinView.setContentDescription(activity.getString(R.string.thumbcast_launcher_pair_pin_description, spaced(pin)));

            final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.thumbcast_launcher_pair_title, pcName))
                    .setView(content)
                    .setCancelable(false)
                    .setPositiveButton(R.string.thumbcast_common_close, null)
                    .setNeutralButton(R.string.thumbcast_launcher_pair_show_guide, null)
                    .create();
            dialog.setOnShowListener(d -> {
                // Poradnik otwiera się nad listą PC, a dialog z PIN-em zostaje (parowanie dalej czeka na kod).
                Button guide = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                guide.setOnClickListener(v -> ThumbcastLauncherUi.openGuide(activity, UiContract.SECTION_PAIR));
            });
            current = new WeakReference<>(dialog);
            dialog.show();
        });
    }

    /** Zamyka dialog postępu lub PIN-u (dowolny wątek). */
    public static void dismiss(Activity activity) {
        activity.runOnUiThread(ThumbcastPairingDialog::dismissNow);
    }

    /** Błąd parowania z przyciskiem do poradnika (dowolny wątek). */
    public static void showFailure(final Activity activity, final String message) {
        activity.runOnUiThread(() -> {
            if (!usable(activity)) {
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.thumbcast_launcher_pair_failed_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.thumbcast_common_ok, null)
                    .setNeutralButton(R.string.thumbcast_launcher_pair_show_guide,
                            (d, w) -> ThumbcastLauncherUi.openGuide(activity, UiContract.SECTION_PAIR))
                    .show();
        });
    }

    private static void dismissNow() {
        AlertDialog dialog = current.get();
        current = new WeakReference<>(null);
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (IllegalArgumentException ignored) {
                // Okno aktywności już odłączone.
            }
        }
    }

    private static boolean usable(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    /** „1234” → „1 2 3 4”, żeby czytnik ekranu czytał cyfry, a nie liczbę. */
    private static String spaced(String pin) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(pin.charAt(i));
        }
        return sb.toString();
    }
}
