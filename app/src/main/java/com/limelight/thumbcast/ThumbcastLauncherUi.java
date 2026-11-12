package com.limelight.thumbcast;

// NOWY PLIK (moduł :app, pakiet P2 „Ekran startowy”, docs/ui-audit.md U2, U3, U8, U9, U11, U13, U15).
// Logika widoków ekranu startowego wywoływana z bloków THUMBCAST w PcView, AppView, PcGridAdapter, AppGridAdapter,
// AddComputerManually i HelpLauncher, żeby zmiany w plikach upstreamu były jak najmniejsze:
// - poradnik „Jak podłączyć PC” (:control-ui, UiContract.setupGuideIntent) zamiast wiki upstreamu;
// - karta PC: plakietka stanu tekstem (kolor tylko jako drugi sygnał), ikona stanu, przycisk ⋮ = menu kontekstowe;
// - pusty stan listy PC: wskaźnik wyszukiwania, po EMPTY_STATE_DELAY_MS karta „Nie widzę komputera”;
// - menu kontekstowe: zmiana etykiet i kolejności istniejących pozycji (te same identyfikatory, ta sama obsługa).

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.AddComputerManually;

import app.thumbcast.ui.UiContract;

public final class ThumbcastLauncherUi {

    /** Po tylu ms bez żadnego PC lista pokazuje kartę „Nie widzę komputera” (U3). */
    public static final long EMPTY_STATE_DELAY_MS = 5000;

    private ThumbcastLauncherUi() {
    }

    // ---------- poradnik (U2) ----------

    /**
     * Otwiera poradnik „Jak podłączyć PC” z :control-ui na podanej sekcji ({@code UiContract.SECTION_*}, null = od
     * początku). Zwraca false, gdy aktywności nie da się uruchomić (wtedy wołający może użyć zapasowej drogi).
     */
    public static boolean openGuide(Context context, String section) {
        try {
            Intent intent = UiContract.setupGuideIntent(context, section);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            LimeLog.warning("Thumbcast: setup guide failed: " + e);
            return false;
        }
    }

    // ---------- paski ekranów ----------

    /** Przycisk „Wróć” w pasku (AppView, AddComputerManually): zamyka aktywność. */
    public static void bindBackButton(final Activity activity, int viewId) {
        View back = activity.findViewById(viewId);
        if (back != null) {
            back.setOnClickListener(v -> activity.finish());
        }
    }

    /** AddComputerManually (U13): „Wróć” i „Nadal nie widać PC?” (poradnik, sekcja „Nie działa?”). */
    public static void bindAddComputerScreen(final Activity activity) {
        bindBackButton(activity, R.id.thumbcast_add_pc_back);
        View help = activity.findViewById(R.id.thumbcast_add_pc_help);
        if (help != null) {
            help.setOnClickListener(v -> openGuide(activity, UiContract.SECTION_TROUBLESHOOT));
        }
    }

    // ---------- pusty stan listy PC (U3) ----------

    /**
     * Pusty stan listy PC w układzie thumbcast_activity_pc_view: najpierw wskaźnik „Szukam…”, po
     * {@link #EMPTY_STATE_DELAY_MS} od utworzenia ekranu karta z podpowiedziami i przyciskami. Widoczność całego
     * kontenera ({@code no_pc_found_layout}) dalej ustawia kod upstreamu. {@link #bind} woła się po każdym
     * {@code setContentView} (także po obrocie), {@link #release} w {@code onDestroy}.
     */
    public static final class EmptyState {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final long createdAtMs = SystemClock.elapsedRealtime();
        private final Runnable reveal = this::showCard;
        private View searching;
        private View card;

        public void bind(final Activity activity) {
            handler.removeCallbacks(reveal);
            searching = activity.findViewById(R.id.thumbcast_pc_searching);
            card = activity.findViewById(R.id.thumbcast_pc_empty_scroll);
            if (searching == null || card == null) {
                return;
            }
            View guide = activity.findViewById(R.id.thumbcast_pc_empty_guide);
            guide.setOnClickListener(v -> openGuide(activity, UiContract.SECTION_INSTALL));
            View add = activity.findViewById(R.id.thumbcast_pc_empty_add);
            add.setOnClickListener(v -> activity.startActivity(new Intent(activity, AddComputerManually.class)));

            long left = EMPTY_STATE_DELAY_MS - (SystemClock.elapsedRealtime() - createdAtMs);
            if (left <= 0) {
                showCard();
            } else {
                searching.setVisibility(View.VISIBLE);
                card.setVisibility(View.GONE);
                handler.postDelayed(reveal, left);
            }
        }

        private void showCard() {
            if (searching == null || card == null) {
                return;
            }
            searching.setVisibility(View.GONE);
            card.setVisibility(View.VISIBLE);
        }

        public void release() {
            handler.removeCallbacks(reveal);
            searching = null;
            card = null;
        }
    }

    // ---------- karta PC (U9) ----------

    /** Tekst stanu PC jak na plakietce karty (też nagłówek menu PC). */
    public static String statusText(Context context, ComputerDetails details) {
        return context.getString(statusTextRes(details));
    }

