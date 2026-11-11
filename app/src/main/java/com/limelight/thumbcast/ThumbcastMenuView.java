package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Menu w grze jako widok w oknie aktywności, nie osobne okno (AlertDialog).
// Dialog z FLAG_NOT_FOCUSABLE i skopiowanymi flagami UI aktywności rozjeżdżał na Androidzie 16
// (edge-to-edge, immersive) miejsce rysowania i obszar dotyku: tap w pozycję trafiał w pozycję niżej.
//
// Widok leży w tym samym FrameLayout co StreamView i overlay, jako ostatnie dziecko (na wierzchu,
// pierwszy dostaje dotyk). Przyciemnione tło na cały ekran konsumuje każdy dotyk, więc overlay pod nim
// nic nie dostaje; tap w tło = „Wróć do gry”. Panel boczny (przy krawędzi END) na pełną wysokość,
// przewijany, wiersze min. 52 dp. Żaden element nie bierze fokusu: StreamView go zachowuje
// (klawiatura ekranowa i klawisze trafiają tam jak bez menu).

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tworzony tylko z kodu (ThumbcastSession), nie z XML: jeden konstruktor z listenerem. */
@SuppressLint("ViewConstructor")
public final class ThumbcastMenuView extends FrameLayout {

    interface Listener {
        /** Kliknięta akcja pozycji ({@code item.action}, {@code item.decrease} albo {@code item.increase}). */
        void onMenuAction(ThumbcastMenuModel.Item item, Runnable action);

        /** Tap w przyciemnione tło. */
        void onDismissRequested();
    }

    private static final int SCRIM_COLOR = 0x99000000;
    private static final int PANEL_COLOR = 0xF2202124;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xB3FFFFFF;
    private static final int DESTRUCTIVE_COLOR = 0xFFFF8A80;
    private static final int PRIMARY_COLOR = 0xFF8AB4F8;
    private static final float DISABLED_ALPHA = 0.35f;

    /** Wiersz i przyciski − / +: powyżej minimum 48 dp celu dotyku. */
    private static final int ROW_MIN_HEIGHT_DP = 52;
    private static final int STEP_BUTTON_DP = 56;
    private static final int PANEL_MIN_WIDTH_DP = 280;
    private static final int PANEL_MAX_WIDTH_DP = 420;
    private static final float PANEL_WIDTH_FRACTION = 0.42f;
    /** Pas tła widoczny zawsze obok panelu (wąski ekran w pionie): jest gdzie tapnąć, żeby wrócić. */
    private static final int MIN_SCRIM_DP = 56;
    private static final int PADDING_DP = 8;
    private static final int ROW_PADDING_DP = 20;

    private final Listener listener;
    private final FrameLayout panel;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final float density;
    /** Wiersze pozycji (po tytule); indeks = indeks w {@link #items}. */
    private final List<View> rows = new ArrayList<>();
    private List<ThumbcastMenuModel.Item> items = Collections.emptyList();
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;

    ThumbcastMenuView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.density = getResources().getDisplayMetrics().density;

        setBackgroundColor(SCRIM_COLOR);
        setFocusable(View.NOT_FOCUSABLE);
        setClickable(true);
        setContentDescription(context.getString(R.string.thumbcast_menu_resume));
        setOnClickListener(v -> listener.onDismissRequested());

