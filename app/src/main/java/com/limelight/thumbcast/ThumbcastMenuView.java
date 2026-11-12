package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Menu w grze jako widok w oknie aktywności, nie osobne okno (AlertDialog).
// Dialog z FLAG_NOT_FOCUSABLE i skopiowanymi flagami UI aktywności rozjeżdżał na Androidzie 16
// (edge-to-edge, immersive) miejsce rysowania i obszar dotyku: tap w pozycję trafiał w pozycję niżej.
//
// Widok leży w tym samym FrameLayout co StreamView i overlay, jako ostatnie dziecko (na wierzchu,
// pierwszy dostaje dotyk). Przyciemnione tło (scrim) na cały ekran konsumuje każdy dotyk, więc overlay pod nim
// nic nie dostaje; tap w tło = „Wróć do gry”. Żaden element nie bierze fokusu: StreamView go zachowuje
// (klawiatura ekranowa i klawisze trafiają tam jak bez menu).
//
// Wygląd od 12.11.2026 (przebudowa UI, pakiet P4, docs/ui-audit.md G1–G17): panel boczny przy krawędzi END
// na pełną wysokość, rogi 28 dp od strony wideo, wjazd 150 ms, kolory z palety (thumbcast_colors.xml).
// Stały nagłówek (tytuł gry albo „Menu” i ikona zamknięcia; na podstronie strzałka wstecz), przewijana treść
// (wiersze z ThumbcastMenuModel: ikona 24 dp, tytuł 16 sp, podpis 14 sp, min. 56 dp, nagłówki sekcji 12 sp
// wersalikami, linie 1 dp; przełączniki, segmenty i stepper rysowane jako kontrolki) i stopka z podpowiedzią
// klawiszy pada („A Wybierz · B Wstecz”), widoczna dopiero po pierwszym poleceniu z klawiatury lub pada.
//
// Nawigacja klawiaturą i padem: klawisze i osie odbiera Game (bramka w ThumbcastSession, MenuInputRouter),
// a widok dostaje gotowe polecenia w handleCommand: góra/dół przesuwa zaznaczenie (z zawijaniem; obramowanie,
// bez fokusu Androida), lewo/prawo zmienia wartość wiersza (stepper, segmenty, przełącznik) albo przechodzi
// między kaflami, A/Enter uruchamia wiersz. Zaznaczenie pojawia się dopiero po pierwszym poleceniu.

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import app.thumbcast.android.menu.MenuCommand;
import app.thumbcast.android.menu.MenuFocus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Tworzony tylko z kodu (ThumbcastSession), nie z XML: jeden konstruktor z listenerem. */
@SuppressLint("ViewConstructor")
public final class ThumbcastMenuView extends FrameLayout {

    interface Listener {
        /** Kliknięta akcja pozycji ({@code item.action}, krok steppera albo wybór segmentu). */
        void onMenuAction(ThumbcastMenuModel.Item item, Runnable action);

        /** Tap w przyciemnione tło albo ikona zamknięcia. */
        void onDismissRequested();

        /** Strzałka wstecz w nagłówku podstrony (jak B / Back). */
        void onBackRequested();
    }

    private static final float DISABLED_ALPHA = 0.38f;
    private static final int SLIDE_MS = 150;

    private static final int PANEL_MIN_WIDTH_DP = 300;
    private static final int PANEL_MAX_WIDTH_DP = 420;
    private static final float PANEL_WIDTH_FRACTION = 0.42f;
    /** Pas tła widoczny zawsze obok panelu (wąski ekran w pionie): jest gdzie tapnąć, żeby wrócić. */
    private static final int MIN_SCRIM_DP = 56;
    private static final int CORNER_DP = 28;
    private static final int GUTTER_DP = 8;
    private static final int ROW_PAD_DP = 16;
    private static final int ROW_MIN_DP = 56;
    private static final int BUTTON_HEIGHT_DP = 52;
    private static final int TOUCH_DP = 48;

    private final Listener listener;
    private final float density;
    private final FrameLayout panel;
    private final GradientDrawable panelBackground;
    private final LinearLayout header;
    private final ImageView backButton;
    private final TextView title;
    private final ImageView closeButton;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final View footerDivider;
    private final LinearLayout footer;

    private final int colorText;
    private final int colorTextSecondary;
    private final int colorAccent;
    private final int colorAccentDim;
    private final int colorOnAccent;
    private final int colorError;
    private final int colorErrorContainer;
    private final int colorDivider;
    private final int colorOutline;
    private final int colorSurfaceRaised;