    private static int statusTextRes(ComputerDetails d) {
        if (d.state == ComputerDetails.State.OFFLINE) {
            return R.string.thumbcast_launcher_status_offline;
        }
        if (d.state != ComputerDetails.State.ONLINE) {
            return R.string.thumbcast_launcher_status_checking;
        }
        if (d.pairState == PairingManager.PairState.NOT_PAIRED) {
            return R.string.thumbcast_launcher_status_unpaired;
        }
        if (d.runningGameId != 0) {
            return R.string.thumbcast_launcher_status_in_game;
        }
        return R.string.thumbcast_launcher_status_online;
    }

    /**
     * Wypełnia kartę PC (thumbcast_pc_grid_item). Zwraca false, gdy widok nie jest kartą Thumbcast (wtedy
     * PcGridAdapter robi to po swojemu, jak upstream).
     */
    public static boolean populatePcCard(View root, ImageView image, ProgressBar spinner, TextView name,
                                         ImageView overlay, ComputerDetails d) {
        TextView status = root.findViewById(R.id.thumbcast_pc_status);
        if (status == null) {
            return false;
        }
        Context context = root.getContext();

        int badgeBg;
        int badgeColor;
        int overlayIcon = 0;
        int overlayColor = R.color.thumbcast_text_secondary;
        float imageAlpha = 1.0f;
        boolean checking = false;
        if (d.state == ComputerDetails.State.OFFLINE) {
            badgeBg = R.drawable.thumbcast_bg_pill_neutral;
            badgeColor = R.color.thumbcast_text_secondary;
            overlayIcon = R.drawable.thumbcast_ic_wifi_off;
            imageAlpha = 0.5f;
        } else if (d.state != ComputerDetails.State.ONLINE) {
            badgeBg = R.drawable.thumbcast_bg_pill_neutral;
            badgeColor = R.color.thumbcast_text_secondary;
            imageAlpha = 0.5f;
            checking = true;
        } else if (d.pairState == PairingManager.PairState.NOT_PAIRED) {
            badgeBg = R.drawable.thumbcast_bg_pill_warning;
            badgeColor = R.color.thumbcast_warning;
            overlayIcon = R.drawable.thumbcast_ic_lock;
            overlayColor = R.color.thumbcast_warning;
        } else if (d.runningGameId != 0) {
            badgeBg = R.drawable.thumbcast_bg_pill_accent;
            badgeColor = R.color.thumbcast_accent;
            overlayIcon = R.drawable.thumbcast_ic_play_arrow;
            overlayColor = R.color.thumbcast_accent;
        } else {
            badgeBg = R.drawable.thumbcast_bg_pill_success;
            badgeColor = R.color.thumbcast_success;
        }

        String statusText = context.getString(statusTextRes(d));
        image.setImageResource(R.drawable.thumbcast_ic_computer);
        image.setAlpha(imageAlpha);
        name.setText(d.name);
        name.setAlpha(1.0f);
        status.setText(statusText);
        status.setBackgroundResource(badgeBg);
        status.setTextColor(context.getColor(badgeColor));
        if (spinner != null) {
            spinner.setVisibility(checking ? View.VISIBLE : View.GONE);
        }
        if (overlay != null) {
            if (overlayIcon != 0) {
                overlay.setImageResource(overlayIcon);
                overlay.setImageTintList(context.getColorStateList(overlayColor));
                overlay.setAlpha(1.0f);
                overlay.setVisibility(View.VISIBLE);
            } else {
                overlay.setVisibility(View.GONE);
            }
        }
        root.setContentDescription(context.getString(R.string.thumbcast_launcher_card_description, d.name, statusText));
        bindMoreButton(root.findViewById(R.id.thumbcast_pc_more), root,
                context.getString(R.string.thumbcast_launcher_more_for, d.name));
        return true;
    }

    /**
     * Przycisk ⋮ na karcie PC lub kafelku gry: otwiera to samo menu kontekstowe co długie przytrzymanie (siatka
     * tworzy {@code AdapterContextMenuInfo} dla elementu {@code itemRoot}). Bez fokusu, żeby siatka dalej
     * dostawała tap w element i nawigację padem (ImageButton ustawia fokus w konstruktorze, więc także w kodzie).
     */
    public static void bindMoreButton(View more, final View itemRoot, String description) {
        if (more == null) {
            return;
        }
        more.setFocusable(false);
        more.setContentDescription(description);
        more.setTooltipText(description);
        more.setOnClickListener(v -> itemRoot.showContextMenu());
    }

    // ---------- menu kontekstowe (U11, U15) ----------

    /**
     * Przenosi istniejącą pozycję menu (ten sam identyfikator, więc obsługa w onContextItemSelected bez zmian) na
     * pozycję {@code order} w grupie {@code groupId} i zmienia jej etykietę. Pozycji, której nie ma, nie dodaje.
     */
    public static void place(Menu menu, int itemId, int groupId, int order, CharSequence title) {
        MenuItem old = menu.findItem(itemId);
        if (old == null) {
            return;
        }
        boolean checkable = old.isCheckable();
        boolean checked = old.isChecked();
        menu.removeItem(itemId);
        MenuItem item = menu.add(groupId, itemId, order, title);
        item.setCheckable(checkable);
        item.setChecked(checked);
    }

    /** Linie między grupami menu (diagnostyka, usuwanie); API 28+, wcześniej menu bez linii. */
    public static void enableGroupDividers(Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menu.setGroupDividerEnabled(true);
        }
    }
}