        panel = new FrameLayout(context);
        panel.setBackgroundColor(PANEL_COLOR);
        // Klikalny panel zjada tapy między wierszami, żeby nie trafiały w tło (zamknięcie).
        panel.setClickable(true);
        panel.setFocusable(View.NOT_FOCUSABLE);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(panel, new LayoutParams(dp(PANEL_MIN_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END));

        scroll = new ScrollView(context);
        scroll.setFocusable(View.NOT_FOCUSABLE);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        panel.addView(scroll, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText(R.string.thumbcast_menu_title);
        title.setTextColor(TITLE_COLOR);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(dp(44));
        title.setPadding(dp(ROW_PADDING_DP), 0, dp(ROW_PADDING_DP), 0);
        list.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        updatePadding();
    }

    /** Pokazuje pozycje; przy tej samej strukturze tylko odświeża wiersze (przewinięcie zostaje). */
    void bind(List<ThumbcastMenuModel.Item> newItems) {
        boolean sameShape = newItems.size() == items.size();
        for (int i = 0; sameShape && i < newItems.size(); i++) {
            sameShape = newItems.get(i).kind == items.get(i).kind;
        }
        items = newItems;
        if (!sameShape) {
            for (View row : rows) {
                list.removeView(row);
            }
            rows.clear();
            for (int i = 0; i < newItems.size(); i++) {
                View row = newItems.get(i).kind == ThumbcastMenuModel.Kind.STEPPER ? createStepperRow(i) : createButtonRow(i);
                rows.add(row);
                list.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }
        for (int i = 0; i < newItems.size(); i++) {
            updateRow(rows.get(i), newItems.get(i));
        }
    }

    /** Na górę listy (każde otwarcie menu zaczyna od pierwszej pozycji). */
    void scrollToTop() {
        scroll.scrollTo(0, 0);
    }

    // =====================================================================================
    // Wiersze
    // =====================================================================================

    private View createButtonRow(final int index) {
        TextView row = newRowLabel();
        row.setClickable(true);
        row.setBackground(themeDrawable(android.R.attr.selectableItemBackground));
        row.setOnClickListener(v -> fire(index, Which.ACTION));
        markAsButton(row);
        return row;
    }

    private View createStepperRow(final int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(0, 0, dp(PADDING_DP), 0);

        TextView label = newRowLabel();
        row.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(newStepButton(R.string.thumbcast_menu_scale_minus, R.string.thumbcast_menu_scale_decrease,
                v -> fire(index, Which.DECREASE)), new LinearLayout.LayoutParams(dp(STEP_BUTTON_DP), dp(STEP_BUTTON_DP)));
        row.addView(newStepButton(R.string.thumbcast_menu_scale_plus, R.string.thumbcast_menu_scale_increase,
                v -> fire(index, Which.INCREASE)), new LinearLayout.LayoutParams(dp(STEP_BUTTON_DP), dp(STEP_BUTTON_DP)));
        return row;
    }

    private void updateRow(View row, ThumbcastMenuModel.Item item) {
        if (item.kind == ThumbcastMenuModel.Kind.STEPPER) {
            LinearLayout r = (LinearLayout) row;
            ((TextView) r.getChildAt(0)).setText(item.label);
            setStepEnabled(r.getChildAt(1), item.decrease != null);
            setStepEnabled(r.getChildAt(2), item.increase != null);
        } else {
            TextView t = (TextView) row;
            t.setText(item.label);
            t.setTextColor(colorFor(item.style));
            t.setTypeface(item.style == ThumbcastMenuModel.Style.PRIMARY ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private enum Which { ACTION, DECREASE, INCREASE }

    private void fire(int index, Which which) {
        if (index >= items.size()) {
            return;
        }
        ThumbcastMenuModel.Item item = items.get(index);
        Runnable action;
        switch (which) {
            case DECREASE:
                action = item.decrease;
                break;
            case INCREASE:
                action = item.increase;
                break;
            default:
                action = item.action;
                break;
        }
        if (action != null) {
            listener.onMenuAction(item, action);
        }
    }

    private TextView newRowLabel() {
        TextView t = new TextView(getContext());
        t.setFocusable(View.NOT_FOCUSABLE);
        t.setTextColor(TEXT_COLOR);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        t.setMinHeight(dp(ROW_MIN_HEIGHT_DP));
        t.setPadding(dp(ROW_PADDING_DP), dp(PADDING_DP), dp(ROW_PADDING_DP), dp(PADDING_DP));
        return t;
    }

    private TextView newStepButton(int textRes, int descriptionRes, View.OnClickListener onClick) {
        TextView b = new TextView(getContext());
        b.setFocusable(View.NOT_FOCUSABLE);
        b.setClickable(true);
        b.setText(textRes);
        b.setContentDescription(getContext().getString(descriptionRes));
        b.setTextColor(TEXT_COLOR);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        b.setGravity(Gravity.CENTER);
        b.setBackground(themeDrawable(android.R.attr.selectableItemBackgroundBorderless));
        b.setOnClickListener(onClick);
        markAsButton(b);
        return b;
    }

    private static void setStepEnabled(View b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    private static int colorFor(ThumbcastMenuModel.Style style) {
        switch (style) {
            case DESTRUCTIVE:
                return DESTRUCTIVE_COLOR;
            case PRIMARY:
                return PRIMARY_COLOR;
            default:
                return TEXT_COLOR;
        }
    }

    /** TalkBack czyta wiersz jako przycisk. */
    private static void markAsButton(View v) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }

    private Drawable themeDrawable(int attr) {
        TypedArray a = getContext().obtainStyledAttributes(new int[]{attr});
        try {
            Drawable d = a.getDrawable(0);
            return d != null ? d : new ColorDrawable(0);
        } finally {
            a.recycle();
        }
    }

    // =====================================================================================
    // Rozmiar panelu i wcięcia (wycięcie ekranu, paski systemowe poza trybem immersive)
    // =====================================================================================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int wanted = Math.round(width * PANEL_WIDTH_FRACTION);
        wanted = Math.max(dp(PANEL_MIN_WIDTH_DP), Math.min(dp(PANEL_MAX_WIDTH_DP), wanted));
        wanted = Math.min(wanted, width - dp(MIN_SCRIM_DP));
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        lp.width = Math.max(0, wanted);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets insets = getRootWindowInsets();
        if (insets != null) {
            applyInsets(insets);
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        applyInsets(insets);
        return insets;
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsets insets) {
        int l;
        int t;
        int r;
        int b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            l = i.left;
            t = i.top;
            r = i.right;
            b = i.bottom;
        } else {
            l = insets.getSystemWindowInsetLeft();
            t = insets.getSystemWindowInsetTop();
            r = insets.getSystemWindowInsetRight();
            b = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                l = Math.max(l, insets.getDisplayCutout().getSafeInsetLeft());
                t = Math.max(t, insets.getDisplayCutout().getSafeInsetTop());
                r = Math.max(r, insets.getDisplayCutout().getSafeInsetRight());
                b = Math.max(b, insets.getDisplayCutout().getSafeInsetBottom());
            }
        }
        if (l != insetLeft || t != insetTop || r != insetRight || b != insetBottom) {
            insetLeft = l;
            insetTop = t;
            insetRight = r;
            insetBottom = b;
            updatePadding();
        }
    }

    /** Treść panelu omija wcięcie po stronie krawędzi ekranu; tło panelu sięga do krawędzi. */
    private void updatePadding() {
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int edge = rtl ? insetLeft : insetRight;
        int base = dp(PADDING_DP);
        if (rtl) {
            scroll.setPadding(base + edge, base + insetTop, base, base + insetBottom);
        } else {
            scroll.setPadding(base, base + insetTop, base + edge, base + insetBottom);
        }
    }

    private int dp(int value) {
        return Math.round(value * density);
    }
}