    /** Wiersze pozycji; indeks = indeks w {@link #items}. */
    private final List<Row> rows = new ArrayList<>();
    private List<ThumbcastMenuModel.Item> items = Collections.emptyList();
    private String shapeKey = "";
    /** Wiersz zaznaczony klawiaturą lub padem; -1 = brak (menu obsługiwane dotykiem). */
    private int selected = -1;
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;
    private ValueAnimator slide;

    ThumbcastMenuView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.density = getResources().getDisplayMetrics().density;
        colorText = context.getColor(R.color.thumbcast_text);
        colorTextSecondary = context.getColor(R.color.thumbcast_text_secondary);
        colorAccent = context.getColor(R.color.thumbcast_accent);
        colorAccentDim = context.getColor(R.color.thumbcast_accent_dim);
        colorOnAccent = context.getColor(R.color.thumbcast_on_accent);
        colorError = context.getColor(R.color.thumbcast_error);
        colorErrorContainer = context.getColor(R.color.thumbcast_error_container);
        colorDivider = context.getColor(R.color.thumbcast_divider);
        colorOutline = context.getColor(R.color.thumbcast_outline_strong);
        colorSurfaceRaised = context.getColor(R.color.thumbcast_surface_raised);

        setBackgroundColor(context.getColor(R.color.thumbcast_scrim));
        setFocusable(View.NOT_FOCUSABLE);
        setClickable(true);
        setContentDescription(context.getString(R.string.thumbcast_game_resume));
        setOnClickListener(v -> listener.onDismissRequested());

        panel = new FrameLayout(context);
        panelBackground = new GradientDrawable();
        panelBackground.setColor(context.getColor(R.color.thumbcast_menu_surface));
        panel.setBackground(panelBackground);
        panel.setClipToOutline(true);
        // Klikalny panel zjada tapy między wierszami, żeby nie trafiały w tło (zamknięcie).
        panel.setClickable(true);
        panel.setFocusable(View.NOT_FOCUSABLE);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(panel, new LayoutParams(dp(PANEL_MIN_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        panel.addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Nagłówek (stały, poza przewijaniem).
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(64));
        backButton = newIconButton(R.drawable.thumbcast_ic_arrow_back,
                context.getString(R.string.thumbcast_common_navigate_up), v -> listener.onBackRequested());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        title = new TextView(context);
        title.setTextColor(colorText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_title_large));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            title.setAccessibilityHeading(true);
        }
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(8));
        header.addView(title, titleLp);
        closeButton = newIconButton(R.drawable.thumbcast_ic_close,
                context.getString(R.string.thumbcast_game_menu_close), v -> listener.onDismissRequested());
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        column.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(context);
        scroll.setFocusable(View.NOT_FOCUSABLE);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        column.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Stopka z klawiszami pada (G13): tylko w trybie zaznaczenia.
        footerDivider = newDivider();
        footerDivider.setVisibility(View.GONE);
        column.addView(footerDivider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));
        footer = buildFooter(context);
        footer.setVisibility(View.GONE);
        column.addView(footer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        updateCorners();
        updatePadding();
    }

    // =====================================================================================
    // API dla sesji
    // =====================================================================================

    /** Pokazuje stronę; przy tej samej strukturze tylko odświeża wiersze (przewinięcie i zaznaczenie zostają). */
    void bind(ThumbcastMenuModel.Page page) {
        title.setText(page.title);
        backButton.setVisibility(page.subPage ? View.VISIBLE : View.GONE);
        closeButton.setVisibility(page.subPage ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) title.getLayoutParams();
        titleLp.setMarginStart(page.subPage ? dp(4) : dp(ROW_PAD_DP));
        title.setLayoutParams(titleLp);

        String key = shapeKey(page);
        items = page.items;
        if (!key.equals(shapeKey)) {
            shapeKey = key;
            rebuild(page);
            // Nowa strona: zaznaczenie (jeśli było) wraca na wiersz domyślny i jest przewijane do widoku.
            if (selected >= 0) {
                select(defaultIndex());
            }
        }
        if (selected >= items.size()) {
            selected = items.isEmpty() ? -1 : items.size() - 1;
        }
        for (int i = 0; i < items.size(); i++) {
            rows.get(i).update(items.get(i));
        }
        updateSelection();
    }

    /** Na górę listy (każde otwarcie menu i każda nowa strona zaczyna od góry). */
    void scrollToTop() {
        scroll.scrollTo(0, 0);
    }

    /** Bez zaznaczenia (nowe otwarcie menu). */
    void clearSelection() {
        selected = -1;
        updateSelection();
    }

    /** Wjazd panelu od krawędzi END (150 ms) i rozjaśnienie tła; wołane po dodaniu widoku. */
    void animateIn() {
        if (slide != null) {
            slide.cancel();
        }
        final boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final float distance = dp(PANEL_MAX_WIDTH_DP);
        slide = ValueAnimator.ofFloat(0f, 1f);
        slide.setDuration(SLIDE_MS);
        slide.setInterpolator(new DecelerateInterpolator());
        slide.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float w = panel.getWidth() > 0 ? panel.getWidth() : distance;
            panel.setTranslationX((rtl ? -w : w) * (1f - f));
            setAlpha(0.4f + 0.6f * f);
        });
        panel.setTranslationX(rtl ? -distance : distance);
        setAlpha(0.4f);
        slide.start();
    }

    /** Przy zdjęciu menu: bez trwającej animacji (następne otwarcie zaczyna od zera). */
    void cancelAnimation() {
        if (slide != null) {
            slide.cancel();
            slide = null;
        }
        panel.setTranslationX(0f);
        setAlpha(1f);
    }

    /**
     * Polecenie z klawiatury lub pada (BACK obsługuje sesja). Pierwsze polecenie kierunku tylko pokazuje
     * zaznaczenie na wierszu domyślnym (Wróć do gry, aktywny profil, Anuluj), A/Enter bez zaznaczenia też.
     */
    void handleCommand(MenuCommand command) {
        if (items.isEmpty()) {
            return;
        }
        boolean fresh = selected < 0 || selected >= items.size();
        if (fresh && command != MenuCommand.NONE && command != MenuCommand.BACK) {
            select(defaultIndex());
            return;
        }
        ThumbcastMenuModel.Item item = fresh ? null : items.get(selected);
        switch (command) {
            case UP:
                select(MenuFocus.move(selected, -1, items.size(), defaultIndex()));
                break;
            case DOWN:
                select(MenuFocus.move(selected, 1, items.size(), defaultIndex()));
                break;
            case LEFT:
            case RIGHT:
                if (item != null) {
                    onHorizontal(item, command == MenuCommand.LEFT ? -1 : 1);
                }
                break;
            case ACTIVATE:
                if (item != null) {
                    onActivate(item);
                }
                break;
            default:
                break;
        }
    }

    private void onHorizontal(ThumbcastMenuModel.Item item, int delta) {
        switch (item.kind) {
            case STEPPER:
                fire(item, delta < 0 ? item.decrease : item.increase);
                break;
            case SEGMENTED: {
                int next = MenuFocus.step(item.selectedOption, delta, item.options.length);
                if (next != item.selectedOption && next >= 0) {
                    fireOption(item, next);
                }
                break;
            }
            case SWITCH:
                if ((delta < 0) == item.checked) {
                    fire(item, item.action);
                }
                break;
            case TILE: {
                int target = selected + delta;
                if (target >= 0 && target < items.size() && items.get(target).kind == ThumbcastMenuModel.Kind.TILE) {
                    select(target);
                }
                break;
            }
            default:
                break;
        }
    }

    private void onActivate(ThumbcastMenuModel.Item item) {
        switch (item.kind) {
            case STEPPER:
                break;
            case SEGMENTED:
                fireOption(item, (item.selectedOption + 1) % item.options.length);
                break;
            default:
                fire(item, item.action);
                break;
        }
    }

    /** Wiersz domyślny zaznaczenia: pierwszy z {@code defaultFocus}, inaczej pierwszy. */
    private int defaultIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).defaultFocus) {
                return i;
            }
        }
        return 0;
    }

    private void select(int index) {
        selected = index;
        updateSelection();
        if (index >= 0 && index < rows.size()) {
            final View row = rows.get(index).root;
            // Po layoucie (nowa lista nie ma jeszcze wymiarów): przewiń tak, żeby wiersz był widoczny.
            row.post(() -> row.requestRectangleOnScreen(new Rect(0, -dp(8), row.getWidth(), row.getHeight() + dp(8)),
                    false));
        }
    }

    private void updateSelection() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            r.root.setForeground(i == selected ? newSelectionDrawable(r.cornerDp) : null);
        }
        int footerVisibility = selected >= 0 ? View.VISIBLE : View.GONE;
        footer.setVisibility(footerVisibility);
        footerDivider.setVisibility(footerVisibility);
    }

    private Drawable newSelectionDrawable(int cornerDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colorAccentDim);
        d.setStroke(dp(2), colorAccent);
        d.setCornerRadius(dp(cornerDp));
        return d;
    }

    private void fire(ThumbcastMenuModel.Item item, Runnable action) {
        if (action != null) {
            listener.onMenuAction(item, action);
        }
    }

    private void fireOption(final ThumbcastMenuModel.Item item, final int index) {
        if (item.selector != null) {
            listener.onMenuAction(item, () -> item.selector.select(index));
        }
    }

    // =====================================================================================
    // Budowa strony
    // =====================================================================================

    private static String shapeKey(ThumbcastMenuModel.Page page) {
        StringBuilder sb = new StringBuilder();
        sb.append(page.subPage).append('|').append(page.introTitle != null).append('|');
        for (ThumbcastMenuModel.Item item : page.items) {
            sb.append(item.kind.name()).append(':').append(item.section).append(';');
        }
        return sb.toString();
    }

    private void rebuild(ThumbcastMenuModel.Page page) {
        list.removeAllViews();
        rows.clear();
        Context c = getContext();
        if (page.introTitle != null) {
            list.addView(buildIntro(page), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        }
        String section = null;
        LinearLayout tileRow = null;
        for (int i = 0; i < page.items.size(); i++) {
            ThumbcastMenuModel.Item item = page.items.get(i);
            if (item.section != null && !item.section.equals(section)) {
                addSectionHeader(item.section);
            }
            section = item.section;
            Row row = createRow(item);
            rows.add(row);
            if (item.kind == ThumbcastMenuModel.Kind.TILE) {
                if (tileRow == null) {
                    tileRow = new LinearLayout(c);
                    tileRow.setOrientation(LinearLayout.HORIZONTAL);
                    tileRow.setPadding(dp(GUTTER_DP), dp(4), dp(GUTTER_DP), dp(4));
                    list.addView(tileRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
                if (tileRow.getChildCount() > 0) {
                    lp.setMarginStart(dp(GUTTER_DP));
                }
                tileRow.addView(row.root, lp);
                continue;
            }
            tileRow = null;
            list.addView(row.root, row.layoutParams);
        }
        // Odstęp na dole: ostatni wiersz nie przylega do krawędzi.
        View spacer = new View(c);
        list.addView(spacer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(GUTTER_DP)));
    }

    private void addSectionHeader(String text) {
        View divider = newDivider();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(GUTTER_DP);
        dlp.setMarginStart(dp(ROW_PAD_DP));
        dlp.setMarginEnd(dp(ROW_PAD_DP));
        list.addView(divider, dlp);

        TextView h = new TextView(getContext());
        h.setText(text.toUpperCase(Locale.getDefault()));
        h.setTextColor(colorAccent);
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_caption));
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.setLetterSpacing(0.08f);
        h.setPadding(dp(ROW_PAD_DP + GUTTER_DP), dp(16), dp(ROW_PAD_DP), dp(4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            h.setAccessibilityHeading(true);
        }
        list.addView(h, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private View buildIntro(ThumbcastMenuModel.Page page) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(ROW_PAD_DP + GUTTER_DP), dp(8), dp(ROW_PAD_DP + GUTTER_DP), dp(16));
        if (page.introIcon != 0) {
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(c.getDrawable(page.introIcon));
            icon.setImageTintList(ColorStateList.valueOf(colorError));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
            ilp.bottomMargin = dp(16);
            box.addView(icon, ilp);
        }
        TextView t = new TextView(c);
        t.setText(page.introTitle);
        t.setTextColor(colorText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_title_large));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            t.setAccessibilityHeading(true);
        }
        box.addView(t, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (page.introBody != null) {
            TextView b = new TextView(c);
            b.setText(page.introBody);
            // Pełny kontrast i 16 sp (G5): to ostrzeżenie, nie przypis.
            b.setTextColor(colorText);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_body));
            b.setLineSpacing(0f, 1.15f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(8);
            box.addView(b, blp);
        }
        return box;
    }

    private LinearLayout buildFooter(Context c) {
        LinearLayout f = new LinearLayout(c);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(ROW_PAD_DP + GUTTER_DP), dp(10), dp(ROW_PAD_DP), dp(10));
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addFooterHint(f, R.string.thumbcast_game_hint_key_a, R.string.thumbcast_game_hint_select);
        TextView dot = newFooterText(" · ");
        f.addView(dot);
        addFooterHint(f, R.string.thumbcast_game_hint_key_b, R.string.thumbcast_game_hint_back);
        return f;
    }

    private void addFooterHint(LinearLayout f, int keyRes, int labelRes) {
        TextView key = new TextView(getContext());
        key.setText(keyRes);
        key.setTextColor(colorText);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setIncludeFontPadding(false);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(dp(1), colorTextSecondary);
        key.setBackground(circle);
        f.addView(key, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = newFooterText(getContext().getString(labelRes));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(6));
        f.addView(label, lp);
    }

    private TextView newFooterText(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(colorTextSecondary);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    // =====================================================================================
    // Wiersze
    // =====================================================================================

    /** Widoki jednego wiersza i odświeżenie ich z pozycji modelu. */
    private final class Row {
        View root;
        LinearLayout.LayoutParams layoutParams;
        int cornerDp = 12;
        ImageView icon;
        TextView titleView;
        TextView subtitleView;
        ImageView trailing;
        SwitchView switchView;
        ImageView decrease;
        ImageView increase;
        TextView value;
        TextView[] segments;

        void update(ThumbcastMenuModel.Item item) {
            if (titleView != null) {
                titleView.setText(item.title);
            }
            if (subtitleView != null) {
                subtitleView.setText(item.subtitle);
                subtitleView.setVisibility(item.subtitle != null ? View.VISIBLE : View.GONE);
            }
            if (icon != null) {
                if (item.icon != 0) {
                    icon.setImageDrawable(getContext().getDrawable(item.icon));
                } else {
                    icon.setImageDrawable(null);
                }
            }
            switch (item.kind) {
                case SWITCH:
                    switchView.setChecked(item.checked);
                    root.setContentDescription(join(getContext().getString(item.checked
                            ? R.string.thumbcast_game_state_on : R.string.thumbcast_game_state_off, item.title),
                            item.subtitle));
                    break;
                case STEPPER:
                    value.setText(item.value);
                    setStepEnabled(decrease, item.decrease != null);
                    setStepEnabled(increase, item.increase != null);
                    root.setContentDescription(getContext().getString(R.string.thumbcast_game_state_value,
                            item.title, item.value));
                    break;
                case SEGMENTED:
                    for (int i = 0; i < segments.length; i++) {
                        boolean on = i == item.selectedOption;
                        TextView seg = segments[i];
                        seg.setText(item.options[i]);
                        seg.setTextColor(on ? colorOnAccent : colorTextSecondary);
                        seg.setTypeface(on ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
                        seg.setBackground(on ? segmentSelectedBackground() : null);
                        seg.setContentDescription(getContext().getString(on ? R.string.thumbcast_game_state_selected
                                : R.string.thumbcast_game_state_value, item.title, item.options[i]));
                        seg.setSelected(on);
                    }
                    break;
                case CHOICE:
                    titleView.setTextColor(item.checked ? colorAccent : colorText);
                    titleView.setTypeface(item.checked ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            : Typeface.DEFAULT);
                    icon.setImageTintList(ColorStateList.valueOf(colorAccent));
                    trailing.setVisibility(item.starred ? View.VISIBLE : View.INVISIBLE);
                    root.setContentDescription(join(item.title, item.subtitle));
                    root.setSelected(item.checked);
                    break;
                case TILE:
                case PRIMARY:
                case SECONDARY:
                case DANGER:
                    root.setContentDescription(item.title);
                    break;
                default:
                    root.setContentDescription(join(item.title, item.subtitle));
                    break;
            }
        }
    }

    private static String join(String a, String b) {
        return b == null || b.isEmpty() ? a : a + ". " + b;
    }

    private Row createRow(final ThumbcastMenuModel.Item item) {
        switch (item.kind) {
            case PRIMARY:
            case SECONDARY:
            case DANGER:
                return createButton(item);
            case TILE:
                return createTile(item);
            case STEPPER:
                return createStepper(item);
            case SEGMENTED:
                return createSegmented(item);
            default:
                return createListRow(item);
        }
    }

    /** Wypełniony albo obrysowany przycisk-pigułka na całą szerokość. */
    private Row createButton(final ThumbcastMenuModel.Item item) {
        Context c = getContext();
        Row r = new Row();
        r.cornerDp = BUTTON_HEIGHT_DP / 2;
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(BUTTON_HEIGHT_DP));
        b.setPadding(dp(ROW_PAD_DP), 0, dp(ROW_PAD_DP), 0);
        int textColor;
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(r.cornerDp));
        if (item.kind == ThumbcastMenuModel.Kind.PRIMARY) {
            shape.setColor(colorAccent);
            textColor = colorOnAccent;
        } else if (item.kind == ThumbcastMenuModel.Kind.DANGER) {
            shape.setColor(colorErrorContainer);
            textColor = colorText;
        } else {
            shape.setColor(0);
            shape.setStroke(dp(1), colorOutline);
            textColor = colorText;
        }
        b.setBackground(ripple(shape, r.cornerDp));
        if (item.icon != 0) {
            r.icon = newIcon(textColor);
            b.addView(r.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        r.titleView = new TextView(c);
        r.titleView.setTextColor(textColor);
        r.titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_title));
        r.titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        if (item.icon != 0) {
            tlp.setMarginStart(dp(8));
        }
        b.addView(r.titleView, tlp);
        b.setClickable(true);
        b.setOnClickListener(v -> fire(itemFor(r), itemFor(r).action));
        markAs(b, Button.class.getName(), false, false);
        r.root = b;
        r.layoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        r.layoutParams.setMargins(dp(GUTTER_DP), dp(4), dp(GUTTER_DP), dp(4));
        return r;
    }

    /** Kafel szybkiej akcji: ikona nad etykietą. */
    private Row createTile(final ThumbcastMenuModel.Item item) {
        Context c = getContext();
        Row r = new Row();
        r.cornerDp = 16;
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setMinimumHeight(dp(76));
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colorSurfaceRaised);
        shape.setCornerRadius(dp(r.cornerDp));
        t.setBackground(ripple(shape, r.cornerDp));
        r.icon = newIcon(colorText);
        t.addView(r.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        r.titleView = new TextView(c);
        r.titleView.setTextColor(colorText);
        r.titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_label));
        r.titleView.setGravity(Gravity.CENTER);
        r.titleView.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        t.addView(r.titleView, lp);
        t.setClickable(true);
        t.setOnClickListener(v -> fire(itemFor(r), itemFor(r).action));
        markAs(t, Button.class.getName(), false, false);
        r.root = t;
        return r;
    }

    /** Wiersz listy: ikona, tytuł, podpis i element końcowy (strzałka, przełącznik, gwiazdka). */
    private Row createListRow(final ThumbcastMenuModel.Item item) {
        Context c = getContext();
        final Row r = new Row();
        LinearLayout row = newRowContainer();
        boolean danger = item.kind == ThumbcastMenuModel.Kind.ROW && item.danger;
        r.icon = newIcon(danger ? colorError : colorText);
        row.addView(r.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout texts = newTextColumn(r, danger ? colorError : colorText);
        row.addView(texts, newTextColumnParams());
        String className = Button.class.getName();
        boolean checkable = false;
        switch (item.kind) {
            case NAV:
                r.trailing = newIcon(colorTextSecondary);
                r.trailing.setImageDrawable(c.getDrawable(R.drawable.thumbcast_ic_chevron_right));
                row.addView(r.trailing, new LinearLayout.LayoutParams(dp(24), dp(24)));
                break;
            case SWITCH:
                r.switchView = new SwitchView(c);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(52), dp(32));
                slp.setMarginStart(dp(12));
                row.addView(r.switchView, slp);
                className = android.widget.Switch.class.getName();
                checkable = true;
                break;
            case CHOICE:
                r.trailing = newIcon(colorAccent);
                r.trailing.setImageDrawable(c.getDrawable(R.drawable.thumbcast_ic_star_filled));
                row.addView(r.trailing, new LinearLayout.LayoutParams(dp(24), dp(24)));
                break;
            default:
                break;
        }
        row.setBackground(ripple(null, r.cornerDp));
        row.setClickable(true);
        row.setOnClickListener(v -> fire(itemFor(r), itemFor(r).action));
        markAs(row, className, checkable, item.kind == ThumbcastMenuModel.Kind.SWITCH);
        r.root = row;
        r.layoutParams = rowParams();
        return r;
    }

    /** Rozmiar przycisków: ikona, tytuł, [−] wartość [+] (ikony w kółkach, wartość w pigułce, G17). */
    private Row createStepper(final ThumbcastMenuModel.Item item) {
        Context c = getContext();
        final Row r = new Row();
        LinearLayout row = newRowContainer();
        r.icon = newIcon(colorText);
        row.addView(r.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout texts = newTextColumn(r, colorText);
        row.addView(texts, newTextColumnParams());
        r.decrease = newStepButton(R.drawable.thumbcast_ic_remove, item.decreaseDescription,
                v -> fire(itemFor(r), itemFor(r).decrease));
        row.addView(r.decrease, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        r.value = new TextView(c);
        r.value.setTextColor(colorText);
        r.value.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_label));
        r.value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        r.value.setFontFeatureSettings("tnum");
        r.value.setGravity(Gravity.CENTER);
        r.value.setMinWidth(dp(56));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(colorAccentDim);
        pill.setCornerRadius(dp(16));
        r.value.setBackground(pill);
        r.value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(r.value, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)));
        r.increase = newStepButton(R.drawable.thumbcast_ic_add, item.increaseDescription,
                v -> fire(itemFor(r), itemFor(r).increase));
        row.addView(r.increase, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        row.setPaddingRelative(dp(ROW_PAD_DP), dp(4), dp(4), dp(4));
        r.root = row;
        r.layoutParams = rowParams();
        return r;
    }

    /** Statystyki: tytuł z ikoną, pod nim trzy segmenty (dotyk; pad: lewo/prawo, G9). */
    private Row createSegmented(final ThumbcastMenuModel.Item item) {
        Context c = getContext();
        final Row r = new Row();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(ROW_PAD_DP), dp(8), dp(ROW_PAD_DP), dp(10));
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setMinimumHeight(dp(40));
        r.icon = newIcon(colorText);
        top.addView(r.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout texts = newTextColumn(r, colorText);
        top.addView(texts, newTextColumnParams());
        box.addView(top, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout group = new LinearLayout(c);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(dp(2), dp(2), dp(2), dp(2));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0);
        outline.setStroke(dp(1), colorOutline);
        outline.setCornerRadius(dp(24));
        group.setBackground(outline);
        r.segments = new TextView[item.options.length];
        for (int i = 0; i < item.options.length; i++) {
            final int index = i;
            TextView seg = new TextView(c);
            seg.setGravity(Gravity.CENTER);
            seg.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_label));
            seg.setSingleLine(true);
            seg.setEllipsize(TextUtils.TruncateAt.END);
            seg.setPadding(dp(4), 0, dp(4), 0);
            seg.setClickable(true);
            seg.setForeground(ripple(null, 22));
            seg.setOnClickListener(v -> {
                ThumbcastMenuModel.Item current = itemFor(r);
                if (current.selectedOption != index) {
                    fireOption(current, index);
                }
            });
            markAs(seg, android.widget.RadioButton.class.getName(), true, false);
            r.segments[i] = seg;
            group.addView(seg, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(6);
        box.addView(group, glp);
        r.root = box;
        r.layoutParams = rowParams();
        return r;
    }

    /** Pozycja modelu dla wiersza (po odświeżeniu lista items jest nowa, a widoki te same). */
    private ThumbcastMenuModel.Item itemFor(Row r) {
        int i = rows.indexOf(r);
        return items.get(i);
    }

    private LinearLayout newRowContainer() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(ROW_MIN_DP));
        row.setPaddingRelative(dp(ROW_PAD_DP), dp(8), dp(ROW_PAD_DP), dp(8));
        return row;
    }

    private LinearLayout newTextColumn(Row r, int titleColor) {
        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        r.titleView = new TextView(getContext());
        r.titleView.setTextColor(titleColor);
        r.titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_title));
        texts.addView(r.titleView);
        r.subtitleView = new TextView(getContext());
        r.subtitleView.setTextColor(colorTextSecondary);
        r.subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.thumbcast_text_body_small));
        r.subtitleView.setVisibility(View.GONE);
        texts.addView(r.subtitleView);
        return texts;
    }

    private LinearLayout.LayoutParams newTextColumnParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(16));
        lp.setMarginEnd(dp(8));
        return lp;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(GUTTER_DP), 0, dp(GUTTER_DP), 0);
        return lp;
    }

    private ImageView newIcon(int color) {
        ImageView v = new ImageView(getContext());
        v.setImageTintList(ColorStateList.valueOf(color));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setFocusable(View.NOT_FOCUSABLE);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    private ImageView newIconButton(int iconRes, String description, View.OnClickListener onClick) {
        ImageView b = newIcon(colorText);
        b.setImageDrawable(getContext().getDrawable(iconRes));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        b.setContentDescription(description);
        b.setClickable(true);
        b.setBackground(ripple(null, TOUCH_DP / 2));
        b.setOnClickListener(onClick);
        markAs(b, Button.class.getName(), false, false);
        return b;
    }

    /** − / + steppera: ikona w kółku (obrys), pole dotyku 48 dp. */
    private ImageView newStepButton(int iconRes, String description, View.OnClickListener onClick) {
        ImageView b = newIconButton(iconRes, description, onClick);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0);
        ring.setStroke(dp(1), colorOutline);
        int inset = dp(6);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                new android.graphics.drawable.InsetDrawable(ring, inset), null));
        return b;
    }

    private static void setStepEnabled(View b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    private View newDivider() {
        View d = new View(getContext());
        d.setBackgroundColor(colorDivider);
        d.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return d;
    }

    private Drawable segmentSelectedBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colorAccent);
        d.setCornerRadius(dp(22));
        return d;
    }

    /** Ripple na treści (null = przezroczyste tło z maską zaokrąglonego prostokąta). */
    private Drawable ripple(Drawable content, int cornerDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(cornerDp));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    /** TalkBack: klasa (przycisk, przełącznik, opcja) i stan zaznaczenia. */
    private static void markAs(View v, final String className, final boolean checkable, final boolean useSwitch) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(className);
                if (checkable) {
                    info.setCheckable(true);
                    if (useSwitch && host instanceof ViewGroup) {
                        View last = ((ViewGroup) host).getChildAt(((ViewGroup) host).getChildCount() - 1);
                        info.setChecked(last instanceof SwitchView && ((SwitchView) last).checked);
                    } else {
                        info.setChecked(host.isSelected());
                    }
                }
            }
        });
    }

    /** Przełącznik rysowany z palety (G3): tor 52×32 dp, gałka; wł. = akcent, wył. = obrys. Bez dotyku (wiersz). */
    private final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        boolean checked;

        SwitchView(Context context) {
            super(context);
            setFocusable(View.NOT_FOCUSABLE);
            setClickable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setChecked(boolean on) {
            if (on != checked) {
                checked = on;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float stroke = dp(2);
            track.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f);
            float r = track.height() / 2f;
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(colorAccent);
                canvas.drawRoundRect(track, r, r, paint);
                paint.setColor(colorOnAccent);
                canvas.drawCircle(w - h / 2f, h / 2f, h / 2f - dp(6), paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(colorOutline);
                canvas.drawRoundRect(track, r, r, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(h / 2f, h / 2f, h / 2f - dp(10), paint);
            }
        }
    }

    // =====================================================================================
    // Rozmiar panelu, rogi i wcięcia (wycięcie ekranu, paski systemowe poza trybem immersive)
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
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateCorners();
        updatePadding();
    }

    /** Rogi zaokrąglone tylko od strony wideo (START panelu); przy krawędzi ekranu proste. */
    private void updateCorners() {
        float r = dp(CORNER_DP);
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        // Kolejność: lewy górny, prawy górny, prawy dolny, lewy dolny (po dwie wartości x, y).
        panelBackground.setCornerRadii(rtl ? new float[]{0, 0, r, r, r, r, 0, 0}
                : new float[]{r, r, 0, 0, 0, 0, r, r});
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

    /** Treść panelu omija wcięcia (góra, dół, krawędź ekranu); tło panelu sięga do krawędzi. */
    private void updatePadding() {
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int edge = rtl ? insetLeft : insetRight;
        int base = dp(GUTTER_DP);
        // Krawędź ekranu to zawsze END panelu.
        header.setPaddingRelative(base, base + insetTop, base + edge, 0);
        scroll.setPaddingRelative(0, 0, edge, 0);
        footer.setPaddingRelative(dp(ROW_PAD_DP + GUTTER_DP), dp(10), dp(ROW_PAD_DP) + edge, dp(10) + insetBottom);
        // Bez stopki dół listy omija pasek nawigacji.
        list.setPadding(0, 0, 0, insetBottom);
    }

    private int dp(int value) {
        return Math.round(value * density);
    }
}
